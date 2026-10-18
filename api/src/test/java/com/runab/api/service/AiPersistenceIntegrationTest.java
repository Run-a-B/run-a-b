package com.runab.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runab.api.dto.policy.PolicySummaryResponse;
import com.runab.api.entity.AuthProvider;
import com.runab.api.entity.BusinessInfo;
import com.runab.api.entity.Policy;
import com.runab.api.entity.User;
import com.runab.api.repository.BusinessInfoRepository;
import com.runab.api.repository.PolicyRepository;
import com.runab.api.repository.PolicySummaryRepository;
import com.runab.api.repository.ReportRepository;
import com.runab.api.repository.UserRepository;
import com.runab.api.service.ai.OpenAiClient;
import com.runab.api.service.report.PolicyReportService;
import com.runab.api.service.summary.PolicySummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
// H2's JSON type wraps JDBC setString input in a JSON string, unlike MySQL's JSON parsing.
// Preserve the production String round-trip while retaining JSON validity checks in this isolated H2 schema.
@Sql(statements = {
        "ALTER TABLE report ALTER COLUMN details_json VARCHAR",
        "ALTER TABLE report ALTER COLUMN related_ids_json VARCHAR",
        "ALTER TABLE report ALTER COLUMN business_impact_json VARCHAR",
        "ALTER TABLE policy_summary ALTER COLUMN summary_lines_json VARCHAR",
        "ALTER TABLE policy_summary ALTER COLUMN highlights_json VARCHAR",
        "ALTER TABLE report ADD CONSTRAINT IF NOT EXISTS ck_report_details_json CHECK (details_json IS JSON)",
        "ALTER TABLE report ADD CONSTRAINT IF NOT EXISTS ck_report_related_json CHECK (related_ids_json IS JSON)",
        "ALTER TABLE report ADD CONSTRAINT IF NOT EXISTS ck_report_impact_json CHECK (business_impact_json IS JSON)",
        "ALTER TABLE policy_summary ADD CONSTRAINT IF NOT EXISTS ck_summary_lines_json CHECK (summary_lines_json IS JSON)",
        "ALTER TABLE policy_summary ADD CONSTRAINT IF NOT EXISTS ck_summary_highlights_json CHECK (highlights_json IS JSON)"
})
class AiPersistenceIntegrationTest {
    private static final String DATABASE_NAME = "ai_persistence_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:h2:mem:" + DATABASE_NAME
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    }

    @Autowired PolicyReportService reportService;
    @Autowired PolicySummaryService summaryService;
    @Autowired ReportRepository reports;
    @Autowired PolicySummaryRepository summaries;
    @Autowired PolicyRepository policies;
    @Autowired UserRepository users;
    @Autowired BusinessInfoRepository businesses;
    @Autowired ObjectMapper mapper;
    @MockitoBean OpenAiClient client;
    @MockitoBean PolicySyncService policySyncService;

    // No test-level transaction: service transactions must commit and subsequent reads must deserialize stored JSON.
    @Test void generatedReportCommitsAndRestoresRelatedPolicyLinksAndTimestamps() throws Exception {
        String unique = UUID.randomUUID().toString();
        String category = "보고서-" + unique;
        User user = users.saveAndFlush(User.builder().email("ai-report-" + unique + "@example.test")
                .username("합성 사업자").provider(AuthProvider.LOCAL).build());
        businesses.saveAndFlush(BusinessInfo.builder().user(user).businessStatus(true)
                .jobCategory("카페/음료").region("서울특별시")
                .annualRevenue(200_000_000L).employeeCount(2).build());
        Policy policy = policies.saveAndFlush(Policy.builder().externalId("PBLN_" + unique)
                .title("합성 정책 원문").category(category).region("전국")
                .originalNoticeText("기업당 최대 100만원 지원").originalApplicationPeriod("예산 소진 시까지")
                .originalApplicationMethod("온라인 신청")
                .applicationStartDate(LocalDate.now().minusDays(1))
                .applicationEndDate(LocalDate.now().plusDays(30)).build());
        Policy related = policies.saveAndFlush(Policy.builder().externalId("PBLN_RELATED_" + unique)
                .title("합성 관련 정책").category(category).publishedDate(LocalDate.now()).build());
        given(client.isConfigured()).willReturn(true);
        var modelOutput = mapper.readTree("""
                {"relevant_source_ids":["notice-0"]}
                """);
        given(client.chatJson(anyString(), anyString())).willAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return modelOutput;
        });

        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var generated = reportService.generate(user.getId(), policy.getId());
        var stored = reports.findByUserIdAndPolicyId(user.getId(), policy.getId()).orElseThrow();
        var restored = reportService.getReport(user.getId(), policy.getId());

        assertThat(stored.getId()).isNotNull();
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
        assertThat(generated.getSavedAt()).isNotBlank();
        assertThat(LocalDateTime.parse(restored.getSavedAt())).isEqualTo(stored.getUpdatedAt());
        assertThat(restored.getSummary()).isEqualTo("등록한 사업 정보와 수집된 정책 조건을 비교한 검토 참고입니다. "
                + "이 비교만으로 신청 가능 여부를 확정할 수 없습니다. 최신 원문 공고와 담당 기관에서 확인해 주세요.");
        assertThat(restored.getDetails()).contains("원문 발췌: 기업당 최대 100만원 지원",
                "신청 기간: 예산 소진 시까지", "신청 방법: 온라인 신청");
        assertThat(generated.getRelatedIds()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(related.getId());
            assertThat(item.getTitle()).isEqualTo("합성 관련 정책");
        });
        assertThat(restored.getRelatedIds()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(related.getId());
            assertThat(item.getTitle()).isEqualTo("합성 관련 정책");
        });
        assertThat(restored.getBusinessImpact()).isEmpty();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        verify(client, times(1)).chatJson(anyString(), anyString());
    }

    @Test void secondSummaryReadRestoresThreeCachedHighlightsWithoutAnotherProviderCall() throws Exception {
        Policy policy = policies.saveAndFlush(Policy.builder().externalId("PBLN_SUMMARY_" + UUID.randomUUID())
                .title("합성 요약 정책").originalNoticeText("소상공인 경영 지원 사업입니다.")
                .supportScale("기업당 최대 100만원 지원").targetGroup("전국 소상공인")
                .originalApplicationPeriod("예산 소진 시까지").originalApplicationMethod("온라인 신청").build());
        given(client.isConfigured()).willReturn(true);
        given(client.getModel()).willReturn("synthetic-model");
        given(client.chatJson(anyString(), anyString())).willReturn(mapper.readTree("""
                {"support_ids":["support"],"eligibility_ids":["target"],"overview_ids":["notice-0"]}
                """));

        var generated = summaryService.getOrGenerate(policy.getId());
        var cached = summaryService.getOrGenerate(policy.getId());
        var stored = summaries.findByPolicyId(policy.getId()).orElseThrow();

        assertThat(stored.getId()).isNotNull();
        assertThat(stored.getSourceFingerprint()).hasSize(64);
        assertThat(generated.getSummaryLines()).containsExactly("지원 내용: 기업당 최대 100만원 지원",
                "지원 대상: 전국 소상공인", "신청 기간: 예산 소진 시까지");
        assertThat(cached.getSummaryLines()).containsExactly("지원 내용: 기업당 최대 100만원 지원",
                "지원 대상: 전국 소상공인", "신청 기간: 예산 소진 시까지");
        assertThat(cached.getHighlights()).extracting(PolicySummaryResponse.Highlight::getContent)
                .containsExactly("기업당 최대 100만원 지원", "전국 소상공인", "예산 소진 시까지");
        assertThat(cached.getExpandedApplicationMethod()).isEqualTo("온라인 신청");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        verify(client, times(1)).chatJson(anyString(), anyString());
    }
}
