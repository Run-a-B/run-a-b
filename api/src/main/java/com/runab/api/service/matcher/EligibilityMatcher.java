package com.runab.api.service.matcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.runab.api.dto.recommend.MatchResult;
import com.runab.api.entity.BusinessInfo;
import com.runab.api.entity.PolicyCard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 자격요건 매칭 점수 계산 (배윤성 확정 공식).
 * 순수 계산 로직 — DB 등 외부 의존성 없음(ObjectMapper만). 단위테스트 용이.
 *
 * 핵심 원칙: 자격요건 데이터(PolicyCard.fullJson)가 비어 있어도 예외 없이 동작하고,
 * 판단 불가 항목은 전부 UNKNOWN으로 처리한다. (데이터는 배포 후 extractor가 채움)
 */
@Slf4j
@Component
public class EligibilityMatcher {

    public static final String MATCHER_VERSION = "eligibility_matcher_v1";
    public static final String SCORING_RULE_VERSION = "scoring_rule_v1";

    // eligibilityScore 항목별 배점 (합 100)
    private static final double W_REGION = 20;
    private static final double W_INDUSTRY = 20;
    private static final double W_DATE = 10;
    private static final double W_BUSINESS_STAGE = 10;
    private static final double W_BUSINESS_TYPE = 8;
    private static final double W_OWNER_TYPE = 8;
    private static final double W_REVENUE = 8;
    private static final double W_EMPLOYEES = 8;
    private static final double W_BUSINESS_MONTHS = 4;
    private static final double W_AGE = 4;

    // 사업 단계 동의어 (card business_stages 값과 user businessStatus 매칭용)
    private static final Set<String> OPERATING_SYNONYMS = Set.of("운영중", "사업중", "영업중", "operating");
    private static final Set<String> PREPARING_SYNONYMS = Set.of("준비중", "예비", "예비창업", "preparing");

    private final ObjectMapper objectMapper;

    public EligibilityMatcher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MatchResult match(BusinessInfo user, PolicyCard card) {
        JsonNode root = parse(card);
        JsonNode elig = root.path("eligibility");

        // 상태별 항목 키 분류 버킷
        List<String> pass = new ArrayList<>();
        List<String> partial = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        List<String> fail = new ArrayList<>();
        List<String> notRequired = new ArrayList<>();
        List<String> hardFailReasons = new ArrayList<>();

        // 누적기 (항목별 status + 배점을 받아 가중점수 합산 + 버킷 분류)
        Scorer scorer = new Scorer(pass, partial, unknown, fail, notRequired);

        // ---- region (20) ----
        MatchStatus regionStatus = evalRegion(elig, user.getRegion(), hardFailReasons);
        scorer.add("region", regionStatus, W_REGION, true);

        // ---- industry (20) ----
        MatchStatus industryStatus = evalIndustry(elig, user.getJobCategory());
        scorer.add("industry", industryStatus, W_INDUSTRY, true);

        // ---- date (10) : application_period.status와 반드시 일치 (규칙1·2) ----
        MatchStatus dateStatus = evalDate(root, card, hardFailReasons);
        scorer.add("date", dateStatus, W_DATE, false);

        // ---- business_stage (10) ----
        scorer.add("business_stage", evalBusinessStage(elig, user.getBusinessStatus()), W_BUSINESS_STAGE, false);

        // ---- business_type (8) : user에 business_type 필드 없음 → UNKNOWN. TODO: 소상공인⊂중소기업 위계 ----
        scorer.add("business_type", presenceOnly(list(elig, "business_types")), W_BUSINESS_TYPE, false);

        // ---- owner_type (8) : user 정보 없음 → UNKNOWN ----
        scorer.add("owner_type", presenceOnly(list(elig, "owner_types")), W_OWNER_TYPE, false);

        // ---- revenue (8) ----
        scorer.add("revenue", evalMax(longOrNull(elig, "max_revenue"),
                user.getAnnualRevenue()), W_REVENUE, false);

        // ---- employees (8) ----
        scorer.add("employees", evalMax(longOrNull(elig, "max_employees"),
                user.getEmployeeCount() == null ? null : user.getEmployeeCount().longValue()), W_EMPLOYEES, false);

        // ---- business_months (4) : user 정보 없음 ----
        scorer.add("business_months", presenceOnlyAny(elig, "min_business_months", "max_business_months"),
                W_BUSINESS_MONTHS, false);

        // ---- age (4) : user 정보 없음 ----
        scorer.add("age", presenceOnlyAny(elig, "min_age", "max_age"), W_AGE, false);

        // ---- required_flags (규칙5) : user 정보 없으면 UNKNOWN으로 표시 ----
        Map<String, String> requiredFlagCheck = new LinkedHashMap<>();
        for (String flag : list(elig, "required_flags")) {
            requiredFlagCheck.put(flag, MatchStatus.UNKNOWN.name());
        }

        int eligibilityScore = (int) Math.round(scorer.weightedSum());
        int goalScore = computeGoalScore(root);
        int documentScore = computeDocumentScore(root);

        boolean hardFail = !hardFailReasons.isEmpty();
        int score = (int) Math.round(eligibilityScore * 0.70 + goalScore * 0.20 + documentScore * 0.10);

        return MatchResult.builder()
                .matcherVersion(MATCHER_VERSION)
                .scoringRuleVersion(SCORING_RULE_VERSION)
                .hardFail(hardFail)
                .score(score)
                .eligibilityScore(eligibilityScore)
                .goalScore(goalScore)
                .documentScore(documentScore)
                .pass(pass)
                .partial(partial)
                .unknown(unknown)
                .fail(fail)
                .notRequired(notRequired)
                .requiredFlagCheck(requiredFlagCheck)
                .hardFailReasons(hardFailReasons)
                .build();
    }

    // ===== 항목별 평가 =====

    // 규칙3·6: regions=[]+unknown은 PASS 금지(UNKNOWN), ["전국"]/nationwide와 혼동 금지
    private MatchStatus evalRegion(JsonNode elig, String userRegion, List<String> hardFailReasons) {
        List<String> regions = list(elig, "regions");
        String type = text(elig, "region_condition_type", "unknown");

        if ("nationwide".equals(type) || regions.contains("전국")) {
            return MatchStatus.PASS; // 전국 → 무조건 충족
        }
        if (regions.isEmpty()) {
            return MatchStatus.UNKNOWN; // 미추출 (전국 아님)
        }
        if (userRegion == null || userRegion.isBlank()) {
            return MatchStatus.UNKNOWN;
        }
        if (regions.contains(userRegion)) {
            return MatchStatus.PASS;
        }
        // 지역 불일치는 hard (규칙: hardFail=true)
        // 권역 매핑(수도권 등 PARTIAL)은 추후. MVP는 단순 일치만.
        hardFailReasons.add("region_mismatch");
        return MatchStatus.FAIL;
    }

    // 규칙4: industries=[]+unknown은 PASS 금지(UNKNOWN). 불일치는 soft(hardFail 아님)
    private MatchStatus evalIndustry(JsonNode elig, String userIndustry) {
        List<String> industries = list(elig, "industries");
        if (industries.isEmpty()) {
            return MatchStatus.UNKNOWN;
        }
        if (userIndustry == null || userIndustry.isBlank()) {
            return MatchStatus.UNKNOWN;
        }
        // 동의어/표준산업분류 비교는 추후. MVP는 문자열 일치만.
        return industries.contains(userIndustry) ? MatchStatus.PASS : MatchStatus.FAIL;
    }

    // 규칙1·2: closed면 date=FAIL + hardFail. open이면 PASS. 없으면 UNKNOWN
    private MatchStatus evalDate(JsonNode root, PolicyCard card, List<String> hardFailReasons) {
        String status = text(root.path("application_period"), "status", null);
        if (status == null && card != null) {
            status = card.getApplicationStatus(); // fullJson에 없으면 컬럼값 폴백
        }
        if ("closed".equals(status)) {
            hardFailReasons.add("application_period.status=closed");
            return MatchStatus.FAIL;
        }
        if ("open".equals(status)) {
            return MatchStatus.PASS;
        }
        return MatchStatus.UNKNOWN;
    }

    private MatchStatus evalBusinessStage(JsonNode elig, Boolean businessStatus) {
        List<String> stages = list(elig, "business_stages");
        if (stages.isEmpty()) {
            return MatchStatus.NOT_REQUIRED;
        }
        if (businessStatus == null) {
            return MatchStatus.UNKNOWN;
        }
        Set<String> synonyms = businessStatus ? OPERATING_SYNONYMS : PREPARING_SYNONYMS;
        for (String stage : stages) {
            if (synonyms.contains(stage)) {
                return MatchStatus.PASS;
            }
        }
        return MatchStatus.FAIL; // soft (hardFail 아님)
    }

    // card 조건은 있으나 user 정보가 없는 항목: 조건 있으면 UNKNOWN, 없으면 NOT_REQUIRED
    private MatchStatus presenceOnly(List<String> cardValues) {
        return cardValues.isEmpty() ? MatchStatus.NOT_REQUIRED : MatchStatus.UNKNOWN;
    }

    private MatchStatus presenceOnlyAny(JsonNode elig, String... fields) {
        for (String f : fields) {
            if (longOrNull(elig, f) != null) {
                return MatchStatus.UNKNOWN;
            }
        }
        return MatchStatus.NOT_REQUIRED;
    }

    // max 조건 비교: card 한도 없으면 NOT_REQUIRED, user 값 없으면 UNKNOWN, 이하면 PASS 아니면 FAIL(soft)
    private MatchStatus evalMax(Long cardMax, Long userValue) {
        if (cardMax == null) {
            return MatchStatus.NOT_REQUIRED;
        }
        if (userValue == null) {
            return MatchStatus.UNKNOWN;
        }
        return userValue <= cardMax ? MatchStatus.PASS : MatchStatus.FAIL;
    }

    // goalScore: user main_business_goal이 아직 BusinessInfo에 없음 → MVP 중립값 50.
    // TODO: 배윤성 매핑표(Map<goal, Map<benefitType, score>>) 도착하면 benefit.benefit_type으로 환산.
    private int computeGoalScore(JsonNode root) {
        return 50;
    }

    private int computeDocumentScore(JsonNode root) {
        String status = text(root.path("attachment_analysis"), "status", null);
        if (status == null) {
            return 50;
        }
        return switch (status) {
            case "documents_found" -> 90;
            case "documents_not_found" -> 50;
            case "pending_hwp_parse" -> 30;
            case "attachments_found_text_extraction_failed" -> 40;
            case "crawl_failed" -> 30;
            default -> 50;
        };
    }

    // ===== JSON 파싱 헬퍼 (절대 예외 던지지 않음) =====

    private JsonNode parse(PolicyCard card) {
        if (card == null || card.getFullJson() == null || card.getFullJson().isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }
        try {
            return objectMapper.readTree(card.getFullJson());
        } catch (Exception e) {
            log.warn("[matcher] fullJson 파싱 실패 (policyId={}) → 전부 UNKNOWN 처리: {}",
                    card.getPolicyId(), e.getMessage());
            return JsonNodeFactory.instance.objectNode();
        }
    }

    private List<String> list(JsonNode node, String field) {
        List<String> result = new ArrayList<>();
        JsonNode arr = node.path(field);
        if (arr.isArray()) {
            arr.forEach(n -> result.add(n.asText()));
        }
        return result;
    }

    private String text(JsonNode node, String field, String def) {
        JsonNode n = node.path(field);
        return (n.isMissingNode() || n.isNull()) ? def : n.asText();
    }

    private Long longOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return (n.isMissingNode() || n.isNull()) ? null : n.asLong();
    }

    // status → 가중점수 환산 + 버킷 분류
    private static class Scorer {
        private final List<String> pass, partial, unknown, fail, notRequired;
        private double sum = 0;

        Scorer(List<String> pass, List<String> partial, List<String> unknown,
               List<String> fail, List<String> notRequired) {
            this.pass = pass;
            this.partial = partial;
            this.unknown = unknown;
            this.fail = fail;
            this.notRequired = notRequired;
        }

        // important(region/industry)는 UNKNOWN 감점이 더 큼(30%), 나머지는 40%
        void add(String name, MatchStatus status, double maxPoints, boolean important) {
            double fraction = switch (status) {
                case PASS, NOT_REQUIRED -> 1.0;
                case PARTIAL -> 0.5;                 // 배윤성 40~70% → MVP 일괄 50% 고정
                case UNKNOWN -> important ? 0.3 : 0.4; // region/industry는 중요필수라 낮게
                case FAIL -> 0.0;
            };
            sum += maxPoints * fraction;
            switch (status) {
                case PASS -> pass.add(name);
                case PARTIAL -> partial.add(name);
                case UNKNOWN -> unknown.add(name);
                case FAIL -> fail.add(name);
                case NOT_REQUIRED -> notRequired.add(name);
            }
        }

        double weightedSum() {
            return sum;
        }
    }
}
