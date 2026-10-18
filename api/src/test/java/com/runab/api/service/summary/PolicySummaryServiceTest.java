package com.runab.api.service.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runab.api.dto.policy.PolicySummaryResponse;
import com.runab.api.entity.Policy;
import com.runab.api.entity.PolicySummary;
import com.runab.api.exception.ErrorCode;
import com.runab.api.repository.PolicyRepository;
import com.runab.api.repository.PolicySummaryRepository;
import com.runab.api.service.ai.OpenAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class PolicySummaryServiceTest {

    private static final Long POLICY_ID = 42L;

    @Mock
    private PolicyRepository policyRepository;
    @Mock
    private PolicySummaryRepository policySummaryRepository;
    @Mock
    private OpenAiClient openAiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PolicySummaryService service;

    @BeforeEach
    void setUp() {
        service = serviceWith(openAiClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void unconfiguredOpenAiFailsExplicitlyWithoutGeneratingOrCaching(String apiKey) {
        OpenAiClient unconfiguredClient = spy(new NoNetworkOpenAiClient(apiKey));
        service = serviceWith(unconfiguredClient);
        given(policySummaryRepository.findByPolicyId(POLICY_ID)).willReturn(Optional.empty());
        given(policyRepository.findById(POLICY_ID)).willReturn(Optional.of(policy()));

        assertThatThrownBy(() -> service.getOrGenerate(POLICY_ID))
                .extracting("errorCode.code").isEqualTo("AI_NOT_CONFIGURED");
        verify(unconfiguredClient, never()).chatJson(anyString(), anyString());
        verify(policySummaryRepository, never()).save(any());
    }

    @Test
    void legacyCacheWithoutFingerprintIsRegenerated() throws Exception {
        given(policySummaryRepository.findByPolicyId(POLICY_ID)).willReturn(Optional.of(
                PolicySummary.builder()
                        .policyId(POLICY_ID)
                        .summaryLinesJson("[\"cached line\"]")
                        .highlightsJson("[]")
                        .expandedDescription("cached description")
                        .expandedApplicationMethod("cached method")
                        .build()));

        given(policyRepository.findById(POLICY_ID)).willReturn(Optional.of(policy()));
        given(openAiClient.isConfigured()).willReturn(true);
        given(openAiClient.chatJson(anyString(), anyString())).willReturn(validSummary());
        PolicySummaryResponse result = service.getOrGenerate(POLICY_ID);
        assertThat(result.getSummaryLines()).hasSize(3);
        assertThat(result.getSummaryLines().get(0)).isEqualTo("지원 내용: 테스트 설명");
        ArgumentCaptor<PolicySummary> saved = ArgumentCaptor.forClass(PolicySummary.class);
        verify(policySummaryRepository).save(saved.capture());
        assertThat(saved.getValue()).extracting("sourceFingerprint").asString().hasSize(64);
    }

    @Test
    void missingPolicyStillUsesPolicyNotFoundWithoutCallingOpenAi() {
        given(policyRepository.findById(POLICY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrGenerate(POLICY_ID))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POLICY_NOT_FOUND);

        verifyNoInteractions(openAiClient);
        verify(policySummaryRepository, never()).save(any());
    }

    @Test
    void configuredOpenAiStillGeneratesAndCachesSummary() throws Exception {
        OpenAiClient configuredClient = spy(new OpenAiClient("test-key", "test-model", 1));
        JsonNode generatedJson = validSummary();
        doReturn(generatedJson).when(configuredClient).chatJson(anyString(), anyString());
        service = serviceWith(configuredClient);
        given(policySummaryRepository.findByPolicyId(POLICY_ID)).willReturn(Optional.empty());
        given(policyRepository.findById(POLICY_ID)).willReturn(Optional.of(policy()));

        PolicySummaryResponse result = service.getOrGenerate(POLICY_ID);

        assertThat(result.getSummaryLines()).hasSize(3);
        assertThat(result.getSummaryLines().get(0)).isEqualTo("지원 내용: 테스트 설명");
        assertThat(result.getExpandedDescription()).isEqualTo("테스트 설명");
        assertThat(result.getExpandedApplicationMethod()).isEqualTo("온라인 접수");
        verify(configuredClient).chatJson(anyString(), anyString());

        ArgumentCaptor<PolicySummary> cacheEntry = ArgumentCaptor.forClass(PolicySummary.class);
        verify(policySummaryRepository).save(cacheEntry.capture());
        assertThat(cacheEntry.getValue().getPolicyId()).isEqualTo(POLICY_ID);
        assertThat(cacheEntry.getValue().getSummaryLinesJson()).isEqualTo(objectMapper.writeValueAsString(result.getSummaryLines()));
        assertThat(cacheEntry.getValue().getExpandedDescription()).isEqualTo("테스트 설명");
        assertThat(cacheEntry.getValue().getExpandedApplicationMethod()).isEqualTo("온라인 접수");
    }

    @Test
    void missingSummaryContentIsRejectedBeforeCaching() throws Exception {
        given(policyRepository.findById(POLICY_ID)).willReturn(Optional.of(policy()));
        given(openAiClient.isConfigured()).willReturn(true);
        given(openAiClient.chatJson(anyString(), anyString())).willReturn(objectMapper.readTree("{}"));
        assertThatThrownBy(() -> service.getOrGenerate(POLICY_ID))
                .extracting("errorCode.code").isEqualTo("AI_INVALID_RESPONSE");
        verify(policySummaryRepository, never()).save(any());
    }

    @Test
    void originalSourceAndApplicationFactsReachTheModelWithBoundedInput() throws Exception {
        given(policyRepository.findById(POLICY_ID)).willReturn(Optional.of(Policy.builder()
                .title("정책").originalNoticeText("원문 본문\n\n" + "가".repeat(100_000))
                .originalApplicationMethod("온라인 원문 신청").originalApplicationPeriod("예산 소진 시까지")
                .executingAgency("실제 수행기관").inquiryContact("담당자 02-123-4567").build()));
        given(openAiClient.isConfigured()).willReturn(true);
        given(openAiClient.chatJson(anyString(), anyString())).willReturn(validSummary());
        service.getOrGenerate(POLICY_ID);
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).chatJson(system.capture(), prompt.capture());
        assertThat(prompt.getValue()).contains("원문 본문", "온라인 원문 신청", "예산 소진 시까지", "실제 수행기관", "담당자 02-123-4567");
        assertThat(prompt.getValue().length()).isLessThan(60_000);
        assertThat(system.getValue()).contains("신뢰할 수 없는");
    }

    private JsonNode validSummary() throws Exception {
        return objectMapper.readTree("""
                {"support_ids":["notice-0"],"eligibility_ids":[],"overview_ids":[]}
                """);
    }

    @Test
    void ungroundedRewriteCannotTurnMoveInDeadlineIntoProgramDuration() throws Exception {
        given(policyRepository.findById(POLICY_ID)).willReturn(Optional.of(semiconductorPolicy()));
        given(openAiClient.isConfigured()).willReturn(true);
        given(openAiClient.chatJson(anyString(), anyString())).willReturn(objectMapper.readTree("""
                {"summary_lines":["2026년까지 지원사업이 진행됩니다.","비수도권 중소기업 대상입니다.","최대 2,000만원 지원"],
                 "highlights":[{"icon":"calendar","label":"기간","content":"2026년까지"}]}
                """));
        assertThatThrownBy(() -> service.getOrGenerate(POLICY_ID))
                .extracting("errorCode.code").isEqualTo("AI_INVALID_RESPONSE");
        verify(policySummaryRepository, never()).save(any());
    }

    @Test
    void selectionPreservesAlternativeEligibilityAndUsesOnlyOfficialApplicationPeriod() throws Exception {
        given(policyRepository.findById(POLICY_ID)).willReturn(Optional.of(semiconductorPolicy()));
        given(openAiClient.isConfigured()).willReturn(true);
        given(openAiClient.chatJson(anyString(), anyString())).willReturn(objectMapper.readTree("""
                {"support_ids":["notice-2"],"eligibility_ids":["notice-1"],"overview_ids":["notice-0"]}
                """));
        PolicySummaryResponse result = service.getOrGenerate(POLICY_ID);
        assertThat(result.getSummaryLines().get(0)).contains("최대 2,000만원").doesNotContain("2026년");
        assertThat(result.getSummaryLines().get(1)).contains("다음 중 하나", "① 비수도권", "② 수도권", "2026년까지", "대구");
        assertThat(result.getSummaryLines().get(2)).isEqualTo("신청 기간: 예산 소진 시까지");
        assertThat(result.getExpandedDescription()).contains("반도체 검증", "② 수도권");
        assertThat(result.getExpandedApplicationMethod()).isEqualTo("이메일 접수 (example@example.org)");
    }

    private Policy semiconductorPolicy() {
        return Policy.builder().title("2026년 반도체 지원 공고")
                .targetGroup("중소기업")
                .originalNoticeText("반도체 검증 사업입니다.\n☞ 다음 중 하나에 해당하는 중소·중견기업\n① 비수도권 기업\n② 수도권 기업 중 2026년까지 대구 입주 예정 기업\n☞ 반도체 검증 지원(최대 2,000만원)")
                .originalApplicationPeriod("예산 소진 시까지")
                .originalApplicationMethod("이메일 접수 (example@example.org)").build();
    }

    @Test
    void oversizedUtf8ExpandedTextUsesOriginalFallbackWithoutBreakingMysqlTextStorage() throws Exception {
        given(policyRepository.findById(POLICY_ID)).willReturn(Optional.of(Policy.builder().title("긴 원문")
                .originalNoticeText("가".repeat(8_000) + "\n\n" + "나".repeat(8_000) + "\n\n" + "다".repeat(8_000))
                .originalApplicationMethod("한".repeat(24_000)).build()));
        given(openAiClient.isConfigured()).willReturn(true);
        given(openAiClient.chatJson(anyString(), anyString())).willReturn(objectMapper.readTree("""
                {"support_ids":["notice-1"],"eligibility_ids":["notice-2"],"overview_ids":["notice-0"]}
                """));
        var response = service.getOrGenerate(POLICY_ID);
        assertThat(response.getExpandedDescription()).isNull();
        assertThat(response.getExpandedApplicationMethod()).isNull();
        assertThat(response.getSummaryLines().get(0)).endsWith("나".repeat(8_000));
        verify(policySummaryRepository).save(any());
    }

    @Test
    void currentCacheIsReusedAndSourceChangesRegenerateTheSameEntry() throws Exception {
        Policy source = policy();
        AtomicReference<PolicySummary> stored = new AtomicReference<>();
        given(policyRepository.findById(POLICY_ID)).willReturn(Optional.of(source));
        given(policySummaryRepository.findByPolicyId(POLICY_ID)).willAnswer(call -> Optional.ofNullable(stored.get()));
        given(policySummaryRepository.save(any())).willAnswer(call -> { stored.set(call.getArgument(0)); return stored.get(); });
        given(openAiClient.isConfigured()).willReturn(true);
        given(openAiClient.chatJson(anyString(), anyString())).willReturn(validSummary());
        PolicySummaryResponse originalResponse = service.getOrGenerate(POLICY_ID);
        PolicySummary originalEntry = stored.get();
        PolicySummaryResponse cachedResponse = service.getOrGenerate(POLICY_ID);
        assertThat(cachedResponse.getHighlights()).hasSize(3);
        assertThat(cachedResponse.getHighlights().get(0).getContent()).isEqualTo(originalResponse.getHighlights().get(0).getContent());
        verify(openAiClient, times(1)).chatJson(anyString(), anyString());
        source.updateSourceFromBizinfo(null, "갱신된 원문", null, null, null, null, null, null, null, null);
        service.getOrGenerate(POLICY_ID);
        assertThat(stored.get()).isSameAs(originalEntry);
        verify(openAiClient, times(2)).chatJson(anyString(), anyString());
    }

    @Test
    void parallelColdReadsShareOnePaidGenerationInThisProcess() throws Exception {
        AtomicReference<PolicySummary> stored = new AtomicReference<>();
        given(policyRepository.findById(POLICY_ID)).willReturn(Optional.of(policy()));
        given(policySummaryRepository.findByPolicyId(POLICY_ID)).willAnswer(call -> Optional.ofNullable(stored.get()));
        given(policySummaryRepository.save(any())).willAnswer(call -> { stored.set(call.getArgument(0)); return stored.get(); });
        given(openAiClient.isConfigured()).willReturn(true);
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        JsonNode generated = validSummary();
        given(openAiClient.chatJson(anyString(), anyString())).willAnswer(call -> {
            entered.countDown();
            if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("generation never released");
            return generated;
        });
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var first = pool.submit(() -> service.getOrGenerate(POLICY_ID));
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> service.getOrGenerate(POLICY_ID));
            var third = pool.submit(() -> service.getOrGenerate(POLICY_ID));
            release.countDown();
            for (var future : java.util.List.of(first, second, third)) {
                assertThat(future.get(5, java.util.concurrent.TimeUnit.SECONDS).getSummaryLines()).hasSize(3);
            }
            verify(openAiClient, times(1)).chatJson(anyString(), anyString());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private PolicySummaryService serviceWith(OpenAiClient client) {
        return new PolicySummaryService(policyRepository, policySummaryRepository, client, objectMapper);
    }

    private Policy policy() {
        return Policy.builder()
                .externalId("PBLN_TEST")
                .title("테스트 정책")
                .description("테스트 설명")
                .applicationMethod("온라인 접수")
                .build();
    }

    private static class NoNetworkOpenAiClient extends OpenAiClient {

        private NoNetworkOpenAiClient(String apiKey) {
            super(apiKey, "test-model", 1);
        }

        @Override
        public JsonNode chatJson(String systemPrompt, String userPrompt) {
            throw new AssertionError("OpenAI must not be called without a configured API key");
        }
    }
}
