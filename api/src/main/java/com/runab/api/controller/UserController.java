package com.runab.api.controller;

import com.runab.api.dto.common.ApiResponse;
import com.runab.api.dto.user.*;
import com.runab.api.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ===== 1) 내 정보 조회 =====
    @GetMapping("/me")
    public ApiResponse<UserDetailResponse> getMyInfo(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(userService.getMyInfo(userId));
    }

    // ===== 2) 내 정보 수정 =====
    @PatchMapping("/me")
    public ApiResponse<UserDetailResponse> updateMyInfo(
            @Valid @RequestBody UserUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(userService.updateMyInfo(userId, request), "정보가 수정되었습니다");
    }

    // ===== 3) 내 사업 정보 조회 =====
    @GetMapping("/me/business")
    public ApiResponse<BusinessInfoResponse> getMyBusinessInfo(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(userService.getMyBusinessInfo(userId));
    }

    // ===== 4) 내 사업 정보 수정 =====
    @PatchMapping("/me/business")
    public ApiResponse<BusinessInfoResponse> updateMyBusinessInfo(
            @Valid @RequestBody BusinessInfoUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(userService.updateMyBusinessInfo(userId, request), "사업 정보가 수정되었습니다");
    }
}