package com.runab.api.service.matcher;

import com.runab.api.entity.Policy;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
            "교육/학원", List.of("학원", "학원업", "교습", "교육기관", "교육기업", "교육 기업", "교육서비스업", "교육서비스 기업", "에듀테크"),
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

    private static final List<String> EXCLUSIVE_INDUSTRIES = List.of(
            "운수업", "숙박업", "바이오산업", "농식품업", "콘텐츠산업", "관광업");

    private static final Pattern EXCLUSION_CONTEXT = Pattern.compile("제외|비대상|불가|아니|아님|않");
    private static final Pattern NON_TARGET_CONTEXT = Pattern.compile("제외|비대상|불가|아니|아님|않|무관|통계|동향|사례|참고");
    private static final Pattern TARGET_PREFIX = Pattern.compile("(?:지원|신청|참여|모집)\\s*대상\\s*[:：]?\\s*$");
    private static final Pattern TARGET_SUFFIX = Pattern.compile(
            "^\\s*(?:전용|대상|(?:사업자|기업|업체)?(?:을|를|에게)\\s*지원|(?:사업자|기업|업체)\\s*지원)");
    private static final Pattern TARGET_GROUP_SUFFIX = Pattern.compile("\\s*(?:사업자|기업|업체|소상공인|중소기업)?\\s*");
    private static final Pattern NOMINAL_SUPPORT_SUFFIX = Pattern.compile(
            "\\s+(?:[\\p{L}\\p{N}·/()-]+\\s+){0,4}(?:지원(?:사업)?|개선|향상|육성)\\s*");

    private PolicyIndustryFallbackMatcher() {
    }

    public static boolean matches(Policy policy, String selectedIndustry) {
        if (selectedIndustry == null || selectedIndustry.isBlank() || "전체 업종".equals(selectedIndustry)) {
            return true;
        }
        return evaluate(policy, selectedIndustry) == MatchStatus.PASS;
    }

    public static MatchStatus evaluate(Policy policy, String selectedIndustry) {
        if (policy == null || selectedIndustry == null || selectedIndustry.isBlank()) {
            return MatchStatus.UNKNOWN;
        }

        String storedIndustry = policy.getIndustry();
        if (storedIndustry != null && !storedIndustry.isBlank()
                && !"전체".equals(storedIndustry.trim()) && !"전체 업종".equals(storedIndustry.trim())) {
            return IndustryHierarchy.sameIndustry(selectedIndustry, storedIndustry)
                    ? MatchStatus.PASS : MatchStatus.FAIL;
        }

        List<String> selectedKeywords = KEYWORDS.entrySet().stream()
                .filter(entry -> IndustryHierarchy.sameIndustry(selectedIndustry, entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (selectedKeywords.stream().anyMatch(keyword ->
                hasExplicitExclusion(policy.getTargetGroup(), keyword)
                        || hasExplicitExclusion(policy.getTitle(), keyword)
                        || hasExplicitExclusion(policy.getDescription(), keyword))) {
            return MatchStatus.FAIL;
        }
        if (selectedKeywords.stream().anyMatch(keyword ->
                hasAffirmativeTarget(policy.getTargetGroup(), keyword, true)
                        || hasAffirmativeTarget(policy.getTitle(), keyword, false)
                        || hasAffirmativeTarget(policy.getDescription(), keyword, false))) {
            return MatchStatus.PASS;
        }
        if (Stream.concat(EXCLUSIVE_INDUSTRIES.stream(), KEYWORDS.values().stream().flatMap(List::stream))
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(keyword -> hasAffirmativeTarget(policy.getTargetGroup(), keyword, true)
                        || hasAffirmativeTarget(policy.getTitle(), keyword, false)
                        || hasAffirmativeTarget(policy.getDescription(), keyword, false))) {
            return MatchStatus.FAIL;
        }
        return MatchStatus.UNKNOWN;
    }

    private static boolean hasExplicitExclusion(String text, String keyword) {
        for (String part : value(text).toLowerCase(Locale.ROOT).split("[.!?;\\n,]")) {
            String clause = part.trim();
            if (clause.contains(keyword) && EXCLUSION_CONTEXT.matcher(clause).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAffirmativeTarget(String text, String keyword, boolean targetGroup) {
        for (String part : value(text).toLowerCase(Locale.ROOT).split("[.!?;\\n,]")) {
            String clause = part.trim();
            int index = clause.indexOf(keyword);
            if (index < 0 || NON_TARGET_CONTEXT.matcher(clause).find()) continue;

            String before = clause.substring(0, index);
            String after = clause.substring(index + keyword.length());
            // 대상 필드의 업종명, 명시적 대상/전용 표현, 업종으로 시작하는 명사형 지원 제목만 부정 근거로 사용한다.
            if ((targetGroup && TARGET_GROUP_SUFFIX.matcher(after).matches())
                    || TARGET_PREFIX.matcher(before).find()
                    || TARGET_SUFFIX.matcher(after).find()
                    || (index == 0 && NOMINAL_SUPPORT_SUFFIX.matcher(after).matches())) {
                return true;
            }
        }
        return false;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
