package com.runab.api.service.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runab.api.entity.Policy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SummarySourceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ordinarySupportParagraphIsNotForcedIntoEligibility() throws Exception {
        SummarySource source = new SummarySource(Policy.builder().description("사업 안내\n\n기업당 100만원 지원\n\n지원 대상: 부산 소재 소상공인").build());
        var ai = mapper.readTree("""
                {"support_ids":["notice-1"],"eligibility_ids":["notice-2"]}
                """);
        assertThat(source.select(ai, "support_ids", 2).get(0).text()).isEqualTo("기업당 100만원 지원");
        assertThat(source.select(ai, "eligibility_ids", 2).get(0).text()).contains("부산 소재 소상공인");
    }

    @Test
    void supportBeforeTargetBulletsDoesNotReverseTheirMeaning() throws Exception {
        SummarySource source = new SummarySource(Policy.builder().description("안내\n☞ 기업당 최대 100만원 지원\n☞ 지원 대상: 부산 소재 소상공인").build());
        var ai = mapper.readTree("""
                {"support_ids":["notice-1"],"eligibility_ids":["notice-2"]}
                """);
        assertThat(source.select(ai, "support_ids", 2).get(0).text()).contains("100만원");
        assertThat(source.select(ai, "eligibility_ids", 2).get(0).text()).contains("부산 소재 소상공인");
    }

    @Test
    void unknownAndDuplicatePassageReferencesAreRejected() throws Exception {
        SummarySource source = new SummarySource(Policy.builder().description("원문").build());
        for (String json : new String[]{"{\"support_ids\":[\"invented-id\"]}", "{\"support_ids\":[\"notice-0\",\"notice-0\"]}"}) {
            var ai = mapper.readTree(json);
            assertThatThrownBy(() -> source.select(ai, "support_ids", 2)).extracting("errorCode.code").isEqualTo("AI_INVALID_RESPONSE");
        }
    }

    @Test
    void titleYearNeverBecomesApplicationDeadline() {
        assertThat(SummarySource.applicationPeriod(Policy.builder().title("2026년 지원 공고").build()))
                .doesNotContain("2026").contains("신청 기간이 없습니다");
    }

    @Test
    void oversizedConditionBlockIsNotCutBeforeAnException() {
        SummarySource source = new SummarySource(Policy.builder().originalNoticeText("가".repeat(12_001) + " 단, 신청 제외")
                .targetGroup("중소기업").build());
        assertThat(source.passages()).extracting(SummarySource.Passage::id).containsExactly("target");
    }

    @Test
    void explicitExclusionsAreNeverDroppedWhenCompleteTargetBlockIsAdded() throws Exception {
        var source = new SummarySource(Policy.builder().description("안내\n☞ 지원 대상: 부산 소재 소상공인\n☞ 제외 대상: 국세 체납 기업\n☞ 기업당 100만원 지원").build());
        var ai = mapper.readTree("{\"eligibility_ids\":[\"notice-1\",\"notice-2\"]}");
        assertThat(source.select(ai, "eligibility_ids", 2)).extracting(SummarySource.Passage::id)
                .containsExactly("notice-1", "notice-2");
        assertThat(source.select(mapper.readTree("{\"eligibility_ids\":[\"notice-1\"]}"), "eligibility_ids", 2))
                .extracting(SummarySource.Passage::id).containsExactly("notice-1", "notice-2");
    }

    @Test
    void mentioningRequirementsInsideBenefitDoesNotMakeItTargetBlock() throws Exception {
        var source = new SummarySource(Policy.builder().description("안내\n☞ 신청 요건 충족 기업에 기업당 100만원 지원\n☞ 지원 대상: 부산 소재 소상공인").build());
        var ai = mapper.readTree("{\"support_ids\":[\"notice-1\"],\"eligibility_ids\":[\"notice-2\"]}");
        assertThat(source.select(ai, "support_ids", 2)).extracting(SummarySource.Passage::id).containsExactly("notice-1");
        assertThat(source.select(ai, "eligibility_ids", 2)).extracting(SummarySource.Passage::id).containsExactly("notice-2");
    }

    @Test
    void plainParagraphConditionsPreserveAlternativeBranchesEvenWhenAiSelectsNothing() throws Exception {
        var source = new SummarySource(Policy.builder().originalNoticeText("""
                반도체 기업으로 다음 ①, ② 요건 중 하나라도 해당하는 기업
                ① 비수도권 소재 기업
                ② 수도권 기업 중 2026년까지 대구 입주 예정인 기업

                제외 대상: 국세 체납 기업

                기업당 최대 2,000만원 지원
                """).build());
        assertThat(source.eligibilityPassages()).extracting(SummarySource.Passage::id)
                .containsExactly("notice-0", "notice-1");
        assertThat(source.select(mapper.readTree("{\"eligibility_ids\":[]}"), "eligibility_ids", 2))
                .extracting(SummarySource.Passage::text)
                .anySatisfy(text -> assertThat(text).contains("① 비수도권", "② 수도권 기업 중 2026년까지"));
        assertThat(source.select(mapper.readTree("{\"support_ids\":[\"notice-2\"]}"), "support_ids", 2))
                .extracting(SummarySource.Passage::text).containsExactly("기업당 최대 2,000만원 지원");
    }
}
