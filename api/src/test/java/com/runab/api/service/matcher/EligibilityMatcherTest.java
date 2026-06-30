package com.runab.api.service.matcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runab.api.dto.recommend.MatchResult;
import com.runab.api.entity.BusinessInfo;
import com.runab.api.entity.PolicyCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        // 합산: 6+6+4+10+8+8+8+8+4+4 = 66
        assertThat(result.getEligibilityScore()).isEqualTo(66);
        // score = round(66*0.7 + 50*0.2 + 50*0.1) = 61
        assertThat(result.getScore()).isEqualTo(61);
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
        assertThat(result.getEligibilityScore()).isEqualTo(76);
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
}
