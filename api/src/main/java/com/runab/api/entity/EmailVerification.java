package com.runab.api.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Entity
@Table(name = "email_verification", indexes = @Index(name = "idx_verification_email_id", columnList = "email,id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(length = 64)
    private String receiptHash;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int failedAttempts;

    @Version
    private Long version;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean verified;

    @Builder
    public EmailVerification(String email, String code, LocalDateTime expiresAt) {
        this.email = email;
        this.code = digest(code);
        this.expiresAt = expiresAt;
        this.verified = false;
    }

    // 코드 일치 + 미만료 확인
    public boolean isValid(String inputCode) {
        return !verified && failedAttempts < 5 && this.code.equals(digest(inputCode))
                && LocalDateTime.now().isBefore(this.expiresAt);
    }

    // 인증 완료 처리
    public void verify(String receipt) {
        this.verified = true;
        this.receiptHash = digest(receipt);
    }

    public void failAttempt() { this.failedAttempts++; }

    public boolean acceptsReceipt(String receipt) {
        return receipt != null && !receipt.isBlank() && verified && receiptHash != null
                && LocalDateTime.now().isBefore(expiresAt)
                && MessageDigest.isEqual(receiptHash.getBytes(StandardCharsets.UTF_8),
                    digest(receipt).getBytes(StandardCharsets.UTF_8));
    }

    public void consume() {
        verified = false;
        receiptHash = null;
        expiresAt = LocalDateTime.now();
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
