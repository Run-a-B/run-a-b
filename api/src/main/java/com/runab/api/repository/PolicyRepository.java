package com.runab.api.repository;

import com.runab.api.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long>, JpaSpecificationExecutor<Policy> {

    // 동적 필터(지역/업종/카테고리/검색어 조합)는 JpaSpecificationExecutor + PolicySpecification으로 처리한다.

    // bizinfo 동기화 중복 방지용: 외부 공고 ID(pblancId)로 기존 정책 조회
    Optional<Policy> findByExternalId(String externalId);
}
