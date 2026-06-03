package com.runab.api.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GoogleLoginRequest {

    @JsonProperty("id_token")
    @NotBlank(message = "구글 토큰이 필요합니다")
    private String idToken;
}