package com.runab.api.service.diagnosis;

import com.runab.api.dto.policy.PolicyDiagnosisRequest;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import com.runab.api.service.matcher.MatchStatus;

import java.util.Map;
import java.util.Set;

/** Validates diagnosis form labels and turns ranges into comparable intervals. */
public class DiagnosisInputNormalizer {
    private static final Map<String, NumericBand> REVENUE_BANDS = Map.ofEntries(
            Map.entry("1,000만원 미만", new NumericBand(0, 9_999_999L, "1,000만원 미만")),
            Map.entry("1,000만원~5,000만원", new NumericBand(10_000_000L, 49_999_999L, "1,000만원~5,000만원")),
            Map.entry("5,000만원~1억원", new NumericBand(50_000_000L, 99_999_999L, "5,000만원~1억원")),
            Map.entry("1억원~3억원", new NumericBand(100_000_000L, 299_999_999L, "1억원~3억원")),
            Map.entry("3억원~5억원", new NumericBand(300_000_000L, 499_999_999L, "3억원~5억원")),
            Map.entry("5억원 이상", new NumericBand(500_000_000L, null, "5억원 이상")),
            Map.entry("5억 이상", new NumericBand(500_000_000L, null, "5억원 이상"))
    );
    private static final Map<String, NumericBand> EMPLOYEE_BANDS = Map.of(
            "없음 (나 혼자)", new NumericBand(0, 0L, "없음 (나 혼자)"),
            "1~4명", new NumericBand(1, 4L, "1~4명"),
            "5~9명", new NumericBand(5, 9L, "5~9명"),
            "10~29명", new NumericBand(10, 29L, "10~29명"),
            "30명 이상", new NumericBand(30, null, "30명 이상")
    );
    private static final Set<String> REGIONS = Set.of(
            "서울특별시", "부산광역시", "대구광역시", "인천광역시", "광주광역시", "대전광역시", "울산광역시",
            "세종특별자치시", "경기도", "강원도", "충청북도", "충청남도", "전라북도", "전라남도",
            "경상북도", "경상남도", "제주특별자치도"
    );
    private static final Map<String, String> INDUSTRIES = Map.ofEntries(
            Map.entry("음식점업", "음식점업"), Map.entry("음식점업 (한식·양식·중식 등)", "음식점업"),
            Map.entry("음식점", "음식점업"), Map.entry("카페/음료", "카페/음료"), Map.entry("카페/베이커리", "카페/음료"),
            Map.entry("의류/패션", "의류/패션"), Map.entry("뷰티/미용", "뷰티/미용"), Map.entry("교육/학원", "교육/학원"),
            Map.entry("교육", "교육/학원"), Map.entry("IT/소프트웨어", "IT/소프트웨어"), Map.entry("제조업", "제조업"),
            Map.entry("제조", "제조업"), Map.entry("도소매업", "도소매업"), Map.entry("도소매", "도소매업"),
            Map.entry("기타", "기타")
    );

    public NumericBand revenueBand(String label) {
        return band(REVENUE_BANDS, label);
    }

    public NumericBand employeeBand(String label) {
        return band(EMPLOYEE_BANDS, label);
    }

    /** Normalize a validated diagnosis request into canonical business values. */
    public NormalizedBusiness normalize(PolicyDiagnosisRequest request) {
        if (request == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        Boolean operating = request.getBusinessStatus();
        String industry = request.getIndustry();
        String region = request.getRegion();
        String revenue = request.getAnnualRevenueBand();
        String employees = request.getEmployeeCountBand();
        if (operating == null || blank(industry) || blank(region)) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        if (!REGIONS.contains(region)) throw new BusinessException(ErrorCode.INVALID_REGION);
        if (!INDUSTRIES.containsKey(industry)) throw new BusinessException(ErrorCode.INVALID_JOB_CATEGORY);
        if (!operating && (!blank(revenue) || !blank(employees))) throw new BusinessException(ErrorCode.BUSINESS_FIELD_NOT_ALLOWED);
        if (operating && (blank(revenue) || blank(employees))) throw new BusinessException(ErrorCode.BUSINESS_FIELD_REQUIRED);
        return new NormalizedBusiness(operating, INDUSTRIES.get(industry), region,
                operating ? revenueBand(revenue) : null,
                operating ? employeeBand(employees) : null);
    }

    private static NumericBand band(Map<String, NumericBand> bands, String label) {
        if (blank(label) || !bands.containsKey(label)) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        return bands.get(label);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record NumericBand(long minInclusive, Long maxInclusive, String label) {
        public MatchStatus compareToMaximum(Long policyMaximum) {
            if (policyMaximum == null) return MatchStatus.UNKNOWN;
            if (minInclusive > policyMaximum) return MatchStatus.FAIL;
            if (maxInclusive == null || maxInclusive > policyMaximum) return MatchStatus.PARTIAL;
            return MatchStatus.PASS;
        }
    }

    public record NormalizedBusiness(boolean operating, String industry, String region,
                                     NumericBand annualRevenue, NumericBand employeeCount) {}
}
