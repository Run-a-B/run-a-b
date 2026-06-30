package com.runab.api.service.recommend;

import com.fasterxml.jackson.databind.JsonNode;
import com.runab.api.dto.recommend.AiRecommendRequest;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * AI Gateway(RunPod)로 추천 요청을 POST하고 응답 JSON을 그대로 받아온다.
 * 응답 스키마(recommended_policies / business_impact_report / preparation_order /
 * document_checklist_backend_payload)는 AI가 생성 → 백엔드는 JsonNode로 받아 전달.
 */
@Slf4j
@Component
public class AiGatewayClient {

    private final RestClient restClient;
    private final String apiKey;

    public AiGatewayClient(
            @Value("${ai.gateway.base-url}") String baseUrl,
            @Value("${ai.gateway.api-key}") String apiKey,
            @Value("${ai.gateway.timeout-seconds:180}") int timeoutSeconds
    ) {
        this.apiKey = apiKey;

        // 추론이 느려서(시연용 Transformers 직접 로드) 타임아웃을 넉넉히 잡는다.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();

        log.info("[AiGateway] 초기화 완료 - baseUrl={}, timeout={}s", baseUrl, timeoutSeconds);
    }

    /** 조립된 요청을 AI Gateway로 전송하고 원본 응답(JsonNode)을 반환 */
    public JsonNode recommend(AiRecommendRequest request) {
        try {
            log.info("[AiGateway] 추천 요청 전송 - requestId={}, 후보 {}건",
                    request.getRequestId(), request.getCandidatePolicies().size());

            JsonNode response = restClient.post()
                    .uri("/v1/policy/recommendations")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);

            log.info("[AiGateway] 추천 응답 수신 완료 - requestId={}", request.getRequestId());
            return response;

        } catch (Exception e) {
            log.error("[AiGateway] 추천 요청 실패 - requestId={}, error={}",
                    request.getRequestId(), e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }
}