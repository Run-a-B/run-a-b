package com.runab.api.service.summary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runab.api.dto.policy.PolicySummaryResponse;
import com.runab.api.entity.Policy;
import com.runab.api.entity.PolicySummary;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import com.runab.api.repository.PolicyRepository;
import com.runab.api.repository.PolicySummaryRepository;
import com.runab.api.service.ai.OpenAiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 정책 공고문 AI 3줄 요약 생성 + 캐싱.
 * 정책 정보(description/purposeText/targetGroup/supportScale/applicationMethod 등)를 OpenAI에 보내 핵심 3줄 요약을 만들고,
 * policy_id 단위로 DB에 캐싱한다(정책당 1개, 사용자 무관). 캐시가 있으면 OpenAI를 재호출하지 않는다.
 *
 * 트랜잭션을 메서드 전체에 걸지 않는다: 저장(save)이 각자의 트랜잭션에서 돌게 해서,
 * 동시 첫 조회로 유니크 충돌이 나도 바깥 트랜잭션이 rollback-only로 오염되지 않게 하기 위함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicySummaryService {

    private static final Set<String> ALLOWED_ICONS = Set.of("money", "check", "calendar");

    private final PolicyRepository policyRepository;
    private final PolicySummaryRepository policySummaryRepository;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public PolicySummaryResponse getOrGenerate(Long policyId) {
        // 캐시 히트 → OpenAI 호출 없이 반환
        var cached = policySummaryRepository.findByPolicyId(policyId);
        if (cached.isPresent()) {
            return toResponse(cached.get());
        }

        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POLICY_NOT_FOUND));

        JsonNode ai = openAiClient.chatJson(buildSystemPrompt(), buildUserPrompt(policy));
        PolicySummaryResponse generated = mapAi(ai);

        // 캐시 저장. 동시 첫 조회 레이스로 유니크(policy_id) 충돌이 나면 다른 요청이 이미 저장한 것이므로 무시하고
        // 방금 생성한 결과를 그대로 반환(내용 동일). save는 자체 트랜잭션이라 여기 catch가 안전.
        try {
            policySummaryRepository.save(PolicySummary.builder()
                    .policyId(policyId)
                    .summaryLinesJson(writeJson(generated.getSummaryLines()))
                    .highlightsJson(writeJson(generated.getHighlights()))
                    .expandedDescription(generated.getExpandedDescription())
                    .expandedApplicationMethod(generated.getExpandedApplicationMethod())
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.info("[summary] 동시 생성 감지(policyId={}) → 기존 캐시 사용", policyId);
        }
        return generated;
    }

    // ===== 프롬프트 =====

    private String buildSystemPrompt() {
        return """
                너는 소상공인 정책 공고문 요약 전문가야. 아래 정책 정보를 보고 핵심만 뽑아 반드시 아래 JSON 스키마로만 응답해.
                절대 다른 텍스트나 설명을 추가하지 마.

                {
                  "summary_lines": ["핵심 요약 1줄", "핵심 요약 2줄", "핵심 요약 3줄"],
                  "highlights": [
                    {"icon": "money", "label": "지원 규모", "content": "한 문장 설명"},
                    {"icon": "check", "label": "지원 대상", "content": "한 문장 설명"},
                    {"icon": "calendar", "label": "신청 기간", "content": "한 문장 설명"}
                  ],
                  "expanded_description": "사업 목적/사업 내용을 자연스럽게 풀어쓴 2~4문단 존댓말 문어체",
                  "expanded_application_method": "신청 방법을 자연스럽게 풀어쓴 문장"
                }

                - summary_lines: 정확히 3개. 각 줄은 이 정책의 핵심(지원 내용/대상/규모/기간 등)을 담은 한 문장.
                - highlights: 1~3개. icon은 반드시 money(지원금·자금·규모), check(자격·대상·조건), calendar(기간·일정) 중 하나만.
                - expanded_description: 주어진 "사업 목적"과 "사업 내용" 필드에 있는 내용만으로 자연스럽고 읽기 좋은 한국어 존댓말 문어체 2~4문단으로 재구성. 원본이 짧으면 짧게. 관련 정보가 전혀 없으면 빈 문자열("").
                - expanded_application_method: 주어진 "신청 방법" 필드에 있는 내용만으로 자연스러운 문장으로 재구성. 정보가 없으면 빈 문자열("").
                - ⚠️ 매우 중요: expanded_description/expanded_application_method는 실제 정부·지자체 공고 내용이라 사용자가 신청 판단에 쓴다. 반드시 주어진 필드에 있는 내용만 재구성하고, 새로운 사실·숫자·금액·날짜·조건·절차·기관을 추가하거나 추측·과장하지 마라. 없는 정보를 지어내지 마라. 표현만 매끄럽게 다듬고 내용(사실관계)은 원본과 100% 일치시켜라.
                - 공고문에 없는 내용을 지어내지 말 것. 정보가 부족하면 있는 것만으로 작성.
                """;
    }

    private String buildUserPrompt(Policy policy) {
        StringBuilder sb = new StringBuilder();
        sb.append("[정책 정보]\n");
        sb.append("- 제목: ").append(policy.getTitle()).append("\n");
        if (policy.getCategory() != null) sb.append("- 카테고리: ").append(policy.getCategory()).append("\n");
        if (policy.getAgency() != null) sb.append("- 주관 기관: ").append(policy.getAgency()).append("\n");
        if (policy.getRegion() != null) sb.append("- 지역: ").append(policy.getRegion()).append("\n");
        if (policy.getTargetGroup() != null) sb.append("- 지원 대상: ").append(policy.getTargetGroup()).append("\n");
        if (policy.getSupportScale() != null) sb.append("- 지원 규모: ").append(policy.getSupportScale()).append("\n");
        if (policy.getPurposeText() != null) sb.append("- 사업 목적: ").append(policy.getPurposeText()).append("\n");
        if (policy.getDescription() != null) sb.append("- 사업 내용: ").append(policy.getDescription()).append("\n");
        if (policy.getApplicationMethod() != null) sb.append("- 신청 방법: ").append(policy.getApplicationMethod()).append("\n");
        if (policy.getApplicationStartDate() != null || policy.getApplicationEndDate() != null) {
            sb.append("- 신청 기간: ")
                    .append(policy.getApplicationStartDate() != null ? policy.getApplicationStartDate() : "")
                    .append(" ~ ")
                    .append(policy.getApplicationEndDate() != null ? policy.getApplicationEndDate() : "")
                    .append("\n");
        }
        return sb.toString();
    }

    // ===== 매핑 =====

    private PolicySummaryResponse mapAi(JsonNode ai) {
        List<String> lines = new ArrayList<>();
        ai.path("summary_lines").forEach(n -> {
            String s = n.asText("").trim();
            if (!s.isEmpty()) lines.add(s);
        });

        List<PolicySummaryResponse.Highlight> highlights = new ArrayList<>();
        ai.path("highlights").forEach(h -> {
            String icon = h.path("icon").asText("check");
            if (!ALLOWED_ICONS.contains(icon)) icon = "check"; // 프론트가 아는 아이콘으로 클램프
            highlights.add(PolicySummaryResponse.Highlight.builder()
                    .icon(icon)
                    .label(h.path("label").asText(""))
                    .content(h.path("content").asText(""))
                    .build());
        });

        return PolicySummaryResponse.builder()
                .summaryLines(lines)
                .highlights(highlights)
                .expandedDescription(blankToNull(ai.path("expanded_description").asText("")))
                .expandedApplicationMethod(blankToNull(ai.path("expanded_application_method").asText("")))
                .build();
    }

    // 빈 문자열은 null로 통일해 프론트가 원본 폴백을 쓰도록 함
    private String blankToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    private PolicySummaryResponse toResponse(PolicySummary s) {
        return PolicySummaryResponse.builder()
                .summaryLines(readList(s.getSummaryLinesJson(), new TypeReference<List<String>>() {}))
                .highlights(readList(s.getHighlightsJson(),
                        new TypeReference<List<PolicySummaryResponse.Highlight>>() {}))
                .expandedDescription(s.getExpandedDescription())
                .expandedApplicationMethod(s.getExpandedApplicationMethod())
                .build();
    }

    // ===== JSON 헬퍼 (Report 패턴과 동일) =====

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Collections.emptyList() : value);
        } catch (Exception e) {
            log.error("[summary] JSON 직렬화 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> typeRef) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.warn("[summary] JSON 역직렬화 실패 → 빈 리스트: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
