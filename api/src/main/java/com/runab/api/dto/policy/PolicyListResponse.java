package com.runab.api.dto.policy;

import com.runab.api.entity.Policy;
import lombok.Builder;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

/**
 * 정책 목록 카드용 응답 (가벼움).
 * 프론트 Policy 인터페이스 기준.
 */
@Getter
@Builder
public class PolicyListResponse {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private Long id;
    private String title;
    private String description;
    private String category;
    private String filterCategory;
    private String region;
    private String industry;
    private String agency;
    private String date;              // publishedDate → "yyyy.MM.dd"
    private int relevance;            // 관련도 도메인 전까지 고정값 0
    private boolean isAIRecommended;  // 추천 도메인 전까지 고정값 false

    public static PolicyListResponse from(Policy policy) {
        return PolicyListResponse.builder()
                .id(policy.getId())
                .title(policy.getTitle())
                .description(policy.getDescription())
                .category(policy.getCategory())
                .filterCategory(policy.getFilterCategory())
                .region(policy.getRegion())
                .industry(policy.getIndustry())
                .agency(policy.getAgency())
                .date(policy.getPublishedDate() != null
                        ? policy.getPublishedDate().format(DATE_FORMATTER)
                        : "")
                // TODO: 관련도(Relevance) 도메인 구현 후 실제 값으로 교체 예정
                .relevance(0)
                // TODO: 추천(Recommendation) 도메인 구현 후 실제 값으로 교체 예정
                .isAIRecommended(false)
                .build();
    }
}
