package com.runab.api.dto.recommend;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * AI 요청의 pipeline 블록. 현재는 1차 랭킹 패스.
 */
@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PipelineDto {

    private String currentPass;
    private List<String> tasks;

    public static PipelineDto defaultPipeline() {
        return PipelineDto.builder()
                .currentPass("pass1_ranking")
                .tasks(List.of("pass1_ranking", "pass2_report"))
                .build();
    }
}
