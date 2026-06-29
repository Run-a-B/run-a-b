package com.runab.api.config;

import com.runab.api.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception{
        http
                // CSRF: REST API는 토큰 기반이라 불필요
                .csrf(AbstractHttpConfigurer::disable)


                // CORS: 프론트 도메인에서 호출 허용
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))


                // 세션: 안 씀
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))


                // 기본 폼 로그인  / HTTP Basic 인증 비활성화  / 이유 : 우린 JWT를 쓰기 때문
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer:: disable)


                // URL별 접근 권한
                .authorizeHttpRequests(auth -> auth
                        // 공개 엔드포인트: 인증 없이 접근 가능
                        .requestMatchers(
                                "/api/v1/auth/signup",
                                "/api/v1/auth/login",
                                "/api/v1/auth/email/send",
                                "/api/v1/auth/email/verify",
                                "/api/v1/auth/google"

                        ).permitAll() //누가 접근 가능한지를 봄

                        // 정책 목록/상세 조회는 비로그인도 가능 (인증 불필요)
                        .requestMatchers(HttpMethod.GET, "/api/v1/policies/**").permitAll()

                        // bizinfo 동기화: 관리자성 작업이나 발표 데모 편의상 일단 permitAll
                        // TODO: 추후 ADMIN 권한으로 제한 필요
                        .requestMatchers(HttpMethod.POST, "/api/v1/policies/sync").permitAll()

                        // 회원가입 2단계: TEMP 토큰 가진 사람만함
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup/business").hasRole("TEMP")

                        // 나머지는 ACCESS 토큰 필요함
                        .anyRequest().hasRole("ACCESS") // ACCESS 토큰 보유자만
                )

                // JWT 필터를 기본 인증 필터 앞에 끼워넣기ㅎ
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 비밀 번호 해싱용(비스크립트)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // CORS 설정 (프론트랑 통신 허용)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:3000",   // React 로컬 개발
                "http://localhost:5173",   // Vite 로컬 개발
                "https://run-a-b.com",     // 배포 도메인
                "https://www.run-a-b.com"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}
