package com.runab.api.service;

import com.runab.api.dto.policy.PolicyDetailResponse;
import com.runab.api.dto.policy.PolicyListResponse;
import com.runab.api.dto.policy.PolicyPageResponse;
import com.runab.api.dto.recommend.MatchResult;
import com.runab.api.entity.BusinessInfo;
import com.runab.api.entity.Policy;
import com.runab.api.entity.PolicyCard;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import com.runab.api.repository.BusinessInfoRepository;
import com.runab.api.repository.PolicyCardRepository;
import com.runab.api.repository.PolicyRepository;
import com.runab.api.repository.PolicySpecification;
import com.runab.api.service.matcher.EligibilityMatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyService {

    // 점수 70 이상이면 "AI 추천" 뱃지 표시 (matcher score 기준)
    private static final int AI_RECOMMEND_THRESHOLD = 70;

    private static final String SORT_RELEVANCE = "관련도 높은순";

    // 관련도 정렬 비교자: 점수 내림차순 → 동점이면 최신순(publishedDate desc, null 뒤) → id desc (안정 정렬)
    private static final Comparator<ScoredPolicy> RELEVANCE_COMPARATOR =
            Comparator.comparingInt(ScoredPolicy::score).reversed()
                    .thenComparing(sp -> sp.policy().getPublishedDate(),
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(sp -> sp.policy().getId(), Comparator.reverseOrder());

    private final PolicyRepository policyRepository;
    private final BusinessInfoRepository businessInfoRepository;
    private final PolicyCardRepository policyCardRepository;
    private final EligibilityMatcher eligibilityMatcher;

    // 점수까지 계산된 정책 (관련도 정렬 + 응답 매핑용 임시 홀더)
    private record ScoredPolicy(Policy policy, int score, boolean aiRecommended) {}

    // ===== 1) 정책 목록 조회 (필터 + 검색 + 정렬 + 페이징) =====
    // userId가 있으면(로그인 + 사업정보 등록) matcher로 관련도를 실제 계산, 없으면 0/false 폴백.
    public PolicyPageResponse getPolicies(
            String region, String industry, String category, String query,
            String sort, int page, int size, Long userId) {

        // 업종 필터는 Policy.industry(전부 "전체") 대신 policy_card.eligibility.industries 기준(자바 레벨)으로 판정한다.
        boolean industryFilter = industry != null && !industry.isBlank() && !industry.equals("전체 업종");

        BusinessInfo businessInfo = (userId == null)
                ? null
                : businessInfoRepository.findByUserId(userId).orElse(null);

        // 관련도 정렬은 관련도가 요청 시점 계산값이라 SQL 정렬 불가 → 로그인+사업정보 있을 때만 자바 레벨로 수행.
        // 비로그인/사업정보 미등록이면 관련도가 0 고정이라 정렬 의미 없어 최신순으로 폴백(기존 동작 유지).
        boolean relevanceSort = SORT_RELEVANCE.equals(sort) && businessInfo != null;

        // 지역/카테고리/검색어 1차 필터 (업종은 카드 기반이라 여기서 제외, 정렬은 경로별로 아래서 분기)
        Specification<Policy> baseSpec = Specification
                .where(PolicySpecification.regionMatches(region))
                .and(PolicySpecification.categoryMatches(category))
                .and(PolicySpecification.titleContains(query));

        // ---- 빠른 경로: 업종 필터 없고 관련도 정렬도 아님 → 기존처럼 SQL이 정렬+페이지네이션까지 담당 ----
        if (!industryFilter && !relevanceSort) {
            Specification<Policy> spec = baseSpec.and(PolicySpecification.sortBy(sort));
            Page<Policy> policyPage = policyRepository.findAll(spec, PageRequest.of(page, size));
            Map<String, PolicyCard> cardMap = (businessInfo == null) ? Map.of() : loadCards(policyPage.getContent());
            Page<PolicyListResponse> result = policyPage.map(p -> toResponse(p, businessInfo, cardMap));
            return PolicyPageResponse.from(result);
        }

        // ---- 자바 레벨 경로: 업종 필터 또는 관련도 정렬 필요 ----
        // 관련도 정렬이면 뒤에서 점수로 재정렬하므로 SQL 정렬 불필요. 업종 필터만이면 요청 정렬을 SQL로 유지.
        Specification<Policy> spec = relevanceSort ? baseSpec : baseSpec.and(PolicySpecification.sortBy(sort));
        List<Policy> candidates = policyRepository.findAll(spec);

        // 후보군 카드 IN 조회 (업종 필터 판정 + 관련도 계산 공용) — N+1 방지 패턴 재사용
        final Map<String, PolicyCard> cardMap = loadCards(candidates);

        // 업종 필터: 선택 업종 기준(사업정보 무관). 카드 없음/industries 비어있음 → 제외.
        if (industryFilter) {
            candidates = candidates.stream()
                    .filter(p -> eligibilityMatcher.matchesIndustryFilter(
                            cardMap.get(PolicyCard.toPolicyId(p.getExternalId())), industry))
                    .toList();
        }

        // 관련도 점수 계산 (로그인+사업정보 있을 때만 유의미, 아니면 0).
        // 성능: 현재 정책 ~1,500건 규모라 후보 전체 로드 후 자바 정렬이 문제없음.
        //       정책 수가 크게 늘면(예: 1만 건 이상) DB 레벨 정렬이나 별도 점수 캐싱 테이블 도입 고려 필요.
        List<ScoredPolicy> scored = candidates.stream()
                .map(p -> {
                    if (businessInfo == null) {
                        return new ScoredPolicy(p, 0, false);
                    }
                    PolicyCard card = cardMap.get(PolicyCard.toPolicyId(p.getExternalId()));
                    MatchResult m = eligibilityMatcher.match(businessInfo, card);
                    return new ScoredPolicy(p, m.getScore(), m.getScore() >= AI_RECOMMEND_THRESHOLD && !m.isHardFail());
                })
                .collect(Collectors.toList());

        if (relevanceSort) {
            scored.sort(RELEVANCE_COMPARATOR);
        }

        // 자바 레벨 페이지네이션
        long total = scored.size();
        int from = Math.min(page * size, scored.size());
        int to = Math.min(from + size, scored.size());
        List<PolicyListResponse> content = scored.subList(from, to).stream()
                .map(sp -> PolicyListResponse.from(sp.policy(), sp.score(), sp.aiRecommended()))
                .toList();

        return PolicyPageResponse.from(new PageImpl<>(content, PageRequest.of(page, size), total));
    }

    // Policy → 목록 응답 (사업정보 있으면 관련도 계산해서 채움)
    private PolicyListResponse toResponse(Policy policy, BusinessInfo businessInfo, Map<String, PolicyCard> cardMap) {
        if (businessInfo == null) {
            return PolicyListResponse.from(policy);
        }
        PolicyCard card = cardMap.get(PolicyCard.toPolicyId(policy.getExternalId()));
        MatchResult match = eligibilityMatcher.match(businessInfo, card);
        return PolicyListResponse.from(policy, match.getScore(),
                match.getScore() >= AI_RECOMMEND_THRESHOLD && !match.isHardFail());
    }

    // 페이지 내 정책들의 PolicyCard를 IN 쿼리 한 번으로 조회해 policyId → card 맵으로 반환
    private Map<String, PolicyCard> loadCards(List<Policy> policies) {
        List<String> keys = policies.stream()
                .map(p -> PolicyCard.toPolicyId(p.getExternalId()))
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            return Map.of();
        }
        return policyCardRepository.findByPolicyIdIn(keys).stream()
                .collect(Collectors.toMap(PolicyCard::getPolicyId, Function.identity(), (a, b) -> a));
    }

    // ===== 2) 정책 상세 조회 =====
    public PolicyDetailResponse getPolicyDetail(Long id) {
        Policy policy = policyRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POLICY_NOT_FOUND));
        return PolicyDetailResponse.from(policy);
    }
}
