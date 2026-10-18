package com.runab.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.runab.api.client.BizinfoApiClient;
import com.runab.api.dto.policy.BizinfoPolicyItem;
import com.runab.api.entity.Policy;
import com.runab.api.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class PolicySyncServiceTest {

    @Autowired private PolicyRepository repository;
    @Autowired private TestEntityManager entityManager;
    @Mock private BizinfoApiClient client;

    private final ObjectMapper mapper = new ObjectMapper();
    private PolicySyncService service;

    @BeforeEach
    void setUp() {
        service = new PolicySyncService(client, repository);
    }

    @Test
    void persistsLongOfficialSourceAlongsideCompactSynopsisAndTypedFiles() throws Exception {
        String html = "<p>공식 상세 본문 &amp; 신청 조건</p>" + "<p>반복 조건</p>".repeat(8000);
        var payload = payload().put("description", html);

        Policy policy = sync(payload);

        assertThat(policy.getDescription()).isEqualTo("카드 요약");
        assertThat(policy).extracting("originalNoticeHtml").isEqualTo(html);
        assertThat(policy).extracting("originalNoticeText")
                .isEqualTo("공식 상세 본문 & 신청 조건\n" + "반복 조건\n".repeat(8000).stripTrailing());
        assertThat(policy).extracting("originalApplicationMethod").isEqualTo("온라인 접수\n신청서 제출");
        assertThat(policy).extracting("originalApplicationPeriod").isEqualTo("20261001 ~ 20261031");
        assertThat(policy).extracting("executingAgency").isEqualTo("광양상공회의소");
        assertThat(policy).extracting("inquiryContact").isEqualTo("기업지원팀 061-000-0000");
        assertThat(policy).extracting("attachmentName", "attachmentUrl", "noticeFileName", "noticeFileUrl")
                .containsExactly("신청서.hwp", "https://www.bizinfo.go.kr/files/application.hwp",
                        "공고문.pdf", "https://www.bizinfo.go.kr/files/notice.pdf");
        assertThat(policy.getApplicationStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(policy.getApplicationEndDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(policy.getPublishedDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(policy.getRegion()).isEqualTo("전라남도");
    }

    @Test
    void repeatsUpdatesWithoutDuplicatesOrErasingEnrichmentOnBlankFields() throws Exception {
        sync(payload().put("description", "<p>공식 상세</p>"));
        Policy updated = sync(payload().put("description", "<p>수정된 공식 상세</p>"));
        assertThat(updated).extracting("originalNoticeText").isEqualTo("수정된 공식 상세");

        var transientPayload = payload();
        for (String field : List.of("description", "bsnsSumryCn", "reqstMthPapersCn", "reqstDt",
                "excInsttNm", "refrncNm", "flpthNm", "fileNm", "printFlpthNm", "printFileNm")) {
            transientPayload.put(field, " ");
        }
        Policy preserved = sync(transientPayload);

        assertThat(repository.count()).isEqualTo(1);
        assertThat(preserved).extracting("originalNoticeHtml", "originalNoticeText", "originalApplicationMethod",
                        "originalApplicationPeriod", "executingAgency", "inquiryContact",
                        "attachmentName", "attachmentUrl", "noticeFileName", "noticeFileUrl")
                .containsExactly("<p>수정된 공식 상세</p>", "수정된 공식 상세", "온라인 접수\n신청서 제출",
                        "20261001 ~ 20261031", "광양상공회의소", "기업지원팀 061-000-0000",
                        "신청서.hwp", "https://www.bizinfo.go.kr/files/application.hwp",
                        "공고문.pdf", "https://www.bizinfo.go.kr/files/notice.pdf");
        assertThat(preserved.getDescription()).isEqualTo("카드 요약");
        assertThat(preserved.getApplicationStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(preserved.getApplicationEndDate()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void blankNoticeOnLaterUpdateDoesNotReplaceRichTextWithSynopsisFallback() throws Exception {
        sync(payload().put("description", "<p>풍부한 공식 상세</p>"));
        Policy policy = sync(payload().put("description", "<p>&nbsp;</p>").put("bsnsSumryCn", "새 카드 요약"));
        assertThat(policy.getDescription()).isEqualTo("새 카드 요약");
        assertThat(policy).extracting("originalNoticeHtml", "originalNoticeText")
                .containsExactly("<p>풍부한 공식 상세</p>", "풍부한 공식 상세");
    }

    @Test
    void fallsBackToSynopsisWithoutInventingMissingSourceFacts() throws Exception {
        var payload = mapper.createObjectNode().put("pblancId", "PBLN_SOURCE").put("pblancNm", "지원사업")
                .put("bsnsSumryCn", "<p>요약 &amp; 조건</p>").put("reqstBeginEndDe", " 예산 소진 시까지 ");
        Policy policy = sync(payload);

        assertThat(policy).extracting("originalNoticeHtml").isNull();
        assertThat(policy).extracting("originalNoticeText").isEqualTo("요약 & 조건");
        assertThat(policy).extracting("originalApplicationPeriod").isEqualTo(" 예산 소진 시까지 ");
        assertThat(policy).extracting("executingAgency", "inquiryContact", "attachmentName", "attachmentUrl",
                "noticeFileName", "noticeFileUrl").containsOnlyNulls();
        assertThat(policy.getApplicationStartDate()).isNull();
        assertThat(policy.getApplicationEndDate()).isNull();
    }

    @Test
    void supportsHyphenatedLegacyPeriodAndAbsoluteFileUrls() throws Exception {
        var payload = payload().put("reqstDt", " ").put("reqstBeginEndDe", "2026-10-02 ~ 2026-11-03")
                .put("flpthNm", "https://files.bizinfo.go.kr/form.hwp").put("printFlpthNm", "files/notice.pdf");
        Policy policy = sync(payload);
        assertThat(policy.getApplicationStartDate()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(policy.getApplicationEndDate()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(policy).extracting("attachmentUrl", "noticeFileUrl")
                .containsExactly("https://files.bizinfo.go.kr/form.hwp", "https://www.bizinfo.go.kr/files/notice.pdf");
    }

    @Test
    void preservesMalformedPeriodWithoutBreakingTheSync() throws Exception {
        Policy policy = sync(payload().put("reqstDt", "~"));
        assertThat(policy).extracting("originalApplicationPeriod").isEqualTo("~");
        assertThat(policy.getApplicationStartDate()).isNull();
        assertThat(policy.getApplicationEndDate()).isNull();
    }

    @Test
    void nonDatePeriodUpdateClearsStaleParsedDates() throws Exception {
        sync(payload());
        Policy policy = sync(payload().put("reqstDt", "예산 소진 시까지"));
        assertThat(policy).extracting("originalApplicationPeriod").isEqualTo("예산 소진 시까지");
        assertThat(policy.getApplicationStartDate()).isNull();
        assertThat(policy.getApplicationEndDate()).isNull();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "기술,전남광주|광양시 소상공인 지원|전라남도",
            "기술,전남광주|[광주] 소상공인 지원|광주광역시",
            "기술,전남광주|[전남] 기업 지원|전라남도",
            "기술,전남광주|전남광주 기업 지원|NULL",
            "기술,전남광주|기업 지원|NULL",
            "기술,전남광주|서구 기업 지원|NULL",
            "기술,서울|광양시 기업 지원|서울특별시",
            "기술,전라남도|기업 지원|전라남도",
            "기술|기업 지원|전국"
    }, delimiter = '|', nullValues = "NULL")
    void onlyResolvesCombinedRegionalTagsWithUnambiguousEvidence(String tags, String title, String region) throws Exception {
        Policy policy = sync(payload().put("hashTags", tags).put("pblancNm", title));
        assertThat(policy.getRegion()).isEqualTo(region);
    }

    private ObjectNode payload() {
        return mapper.createObjectNode().put("pblancId", "PBLN_SOURCE").put("pblancNm", "광양시 지원사업")
                .put("bsnsSumryCn", "<p>카드 요약</p>").put("hashTags", "자금,전남광주")
                .put("reqstMthPapersCn", "<p>온라인 접수<br>신청서 제출</p>")
                .put("reqstDt", "20261001 ~ 20261031").put("creatPnttm", "20260930 12:00:00")
                .put("excInsttNm", "광양상공회의소").put("refrncNm", "기업지원팀 061-000-0000")
                .put("flpthNm", "/files/application.hwp").put("fileNm", "신청서.hwp")
                .put("printFlpthNm", "/files/notice.pdf").put("printFileNm", "공고문.pdf");
    }

    private Policy sync(ObjectNode payload) throws Exception {
        given(client.fetchAllPolicies()).willReturn(List.of(mapper.treeToValue(payload, BizinfoPolicyItem.class)));
        service.syncPolicies();
        entityManager.flush();
        entityManager.clear();
        return repository.findByExternalId("PBLN_SOURCE").orElseThrow();
    }
}
