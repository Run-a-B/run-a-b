package com.runab.api.dto.policy;

import com.runab.api.entity.Policy;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 정책 상세 응답 (상세용).
 * 이번 단계에서는 단일 컬럼 필드만 반환한다. (배열형 필드는 다음 단계에서 확장)
 */
@Getter
@Builder
public class PolicyDetailResponse {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private Long id;
    private String title;
    private String description;
    private String date;
    private String announcementNo;
    private String department;
    private String applicationPeriod;
    private String supportScale;
    private String targetGroup;
    private String purposeText;
    private String applicationMethod;
    private String applicationUrl;
    private String detailUrl;
    private String category;
    private String region;
    private String industry;
    private String agency;

    public static PolicyDetailResponse from(Policy policy) {
        return PolicyDetailResponse.builder()
                .id(policy.getId())
                .title(policy.getTitle())
                .description(policy.getDescription())
                .date(policy.getPublishedDate() != null
                        ? policy.getPublishedDate().format(DATE_FORMATTER) : null)
                .announcementNo(policy.getAnnouncementNo())
                .department(policy.getDepartment())
                .applicationPeriod(formatPeriod(
                        policy.getApplicationStartDate(),
                        policy.getApplicationEndDate()))
                .supportScale(policy.getSupportScale())
                .targetGroup(policy.getTargetGroup())
                .purposeText(policy.getPurposeText())
                .applicationMethod(policy.getApplicationMethod())
                .applicationUrl(policy.getApplicationUrl())
                .detailUrl(policy.getDetailUrl())
                .category(policy.getCategory())
                .region(policy.getRegion())
                .industry(policy.getIndustry())
                .agency(policy.getAgency())
                .build();
    }

    // "yyyy.MM.dd ~ yyyy.MM.dd" 조합. 둘 중 null이면 적절히 처리
    private static String formatPeriod(LocalDate start, LocalDate end) {
        String startStr = start != null ? start.format(DATE_FORMATTER) : "";
        String endStr = end != null ? end.format(DATE_FORMATTER) : "";
        if (startStr.isEmpty() && endStr.isEmpty()) {
            return "";
        }
        return startStr + " ~ " + endStr;
    }
}
