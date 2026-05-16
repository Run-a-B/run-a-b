package com.runab.api.dto.common;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // error필드가 null이면 json 응답에서 아예 빼버린다.
public class ApiResponse<T> {  // <T>은 제네릭 으로 어떤 데이터 타입이든 담을 수 있는 역할 (User, Policy, List등)

    private boolean success;
    private T data;
    private String message;
    private ErrorDetail error;

    // 성공 응답이 오면 -->  데이터 + 메시지를 줌
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, null);
    }

    // 성공 응답이 오면 -->  데이터만 줌
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, "요청 성공", null);
    }

    // 성공 응답이 오면 -->  메시지만 줌
    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, null, message, null);
    }

    // 에러응답
    public static <T> ApiResponse<T> error(String code, int status, String message) {
        return new ApiResponse<>(false, null, message, new ErrorDetail(code, status));
    }
    // ----------------------------------------------------------------------------------------------------------------
    @Getter
    @AllArgsConstructor
    public static class ErrorDetail {
        private String code;
        private int status;
    }

    // 여기서 static을 쓰는 이유는 ErrorDetail은 보조 클래스이기 때문에 데이터의 묶음으로
    // ApiResponse 인스턴스랑 굳이 묵을 필요가 없기에 static으로 독립
}
