package com.runab.api.dto.policy;

import com.runab.api.entity.Policy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyDetailResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesExactOfficialSourceFactsAndOrderedFileRolesWithoutRawHtml() {
        String sourceText = "  첫 문단\n☞ 반복 조건\n☞ 반복 조건\n\n<script>문자 그대로</script>\n마지막 조항  ";
        Policy policy = Policy.builder()
                .externalId("PBLN_SOURCE")
                .title("공식 원문 정책")
                .originalNoticeHtml("<p>내부 보관 HTML</p>")
                .originalNoticeText(sourceText)
                .originalApplicationMethod("방문 접수\n담당자 확인")
                .originalApplicationPeriod("예산 소진 시까지")
                .executingAgency("수행 기관")
                .inquiryContact("지원팀 02-1234-5678")
                .attachmentName("신청서.hwp")
                .attachmentUrl("https://example.com/application.hwp")
                .noticeFileName("공고문.pdf")
                .noticeFileUrl("https://example.com/notice.pdf")
                .build();

        JsonNode response = objectMapper.valueToTree(PolicyDetailResponse.from(policy));

        assertThat(response.path("originalNoticeText").asText()).isEqualTo(sourceText);
        assertThat(response.path("originalApplicationMethod").asText()).isEqualTo("방문 접수\n담당자 확인");
        assertThat(response.path("originalApplicationPeriod").asText()).isEqualTo("예산 소진 시까지");
        assertThat(response.path("executingAgency").asText()).isEqualTo("수행 기관");
        assertThat(response.path("inquiryContact").asText()).isEqualTo("지원팀 02-1234-5678");
        assertThat(response.has("originalNoticeHtml")).isFalse();
        assertThat(response.path("sourceAttachments")).hasSize(2);
        assertThat(response.path("sourceAttachments").get(0).toString())
                .isEqualTo("{\"name\":\"신청서.hwp\",\"url\":\"https://example.com/application.hwp\",\"role\":\"attachment\"}");
        assertThat(response.path("sourceAttachments").get(1).toString())
                .isEqualTo("{\"name\":\"공고문.pdf\",\"url\":\"https://example.com/notice.pdf\",\"role\":\"notice\"}");
    }

    @Test
    void legacyPoliciesExposeNullableSourceFactsAndAnEmptyAttachmentList() {
        JsonNode response = objectMapper.valueToTree(PolicyDetailResponse.from(
                Policy.builder().externalId("PBLN_LEGACY").title("기존 정책").build()));

        assertThat(response.has("originalNoticeText")).isTrue();
        assertThat(response.path("originalNoticeText").isNull()).isTrue();
        assertThat(response.path("originalApplicationMethod").isNull()).isTrue();
        assertThat(response.path("sourceAttachments").isArray()).isTrue();
        assertThat(response.path("sourceAttachments")).isEmpty();
    }

    @Test
    void keepsPartialFileMetadataWithoutInventingMissingNamesOrUrls() {
        JsonNode response = objectMapper.valueToTree(PolicyDetailResponse.from(Policy.builder()
                .externalId("PBLN_PARTIAL").title("파일 정보 일부")
                .attachmentName("이름만 있는 첨부.hwp")
                .noticeFileUrl("https://example.com/notice.pdf")
                .build()));

        assertThat(response.path("sourceAttachments")).hasSize(2);
        assertThat(response.path("sourceAttachments").get(0).path("name").asText())
                .isEqualTo("이름만 있는 첨부.hwp");
        assertThat(response.path("sourceAttachments").get(0).path("url").isNull()).isTrue();
        assertThat(response.path("sourceAttachments").get(1).path("name").isNull()).isTrue();
        assertThat(response.path("sourceAttachments").get(1).path("role").asText()).isEqualTo("notice");
    }

    @Test
    void omitsFileRolesWithNoMeaningfulMetadata() {
        JsonNode response = objectMapper.valueToTree(PolicyDetailResponse.from(Policy.builder()
                .externalId("PBLN_BLANK_FILES").title("빈 파일 정보")
                .attachmentName(" \t").attachmentUrl("\n")
                .noticeFileName("공고문.pdf").noticeFileUrl("https://example.com/notice.pdf")
                .build()));

        assertThat(response.path("sourceAttachments")).hasSize(1);
        assertThat(response.path("sourceAttachments").get(0).path("role").asText()).isEqualTo("notice");
    }

    @Test
    void exposesTheStoredSourceSystem() {
        Policy policy = Policy.builder()
                .externalId("PBLN_TEST")
                .title("테스트 정책")
                .sourceSystem("bizinfo")
                .build();

        assertThat(PolicyDetailResponse.from(policy).getSourceSystem()).isEqualTo("bizinfo");
    }

    @Test
    void exposesThePolicyCardExtractionStatus() {
        Policy policy = Policy.builder()
                .externalId("PBLN_TEST")
                .title("테스트 정책")
                .build();

        assertThat(PolicyDetailResponse.from(policy, "partially_extracted").getExtractionStatus())
                .isEqualTo("partially_extracted");
    }
}
