package com.runab.api.service.matcher;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 지역 매칭 시 "강남구" ⊂ "서울특별시" 같은 상하위 관계를 판단하기 위한 정적 매핑.
 * 사용자는 시/도 단위(REGIONS 드롭다운, 17개)만 선택하지만,
 * 정책 카드(policy_card)의 eligibility.regions는 원문 그대로라 구/군 단위로 추출되는 경우가 있음.
 * → 순수 문자열 비교("서울특별시" === "강남구")로는 매칭 실패하는 문제를 해결.
 *
 * 외부 API 호출 없이 고정된 행정구역 데이터(변하지 않는 사실)로 처리 — 계산 비용 없음.
 */
public final class RegionHierarchy {

    private RegionHierarchy() {}

    // 시/도 축약형("서울") → 정식명("서울특별시") 매핑. 정책 원문에 축약형이 쓰이는 경우 대응.
    private static final Map<String, String> PROVINCE_ALIAS = Map.ofEntries(
            Map.entry("서울", "서울특별시"),
            Map.entry("부산", "부산광역시"),
            Map.entry("대구", "대구광역시"),
            Map.entry("인천", "인천광역시"),
            Map.entry("광주", "광주광역시"),
            Map.entry("대전", "대전광역시"),
            Map.entry("울산", "울산광역시"),
            Map.entry("세종", "세종특별자치시"),
            Map.entry("경기", "경기도"),
            Map.entry("강원", "강원특별자치도"),
            Map.entry("충북", "충청북도"),
            Map.entry("충남", "충청남도"),
            Map.entry("전북", "전북특별자치도"),
            Map.entry("전남", "전라남도"),
            Map.entry("경북", "경상북도"),
            Map.entry("경남", "경상남도"),
            Map.entry("제주", "제주특별자치도")
    );

    // 구/군/시(기초자치단체) → 소속 시/도 정식명 집합.
    // 주의: "중구","남구","동구","서구","북구","강서구","고성군" 등 같은 이름의 기초단체가 여러 시/도에 존재한다.
    // 단일 값 Map으로 두면 마지막에 put된 시/도로 덮여, 예컨대 서울 강서구 정책이 부산으로 매핑돼 서울 사용자와 오매칭(FAIL)된다.
    // → 값을 Set으로 보관해 "그 구가 속할 수 있는 시/도 중 하나라도 겹치면 매칭"으로 판단(오탐 FAIL 방지).
    private static final Map<String, Set<String>> DISTRICT_TO_PROVINCES = buildDistrictMap();

    /** regionText가 속할 수 있는 시/도 정식명 집합 반환. 이미 시/도면 자기 자신, 매핑 불가면 빈 집합. */
    public static Set<String> provincesOf(String regionText) {
        if (regionText == null || regionText.isBlank()) return Collections.emptySet();
        String trimmed = regionText.trim();

        if (PROVINCE_ALIAS.containsValue(trimmed)) return Set.of(trimmed);          // 이미 정식 시/도명
        if (PROVINCE_ALIAS.containsKey(trimmed)) return Set.of(PROVINCE_ALIAS.get(trimmed)); // 축약형("서울")

        String district = trimmed;
        // "서울특별시 강남구" 처럼 붙어있는 경우 마지막 토큰(구/군/시)만 추출
        if (trimmed.contains(" ")) {
            String[] parts = trimmed.split("\\s+");
            district = parts[parts.length - 1];
        }
        return DISTRICT_TO_PROVINCES.getOrDefault(district, Collections.emptySet());
    }

    /** userRegion(시/도)과 policyRegion(시/도 또는 구/군)이 같은 시/도에 속할 수 있는지 판단 */
    public static boolean sameProvince(String userRegion, String policyRegion) {
        if (userRegion == null || policyRegion == null) return false;
        if (userRegion.equals(policyRegion)) return true;
        Set<String> userProvinces = provincesOf(userRegion);
        Set<String> policyProvinces = provincesOf(policyRegion);
        // 교집합이 하나라도 있으면 같은 시/도 소속 가능 → 매칭
        return userProvinces.stream().anyMatch(policyProvinces::contains);
    }

    private static Map<String, Set<String>> buildDistrictMap() {
        Map<String, Set<String>> m = new java.util.HashMap<>();
        put(m, "서울특별시", "강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구",
                "노원구", "도봉구", "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구", "성북구",
                "송파구", "양천구", "영등포구", "용산구", "은평구", "종로구", "중구", "중랑구");
        put(m, "부산광역시", "강서구", "금정구", "남구", "동구", "동래구", "부산진구", "북구", "사상구",
                "사하구", "서구", "수영구", "연제구", "영도구", "해운대구", "기장군");
        put(m, "대구광역시", "남구", "달서구", "달성군", "동구", "북구", "서구", "수성구", "군위군");
        put(m, "인천광역시", "계양구", "남동구", "동구", "미추홀구", "부평구", "서구", "연수구", "강화군", "옹진군");
        put(m, "광주광역시", "광산구", "남구", "동구", "북구", "서구");
        put(m, "대전광역시", "대덕구", "동구", "서구", "유성구", "중구");
        put(m, "울산광역시", "남구", "동구", "북구", "울주군");
        put(m, "경기도", "수원시", "성남시", "의정부시", "안양시", "부천시", "광명시", "평택시", "동두천시",
                "안산시", "고양시", "과천시", "구리시", "남양주시", "오산시", "시흥시", "군포시", "의왕시",
                "하남시", "용인시", "파주시", "이천시", "안성시", "김포시", "화성시", "광주시", "양주시",
                "포천시", "여주시", "연천군", "가평군", "양평군");
        put(m, "강원특별자치도", "춘천시", "원주시", "강릉시", "동해시", "태백시", "속초시", "삼척시",
                "홍천군", "횡성군", "영월군", "평창군", "정선군", "철원군", "화천군", "양구군", "인제군",
                "고성군", "양양군");
        put(m, "충청북도", "청주시", "충주시", "제천시", "보은군", "옥천군", "영동군", "증평군", "진천군",
                "괴산군", "음성군", "단양군");
        put(m, "충청남도", "천안시", "공주시", "보령시", "아산시", "서산시", "논산시", "계룡시", "당진시",
                "금산군", "부여군", "서천군", "청양군", "홍성군", "예산군", "태안군");
        put(m, "전북특별자치도", "전주시", "군산시", "익산시", "정읍시", "남원시", "김제시", "완주군",
                "진안군", "무주군", "장수군", "임실군", "순창군", "고창군", "부안군");
        put(m, "전라남도", "목포시", "여수시", "순천시", "나주시", "광양시", "담양군", "곡성군", "구례군",
                "고흥군", "보성군", "화순군", "장흥군", "강진군", "해남군", "영암군", "무안군", "함평군",
                "영광군", "장성군", "완도군", "진도군", "신안군");
        put(m, "경상북도", "포항시", "경주시", "김천시", "안동시", "구미시", "영주시", "영천시", "상주시",
                "문경시", "경산시", "의성군", "청송군", "영양군", "영덕군", "청도군", "고령군", "성주군",
                "칠곡군", "예천군", "봉화군", "울진군", "울릉군");
        put(m, "경상남도", "창원시", "진주시", "통영시", "사천시", "김해시", "밀양시", "거제시", "양산시",
                "의령군", "함안군", "창녕군", "고성군", "남해군", "하동군", "산청군", "함양군", "거창군", "합천군");
        put(m, "제주특별자치도", "제주시", "서귀포시");
        return m;
    }

    private static void put(Map<String, Set<String>> map, String province, String... districts) {
        for (String d : districts) {
            map.computeIfAbsent(d, k -> new java.util.HashSet<>()).add(province);
        }
    }
}
