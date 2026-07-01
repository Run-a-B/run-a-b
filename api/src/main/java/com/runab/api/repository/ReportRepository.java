package com.runab.api.repository;

import com.runab.api.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // 내 리포트 목록 (최신순). 프론트에서 추가 정렬/필터하지만 기본 정렬은 최신 갱신순.
    List<Report> findByUserIdOrderByUpdatedAtDesc(Long userId);

    // 특정 정책에 대한 내 리포트 (upsert 판단 / 상세 조회 / 삭제용)
    Optional<Report> findByUserIdAndPolicyId(Long userId, Long policyId);

    // 삭제: 내 리포트만 지워지도록 userId까지 조건에 포함
    long deleteByUserIdAndPolicyId(Long userId, Long policyId);
}
