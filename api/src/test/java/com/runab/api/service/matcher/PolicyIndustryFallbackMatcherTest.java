package com.runab.api.service.matcher;

import com.runab.api.entity.Policy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyIndustryFallbackMatcherTest {

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
}
