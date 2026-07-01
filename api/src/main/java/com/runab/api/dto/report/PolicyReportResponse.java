package com.runab.api.dto.report;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * AI(OpenAI) 기반 정책 영향 리포트 응답.
 * 프론트 SavedReport(apps/run-a-b-fe/src/data/reports.ts) 구조와 1:1로 맞춘다.
 */
@Getter
@Builder
public class PolicyReportResponse {

    private Long policyId;
    private String policyTitle;
    private String category;

    private String impactLabel;   // 예: "사업에 긍정적 영향"
    private String impactStyle;   // tailwind 클래스 (bg-*, text-*)
    private String summary;
    private List<String> details;
    private List<Long> relatedIds;
    private List<BusinessImpactItem> businessImpact;
    private String savedAt;       // 저장/갱신 시각 ISO-8601 (프론트 SavedReport.savedAt) — 생성 응답엔 없을 수 있음

    @Getter
    @Builder
    public static class BusinessImpactItem {
        private String label;       // 예: "매출", "인건비"
        private int level;          // 0~100
        private String direction;   // "up" | "down"
        private String tag;         // 예: "+15%"
        private String barColor;    // tailwind 클래스
        private String tagColor;    // tailwind 클래스
    }
}
