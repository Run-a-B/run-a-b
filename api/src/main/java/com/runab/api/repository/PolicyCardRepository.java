package com.runab.api.repository;

import com.runab.api.entity.PolicyCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PolicyCardRepository extends JpaRepository<PolicyCard, Long> {

    // 매칭키로 조회 (extractor 결과 upsert용)
    Optional<PolicyCard> findByPolicyId(String policyId);

    // 목록 관련도 계산 시 N+1 방지용: 페이지 내 정책 카드들을 한 번에 조회
    List<PolicyCard> findByPolicyIdIn(Collection<String> policyIds);

    // 우리 Policy.externalId와 연결
    Optional<PolicyCard> findByExternalId(String externalId);

    // closed 제외 등 신청 가능한 카드 조회용 (선택)
    List<PolicyCard> findByApplicationStatusNot(String status);
}
