package com.runab.api.controller;

import com.runab.api.dto.common.ApiResponse;
import com.runab.api.dto.report.PolicyReportResponse;
import com.runab.api.service.report.PolicyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 사용자별 AI 리포트 조회/삭제.
 * 생성(POST)은 정책 컨텍스트라 PolicyController(/api/v1/policies/{id}/report)에 유지.
 * 모든 엔드포인트는 SecurityConfig의 anyRequest().hasRole("ACCESS")로 인증 필요 → 자기 리포트만 접근.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final PolicyReportService policyReportService;

    // ===== 1) 내 리포트 목록 =====
    @GetMapping
    public ApiResponse<List<PolicyReportResponse>> getMyReports(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(policyReportService.getReports(userId));
    }

    // ===== 2) 내 리포트 상세 (정책 id 기준) =====
    @GetMapping("/{policyId}")
    public ApiResponse<PolicyReportResponse> getMyReport(@PathVariable Long policyId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(policyReportService.getReport(userId, policyId));
    }

    // ===== 3) 내 리포트 삭제 =====
    @DeleteMapping("/{policyId}")
    public ApiResponse<Void> deleteMyReport(@PathVariable Long policyId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        policyReportService.deleteReport(userId, policyId);
        return ApiResponse.success(null, "리포트가 삭제되었습니다");
    }
}
