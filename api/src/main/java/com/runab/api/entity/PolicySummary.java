package com.runab.api.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 정책 공고문 AI 3줄 요약 캐시.
 * 리포트(Report)와 달리 user_id 스코프가 필요 없다 — 정책 하나당 결과 하나(모든 사용자 공용).
 * 같은 정책을 여러 사용자가 봐도 OpenAI를 한 번만 호출하도록 policy_id 유니크로 캐싱한다.
 *
 * summaryLines/highlights는 구조가 유동적이라 컬럼으로 풀지 않고 JSON 문자열로 보관(PolicyCard.fullJson·Report 패턴).
 */
@Entity
@Table(name = "policy_summary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 정책의 요약인지 (Policy.id). 정책당 하나 → 유니크
    @Column(name = "policy_id", unique = true, nullable = false)
    private Long policyId;

    // AI 3줄 요약 (JSON 문자열: ["...","...","..."])
    @Column(name = "summary_lines_json", columnDefinition = "JSON")
    private String summaryLinesJson;

    // 하이라이트 (JSON 문자열: [{"icon","label","content"}])
    @Column(name = "highlights_json", columnDefinition = "JSON")
    private String highlightsJson;

    // "사업 내용"(description+purposeText)을 원본 정보만으로 자연스럽게 재구성한 문장 (H-2). 없으면 null → 프론트가 원본 폴백.
    @Column(name = "expanded_description", columnDefinition = "TEXT")
    private String expandedDescription;

    // "신청 방법"(applicationMethod)을 원본 정보만으로 재구성한 문장 (H-2).
    @Column(name = "expanded_application_method", columnDefinition = "TEXT")
    private String expandedApplicationMethod;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    //--------------------------------------------------------------------------------------------------------

    @Builder
    public PolicySummary(Long policyId, String summaryLinesJson, String highlightsJson,
                         String expandedDescription, String expandedApplicationMethod) {
        this.policyId = policyId;
        this.summaryLinesJson = summaryLinesJson;
        this.highlightsJson = highlightsJson;
        this.expandedDescription = expandedDescription;
        this.expandedApplicationMethod = expandedApplicationMethod;
    }

    // 재생성 시 갱신용 비즈니스 메서드 (setter 금지 원칙). updatedAt은 @PreUpdate가 처리.
    public void update(String summaryLinesJson, String highlightsJson,
                       String expandedDescription, String expandedApplicationMethod) {
        this.summaryLinesJson = summaryLinesJson;
        this.highlightsJson = highlightsJson;
        this.expandedDescription = expandedDescription;
        this.expandedApplicationMethod = expandedApplicationMethod;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
