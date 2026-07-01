package com.runab.api.dto.policy;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * "신청 준비하기" 체크리스트 응답.
 * 프론트 PolicyChecklist.tsx의 기존 mock(POLICY_DETAILS[policyId]) 구조와 1:1로 맞춘다 — UI 무변경이 목표.
 */
@Getter
@Builder
public class PolicyChecklistResponse {

    private String applicationPeriod;   // "yyyy.MM.dd ~ yyyy.MM.dd"
    private String department;          // 담당 부서/기관
    private String applicationUrl;      // 신청 페이지 URL (없으면 detailUrl 폴백)
    private List<ChecklistItem> applicationChecklist;

    @Getter
    @Builder
    public static class ChecklistItem {
        private String id;
        private String label;           // 서류명
        private boolean required;        // 필수 여부
        private String description;      // 부가 설명 (없으면 null)
    }
}
