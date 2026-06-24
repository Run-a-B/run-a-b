package com.runab.api.service.recommend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.runab.api.dto.recommend.AiRecommendRequest;
import com.runab.api.dto.recommend.OutputRequirementsDto;
import com.runab.api.dto.recommend.PipelineDto;
import com.runab.api.dto.recommend.UserBusinessInfoDto;
import com.runab.api.entity.BusinessInfo;
import com.runab.api.entity.PolicyCard;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import com.runab.api.repository.BusinessInfoRepository;
import com.runab.api.repository.PolicyCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 사용자 BusinessInfo + 선별된 candidate_policies를 최종 명세의 AI 요청 형식으로 조립한다.
 * 전송은 다음 단계 — 이번 단계 산출물은 조립된 AiRecommendRequest 객체.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendRequestAssembler {

    private static final int TOP_N = 5;
    private static final DateTimeFormatter REQ_ID_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BusinessInfoRepository businessInfoRepository;
    private final PolicyCardRepository policyCardRepository;
    private final CandidateSelector candidateSelector;
    private final ObjectMapper objectMapper;

    public AiRecommendRequest assemble(Long userId) {
        BusinessInfo info = businessInfoRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_INFO_REQUIRED));

        // MVP: 전체 카드 대상으로 선별 (데이터 0개여도 빈 리스트로 정상 동작)
        List<PolicyCard> cards = policyCardRepository.findAll();
        List<ScoredCandidate> selected = candidateSelector.select(info, cards);

        List<JsonNode> candidatePolicies = new ArrayList<>();
        for (ScoredCandidate sc : selected) {
            candidatePolicies.add(toCandidateNode(sc));
        }

        AiRecommendRequest request = AiRecommendRequest.builder()
                .requestId(generateRequestId())
                .referenceDate(LocalDate.now())
                .topN(TOP_N)
                .pipeline(PipelineDto.defaultPipeline())
                .userBusinessInfo(UserBusinessInfoDto.from(info))
                .candidatePolicies(candidatePolicies)
                .outputRequirements(OutputRequirementsDto.defaultRequirements())
                .build();

        log.info("[recommend] assemble 완료 - userId={}, 후보 {}건", userId, candidatePolicies.size());
        return request;
    }

    // PolicyCard.fullJson(파싱) + match 결과를 합친 candidate_policy 노드 생성
    private JsonNode toCandidateNode(ScoredCandidate sc) {
        PolicyCard card = sc.card();
        ObjectNode node;
        try {
            String json = card.getFullJson();
            JsonNode parsed = (json == null || json.isBlank())
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(json);
            node = parsed.isObject() ? (ObjectNode) parsed : objectMapper.createObjectNode();
        } catch (Exception e) {
            log.warn("[recommend] fullJson 파싱 실패 (policyId={}) → 빈 카드로 대체: {}",
                    card.getPolicyId(), e.getMessage());
            node = objectMapper.createObjectNode();
        }

        // policy_id 보강 (fullJson에 없으면 컬럼값 사용)
        if (!node.hasNonNull("policy_id") && card.getPolicyId() != null) {
            node.put("policy_id", card.getPolicyId());
        }
        // 매칭 결과 부착
        node.set("match", objectMapper.valueToTree(sc.match()));
        return node;
    }

    private String generateRequestId() {
        String date = LocalDate.now().format(REQ_ID_DATE);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return "req_" + date + "_" + suffix;
    }
}
