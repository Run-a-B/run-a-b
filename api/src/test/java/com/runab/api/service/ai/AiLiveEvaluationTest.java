package com.runab.api.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runab.api.entity.BusinessInfo;
import com.runab.api.entity.Policy;
import com.runab.api.entity.PolicyCard;
import com.runab.api.repository.*;
import com.runab.api.service.matcher.EligibilityMatcher;
import com.runab.api.service.report.PolicyReportService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Opt-in paid evaluation. Only synthetic business information and public notice excerpts leave the process. */
@EnabledIfEnvironmentVariable(named = "RUNAB_LIVE_AI_TESTS", matches = "true")
class AiLiveEvaluationTest {
    @Test
    @Timeout(90)
    void publicSemiconductorNoticeDoesNotRecommendAnUnrelatedCafeAsEligible() {
        String key = System.getenv("OPENAI_API_KEY");
        assertThat(key != null && !key.isBlank()).as("live AI key must be configured").isTrue();
        ObjectMapper mapper = new ObjectMapper();
        OpenAiClient client = new OpenAiClient(key, System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini"), 60);
        PolicyRepository policies = mock(PolicyRepository.class);
        BusinessInfoRepository businesses = mock(BusinessInfoRepository.class);
        PolicyCardRepository cards = mock(PolicyCardRepository.class);
        ReportRepository reports = mock(ReportRepository.class);
        Policy policy = Policy.builder().externalId("LIVE_EVAL_PUBLIC_492").title("2026년 고신뢰 반도체 상용화를 위한 검증ㆍ확인 지원사업")
                .region("전국").industry("제조업").targetGroup("중소기업")
                .originalNoticeText("""
                        고신뢰 반도체 설계 기업(팹리스, IP 전문 기업 등)으로 중소기업 및 중견기업(창업 및 벤처기업 포함)이며, 다음 ①, ② 요건 중 하나라도 해당하는 기업
                        ① 비수도권(서울ㆍ인천ㆍ경기ㆍ해외 제외) 내 본사, 지사, 공장, 연구소 중 1개 이상 소재한 기업
                        ② 수도권 기업 중 2026년까지 (대구)지능형 반도체 개발지원센터 입주 예정인 기업
                        반도체 검증ㆍ확인(V&V) 지원(최대 2,000만원 이내)
                        기능안전 인증 및 기술 대응을 위한 핵심 산출물 생성, FMEDA 분석 및 시뮬레이션 지원
                        """).originalApplicationPeriod("예산 소진 시까지").build();
        ReflectionTestUtils.setField(policy, "id", 492L);
        when(policies.findById(492L)).thenReturn(Optional.of(policy));
        when(businesses.findByUserId(9001L)).thenReturn(Optional.of(BusinessInfo.builder()
                .businessStatus(true).jobCategory("카페/음료").region("서울특별시")
                .annualRevenue(200_000_000L).employeeCount(2).build()));
        when(cards.findByPolicyId(any())).thenReturn(Optional.of(PolicyCard.builder()
                .extractionStatus("extracted").fullJson("""
                        {"eligibility":{"industries":["제조업"],"industry_condition_type":"specific"}}
                        """).build()));
        when(reports.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        PolicyReportService service = new PolicyReportService(policies, businesses, cards, reports, client,
                mapper, new EligibilityMatcher(mapper), new MemoryTransactionManager(), mock(EntityManager.class));
        long start = System.nanoTime();
        var response = service.generate(9001L, 492L);
        long milliseconds = (System.nanoTime() - start) / 1_000_000;
        assertThat(response.getDirection()).isEqualTo("negative");
        assertThat(response.getDetails()).contains("신청 기간: 예산 소진 시까지")
                .anySatisfy(detail -> assertThat(detail).contains("수도권 기업 중 2026년까지"));
        assertThat(response.getBusinessImpact()).isEmpty();
        System.out.println("Live evaluation (synthetic cafe): milliseconds=" + milliseconds);
        System.out.println("Summary: " + response.getSummary());
        response.getDetails().forEach(System.out::println);
        verify(reports).saveAndFlush(any()); // Mock persistence only; no real account/report is changed.
    }

    private static final class MemoryTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {}
        @Override protected void doRollback(DefaultTransactionStatus status) {}
    }
}
