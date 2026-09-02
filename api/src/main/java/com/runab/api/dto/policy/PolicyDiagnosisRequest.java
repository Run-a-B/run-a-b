package com.runab.api.dto.policy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PolicyDiagnosisRequest {
    @NotNull
    private Boolean businessStatus;
    @NotBlank
    private String industry;
    @NotBlank
    private String region;
    private String annualRevenueBand;
    private String employeeCountBand;
}
