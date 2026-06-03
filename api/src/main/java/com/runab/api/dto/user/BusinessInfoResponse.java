package com.runab.api.dto.user;

import com.runab.api.entity.BusinessInfo;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BusinessInfoResponse {

    private Boolean businessStatus;
    private String jobCategory;
    private String region;
    private String annualRevenue;     // DB는 숫자, 응답은 텍스트
    private String employeeCount;     // DB는 숫자, 응답은 텍스트

    public static BusinessInfoResponse from(
            BusinessInfo info,
            String annualRevenueText,
            String employeeCountText
    ) {
        return BusinessInfoResponse.builder()
                .businessStatus(info.getBusinessStatus())
                .jobCategory(info.getJobCategory())
                .region(info.getRegion())
                .annualRevenue(annualRevenueText)
                .employeeCount(employeeCountText)
                .build();
    }
}