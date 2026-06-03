package com.runab.api.dto.user;

import com.runab.api.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserDetailResponse {

    private Long id;
    private String name;
    private String email;
    private Integer age;

    public static UserDetailResponse from(User user) {
        return UserDetailResponse.builder()
                .id(user.getId())
                .name(user.getUsername())
                .email(user.getEmail())
                .age(user.getAge())
                .build();
    }
}