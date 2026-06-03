package com.runab.api.repository;

import com.runab.api.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    // 해당 이메일의 가장 최근 인증 레코드
    Optional<EmailVerification> findTopByEmailOrderByIdDesc(String email);
}
