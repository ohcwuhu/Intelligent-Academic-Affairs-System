package com.iaas.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * 大模型调用。
 *
 * <p>支持两种提供方：本机 Ollama（数据不出校）与 OpenAI 兼容接口（如 DeepSeek）。
 * provider 设为 none、调用失败或超时，一律返回空，由上层降级为抽取式回答，
 * 绝不因为模型不可用就让整个问答功能不可用。
 */
@Component
@Slf4j
public class LlmClient {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${iaas.assistant.llm.provider:none}")
    private String provider;

    @Value("${iaas.assistant.llm.ollama.base-url:http://127.0.0.1:11434}")
    private String ollamaBaseUrl;

    @Value("${iaas.assistant.llm.ollama.model:qwen3:8b}")
    private String ollamaModel;

    @Value("${iaas.assistant.llm.openai.base-url:https://api.deepseek.com/v1}")
    private String openAiBaseUrl;

    @Value("${iaas.assistant.llm.openai.model:deepseek-chat}")
    private String openAiModel;

    @Value("${iaas.assistant.llm.openai.api-key:}")
    private String openAiApiKey;

    @Value("${iaas.assistant.llm.timeout-seconds:120}")
    private long timeoutSeconds;

    public boolean isEnabled() {
        if (provider == null || "none".equalsIgnoreCase(provider)) {
            return false;
        }
        // 选了远程提供方却没有密钥，等于不可用，直接按未接入处理
        if (usesOpenAiCompatible() && (openAiApiKey == null || openAiApiKey.isBlank())) {
            return false;
        }
        return true;
    }

    public String providerName() {
        return provider;
    }

    public String modelName() {
        if ("ollama".equalsIgnoreCase(provider)) {
            return ollamaModel;
        }
        return usesOpenAiCompatible() ? openAiModel : provider;
    }

    private boolean usesOpenAiCompatible() {
        return "deepseek".equalsIgnoreCase(provider)
                || "openai".equalsIgnoreCase(provider)
                || "compatible".equalsIgnoreCase(provider);
    }

    /** 生成回答。任何异常都吞掉并返回空，让上层走降级路径。 */
    public Optional<String> chat(String systemPrompt, String userPrompt) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        return usesOpenAiCompatible()
                ? chatOpenAiCompatible(systemPrompt, userPrompt)
                : chatOllama(systemPrompt, userPrompt);
    }

    private Optional<String> chatOllama(String systemPrompt, String userPrompt) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", ollamaModel);
            body.put("stream", false);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);
            // 问答要稳定克制，温度压到 0.1
            ObjectNode options = body.putObject("options");
            options.put("temperature", 0.1);
            options.put("num_predict", 600);

            HttpResponse<String> resp = send(ollamaBaseUrl + "/api/chat", body, null);
            if (resp == null) {
                return Optional.empty();
            }
            JsonNode root = mapper.readTree(resp.body());
            String content = root.path("message").path("content").asText("");
            return content.isBlank() ? Optional.empty() : Optional.of(content.strip());
        } catch (Exception e) {
            log.warn("模型调用失败，降级为抽取式回答：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /** OpenAI 兼容接口。DeepSeek、通义、智谱等都走这套协议。 */
    private Optional<String> chatOpenAiCompatible(String systemPrompt, String userPrompt) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", openAiModel);
            body.put("stream", false);
            body.put("temperature", 0.1);
            body.put("max_tokens", 600);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);

            HttpResponse<String> resp = send(openAiBaseUrl + "/chat/completions",
                    body, "Bearer " + openAiApiKey);
            if (resp == null) {
                return Optional.empty();
            }
            JsonNode root = mapper.readTree(resp.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            return content.isBlank() ? Optional.empty() : Optional.of(content.strip());
        } catch (Exception e) {
            log.warn("模型调用失败，降级为抽取式回答：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 发送请求。非 200 或异常统一返回 null，由调用方降级。 */
    private HttpResponse<String> send(String url, ObjectNode body, String authHeader) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json; charset=utf-8");
        if (authHeader != null) {
            builder.header("Authorization", authHeader);
        }
        HttpRequest req = builder.POST(HttpRequest.BodyPublishers.ofString(
                mapper.writeValueAsString(body), StandardCharsets.UTF_8)).build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            log.warn("模型返回非 200：{}，降级为抽取式回答", resp.statusCode());
            return null;
        }
        return resp;
    }
}
