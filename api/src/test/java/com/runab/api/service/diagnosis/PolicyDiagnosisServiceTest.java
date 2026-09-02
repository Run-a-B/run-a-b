package com.runab.api.service.diagnosis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runab.api.dto.policy.PolicyDiagnosisRequest;
import com.runab.api.dto.policy.PolicyDiagnosisResponse;
import com.runab.api.entity.Policy;
import com.runab.api.entity.PolicyCard;
import com.runab.api.repository.PolicyCardRepository;
import com.runab.api.repository.PolicyRepository;
import com.runab.api.service.matcher.MatchStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PolicyDiagnosisServiceTest {

    @Mock
    private PolicyRepository policyRepository;
    @Mock
    private PolicyCardRepository policyCardRepository;

    private PolicyDiagnosisService service;

    @BeforeEach
    void setUp() {
        service = new PolicyDiagnosisService(policyRepository, policyCardRepository,
                new ObjectMapper(), new DiagnosisInputNormalizer());
    }

    @Test
    void missingCardUsesMetadataButKeepsUnsupportedCriteriaUnknown() {
        given(policyRepository.findById(42L)).willReturn(Optional.of(policy(
                "전국", "전체", LocalDate.now().minusDays(1), LocalDate.now().plusDays(10))));
        given(policyCardRepository.findByPolicyId(anyString())).willReturn(Optional.empty());

        PolicyDiagnosisResponse result = service.diagnose(42L, operatingCafeInSeoul());

        assertThat(result.getVerdict()).isEqualTo("NEEDS_CONFIRMATION");
        assertThat(result.getConfidence()).isEqualTo("LOW");
        assertThat(result.getEvidence()).extracting(PolicyDiagnosisResponse.Evidence::getKey)
                .containsExactly("region", "industry", "date", "business_stage", "revenue", "employees");
        assertThat(evidence(result, "region").getStatus()).isEqualTo(MatchStatus.PASS);
        assertThat(evidence(result, "region").getSource()).isEqualTo("정책 목록의 지역 정보");
        assertThat(evidence(result, "revenue").getStatus()).isEqualTo(MatchStatus.UNKNOWN);
        assertThat(result.getDataQuality().isPolicyCardAvailable()).isFalse();
    }

    @Test
    void expiredPeriodIsNotEligible() {
        given(policyRepository.findById(42L)).willReturn(Optional.of(policy(
                "전국", "전체", LocalDate.now().minusDays(20), LocalDate.now().minusDays(1))));
        given(policyCardRepository.findByPolicyId(anyString())).willReturn(Optional.empty());

        PolicyDiagnosisResponse result = service.diagnose(42L, operatingCafeInSeoul());

        assertThat(result.getVerdict()).isEqualTo("NOT_ELIGIBLE");
        assertThat(result.isHardFail()).isTrue();
        assertThat(result.getHardFailReasons()).containsExactly("신청 기간이 종료되었어요.");
        assertThat(evidence(result, "date").getStatus()).isEqualTo(MatchStatus.FAIL);
    }

    @Test
    void futureMetadataStartIsPartialRatherThanClosed() {
        given(policyRepository.findById(42L)).willReturn(Optional.of(policy(
                "전국", null, LocalDate.now().plusDays(1), LocalDate.now().plusDays(10))));
        given(policyCardRepository.findByPolicyId(anyString())).willReturn(Optional.empty());

        PolicyDiagnosisResponse result = service.diagnose(42L, operatingCafeInSeoul());

        assertThat(result.isHardFail()).isFalse();
        assertThat(evidence(result, "date").getStatus()).isEqualTo(MatchStatus.PARTIAL);
    }

    @Test
    void missingPolicyUsesThePolicyNotFoundError() {
        given(policyRepository.findById(42L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.diagnose(42L, operatingCafeInSeoul()))
                .extracting("errorCode").isEqualTo(com.runab.api.exception.ErrorCode.POLICY_NOT_FOUND);
    }

    @Test
    void specificRegionMismatchIsNotEligible() {
        given(policyRepository.findById(42L)).willReturn(Optional.of(policy(
                "서울특별시", "전체", LocalDate.now().minusDays(1), LocalDate.now().plusDays(10))));
        given(policyCardRepository.findByPolicyId(anyString())).willReturn(Optional.empty());

        PolicyDiagnosisResponse result = service.diagnose(42L, operatingCafeInBusan());

        assertThat(result.getVerdict()).isEqualTo("NOT_ELIGIBLE");
        assertThat(result.isHardFail()).isTrue();
        assertThat(result.getHardFailReasons()).containsExactly("지역 조건이 맞지 않아요.");
        assertThat(evidence(result, "region").getStatus()).isEqualTo(MatchStatus.FAIL);
    }

    @Test
    void extractedMaximumProducesPartialRangeEvidence() {
        given(policyRepository.findById(42L)).willReturn(Optional.of(policy(
                "전국", "전체", LocalDate.now().minusDays(1), LocalDate.now().plusDays(10))));
        given(policyCardRepository.findByPolicyId(PolicyCard.toPolicyId("PBLN_TEST")))
                .willReturn(Optional.of(card("extracted", """
                        {
                          "application_period": {"status": "open"},
                          "eligibility": {
                            "regions": ["전국"], "region_condition_type": "nationwide",
                            "industries": ["전업종"], "industry_condition_type": "all",
                            "business_stages": ["운영중"],
                            "max_revenue": 75000000,
                            "max_employees": 4
                          }
                        }
                        """)));

        PolicyDiagnosisResponse result = service.diagnose(42L, operatingCafeInSeoul());

        PolicyDiagnosisResponse.Evidence revenue = evidence(result, "revenue");
        assertThat(revenue.getStatus()).isEqualTo(MatchStatus.PARTIAL);
        assertThat(revenue.getPolicyValue()).isEqualTo("7,500만원 이하");
        assertThat(revenue.getSource()).isEqualTo("구조화된 공고 조건");
    }

    @Test
    void malformedFailedAndPartialMissingCardsDoNotInventConditions() {
        Policy noConditions = policy(null, null, null, null);
        ReflectionTestUtils.setField(noConditions, "description", "");
        given(policyRepository.findById(42L)).willReturn(Optional.of(noConditions));
        given(policyCardRepository.findByPolicyId(anyString())).willReturn(Optional.of(card("extracted", "{bad json")));

        PolicyDiagnosisResponse malformed = service.diagnose(42L, operatingCafeInSeoul());

        assertThat(malformed.getVerdict()).isEqualTo("ON_HOLD");
        assertThat(malformed.getDataQuality().isPolicyCardAvailable()).isFalse();
        assertThat(evidence(malformed, "business_stage").getStatus()).isEqualTo(MatchStatus.UNKNOWN);

        given(policyCardRepository.findByPolicyId(anyString())).willReturn(Optional.of(card("failed", "{}")));
        PolicyDiagnosisResponse failed = service.diagnose(42L, operatingCafeInSeoul());
        assertThat(failed.getDataQuality().isPolicyCardAvailable()).isFalse();
        assertThat(evidence(failed, "revenue").getStatus()).isEqualTo(MatchStatus.UNKNOWN);

        given(policyCardRepository.findByPolicyId(anyString())).willReturn(Optional.of(card("partially_extracted", """
                {"eligibility":{"regions":["전국"],"region_condition_type":"nationwide"}}
                """)));
        PolicyDiagnosisResponse partial = service.diagnose(42L, operatingCafeInSeoul());
        assertThat(partial.getDataQuality().isPolicyCardAvailable()).isTrue();
        assertThat(evidence(partial, "revenue").getStatus()).isEqualTo(MatchStatus.UNKNOWN);
        assertThat(evidence(partial, "business_stage").getStatus()).isEqualTo(MatchStatus.UNKNOWN);
    }

    @Test
    void fullyExtractedCompleteMatchIsLikelyEligible() {
        given(policyRepository.findById(42L)).willReturn(Optional.of(policy(null, null, null, null)));
        given(policyCardRepository.findByPolicyId(anyString())).willReturn(Optional.of(card("extracted", """
                {
                  "application_period":{"status":"open"},
                  "eligibility": {
                    "regions":["전국"], "region_condition_type":"nationwide",
                    "industries":["전업종"], "industry_condition_type":"all",
                    "business_stages":["운영중"], "max_revenue":100000000, "max_employees":4
                  }
                }
                """)));

        PolicyDiagnosisResponse result = service.diagnose(42L, operatingCafeInSeoul());

        assertThat(result.getVerdict()).isEqualTo("LIKELY_ELIGIBLE");
        assertThat(result.getConfidence()).isEqualTo("HIGH");
        assertThat(result.getScore()).isEqualTo(100);
        assertThat(result.getUnknownConditions()).isEmpty();
    }

    @Test
    void fullyExtractedMissingConditionIsExplicitlyNotRequired() {
        given(policyRepository.findById(42L)).willReturn(Optional.of(policy(null, null, null, null)));
        given(policyCardRepository.findByPolicyId(anyString())).willReturn(Optional.of(card("extracted", """
                {"eligibility":{"regions":["전국"],"region_condition_type":"nationwide"}}
                """)));

        PolicyDiagnosisResponse result = service.diagnose(42L, operatingCafeInSeoul());

        assertThat(evidence(result, "revenue").getStatus()).isEqualTo(MatchStatus.NOT_REQUIRED);
        assertThat(evidence(result, "revenue").getPolicyValue()).isEqualTo("별도 조건 없음");
    }

    @Test
    void oneComparableMetadataFactLeavesScoreUnavailable() {
        Policy regionOnly = policy("전국", null, null, null);
        ReflectionTestUtils.setField(regionOnly, "description", "");
        given(policyRepository.findById(42L)).willReturn(Optional.of(regionOnly));
        given(policyCardRepository.findByPolicyId(anyString())).willReturn(Optional.empty());

        PolicyDiagnosisResponse result = service.diagnose(42L, operatingCafeInSeoul());

        assertThat(result.getScore()).isNull();
        assertThat(result.getVerdict()).isEqualTo("NEEDS_CONFIRMATION");
    }

    private Policy policy(String region, String industry, LocalDate start, LocalDate end) {
        Policy policy = Policy.builder()
                .externalId("PBLN_TEST")
                .title("테스트 정책")
                .category("경영")
                .region(region)
                .industry(industry)
                .description("카페 사업자를 지원하는 테스트 설명")
                .agency("테스트 기관")
                .applicationStartDate(start)
                .applicationEndDate(end)
                .targetGroup("소상공인")
                .detailUrl("https://www.bizinfo.go.kr/test")
                .sourceSystem("bizinfo")
                .build();
        ReflectionTestUtils.setField(policy, "id", 42L);
        return policy;
    }

    private PolicyCard card(String extractionStatus, String fullJson) {
        return PolicyCard.builder()
                .policyId(PolicyCard.toPolicyId("PBLN_TEST"))
                .externalId("unreliable-card-id")
                .applicationStatus("open")
                .extractionStatus(extractionStatus)
                .fullJson(fullJson)
                .build();
    }

    private PolicyDiagnosisRequest operatingCafeInSeoul() {
        return PolicyDiagnosisRequest.builder()
                .businessStatus(true)
                .industry("카페/음료")
                .region("서울특별시")
                .annualRevenueBand("5,000만원~1억원")
                .employeeCountBand("1~4명")
                .build();
    }

    private PolicyDiagnosisRequest operatingCafeInBusan() {
        return operatingCafeInSeoul().toBuilder().region("부산광역시").build();
    }

    private PolicyDiagnosisResponse.Evidence evidence(PolicyDiagnosisResponse result, String key) {
        return result.getEvidence().stream()
                .filter(item -> item.getKey().equals(key))
                .findFirst()
                .orElseThrow();
    }
}
