package com.runab.api.exception;


import lombok.Getter;

@Getter
public enum ErrorCode {

    // 공통일때
    UNAUTHORIZED("UNAUTHORIZED", 401, "인증이 필요합니다"),
    FORBIDDEN("FORBIDDEN", 403, "접근 권한이 없습니다"),
    NOT_FOUND("NOT_FOUND", 404, "리소스를 찾을 수 없습니다"),
    VALIDATION_ERROR("VALIDATION_ERROR", 400, "잘못된 요청입니다"),
    INTERNAL_ERROR("INTERNAL_ERROR", 500, "서버 내부 오류"),

    // 인증할때
    DUPLICATE_EMAIL("DUPLICATE_EMAIL", 409, "이미 등록된 이메일입니다"),
    PASSWORD_MISMATCH("PASSWORD_MISMATCH", 400, "비밀번호 확인이 일치하지 않습니다"),
    INVALID_USERNAME("INVALID_USERNAME", 400, "유저 이름 형식이 올바르지 않습니다"),
    INVALID_PASSWORD("INVALID_PASSWORD", 400, "비밀번호 형식이 올바르지 않습니다"),
    USER_NOT_FOUND("USER_NOT_FOUND", 401, "등록되지 않은 이메일입니다"),
    WRONG_PASSWORD("WRONG_PASSWORD", 401, "비밀번호가 올바르지 않습니다"),
    GOOGLE_AUTH_FAILED("GOOGLE_AUTH_FAILED", 401, "구글 인증에 실패했습니다"),

    // 비즈니스일때
    BUSINESS_FIELD_NOT_ALLOWED("BUSINESS_FIELD_NOT_ALLOWED", 400, "사업 준비중 상태에서 매출/직원수는 입력할 수 없습니다"),
    BUSINESS_FIELD_REQUIRED("BUSINESS_FIELD_REQUIRED", 400, "사업 중 상태에서는 매출/직원수가 필수입니다"),
    BUSINESS_INFO_REQUIRED("BUSINESS_INFO_REQUIRED", 400, "사업 정보가 등록되지 않았습니다"),
    INVALID_REGION("INVALID_REGION", 400, "지역값이 올바르지 않습니다"),
    INVALID_JOB_CATEGORY("INVALID_JOB_CATEGORY", 400, "직군값이 올바르지 않습니다"),
    INVALID_BUSINESS_STATUS("INVALID_BUSINESS_STATUS", 400, "사업 상태값이 올바르지 않습니다"),
    POLICY_NOT_FOUND("POLICY_NOT_FOUND", 404, "해당 정책을 찾을 수 없습니다"),
    REPORT_NOT_FOUND("REPORT_NOT_FOUND", 404, "해당 리포트를 찾을 수 없습니다"),
    SUPPORT_NOT_FOUND("SUPPORT_NOT_FOUND", 404, "해당 지원사업을 찾을 수 없습니다"),
    DUPLICATE_REPORT("DUPLICATE_REPORT", 409, "이미 생성된 리포트입니다"),
    AI_SERVICE_UNAVAILABLE("AI_SERVICE_UNAVAILABLE", 503, "AI 서버가 응답하지 않습니다");

    private final String code;
    private final int status;
    private final String message;

    ErrorCode(String code, int status, String message){
        this.code =code;
        this.status= status;
        this.message = message;
    }
}
