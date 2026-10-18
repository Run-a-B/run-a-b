package com.runab.api.service;

import com.runab.api.exception.ErrorCode;
import com.runab.api.repository.EmailVerificationRepository;
import com.runab.api.service.recommend.AiGatewayClient;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class IntegrationConfigurationTest {
    @Test void missingSmtpCredentialsFailBeforeDatabaseOrDelivery() {
        var sender = mock(JavaMailSender.class);
        var repository = mock(EmailVerificationRepository.class);
        var service = new EmailService(sender, repository, "", "");
        assertThatThrownBy(() -> service.sendCode("owner@example.com"))
                .extracting("errorCode").isEqualTo(ErrorCode.MAIL_NOT_CONFIGURED);
        verifyNoInteractions(sender, repository);
    }

    @Test void deprecatedUnconfiguredGatewayFailsBeforeNetworkOrPayloadAccess() {
        var client = new AiGatewayClient("https://unused.invalid", "unused", 1);
        assertThatThrownBy(() -> client.recommend(null))
                .extracting("errorCode").isEqualTo(ErrorCode.AI_NOT_CONFIGURED);
    }
}
