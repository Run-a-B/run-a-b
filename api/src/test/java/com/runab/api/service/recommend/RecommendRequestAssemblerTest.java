package com.runab.api.service.recommend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.runab.api.dto.recommend.AiRecommendRequest;
import com.runab.api.entity.BusinessInfo;
import com.runab.api.entity.PolicyCard;
import com.runab.api.repository.BusinessInfoRepository;
import com.runab.api.repository.PolicyCardRepository;
import com.runab.api.service.matcher.EligibilityMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RecommendRequestAssembler가 최종 명세 형식(snake_case + candidate.match)의 JSON을 조립하는지 검증.
 * 리포지토리만 mock하고, selector/matcher/조립은 실제 로직으로 구동한다.
 * 샘플 JSON은 build/sample-recommend-request.json 으로도 떨어뜨린다.
 */
class RecommendRequestAssemblerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    @DisplayName("BusinessInfo + PolicyCard로 snake_case AI 요청 JSON이 조립된다 (candidate에 match 포함)")
    void assemble_producesSpecJson() throws Exception {
        BusinessInfoRepository biRepo = mock(BusinessInfoRepository.class);
        PolicyCardRepository cardRepo = mock(PolicyCardRepository.class);
        EligibilityMatcher matcher = new EligibilityMatcher(objectMapper);
        CandidateSelector selector = new CandidateSelector(matcher);
        RecommendRequestAssembler assembler =
                new RecommendRequestAssembler(biRepo, cardRepo, selector, objectMapper);

        BusinessInfo info = BusinessInfo.builder()
                .businessStatus(true)
                .jobCategory("음식점업")
                .region("서울특별시")
                .annualRevenue(500_000_000L)
                .employeeCount(10)
                .build();

        String fullJson = """
            {
              "policy_id": "bizinfo_support_program:PBLN_000000000123405",
              "category": "금융",
              "application_period": { "start": "2026-06-15", "end": "2026-06-29", "status": "open" },
              "benefit": { "benefit_type": "loan_low_interest" },
              "eligibility": {
                "regions": ["서울특별시"], "region_condition_type": "specific",
                "industries": ["음식점업"], "industry_condition_type": "specific",
                "business_types": ["중소기업"],
                "max_revenue": 1000000000, "max_employees": 50,
                "business_stages": ["운영중"], "owner_types": [],
                "required_flags": [], "excluded_targets": []
              },
              "attachment_analysis": { "status": "documents_found" }
            }
            """;
        PolicyCard card = PolicyCard.builder()
                .policyId("bizinfo_support_program:PBLN_000000000123405")
                .externalId("PBLN_000000000123405")
                .applicationStatus("open")
                .extractionStatus("extracted")
                .fullJson(fullJson)
                .build();

        when(biRepo.findByUserId(1L)).thenReturn(Optional.of(info));
        when(cardRepo.findAll()).thenReturn(List.of(card));

        AiRecommendRequest request = assembler.assemble(1L);
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);

        // 형식 확인용 샘플 파일 출력
        Files.createDirectories(Path.of("build"));
        Files.writeString(Path.of("build/sample-recommend-request.json"), json);

        // 최상위 snake_case 키
        assertThat(json).contains("\"request_id\"", "\"reference_date\"", "\"top_n\"",
                "\"user_business_info\"", "\"candidate_policies\"", "\"output_requirements\"");
        // user_business_info 변환/unknown 채움
        assertThat(json).contains("\"business_stage\" : \"운영중\"", "\"main_business_goal\" : \"unknown\"");
        // candidate에 match(camelCase) 포함 + 점수
        // business_types가 비어있지 않으나 user에 business_type 필드가 없어 UNKNOWN(8*0.4) → 100이 아닌 95
        assertThat(json).contains("\"match\"", "\"matcherVersion\" : \"eligibility_matcher_v1\"",
                "\"eligibilityScore\" : 95");
        // request_id 포맷
        assertThat(request.getRequestId()).startsWith("req_");
        assertThat(request.getCandidatePolicies()).hasSize(1);
    }

    @Test
    @DisplayName("PolicyCard가 0개여도 candidate_policies 빈 리스트로 정상 조립된다")
    void assemble_noCards_ok() {
        BusinessInfoRepository biRepo = mock(BusinessInfoRepository.class);
        PolicyCardRepository cardRepo = mock(PolicyCardRepository.class);
        CandidateSelector selector = new CandidateSelector(new EligibilityMatcher(objectMapper));
        RecommendRequestAssembler assembler =
                new RecommendRequestAssembler(biRepo, cardRepo, selector, objectMapper);

        BusinessInfo info = BusinessInfo.builder()
                .businessStatus(false).jobCategory("도소매업").region("부산광역시").build();
        when(biRepo.findByUserId(1L)).thenReturn(Optional.of(info));
        when(cardRepo.findAll()).thenReturn(List.of());

        AiRecommendRequest request = assembler.assemble(1L);
        assertThat(request.getCandidatePolicies()).isEmpty();
        assertThat(request.getUserBusinessInfo().getBusinessStage()).isEqualTo("준비중");
    }
}
