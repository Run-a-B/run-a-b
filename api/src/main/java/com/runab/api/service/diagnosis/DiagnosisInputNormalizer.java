package com.runab.api.service.diagnosis;

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
            Map.entry("5억원 이상", new NumericBand(500_000_000L, null, "5억원 이상"))
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
    private static final Set<String> INDUSTRIES = Set.of(
            "음식점업", "카페/음료", "의류/패션", "뷰티/미용", "교육/학원", "IT/소프트웨어", "제조업", "도소매업"
    );

    public NumericBand revenueBand(String label) {
        return band(REVENUE_BANDS, label);
    }

    public NumericBand employeeBand(String label) {
        return band(EMPLOYEE_BANDS, label);
    }

    /**
     * Accepts the diagnosis request DTO without coupling this boundary to its package
     * during the independently delivered task. The DTO exposes standard bean getters.
     */
    public NormalizedBusiness normalize(Object request) {
        if (request == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        Boolean operating = property(request, "getBusinessStatus", Boolean.class);
        String industry = property(request, "getIndustry", String.class);
        String region = property(request, "getRegion", String.class);
        String revenue = property(request, "getAnnualRevenueBand", String.class);
        String employees = property(request, "getEmployeeCountBand", String.class);
        if (operating == null || blank(industry) || blank(region)) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        if (!REGIONS.contains(region)) throw new BusinessException(ErrorCode.INVALID_REGION);
        if (!INDUSTRIES.contains(industry)) throw new BusinessException(ErrorCode.INVALID_JOB_CATEGORY);
        if (!operating && (!blank(revenue) || !blank(employees))) throw new BusinessException(ErrorCode.BUSINESS_FIELD_NOT_ALLOWED);
        if (operating && (blank(revenue) || blank(employees))) throw new BusinessException(ErrorCode.BUSINESS_FIELD_REQUIRED);
        return new NormalizedBusiness(operating, industry, region,
                operating ? revenueBand(revenue) : null,
                operating ? employeeBand(employees) : null);
    }

    private static NumericBand band(Map<String, NumericBand> bands, String label) {
        if (blank(label) || !bands.containsKey(label)) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        return bands.get(label);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static <T> T property(Object bean, String getter, Class<T> type) {
        try { return type.cast(bean.getClass().getMethod(getter).invoke(bean)); }
        catch (ReflectiveOperationException e) { throw new BusinessException(ErrorCode.VALIDATION_ERROR); }
    }

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
