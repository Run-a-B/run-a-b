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
    void unconfiguredOpenAiReturnsEmptySummaryWithoutGeneratingOrCaching(String apiKey) {
        OpenAiClient unconfiguredClient = spy(new NoNetworkOpenAiClient(apiKey));
        service = serviceWith(unconfiguredClient);
        given(policySummaryRepository.findByPolicyId(POLICY_ID)).willReturn(Optional.empty());
        given(policyRepository.findById(POLICY_ID)).willReturn(Optional.of(policy()));

        PolicySummaryResponse result = service.getOrGenerate(POLICY_ID);

        assertThat(result.getSummaryLines()).isEmpty();
        assertThat(result.getHighlights()).isEmpty();
        assertThat(result.getExpandedDescription()).isNull();
        assertThat(result.getExpandedApplicationMethod()).isNull();
        verify(unconfiguredClient, never()).chatJson(anyString(), anyString());
        verify(policySummaryRepository, never()).save(any());
    }

    @Test
    void cacheHitIsReturnedWithoutCheckingOpenAiConfiguration() {
        given(policySummaryRepository.findByPolicyId(POLICY_ID)).willReturn(Optional.of(
                PolicySummary.builder()
                        .policyId(POLICY_ID)
                        .summaryLinesJson("[\"cached line\"]")
                        .highlightsJson("[]")
                        .expandedDescription("cached description")
                        .expandedApplicationMethod("cached method")
                        .build()));

        PolicySummaryResponse result = service.getOrGenerate(POLICY_ID);

        assertThat(result.getSummaryLines()).containsExactly("cached line");
        assertThat(result.getHighlights()).isEmpty();
        assertThat(result.getExpandedDescription()).isEqualTo("cached description");
        assertThat(result.getExpandedApplicationMethod()).isEqualTo("cached method");
        verifyNoInteractions(policyRepository, openAiClient);
        verify(policySummaryRepository, never()).save(any());
    }

    @Test
    void missingPolicyStillUsesPolicyNotFoundWithoutCallingOpenAi() {
        given(policySummaryRepository.findByPolicyId(POLICY_ID)).willReturn(Optional.empty());
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
        JsonNode generatedJson = objectMapper.readTree("""
                {
                  "summary_lines": ["generated line"],
                  "highlights": [
                    {"icon": "check", "label": "지원 대상", "content": "generated detail"}
                  ],
                  "expanded_description": "generated description",
                  "expanded_application_method": "generated method"
                }
                """);
        doReturn(generatedJson).when(configuredClient).chatJson(anyString(), anyString());
        service = serviceWith(configuredClient);
        given(policySummaryRepository.findByPolicyId(POLICY_ID)).willReturn(Optional.empty());
        given(policyRepository.findById(POLICY_ID)).willReturn(Optional.of(policy()));

        PolicySummaryResponse result = service.getOrGenerate(POLICY_ID);

        assertThat(result.getSummaryLines()).containsExactly("generated line");
        assertThat(result.getExpandedDescription()).isEqualTo("generated description");
        assertThat(result.getExpandedApplicationMethod()).isEqualTo("generated method");
        verify(configuredClient).chatJson(anyString(), anyString());

        ArgumentCaptor<PolicySummary> cacheEntry = ArgumentCaptor.forClass(PolicySummary.class);
        verify(policySummaryRepository).save(cacheEntry.capture());
        assertThat(cacheEntry.getValue().getPolicyId()).isEqualTo(POLICY_ID);
        assertThat(cacheEntry.getValue().getSummaryLinesJson()).isEqualTo("[\"generated line\"]");
        assertThat(cacheEntry.getValue().getExpandedDescription()).isEqualTo("generated description");
        assertThat(cacheEntry.getValue().getExpandedApplicationMethod()).isEqualTo("generated method");
    }

    private PolicySummaryService serviceWith(OpenAiClient client) {
        return new PolicySummaryService(policyRepository, policySummaryRepository, client, objectMapper);
    }

    private Policy policy() {
        return Policy.builder()
                .externalId("PBLN_TEST")
                .title("테스트 정책")
                .description("테스트 설명")
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
