package com.runab.api.service.matcher;

import com.runab.api.entity.Policy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyIndustryFallbackMatcherTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "사업자등록증(도매 및 소매업, 숙박 및 음식점업 등) 소지자",
            "서울시 내 외식업체에 맞춤형 컨설팅을 제공합니다",
            "지원대상: 서울시 내 일반ㆍ휴게음식점",
            "지원한도: 중소기업 2억 원, 소상공인 7천만 원, 음식점업 5천만 원 이내"
    })
    void liveCafeEligibilityAndBenefitPhrasesAreAffirmativeTargets(String description) {
        Policy policy = Policy.builder().externalId("PBLN_LIVE_CAFE").industry("전체")
                .title("서울시 소상공인 지원").targetGroup("중소기업")
                .description(description).build();

        assertThat(PolicyIndustryFallbackMatcher.evaluate(policy, "카페/베이커리"))
                .isEqualTo(MatchStatus.PASS);
    }

    @Test
    void selectedIndustryExclusionMustBeBoundToTheIndustryPhrase() {
        Policy unrelatedExclusion = Policy.builder().externalId("PBLN_UNRELATED_EXCLUSION").industry("전체")
                .title("소상공인 경영 지원").targetGroup("카페 사업자 지원(휴·폐업자 제외)").build();

        assertThat(PolicyIndustryFallbackMatcher.evaluate(unrelatedExclusion, "카페/음료"))
                .isEqualTo(MatchStatus.PASS);
    }

    @Test
    void naturalOperatingPhraseEstablishesSelectedIndustryTarget() {
        Policy naturalOperatingTarget = Policy.builder().externalId("PBLN_OPERATING_TARGET").industry("전체")
                .title("소상공인 경영 지원").targetGroup("카페를 운영하는 소상공인").build();

        assertThat(PolicyIndustryFallbackMatcher.evaluate(naturalOperatingTarget, "카페/음료"))
                .isEqualTo(MatchStatus.PASS);
    }

    @Test
    void selectedIndustryRequiresAffirmativeTargetingAndHonorsExplicitExclusion() {
        Policy excluded = Policy.builder().externalId("PBLN_EXCLUDED").industry("전체")
                .title("소상공인 경영 지원").targetGroup("카페 제외")
                .description("제조업 사업자를 지원합니다.").build();
        Policy incidental = Policy.builder().externalId("PBLN_INCIDENTAL").industry("전체")
                .title("소상공인 경영 지원").targetGroup("소상공인")
                .description("정책 설계 시 카페 매출 통계를 참고합니다.").build();
        Policy incidentalSupportCase = Policy.builder().externalId("PBLN_INCIDENTAL_CASE").industry("전체")
                .title("소상공인 경영 지원").targetGroup("소상공인")
                .description("우수한 카페 사업자 지원 사례를 참고합니다.").build();
        Policy affirmative = Policy.builder().externalId("PBLN_AFFIRMATIVE").industry("전체")
                .title("카페 사업자 시설 개선 지원").targetGroup("소상공인").build();

        assertThat(PolicyIndustryFallbackMatcher.evaluate(excluded, "카페/음료"))
                .isEqualTo(MatchStatus.FAIL);
        assertThat(PolicyIndustryFallbackMatcher.evaluate(incidental, "카페/음료"))
                .isEqualTo(MatchStatus.UNKNOWN);
        assertThat(PolicyIndustryFallbackMatcher.evaluate(incidentalSupportCase, "카페/음료"))
                .isEqualTo(MatchStatus.UNKNOWN);
        assertThat(PolicyIndustryFallbackMatcher.evaluate(affirmative, "카페/음료"))
                .isEqualTo(MatchStatus.PASS);
    }

    @ParameterizedTest(name = "{0} - {1}")
    @CsvSource(delimiter = '|', textBlock = """
            음식점업       | 외식업체 시설 개선 지원
            카페/음료      | 커피전문점 경영 환경 개선
            음식점         | 외식업체 시설 개선 지원
            카페/베이커리  | 커피전문점 경영 환경 개선
            의류/패션      | 패션기업 해외 진출 지원
            뷰티/미용      | 미용업 사업자 시설 개선
            교육/학원      | 학원업 소상공인 운영 지원
            교육/학원      | 학원 운영자 경영 지원
            교육           | 학원 운영자 경영 지원
            IT/소프트웨어  | 소프트웨어기업 기술개발 지원
            IT/소프트웨어  | 소프트웨어 개발사 해외 진출 지원
            제조업         | 제조기업 생산성 향상 지원
            제조           | 제조기업 생산성 향상 지원
            도소매업       | 도소매업 사업자 판로 지원
            도소매         | 도소매업 사업자 판로 지원
            """)
    void matchesExplicitIndustryTerms(String industry, String title) {
        Policy policy = Policy.builder()
                .externalId("PBLN_TEST")
                .title(title)
                .industry("전체")
                .build();

        assertThat(PolicyIndustryFallbackMatcher.matches(policy, industry)).isTrue();
    }

    @ParameterizedTest(name = "주제 문구만으로는 {0} 업종으로 분류하지 않는다 - {1}")
    @CsvSource(delimiter = '|', textBlock = """
            IT/소프트웨어 | 중소기업 소프트웨어 도입 지원
            IT/소프트웨어 | 소상공인 ICT 기술 활용 지원
            IT/소프트웨어 | 정보통신 기술을 활용한 판로 개척
            """)
    void doesNotMatchTechnologyTopicWithoutIndustryContext(String industry, String title) {
        Policy policy = Policy.builder()
                .externalId("PBLN_TEST")
                .title(title)
                .targetGroup("업종 제한 없는 중소기업")
                .industry("전체")
                .build();

        assertThat(PolicyIndustryFallbackMatcher.matches(policy, industry)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            카페/음료 | 제조기업 생산성 향상 지원
            카페/음료 | 관광업 전용 지원
            카페/음료 | 업종 제한 없는 소상공인 지원
            """)
    void unrelatedOrAbsentIndustryEvidenceDoesNotPassFilter(String industry, String title) {
        Policy policy = Policy.builder().externalId("PBLN_TEST").title(title).industry("전체").build();

        assertThat(PolicyIndustryFallbackMatcher.matches(policy, industry)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            소상공인 경영 지원 | 소상공인 | 제조업 제외
            소상공인 경영 지원 | 제조업 제외 | 운영 비용을 지원합니다.
            소상공인 경영 지원 | 소상공인 | 지원대상: 제조업 제외
            소상공인 경영 지원 | 소상공인 | 제조업을 지원하지 않습니다.
            소상공인 경영 지원 | 제조업 대상 아님 | 운영 비용을 지원합니다.
            소상공인 경영 지원 | 소상공인 | 제조업 통계를 참고합니다.
            소상공인 경영 지원 | 소상공인 | 제조기업에 납품하는 사업자의 판로를 지원합니다.
            제조업 사례를 참고한 경영 지원 | 소상공인 | 운영 비용을 지원합니다.
            """)
    void exclusionOrIncidentalMentionDoesNotEstablishTargetIndustry(
            String title, String targetGroup, String description) {
        Policy policy = Policy.builder().externalId("PBLN_TEST").industry("전체")
                .title(title).targetGroup(targetGroup).description(description).build();

        assertThat(PolicyIndustryFallbackMatcher.evaluate(policy, "카페/음료"))
                .isEqualTo(MatchStatus.UNKNOWN);
        assertThat(PolicyIndustryFallbackMatcher.matches(policy, "카페/음료")).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            제조기업 생산성 향상 지원 | 소상공인 | 경영 비용을 지원합니다.
            소상공인 경영 지원 | 제조업 사업자 | 경영 비용을 지원합니다.
            소상공인 경영 지원 | 소상공인 | 지원대상: 제조업 사업자
            소상공인 경영 지원 | 소상공인 | 관광업 전용 지원
            소상공인 경영 지원 | 소상공인 | 운수업 사업자를 지원합니다.
            """)
    void affirmativeUnrelatedTargetIndustryRemainsFail(String title, String targetGroup, String description) {
        Policy policy = Policy.builder().externalId("PBLN_TEST").industry("전체")
                .title(title).targetGroup(targetGroup).description(description).build();

        assertThat(PolicyIndustryFallbackMatcher.evaluate(policy, "카페/음료"))
                .isEqualTo(MatchStatus.FAIL);
    }
}
