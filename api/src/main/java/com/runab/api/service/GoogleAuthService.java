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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final BusinessInfoRepository businessInfoRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final GoogleIdentityVerifier identityVerifier;

    @Transactional
    public LoginResponse googleLogin(GoogleLoginRequest request) {
        GoogleIdentityVerifier.VerifiedIdentity identity = identityVerifier.verify(request.getIdToken());
        String email = EmailService.normalize(identity.email());

        // Google의 고정 식별자로 기존 계정을 찾는다. 이메일만으로 다른 로그인 계정에 연결하지 않는다.
        User user = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, identity.subject()).orElse(null);
        if (user != null && user.isDeleted()) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        User emailOwner = userRepository.findByEmail(email).orElse(null);
        if (emailOwner != null) {
            if (emailOwner.isDeleted()) throw new BusinessException(ErrorCode.UNAUTHORIZED);
            if (user == null || !emailOwner.getId().equals(user.getId())) {
                throw new BusinessException(ErrorCode.GOOGLE_ACCOUNT_CONFLICT);
            }
        }
        if (user == null) {
            user = User.builder()
                    .email(email)
                    .username(displayName(identity.name(), email))
                    .password(null)
                    .provider(AuthProvider.GOOGLE)
                    .providerId(identity.subject())
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

    private static String displayName(String name, String email) {
        String candidate = name == null || name.isBlank() ? email.split("@", 2)[0] : name.strip();
        return candidate.substring(0, candidate.offsetByCodePoints(0, Math.min(20, candidate.codePointCount(0, candidate.length()))));
    }
}
