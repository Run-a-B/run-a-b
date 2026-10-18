package com.runab.api.service.matcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runab.api.dto.recommend.MatchResult;
import com.runab.api.entity.BusinessInfo;
import com.runab.api.entity.Policy;
import com.runab.api.entity.PolicyCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EligibilityMatcher 순수 계산 로직 검증.
 * DB 없이 ObjectMapper만으로 구동.
 */
class EligibilityMatcherTest {

    private final EligibilityMatcher matcher = new EligibilityMatcher(new ObjectMapper());

    private BusinessInfo user(Boolean status, String industry, String region, Long revenue, Integer employees) {
        return BusinessInfo.builder()
                .businessStatus(status)
                .jobCategory(industry)
                .region(region)
                .annualRevenue(revenue)
                .employeeCount(employees)
                .build();
    }

    private PolicyCard card(String fullJson) {
        return PolicyCard.builder()
                .policyId("bizinfo_support_program:PBLN_TEST")
                .externalId("PBLN_TEST")
                .fullJson(fullJson)
                .build();
    }

    private Policy policy(String region, String industry, String description,
                          LocalDate startDate, LocalDate endDate) {
        return Policy.builder()
                .externalId("PBLN_POLICY")
                .title("정책 제목")
                .region(region)
                .industry(industry)
                .description(description)
                .applicationStartDate(startDate)
                .applicationEndDate(endDate)
                .build();
    }

    @Test
    @DisplayName("카드가 없으면 전국·명시 업종·접수 기간을 정책 메타데이터에서 판정한다")
    void missingCard_usesPolicyMetadata() {
        BusinessInfo user = user(true, "카페/음료", "서울특별시", null, null);
        Policy policy = policy(
                "전국",
                "카페/음료",
                "전국 카페 사업자 지원",
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(7));

        MatchResult result = matcher.match(user, null, policy);

        assertThat(result.getPass()).contains("region", "industry", "date");
        assertThat(result.getScore()).isGreaterThan(37);
        assertThat(result.isHardFail()).isFalse();
    }

    @Test
    @DisplayName("정책의 같은 시도와 보수적 본문 업종 근거를 사용할 수 있다")
    void missingCard_usesSameProvinceAndConservativeIndustryText() {
        BusinessInfo user = user(true, "카페/음료", "서울특별시", null, null);
        Policy policy = policy(
                "강남구",
                "전체",
                "베이커리와 제과점 사업자를 지원합니다.",
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(7));

        MatchResult result = matcher.match(user, null, policy);

        assertThat(result.getPass()).contains("region", "industry", "date");
    }

    @Test
    @DisplayName("명시된 정책 업종 불일치는 soft fail로 구분한다")
    void explicitPolicyIndustryMismatch_isSoftFail() {
        BusinessInfo user = user(true, "카페/음료", "서울특별시", null, null);
        Policy policy = policy(
                "전국",
                "제조업",
                null,
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(7));

        MatchResult result = matcher.match(user, null, policy);

        assertThat(result.getFail()).contains("industry");
        assertThat(result.isHardFail()).isFalse();
    }

    @Test
    @DisplayName("접수 예정은 부분 일치이고 마감된 정책은 hard fail이다")
    void policyDates_distinguishUpcomingAndClosed() {
        BusinessInfo user = user(true, "카페/음료", "서울특별시", null, null);
        MatchResult upcoming = matcher.match(user, null, policy(
                "전국", "전체", null,
                LocalDate.now().plusDays(2), LocalDate.now().plusDays(9)));
        MatchResult closed = matcher.match(user, null, policy(
                "전국", "전체", null,
                LocalDate.now().minusDays(9), LocalDate.now().minusDays(1)));

        assertThat(upcoming.getPartial()).contains("date");
        assertThat(upcoming.isHardFail()).isFalse();
        assertThat(closed.getFail()).contains("date");
        assertThat(closed.getHardFailReasons()).contains("application_period.status=closed");
        assertThat(closed.isHardFail()).isTrue();
        assertThat(upcoming.getScore()).isGreaterThan(closed.getScore());
    }

    @Test
    @DisplayName("접수중 정책은 마감 임박도를 날짜 배점에 반영해 동률을 줄인다")
    void openPolicyDates_useDeadlineUrgencyAsTieBreaker() {
        BusinessInfo user = user(true, "카페/음료", "서울특별시", null, null);
        MatchResult closesSoon = matcher.match(user, null, policy(
                "전국", "전체", null,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(3)));
        MatchResult closesLater = matcher.match(user, null, policy(
                "전국", "전체", null,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(150)));

        assertThat(closesSoon.getPass()).contains("date");
        assertThat(closesLater.getPass()).contains("date");
        assertThat(closesSoon.getScore()).isGreaterThan(closesLater.getScore());
    }

    @Test
    @DisplayName("접수 마감 10일과 20일 정책도 날짜 관련도에서 구분한다")
    void openPolicyDates_distinguishNearDeadlineBands() {
        BusinessInfo user = user(true, "카페/음료", "서울특별시", null, null);
        MatchResult tenDays = matcher.match(user, null, policy(
                "전국", "전체", null,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10)));
        MatchResult twentyDays = matcher.match(user, null, policy(
                "전국", "전체", null,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(20)));

        assertThat(tenDays.getPass()).contains("date");
        assertThat(twentyDays.getPass()).contains("date");
        assertThat(tenDays.getScore()).isGreaterThan(twentyDays.getScore());
    }

    @Test
    @DisplayName("회원가입 업종 별칭도 정책 업종과 같은 그룹으로 판정한다")
    void signupIndustryAlias_matchesPolicyIndustry() {
        BusinessInfo user = user(true, "카페/베이커리", "서울특별시", null, null);
        Policy policy = policy(
                "전국", "음식점업", null,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(7));

        MatchResult result = matcher.match(user, null, policy);

        assertThat(result.getPass()).contains("industry");
        assertThat(result.getFail()).doesNotContain("industry");
    }

    @Test
    @DisplayName("정책 메타데이터도 비어 있으면 기존 UNKNOWN 기준점을 유지한다")
    void missingCardAndMetadata_remainsUnknown() {
        BusinessInfo user = user(null, null, null, null, null);

        MatchResult result = matcher.match(user, null, policy(null, null, null, null, null));

        assertThat(result.getUnknown()).contains("region", "industry", "date");
        assertThat(result.getScore()).isEqualTo(37);
    }

    @Test
    void explicitPolicyProvinceMismatch_isHardFail() {
        MatchResult result = matcher.match(user(true, "카페/음료", "서울특별시", null, null),
                null, policy("부산광역시 강서구", "전체", null, null, null));

        assertThat(result.getFail()).contains("region");
        assertThat(result.getHardFailReasons()).contains("region_mismatch");
        assertThat(result.isHardFail()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"강서구", "미확인 지역"})
    void ambiguousOrUnresolvedRegions_remainUnknown(String region) {
        BusinessInfo user = user(true, "카페/음료", "서울특별시", null, null);
        MatchResult stored = matcher.match(user, null, policy(region, "전체", null, null, null));
        MatchResult structured = matcher.match(user, card("""
                {"eligibility":{"regions":["%s"],"region_condition_type":"specific"}}
                """.formatted(region)));

        assertThat(stored.getUnknown()).contains("region");
        assertThat(structured.getUnknown()).contains("region");
        assertThat(stored.isHardFail()).isFalse();
        assertThat(structured.isHardFail()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"전국", "제주특별자치도"})
    void ambiguousCardRegionRemainsAuthoritativeOverStoredRegion(String storedRegion) {
        MatchResult result = matcher.match(user(true, "카페/음료", "서울특별시", null, null),
                card("""
                        {"eligibility":{"regions":["강서구"],"region_condition_type":"specific"}}
                        """),
                policy(storedRegion, "전체", null, null, null));

        assertThat(result.getUnknown()).contains("region");
        assertThat(result.getPass()).doesNotContain("region");
        assertThat(result.getFail()).doesNotContain("region");
        assertThat(result.isHardFail()).isFalse();
    }

    @Test
    void explicitUnknownConditionTypesRemainUnknownAndBlockMetadataFallback() {
        MatchResult result = matcher.match(user(true, "카페/음료", "서울특별시", null, null),
                card("""
                        {"eligibility":{
                          "regions":["부산광역시"],"region_condition_type":"unknown",
                          "industries":["제조업"],"industry_condition_type":"unknown"
                        }}
                        """),
                policy("전국", "카페/음료", "카페 사업자를 지원합니다.", null, null));

        assertThat(result.getUnknown()).contains("region", "industry");
        assertThat(result.getPass()).doesNotContain("region", "industry");
        assertThat(result.getFail()).doesNotContain("region", "industry");
        assertThat(result.isHardFail()).isFalse();
    }

    @Test
    void missingConditionTypesStillUseNonBlankArrayEvidence() {
        MatchResult result = matcher.match(user(true, "카페/음료", "서울특별시", null, null),
                card("""
                        {"eligibility":{"regions":["부산광역시"],"industries":["제조업"]}}
                        """),
                policy("전국", "카페/음료", null, null, null));

        assertThat(result.getFail()).contains("region", "industry");
        assertThat(result.getHardFailReasons()).contains("region_mismatch");
    }

    @Test
    void blankArrayEntriesAreDroppedAndDoNotBlockMetadataFallback() {
        MatchResult mixed = matcher.match(user(true, "카페/음료", "서울특별시", null, null),
                card("""
                        {"eligibility":{
                          "regions":["  "," 서울특별시 "],"region_condition_type":"specific",
                          "industries":["", " 카페/음료 "],"industry_condition_type":"specific"
                        }}
                        """));
        MatchResult blankOnly = matcher.match(user(true, "카페/음료", "서울특별시", null, null),
                card("""
                        {"eligibility":{"regions":["  "],"industries":[""]}}
                        """),
                policy("전국", "카페/음료", null, null, null));

        assertThat(mixed.getPass()).contains("region", "industry");
        assertThat(blankOnly.getPass()).contains("region", "industry");
    }

    @Test
    void unresolvedNonBlankCardRegionBlocksStoredNationwideFallback() {
        MatchResult result = matcher.match(user(true, "카페/음료", "서울특별시", null, null),
                card("""
                        {"eligibility":{"regions":["수도권"]}}
                        """),
                policy("전국", "카페/음료", null, null, null));

        assertThat(result.getUnknown()).contains("region");
        assertThat(result.getPass()).doesNotContain("region");
    }

    @Test
    void explicitCardProvinceMismatch_isHardFailDespiteAmbiguousDistrict() {
        MatchResult result = matcher.match(user(true, "카페/음료", "서울특별시", null, null),
                card("""
                        {"eligibility":{"regions":["부산광역시 강서구"]}}
                        """));

        assertThat(result.getFail()).contains("region");
        assertThat(result.isHardFail()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"제조기업 생산성 향상 지원", "관광업 전용 지원", "운수업 사업자 지원"})
    void unrelatedExplicitTextIndustry_isSoftFail(String description) {
        MatchResult result = matcher.match(user(true, "카페/음료", "서울특별시", null, null),
                null, policy("전국", "전체", description, null, null));

        assertThat(result.getFail()).contains("industry");
        assertThat(result.getUnknown()).doesNotContain("industry");
        assertThat(result.isHardFail()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"제조업 제외", "제조업을 지원하지 않습니다.", "제조업 통계를 참고합니다.", "제조기업에 납품하는 사업자의 판로를 지원합니다."})
    void exclusionOrIncidentalIndustryMention_remainsUnknown(String description) {
        MatchResult result = matcher.match(user(true, "카페/음료", "서울특별시", null, null),
                null, policy("전국", "전체", description, null, null));

        assertThat(result.getUnknown()).contains("industry");
        assertThat(result.getFail()).doesNotContain("industry");
        assertThat(result.isHardFail()).isFalse();
    }

    @Test
    void technologyTopicWithoutIndustryEvidence_remainsUnknown() {
        MatchResult result = matcher.match(user(true, "IT/소프트웨어", "서울특별시", null, null),
                null, policy("전국", "전체", "소상공인 소프트웨어 도입 지원", null, null));

        assertThat(result.getUnknown()).contains("industry");
    }

    @Test
    void cardDateBoundsOverrideStoredDatesAndStaleStatus() {
        LocalDate today = LocalDate.now();
        MatchResult result = matcher.match(user(true, "카페/음료", "서울특별시", null, null),
                card("""
                        {"application_period":{"status":"closed","start":"%s","end":"%s"}}
                        """.formatted(today.minusDays(1), today.plusDays(3))),
                policy("전국", "전체", null, today.plusDays(2), today.minusDays(1)));

        assertThat(result.getPass()).contains("date");
        assertThat(result.isHardFail()).isFalse();
    }

    @Test
    void expiredCardDateOverridesStaleOpenStatus() {
        MatchResult result = matcher.match(user(true, "카페/음료", "서울특별시", null, null),
                card("""
                        {"application_period":{"status":"open","end":"%s"}}
                        """.formatted(LocalDate.now().minusDays(1))),
                policy("전국", "전체", null, null, LocalDate.now().plusDays(7)));

        assertThat(result.getFail()).contains("date");
        assertThat(result.getHardFailReasons()).contains("application_period.status=closed");
    }

    @Test
    void missingCardEndUsesStoredEndBeforeStaleOpenStatus() {
        MatchResult result = matcher.match(user(true, "카페/음료", "서울특별시", null, null),
                card("""
                        {"application_period":{"status":"open","start":"%s"}}
                        """.formatted(LocalDate.now().minusDays(10))),
                policy("전국", "전체", null, null, LocalDate.now().minusDays(1)));

        assertThat(result.getFail()).contains("date");
        assertThat(result.isHardFail()).isTrue();
    }

    @Test
    void invalidCardStartUsesStoredStartBeforeStaleOpenStatus() {
        MatchResult result = matcher.match(user(true, "카페/음료", "서울특별시", null, null),
                card("""
                        {"application_period":{"status":"open","start":"invalid","end":"%s"}}
                        """.formatted(LocalDate.now().plusDays(7))),
                policy("전국", "전체", null, LocalDate.now().plusDays(2), null));

        assertThat(result.getPartial()).contains("date");
        assertThat(result.isHardFail()).isFalse();
    }

    @Test
    void pastStartOnlyConsultsClosedStatus() {
        MatchResult result = matcher.match(user(true, "카페/음료", "서울특별시", null, null),
                card("""
                        {"application_period":{"status":"closed","start":"%s"}}
                        """.formatted(LocalDate.now().minusDays(10))));

        assertThat(result.getFail()).contains("date");
        assertThat(result.getHardFailReasons()).contains("application_period.status=closed");
        assertThat(result.isHardFail()).isTrue();
    }

    @Test
    void futureEndOnlyConsultsClosedOrUpcomingStatus() {
        LocalDate futureEnd = LocalDate.now().plusDays(10);
        MatchResult closed = matcher.match(user(true, "카페/음료", "서울특별시", null, null),
                card("""
                        {"application_period":{"status":"closed","end":"%s"}}
                        """.formatted(futureEnd)));
        MatchResult upcoming = matcher.match(user(true, "카페/음료", "서울특별시", null, null),
                card("""
                        {"application_period":{"status":"upcoming","end":"%s"}}
                        """.formatted(futureEnd)));

        assertThat(closed.getFail()).contains("date");
        assertThat(closed.isHardFail()).isTrue();
        assertThat(upcoming.getPartial()).contains("date");
        assertThat(upcoming.isHardFail()).isFalse();
    }

    @Test
    void partialNonDecisiveBoundsUseOpenStatusButDecisiveFutureStartWins() {
        MatchResult pastStartOpen = matcher.match(user(true, "카페/음료", "서울특별시", null, null),
                card("""
                        {"application_period":{"status":"open","start":"%s"}}
                        """.formatted(LocalDate.now().minusDays(1))));
        MatchResult futureStartClosed = matcher.match(user(true, "카페/음료", "서울특별시", null, null),
                card("""
                        {"application_period":{"status":"closed","start":"%s"}}
                        """.formatted(LocalDate.now().plusDays(1))));

        assertThat(pastStartOpen.getPass()).contains("date");
        assertThat(futureStartClosed.getPartial()).contains("date");
        assertThat(futureStartClosed.isHardFail()).isFalse();
    }

    @Test
    void effectiveCardDatesRetainDeadlineUrgencyScoring() {
        BusinessInfo user = user(true, "카페/음료", "서울특별시", null, null);
        MatchResult soon = matcher.match(user, card("""
                {"application_period":{"status":"open","end":"%s"}}
                """.formatted(LocalDate.now().plusDays(3))));
        MatchResult later = matcher.match(user, card("""
                {"application_period":{"status":"open","end":"%s"}}
                """.formatted(LocalDate.now().plusDays(150))));

        assertThat(soon.getScore()).isGreaterThan(later.getScore());
    }

    @Test
    @DisplayName("카드의 명시 조건은 더 약한 정책 메타데이터보다 우선한다")
    void cardEvidence_remainsAuthoritative() {
        String json = """
            {
              "application_period": { "status": "open" },
              "eligibility": {
                "regions": ["부산광역시"], "region_condition_type": "specific",
                "industries": ["제조업"], "industry_condition_type": "specific"
              }
            }
            """;
        BusinessInfo user = user(true, "카페/음료", "서울특별시", null, null);
        Policy policy = policy("전국", "카페/음료", null, null, LocalDate.now().plusDays(7));

        MatchResult result = matcher.match(user, card(json), policy);

        assertThat(result.getFail()).contains("region", "industry");
        assertThat(result.getPass()).contains("date");
        assertThat(result.isHardFail()).isTrue();
    }

    @Test
    @DisplayName("유효한 날짜가 없을 때는 카드의 접수 예정 상태를 사용한다")
    void upcomingCardStatus_isUsedWhenDatesAreAbsent() {
        BusinessInfo user = user(true, "카페/음료", "서울특별시", null, null);
        Policy openMetadata = policy(
                "전국", "카페/음료", null,
                null, null);
        PolicyCard scheduledColumnCard = PolicyCard.builder()
                .policyId("bizinfo_support_program:PBLN_SCHEDULED")
                .externalId("PBLN_SCHEDULED")
                .applicationStatus("scheduled")
                .build();

        MatchResult upcomingStatus = matcher.match(user, card("""
                {"application_period":{"status":"upcoming"},"eligibility":{}}
                """), openMetadata);
        MatchResult scheduledColumn = matcher.match(user, scheduledColumnCard, openMetadata);
        MatchResult futureStructuredStart = matcher.match(user, card("""
                {"application_period":{"start":"%s"},"eligibility":{}}
                """.formatted(LocalDate.now().plusDays(1))), openMetadata);

        assertThat(upcomingStatus.getPartial()).contains("date");
        assertThat(scheduledColumn.getPartial()).contains("date");
        assertThat(futureStructuredStart.getPartial()).contains("date");
        assertThat(upcomingStatus.getPass()).doesNotContain("date");
        assertThat(scheduledColumn.getPass()).doesNotContain("date");
        assertThat(futureStructuredStart.getPass()).doesNotContain("date");
    }

    @Test
    @DisplayName("자격요건 데이터가 전혀 없어도 예외 없이 UNKNOWN/NOT_REQUIRED로 동작한다")
    void noEligibilityData_allUnknown() {
        BusinessInfo user = user(null, null, null, null, null);
        MatchResult result = matcher.match(user, card(null)); // fullJson null

        assertThat(result).isNotNull();
        assertThat(result.isHardFail()).isFalse();
        // region/industry/date는 판단 불가 → UNKNOWN
        assertThat(result.getUnknown()).contains("region", "industry", "date");
        // 조건 자체가 없는 항목 → NOT_REQUIRED
        assertThat(result.getNotRequired()).contains("business_stage", "revenue", "employees", "age");
        // [2026.07.01 정규화 반영] NOT_REQUIRED 항목은 점수 계산에서 제외. 판단 대상은 region/industry/date뿐.
        //   판단 가능 배점 = 20+20+10 = 50, 획득 = region 20*0.3 + industry 20*0.3 + date 10*0.4 = 6+6+4 = 16
        //   eligibilityScore = round(16/50*100) = 32  (이전엔 NOT_REQUIRED 만점 포함이라 66이었음)
        assertThat(result.getEligibilityScore()).isEqualTo(32);
        // score = round(32*0.7 + 50*0.2 + 50*0.1) = 37  (이전 61 → 베이스라인 인플레 제거로 하락)
        assertThat(result.getScore()).isEqualTo(37);
    }

    @Test
    @DisplayName("자격요건이 모두 충족되면 region/industry/date/stage/revenue/employees가 PASS이고 만점에 가깝다")
    void fullMatch_highScore() {
        String json = """
            {
              "application_period": { "status": "open" },
              "eligibility": {
                "regions": ["서울특별시"], "region_condition_type": "specific",
                "industries": ["음식점업"], "industry_condition_type": "specific",
                "business_stages": ["운영중"],
                "business_types": [], "owner_types": [],
                "max_revenue": 1000000000, "max_employees": 50,
                "required_flags": []
              },
              "attachment_analysis": { "status": "documents_found" }
            }
            """;
        BusinessInfo user = user(true, "음식점업", "서울특별시", 500000000L, 10);
        MatchResult result = matcher.match(user, card(json));

        assertThat(result.isHardFail()).isFalse();
        assertThat(result.getPass()).contains("region", "industry", "date", "business_stage", "revenue", "employees");
        assertThat(result.getEligibilityScore()).isEqualTo(100);
        assertThat(result.getDocumentScore()).isEqualTo(90);
        // score = round(100*0.7 + 50*0.2 + 90*0.1) = 89
        assertThat(result.getScore()).isEqualTo(89);
    }

    @Test
    @DisplayName("closed 정책은 date=FAIL + hardFail=true (규칙1·2)")
    void closedPolicy_hardFail() {
        String json = """
            {
              "application_period": { "status": "closed" },
              "eligibility": {
                "regions": ["서울특별시"], "region_condition_type": "specific",
                "industries": [], "industry_condition_type": "unknown"
              }
            }
            """;
        BusinessInfo user = user(true, "음식점업", "서울특별시", null, null);
        MatchResult result = matcher.match(user, card(json));

        assertThat(result.isHardFail()).isTrue();
        assertThat(result.getHardFailReasons()).contains("application_period.status=closed");
        assertThat(result.getFail()).contains("date");
        assertThat(result.getPass()).contains("region"); // 지역은 일치
        // industries=[]+unknown → PASS 금지, UNKNOWN (규칙4)
        assertThat(result.getUnknown()).contains("industry");
        // [2026.07.01 정규화 반영] 판단 대상 = region/industry/date (배점 50).
        //   획득 = region 20*1.0(PASS) + industry 20*0.3(UNKNOWN) + date 10*0.0(FAIL) = 26
        //   eligibilityScore = round(26/50*100) = 52  (이전 76 → NOT_REQUIRED 만점 제외로 하락)
        assertThat(result.getEligibilityScore()).isEqualTo(52);
    }

    @Test
    @DisplayName("지역이 명확히 불일치하면 region=FAIL + hardFail=true")
    void regionMismatch_hardFail() {
        String json = """
            {
              "application_period": { "status": "open" },
              "eligibility": {
                "regions": ["부산광역시"], "region_condition_type": "specific",
                "industries": [], "industry_condition_type": "unknown"
              }
            }
            """;
        BusinessInfo user = user(true, "음식점업", "서울특별시", null, null);
        MatchResult result = matcher.match(user, card(json));

        assertThat(result.isHardFail()).isTrue();
        assertThat(result.getHardFailReasons()).contains("region_mismatch");
        assertThat(result.getFail()).contains("region");
        assertThat(result.getPass()).contains("date"); // open이므로 날짜는 통과
    }

    @Test
    @DisplayName("전국(nationwide) 정책은 사용자 지역과 무관하게 region=PASS")
    void nationwide_alwaysPass() {
        String json = """
            { "eligibility": { "regions": ["전국"], "region_condition_type": "nationwide" } }
            """;
        BusinessInfo user = user(true, "음식점업", "제주특별자치도", null, null);
        MatchResult result = matcher.match(user, card(json));

        assertThat(result.getPass()).contains("region");
        assertThat(result.isHardFail()).isFalse();
    }

    // ===== 업종 동의어 매핑 + 전업종 예외처리 (Task F) =====
    // extractor 이름표와 앱 드롭다운 이름표가 달라도 같은 업종이면 매칭돼야 함.
    // region은 nationwide로 고정해 industry 판정만 격리 검증.

    private String industryCard(String industries, String industryConditionType) {
        return """
            {
              "application_period": { "status": "open" },
              "eligibility": {
                "regions": ["전국"], "region_condition_type": "nationwide",
                "industries": %s, "industry_condition_type": "%s"
              }
            }
            """.formatted(industries, industryConditionType);
    }

    @Test
    @DisplayName("동의어: industries=[미용업] + 사용자=뷰티/미용 → industry PASS")
    void industrySynonym_beauty_pass() {
        BusinessInfo user = user(true, "뷰티/미용", "서울특별시", null, null);
        MatchResult result = matcher.match(user, card(industryCard("[\"미용업\"]", "specific")));
        assertThat(result.getPass()).contains("industry");
        assertThat(result.getFail()).doesNotContain("industry");
    }

    @Test
    @DisplayName("동의어: industries=[정보통신업] + 사용자=IT/소프트웨어 → industry PASS")
    void industrySynonym_it_pass() {
        BusinessInfo user = user(true, "IT/소프트웨어", "서울특별시", null, null);
        MatchResult result = matcher.match(user, card(industryCard("[\"정보통신업\"]", "specific")));
        assertThat(result.getPass()).contains("industry");
    }

    @Test
    @DisplayName("동의어: industries=[음식점업] + 사용자=카페/음료 → industry PASS (extractor가 카페를 음식점업에 뭉뚱그림)")
    void industrySynonym_cafe_pass() {
        BusinessInfo user = user(true, "카페/음료", "서울특별시", null, null);
        MatchResult result = matcher.match(user, card(industryCard("[\"음식점업\"]", "specific")));
        assertThat(result.getPass()).contains("industry");
    }

    @Test
    @DisplayName("전업종(all): 사용자 업종이 뭐든 industry PASS")
    void industryAll_alwaysPass() {
        BusinessInfo user = user(true, "아무거나", "서울특별시", null, null);
        MatchResult result = matcher.match(user, card(industryCard("[\"전업종\"]", "all")));
        assertThat(result.getPass()).contains("industry");
        assertThat(result.isHardFail()).isFalse();
    }

    @Test
    @DisplayName("매핑 없는 업종: industries=[운수업] + 사용자=뷰티/미용 → industry FAIL (억지 매칭 금지)")
    void industryUnmapped_fail() {
        BusinessInfo user = user(true, "뷰티/미용", "서울특별시", null, null);
        MatchResult result = matcher.match(user, card(industryCard("[\"운수업\"]", "specific")));
        assertThat(result.getFail()).contains("industry");
        assertThat(result.getPass()).doesNotContain("industry");
    }

    // ===== 목록 업종 필터 판정 matchesIndustryFilter (Task G) =====
    // 드롭다운 선택 업종 기준. 동의어/전업종은 통과, 미매핑/카드없음/빈 industries는 제외.

    @Test
    @DisplayName("업종필터: 동의어(미용업↔뷰티/미용)/전업종은 통과, 불일치·카드없음·빈배열은 제외")
    void matchesIndustryFilter_contract() {
        // 동의어 매칭
        assertThat(matcher.matchesIndustryFilter(card(industryCard("[\"미용업\"]", "specific")), "뷰티/미용")).isTrue();
        assertThat(matcher.matchesIndustryFilter(card(industryCard("[\"정보통신업\"]", "specific")), "IT/소프트웨어")).isTrue();
        // 전업종(all)은 아무 업종 선택에도 통과
        assertThat(matcher.matchesIndustryFilter(card(industryCard("[\"전업종\"]", "all")), "제조업")).isTrue();
        // 매핑 없는/다른 업종은 제외
        assertThat(matcher.matchesIndustryFilter(card(industryCard("[\"제조업\"]", "specific")), "뷰티/미용")).isFalse();
        // 카드 없음(null) → 제외
        assertThat(matcher.matchesIndustryFilter(null, "뷰티/미용")).isFalse();
        // industries 비어있음 → 판단 불가 → 제외
        assertThat(matcher.matchesIndustryFilter(card(industryCard("[]", "unknown")), "뷰티/미용")).isFalse();
    }
}
