package com.runab.api.controller;

import com.runab.api.dto.common.ApiResponse;
import com.runab.api.dto.policy.PolicyDetailResponse;
import com.runab.api.dto.policy.PolicyPageResponse;
import com.runab.api.dto.policy.PolicySyncResult;
import com.runab.api.dto.report.PolicyReportResponse;
import com.runab.api.service.PolicyService;
import com.runab.api.service.PolicySyncService;
import com.runab.api.service.report.PolicyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;
    private final PolicySyncService policySyncService;
    private final PolicyReportService policyReportService;

    // ===== 1) 정책 목록 조회 =====
    @GetMapping
    public ApiResponse<PolicyPageResponse> getPolicies(
            @RequestParam(defaultValue = "전체 지역") String region,
            @RequestParam(defaultValue = "전체 업종") String industry,
            @RequestParam(defaultValue = "전체 카테고리") String category,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "최신순") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "6") int size,
            Authentication authentication) {

        // permitAll 엔드포인트 → 비로그인이면 principal이 "anonymousUser"(String) → null 처리
        Long userId = (authentication != null && authentication.getPrincipal() instanceof Long id) ? id : null;

        // 프론트는 page가 1부터, Spring Pageable은 0부터 → page - 1 변환
        PolicyPageResponse response =
                policyService.getPolicies(region, industry, category, query, sort, page - 1, size, userId);
        return ApiResponse.success(response);
    }

    // ===== 2) 정책 상세 조회 =====
    @GetMapping("/{id}")
    public ApiResponse<PolicyDetailResponse> getPolicyDetail(@PathVariable Long id) {
        return ApiResponse.success(policyService.getPolicyDetail(id));
    }

    // ===== 2-1) AI 리포트 생성 (OpenAI) =====
    // 로그인 + 사업정보 등록 필요. 사용자 사업정보 × 이 정책을 기반으로 영향 분석 리포트를 생성한다.
    @PostMapping("/{id}/report")
    public ApiResponse<PolicyReportResponse> generateReport(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(policyReportService.generate(userId, id), "리포트 생성 완료");
    }

    // ===== 3) bizinfo 정책 동기화 (수동 트리거) =====
    // TODO: 관리자성 작업 → 추후 ADMIN 권한 제한 필요. 현재는 발표 데모 편의상 permitAll(SecurityConfig 참고)
    @PostMapping("/sync")
    public ApiResponse<PolicySyncResult> syncPolicies() {
        PolicySyncResult result = policySyncService.syncPolicies();
        return ApiResponse.success(result, "동기화 완료");
    }
}
