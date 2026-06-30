package com.runab.api.service.matcher;

/**
 * 자격요건 항목별 매칭 상태 (배윤성 명세 5종 고정).
 * 임의 문자열 금지 — 반드시 이 enum만 사용한다.
 */
public enum MatchStatus {
    PASS,          // 사용자 정보가 정책 조건 충족
    PARTIAL,       // 완전일치 아니나 넓은 범위 가능성 (예: 업종 대분류만 일치)
    FAIL,          // 명확히 불일치
    UNKNOWN,       // 판단에 필요한 사용자 정보 또는 정책 조건이 없음
    NOT_REQUIRED   // 정책에 해당 조건 자체가 없음
}
