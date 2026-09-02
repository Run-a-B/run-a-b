package com.runab.api.dto.policy;

import com.runab.api.entity.Policy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyDetailResponseTest {

    @Test
    void exposesTheStoredSourceSystem() {
        Policy policy = Policy.builder()
                .externalId("PBLN_TEST")
                .title("테스트 정책")
                .sourceSystem("bizinfo")
                .build();

        assertThat(PolicyDetailResponse.from(policy).getSourceSystem()).isEqualTo("bizinfo");
    }

    @Test
    void exposesThePolicyCardExtractionStatus() {
        Policy policy = Policy.builder()
                .externalId("PBLN_TEST")
                .title("테스트 정책")
                .build();

        assertThat(PolicyDetailResponse.from(policy, "partially_extracted").getExtractionStatus())
                .isEqualTo("partially_extracted");
    }
}
