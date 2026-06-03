package com.runab.api.dto.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BusinessInfoUpdateRequest {

    private Boolean businessStatus;
    private String jobCategory;
    private String region;
    private String annualRevenue;     // 텍스트 받음 (1억원~3억원)
    private String employeeCount;     // 텍스트 받음 (1~4명)
}