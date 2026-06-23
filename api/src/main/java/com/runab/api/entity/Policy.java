package com.runab.api.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 외부 시스템(bizinfo) 공고 ID (PBLN_...). 동기화 중복 방지용 unique 키
    @Column(name = "external_id", unique = true, length = 100)
    private String externalId;

    // 정책 제목
    @Column(nullable = false, length = 500)
    private String title;

    // 카테고리 뱃지용 (예: "최저임금", "노동·복지")
    @Column(length = 50)
    private String category;

    // 필터 드롭다운용 (예: "자금 지원", "세금 감면")
    @Column(name = "filter_category", length = 50)
    private String filterCategory;

    // 지역. "전국" 또는 "서울특별시" 등
    @Column(length = 50)
    private String region;

    // 업종. "전체" 또는 "음식점업" 등
    @Column(length = 50)
    private String industry;

    // 카드 요약 설명
    @Column(length = 1000, columnDefinition = "TEXT")
    private String description;

    // 소관부처 (예: "고용노동부")
    @Column(length = 100)
    private String agency;

    // 신청 시작일
    @Column(name = "application_start_date")
    private LocalDate applicationStartDate;

    // 신청 종료일(마감). 정렬(마감임박순)에 사용
    @Column(name = "application_end_date")
    private LocalDate applicationEndDate;

    // 게시일
    @Column(name = "published_date")
    private LocalDate publishedDate;

    // 공고번호. 상세용
    @Column(name = "announcement_no", length = 100)
    private String announcementNo;

    // 담당부서. 상세용
    @Column(length = 100)
    private String department;

    // 지원규모. 상세용
    @Column(name = "support_scale", length = 200)
    private String supportScale;

    // 지원대상. 상세용
    @Column(name = "target_group", length = 200)
    private String targetGroup;

    // 사업목적 본문. 상세용
    @Column(name = "purpose_text", columnDefinition = "TEXT")
    private String purposeText;

    // 신청방법. 상세용
    @Column(name = "application_method", columnDefinition = "TEXT")
    private String applicationMethod;

    // 신청 링크
    @Column(name = "application_url", length = 500)
    private String applicationUrl;

    // 원본 상세페이지 URL (bizinfo)
    @Column(name = "detail_url", length = 500)
    private String detailUrl;

    // 출처 시스템 (예: "bizinfo")
    @Column(name = "source_system", length = 50)
    private String sourceSystem;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    //--------------------------------------------------------------------------------------------------------

    // 데이터 적재(B단계)를 위해 모든 필드를 받는 Builder 생성자
    @Builder
    public Policy(String externalId, String title, String category, String filterCategory, String region,
                  String industry, String description, String agency,
                  LocalDate applicationStartDate, LocalDate applicationEndDate, LocalDate publishedDate,
                  String announcementNo, String department, String supportScale, String targetGroup,
                  String purposeText, String applicationMethod, String applicationUrl,
                  String detailUrl, String sourceSystem) {
        this.externalId = externalId;
        this.title = title;
        this.category = category;
        this.filterCategory = filterCategory;
        this.region = region;
        this.industry = industry;
        this.description = description;
        this.agency = agency;
        this.applicationStartDate = applicationStartDate;
        this.applicationEndDate = applicationEndDate;
        this.publishedDate = publishedDate;
        this.announcementNo = announcementNo;
        this.department = department;
        this.supportScale = supportScale;
        this.targetGroup = targetGroup;
        this.purposeText = purposeText;
        this.applicationMethod = applicationMethod;
        this.applicationUrl = applicationUrl;
        this.detailUrl = detailUrl;
        this.sourceSystem = sourceSystem;
    }

    // bizinfo 동기화 시 기존 정책 갱신용 (setter 금지 원칙 → 비즈니스 메서드로만 수정)
    // 신청기간/마감 등이 바뀔 수 있어 매핑 가능한 필드를 갱신한다. updatedAt은 @PreUpdate가 처리.
    public void updateFromBizinfo(String title, String category, String filterCategory, String region,
                                  String industry, String description, String agency,
                                  LocalDate applicationStartDate, LocalDate applicationEndDate,
                                  LocalDate publishedDate, String targetGroup,
                                  String applicationMethod, String applicationUrl,
                                  String detailUrl, String sourceSystem) {
        this.title = title;
        this.category = category;
        this.filterCategory = filterCategory;
        this.region = region;
        this.industry = industry;
        this.description = description;
        this.agency = agency;
        this.applicationStartDate = applicationStartDate;
        this.applicationEndDate = applicationEndDate;
        this.publishedDate = publishedDate;
        this.targetGroup = targetGroup;
        this.applicationMethod = applicationMethod;
        this.applicationUrl = applicationUrl;
        this.detailUrl = detailUrl;
        this.sourceSystem = sourceSystem;
    }

    // 생성및 수정 시간 자동 세팅 (User 엔티티와 동일 방식)
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
