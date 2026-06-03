package com.runab.api.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.runab.api.dto.auth.GoogleLoginRequest;
import com.runab.api.dto.auth.LoginResponse;
import com.runab.api.dto.auth.UserDto;
import com.runab.api.entity.AuthProvider;
import com.runab.api.entity.BusinessInfo;
import com.runab.api.entity.User;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import com.runab.api.jwt.JwtTokenProvider;
import com.runab.api.repository.BusinessInfoRepository;
import com.runab.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final BusinessInfoRepository businessInfoRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${google.client-id}")
    private String googleClientId;

    @Transactional
    public LoginResponse googleLogin(GoogleLoginRequest request) {
        // 1) 구글 ID 토큰 검증
        GoogleIdToken.Payload payload = verifyToken(request.getIdToken());

        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String providerId = payload.getSubject();

        // 2) 기존 회원이면 로그인, 없으면 자동 가입
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // 신규 → 구글 회원 자동 생성 (비번 없음)
            user = User.builder()
                    .email(email)
                    .username(name != null ? name : email.split("@")[0])
                    .password(null)
                    .provider(AuthProvider.GOOGLE)
                    .providerId(providerId)
                    .age(null)
                    .build();
            user = userRepository.save(user);
        }

        // 3) ACCESS 토큰 발급
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());

        // 4) 사업 정보 같이 조회
        BusinessInfo businessInfo = businessInfoRepository.findByUser(user).orElse(null);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .user(UserDto.from(user, businessInfo))
                .build();
    }

    // 구글 ID 토큰 검증
    private GoogleIdToken.Payload verifyToken(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new BusinessException(ErrorCode.GOOGLE_AUTH_FAILED);
            }
            return idToken.getPayload();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }
}