package com.runab.api.service.checklist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.runab.api.dto.policy.PolicyChecklistResponse;
import com.runab.api.entity.Policy;
import com.runab.api.entity.PolicyCard;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import com.runab.api.repository.PolicyCardRepository;
import com.runab.api.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "신청 준비하기" 체크리스트 조립.
 * ⚠️ 신청 서류는 틀리면 사용자에게 피해를 주므로 AI 자유생성 금지 — 정적 공통 준비 권장 + policy_card의 문서 언급만 조립한다.
 * 우선순위: (1) 공통 준비 권장(하드코딩) → (2) policy_card.eligibility.documents(extractor가 채운 값) → 데이터 없어도 (1)은 항상 반환.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyChecklistService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    // 대부분의 소상공인/중소기업 지원사업에 일반적으로 준비하면 좋은 서류. 정책별 필수 서류로 단정하지 않는다.
    private static final List<PolicyChecklistResponse.ChecklistItem> COMMON_DOCS = List.of(
            item("biz_reg", "사업자등록증", false, "일반적으로 준비하면 좋은 서류예요. 최근 3개월 이내 발급본을 권장해요."),
            item("application_form", "신청서", false, "일반적으로 준비하면 좋은 서류예요. 공고문에 첨부된 지정 양식을 확인해 주세요."),
            item("biz_plan", "사업계획서", false, "일반적으로 준비하면 좋은 서류예요. 지원 목적과 활용 계획을 정리해 보세요."),
            item("bank_copy", "통장 사본", false, "일반적으로 준비하면 좋은 서류예요. 사업자 명의 계좌인지 확인해 주세요."),
            item("privacy_consent", "개인정보 수집·이용 동의서", false, "일반적으로 준비하면 좋은 서류예요. 원문 공고의 제출 여부를 확인해 주세요.")
    );

    private final PolicyRepository policyRepository;
    private final PolicyCardRepository policyCardRepository;
    private final ObjectMapper objectMapper;

    public PolicyChecklistResponse getChecklist(Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POLICY_NOT_FOUND));

        PolicyCard card = policyCardRepository
                .findByPolicyId(PolicyCard.toPolicyId(policy.getExternalId()))
                .orElse(null);

        // (1) 공통 서류부터 — 절대 빈 리스트가 되지 않도록 항상 시작점으로 둔다.
        List<PolicyChecklistResponse.ChecklistItem> checklist = new ArrayList<>(COMMON_DOCS);

        // (2) 정책별 특이사항 (AI 아님, extractor가 채운 카드 데이터에서만 추가)
        checklist.addAll(extractCardDocs(card));

        return PolicyChecklistResponse.builder()
                .applicationPeriod(formatPeriod(policy))
                .department(resolveDepartment(policy))
                .applicationUrl(resolveUrl(policy))
                .applicationChecklist(checklist)
                .build();
    }

    // policy_card.eligibility.documents 배열만 체크리스트 항목으로 변환. required_flags는 문서가 아니므로 표시하지 않는다.
    private List<PolicyChecklistResponse.ChecklistItem> extractCardDocs(PolicyCard card) {
        List<PolicyChecklistResponse.ChecklistItem> result = new ArrayList<>();
        JsonNode elig = parseEligibility(card);

        // 공통 서류와 라벨이 겹치면 중복 추가하지 않도록 정규화 집합으로 관리
        Set<String> seen = new LinkedHashSet<>();
        COMMON_DOCS.forEach(c -> seen.add(normalize(c.getLabel())));

        JsonNode documents = elig.path("documents");
        if (!documents.isArray()) return result;

        int idx = 0;
        for (JsonNode document : documents) {
            if (!document.isTextual()) continue;
            String label = document.asText().trim();
            if (label.isEmpty() || !seen.add(normalize(label))) continue; // 빈값·중복 제외
            result.add(item("card_document_" + idx++, label, false,
                    "공고문에 언급된 서류예요. 필수 여부는 원문을 확인해 주세요."));
        }
        return result;
    }

    private JsonNode parseEligibility(PolicyCard card) {
        if (card == null || !isUsableExtraction(card.getExtractionStatus())
                || card.getFullJson() == null || card.getFullJson().isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }
        try {
            return objectMapper.readTree(card.getFullJson()).path("eligibility");
        } catch (Exception e) {
            log.warn("[checklist] fullJson 파싱 실패 (policyId={}) → 공통 서류만 제공: {}",
                    card.getPolicyId(), e.getMessage());
            return JsonNodeFactory.instance.objectNode();
        }
    }

    private boolean isUsableExtraction(String extractionStatus) {
        return "extracted".equals(extractionStatus)
                || "partially_extracted".equals(extractionStatus);
    }

    // 담당 부서 우선, 없으면 주관 기관, 그것도 없으면 지역
    private String resolveDepartment(Policy policy) {
        if (policy.getDepartment() != null && !policy.getDepartment().isBlank()) return policy.getDepartment();
        if (policy.getAgency() != null && !policy.getAgency().isBlank()) return policy.getAgency();
        return policy.getRegion();
    }

    // 신청 URL 없으면 상세 URL로 폴백
    private String resolveUrl(Policy policy) {
        if (policy.getApplicationUrl() != null && !policy.getApplicationUrl().isBlank()) return policy.getApplicationUrl();
        return policy.getDetailUrl();
    }

    private String formatPeriod(Policy policy) {
        if (policy.getOriginalApplicationPeriod() != null && !policy.getOriginalApplicationPeriod().isBlank()) {
            return policy.getOriginalApplicationPeriod().trim();
        }
        String start = policy.getApplicationStartDate() != null ? policy.getApplicationStartDate().format(DATE_FORMATTER) : "";
        String end = policy.getApplicationEndDate() != null ? policy.getApplicationEndDate().format(DATE_FORMATTER) : "";
        if (start.isEmpty() && end.isEmpty()) return "기간 정보 없음 — 원문 공고 확인";
        return start + " ~ " + end;
    }

    private String normalize(String label) {
        return label.replaceAll("\\s+", "").toLowerCase();
    }

    private static PolicyChecklistResponse.ChecklistItem item(String id, String label, boolean required, String description) {
        return PolicyChecklistResponse.ChecklistItem.builder()
                .id(id).label(label).required(required).description(description).build();
    }
}
