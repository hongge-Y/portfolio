package com.campus.portfolio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.campus.portfolio.common.BizException;
import com.campus.portfolio.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * LLM 调用层：只认 OpenAI 兼容协议（/chat/completions），因此
 * 百炼 DashScope 兼容模式、自建网关、本机 Ollama 三种后端切换只改配置。
 * <p>
 * 两个入口：
 * <ul>
 *   <li>{@link #chat} 一次性拿全量回答（按钮类场景：请假理由、审批建议、新闻摘要）</li>
 *   <li>{@link #stream} 边收边回调（对话框场景：打字机效果，首字节 ~600ms）</li>
 * </ul>
 * 不引任何 SDK：JDK17 自带的 java.net.http + Jackson 足够，少一个依赖少一处版本坑。
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final LlmProperties props;
    /**
     * HttpClient 的 1.1 连接池会复用 keep-alive 连接；上游（DashScope）空闲后会主动关，
     * 复用到已关的连接就抛 IOException。所以这里失败一次就换一个全新 client 重试，
     * 只重试一次（已成流式输出时不重试，避免重复 token）。
     */
    private volatile HttpClient client;

    public LlmService(LlmProperties props) {
        this.props = props;
        this.client = newClient();
        log.info("LLM 接入：enabled={} model={} baseUrl={} key={}",
                props.isEnabled(), props.getModel(), props.getBaseUrl(), props.maskedKey());
    }

    private HttpClient newClient() {
        // 固定 HTTP/1.1：部分网关在 h2 下 SSE 分块不 flush，表现为"等半天一次性吐出来"
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public LlmProperties getProps() {
        return props;
    }

    public boolean available() {
        return props.isEnabled();
    }

    /** /v1/ai/status 用：把"能不能用、用的哪个模型"直接告诉前端 */
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", props.isEnabled());
        m.put("model", props.getModel());
        m.put("provider", providerName());
        m.put("baseUrl", props.getBaseUrl());
        m.put("apiKeyMasked", props.maskedKey());
        m.put("maxTokens", props.getMaxTokens());
        m.put("temperature", props.getTemperature());
        return m;
    }

    private String providerName() {
        String u = props.getBaseUrl() == null ? "" : props.getBaseUrl();
        if (u.contains("dashscope")) return "阿里云百炼 DashScope";
        if (u.contains("11434")) return "本机 Ollama";
        return "OpenAI 兼容网关";
    }

    // ------------------------------------------------------------------ 非流式

    /**
     * 一次性对话。
     *
     * @param messages [{role: system|user|assistant, content: String}]
     */
    public String chat(List<Map<String, String>> messages) {
        return chat(messages, props.getTimeoutSeconds());
    }

    /** 一次性对话，可指定本次调用的超时秒数（审批建议这类要在页面上等的场景用 8 秒） */
    public String chat(List<Map<String, String>> messages, int timeoutSeconds) {
        ensureReady();
        long t0 = System.currentTimeMillis();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getModel());
        body.put("messages", messages);
        body.put("temperature", props.getTemperature());
        body.put("max_tokens", props.getMaxTokens());
        body.put("stream", false);

        HttpResponse<String> resp;
        try {
            // 短超时（≤10s，页面等人场景）不重试，避免 8s×2=16s 的等待
            resp = sendWithRetry(request(body, timeoutSeconds),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), timeoutSeconds > 10);
        } catch (Exception e) {
            throw upstream(e, timeoutSeconds);
        }
        if (resp.statusCode() != 200) {
            throw httpError(resp.statusCode(), resp.body());
        }
        try {
            JsonNode root = JSON.readTree(resp.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new BizException(502, "LLM_EMPTY", "模型没有返回内容，请重试");
            }
            log.info("LLM chat ok model={} {}ms msgLen={} tokens={}", props.getModel(),
                    System.currentTimeMillis() - t0, content.length(),
                    root.path("usage").path("total_tokens").asInt());
            return content.trim();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "LLM_BAD_JSON", "模型返回解析失败");
        }
    }

    // ------------------------------------------------------------------ 流式

    /**
     * 流式对话：每收到一个 delta 就回调一次，返回拼接后的全文。
     * 回调抛异常（例如前端已断开）会中断读取，避免白烧 token。
     */
    public String stream(List<Map<String, String>> messages, Consumer<String> onDelta) {
        ensureReady();
        long t0 = System.currentTimeMillis();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getModel());
        body.put("messages", messages);
        body.put("temperature", props.getTemperature());
        body.put("max_tokens", props.getMaxTokens());
        body.put("stream", true);

        HttpResponse<InputStream> resp;
        try {
            resp = sendWithRetry(request(body), HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception e) {
            throw upstream(e);
        }
        if (resp.statusCode() != 200) {
            String err = readAll(resp.body());
            throw httpError(resp.statusCode(), err);
        }

        StringBuilder full = new StringBuilder();
        long firstDeltaAt = -1;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || !line.startsWith("data:")) continue;
                String payload = line.substring(5).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) {
                    if ("[DONE]".equals(payload)) break;
                    continue;
                }
                String delta;
                try {
                    delta = JSON.readTree(payload)
                            .path("choices").path(0).path("delta").path("content").asText("");
                } catch (Exception ignore) {
                    continue; // 个别网关会插空行/注释行，忽略即可
                }
                if (delta.isEmpty()) continue;
                if (firstDeltaAt < 0) firstDeltaAt = System.currentTimeMillis() - t0;
                full.append(delta);
                onDelta.accept(delta);
            }
        } catch (Exception e) {
            if (!full.isEmpty()) {
                log.warn("LLM stream 中断，已返回部分内容 {} 字：{}", full.length(), e.toString());
                return full.toString();
            }
            throw upstream(e);
        }
        log.info("LLM stream ok model={} 首字节={}ms 总耗时={}ms 字数={}",
                props.getModel(), firstDeltaAt, System.currentTimeMillis() - t0, full.length());
        return full.toString();
    }

    // ------------------------------------------------------------------ 内部

    private void ensureReady() {
        if (!props.getEnabled()) {
            throw new BizException(503, "LLM_DISABLED", "AI 助手未启用（portfolio.llm.enabled=false）");
        }
        if (!isBlank(props.getApiKey())) {
            return;
        }
        throw new BizException(503, "LLM_NO_KEY",
                "AI 助手未配置密钥：请设置环境变量 DASHSCOPE_API_KEY 或 application.yml 的 portfolio.llm.api-key");
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 发送请求，遇到连接层 IOException 时换一个全新 client 重试一次。
     * 只在"还没开始读响应体"的阶段调用，所以重试不会造成重复输出。
     */
    private <T> HttpResponse<T> sendWithRetry(HttpRequest req, HttpResponse.BodyHandler<T> handler)
            throws Exception {
        return sendWithRetry(req, handler, true);
    }

    private <T> HttpResponse<T> sendWithRetry(HttpRequest req, HttpResponse.BodyHandler<T> handler, boolean retry)
            throws Exception {
        try {
            return client.send(req, handler);
        } catch (java.io.IOException first) {
            if (!retry) throw first;
            log.warn("LLM 连接异常，换新连接重试一次：{}", first.toString());
            client = newClient();
            return client.send(req, handler);
        }
    }

    private HttpRequest request(Map<String, Object> body) {
        return request(body, props.getTimeoutSeconds());
    }

    private HttpRequest request(Map<String, Object> body, int timeoutSeconds) {
        String json;
        try {
            json = JSON.writeValueAsString(body);
        } catch (Exception e) {
            throw new BizException(500, "LLM_REQ", "构造请求失败");
        }
        return HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(props.getBaseUrl()) + "/chat/completions"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
    }

    private String trimSlash(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private BizException httpError(int status, String body) {
        String detail = body == null ? "" : body;
        if (detail.length() > 300) detail = detail.substring(0, 300);
        log.warn("LLM 上游错误 status={} body={}", status, detail);
        String msg = switch (status) {
            case 401, 403 -> "AI 密钥无效或无权限（HTTP " + status + "）";
            case 404 -> "AI 模型不存在：" + props.getModel();
            case 429 -> "AI 调用过于频繁，请稍后再试";
            case 400 -> "AI 请求参数有误：" + detail;
            default -> "AI 服务返回 " + status;
        };
        return new BizException(502, "LLM_HTTP_" + status, msg);
    }

    private BizException upstream(Exception e) {
        return upstream(e, props.getTimeoutSeconds());
    }

    private BizException upstream(Exception e, int timeoutSeconds) {
        if (e instanceof java.net.http.HttpTimeoutException) {
            log.warn("LLM 超时 {}s", timeoutSeconds);
            return new BizException(504, "LLM_TIMEOUT", "AI 响应超时（" + timeoutSeconds + "s），请手动填写");
        }
        if (e instanceof BizException be) return be;
        log.warn("LLM 网络异常：{}", e.toString());
        return new BizException(502, "LLM_NETWORK", "AI 服务连接失败：" + e.getClass().getSimpleName());
    }

    private String readAll(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
