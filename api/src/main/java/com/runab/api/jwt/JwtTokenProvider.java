package com.runab.api.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @Value("${jwt.temp-token-expiration-ms}")
    private long tempTokenExpirationMs;

    private SecretKey key;

    // 빈 생성 후 시크릿 키를 SecretKey 객체로 변환
    @PostConstruct
    protected void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    //토큰 발급

    // 정식 Access Token 발급
    public String createAccessToken(Long userId) {
        return buildToken(userId, "ACCESS", accessTokenExpirationMs);
    }

    // 임시 토큰 발급
    public String createTempToken(Long userId) {
        return buildToken(userId, "TEMP", tempTokenExpirationMs);
    }

    // 실제 토큰을 만드는 공통 메서드
    private String buildToken(Long userId, String tokenType, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))   // 누구의 토큰인가 페이로드의 sub 클레임임
                .claim("type", tokenType)          // 커스텀 클레임: ACCESS or TEMP 로함
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)                     // 시크릿 키로 사인
                .compact();
    }

    // ===== 토큰 검증 =====

    // 토큰이 유효한지 검증 (서명·만료 둘 다 체크)
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            // 만료된 토큰
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            // 서명 위조, 형식 오류 등
            return false;
        }
    }

    // ===== 토큰에서 정보 추출 =====

    // 토큰에서 사용자 ID 꺼내기
    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    // 토큰 종류 꺼내기 (ACCESS or TEMP)
    public String getTokenType(String token) {
        Claims claims = parseClaims(token);
        return claims.get("type", String.class);
    }

    // 파싱 공통 메서드
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}