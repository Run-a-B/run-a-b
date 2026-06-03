package com.runab.api.service;

import com.runab.api.entity.EmailVerification;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import com.runab.api.repository.EmailVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Transactional
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailVerificationRepository verificationRepository;

    // 인증코드 발송
    public void sendCode(String email) {
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
        message.setTo(email);
        message.setSubject("[Run a B] 이메일 인증 코드");
        message.setText("인증 코드: " + code + "\n\n5분 안에 입력해주세요.");
        mailSender.send(message);
    }

    // 인증코드 확인
    public void verifyCode(String email, String code) {
        EmailVerification verification = verificationRepository
                .findTopByEmailOrderByIdDesc(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND));

        if (!verification.isValid(code)) {
            throw new BusinessException(ErrorCode.VERIFICATION_FAILED);
        }
        verification.verify();
    }

    // 이메일이 인증 완료됐는지 확인 (회원가입 시 사용)
    @Transactional(readOnly = true)
    public boolean isVerified(String email) {
        return verificationRepository.findTopByEmailOrderByIdDesc(email)
                .map(EmailVerification::isVerified)
                .orElse(false);
    }

    // 6자리 코드 생성
    private String generateCode() {
        return String.valueOf(new Random().nextInt(900000) + 100000);
    }
}
