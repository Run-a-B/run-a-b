package com.runab.api.service;

import com.runab.api.dto.auth.*;
import com.runab.api.entity.AuthProvider;
import com.runab.api.entity.BusinessInfo;
import com.runab.api.entity.User;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import com.runab.api.jwt.JwtTokenProvider;
import com.runab.api.repository.BusinessInfoRepository;
import com.runab.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final BusinessInfoRepository businessInfoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final BusinessFieldConverter converter;
    private final EmailService emailService;

    // ===== 1) 회원가입 1단계 =====
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        // 1-1) 비밀번호 일치 확인
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 1-2) 이메일 중복 체크
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        // 1-2.5) 이메일 인증 여부 확인
        if (!emailService.isVerified(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        // 1-3) 비밀번호 해싱 후 User 저장
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .username(request.getName())
                .age(request.getAge())
                .provider(AuthProvider.LOCAL)
                .providerId(null)
                .build();
        User savedUser = userRepository.save(user);

        // 1-4) TEMP 토큰 발급 (2단계 진행 자격)
        String tempToken = jwtTokenProvider.createTempToken(savedUser.getId());

        return SignupResponse.builder()
                .tempToken(tempToken)
                .build();
    }

    // ===== 2) 회원가입 2단계 (사업 정보 등록) =====
    @Transactional
    public LoginResponse signupBusiness(Long userId, SignupBusinessRequest request) {
        // 2-1) 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2-2) 비즈니스 규칙 검증 (사업 중인데 매출/직원수 없으면 에러)
        if (Boolean.TRUE.equals(request.getBusinessStatus())) {
            if (request.getAnnualRevenue() == null || request.getEmployeeCount() == null) {
                throw new BusinessException(ErrorCode.BUSINESS_FIELD_REQUIRED);
            }
        }

        // 2-3) 텍스트 → 숫자 변환
        Long annualRevenue = converter.parseRevenue(request.getAnnualRevenue());
        Integer employeeCount = converter.parseEmployeeCount(request.getEmployeeCount());

        // 2-4) BusinessInfo 저장
        BusinessInfo businessInfo = BusinessInfo.builder()
                .user(user)
                .businessStatus(request.getBusinessStatus())
                .jobCategory(request.getJobCategory())
                .region(request.getRegion())
                .annualRevenue(annualRevenue)
                .employeeCount(employeeCount)
                .build();
        businessInfoRepository.save(businessInfo);

        // 2-5) ACCESS 토큰 발급
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .user(UserDto.from(user, businessInfo))
                .build();
    }

    // ===== 3) 로그인 =====
    public LoginResponse login(LoginRequest request) {
        // 3-1) 이메일로 사용자 찾기
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 3-2) 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.WRONG_PASSWORD);
        }

        // 3-3) ACCESS 토큰 발급
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());

        // 3-4) 사업 정보 같이 조회 (있으면 응답에 포함)
        BusinessInfo businessInfo = businessInfoRepository.findByUser(user).orElse(null);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .user(UserDto.from(user, businessInfo))
                .build();
    }

    // ===== 4) 로그아웃 =====
    // JWT는 stateless라 서버에서 할 게 거의 없음. 프론트가 토큰 제거하면 끝.
    // 진짜 무효화를 원하면 블랙리스트(Redis)가 필요하지만, capstone scope에선 생략.
    public void logout() {
        // 의도적으로 비워둠. 프론트가 localStorage에서 토큰 제거.
    }
}
