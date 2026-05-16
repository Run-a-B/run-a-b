package com.runab.api.exception;


import com.runab.api.dto.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1  우리가 직접 던지는 비즈니스 예외처리
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        return ResponseEntity
                .status(ec.getStatus())
                .body(ApiResponse.error(ec.getCode(), ec.getStatus(), ec.getMessage()));
    }

    // 2) @Valid 검증 실패 (요청 파라미터 validation)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("잘못된 요청입니다");
        return ResponseEntity
                .status(400)
                .body(ApiResponse.error("VALIDATION_ERROR", 400, message));
    }

    // 3) 그 외 예상 못한 모든 예외처릴
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleAll(Exception e) {
        e.printStackTrace();  // 개발 중엔 에러 추적 위해 콘솔에 찍음 (실제로 서비스 시작하면 지워도됨)
        return ResponseEntity
                .status(500)
                .body(ApiResponse.error("INTERNAL_ERROR", 500, "서버 내부 오류가 발생했습니다"));
    }


}
