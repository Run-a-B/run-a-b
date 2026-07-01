package com.runab.api.dto.policy;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 정책 공고문 AI 요약 응답.
 * 프론트 PolicyDetail.tsx의 aiSummaryText(→ summaryLines)/aiHighlights(icon·label·content) 구조와 맞춘다.
 */
@Getter
@Builder
public class PolicySummaryResponse {

    private List<String> summaryLines;      // AI 3줄 요약
    private List<Highlight> highlights;     // 아이콘 하이라이트

    // 원본 정보만으로 자연스럽게 재구성한 본문 (H-2). 비어 있으면 프론트가 원본 필드로 폴백.
    private String expandedDescription;         // "사업 내용"(description+purposeText) 재구성
    private String expandedApplicationMethod;   // "신청 방법"(applicationMethod) 재구성

    @Getter
    @Builder
    public static class Highlight {
        private String icon;    // money | check | calendar (프론트 HIGHLIGHT_ICONS 키)
        private String label;   // 짧은 라벨
        private String content; // 한 문장 설명
    }
}
