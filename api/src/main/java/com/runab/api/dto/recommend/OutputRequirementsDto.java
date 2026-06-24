package com.runab.api.dto.recommend;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;

/**
 * AI 요청의 output_requirements 블록.
 * TODO: 최종 명세 확정 시 필드 보강 (배윤성 매핑표/리포트 포맷).
 */
@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class OutputRequirementsDto {

    private String language;
    private String format;
    private boolean includeReasons;

    public static OutputRequirementsDto defaultRequirements() {
        return OutputRequirementsDto.builder()
                .language("ko")
                .format("structured_report")
                .includeReasons(true)
                .build();
    }
}
