package com.runab.api.service;

import com.runab.api.entity.Policy;
import com.runab.api.entity.PolicyCard;
import com.runab.api.repository.BusinessInfoRepository;
import com.runab.api.repository.PolicyCardRepository;
import com.runab.api.repository.PolicyRepository;
import com.runab.api.service.matcher.EligibilityMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PolicyServiceDetailTest {

    @Mock private PolicyRepository policyRepository;
    @Mock private BusinessInfoRepository businessInfoRepository;
    @Mock private PolicyCardRepository policyCardRepository;
    @Mock private EligibilityMatcher eligibilityMatcher;

    private PolicyService service;

    @BeforeEach
    void setUp() {
        service = new PolicyService(
                policyRepository,
                businessInfoRepository,
                policyCardRepository,
                eligibilityMatcher);
    }

    @Test
    void detailIncludesTheStoredCardExtractionStatus() {
        Policy policy = Policy.builder().externalId("PBLN_TEST").title("테스트 정책").build();
        ReflectionTestUtils.setField(policy, "id", 42L);
        PolicyCard card = PolicyCard.builder()
                .policyId(PolicyCard.toPolicyId("PBLN_TEST"))
                .externalId("PBLN_TEST")
                .extractionStatus("extracted")
                .build();
        given(policyRepository.findById(42L)).willReturn(Optional.of(policy));
        given(policyCardRepository.findByPolicyId(PolicyCard.toPolicyId("PBLN_TEST")))
                .willReturn(Optional.of(card));

        var result = service.getPolicyDetail(42L);

        assertThat(result.getExtractionStatus()).isEqualTo("extracted");
    }

    @Test
    void detailLeavesExtractionStatusUnknownWhenThereIsNoCard() {
        Policy policy = Policy.builder().externalId("PBLN_TEST").title("테스트 정책").build();
        ReflectionTestUtils.setField(policy, "id", 42L);
        given(policyRepository.findById(42L)).willReturn(Optional.of(policy));
        given(policyCardRepository.findByPolicyId(PolicyCard.toPolicyId("PBLN_TEST")))
                .willReturn(Optional.empty());

        assertThat(service.getPolicyDetail(42L).getExtractionStatus()).isNull();
    }
}
