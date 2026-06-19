package com.runab.api.service;

import com.runab.api.dto.policy.PolicyDetailResponse;
import com.runab.api.dto.policy.PolicyListResponse;
import com.runab.api.dto.policy.PolicyPageResponse;
import com.runab.api.entity.Policy;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import com.runab.api.repository.PolicyRepository;
import com.runab.api.repository.PolicySpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyService {

    private final PolicyRepository policyRepository;

    // ===== 1) 정책 목록 조회 (필터 + 검색 + 정렬 + 페이징) =====
    public PolicyPageResponse getPolicies(
            String region, String industry, String category, String query,
            String sort, int page, int size) {

        // 동적 필터 + 정렬 조합 (프론트 Policies.tsx 로직 그대로)
        // 정렬은 NULLS LAST 처리를 위해 Sort 대신 Specification(sortBy)에서 처리한다.
        Specification<Policy> spec = Specification
                .where(PolicySpecification.regionMatches(region))
                .and(PolicySpecification.industryMatches(industry))
                .and(PolicySpecification.categoryMatches(category))
                .and(PolicySpecification.titleContains(query))
                .and(PolicySpecification.sortBy(sort));

        Pageable pageable = PageRequest.of(page, size);

        Page<PolicyListResponse> result = policyRepository.findAll(spec, pageable)
                .map(PolicyListResponse::from);

        return PolicyPageResponse.from(result);
    }

    // ===== 2) 정책 상세 조회 =====
    public PolicyDetailResponse getPolicyDetail(Long id) {
        Policy policy = policyRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POLICY_NOT_FOUND));
        return PolicyDetailResponse.from(policy);
    }
}
