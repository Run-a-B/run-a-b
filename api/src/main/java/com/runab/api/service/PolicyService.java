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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final PolicyRepository policyRepository;
    private final BusinessInfoRepository businessInfoRepository;
    private final PolicyCardRepository policyCardRepository;
    private final EligibilityMatcher eligibilityMatcher;

    // ===== 1) 정책 목록 조회 (필터 + 검색 + 정렬 + 페이징) =====
    // userId가 있으면(로그인 + 사업정보 등록) matcher로 관련도를 실제 계산, 없으면 0/false 폴백.
    public PolicyPageResponse getPolicies(
            String region, String industry, String category, String query,
            String sort, int page, int size, Long userId) {

        Specification<Policy> spec = Specification
                .where(PolicySpecification.regionMatches(region))
                .and(PolicySpecification.industryMatches(industry))
                .and(PolicySpecification.categoryMatches(category))
                .and(PolicySpecification.titleContains(query))
                .and(PolicySpecification.sortBy(sort));

        Pageable pageable = PageRequest.of(page, size);
        Page<Policy> policyPage = policyRepository.findAll(spec, pageable);

        BusinessInfo businessInfo = (userId == null)
                ? null
                : businessInfoRepository.findByUserId(userId).orElse(null);

        // 페이지 내 정책 카드를 한 번에 조회해 매칭 시 N+1 쿼리 방지 (페이지당 카드 개수만큼 개별 조회하던 것을 IN 한 방으로)
        final Map<String, PolicyCard> cardMap = (businessInfo == null)
                ? Map.of()
                : loadCards(policyPage.getContent());

        Page<PolicyListResponse> result = policyPage.map(policy -> {
            if (businessInfo == null) {
                return PolicyListResponse.from(policy);
            }
            PolicyCard card = cardMap.get(PolicyCard.toPolicyId(policy.getExternalId()));
            MatchResult match = eligibilityMatcher.match(businessInfo, card);
            return PolicyListResponse.from(policy, match.getScore(), match.getScore() >= AI_RECOMMEND_THRESHOLD && !match.isHardFail());
        });

        return PolicyPageResponse.from(result);
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
