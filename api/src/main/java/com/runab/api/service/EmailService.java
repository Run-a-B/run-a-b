package com.runab.api.service;

import com.runab.api.entity.EmailVerification;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import com.runab.api.repository.EmailVerificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

@Service
@Transactional
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailVerificationRepository verificationRepository;
    private final String username;
    private final String password;
    private final SecureRandom random = new SecureRandom();

    public EmailService(JavaMailSender mailSender, EmailVerificationRepository verificationRepository,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password) {
        this.mailSender = mailSender;
        this.verificationRepository = verificationRepository;
        this.username = username;
        this.password = password;
    }

    // 인증코드 발송
    public void sendCode(String email) {
        if (username.isBlank() || password.isBlank()) {
            throw new BusinessException(ErrorCode.MAIL_NOT_CONFIGURED);
        }
        email = normalize(email);
        // Do not hold a range lock across slow SMTP delivery. Verification/consumption lock the indexed row.
        verificationRepository.findTopByEmailOrderByIdDesc(email).ifPresent(previous -> {
            if (previous.getExpiresAt().minusMinutes(4).isAfter(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.RATE_LIMITED);
            }
        });
        String code = generateCode();

        // DB 저장 (5분 만료)
        EmailVerification verification = EmailVerification.builder()
                .email(email)
                .code(code)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        verificationRepository.save(verification);

        // 메일 발송
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(username);
        message.setTo(email);
        message.setSubject("[Run a B] 이메일 인증 코드");
        message.setText("인증 코드: " + code + "\n\n5분 안에 입력해주세요.");
        try {
            mailSender.send(message);
        } catch (MailException e) {
            throw new BusinessException(ErrorCode.MAIL_DELIVERY_FAILED);
        }
    }

    // 인증코드 확인
    @Transactional(noRollbackFor = BusinessException.class)
    public String verifyCode(String email, String code) {
        EmailVerification verification = verificationRepository
                .findFirstByEmailOrderByIdDesc(normalize(email))
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND));

        if (verification.getFailedAttempts() >= 5) throw new BusinessException(ErrorCode.RATE_LIMITED);
        if (!verification.isValid(code)) {
            verification.failAttempt();
            throw new BusinessException(ErrorCode.VERIFICATION_FAILED);
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String receipt = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        verification.verify(receipt);
        return receipt;
    }

    // 이메일이 인증 완료됐는지 확인 (회원가입 시 사용)
    public void consumeVerification(String email, String receipt) {
        EmailVerification verification = verificationRepository.findFirstByEmailOrderByIdDesc(normalize(email))
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED));
        if (!verification.acceptsReceipt(receipt)) throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        verification.consume();
    }

    // 6자리 코드 생성
    private String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    public static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
