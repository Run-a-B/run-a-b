package com.runab.api.dto.recommend;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * EligibilityMatcher 계산 결과.
 * AI 요청의 candidate_policy.match 필드로 그대로 들어간다 (camelCase 유지 — 최종 명세 기준).
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MatchResult {

    private String matcherVersion;       // "eligibility_matcher_v1"
    private String scoringRuleVersion;   // "scoring_rule_v1"

    private boolean hardFail;
    private int score;                   // 최종 점수 (0~100, 반올림 int)
    private int eligibilityScore;        // 자격요건 점수 (0~100)
    private int goalScore;               // 목표 적합도 (MVP 중립값 50)
    private int documentScore;           // 첨부 분석 상태 점수

    // 항목 키들을 상태별로 분류
    private List<String> pass;
    private List<String> partial;
    private List<String> unknown;
    private List<String> fail;
    private List<String> notRequired;

    // required_flags 비교 결과 {flag명: 상태}
    private Map<String, String> requiredFlagCheck;

    // hardFail 사유 (예: "application_period.status=closed", "region_mismatch")
    private List<String> hardFailReasons;
}
