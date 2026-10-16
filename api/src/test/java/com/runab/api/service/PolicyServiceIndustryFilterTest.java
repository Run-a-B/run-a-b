package com.runab.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runab.api.dto.policy.PolicyPageResponse;
import com.runab.api.entity.BusinessInfo;
import com.runab.api.entity.Policy;
import com.runab.api.entity.PolicyCard;
import com.runab.api.repository.BusinessInfoRepository;
import com.runab.api.repository.PolicyCardRepository;
import com.runab.api.repository.PolicyRepository;
import com.runab.api.service.matcher.EligibilityMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyServiceIndustryFilterTest {

    @Mock
    private PolicyRepository policyRepository;
    @Mock
    private BusinessInfoRepository businessInfoRepository;
    @Mock
    private PolicyCardRepository policyCardRepository;

    private PolicyService policyService;

    @BeforeEach
    void setUp() {
        policyService = new PolicyService(
                policyRepository,
                businessInfoRepository,
                policyCardRepository,
                new EligibilityMatcher(new ObjectMapper()));
    }

    @Test
    @DisplayName("정책카드가 없으면 명확한 정책 본문을 이용해 업종 필터를 적용한다")
    void filtersByPolicyTextWhenPolicyCardsAreMissing() {
        Policy restaurantPolicy = policy(
                "PBLN_RESTAURANT",
                "외식업 소상공인 시설 개선 지원",
                "음식점업 사업자의 주방 환경 개선을 지원합니다.");
        Policy softwarePolicy = policy(
                "PBLN_SOFTWARE",
                "소프트웨어 기업 기술개발 지원",
                "정보통신업 기업의 서비스 개발을 지원합니다.");

        when(policyRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(restaurantPolicy, softwarePolicy));
        when(policyCardRepository.findByPolicyIdIn(anyCollection())).thenReturn(List.of());

        PolicyPageResponse response = policyService.getPolicies(
                "전체 지역", "음식점업", "전체 카테고리", null,
                "최신순", 0, 12, null);

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getPolicies())
                .extracting(policy -> policy.getTitle())
                .containsExactly("외식업 소상공인 시설 개선 지원");
    }

    @Test
    @DisplayName("정책카드의 명시적 업종 판정은 본문 fallback보다 우선한다")
    void prioritizesPolicyCardIndustryOverTextFallback() {
        Policy textMatchesButCardFails = policy(
                "PBLN_CARD_FAIL",
                "외식업체 시설 개선 지원",
                "음식점업 사업자를 지원합니다.");
        Policy textMissesButCardPasses = policy(
                "PBLN_CARD_PASS",
                "창업기업 운영자금 지원",
                "초기 사업자의 안정적인 운영을 지원합니다.");

        PolicyCard failCard = card("PBLN_CARD_FAIL", "정보통신업");
        PolicyCard passCard = card("PBLN_CARD_PASS", "음식점업");

        when(policyRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(textMatchesButCardFails, textMissesButCardPasses));
        when(policyCardRepository.findByPolicyIdIn(anyCollection()))
                .thenReturn(List.of(failCard, passCard));

        PolicyPageResponse response = policyService.getPolicies(
                "전체 지역", "음식점업", "전체 카테고리", null,
                "최신순", 0, 12, null);

        assertThat(response.getPolicies())
                .extracting(policy -> policy.getTitle())
                .containsExactly("창업기업 운영자금 지원");
    }

    @Test
    @DisplayName("정책카드가 없어도 실제 정책 메타데이터에 따라 관련도가 달라진다")
    void scoresPoliciesFromTheirOwnMetadataWhenCardsAreMissing() {
        BusinessInfo business = BusinessInfo.builder()
                .businessStatus(true)
                .jobCategory("카페/음료")
                .region("서울특별시")
                .build();
        Policy matching = Policy.builder()
                .externalId("PBLN_MATCH")
                .title("전국 카페 사업자 지원")
                .region("전국")
                .industry("카페/음료")
                .publishedDate(LocalDate.now().minusDays(2))
                .applicationStartDate(LocalDate.now().minusDays(1))
                .applicationEndDate(LocalDate.now().plusDays(7))
                .build();
        Policy closedMismatch = Policy.builder()
                .externalId("PBLN_MISMATCH")
                .title("지역 제조기업 지원")
                .region("부산광역시")
                .industry("제조업")
                .publishedDate(LocalDate.now().minusDays(1))
                .applicationStartDate(LocalDate.now().minusDays(10))
                .applicationEndDate(LocalDate.now().minusDays(1))
                .build();

        when(businessInfoRepository.findByUserId(99L)).thenReturn(Optional.of(business));
        when(policyRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(closedMismatch, matching));
        when(policyCardRepository.findByPolicyIdIn(anyCollection())).thenReturn(List.of());

        PolicyPageResponse response = policyService.getPolicies(
                "전체 지역", "전체 업종", "전체 카테고리", null,
                "관련도 높은순", 0, 12, 99L);

        assertThat(response.getPolicies())
                .extracting(policy -> policy.getTitle())
                .containsExactly("전국 카페 사업자 지원", "지역 제조기업 지원");
        assertThat(response.getPolicies())
                .extracting(policy -> policy.getRelevance())
                .doesNotHaveDuplicates();
    }

    private Policy policy(String externalId, String title, String description) {
        return Policy.builder()
                .externalId(externalId)
                .title(title)
                .category("경영")
                .filterCategory("경영")
                .region("전국")
                .industry("전체")
                .description(description)
                .targetGroup("소상공인")
                .build();
    }

    private PolicyCard card(String externalId, String industry) {
        return PolicyCard.builder()
                .policyId(PolicyCard.toPolicyId(externalId))
                .externalId(externalId)
                .applicationStatus("open")
                .extractionStatus("extracted")
                .fullJson("""
                        {
                          "eligibility": {
                            "industries": ["%s"],
                            "industry_condition_type": "specific"
                          }
                        }
                        """.formatted(industry))
                .build();
    }
}
