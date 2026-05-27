package com.runab.api.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SignupResponse {

    @JsonProperty("temp_token")    // JSON 응답 시 키 이름을 snake_case로
    private String tempToken;
}