package com.runab.api.dto.recommend;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.runab.api.entity.BusinessInfo;
import lombok.Builder;
import lombok.Getter;

/**
 * AI 요청의 user_business_info 블록.
 * BusinessInfo(필드 5개)에서 변환하고, 우리가 아직 보유하지 않는 항목은 "unknown"/null로 채운다.
 */
@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserBusinessInfoDto {

    private String region;
    private String district;          // 시군구 — 미보유
    private String industry;
    private Long annualRevenue;
    private Integer employees;
    private String businessStage;     // "운영중" / "준비중"

    // 아래는 현재 BusinessInfo에 없는 항목 → unknown
    private String ownerType;
    private Integer ownerAge;
    private Integer businessMonths;
    private String taxArrearsStatus;
    private String creditGuaranteeStatus;
    private String mainBusinessGoal;
    private String preferredSupportType;

    public static UserBusinessInfoDto from(BusinessInfo info) {
        Boolean status = info.getBusinessStatus();
        return UserBusinessInfoDto.builder()
                .region(info.getRegion())
                .district("unknown")
                .industry(info.getJobCategory())
                .annualRevenue(info.getAnnualRevenue())
                .employees(info.getEmployeeCount())
                .businessStage(Boolean.TRUE.equals(status) ? "운영중" : "준비중")
                .ownerType("unknown")
                .ownerAge(null)
                .businessMonths(null)
                .taxArrearsStatus("unknown")
                .creditGuaranteeStatus("unknown")
                .mainBusinessGoal("unknown")
                .preferredSupportType("unknown")
                .build();
    }
}
