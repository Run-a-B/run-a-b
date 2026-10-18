package com.runab.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runab.api.entity.AuthProvider;
import com.runab.api.entity.User;
import com.runab.api.jwt.JwtTokenProvider;
import com.runab.api.repository.BusinessInfoRepository;
import com.runab.api.repository.EmailVerificationRepository;
import com.runab.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.mail.username=test@example.com", "spring.mail.password=test-only"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired BusinessInfoRepository businesses;
    @Autowired EmailVerificationRepository verifications;
    @Autowired JwtTokenProvider tokens;
    @MockitoBean JavaMailSender mail;
    @MockitoBean com.runab.api.service.PolicySyncService sync;
    @MockitoBean com.runab.api.service.summary.PolicySummaryService summaries;
    private String email;

    @BeforeEach void uniqueEmail() { email = "test-" + java.util.UUID.randomUUID() + "@example.com"; }

    @Test void verificationReturnsCallerHeldProofAndDoesNotStoreRawOtp() throws Exception {
        String code = sendCode();
        String body = mvc.perform(post("/api/v1/auth/email/verify").contentType("application/json")
                        .content(mapper.writeValueAsString(Map.of("email", email, "code", code))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.verification_token").isString())
                .andReturn().getResponse().getContentAsString();
        String receipt = mapper.readTree(body).path("data").path("verification_token").asText();
        assertThat(receipt).hasSizeGreaterThanOrEqualTo(32);
        assertThat(verifications.findTopByEmailOrderByIdDesc(email).orElseThrow().getCode()).isNotEqualTo(code);
        mvc.perform(post("/api/v1/auth/signup").contentType("application/json")
                .content(mapper.writeValueAsString(account("wrong-proof")))).andExpect(status().isBadRequest());
        assertThat(users.existsByEmail(email)).isFalse();
    }

    @Test void atomicSignupRollsBackUserAndProofOnBusinessFailureThenAllowsRetry() throws Exception {
        String receipt = verifiedReceipt();
        mvc.perform(post("/api/v1/auth/signup/complete").contentType("application/json")
                .content(payload(receipt, "invalid-band"))).andExpect(status().isBadRequest());
        assertThat(users.existsByEmail(email)).isFalse();
        mvc.perform(post("/api/v1/auth/signup/complete").contentType("application/json")
                .content(payload(receipt, "1억원~3억원"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").isString());
        User saved = users.findByEmail(email).orElseThrow();
        assertThat(businesses.findByUserId(saved.getId())).isPresent();
        assertThat(verifications.findTopByEmailOrderByIdDesc(email).orElseThrow().isVerified()).isFalse();
    }

    @Test void wrongCodesAreLimitedAndResendHasServerCooldown() throws Exception {
        String code = sendCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        mvc.perform(post("/api/v1/auth/email/send").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email", email)))).andExpect(status().isTooManyRequests());
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/email/verify").contentType("application/json")
                    .content(mapper.writeValueAsString(Map.of("email", email, "code", wrong))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/auth/email/verify").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email", email, "code", code))))
                .andExpect(status().isTooManyRequests());
    }

    @Test void publicCannotRunSynchronizationOrPaidSummary() throws Exception {
        mvc.perform(post("/api/v1/policies/sync")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/policies/492/summary")).andExpect(status().isUnauthorized());
    }

    @Test void deletedUserCannotUseOldAccessToken() throws Exception {
        User user = users.save(User.builder().email(email).username("테스트").provider(AuthProvider.LOCAL).build());
        String token = tokens.createAccessToken(user.getId());
        user.softDelete(); users.saveAndFlush(user);
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test void repeatedLoginAttemptsAreThrottledBeforeAuthentication() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/v1/auth/login").with(request -> {
                        request.setRemoteAddr("192.0.2.88"); return request;
                    }).contentType("application/json")
                    .content(mapper.writeValueAsString(Map.of("email", email, "password", "Wrong123!"))))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").with(request -> {
                    request.setRemoteAddr("192.0.2.88"); return request;
                }).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email", email, "password", "Wrong123!"))))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test void expiredReceiptCannotCreateAccount() throws Exception {
        String receipt = verifiedReceipt();
        var verification = verifications.findTopByEmailOrderByIdDesc(email).orElseThrow();
        org.springframework.test.util.ReflectionTestUtils.setField(verification, "expiresAt", java.time.LocalDateTime.now().minusSeconds(1));
        verifications.saveAndFlush(verification);
        mvc.perform(post("/api/v1/auth/signup/complete").contentType("application/json")
                .content(payload(receipt, "1억원~3억원"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));
        assertThat(users.existsByEmail(email)).isFalse();
    }

    private String sendCode() throws Exception {
        mvc.perform(post("/api/v1/auth/email/send").with(request -> {
                    request.setRemoteAddr("test-client-" + email); return request;
                }).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email", email)))).andExpect(status().isOk());
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        return message.getValue().getText().replaceAll("(?s).*인증 코드: (\\d{6}).*", "$1");
    }

    private String verifiedReceipt() throws Exception {
        String code = sendCode();
        String response = mvc.perform(post("/api/v1/auth/email/verify").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email", email, "code", code))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("data").path("verification_token").asText();
    }

    private Map<String, Object> account(String receipt) {
        return Map.of("email", email, "name", "테스트", "password", "Test123!",
                "passwordConfirm", "Test123!", "verificationToken", receipt);
    }

    private String payload(String receipt, String revenue) throws Exception {
        return mapper.writeValueAsString(Map.of("account", account(receipt), "business", Map.of(
                "businessStatus", true, "jobCategory", "카페/베이커리", "region", "서울",
                "annualRevenue", revenue, "employeeCount", "1~4명")));
    }
}
