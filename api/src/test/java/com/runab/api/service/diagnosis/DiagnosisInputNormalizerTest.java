package com.runab.api.service.diagnosis;

import com.runab.api.dto.policy.PolicyDiagnosisRequest;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import com.runab.api.service.matcher.MatchStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnosisInputNormalizerTest {

    @Test
    void revenueBandCanPassPartiallyOverlapOrFailAMaximum() {
        var normalizer = new DiagnosisInputNormalizer();
        assertThat(normalizer.revenueBand("1,000만원~5,000만원").compareToMaximum(50_000_000L))
                .isEqualTo(MatchStatus.PASS);
        assertThat(normalizer.revenueBand("5,000만원~1억원").compareToMaximum(75_000_000L))
                .isEqualTo(MatchStatus.PARTIAL);
        assertThat(normalizer.revenueBand("1억원~3억원").compareToMaximum(90_000_000L))
                .isEqualTo(MatchStatus.FAIL);
    }

    @Test
    void openEndedEmployeeBandPartiallyOverlapsOrFails() {
        var normalizer = new DiagnosisInputNormalizer();
        assertThat(normalizer.employeeBand("30명 이상").compareToMaximum(50L))
                .isEqualTo(MatchStatus.PARTIAL);
        assertThat(normalizer.employeeBand("30명 이상").compareToMaximum(20L))
                .isEqualTo(MatchStatus.FAIL);
        assertThat(normalizer.employeeBand("30명 이상").compareToMaximum(null)).isEqualTo(MatchStatus.UNKNOWN);
        assertThat(normalizer.revenueBand("1,000만원 미만").compareToMaximum(9_999_999L)).isEqualTo(MatchStatus.PASS);
        assertThat(normalizer.revenueBand("5억원 이상").compareToMaximum(500_000_000L)).isEqualTo(MatchStatus.PARTIAL);
    }

    @Test
    void normalizeMapsAliasesAndReturnsBands() {
        var result = new DiagnosisInputNormalizer().normalize(PolicyDiagnosisRequest.builder()
                .businessStatus(true).industry("카페/베이커리").region("서울특별시")
                .annualRevenueBand("5억 이상").employeeCountBand("30명 이상").build());
        assertThat(result.industry()).isEqualTo("카페/음료");
        assertThat(result.annualRevenue().minInclusive()).isEqualTo(500_000_000L);
        assertThat(result.employeeCount().maxInclusive()).isNull();
    }

    @Test
    void normalizeAcceptsEveryIndustryAliasAndCanonicalChoice() {
        var normalizer = new DiagnosisInputNormalizer();
        var expected = new String[][]{{"음식점업 (한식·양식·중식 등)", "음식점업"}, {"음식점", "음식점업"},
                {"카페/베이커리", "카페/음료"}, {"교육", "교육/학원"}, {"제조", "제조업"}, {"도소매", "도소매업"},
                {"기타", "기타"}, {"IT/소프트웨어", "IT/소프트웨어"}};
        for (var pair : expected) {
            var result = normalizer.normalize(PolicyDiagnosisRequest.builder().businessStatus(false)
                    .industry(pair[0]).region("서울특별시").build());
            assertThat(result.industry()).isEqualTo(pair[1]);
        }
    }

    @Test
    void normalizeRejectsMissingOrDisallowedBusinessFields() {
        var normalizer = new DiagnosisInputNormalizer();
        var base = PolicyDiagnosisRequest.builder().businessStatus(true).industry("기타").region("서울특별시").build();
        assertThatThrownBy(() -> normalizer.normalize(base)).extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_FIELD_REQUIRED);
        var preparing = base.toBuilder().businessStatus(false).annualRevenueBand("1,000만원 미만").build();
        assertThatThrownBy(() -> normalizer.normalize(preparing)).extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_FIELD_NOT_ALLOWED);
    }

    @Test
    void normalizeRejectsInvalidIndustryRegionAndRanges() {
        var normalizer = new DiagnosisInputNormalizer();
        var base = PolicyDiagnosisRequest.builder().businessStatus(true).industry("없는 업종").region("서울특별시")
                .annualRevenueBand("1,000만원 미만").employeeCountBand("없음 (나 혼자)").build();
        assertThatThrownBy(() -> normalizer.normalize(base)).extracting("errorCode").isEqualTo(ErrorCode.INVALID_JOB_CATEGORY);
        assertThatThrownBy(() -> normalizer.normalize(base.toBuilder().industry("기타").region("없는 지역").build()))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REGION);
        assertThatThrownBy(() -> normalizer.revenueBand("5억원~3억원")).extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> normalizer.employeeBand("3~1명")).extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void everyRevenueAndEmployeeLabelHasItsExactInterval() {
        var normalizer = new DiagnosisInputNormalizer();
        assertBand(normalizer.revenueBand("1,000만원 미만"), 0, 9_999_999L);
        assertBand(normalizer.revenueBand("1,000만원~5,000만원"), 10_000_000L, 49_999_999L);
        assertBand(normalizer.revenueBand("5,000만원~1억원"), 50_000_000L, 99_999_999L);
        assertBand(normalizer.revenueBand("1억원~3억원"), 100_000_000L, 299_999_999L);
        assertBand(normalizer.revenueBand("3억원~5억원"), 300_000_000L, 499_999_999L);
        assertBand(normalizer.revenueBand("5억원 이상"), 500_000_000L, null);
        assertBand(normalizer.employeeBand("없음 (나 혼자)"), 0, 0L);
        assertBand(normalizer.employeeBand("1~4명"), 1, 4L);
        assertBand(normalizer.employeeBand("5~9명"), 5, 9L);
        assertBand(normalizer.employeeBand("10~29명"), 10, 29L);
        assertBand(normalizer.employeeBand("30명 이상"), 30, null);
    }

    @Test
    void everyCurrentFormIndustryLabelNormalizesToCanonicalValue() {
        var normalizer = new DiagnosisInputNormalizer();
        var labels = new String[][]{
                {"음식점", "음식점업"}, {"카페/베이커리", "카페/음료"}, {"의류/패션", "의류/패션"},
                {"뷰티/미용", "뷰티/미용"}, {"교육", "교육/학원"}, {"IT/소프트웨어", "IT/소프트웨어"},
                {"제조", "제조업"}, {"도소매", "도소매업"}, {"기타", "기타"},
                {"음식점업 (한식·양식·중식 등)", "음식점업"}, {"카페/음료", "카페/음료"},
                {"교육/학원", "교육/학원"}, {"제조업", "제조업"}, {"도소매업", "도소매업"}, {"음식점업", "음식점업"}
        };
        for (var label : labels) {
            var result = normalizer.normalize(PolicyDiagnosisRequest.builder().businessStatus(false)
                    .industry(label[0]).region("서울특별시").build());
            assertThat(result.industry()).isEqualTo(label[1]);
        }
    }

    private static void assertBand(DiagnosisInputNormalizer.NumericBand band, long min, Long max) {
        assertThat(band.minInclusive()).isEqualTo(min);
        assertThat(band.maxInclusive()).isEqualTo(max);
    }
}
