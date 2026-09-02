package com.runab.api.controller;

import com.runab.api.config.SecurityConfig;
import com.runab.api.dto.policy.PolicyDiagnosisRequest;
import com.runab.api.dto.policy.PolicyDiagnosisResponse;
import com.runab.api.jwt.JwtAuthenticationFilter;
import com.runab.api.jwt.JwtTokenProvider;
import com.runab.api.service.PolicyService;
import com.runab.api.service.PolicySyncService;
import com.runab.api.service.checklist.PolicyChecklistService;
import com.runab.api.service.diagnosis.PolicyDiagnosisService;
import com.runab.api.service.report.PolicyReportService;
import com.runab.api.service.summary.PolicySummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PolicyDiagnosisSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private PolicyService policyService;
    @MockitoBean private PolicySyncService policySyncService;
    @MockitoBean private PolicyReportService policyReportService;
    @MockitoBean private PolicySummaryService policySummaryService;
    @MockitoBean private PolicyChecklistService policyChecklistService;
    @MockitoBean private PolicyDiagnosisService policyDiagnosisService;

    @Test
    void anonymousDiagnosisPostIsPermittedButUnrelatedPostIsProtected() throws Exception {
        given(policyDiagnosisService.diagnose(eq(42L), any(PolicyDiagnosisRequest.class)))
                .willReturn(PolicyDiagnosisResponse.builder().verdict("ON_HOLD").build());

        mockMvc.perform(post("/api/v1/policies/42/diagnosis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessStatus":true,"industry":"카페/음료","region":"서울특별시",
                                "annualRevenueBand":"5,000만원~1억원","employeeCountBand":"1~4명"}
                                """))
                .andExpect(status().isOk());

        verify(policyDiagnosisService).diagnose(eq(42L), any(PolicyDiagnosisRequest.class));

        mockMvc.perform(post("/api/v1/policies/42/report"))
                .andExpect(status().isForbidden());
    }
}
