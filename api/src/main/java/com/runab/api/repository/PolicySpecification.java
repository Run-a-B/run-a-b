package com.runab.api.repository;

import com.runab.api.entity.Policy;
import org.springframework.data.jpa.domain.Specification;

/**
 * 정책 목록 동적 필터 조건.
 * 프론트(Policies.tsx)의 필터 로직을 그대로 이식한다.
 */
public class PolicySpecification {

    private PolicySpecification() {
    }

    /**
     * region 필터:
     * - null 또는 "전체 지역"이면 조건 없음
     * - 아니면 (region = '전국' OR region = :region)
     *   → "전국" 정책은 어떤 지역을 골라도 항상 노출, 특정 지역 정책은 그 지역 선택시만 노출
     */
    public static Specification<Policy> regionMatches(String region) {
        return (root, query, cb) -> {
            if (region == null || region.equals("전체 지역")) {
                return cb.conjunction();
            }
            return cb.or(
                    cb.equal(root.get("region"), "전국"),
                    cb.equal(root.get("region"), region)
            );
        };
    }

    /**
     * industry 필터:
     * - null 또는 "전체 업종"이면 조건 없음
     * - 아니면 (industry = '전체' OR industry = :industry)
     *   → "전체" 업종 정책은 항상 노출, 특정 업종 정책은 그 업종 선택시만 노출
     */
    public static Specification<Policy> industryMatches(String industry) {
        return (root, query, cb) -> {
            if (industry == null || industry.equals("전체 업종")) {
                return cb.conjunction();
            }
            return cb.or(
                    cb.equal(root.get("industry"), "전체"),
                    cb.equal(root.get("industry"), industry)
            );
        };
    }

    /**
     * category 필터:
     * - null 또는 "전체 카테고리"면 조건 없음
     * - 아니면 category = :category
     *
     * 프론트 Policies.tsx의 CATEGORIES 드롭다운("기술","경영","수출","인력","창업","금융","내수","기타")이
     * 보내는 값은 카드 뱃지에 표시되는 Policy.category 컬럼과 매칭된다.
     * 이전에는 filter_category 컬럼으로 비교하고 있어(2026.07.01 버그) 카테고리 선택이 결과에 반영되지 않았다.
     * (filter_category는 원래 "자금 지원/세금 감면" 같은 별도 분류용으로 설계됐으나 현재 대응 UI가 없고,
     *  PolicySyncService가 category와 동일 값으로 채우고 있어 사실상 미사용 컬럼 — 제거는 이번 범위 밖.)
     */
    public static Specification<Policy> categoryMatches(String category) {
        return (root, query, cb) -> {
            if (category == null || category.equals("전체 카테고리")) {
                return cb.conjunction();
            }
            return cb.equal(root.get("category"), category);
        };
    }

    /**
     * 검색어(query) 필터:
     * - null 또는 blank면 조건 없음
     * - 아니면 title LIKE %query% (대소문자 무시)
     */
    public static Specification<Policy> titleContains(String query) {
        return (root, q, cb) -> {
            if (query == null || query.isBlank()) {
                return cb.conjunction();
            }
            return cb.like(cb.lower(root.get("title")), "%" + query.toLowerCase() + "%");
        };
    }

    /**
     * 정렬 처리.
     * - "마감임박순": applicationEndDate ASC (null은 뒤로)
     * - "최신순" / "관련도 높은순"(폴백): publishedDate DESC (null은 뒤로)
     *   TODO: "관련도 높은순"은 관련도(Relevance) 도메인 구현 후 실제 관련도 정렬로 교체 예정
     *
     * null을 뒤로 보내는 처리(NULLS LAST)는 Hibernate Criteria가 직접 지원하지 않으므로
     * "값이 null이면 1, 아니면 0" CASE 식을 1차 정렬키로 둬서 emulate 한다.
     */
    public static Specification<Policy> sortBy(String sort) {
        return (root, query, cb) -> {
            // count 쿼리(결과 타입 Long)에는 정렬을 적용하지 않는다
            if (query.getResultType() == Long.class || query.getResultType() == long.class) {
                return cb.conjunction();
            }

            if ("마감임박순".equals(sort)) {
                query.orderBy(
                        cb.asc(cb.<Integer>selectCase()
                                .when(cb.isNull(root.get("applicationEndDate")), 1)
                                .otherwise(0)),
                        cb.asc(root.get("applicationEndDate"))
                );
            } else {
                query.orderBy(
                        cb.asc(cb.<Integer>selectCase()
                                .when(cb.isNull(root.get("publishedDate")), 1)
                                .otherwise(0)),
                        cb.desc(root.get("publishedDate"))
                );
            }
            return cb.conjunction();
        };
    }
}
