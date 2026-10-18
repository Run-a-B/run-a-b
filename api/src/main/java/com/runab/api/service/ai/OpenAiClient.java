package com.runab.api.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Set;

/**
 * OpenAI Chat Completions API 호출 클라이언트.
 * RunPod AiGatewayClient를 대체 — 항상 JSON 객체 형식으로만 응답받는다(response_format=json_object).
 */
@Slf4j
@Component
public class OpenAiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    private static final Set<String> QUOTA_ERROR_CODES = Set.of(
            "insufficient_quota", "credit_balance_exhausted", "organization_spend_limit_exceeded",
            "project_spend_limit_exceeded", "organization_usage_limit_exceeded");

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public OpenAiClient(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model:gpt-4o-mini}") String model,
            @Value("${openai.timeout-seconds:60}") int timeoutSeconds
    ) {
        this.apiKey = apiKey;
        this.model = model;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .requestFactory(factory)
                .build();

        log.info("[OpenAiClient] 초기화 완료 - model={}", model);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getModel() {
        return model;
    }

    /**
     * systemPrompt + userPrompt를 보내고, JSON 객체 응답 본문(JsonNode)을 그대로 파싱해서 반환.
     */
    public JsonNode chatJson(String systemPrompt, String userPrompt) {
        if (!isConfigured()) throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.4);
        body.put("max_completion_tokens", 4_000);

        ObjectNode responseFormat = MAPPER.createObjectNode();
        responseFormat.put("type", "json_object");
        body.set("response_format", responseFormat);

        var messages = MAPPER.createArrayNode();
        ObjectNode sys = MAPPER.createObjectNode();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        messages.add(sys);

        ObjectNode user = MAPPER.createObjectNode();
        user.put("role", "user");
        user.put("content", userPrompt);
        messages.add(user);

        body.set("messages", messages);

        try {
            String responseBody = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw new BusinessException(ErrorCode.AI_INVALID_RESPONSE);
            }
            // Parse both layers strictly: ambiguous/partial JSON must never become a cached analysis.
            JsonNode response = MAPPER.readTree(responseBody);
            JsonNode choice = response.path("choices").path(0);
            JsonNode message = choice.path("message");
            if (!"stop".equals(choice.path("finish_reason").asText())
                    || message.hasNonNull("refusal") || !message.path("content").isTextual()) {
                throw new BusinessException(ErrorCode.AI_INVALID_RESPONSE);
            }
            JsonNode parsed = MAPPER.readTree(message.path("content").textValue());
            if (parsed == null || !parsed.isObject() || parsed.isEmpty()) {
                throw new BusinessException(ErrorCode.AI_INVALID_RESPONSE);
            }
            return parsed;

        } catch (BusinessException e) {
            throw e;
        } catch (RestClientResponseException e) {
            // Provider exception messages include request/response bodies; never log those or user data.
            log.warn("[OpenAiClient] provider rejected request - status={}", e.getStatusCode().value());
            throw new BusinessException(providerErrorCode(e));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BusinessException(ErrorCode.AI_INVALID_RESPONSE);
        } catch (Exception e) {
            log.warn("[OpenAiClient] request failed - type={}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    private ErrorCode providerErrorCode(RestClientResponseException exception) {
        if (exception.getStatusCode().value() == 401) return ErrorCode.AI_CONFIGURATION_ERROR;
        if (exception.getStatusCode().value() != 429) return ErrorCode.AI_SERVICE_UNAVAILABLE;
        try {
            JsonNode response = MAPPER.readTree(exception.getResponseBodyAsString());
            if (response != null) {
                JsonNode error = response.path("error");
                if (QUOTA_ERROR_CODES.contains(error.path("code").asText(""))
                        || "insufficient_quota".equals(error.path("type").asText(""))) {
                    return ErrorCode.AI_QUOTA_EXHAUSTED;
                }
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            // An unrecognized 429 body still means rate limited. Never log provider payloads.
        }
        return ErrorCode.AI_RATE_LIMITED;
    }
}
