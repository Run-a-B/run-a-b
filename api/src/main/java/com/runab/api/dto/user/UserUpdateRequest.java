package com.runab.api.dto.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserUpdateRequest {

    @Size(min = 2, max = 20, message = "이름은 2~20자로 입력해주세요")
    private String name;   // null이면 변경 안하게 설정


    @Min(value = 1, message = "올바른 나이를 입력해주세요")
    @Max(value = 120, message = "올바른 나이를 입력해주세요")
    private Integer age;   // null이면 변경 안 함


    // 비밀번호 변경  -> 둘다 입력
    private String currentPassword;

    @Pattern(
            regexp = "^[A-Za-z0-9!@$*]{6,20}$",
            message = "비밀번호는 영어·숫자·특수문자(!@$*) 6~20자여야 합니다"
    )
    private String newPassword;
}
