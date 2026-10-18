package com.runab.api.repository;

import com.runab.api.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    // 해당 이메일의 가장 최근 인증 레코드
    Optional<EmailVerification> findTopByEmailOrderByIdDesc(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerification> findFirstByEmailOrderByIdDesc(String email);
}
