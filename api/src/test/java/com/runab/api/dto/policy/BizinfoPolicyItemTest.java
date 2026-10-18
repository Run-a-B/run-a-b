package com.runab.api.dto.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BizinfoPolicyItemTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void retainsOfficialSourceFieldsAndBothFileRoles() throws Exception {
        var item = mapper.readValue("""
                {
                  "pblancId": "PBLN_SOURCE", "pblancNm": "광양시 지원사업",
                  "description": "<p>공식 공고 본문</p>", "bsnsSumryCn": "카드 요약",
                  "hashTags": "자금,전남광주", "reqstMthPapersCn": "온라인 접수",
                  "reqstDt": "20261001 ~ 20261031", "reqstBeginEndDe": "예산 소진 시까지",
                  "excInsttNm": "광양상공회의소", "refrncNm": "기업지원팀 061-000-0000",
                  "flpthNm": "/files/application.hwp", "fileNm": "신청서.hwp",
                  "printFlpthNm": "/files/notice.pdf", "printFileNm": "공고문.pdf",
                  "unknownFutureField": "ignored"
                }
                """, BizinfoPolicyItem.class);

        var fields = mapper.valueToTree(item);
        assertThat(fields.path("description").asText()).isEqualTo("<p>공식 공고 본문</p>");
        assertThat(fields.path("bsnsSumryCn").asText()).isEqualTo("카드 요약");
        assertThat(item.getHashtags()).isEqualTo("자금,전남광주");
        assertThat(fields.path("reqstMthPapersCn").asText()).isEqualTo("온라인 접수");
        assertThat(fields.path("reqstDt").asText()).isEqualTo("20261001 ~ 20261031");
        assertThat(fields.path("reqstBeginEndDe").asText()).isEqualTo("예산 소진 시까지");
        assertThat(fields.path("excInsttNm").asText()).isEqualTo("광양상공회의소");
        assertThat(fields.path("refrncNm").asText()).isEqualTo("기업지원팀 061-000-0000");
        assertThat(fields.path("flpthNm").asText()).isEqualTo("/files/application.hwp");
        assertThat(fields.path("fileNm").asText()).isEqualTo("신청서.hwp");
        assertThat(fields.path("printFlpthNm").asText()).isEqualTo("/files/notice.pdf");
        assertThat(fields.path("printFileNm").asText()).isEqualTo("공고문.pdf");
    }

    @Test
    void stillAcceptsLegacyLowercaseHashtags() throws Exception {
        var item = mapper.readValue("{\"hashtags\":\"서울\"}", BizinfoPolicyItem.class);
        assertThat(item.getHashtags()).isEqualTo("서울");
    }
}
