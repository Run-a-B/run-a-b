package com.runab.api.service.diagnosis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runab.api.dto.policy.PolicyDiagnosisRequest;
import com.runab.api.dto.policy.PolicyDiagnosisResponse;
import com.runab.api.entity.Policy;
import com.runab.api.entity.PolicyCard;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import com.runab.api.repository.PolicyCardRepository;
import com.runab.api.repository.PolicyRepository;
import com.runab.api.service.matcher.IndustryHierarchy;
import com.runab.api.service.matcher.MatchStatus;
import com.runab.api.service.matcher.PolicyIndustryFallbackMatcher;
import com.runab.api.service.matcher.RegionHierarchy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PolicyDiagnosisService {
    private static final int TOTAL_FACTS = 6;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final Set<String> OPERATING_STAGES = Set.of("운영중", "사업중", "영업중", "operating");
    private static final Set<String> PREPARING_STAGES = Set.of("준비중", "예비", "예비창업", "preparing");

    private final PolicyRepository policyRepository;
    private final PolicyCardRepository policyCardRepository;
    private final ObjectMapper objectMapper;
    private final DiagnosisInputNormalizer inputNormalizer;

    public PolicyDiagnosisResponse diagnose(Long policyId, PolicyDiagnosisRequest request) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POLICY_NOT_FOUND));
        DiagnosisInputNormalizer.NormalizedBusiness business = inputNormalizer.normalize(request);
        CardData cardData = loadCard(policy);

        List<String> hardFailReasons = new ArrayList<>();
        List<PolicyDiagnosisResponse.Evidence> evidence = new ArrayList<>();
        evidence.add(regionEvidence(policy, cardData, business, hardFailReasons));
        evidence.add(industryEvidence(policy, cardData, business));
        evidence.add(dateEvidence(policy, cardData, hardFailReasons));
        evidence.add(stageEvidence(cardData, business));
        evidence.add(revenueEvidence(cardData, business));
        evidence.add(employeeEvidence(cardData, business));

        List<String> unknownConditions = evidence.stream()
                .filter(item -> item.getStatus() == MatchStatus.UNKNOWN)
                .map(PolicyDiagnosisResponse.Evidence::getLabel)
                .toList();
        int comparableCount = (int) evidence.stream().filter(item -> comparable(item.getStatus())).count();
        boolean hardFail = !hardFailReasons.isEmpty();
        String verdict = verdict(evidence, hardFail, comparableCount);

        return PolicyDiagnosisResponse.builder()
                .policy(policyInfo(policy))
                .verdict(verdict)
                .verdictLabel(verdictLabel(verdict))
                .score(score(cardData, evidence, comparableCount))
                .confidence(confidence(cardData, evidence, comparableCount))
                .summary(summary(verdict))
                .evidence(evidence)
                .unknownConditions(unknownConditions)
                .hardFailReasons(hardFailReasons)
                .hardFail(hardFail)
                .dataQuality(PolicyDiagnosisResponse.DataQuality.builder()
                        .policyCardAvailable(cardData.usable())
                        .extractionStatus(cardData.extractionStatus())
                        .sourceSystem(policy.getSourceSystem())
                        .verifiedFactCount((int) evidence.stream()
                                .filter(item -> item.getStatus() != MatchStatus.UNKNOWN).count())
                        .totalFactCount(TOTAL_FACTS)
                        .build())
                .disclaimer("공고문 조건과 입력 정보를 비교한 1차 진단입니다. 최종 자격은 원문 공고와 담당 기관에서 확인해 주세요.")
                .build();
    }

    private PolicyDiagnosisResponse.Evidence regionEvidence(Policy policy, CardData cardData,
                                                             DiagnosisInputNormalizer.NormalizedBusiness business,
                                                             List<String> hardFailReasons) {
        if (cardData.usable()) {
            JsonNode eligibility = cardData.eligibility();
            JsonNode regionNode = field(eligibility, "regions");
            String type = text(cardData.eligibility(), "region_condition_type");
            if ("nationwide".equals(type)) {
                return evidence("region", "지역", business.region(), "전국", MatchStatus.PASS, cardSource());
            }
            if (!"unknown".equals(type) && regionNode != null) {
                List<String> regions = textList(regionNode);
                if (regions.isEmpty()) {
                    if (regionNode.isArray() && !regionNode.isEmpty()) {
                        if (!cardData.fullyExtracted()) {
                            return nonFailingMetadataRegionEvidence(policy, business);
                        }
                        return unknown("region", "지역", business.region());
                    }
                    if (cardData.fullyExtracted()) {
                        if ("specific".equals(type)) return unknown("region", "지역", business.region());
                        return explicitNoCondition("region", "지역", business.region(), cardData, regionNode);
                    }
                } else {
                    if (regions.contains("전국")) {
                        return evidence("region", "지역", business.region(), "전국", MatchStatus.PASS, cardSource());
                    }
                    boolean match = regions.stream()
                            .anyMatch(region -> RegionHierarchy.sameProvince(business.region(), region));
                    if (match) {
                        return evidence("region", "지역", business.region(), String.join(", ", regions),
                                MatchStatus.PASS, cardSource());
                    }
                    boolean allRecognized = regions.stream()
                            .allMatch(region -> !RegionHierarchy.provincesOf(region).isEmpty());
                    if (!allRecognized) {
                        if (!cardData.fullyExtracted()) {
                            return nonFailingMetadataRegionEvidence(policy, business);
                        }
                        return unknown("region", "지역", business.region());
                    }
                    hardFailReasons.add("지역 조건이 맞지 않아요.");
                    return evidence("region", "지역", business.region(), String.join(", ", regions),
                            MatchStatus.FAIL, cardSource());
                }
            }
            if (cardData.fullyExtracted()) return unknown("region", "지역", business.region());
        }
        return metadataRegionEvidence(policy, business, hardFailReasons);
    }

    private PolicyDiagnosisResponse.Evidence metadataRegionEvidence(
            Policy policy, DiagnosisInputNormalizer.NormalizedBusiness business, List<String> hardFailReasons) {
        String policyRegion = policy.getRegion();
        if (blank(policyRegion)) return unknown("region", "지역", business.region());
        if ("전국".equals(policyRegion)) {
            return evidence("region", "지역", business.region(), policyRegion, MatchStatus.PASS, metadataSource("지역"));
        }
        boolean match = RegionHierarchy.sameProvince(business.region(), policyRegion);
        if (!match) hardFailReasons.add("지역 조건이 맞지 않아요.");
        return evidence("region", "지역", business.region(), policyRegion,
                match ? MatchStatus.PASS : MatchStatus.FAIL, metadataSource("지역"));
    }

    private PolicyDiagnosisResponse.Evidence nonFailingMetadataRegionEvidence(
            Policy policy, DiagnosisInputNormalizer.NormalizedBusiness business) {
        PolicyDiagnosisResponse.Evidence metadata = metadataRegionEvidence(policy, business, new ArrayList<>());
        return metadata.getStatus() == MatchStatus.FAIL
                ? unknown("region", "지역", business.region())
                : metadata;
    }

    private PolicyDiagnosisResponse.Evidence industryEvidence(Policy policy, CardData cardData,
                                                               DiagnosisInputNormalizer.NormalizedBusiness business) {
        if (cardData.usable()) {
            JsonNode eligibility = cardData.eligibility();
            JsonNode industryNode = field(eligibility, "industries");
            String type = text(cardData.eligibility(), "industry_condition_type");
            if ("all".equals(type)) {
                return evidence("industry", "업종", business.industry(), "전업종", MatchStatus.PASS, cardSource());
            }
            if (!"unknown".equals(type) && industryNode != null) {
                List<String> industries = textList(industryNode);
                if (industries.isEmpty()) {
                    if (cardData.fullyExtracted()) {
                        if ("specific".equals(type)) return unknown("industry", "업종", business.industry());
                        return explicitNoCondition("industry", "업종", business.industry(), cardData, industryNode);
                    }
                } else {
                    if (industries.contains("전업종")) {
                        return evidence("industry", "업종", business.industry(), "전업종", MatchStatus.PASS, cardSource());
                    }
                    boolean match = industries.stream()
                            .anyMatch(industry -> IndustryHierarchy.sameIndustry(business.industry(), industry));
                    return evidence("industry", "업종", business.industry(), String.join(", ", industries),
                            match ? MatchStatus.PASS : MatchStatus.FAIL, cardSource());
                }
            }
            if (cardData.fullyExtracted()) return unknown("industry", "업종", business.industry());
        }
        return metadataIndustryEvidence(policy, business);
    }

    private PolicyDiagnosisResponse.Evidence metadataIndustryEvidence(
            Policy policy, DiagnosisInputNormalizer.NormalizedBusiness business) {
        String policyIndustry = policy.getIndustry();
        if (!blank(policyIndustry) && !"전체".equals(policyIndustry)) {
            boolean match = IndustryHierarchy.sameIndustry(business.industry(), policyIndustry);
            return evidence("industry", "업종", business.industry(), policyIndustry,
                    match ? MatchStatus.PASS : MatchStatus.FAIL, metadataSource("업종"));
        }
        if (PolicyIndustryFallbackMatcher.matches(policy, business.industry())) {
            return evidence("industry", "업종", business.industry(), "본문의 업종 표현", MatchStatus.PASS,
                    "정책 목록의 제목·지원 대상·설명 정보");
        }
        return unknown("industry", "업종", business.industry());
    }

    private PolicyDiagnosisResponse.Evidence dateEvidence(Policy policy, CardData cardData, List<String> hardFailReasons) {
        if (cardData.usable()) {
            JsonNode applicationPeriod = field(cardData.root(), "application_period");
            if (applicationPeriod != null && applicationPeriod.isNull()) {
                if (cardData.fullyExtracted()) {
                    return explicitNoCondition("date", "신청 기간", "오늘", cardData, applicationPeriod);
                }
            } else if (applicationPeriod != null) {
                String status = text(applicationPeriod, "status");
                if ("closed".equals(status)) {
                    hardFailReasons.add("신청 기간이 종료되었어요.");
                    return evidence("date", "신청 기간", "오늘", "마감", MatchStatus.FAIL, cardSource());
                }
                if ("open".equals(status)) {
                    return evidence("date", "신청 기간", "오늘", "접수 중", MatchStatus.PASS, cardSource());
                }
                if ("upcoming".equals(status) || "scheduled".equals(status)) {
                    return evidence("date", "신청 기간", "오늘", "접수 예정", MatchStatus.PARTIAL, cardSource());
                }
                if (!"unknown".equals(status) && hasFutureStructuredStart(applicationPeriod)) {
                    return evidence("date", "신청 기간", "오늘", "접수 예정", MatchStatus.PARTIAL, cardSource());
                }
            }
            if (cardData.fullyExtracted()) return unknown("date", "신청 기간", "오늘");
        }
        return metadataDateEvidence(policy, hardFailReasons);
    }

    private PolicyDiagnosisResponse.Evidence metadataDateEvidence(Policy policy, List<String> hardFailReasons) {
        LocalDate today = LocalDate.now();
        LocalDate start = policy.getApplicationStartDate();
        LocalDate end = policy.getApplicationEndDate();
        String period = formatPeriod(start, end);
        if (end != null && end.isBefore(today)) {
            hardFailReasons.add("신청 기간이 종료되었어요.");
            return evidence("date", "신청 기간", "오늘", period, MatchStatus.FAIL, metadataSource("신청 기간"));
        }
        if (start != null && start.isAfter(today)) {
            return evidence("date", "신청 기간", "오늘", period, MatchStatus.PARTIAL, metadataSource("신청 기간"));
        }
        if (start != null || end != null) {
            return evidence("date", "신청 기간", "오늘", period, MatchStatus.PASS, metadataSource("신청 기간"));
        }
        return unknown("date", "신청 기간", "오늘");
    }

    private PolicyDiagnosisResponse.Evidence stageEvidence(CardData cardData,
                                                            DiagnosisInputNormalizer.NormalizedBusiness business) {
        String userValue = business.operating() ? "사업 중" : "사업 준비 중";
        if (!cardData.usable()) return unknown("business_stage", "사업 상태", userValue);
        JsonNode stageNode = field(cardData.eligibility(), "business_stages");
        if (stageNode == null) return unknown("business_stage", "사업 상태", userValue);
        List<String> stages = textList(stageNode);
        if (stages.isEmpty()) return explicitNoCondition("business_stage", "사업 상태", userValue, cardData, stageNode);
        Set<String> allowed = business.operating() ? OPERATING_STAGES : PREPARING_STAGES;
        boolean match = stages.stream().anyMatch(allowed::contains);
        return evidence("business_stage", "사업 상태", userValue, String.join(", ", stages),
                match ? MatchStatus.PASS : MatchStatus.FAIL, cardSource());
    }

    private PolicyDiagnosisResponse.Evidence revenueEvidence(CardData cardData,
                                                              DiagnosisInputNormalizer.NormalizedBusiness business) {
        String userValue = business.annualRevenue() == null ? "입력하지 않음" : business.annualRevenue().label();
        if (!cardData.usable()) return unknown("revenue", "연 매출", userValue);
        JsonNode revenueNode = field(cardData.eligibility(), "max_revenue");
        if (revenueNode == null) return unknown("revenue", "연 매출", userValue);
        if (revenueNode.isNull()) return explicitNoCondition("revenue", "연 매출", userValue, cardData, revenueNode);
        Long maximum = longValue(revenueNode);
        if (maximum == null) return unknown("revenue", "연 매출", userValue);
        MatchStatus status = business.annualRevenue() == null ? MatchStatus.UNKNOWN
                : business.annualRevenue().compareToMaximum(maximum);
        return evidence("revenue", "연 매출", userValue, formatMaximum(maximum), status, cardSource());
    }

    private PolicyDiagnosisResponse.Evidence employeeEvidence(CardData cardData,
                                                               DiagnosisInputNormalizer.NormalizedBusiness business) {
        String userValue = business.employeeCount() == null ? "입력하지 않음" : business.employeeCount().label();
        if (!cardData.usable()) return unknown("employees", "직원 수", userValue);
        JsonNode employeeNode = field(cardData.eligibility(), "max_employees");
        if (employeeNode == null) return unknown("employees", "직원 수", userValue);
        if (employeeNode.isNull()) return explicitNoCondition("employees", "직원 수", userValue, cardData, employeeNode);
        Long maximum = longValue(employeeNode);
        if (maximum == null) return unknown("employees", "직원 수", userValue);
        MatchStatus status = business.employeeCount() == null ? MatchStatus.UNKNOWN
                : business.employeeCount().compareToMaximum(maximum);
        return evidence("employees", "직원 수", userValue, formatEmployeeMaximum(maximum), status, cardSource());
    }

    private PolicyDiagnosisResponse.Evidence explicitNoCondition(String key, String label, String userValue,
                                                                  CardData cardData, JsonNode conditionNode) {
        if (cardData.fullyExtracted() && (conditionNode.isNull()
                || (conditionNode.isArray() && conditionNode.isEmpty()))) {
            return evidence(key, label, userValue, "별도 조건 없음", MatchStatus.NOT_REQUIRED, cardSource());
        }
        return unknown(key, label, userValue);
    }

    private PolicyDiagnosisResponse.Evidence unknown(String key, String label, String userValue) {
        return evidence(key, label, userValue, "확인되지 않음", MatchStatus.UNKNOWN, "확인 가능한 공고 조건 없음");
    }

    private PolicyDiagnosisResponse.Evidence evidence(String key, String label, String userValue, String policyValue,
                                                       MatchStatus status, String source) {
        return PolicyDiagnosisResponse.Evidence.builder()
                .key(key).label(label).userValue(userValue).policyValue(policyValue).status(status)
                .reason(reason(key, status)).source(source).build();
    }

    private String reason(String key, MatchStatus status) {
        return switch (status) {
            case PASS -> "입력한 조건이 공고 조건과 맞아요.";
            case PARTIAL -> "date".equals(key)
                    ? "아직 접수 전이에요. 신청 시작일을 확인해 주세요."
                    : "입력한 구간이 공고 기준과 일부 겹쳐요.";
            case FAIL -> "date".equals(key) ? "신청 기간이 종료되었어요."
                    : "region".equals(key) ? "지역 조건이 맞지 않아요." : "공고 조건과 맞지 않아요.";
            case UNKNOWN -> "공고 조건을 확인하지 못했어요.";
            case NOT_REQUIRED -> "구조화된 공고에서 별도 조건이 확인되지 않았어요.";
        };
    }

    private CardData loadCard(Policy policy) {
        PolicyCard card = policyCardRepository.findByPolicyId(PolicyCard.toPolicyId(policy.getExternalId())).orElse(null);
        if (card == null) return new CardData(null, null, false, false, "not_available");
        String extractionStatus = card.getExtractionStatus();
        boolean allowedStatus = "extracted".equals(extractionStatus) || "partially_extracted".equals(extractionStatus);
        if (!allowedStatus || blank(card.getFullJson())) return new CardData(card, null, false, false, extractionStatus);
        try {
            JsonNode root = objectMapper.readTree(card.getFullJson());
            if (root == null || !root.isObject()) return new CardData(card, null, false, false, "invalid_json");
            return new CardData(card, root, true, "extracted".equals(extractionStatus), extractionStatus);
        } catch (Exception ignored) {
            return new CardData(card, null, false, false, "invalid_json");
        }
    }

    private PolicyDiagnosisResponse.PolicyInfo policyInfo(Policy policy) {
        return PolicyDiagnosisResponse.PolicyInfo.builder().id(policy.getId()).title(policy.getTitle())
                .category(policy.getCategory()).agency(policy.getAgency())
                .applicationPeriod(formatPeriod(policy.getApplicationStartDate(), policy.getApplicationEndDate()))
                .detailUrl(policy.getDetailUrl()).build();
    }

    private String verdict(List<PolicyDiagnosisResponse.Evidence> evidence, boolean hardFail, int comparableCount) {
        if (hardFail) return "NOT_ELIGIBLE";
        if (comparableCount == 0 && evidence.stream().noneMatch(item -> item.getStatus() == MatchStatus.NOT_REQUIRED)) return "ON_HOLD";
        if (evidence.stream().anyMatch(item -> item.getStatus() == MatchStatus.FAIL
                || item.getStatus() == MatchStatus.PARTIAL || item.getStatus() == MatchStatus.UNKNOWN)) return "NEEDS_CONFIRMATION";
        return "LIKELY_ELIGIBLE";
    }

    private Integer score(CardData cardData, List<PolicyDiagnosisResponse.Evidence> evidence, int comparableCount) {
        if (comparableCount < 2 && !completeEvidence(cardData, evidence)) return null;
        int[] weights = {25, 25, 15, 15, 10, 10};
        double numerator = 0;
        int denominator = 0;
        for (int i = 0; i < evidence.size(); i++) {
            MatchStatus status = evidence.get(i).getStatus();
            if (status == MatchStatus.NOT_REQUIRED) continue;
            denominator += weights[i];
            numerator += weights[i] * multiplier(status);
        }
        if (denominator == 0) return null;
        return (int) Math.round(numerator * 100 / denominator);
    }

    private double multiplier(MatchStatus status) {
        return switch (status) {
            case PASS -> 1.0;
            case PARTIAL -> 0.5;
            case UNKNOWN -> 0.3;
            case FAIL -> 0.0;
            case NOT_REQUIRED -> 0.0;
        };
    }

    private String confidence(CardData cardData, List<PolicyDiagnosisResponse.Evidence> evidence, int comparableCount) {
        if (completeEvidence(cardData, evidence)) return "HIGH";
        if (!cardData.usable() || comparableCount <= 2) return "LOW";
        return "MEDIUM";
    }

    private static boolean completeEvidence(CardData cardData, List<PolicyDiagnosisResponse.Evidence> evidence) {
        return cardData.fullyExtracted()
                && evidence.stream().noneMatch(item -> item.getStatus() == MatchStatus.UNKNOWN);
    }

    private static boolean comparable(MatchStatus status) {
        return status != MatchStatus.UNKNOWN && status != MatchStatus.NOT_REQUIRED;
    }

    private static String verdictLabel(String verdict) {
        return switch (verdict) {
            case "NOT_ELIGIBLE" -> "현재 대상 아님";
            case "ON_HOLD" -> "판정 보류";
            case "NEEDS_CONFIRMATION" -> "조건 확인 필요";
            default -> "신청 가능성 높음";
        };
    }

    private static String summary(String verdict) {
        return switch (verdict) {
            case "NOT_ELIGIBLE" -> "현재 입력 정보로는 공고 조건과 맞지 않는 항목이 있어요. 원문 공고를 확인해 주세요.";
            case "ON_HOLD" -> "확인 가능한 공고 조건이 부족해 지금은 판정을 보류해요.";
            case "NEEDS_CONFIRMATION" -> "확인되지 않았거나 추가 확인이 필요한 조건이 있어요.";
            default -> "입력한 정보가 확인된 공고 조건과 대체로 맞아요.";
        };
    }

    private static String metadataSource(String field) {
        return "정책 목록의 " + field + " 정보";
    }

    private static String cardSource() { return "구조화된 공고 조건"; }

    private static String formatPeriod(LocalDate start, LocalDate end) {
        if (start == null && end == null) return "기간 정보 없음";
        String startValue = start == null ? "" : start.format(DATE_FORMATTER);
        String endValue = end == null ? "" : end.format(DATE_FORMATTER);
        return startValue + " ~ " + endValue;
    }

    private static String formatMaximum(long value) { return String.format("%,d만원 이하", value / 10_000); }

    private static String formatEmployeeMaximum(long value) { return String.format("%,d명", value); }

    private static boolean hasFutureStructuredStart(JsonNode applicationPeriod) {
        String start = text(applicationPeriod, "start");
        if (blank(start)) return false;
        try {
            return LocalDate.parse(start).isAfter(LocalDate.now());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static List<String> textList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(value -> {
                if (!value.isTextual()) return;
                String text = value.asText().trim();
                if (!text.isEmpty()) values.add(text);
            });
        }
        return values;
    }

    private static JsonNode field(JsonNode node, String field) {
        return node != null && node.isObject() ? node.get(field) : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static Long longValue(JsonNode value) {
        return value.canConvertToLong() ? value.asLong() : null;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private record CardData(PolicyCard card, JsonNode root, boolean usable, boolean fullyExtracted,
                            String extractionStatus) {
        private JsonNode eligibility() { return root == null ? null : root.path("eligibility"); }
    }
}
