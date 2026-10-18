package com.runab.api.dto.policy;

import com.runab.api.entity.Policy;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 정책 상세 응답 (상세용).
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
    private String sourceSystem;
    private String extractionStatus;
    private String originalNoticeText;
    private String originalApplicationMethod;
    private String originalApplicationPeriod;
    private String executingAgency;
    private String inquiryContact;
    @Builder.Default
    private List<SourceAttachment> sourceAttachments = List.of();

    public record SourceAttachment(String name, String url, String role) {}

    public static PolicyDetailResponse from(Policy policy) {
        return from(policy, null);
    }

    public static PolicyDetailResponse from(Policy policy, String extractionStatus) {
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
                .sourceSystem(policy.getSourceSystem())
                .extractionStatus(extractionStatus)
                .originalNoticeText(policy.getOriginalNoticeText())
                .originalApplicationMethod(policy.getOriginalApplicationMethod())
                .originalApplicationPeriod(policy.getOriginalApplicationPeriod())
                .executingAgency(policy.getExecutingAgency())
                .inquiryContact(policy.getInquiryContact())
                .sourceAttachments(sourceAttachments(policy))
                .build();
    }

    private static List<SourceAttachment> sourceAttachments(Policy policy) {
        List<SourceAttachment> attachments = new ArrayList<>();
        addAttachment(attachments, policy.getAttachmentName(), policy.getAttachmentUrl(), "attachment");
        addAttachment(attachments, policy.getNoticeFileName(), policy.getNoticeFileUrl(), "notice");
        return List.copyOf(attachments);
    }

    private static void addAttachment(List<SourceAttachment> attachments, String name, String url, String role) {
        String storedName = name != null && !name.isBlank() ? name : null;
        String storedUrl = url != null && !url.isBlank() ? url : null;
        if (storedName != null || storedUrl != null) {
            attachments.add(new SourceAttachment(storedName, storedUrl, role));
        }
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
