package com.runab.api.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자별 AI 정책 리포트 저장.
 * 기존엔 브라우저 localStorage(rab_reports)에 저장돼 계정 구분 없이 브라우저 단위로 공유됐음(버그).
 * → user_id로 스코프해서 DB에 저장한다. (user_id, policy_id) 유니크 = 같은 정책 재생성 시 upsert(덮어쓰기).
 *
 * details/relatedIds/businessImpact는 구조가 유동적이라 컬럼으로 풀지 않고 JSON 문자열로 통째로 보관한다
 * (PolicyCard.fullJson과 동일한 패턴).
 */
@Entity
@Table(
        name = "report",
        uniqueConstraints = @UniqueConstraint(name = "uk_report_user_policy", columnNames = {"user_id", "policy_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 리포트 소유자. 로그인 사용자 단위로 스코프(조회/삭제는 항상 이 값 기준)
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 어떤 정책에 대한 리포트인지 (Policy.id)
    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "policy_title", nullable = false, length = 500)
    private String policyTitle;

    @Column(length = 50)
    private String category;

    @Column(name = "impact_label", length = 100)
    private String impactLabel;

    @Column(name = "impact_style", length = 100)
    private String impactStyle;

    // "positive" | "negative" (C-1: 프론트 긍정/부정 필터가 impactLabel 텍스트 매칭 대신 이 필드로 판단)
    @Column(name = "direction", length = 20)
    private String direction;

    @Column(columnDefinition = "TEXT")
    private String summary;

    // 상세 분석 문단 배열 (JSON 문자열)
    @Column(name = "details_json", columnDefinition = "JSON")
    private String detailsJson;

    // "함께 신청하면 좋아요" 관련 정책 id 배열 (JSON 문자열)
    @Column(name = "related_ids_json", columnDefinition = "JSON")
    private String relatedIdsJson;

    // 사업 영향도 항목 배열 (JSON 문자열)
    @Column(name = "business_impact_json", columnDefinition = "JSON")
    private String businessImpactJson;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    //--------------------------------------------------------------------------------------------------------

    @Builder
    public Report(Long userId, Long policyId, String policyTitle, String category,
                  String impactLabel, String impactStyle, String direction, String summary,
                  String detailsJson, String relatedIdsJson, String businessImpactJson) {
        this.userId = userId;
        this.policyId = policyId;
        this.policyTitle = policyTitle;
        this.category = category;
        this.impactLabel = impactLabel;
        this.impactStyle = impactStyle;
        this.direction = direction;
        this.summary = summary;
        this.detailsJson = detailsJson;
        this.relatedIdsJson = relatedIdsJson;
        this.businessImpactJson = businessImpactJson;
    }

    // 재생성(upsert) 시 내용 갱신용 비즈니스 메서드 (setter 금지 원칙). updatedAt은 @PreUpdate가 처리.
    public void update(String policyTitle, String category, String impactLabel, String impactStyle,
                       String direction, String summary, String detailsJson, String relatedIdsJson,
                       String businessImpactJson) {
        this.policyTitle = policyTitle;
        this.category = category;
        this.impactLabel = impactLabel;
        this.impactStyle = impactStyle;
        this.direction = direction;
        this.summary = summary;
        this.detailsJson = detailsJson;
        this.relatedIdsJson = relatedIdsJson;
        this.businessImpactJson = businessImpactJson;
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
