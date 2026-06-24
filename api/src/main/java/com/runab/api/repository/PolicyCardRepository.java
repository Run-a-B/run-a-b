package com.runab.api.repository;

import com.runab.api.entity.PolicyCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyCardRepository extends JpaRepository<PolicyCard, Long> {

    // 매칭키로 조회 (extractor 결과 upsert용)
    Optional<PolicyCard> findByPolicyId(String policyId);

    // 우리 Policy.externalId와 연결
    Optional<PolicyCard> findByExternalId(String externalId);

    // closed 제외 등 신청 가능한 카드 조회용 (선택)
    List<PolicyCard> findByApplicationStatusNot(String status);
}
