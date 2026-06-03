package com.runab.api.controller;

import com.runab.api.dto.common.ApiResponse;
import com.runab.api.dto.email.EmailSendRequest;
import com.runab.api.dto.email.EmailVerifyRequest;
import com.runab.api.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/email")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    // 인증코드 발송
    @PostMapping("/send")
    public ApiResponse<Void> sendCode(@Valid @RequestBody EmailSendRequest request) {
        emailService.sendCode(request.getEmail());
        return ApiResponse.success("인증 코드가 발송되었습니다");
    }

    // 인증코드 확인
    @PostMapping("/verify")
    public ApiResponse<Void> verifyCode(@Valid @RequestBody EmailVerifyRequest request) {
        emailService.verifyCode(request.getEmail(), request.getCode());
        return ApiResponse.success("이메일 인증이 완료되었습니다");
    }
}
