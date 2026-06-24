package com.runab.api.controller;

import com.runab.api.dto.common.ApiResponse;
import com.runab.api.dto.recommend.AiRecommendRequest;
import com.runab.api.service.recommend.RecommendRequestAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendRequestAssembler recommendRequestAssembler;

    // ===== AI 요청 조립 미리보기 (형식 검증용) =====
    // 인증 사용자 기준. 실제 AI 전송은 다음 단계 — 여기선 조립된 요청 JSON을 그대로 돌려준다.
    @PostMapping("/preview")
    public ApiResponse<AiRecommendRequest> preview(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(recommendRequestAssembler.assemble(userId), "조립 완료");
    }
}
