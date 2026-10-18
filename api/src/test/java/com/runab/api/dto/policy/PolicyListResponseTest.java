package com.runab.api.dto.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runab.api.entity.Policy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyListResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("사업정보 비교를 하지 않은 목록 응답은 관련도를 null로 구분한다")
    void anonymousResponse_hasNoRelevanceScore() {
        Policy policy = Policy.builder()
                .externalId("PBLN_TEST")
                .title("테스트 공고")
                .build();

        PolicyListResponse response = PolicyListResponse.from(policy);

        assertThat(response.getRelevance()).isNull();
    }

    @Test
    @DisplayName("AI 추천 여부는 프론트 계약 키로 직렬화한다")
    void aiRecommendationFlag_usesCanonicalJsonProperty() {
        Policy policy = Policy.builder()
                .externalId("PBLN_TEST")
                .title("테스트 공고")
                .build();

        PolicyListResponse response = PolicyListResponse.from(policy, 90, true);

        JsonNode json = objectMapper.valueToTree(response);
        assertThat(json.path("isAIRecommended").asBoolean()).isTrue();
        assertThat(json.has("airecommended")).isFalse();
    }
}
