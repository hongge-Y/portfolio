package com.campus.portfolio.controller;

import com.campus.portfolio.common.Result;
import com.campus.portfolio.common.UserContext;
import com.campus.portfolio.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 能力出口。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>流式接口用 SseEmitter，事件名 delta/done/error，前端只需按行读 "data:"；</li>
 *   <li>UserContext 是 ThreadLocal，异步线程读不到，所以提示词在请求线程里先拼好再交给线程池；</li>
 *   <li>所有失败都以业务码返回（503 未配置 / 504 超时 / 429 限流），前端可以给出人话提示。</li>
 * </ul>
 */
@Tag(name = "AI", description = "AI 助手 / 请假理由 / 审批建议 / 新闻摘要")
@RestController
@RequestMapping("/v1/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final AiService ai;
    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "ai-sse");
        t.setDaemon(true);
        return t;
    });

    public AiController(AiService ai) {
        this.ai = ai;
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }

    @Operation(summary = "AI 能力状态（是否可用、模型、供应商）")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> m = new LinkedHashMap<>(ai.llm().status());
        m.put("scenes", List.of(
                Map.of("key", "chat", "name", "AI 助手", "desc", "随时提问，懂平台业务"),
                Map.of("key", "leave-draft", "name", "请假理由", "desc", "一句话生成可提交的事由"),
                Map.of("key", "approve-suggest", "name", "审批建议", "desc", "辅导员看风险点"),
                Map.of("key", "news-summary", "name", "新闻摘要", "desc", "3 句话读完通知")));
        return Result.ok(m);
    }

    @Operation(summary = "AI 对话（一次性返回全文）")
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        UserContext ctx = UserContext.require();
        String message = str(body.get("message"));
        if (message.isBlank()) {
            return Result.fail(400, "请输入要问的内容");
        }
        List<Map<String, String>> messages = ai.buildChatMessages(ctx, message, history(body));
        long t0 = System.currentTimeMillis();
        String text = ai.chat(ctx, messages);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", text);
        m.put("model", ai.llm().getProps().getModel());
        m.put("costMs", System.currentTimeMillis() - t0);
        return Result.ok(m);
    }

    @Operation(summary = "AI 对话（SSE 流式）")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, Object> body) {
        UserContext ctx = UserContext.require();
        String message = str(body.get("message"));
        if (message.isBlank()) {
            throw new com.campus.portfolio.common.BizException(400, "请输入要问的内容");
        }
        // 关键：请求线程里拼好提示词，异步线程只负责读取流
        List<Map<String, String>> messages = ai.buildChatMessages(ctx, message, history(body));
        String model = ai.llm().getProps().getModel();

        SseEmitter emitter = new SseEmitter(120_000L);
        emitter.onTimeout(() -> emitter.complete());
        sender(emitter).accept(ev("open", Map.of("model", model)));

        pool.execute(() -> {
            long t0 = System.currentTimeMillis();
            try {
                ai.streamChat(ctx, messages, delta -> sender(emitter).accept(ev("delta", Map.of("d", delta))));
                sender(emitter).accept(ev("done", Map.of(
                        "model", model,
                        "costMs", System.currentTimeMillis() - t0)));
                emitter.complete();
            } catch (Exception e) {
                log.warn("AI 流式失败：{}", e.getMessage());
                sender(emitter).accept(ev("error", Map.of("message", friendly(e))));
                emitter.complete();
            }
        });
        return emitter;
    }

    @Operation(summary = "请假理由生成")
    @PostMapping("/leave-draft")
    public Result<Map<String, Object>> leaveDraft(@RequestBody Map<String, Object> body) {
        UserContext ctx = UserContext.require();
        return Result.ok(ai.leaveDraft(ctx,
                toInt(body.get("type")),
                str(body.get("startTime")),
                str(body.get("endTime")),
                toInt(body.get("days")),
                str(body.get("remark"))));
    }

    @Operation(summary = "审批建议（仅审批人）")
    @PostMapping("/approve-suggest")
    public Result<Map<String, Object>> approveSuggest(@RequestBody Map<String, Object> body) {
        UserContext ctx = UserContext.require();
        Integer leaveId = toInt(body.get("leaveId"));
        if (leaveId == null) {
            return Result.fail(400, "缺少 leaveId");
        }
        return Result.ok(ai.approveSuggest(ctx, leaveId.longValue()));
    }

    @Operation(summary = "新闻摘要（3 句话读完）")
    @PostMapping("/news-summary")
    public Result<Map<String, Object>> newsSummary(@RequestBody Map<String, Object> body) {
        UserContext ctx = UserContext.require();
        Integer newsId = toInt(body.get("newsId"));
        if (newsId == null) {
            return Result.fail(400, "缺少 newsId");
        }
        return Result.ok(ai.newsSummary(ctx, newsId.longValue()));
    }

    // ------------------------------------------------------------------ 工具

    private SseEmitter.SseEventBuilder ev(String name, Map<String, Object> data) {
        return SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON);
    }

    /** 包一层：客户端断开时 send 会抛 IOException，这里吞掉，让上层读流自然结束 */
    private interface Sender {
        void accept(SseEmitter.SseEventBuilder b);
    }

    private Sender sender(SseEmitter emitter) {
        return b -> {
            try {
                emitter.send(b);
            } catch (Exception e) {
                throw new IllegalStateException("SSE 连接已断开", e);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> history(Map<String, Object> body) {
        Object h = body.get("history");
        if (!(h instanceof List<?> list)) return new ArrayList<>();
        List<Map<String, String>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("role", str(m.get("role")));
                item.put("content", str(m.get("content")));
                out.add(item);
            }
        }
        return out;
    }

    private String friendly(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "AI 暂时不可用，请稍后再试";
        // BizException 的 message 形如 "LLM_TIMEOUT:AI 响应超时（90s），请重试"，取冒号后的可读文案
        int i = msg.indexOf(':');
        return i > 0 && i < 30 ? msg.substring(i + 1) : msg;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private Integer toInt(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return null;
        try {
            return (int) Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
