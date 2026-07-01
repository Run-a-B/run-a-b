package com.runab.api.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.runab.api.dto.report.PolicyReportResponse;
import com.runab.api.entity.BusinessInfo;
import com.runab.api.entity.Policy;
import com.runab.api.entity.PolicyCard;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import com.runab.api.repository.BusinessInfoRepository;
import com.runab.api.repository.PolicyCardRepository;
import com.runab.api.repository.PolicyRepository;
import com.runab.api.service.ai.OpenAiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 정책 상세 페이지의 "AI 리포트 생성하기" 기능.
 * 사용자 사업 정보 + 정책 정보를 OpenAI에 보내 사업 영향 분석 리포트를 생성한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyReportService {

    private final PolicyRepository policyRepository;
    private final BusinessInfoRepository businessInfoRepository;
    private final PolicyCardRepository policyCardRepository;
    private final OpenAiClient openAiClient;

    public PolicyReportResponse generate(Long userId, Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POLICY_NOT_FOUND));

        BusinessInfo businessInfo = businessInfoRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_INFO_REQUIRED));

        PolicyCard card = policyCardRepository.findByPolicyId(PolicyCard.toPolicyId(policy.getExternalId())).orElse(null);

        JsonNode ai = openAiClient.chatJson(buildSystemPrompt(), buildUserPrompt(businessInfo, policy, card));

        return mapToResponse(policy, ai);
    }

    private String buildSystemPrompt() {
        return """
                너는 소상공인 정책 분석 전문가야. 사용자의 사업 정보와 정책 정보를 보고,
                이 정책이 사용자 사업에 미치는 영향을 분석해서 반드시 아래 JSON 스키마로만 응답해.
                절대 다른 텍스트나 설명을 추가하지 마.

                {
                  "overall_direction": "positive" | "negative" | "neutral",
                  "overall_label": "8자 내외 한글 요약 라벨 (예: 사업에 긍정적 영향)",
                  "summary": "2~3문장 한글 요약",
                  "details": ["상세 분석 문단1", "상세 분석 문단2", "상세 분석 문단3"],
                  "business_impact": [
                    {"label": "매출", "level": 0~100 정수, "direction": "up"|"down", "tag": "+15% 같은 짧은 태그"},
                    {"label": "운영비", "level": 0~100 정수, "direction": "up"|"down", "tag": "짧은 태그"}
                  ]
                }

                business_impact는 2~4개 항목으로, 매출/인건비/운영비/세금 등 사업 관련 항목 중 실제로 이 정책과 관련 있는 것만 선택해.
                """;
    }

    private String buildUserPrompt(BusinessInfo info, Policy policy, PolicyCard card) {
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
        return sb.toString();
    }

    private PolicyReportResponse mapToResponse(Policy policy, JsonNode ai) {
        String direction = ai.path("overall_direction").asText("neutral");
        String overallLabel = ai.path("overall_label").asText("분석 완료");
        String impactStyle = switch (direction) {
            case "positive" -> "bg-green-100 text-green-700";
            case "negative" -> "bg-red-100 text-red-700";
            default -> "bg-gray-100 text-gray-600";
        };

        List<String> details = new ArrayList<>();
        ai.path("details").forEach(n -> details.add(n.asText()));

        List<PolicyReportResponse.BusinessImpactItem> impactItems = new ArrayList<>();
        ai.path("business_impact").forEach(item -> {
            String itemDirection = item.path("direction").asText("up");
            boolean up = "up".equals(itemDirection);
            impactItems.add(PolicyReportResponse.BusinessImpactItem.builder()
                    .label(item.path("label").asText(""))
                    .level(item.path("level").asInt(50))
                    .direction(itemDirection)
                    .tag(item.path("tag").asText(""))
                    .barColor(up ? "bg-green-500" : "bg-red-500")
                    .tagColor(up ? "text-green-600" : "text-red-600")
                    .build());
        });

        List<Long> relatedIds = policyRepository
                .findTop3ByCategoryAndIdNotOrderByPublishedDateDesc(policy.getCategory(), policy.getId())
                .stream()
                .limit(2)
                .map(Policy::getId)
                .toList();

        return PolicyReportResponse.builder()
                .policyId(policy.getId())
                .policyTitle(policy.getTitle())
                .category(policy.getCategory())
                .impactLabel(overallLabel)
                .impactStyle(impactStyle)
                .summary(ai.path("summary").asText(""))
                .details(details)
                .relatedIds(relatedIds)
                .businessImpact(impactItems)
                .build();
    }
}
