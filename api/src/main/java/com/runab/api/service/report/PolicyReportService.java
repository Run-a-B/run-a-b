package com.runab.api.service.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runab.api.dto.recommend.MatchResult;
import com.runab.api.dto.report.PolicyReportResponse;
import com.runab.api.entity.BusinessInfo;
import com.runab.api.entity.Policy;
import com.runab.api.entity.PolicyCard;
import com.runab.api.entity.Report;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import com.runab.api.repository.BusinessInfoRepository;
import com.runab.api.repository.PolicyCardRepository;
import com.runab.api.repository.PolicyRepository;
import com.runab.api.repository.ReportRepository;
import com.runab.api.service.ai.OpenAiClient;
import com.runab.api.service.matcher.EligibilityMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 정책 상세 페이지의 "AI 리포트 생성하기" 기능 + 사용자별 리포트 저장/조회/삭제.
 * 사용자 사업 정보 + 정책 정보를 OpenAI에 보내 사업 영향 분석 리포트를 생성하고, user_id로 스코프해 DB에 저장(upsert)한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyReportService {

    private final PolicyRepository policyRepository;
    private final BusinessInfoRepository businessInfoRepository;
    private final PolicyCardRepository policyCardRepository;
    private final ReportRepository reportRepository;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final EligibilityMatcher eligibilityMatcher;

    // ===== 생성 (OpenAI 호출 + DB upsert) =====
    @Transactional
    public PolicyReportResponse generate(Long userId, Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POLICY_NOT_FOUND));

        BusinessInfo businessInfo = businessInfoRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_INFO_REQUIRED));

        PolicyCard card = policyCardRepository.findByPolicyId(PolicyCard.toPolicyId(policy.getExternalId())).orElse(null);

        // 자격요건 매칭 결과를 프롬프트 근거로 함께 넘긴다 (C-2: pass/fail/unknown·점수를 AI가 판단 근거로 설명)
        MatchResult match = eligibilityMatcher.match(businessInfo, card);

        JsonNode ai = openAiClient.chatJson(buildSystemPrompt(), buildUserPrompt(businessInfo, policy, card, match));

        PolicyReportResponse generated = mapToResponse(policy, ai);

        // (user_id, policy_id) 유니크 → 있으면 덮어쓰기, 없으면 새로 생성 (프론트 saveReport의 replace/unshift와 동일 시맨틱)
        Report report = reportRepository.findByUserIdAndPolicyId(userId, policyId)
                .map(existing -> {
                    existing.update(
                            generated.getPolicyTitle(), generated.getCategory(),
                            generated.getImpactLabel(), generated.getImpactStyle(), generated.getDirection(),
                            generated.getSummary(),
                            writeJson(generated.getDetails()), writeJson(generated.getRelatedIds()),
                            writeJson(generated.getBusinessImpact()));
                    return existing;
                })
                .orElseGet(() -> Report.builder()
                        .userId(userId)
                        .policyId(policyId)
                        .policyTitle(generated.getPolicyTitle())
                        .category(generated.getCategory())
                        .impactLabel(generated.getImpactLabel())
                        .impactStyle(generated.getImpactStyle())
                        .direction(generated.getDirection())
                        .summary(generated.getSummary())
                        .detailsJson(writeJson(generated.getDetails()))
                        .relatedIdsJson(writeJson(generated.getRelatedIds()))
                        .businessImpactJson(writeJson(generated.getBusinessImpact()))
                        .build());

        Report saved = reportRepository.saveAndFlush(report); // @PrePersist/@PreUpdate로 savedAt 채우기 위해 flush
        return toResponse(saved);
    }

    // ===== 내 리포트 목록 =====
    public List<PolicyReportResponse> getReports(Long userId) {
        return reportRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ===== 내 리포트 상세 (정책 기준) =====
    public PolicyReportResponse getReport(Long userId, Long policyId) {
        Report report = reportRepository.findByUserIdAndPolicyId(userId, policyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        return toResponse(report);
    }

    // ===== 내 리포트 삭제 =====
    @Transactional
    public void deleteReport(Long userId, Long policyId) {
        long deleted = reportRepository.deleteByUserIdAndPolicyId(userId, policyId);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
        }
    }

    // ===== 프롬프트 =====

    private String buildSystemPrompt() {
        return """
                너는 소상공인 정책 분석 전문가야. 사용자의 사업 정보와 정책 정보, 그리고 자격요건 매칭 결과를 보고,
                이 정책이 사용자 사업에 미치는 영향을 깊이 있게 분석해서 반드시 아래 JSON 스키마로만 응답해.
                절대 다른 텍스트나 설명을 추가하지 마.

                {
                  "overall_direction": "positive" | "negative",
                  "overall_label": "8자 내외 한글 요약 라벨 (예: 사업에 긍정적 영향)",
                  "summary": "2~3문장 한글 요약",
                  "details": ["상세 분석 문단1", "상세 분석 문단2", "상세 분석 문단3", "상세 분석 문단4", "상세 분석 문단5"],
                  "business_impact": [
                    {"label": "매출", "level": 0~100 정수, "direction": "up"|"down", "tag": "+15% 같은 짧은 태그"},
                    {"label": "운영비", "level": 0~100 정수, "direction": "up"|"down", "tag": "짧은 태그"}
                  ]
                }

                [overall_direction — 반드시 positive 또는 negative 둘 중 하나. neutral 금지]
                - 자격요건 매칭 점수(관련도)와 pass/fail 항목, business_impact의 up/down 방향 비율을 종합해서 판단해.
                - 자격요건이 대체로 충족되고 사업에 도움이 되면 positive, 자격 불일치(fail/hardFail)가 많거나 사업에 부담/무관하면 negative.

                [details — 가능한 한 상세하고 깊이 있게. 아래 5개 관점을 각각 별도 문단으로, 각 문단 2~3문장 이상 충분한 분량으로 작성]
                1. 이 정책의 핵심 지원 내용이 사용자 사업(업종·규모·지역)에 구체적으로 어떻게 적용되는지.
                2. 지원 자격 충족 여부의 판단 근거 — 제공된 매칭 결과(pass/fail/unknown 항목, 관련도 점수)를 근거로 어떤 조건이 맞고 안 맞는지 구체적으로 설명.
                3. 신청 시 예상되는 경쟁률/난이도 코멘트 — 지원규모·지원대상 범위 기반 추정. 단정적 수치 대신 "~것으로 보입니다" 식 완곡한 표현.
                4. 놓치기 쉬운 유의사항 — 마감일·신청방법·필요 서류 등 제공된 신청방법/신청기간 정보 활용.
                5. 함께 고려하면 좋은 전략적 관점 — 이 정책을 관련 정책과 연계했을 때의 시너지 등, 제공된 데이터 안에서 자연스럽게.

                [business_impact]
                - overall_direction이 "positive"일 때만 최소 2개 이상(매출/인건비/운영비/세금 등 실제 관련 있는 항목) 생성.
                - overall_direction이 "negative"이면 business_impact는 빈 배열([])로 둘 것.
                - level(0~100 막대 길이)과 tag(예: "+15%")는 정책의 지원 내용에 근거해 의미 있게 추정하되, 확정 수치가 아닌 추정치임.

                [⚠️ 사실 원칙] 분량을 늘리되 없는 사실(숫자·조건·날짜)을 지어내지 마라. 반드시 제공된 정책 데이터·매칭 결과에 근거해서 더 깊이 풀어 설명하는 것이지, 없는 정보를 만들어내는 게 아니다.
                """;
    }

    private String buildUserPrompt(BusinessInfo info, Policy policy, PolicyCard card, MatchResult match) {
        StringBuilder sb = new StringBuilder();
        sb.append("[사용자 사업 정보]\n");
        sb.append("- 업종: ").append(info.getJobCategory()).append("\n");
        sb.append("- 지역: ").append(info.getRegion()).append("\n");
        sb.append("- 사업 상태: ").append(Boolean.TRUE.equals(info.getBusinessStatus()) ? "운영중" : "준비중").append("\n");
        if (info.getAnnualRevenue() != null) sb.append("- 연매출: ").append(info.getAnnualRevenue()).append("원\n");
        if (info.getEmployeeCount() != null) sb.append("- 직원수: ").append(info.getEmployeeCount()).append("명\n");

        sb.append("\n[정책 정보]\n");
        sb.append("- 제목: ").append(policy.getTitle()).append("\n");
        sb.append("- 카테고리: ").append(policy.getCategory()).append("\n");
        if (policy.getDescription() != null) sb.append("- 설명: ").append(policy.getDescription()).append("\n");
        if (policy.getTargetGroup() != null) sb.append("- 지원대상: ").append(policy.getTargetGroup()).append("\n");
        if (policy.getSupportScale() != null) sb.append("- 지원규모: ").append(policy.getSupportScale()).append("\n");
        if (policy.getPurposeText() != null) sb.append("- 사업목적: ").append(policy.getPurposeText()).append("\n");
        if (card != null && card.getFullJson() != null) {
            sb.append("\n[자격요건 추출 데이터(JSON)]\n").append(card.getFullJson()).append("\n");
        }

        // 자격요건 매칭 결과 (AI가 자격 충족 근거를 구체적으로 설명하도록 함)
        sb.append("\n[자격요건 매칭 결과]\n");
        sb.append("- 관련도 점수: ").append(match.getScore()).append("/100\n");
        sb.append("- 충족(pass) 항목: ").append(match.getPass()).append("\n");
        sb.append("- 불충족(fail) 항목: ").append(match.getFail()).append("\n");
        sb.append("- 판단불가(unknown) 항목: ").append(match.getUnknown()).append("\n");
        sb.append("- 자격 탈락(hardFail) 여부: ").append(match.isHardFail()).append("\n");
        return sb.toString();
    }

    // ===== 매핑 =====

    private PolicyReportResponse mapToResponse(Policy policy, JsonNode ai) {
        // neutral 제거 → positive/negative 이진화. 명시적으로 negative인 경우만 negative, 나머지는 positive.
        boolean negative = "negative".equals(ai.path("overall_direction").asText("positive"));
        String direction = negative ? "negative" : "positive";
        String overallLabel = ai.path("overall_label").asText("분석 완료");
        // Tailwind v4는 프론트 소스에 리터럴로 등장하는 클래스만 CSS를 생성한다. 백엔드가 만들어 보내는 색상 클래스는
        // 반드시 프론트에 이미 존재하는 것으로 골라야 렌더링됨. (negative의 bg-red-100은 hover: 변형으로만, text-red-700은
        // 아예 미등장이라 색이 안 칠해졌음 → 프론트에 실재하는 bg-red-50/text-red-600으로 교체)
        String impactStyle = negative ? "bg-red-50 text-red-600" : "bg-green-100 text-green-700";

        List<String> details = new ArrayList<>();
        ai.path("details").forEach(n -> details.add(n.asText()));

        // C-3: 사업 영향도는 positive 리포트에만. negative면 빈 배열로 반환(부정 리포트엔 억지 수치 안 붙임).
        List<PolicyReportResponse.BusinessImpactItem> impactItems = new ArrayList<>();
        if (!negative) {
            ai.path("business_impact").forEach(item -> {
                String itemDirection = item.path("direction").asText("up");
                boolean up = "up".equals(itemDirection);
                impactItems.add(PolicyReportResponse.BusinessImpactItem.builder()
                        .label(item.path("label").asText(""))
                        .level(item.path("level").asInt(50))
                        .direction(itemDirection)
                        .tag(item.path("tag").asText(""))
                        // 프론트(policies.ts 등)에 다수 등장해 Tailwind 생성이 보장되는 클래스로 지정
                        // (기존 bg-green-500/bg-red-500/text-red-600은 프론트에 리터럴로 거의 없어 CSS 누락 → 막대·태그 무색)
                        .barColor(up ? "bg-green-400" : "bg-red-400")
                        .tagColor(up ? "text-green-600" : "text-red-500")
                        .build());
            });
        }

        // C-4: 관련 정책을 id뿐 아니라 제목까지 (같은 카테고리 최신 2개 — 이미 Policy 엔티티라 제목 바로 사용)
        List<PolicyReportResponse.RelatedPolicy> relatedPolicies = policyRepository
                .findTop3ByCategoryAndIdNotOrderByPublishedDateDesc(policy.getCategory(), policy.getId())
                .stream()
                .limit(2)
                .map(p -> PolicyReportResponse.RelatedPolicy.builder()
                        .id(p.getId())
                        .title(p.getTitle())
                        .build())
                .toList();

        return PolicyReportResponse.builder()
                .policyId(policy.getId())
                .policyTitle(policy.getTitle())
                .category(policy.getCategory())
                .impactLabel(overallLabel)
                .impactStyle(impactStyle)
                .direction(direction)
                .summary(ai.path("summary").asText(""))
                .details(details)
                .relatedIds(relatedPolicies)
                .businessImpact(impactItems)
                .build();
    }

    // DB 엔티티 → 프론트 응답 (JSON 컬럼 역직렬화)
    private PolicyReportResponse toResponse(Report r) {
        // 하위호환: direction 없는 옛 리포트는 positive로 간주. relatedIds가 옛 [숫자] 형식이면 파싱 실패→빈 리스트.
        String direction = r.getDirection() != null ? r.getDirection() : "positive";
        return PolicyReportResponse.builder()
                .policyId(r.getPolicyId())
                .policyTitle(r.getPolicyTitle())
                .category(r.getCategory())
                .impactLabel(r.getImpactLabel())
                .impactStyle(r.getImpactStyle())
                .direction(direction)
                .summary(r.getSummary())
                .details(readList(r.getDetailsJson(), new TypeReference<List<String>>() {}))
                .relatedIds(readList(r.getRelatedIdsJson(),
                        new TypeReference<List<PolicyReportResponse.RelatedPolicy>>() {}))
                .businessImpact(readList(r.getBusinessImpactJson(),
                        new TypeReference<List<PolicyReportResponse.BusinessImpactItem>>() {}))
                .savedAt(r.getUpdatedAt() != null ? r.getUpdatedAt().toString() : null)
                .build();
    }

    // ===== JSON 직렬화 헬퍼 =====

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Collections.emptyList() : value);
        } catch (Exception e) {
            log.error("[report] JSON 직렬화 실패: {}", e.getMessage());
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
            log.warn("[report] JSON 역직렬화 실패 → 빈 리스트: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
