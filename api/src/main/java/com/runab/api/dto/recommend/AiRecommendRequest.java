package com.runab.api.dto.recommend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * AI Gateway로 보낼 추천 요청 최상위 DTO.
 * candidate_policies는 PolicyCard.fullJson(파싱) + match 결과를 합친 JSON 노드 리스트.
 * (전송은 다음 단계 — 이번엔 조립까지만)
 */
@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AiRecommendRequest {

    private String requestId;            // "req_yyyyMMdd_xxxx"
    private LocalDate referenceDate;     // 오늘
    private int topN;                    // 5
    private PipelineDto pipeline;
    private UserBusinessInfoDto userBusinessInfo;
    private List<JsonNode> candidatePolicies;
    private OutputRequirementsDto outputRequirements;
}
