package com.runab.api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SignupBusinessRequest {

    @NotNull(message = "사업 상태를 선택해주세요")
    private Boolean businessStatus;   // 프론트가 boolean 변환해서 보냄

    @NotBlank(message = "업종을 선택해주세요")
    private String jobCategory;       // "음식점", "카페/베이커리" 등

    @NotBlank(message = "지역을 선택해주세요")
    private String region;            // "서울", "부산" 등

    private String annualRevenue;     // "1억 미만" 같은 텍스트 (사업 중일 때만)
    private String employeeCount;    // 1, 2, 3, ... 숫자 (선택, 사업 중일 때만)
}