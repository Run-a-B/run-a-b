package com.runab.api.service.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runab.api.entity.BusinessInfo;
import com.runab.api.entity.Policy;
import com.runab.api.entity.PolicyCard;
import com.runab.api.entity.Report;
import com.runab.api.exception.BusinessException;
import com.runab.api.repository.*;
import com.runab.api.service.ai.OpenAiClient;
import com.runab.api.service.matcher.EligibilityMatcher;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyReportServiceTest {
    @Mock PolicyRepository policies;
    @Mock BusinessInfoRepository businesses;
    @Mock PolicyCardRepository cards;
    @Mock ReportRepository reports;
    @Mock OpenAiClient client;
    @Mock EntityManager entityManager;
    ObjectMapper mapper = new ObjectMapper();
    PolicyReportService service;

    @BeforeEach void setup() {
        service = new PolicyReportService(policies, businesses, cards, reports, client, mapper,
                new EligibilityMatcher(mapper), new TestTransactionManager(), entityManager);
    }

    void fixtures() {
        given(client.isConfigured()).willReturn(true);
        given(policies.findById(42L)).willReturn(Optional.of(Policy.builder().externalId("PBLN_TEST")
                .title("서울 마감 정책").region("서울특별시").applicationEndDate(LocalDate.of(2000, 1, 1))
                .originalNoticeText("실제 원문 본문").originalApplicationMethod("원문 온라인 신청")
                .originalApplicationPeriod("원문 기간").executingAgency("수행 기관").inquiryContact("문의 전화").build()));
        given(businesses.findByUserId(7L)).willReturn(Optional.of(BusinessInfo.builder()
                .businessStatus(true).region("부산광역시").jobCategory("카페/음료").build()));
    }

    @Test void malformedReportNeverReachesPersistence() throws Exception {
        fixtures();
        given(client.chatJson(anyString(), anyString())).willReturn(mapper.readTree("{}"));
        assertThatThrownBy(() -> service.generate(7L, 42L)).extracting("errorCode.code").isEqualTo("AI_INVALID_RESPONSE");
        verify(reports, never()).saveAndFlush(any());
    }

    @Test void reportReceivesOriginalFactsAndMetadataMismatchThenLimitsRepeatedPaidRequests() throws Exception {
        fixtures();
        given(client.chatJson(anyString(), anyString())).willReturn(sourceSelection());
        given(policies.findTop3ByCategoryAndIdNotOrderByPublishedDateDesc(any(), any())).willReturn(List.of());
        given(reports.saveAndFlush(any())).willAnswer(call -> call.getArgument(0));
        assertThat(service.generate(7L, 42L).getSummary()).contains("지역 조건", "신청 기간", "원문");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(client).chatJson(anyString(), prompt.capture());
        assertThat(prompt.getValue()).contains("실제 원문 본문", "원문 온라인 신청", "원문 기간", "수행 기관", "문의 전화", "hardFail) 여부: true");
        assertThatThrownBy(() -> service.generate(7L, 42L)).extracting("errorCode.code").isEqualTo("AI_RATE_LIMITED");
        verify(client, times(1)).chatJson(anyString(), anyString());
    }

    @Test void knownMismatchesRemainNegativeWithoutClaimingDefinitiveDisqualification() throws Exception {
        fixtures();
        given(client.chatJson(anyString(), anyString())).willReturn(sourceSelection());
        given(policies.findTop3ByCategoryAndIdNotOrderByPublishedDateDesc(any(), any())).willReturn(List.of());
        given(reports.saveAndFlush(any())).willAnswer(call -> call.getArgument(0));
        var response = service.generate(7L, 42L);
        assertThat(response.getDirection()).isEqualTo("negative");
        assertThat(response.getImpactLabel()).isNotEqualTo("신청 가능");
        assertThat(response.getSummary()).contains("지역 조건", "신청 기간").doesNotContain("모든 조건을 충족");
        assertThat(response.getSummary()).contains("신청 가능 여부를 확정할 수 없", "원문 예외");
        assertThat(response.getBusinessImpact()).isEmpty();
        assertThat(response.getDetails()).noneMatch(detail -> detail.contains("확실히 지원"));
    }

    @Test void sendsSavedBandsAndPartialEvidenceWithoutQuantitativeForecasts() throws Exception {
        fixtures();
        given(policies.findById(42L)).willReturn(Optional.of(Policy.builder().externalId("PBLN_TEST")
                .title("정책").region("전국").build()));
        given(businesses.findByUserId(7L)).willReturn(Optional.of(BusinessInfo.builder()
                .businessStatus(true).region("서울특별시").jobCategory("카페/음료")
                .annualRevenue(200_000_000L).employeeCount(2).build()));
        given(cards.findByPolicyId(anyString())).willReturn(Optional.of(PolicyCard.builder()
                .extractionStatus("extracted").fullJson("""
                {"eligibility":{"max_revenue":250000000,"max_employees":2}}
                """).build()));
        given(client.chatJson(anyString(), anyString())).willReturn(sourceSelection());
        given(policies.findTop3ByCategoryAndIdNotOrderByPublishedDateDesc(any(), any())).willReturn(List.of());
        given(reports.saveAndFlush(any())).willAnswer(call -> call.getArgument(0));
        var response = service.generate(7L, 42L);
        assertThat(response.getBusinessImpact()).isEmpty();
        assertThat(response.getDirection()).isEqualTo("neutral");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(client).chatJson(anyString(), prompt.capture());
        assertThat(prompt.getValue()).contains("연매출 구간: 1억원~3억원", "직원수 구간: 1~4명",
                "부분 충족(partial) 항목: [revenue, employees]")
                .doesNotContain("- 연매출: 200000000원", "- 직원수: 2명");
    }

    @Test void modelAuthoredLabelsAreRejectedBeforeSave() throws Exception {
        fixtures();
        var generated = sourceSelection().put("overall_label", "가".repeat(101));
        given(client.chatJson(anyString(), anyString())).willReturn(generated);
        lenient().when(reports.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        assertThatThrownBy(() -> service.generate(7L, 42L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo("AI_INVALID_RESPONSE");
        verify(reports, never()).saveAndFlush(any());
    }

    @Test void generatedRelatedPoliciesSurvivePersistenceMapping() throws Exception {
        fixtures();
        given(client.chatJson(anyString(), anyString())).willReturn(sourceSelection());
        Policy related = Policy.builder().externalId("PBLN_RELATED").title("관련 지원 정책").build();
        ReflectionTestUtils.setField(related, "id", 43L);
        given(policies.findTop3ByCategoryAndIdNotOrderByPublishedDateDesc(any(), any())).willReturn(List.of(related));
        given(reports.saveAndFlush(any())).willAnswer(call -> call.getArgument(0));

        var response = service.generate(7L, 42L);

        assertThat(response.getRelatedIds()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(43L);
            assertThat(item.getTitle()).isEqualTo("관련 지원 정책");
        });
    }

    @Test void storedJsonNullListsReturnEmptyArrays() {
        given(reports.findByUserIdAndPolicyId(7L, 42L)).willReturn(Optional.of(Report.builder()
                .userId(7L).policyId(42L).policyTitle("정책").detailsJson("null")
                .relatedIdsJson("null").businessImpactJson("null").build()));

        var response = service.getReport(7L, 42L);

        assertThat(response.getDetails()).isEmpty();
        assertThat(response.getRelatedIds()).isEmpty();
        assertThat(response.getBusinessImpact()).isEmpty();
    }

    @Test void reportPromptIncludesPolicyRegionAndUnverifiedRequiredFlags() throws Exception {
        fixtures();
        given(cards.findByPolicyId(anyString())).willReturn(Optional.of(PolicyCard.builder()
                .extractionStatus("extracted").fullJson("""
                {"eligibility":{"required_flags":["no_tax_arrears"]}}
                """).build()));
        given(client.chatJson(anyString(), anyString())).willReturn(sourceSelection());
        given(reports.saveAndFlush(any())).willAnswer(call -> call.getArgument(0));

        service.generate(7L, 42L);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(client).chatJson(anyString(), prompt.capture());
        assertThat(prompt.getValue()).contains("정책 대상 지역: 서울특별시", "no_tax_arrears=UNKNOWN");
    }

    @Test void providerCallRunsOutsideDatabaseTransactionAndSaveRunsInsideOne() throws Exception {
        fixtures();
        var transactionManager = new TestTransactionManager();
        TransactionInterceptor advice = new TransactionInterceptor();
        advice.setTransactionManager(transactionManager);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(service);
        proxyFactory.addAdvice(advice);
        PolicyReportService transactionalService = (PolicyReportService) proxyFactory.getProxy();
        var generated = sourceSelection();
        given(client.chatJson(anyString(), anyString())).willAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("provider call must not hold a database transaction open").isFalse();
            return generated;
        });
        lenient().when(reports.saveAndFlush(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("report writes must remain atomic").isTrue();
            return call.getArgument(0);
        });

        assertThat(transactionalService.generate(7L, 42L).getSummary()).contains("지역 조건");
    }

    @ParameterizedTest
    @ValueSource(strings = {"business", "policy", "card"})
    void changedSourceDuringGenerationDoesNotReplaceSavedReport(String changedSource) throws Exception {
        fixtures();
        BusinessInfo business = businesses.findByUserId(7L).orElseThrow();
        Policy policy = policies.findById(42L).orElseThrow();
        PolicyCard card = PolicyCard.builder().extractionStatus("extracted").fullJson("{}").build();
        given(cards.findByPolicyId(anyString())).willReturn(Optional.of(card));
        Report saved = Report.builder().userId(7L).policyId(42L).policyTitle("저장된 정책")
                .summary("기존 리포트 내용").build();
        lenient().when(reports.findByUserIdAndPolicyId(7L, 42L)).thenReturn(Optional.of(saved));
        lenient().when(reports.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var generated = sourceSelection();
        given(client.chatJson(anyString(), anyString())).willAnswer(call -> {
            switch (changedSource) {
                case "business" -> business.update(null, null, "대구광역시", null, null);
                case "policy" -> policy.updateSourceFromBizinfo(null, "변경된 원문", null, null, null,
                        null, null, null, null, null);
                case "card" -> card.updateCard("{\"eligibility\":{\"max_revenue\":1000000}}", null, "extracted");
                default -> throw new AssertionError(changedSource);
            }
            return generated;
        });

        assertThatThrownBy(() -> service.generate(7L, 42L)).isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo("REPORT_CONTEXT_CHANGED");
        assertThat(saved.getSummary()).isEqualTo("기존 리포트 내용");
        verify(reports, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"originalApplicationMethod", "applicationMethod", "originalApplicationPeriod"})
    void changedApplicationFieldBeyondPromptLimitDoesNotReplaceSavedReport(String field) throws Exception {
        fixtures();
        Policy policy = policies.findById(42L).orElseThrow();
        int promptLimit = switch (field) {
            case "originalApplicationMethod" -> 4_000;
            case "applicationMethod" -> 2_000;
            case "originalApplicationPeriod" -> 1_000;
            default -> throw new AssertionError(field);
        };
        String prefix = "가".repeat(promptLimit);
        if (field.equals("applicationMethod")) {
            ReflectionTestUtils.setField(policy, "originalApplicationMethod", null);
        }
        ReflectionTestUtils.setField(policy, field, prefix + "기존 안내");
        Report saved = Report.builder().userId(7L).policyId(42L).policyTitle("저장된 정책")
                .summary("기존 리포트 내용").build();
        lenient().when(reports.findByUserIdAndPolicyId(7L, 42L)).thenReturn(Optional.of(saved));
        lenient().when(reports.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        given(client.chatJson(anyString(), anyString())).willAnswer(call -> {
            assertThat((String) call.getArgument(1)).doesNotContain("기존 안내");
            ReflectionTestUtils.setField(policy, field, prefix + "수정 안내");
            return sourceSelection();
        });

        assertThatThrownBy(() -> service.generate(7L, 42L)).isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo("REPORT_CONTEXT_CHANGED");
        assertThat(saved.getSummary()).isEqualTo("기존 리포트 내용");
        verify(reports, never()).saveAndFlush(any());
    }

    @Test void sourceSnapshotReadsUseShortReadOnlyTransaction() throws Exception {
        fixtures();
        BusinessInfo business = businesses.findByUserId(7L).orElseThrow();
        int[] reads = {0};
        given(businesses.findByUserId(7L)).willAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("custom source queries must release their connection before provider call").isTrue();
            if (reads[0]++ == 0) {
                assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            }
            return Optional.of(business);
        });
        lenient().when(client.chatJson(anyString(), anyString())).thenReturn(sourceSelection());
        lenient().when(reports.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.generate(7L, 42L).getSummary()).contains("지역 조건");
    }

    @Test void reportPromptRequestsOnlySourceIdsWithoutAuthoredFacts() throws Exception {
        fixtures();
        given(client.chatJson(anyString(), anyString())).willReturn(sourceSelection());
        given(reports.saveAndFlush(any())).willAnswer(call -> call.getArgument(0));

        service.generate(7L, 42L);

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(client).chatJson(system.capture(), anyString());
        assertThat(system.getValue()).doesNotContain("분량을 늘리되", "2~3문장 이상", "\"summary\"", "\"overall_direction\"")
                .contains("relevant_source_ids", "문단 ID");
    }

    @Test void freeProseThatContradictsOfficialPeriodIsRejectedBeforePersistence() throws Exception {
        fixtures();
        given(client.chatJson(anyString(), anyString())).willReturn(mapper.readTree("""
                {"overall_direction":"positive","overall_label":"신청 가능","summary":"모든 조건을 충족합니다.",
                 "details":["신청 기간에 대한 정보는 확인되지 않습니다."],"business_impact":[]}
                """));
        lenient().when(reports.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        assertThatThrownBy(() -> service.generate(7L, 42L)).extracting("errorCode.code")
                .isEqualTo("AI_INVALID_RESPONSE");
        verify(reports, never()).saveAndFlush(any());
    }

    @Test void selectedSourcesPreserveOfficialPeriodAndAlternativeEligibilityAndExclusions() throws Exception {
        alternativeRegionFixtures();
        given(client.chatJson(anyString(), anyString())).willReturn(mapper.readTree("{\"relevant_source_ids\":[\"notice-3\"]}"));
        given(reports.saveAndFlush(any())).willAnswer(call -> call.getArgument(0));

        var response = service.generate(7L, 42L);

        assertThat(response.getDirection()).isEqualTo("negative");
        assertThat(response.getSummary()).contains("업종");
        assertThat(response.getDetails()).contains("신청 기간: 예산 소진 시까지", "신청 방법: 원문 온라인 신청")
                .anySatisfy(detail -> assertThat(detail).contains("경기도 소재 반도체 기업 또는 경기도 이전 예정인 반도체 기업"))
                .anySatisfy(detail -> assertThat(detail).contains("제외 대상: 국세 체납 기업"))
                .anySatisfy(detail -> assertThat(detail).contains("지역", "판단 불가"))
                .noneSatisfy(detail -> assertThat(detail).contains("지역 조건이 맞지"));
        assertThat(response.getBusinessImpact()).isEmpty();
    }

    @Test void unknownEligibilityIsNotPresentedAsConfirmedQualification() throws Exception {
        alternativeRegionFixtures();
        given(cards.findByPolicyId(anyString())).willReturn(Optional.of(PolicyCard.builder()
                .extractionStatus("extracted").fullJson("""
                {"eligibility":{"region_condition_type":"unknown","industry_condition_type":"all",
                 "required_flags":["no_tax_arrears"]}}
                """).build()));
        given(client.chatJson(anyString(), anyString())).willReturn(mapper.readTree("{\"relevant_source_ids\":[]}"));
        given(reports.saveAndFlush(any())).willAnswer(call -> call.getArgument(0));

        var response = service.generate(7L, 42L);

        assertThat(response.getDirection()).isEqualTo("neutral");
        assertThat(response.getImpactLabel()).isEqualTo("추가 조건 확인 필요");
        assertThat(response.getImpactStyle()).isEqualTo("bg-gray-100 text-gray-600");
        assertThat(response.getSummary()).contains("신청 가능 여부를 확정할 수 없");
        assertThat(response.getDetails()).anySatisfy(detail -> assertThat(detail).contains("no_tax_arrears", "UNKNOWN"))
                .anySatisfy(detail -> assertThat(detail).contains("경기도 소재 반도체 기업 또는 경기도 이전 예정인 반도체 기업"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"relevant_source_ids\":[\"invented\"]}",
            "{\"relevant_source_ids\":[\"notice-0\",\"notice-0\"]}",
            "{\"relevant_source_ids\":[],\"summary\":\"신청 가능\"}"})
    void invalidSourceSelectionNeverReachesPersistence(String modelOutput) throws Exception {
        fixtures();
        given(client.chatJson(anyString(), anyString())).willReturn(mapper.readTree(modelOutput));

        assertThatThrownBy(() -> service.generate(7L, 42L)).extracting("errorCode.code")
                .isEqualTo("AI_INVALID_RESPONSE");
        verify(reports, never()).saveAndFlush(any());
    }

    private void alternativeRegionFixtures() {
        fixtures();
        given(policies.findById(42L)).willReturn(Optional.of(Policy.builder().externalId("PBLN_TEST")
                .title("반도체 지원 정책").region("경기도").industry("제조업")
                .originalNoticeText("지원 안내\n☞ 지원 대상: 경기도 소재 반도체 기업 또는 경기도 이전 예정인 반도체 기업"
                        + "\n☞ 제외 대상: 국세 체납 기업\n☞ 기업당 최대 100만원 지원")
                .originalApplicationPeriod("예산 소진 시까지").originalApplicationMethod("원문 온라인 신청").build()));
        given(cards.findByPolicyId(anyString())).willReturn(Optional.of(PolicyCard.builder()
                .extractionStatus("extracted").fullJson("""
                {"eligibility":{"region_condition_type":"unknown","industries":["제조업"]}}
                """).build()));
    }

    private com.fasterxml.jackson.databind.node.ObjectNode sourceSelection() {
        var response = mapper.createObjectNode();
        response.putArray("relevant_source_ids");
        return response;
    }

    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {}
        @Override protected void doRollback(DefaultTransactionStatus status) {}
    }
}
