package com.runab.api.service;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BusinessFieldConverter {

    // 프론트 매출 옵션 → DB 저장용 숫자 (중앙값 추정)
    private static final Map<String, Long> REVENUE_MAP = Map.of(
            "1,000만원 미만", 5_000_000L,
            "1,000만원~5,000만원", 30_000_000L,
            "5,000만원~1억원", 75_000_000L,
            "1억원~3억원", 200_000_000L,
            "3억원~5억원", 400_000_000L,
            "5억원 이상", 500_000_000L
    );

    // 프론트 직원수 옵션 → DB 저장용 숫자
    private static final Map<String, Integer> EMPLOYEE_MAP = Map.of(
            "없음 (나 혼자)", 0,
            "1~4명", 2,
            "5~9명", 7,
            "10~29명", 20,
            "30명 이상", 50
    );

    public Long parseRevenue(String text) {
        if (text == null || text.isBlank()) return null;
        return REVENUE_MAP.get(text);   // 매핑 없으면 null
    }

    public Integer parseEmployeeCount(String text) {
        if (text == null || text.isBlank()) return null;
        return EMPLOYEE_MAP.get(text);
    }

    // 역변환 — DB의 숫자를 프론트 텍스트로 (마이페이지 조회 시 필요)
    public String formatRevenue(Long revenue) {
        if (revenue == null) return null;
        if (revenue < 10_000_000) return "1,000만원 미만";
        if (revenue < 50_000_000) return "1,000만원~5,000만원";
        if (revenue < 100_000_000) return "5,000만원~1억원";
        if (revenue < 300_000_000) return "1억원~3억원";
        if (revenue < 500_000_000) return "3억원~5억원";
        return "5억 이상";
    }

    public String formatEmployeeCount(Integer count) {
        if (count == null) return null;
        if (count == 0) return "없음 (나 혼자)";
        if (count <= 4) return "1~4명";
        if (count <= 9) return "5~9명";
        if (count <= 29) return "10~29명";
        return "30명 이상";
    }
}