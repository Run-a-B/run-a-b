package com.runab.api.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI extractor가 추출한 정책 자격요건(policy_card_full)을 저장.
 * 핵심 조회/필터 필드만 컬럼으로 빼고, 전체 구조는 fullJson(JSON 컬럼)에 통째로 보관한다.
 * 데이터는 배포 후 배치(extractor)가 채운다 — 현재는 비어 있을 수 있다.
 */
@Entity
@Table(name = "policy_card")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 매칭키. "bizinfo_support_program:{pblancId}" 형식
    @Column(name = "policy_id", unique = true, length = 200)
    private String policyId;

    // 우리 Policy.externalId(PBLN_...)와 연결용
    @Column(name = "external_id", length = 100)
    private String externalId;

    // open / closed / unknown (조회·필터용으로 빼둠)
    @Column(name = "application_status", length = 20)
    private String applicationStatus;

    // extracted / partially_extracted / failed
    @Column(name = "extraction_status", length = 30)
    private String extractionStatus;

    // policy_card_full 전체를 JSON 문자열로 저장 (MySQL JSON 컬럼)
    @Column(name = "full_json", columnDefinition = "JSON")
    private String fullJson;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    //--------------------------------------------------------------------------------------------------------

    @Builder
    public PolicyCard(String policyId, String externalId, String applicationStatus,
                      String extractionStatus, String fullJson) {
        this.policyId = policyId;
        this.externalId = externalId;
        this.applicationStatus = applicationStatus;
        this.extractionStatus = extractionStatus;
        this.fullJson = fullJson;
    }

    // extractor 재실행 시 갱신용 (setter 금지 원칙 → 비즈니스 메서드로만)
    public void updateCard(String fullJson, String applicationStatus, String extractionStatus) {
        this.fullJson = fullJson;
        this.applicationStatus = applicationStatus;
        this.extractionStatus = extractionStatus;
    }

    // Policy.externalId(PBLN_...) → PolicyCard.policyId 형식으로 변환
    // ⚠️ PolicyCard.externalId 컬럼은 값이 policyId와 동일하게(접두사 포함) 잘못 들어가 있어 매칭에 못 씀(2026.07.01 확인).
    //    policyId 컬럼은 항상 이 형식이 보장되므로 이걸로 조회해야 함.
    public static String toPolicyId(String bizinfoExternalId) {
        return "bizinfo_support_program:" + bizinfoExternalId;
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
