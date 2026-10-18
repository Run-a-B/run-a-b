package com.runab.api.dto.email;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EmailVerifyRequest {

    @NotBlank(message = "이메일을 입력해주세요")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    @Size(max = 100)
    private String email;

    @NotBlank(message = "인증 코드를 입력해주세요")
    @Pattern(regexp = "[0-9]{6}", message = "6자리 인증 코드를 입력해주세요")
    private String code;
}
