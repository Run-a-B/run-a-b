package com.runab.api.dto.policy;

import com.runab.api.entity.Policy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyListResponseTest {

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
}
