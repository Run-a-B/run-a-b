package com.runab.api.service.checklist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runab.api.dto.policy.PolicyChecklistResponse;
import com.runab.api.entity.Policy;
import com.runab.api.entity.PolicyCard;
import com.runab.api.repository.PolicyCardRepository;
import com.runab.api.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PolicyChecklistServiceTest {

    @Mock
    private PolicyRepository policyRepository;
    @Mock
    private PolicyCardRepository policyCardRepository;

    private PolicyChecklistService service;

    @BeforeEach
    void setUp() {
        service = new PolicyChecklistService(policyRepository, policyCardRepository, new ObjectMapper());
    }

    @Test
    void missingCardReturnsRecommendationsNotRequiredDocuments() {
        given(policyRepository.findById(1L)).willReturn(Optional.of(policyWithoutDates()));
        given(policyCardRepository.findByPolicyId(anyString())).willReturn(Optional.empty());

        var result = service.getChecklist(1L);

        assertThat(result.getApplicationPeriod()).isEqualTo("기간 정보 없음 — 원문 공고 확인");
        assertThat(result.getApplicationChecklist()).allMatch(item -> !item.isRequired());
        assertThat(result.getApplicationChecklist())
                .extracting(PolicyChecklistResponse.ChecklistItem::getDescription)
                .allMatch(description -> description.contains("일반적으로 준비하면 좋은 서류"));
    }

    @Test
    void requiredFlagsAreNotMisrepresentedAsDocuments() {
        Policy policy = policyWithoutDates();
        PolicyCard card = PolicyCard.builder()
                .policyId(PolicyCard.toPolicyId("PBLN_TEST"))
                .externalId("PBLN_TEST")
                .extractionStatus("extracted")
                .fullJson("""
                        {"eligibility": {
                          "documents": ["매출 증빙"],
                          "required_flags": ["국세 완납"]
                        }}
                        """)
                .build();
        given(policyRepository.findById(1L)).willReturn(Optional.of(policy));
        given(policyCardRepository.findByPolicyId(PolicyCard.toPolicyId("PBLN_TEST")))
                .willReturn(Optional.of(card));

        var result = service.getChecklist(1L);

        assertThat(result.getApplicationChecklist())
                .extracting(PolicyChecklistResponse.ChecklistItem::getLabel)
                .contains("매출 증빙")
                .doesNotContain("국세 완납");
        assertThat(result.getApplicationChecklist()).allMatch(item -> !item.isRequired());
        assertThat(result.getApplicationChecklist())
                .filteredOn(item -> item.getLabel().equals("매출 증빙"))
                .extracting(PolicyChecklistResponse.ChecklistItem::getDescription)
                .containsExactly("공고문에 언급된 서류예요. 필수 여부는 원문을 확인해 주세요.");
    }

    private Policy policyWithoutDates() {
        Policy policy = Policy.builder()
                .externalId("PBLN_TEST")
                .title("테스트 정책")
                .category("경영")
                .region("전국")
                .industry("전체")
                .description("테스트 설명")
                .agency("테스트 기관")
                .detailUrl("https://www.bizinfo.go.kr/test")
                .sourceSystem("bizinfo")
                .build();
        ReflectionTestUtils.setField(policy, "id", 1L);
        return policy;
    }
}
