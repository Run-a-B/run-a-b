package com.runab.api.service.matcher;

import java.util.HashMap;
import java.util.Map;

/**
 * 업종 동의어 매핑 (RegionHierarchy와 동일 패턴).
 *
 * 배윤성 extractor(seed_policy_card_from_policy_v2.py)가 policy_card.full_json의 eligibility.industries에
 * 뽑아 넣는 업종 이름표(INDUSTRY_RULES 키)와, 우리 앱 드롭다운(Policies.tsx INDUSTRIES / BusinessInfo.jobCategory)의
 * 업종 이름표가 서로 달라서(미용업↔뷰티/미용, 정보통신업↔IT/소프트웨어 등) 단순 문자열 비교로는 매칭이 안 된다.
 * → 같은 업종을 가리키는 이름들을 하나의 "그룹"으로 묶어, 그룹이 같으면 동일 업종으로 간주한다.
 *
 * 외부 API 호출 없이 고정된 매핑(변하지 않는 사실)으로 처리 — 계산 비용 없음.
 */
public final class IndustryHierarchy {

    private IndustryHierarchy() {}

    // 별칭(extractor 이름표 + 앱 드롭다운 이름표) → 그룹 대표 토큰.
    // 같은 그룹에 속하면 동일 업종으로 판단한다.
    //
    // 주의: extractor 전용 값 중 우리 8개 드롭다운에 대응이 없는 것(운수업/숙박업/바이오산업/농식품업/콘텐츠산업/관광업)은
    //       일부러 매핑하지 않는다 — 억지로 끼워맞추지 않고, 자기 자신하고만 일치(=우리 드롭다운과는 불일치)하도록 둔다.
    private static final Map<String, String> ALIAS_TO_GROUP = buildAliasMap();

    private static Map<String, String> buildAliasMap() {
        Map<String, String> m = new HashMap<>();
        // extractor "음식점업" 규칙에 "카페" 키워드가 포함돼 있어, 음식점업 카드는 카페/음료 사용자에게도 매칭돼야 함
        group(m, "음식점업", "음식점업", "카페/음료");
        group(m, "미용업", "미용업", "뷰티/미용");
        group(m, "교육서비스업", "교육서비스업", "교육/학원");
        group(m, "정보통신업", "정보통신업", "IT/소프트웨어");
        // 이름이 정확히 같은 것도 명시(가독성용 — equals로도 처리되지만 그룹 목록을 한눈에 보이게)
        group(m, "제조업", "제조업");
        group(m, "도소매업", "도소매업");
        return m;
    }

    private static void group(Map<String, String> map, String token, String... aliases) {
        for (String a : aliases) {
            map.put(a, token);
        }
    }

    /** 이름을 정규화 그룹 토큰으로 변환. 매핑에 없으면 자기 자신(trim). */
    private static String canonical(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        return ALIAS_TO_GROUP.getOrDefault(trimmed, trimmed);
    }

    /**
     * 사용자 업종(드롭다운 값)과 정책 카드 업종(extractor 이름표)이 같은 업종인지 판단.
     * 완전 동일하거나, 같은 동의어 그룹에 속하면 true.
     */
    public static boolean sameIndustry(String userIndustry, String cardIndustry) {
        if (userIndustry == null || cardIndustry == null) return false;
        if (userIndustry.equals(cardIndustry)) return true;
        String uc = canonical(userIndustry);
        String cc = canonical(cardIndustry);
        return uc != null && uc.equals(cc);
    }
}
