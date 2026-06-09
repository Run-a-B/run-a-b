package com.runab.api.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final BusinessInfoRepository businessInfoRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public LoginResponse googleLogin(GoogleLoginRequest request) {
        // 구글 access_token으로 유저 정보 조회
        Map<String, Object> userInfo = fetchGoogleUserInfo(request.getIdToken());

        String email = (String) userInfo.get("email");
        String name = (String) userInfo.get("name");
        String providerId = (String) userInfo.get("sub");

        if (email == null) {
            throw new BusinessException(ErrorCode.GOOGLE_AUTH_FAILED);
        }

        // 기존 회원이면 로그인, 없으면 자동 가입
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
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

        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        BusinessInfo businessInfo = businessInfoRepository.findByUser(user).orElse(null);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .user(UserDto.from(user, businessInfo))
                .build();
    }

    // 구글 access_token → userinfo 조회
    private Map<String, Object> fetchGoogleUserInfo(String accessToken) {
        try {
            RestClient restClient = RestClient.create();
            return restClient.get()
                    .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }
}