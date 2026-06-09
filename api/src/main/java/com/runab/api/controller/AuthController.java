package com.runab.api.controller;

import com.runab.api.dto.auth.*;
import com.runab.api.dto.common.ApiResponse;
import com.runab.api.service.AuthService;
import com.runab.api.service.GoogleAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;

    //  회원가입 1단계
    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ApiResponse.success(response, "1단계 회원가입이 완료되었습니다");
    }

    // 회원가입 2단계
    // SecurityConfig에서 hasRole("TEMP") 로 막아둠 → TEMP 토큰만 통과
    @PostMapping("/signup/business")
    public ApiResponse<LoginResponse> signupBusiness(
            @Valid @RequestBody SignupBusinessRequest request,
            Authentication authentication) {

        // TEMP 토큰에서 추출된 userId (JwtAuthenticationFilter가 미리 넣어둠)
        Long userId = (Long) authentication.getPrincipal();

        LoginResponse response = authService.signupBusiness(userId, request);
        return ApiResponse.success(response, "회원가입이 완료되었습니다");
    }

    // 로그인
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ApiResponse.success(response, "로그인 성공");
    }

    // 구글 로그인
    @PostMapping("/google")
    public ApiResponse<LoginResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return ApiResponse.success(googleAuthService.googleLogin(request), "구글 로그인 성공");
    }

    //  로그아웃
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.success("로그아웃 되었습니다");
    }


}