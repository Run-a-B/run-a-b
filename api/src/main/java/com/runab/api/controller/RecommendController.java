package com.runab.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.runab.api.dto.common.ApiResponse;
import com.runab.api.dto.recommend.AiRecommendRequest;
import com.runab.api.service.recommend.AiGatewayClient;
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
    private final AiGatewayClient aiGatewayClient;

    // ===== AI 요청 조립 미리보기 (형식 검증용) =====
    // 인증 사용자 기준. 조립된 요청 JSON을 그대로 돌려준다.
    @PostMapping("/preview")
    public ApiResponse<AiRecommendRequest> preview(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(recommendRequestAssembler.assemble(userId), "조립 완료");
    }

    // ===== AI 추천 실행 =====
    // 사용자 사업정보 기반으로 후보를 조립 → AI Gateway 호출 → 추천 결과(JSON) 반환
    @PostMapping
    public ApiResponse<JsonNode> recommend(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        AiRecommendRequest request = recommendRequestAssembler.assemble(userId);
        JsonNode result = aiGatewayClient.recommend(request);
        return ApiResponse.success(result, "추천 완료");
    }
}
