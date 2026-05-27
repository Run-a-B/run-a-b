package com.runab.api.dto.auth;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SignupRequest {

    @NotBlank(message = "이름을 입력해주세요")
    @Size(min = 2, max = 20, message = "이름은 2~20자로 입력해주세요")
    private String name;

    @NotBlank(message = "이메일을 입력해주세요")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    private String email;

    @Min(value = 1, message = "올바른 나이를 입력해주세요")
    @Max(value = 120, message = "올바른 나이를 입력해주세요")
    private Integer age;   // 선택 항목이라 @NotNull 없음

    @NotBlank(message = "비밀번호를 입력해주세요")
    @Pattern(
            regexp = "^[A-Za-z0-9!@$*]{6,20}$",
            message = "비밀번호는 영어·숫자·특수문자(!@$*) 6~20자여야 합니다"
    )
    private String password;

    @NotBlank(message = "비밀번호 확인을 입력해주세요")
    private String passwordConfirm;
}