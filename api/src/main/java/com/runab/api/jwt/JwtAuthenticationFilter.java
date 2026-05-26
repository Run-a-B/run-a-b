package com.runab.api.jwt;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor // final 필드를 받는 생성자 자동 생성
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    //------------------------------------------------------------------------------------------------------------------------

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 1 . Authorization 헤더에서 토큰 꺼내기
        String token  = resolveToken(request);

    //------------------------------------------------------------------------------------------------------------------------

        // 2 토큰이 유효하면 시큐리티 컨텍스트에서 사용자 정보 저장을 함
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)){
            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            String tokenType = jwtTokenProvider.getTokenType(token);

            // 유저 아아디를 principal로 저장. 권한은 토큰 종류로 구분 (ROLE_ACCESS, ROLE_TEMP)
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userId, // principals : userId
                            null, // credentials :  비번 같은 거 (불필요)
                            Collections.singletonList(
                                    () -> "ROLE_" + tokenType // 권한 : ROLE_ACCESS or ROLE_TEMP
                            )
                    );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

    //------------------------------------------------------------------------------------------------------------------------

        // 3) 다음 필터로 넘겨버림 (토큰 없거나 무효해도 통과 — 컨트롤러나 SecurityConfig에서 인증 필요 여부 판단)
        filterChain.doFilter(request, response);
    }

    //------------------------------------------------------------------------------------------------------------------------

    // "Authorization: Bearer "~~~~~~.~~~~.~~~~" 헤더에서 가운데 헤더값만 꺼내오기
    private String resolveToken(HttpServletRequest request){
        String bearerToken = request.getHeader("Authorization");
        if(StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // "Bearer " 7글자만 떼어냄
        }
        return null;
    }

}
