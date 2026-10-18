package com.runab.api.dto.policy;

import com.runab.api.service.matcher.MatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyDiagnosisResponse {
    private PolicyInfo policy;
    private String verdict;
    private String verdictLabel;
    private Integer score;
    private String confidence;
    private String summary;
    private List<Evidence> evidence;
    private List<String> unknownConditions;
    private List<String> hardFailReasons;
    private boolean hardFail;
    private DataQuality dataQuality;
    private String disclaimer;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyInfo {
        private Long id;
        private String title;
        private String category;
        private String agency;
        private String applicationPeriod;
        private String detailUrl;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Evidence {
        private String key;
        private String label;
        private String userValue;
        private String policyValue;
        private MatchStatus status;
        private String reason;
        private String source;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataQuality {
        private boolean policyCardAvailable;
        private String extractionStatus;
        private String sourceSystem;
        private int verifiedFactCount;
        private int totalFactCount;
    }
}
