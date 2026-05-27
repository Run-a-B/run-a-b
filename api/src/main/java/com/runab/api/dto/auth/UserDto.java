package com.runab.api.dto.auth;

import com.runab.api.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private Long id;
    private String name;
    private String email;
    private Integer age;
    private String industry;     // BusinessInfo의 jobCategory를 industry로 노출 (프론트 명세)
    private String region;       // BusinessInfo의 region

    // User 엔티티에서 UserDto로 변환 (BusinessInfo 없는 경우 — 회원가입 1단계 직후 등)
    public static UserDto from(User user) {
        return UserDto.builder()
                .id(user.getId())
                .name(user.getUsername())
                .email(user.getEmail())
                .age(user.getAge())
                .industry(null)
                .region(null)
                .build();
    }

    // User + BusinessInfo 둘 다 있을 때 (로그인, 마이페이지 등)
    public static UserDto from(User user, com.runab.api.entity.BusinessInfo businessInfo) {
        return UserDto.builder()
                .id(user.getId())
                .name(user.getUsername())
                .email(user.getEmail())
                .age(user.getAge())
                .industry(businessInfo != null ? businessInfo.getJobCategory() : null)
                .region(businessInfo != null ? businessInfo.getRegion() : null)
                .build();
    }
}