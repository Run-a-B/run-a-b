package com.runab.api.service.matcher;

import com.runab.api.entity.Policy;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PolicyCard 업종 정보가 없을 때만 쓰는 보수적인 텍스트 fallback.
 * 지원 주제와 업종을 혼동하지 않도록 업종성이 명확한 표현만 사용한다.
 */
public final class PolicyIndustryFallbackMatcher {

    private static final Map<String, List<String>> KEYWORDS = Map.of(
            "음식점업", List.of("음식점업", "외식업", "외식 업체", "외식업체", "식품접객업", "식당 사업자"),
            "카페/음료", List.of("카페", "커피전문점", "음료업", "제과점", "제빵업", "베이커리", "디저트점"),
            "의류/패션", List.of("의류업", "의류 기업", "패션기업", "패션 기업", "봉제업", "섬유기업", "신발업", "가죽제품"),
            "뷰티/미용", List.of("미용업", "미용 사업자", "뷰티기업", "뷰티 기업", "화장품기업", "화장품 기업", "헤어샵", "네일샵"),
            "교육/학원", List.of("학원", "교습", "교육기관", "교육기업", "교육 기업", "교육서비스업", "교육서비스 기업", "에듀테크"),
            "IT/소프트웨어", List.of(
                    "소프트웨어기업", "소프트웨어 기업", "소프트웨어 개발사", "소프트웨어 사업자",
                    "SW기업", "SW 기업", "SW사업자", "SW 사업자",
                    "정보통신업", "정보통신 기업", "정보통신 사업자",
                    "ICT기업", "ICT 기업", "ICT사업자", "ICT 사업자",
                    "IT기업", "IT 기업", "인공지능기업", "인공지능 기업",
                    "AI기업", "AI 기업", "클라우드 기업"),
            "제조업", List.of("제조업", "제조기업", "제조 기업", "제조 중소기업", "생산기업", "생산 기업", "스마트공장", "뿌리기업", "공장 자동화", "금형업"),
            "도소매업", List.of("도소매업", "도소매 사업자", "소매업", "도매업", "유통업", "유통기업", "유통 기업", "전통시장 상인", "전자상거래업", "온라인몰 사업자")
    );

    private PolicyIndustryFallbackMatcher() {
    }

    public static boolean matches(Policy policy, String selectedIndustry) {
        if (policy == null || selectedIndustry == null || selectedIndustry.isBlank()
                || "전체 업종".equals(selectedIndustry)) {
            return true;
        }

        String storedIndustry = policy.getIndustry();
        if (storedIndustry != null && !storedIndustry.isBlank() && !"전체".equals(storedIndustry)) {
            return IndustryHierarchy.sameIndustry(selectedIndustry, storedIndustry);
        }

        String text = String.join(" ",
                value(policy.getTitle()),
                value(policy.getTargetGroup()),
                value(policy.getDescription())).toLowerCase(Locale.ROOT);

        return KEYWORDS.getOrDefault(selectedIndustry, List.of()).stream()
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(text::contains);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
