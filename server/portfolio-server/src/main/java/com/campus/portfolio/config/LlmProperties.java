package com.campus.portfolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 接入配置（百炼 DashScope 兼容模式 / 任意 OpenAI 兼容网关 / 本机 Ollama 都能套）。
 * <p>
 * 全部可在 application.yml 的 portfolio.llm 下覆盖，密钥支持环境变量注入：
 * <pre>
 * portfolio:
 *   llm:
 *     base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
 *     api-key: ${DASHSCOPE_API_KEY:}      # 不写死密钥，部署时用环境变量给
 *     model: qwen-plus
 * </pre>
 * enabled=false 或 api-key 为空时，AI 接口返回 503 并在文案里说明原因，前端按降级提示处理，
 * 不会把整套页面拖死。
 */
@Component
@ConfigurationProperties(prefix = "portfolio.llm")
public class LlmProperties {

    /** 总开关：关闭后所有 AI 接口直接返回 503 */
    private boolean enabled = true;

    /** OpenAI 兼容的 base url（不带 /chat/completions） */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private String apiKey = "";

    private String model = "qwen-plus";

    /** 生成温度：0 严谨 / 1 发散；校园场景 0.5~0.7 比较稳 */
    private double temperature = 0.6;

    private int maxTokens = 800;

    /** 单次调用超时（秒）：流式首字 1 秒内，整段 60 秒内基本能返回 */
    private int timeoutSeconds = 90;

    /** 单用户每分钟最多请求数（本机内存限流，防手抖连点） */
    private int rateLimitPerMinute = 20;

    /** 是否在响应里回传用量（tokens），课堂演示可看成本 */
    private boolean returnUsage = true;

    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public boolean getEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }
    public boolean isReturnUsage() { return returnUsage; }
    public void setReturnUsage(boolean returnUsage) { this.returnUsage = returnUsage; }

    /** 脱敏后的密钥，只用于 /v1/ai/status 展示 */
    public String maskedKey() {
        if (apiKey == null || apiKey.isBlank()) return "";
        if (apiKey.length() <= 10) return "***";
        return apiKey.substring(0, 6) + "***" + apiKey.substring(apiKey.length() - 4);
    }
}
