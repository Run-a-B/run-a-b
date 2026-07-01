package com.runab.api.repository;

import com.runab.api.entity.PolicySummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PolicySummaryRepository extends JpaRepository<PolicySummary, Long> {

    // 정책별 요약 캐시 조회 (정책당 하나)
    Optional<PolicySummary> findByPolicyId(Long policyId);
}
