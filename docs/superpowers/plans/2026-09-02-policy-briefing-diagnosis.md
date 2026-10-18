# Policy Briefing and 60-Second Diagnosis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve Run-a-B's current visual concept while adding factual policy briefing, a deterministic guest-accessible 60-second diagnosis with evidence, and truthful checklist states.

**Architecture:** A new stateless Spring service evaluates six policy criteria from `PolicyCard` first and conservative `Policy` metadata fallbacks second; it never calls AI or persists diagnosis data. React receives a stable DTO, uses pure presentation/validation modules for testable behavior, and renders a dedicated route using the existing policy-detail card language. Checklist output becomes API-first and separates verified requirements from general preparation suggestions.

**Tech Stack:** Java 21, Spring Boot 3.5, JPA repositories, Jackson, JUnit 5/AssertJ/Mockito, React 19, TypeScript 6, Vite 8, Tailwind CSS v4, Node 22 test runner.

**Spec:** `docs/superpowers/specs/2026-09-02-policy-briefing-diagnosis-design.md`

## Global Constraints

- Reuse `primary-50…700`, the gray scale, existing semantic colors, Pretendard, `rounded-2xl` cards, `rounded-xl` inner controls, `pt-15`, and `px-40`.
- Do not introduce a new palette, chat UI, glass effect, neon effect, or separate visual language.
- Never show “합격률”, “확정 자격”, or claim that AI made an eligibility decision.
- Use “조건 일치도”, “1차 진단”, and “원문 최종 확인”.
- Missing source facts stay `UNKNOWN`; never turn missing extraction into `NOT_REQUIRED` or a pass.
- Diagnosis is stateless, publicly callable, and must not invoke OpenAI, the AI gateway, external URLs, or report persistence.
- Existing uncommitted setup, authentication, policy filtering, and user changes must be preserved.
- Production behavior is written only after its covering test has failed for the expected missing behavior.

---

## File Structure

### Backend

- Create `api/src/main/java/com/runab/api/dto/policy/PolicyDiagnosisRequest.java`: validated five-field guest input contract.
- Create `api/src/main/java/com/runab/api/dto/policy/PolicyDiagnosisResponse.java`: stable policy/verdict/evidence/data-quality response.
- Create `api/src/main/java/com/runab/api/service/diagnosis/DiagnosisInputNormalizer.java`: option validation and numeric band comparison.
- Create `api/src/main/java/com/runab/api/service/diagnosis/PolicyDiagnosisService.java`: source-priority evaluation, score, confidence, and wording.
- Modify `api/src/main/java/com/runab/api/controller/PolicyController.java`: public POST diagnosis endpoint.
- Modify `api/src/main/java/com/runab/api/config/SecurityConfig.java`: permit only the stateless diagnosis POST.
- Modify `api/src/main/java/com/runab/api/service/checklist/PolicyChecklistService.java`: truthful period and recommendation semantics.
- Test with focused unit tests in `api/src/test/java/com/runab/api/service/diagnosis`, `api/src/test/java/com/runab/api/service/checklist`, and `api/src/test/java/com/runab/api/dto/policy`.

### Frontend

- Create `apps/run-a-b-fe/src/features/policy/presentation.ts`: policy brief and information-quality view models.
- Create `apps/run-a-b-fe/src/features/policy/checklist.ts`: API-first checklist loader.
- Create `apps/run-a-b-fe/src/features/diagnosis/model.ts`: form options, validation, request mapping, and status presentation.
- Create `apps/run-a-b-fe/src/components/policy/PolicyBriefing.tsx`: three quick-understanding cards and quality notice.
- Create `apps/run-a-b-fe/src/components/policy/DiagnosisEntryCard.tsx`: visual-system-aligned launch card.
- Create `apps/run-a-b-fe/src/pages/PolicyDiagnosis.tsx`: input, audit animation, error, and result flow.
- Modify policy detail, policy list, checklist, routes, and package test script only where needed to connect those units.
- Add Node tests for the pure modules; use browser verification for component interaction and visual layout.

---

### Task 1: Normalize and Compare Diagnosis Inputs

**Files:**
- Create: `api/src/test/java/com/runab/api/service/diagnosis/DiagnosisInputNormalizerTest.java`
- Create: `api/src/main/java/com/runab/api/service/diagnosis/DiagnosisInputNormalizer.java`

**Interfaces:**
- Consumes: Korean option labels already used by signup and my-page forms.
- Produces: `normalize(PolicyDiagnosisRequest)` returning `NormalizedBusiness`; `NumericBand.compareToMaximum(Long)` returning `MatchStatus`.

- [ ] **Step 1: Write the failing band-comparison tests**

```java
@Test
void revenueBandCanPassPartiallyOverlapOrFailAMaximum() {
    var normalizer = new DiagnosisInputNormalizer();
    assertThat(normalizer.revenueBand("1,000만원~5,000만원").compareToMaximum(50_000_000L))
            .isEqualTo(MatchStatus.PASS);
    assertThat(normalizer.revenueBand("5,000만원~1억원").compareToMaximum(75_000_000L))
            .isEqualTo(MatchStatus.PARTIAL);
    assertThat(normalizer.revenueBand("1억원~3억원").compareToMaximum(90_000_000L))
            .isEqualTo(MatchStatus.FAIL);
}

@Test
void openEndedEmployeeBandPartiallyOverlapsOrFails() {
    var normalizer = new DiagnosisInputNormalizer();
    assertThat(normalizer.employeeBand("30명 이상").compareToMaximum(50L))
            .isEqualTo(MatchStatus.PARTIAL);
    assertThat(normalizer.employeeBand("30명 이상").compareToMaximum(20L))
            .isEqualTo(MatchStatus.FAIL);
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `cd api && GRADLE_USER_HOME="$PWD/.gradle" ./gradlew test --tests '*DiagnosisInputNormalizerTest'`

Expected: compilation failure because `DiagnosisInputNormalizer` does not exist.

- [ ] **Step 3: Implement exact option maps and conservative comparison**

```java
public record NumericBand(long minInclusive, Long maxInclusive, String label) {
    public MatchStatus compareToMaximum(Long policyMaximum) {
        if (policyMaximum == null) return MatchStatus.UNKNOWN;
        if (minInclusive > policyMaximum) return MatchStatus.FAIL;
        if (maxInclusive == null || maxInclusive > policyMaximum) return MatchStatus.PARTIAL;
        return MatchStatus.PASS;
    }
}

public record NormalizedBusiness(
        boolean operating,
        String industry,
        String region,
        NumericBand annualRevenue,
        NumericBand employeeCount) {}
```

Revenue ranges use `0..9_999_999`, `10_000_000..49_999_999`, `50_000_000..99_999_999`, `100_000_000..299_999_999`, `300_000_000..499_999_999`, and `500_000_000..open`. Employee ranges use `0..0`, `1..4`, `5..9`, `10..29`, and `30..open`.

`normalize` must throw the existing `BUSINESS_FIELD_REQUIRED`, `BUSINESS_FIELD_NOT_ALLOWED`, `INVALID_REGION`, `INVALID_JOB_CATEGORY`, or `VALIDATION_ERROR` business errors for the matching invalid input. Its allowed regions are the existing 17 provinces and its industries are the existing eight diagnosis choices.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `cd api && GRADLE_USER_HOME="$PWD/.gradle" ./gradlew test --tests '*DiagnosisInputNormalizerTest'`

Expected: all normalizer tests pass with zero failures.

- [ ] **Step 5: Commit the task**

```bash
git add api/src/main/java/com/runab/api/service/diagnosis/DiagnosisInputNormalizer.java api/src/test/java/com/runab/api/service/diagnosis/DiagnosisInputNormalizerTest.java
git commit -m "feat: normalize policy diagnosis inputs"
```

---

### Task 2: Build the Source-Grounded Diagnosis API

**Files:**
- Create: `api/src/main/java/com/runab/api/dto/policy/PolicyDiagnosisRequest.java`
- Create: `api/src/main/java/com/runab/api/dto/policy/PolicyDiagnosisResponse.java`
- Create: `api/src/main/java/com/runab/api/service/diagnosis/PolicyDiagnosisService.java`
- Create: `api/src/test/java/com/runab/api/service/diagnosis/PolicyDiagnosisServiceTest.java`
- Create: `api/src/test/java/com/runab/api/dto/policy/PolicyDetailResponseTest.java`
- Modify: `api/src/main/java/com/runab/api/controller/PolicyController.java`
- Modify: `api/src/main/java/com/runab/api/config/SecurityConfig.java`
- Modify: `api/src/main/java/com/runab/api/dto/policy/PolicyDetailResponse.java`

**Interfaces:**
- Consumes: `DiagnosisInputNormalizer`, `PolicyRepository`, `PolicyCardRepository`, `PolicyCard.toPolicyId`, `RegionHierarchy`, `IndustryHierarchy`, and `PolicyIndustryFallbackMatcher`.
- Produces: `PolicyDiagnosisService.diagnose(Long policyId, PolicyDiagnosisRequest request)` and `POST /api/v1/policies/{id}/diagnosis`.

- [ ] **Step 1: Write failing service tests for verdict and evidence behavior**

Use repository mocks only for database boundaries; instantiate the real `ObjectMapper` and `DiagnosisInputNormalizer`.

```java
@Test
void missingCardUsesMetadataButKeepsUnsupportedCriteriaUnknown() {
    given(policyRepository.findById(42L)).willReturn(Optional.of(policy(
            "전국", "전체", LocalDate.now().minusDays(1), LocalDate.now().plusDays(10))));
    given(policyCardRepository.findByPolicyId(anyString())).willReturn(Optional.empty());

    PolicyDiagnosisResponse result = service.diagnose(42L, operatingCafeInSeoul());

    assertThat(result.getVerdict()).isEqualTo("NEEDS_CONFIRMATION");
    assertThat(result.getConfidence()).isEqualTo("LOW");
    assertThat(result.getEvidence()).extracting(PolicyDiagnosisResponse.Evidence::getKey)
            .containsExactly("region", "industry", "date", "business_stage", "revenue", "employees");
    assertThat(evidence(result, "region").getStatus()).isEqualTo(MatchStatus.PASS);
    assertThat(evidence(result, "revenue").getStatus()).isEqualTo(MatchStatus.UNKNOWN);
    assertThat(result.getDataQuality().isPolicyCardAvailable()).isFalse();
}

@Test
void closedPeriodOrSpecificRegionMismatchIsNotEligible() {
    Policy expired = policy("전국", "전체", LocalDate.now().minusDays(20), LocalDate.now().minusDays(1));
    given(policyRepository.findById(42L)).willReturn(Optional.of(expired));
    given(policyCardRepository.findByPolicyId(anyString())).willReturn(Optional.empty());

    PolicyDiagnosisResponse expiredResult = service.diagnose(42L, operatingCafeInSeoul());

    assertThat(expiredResult.getVerdict()).isEqualTo("NOT_ELIGIBLE");
    assertThat(expiredResult.isHardFail()).isTrue();
    assertThat(evidence(expiredResult, "date").getStatus()).isEqualTo(MatchStatus.FAIL);

    Policy seoulOnly = policy("서울특별시", "전체", LocalDate.now().minusDays(1), LocalDate.now().plusDays(10));
    given(policyRepository.findById(42L)).willReturn(Optional.of(seoulOnly));

    PolicyDiagnosisRequest busan = PolicyDiagnosisRequest.builder()
            .businessStatus(true)
            .industry("카페/음료")
            .region("부산광역시")
            .annualRevenueBand("5,000만원~1억원")
            .employeeCountBand("1~4명")
            .build();
    PolicyDiagnosisResponse regionResult = service.diagnose(42L, busan);

    assertThat(regionResult.getVerdict()).isEqualTo("NOT_ELIGIBLE");
    assertThat(regionResult.isHardFail()).isTrue();
    assertThat(evidence(regionResult, "region").getStatus()).isEqualTo(MatchStatus.FAIL);
}

@Test
void extractedMaximumProducesPartialRangeEvidence() {
    Policy policy = policy("전국", "전체", LocalDate.now().minusDays(1), LocalDate.now().plusDays(10));
    PolicyCard card = PolicyCard.builder()
            .policyId(PolicyCard.toPolicyId("PBLN_TEST"))
            .externalId("PBLN_TEST")
            .applicationStatus("open")
            .extractionStatus("extracted")
            .fullJson("""
                    {
                      "application_period": {"status": "open"},
                      "eligibility": {
                        "regions": ["전국"], "region_condition_type": "nationwide",
                        "industries": ["전업종"], "industry_condition_type": "all",
                        "business_stages": ["운영중"],
                        "max_revenue": 75000000,
                        "max_employees": 4
                      }
                    }
                    """)
            .build();
    given(policyRepository.findById(42L)).willReturn(Optional.of(policy));
    given(policyCardRepository.findByPolicyId(PolicyCard.toPolicyId("PBLN_TEST")))
            .willReturn(Optional.of(card));

    PolicyDiagnosisResponse result = service.diagnose(42L, operatingCafeInSeoul());

    PolicyDiagnosisResponse.Evidence revenue = evidence(result, "revenue");
    assertThat(revenue.getStatus()).isEqualTo(MatchStatus.PARTIAL);
    assertThat(revenue.getPolicyValue()).isEqualTo("7,500만원 이하");
    assertThat(revenue.getSource()).isEqualTo("구조화된 공고 조건");
}
```

Define the literal helpers in the test as follows so later assertions do not depend on production formatting logic:

```java
private Policy policy(String region, String industry, LocalDate start, LocalDate end) {
    Policy policy = Policy.builder()
            .externalId("PBLN_TEST")
            .title("테스트 정책")
            .category("경영")
            .region(region)
            .industry(industry)
            .description("카페 사업자를 지원하는 테스트 설명")
            .agency("테스트 기관")
            .applicationStartDate(start)
            .applicationEndDate(end)
            .targetGroup("소상공인")
            .detailUrl("https://www.bizinfo.go.kr/test")
            .sourceSystem("bizinfo")
            .build();
    ReflectionTestUtils.setField(policy, "id", 42L);
    return policy;
}

private PolicyDiagnosisRequest operatingCafeInSeoul() {
    return PolicyDiagnosisRequest.builder()
            .businessStatus(true)
            .industry("카페/음료")
            .region("서울특별시")
            .annualRevenueBand("5,000만원~1억원")
            .employeeCountBand("1~4명")
            .build();
}

private PolicyDiagnosisResponse.Evidence evidence(PolicyDiagnosisResponse result, String key) {
    return result.getEvidence().stream()
            .filter(item -> item.getKey().equals(key))
            .findFirst()
            .orElseThrow();
}
```

- [ ] **Step 2: Run focused test and confirm RED**

Run: `cd api && GRADLE_USER_HOME="$PWD/.gradle" ./gradlew test --tests '*PolicyDiagnosisServiceTest'`

Expected: compilation failure because the diagnosis DTOs and service do not exist.

- [ ] **Step 3: Implement request and response DTOs**

`PolicyDiagnosisRequest` fields:

```java
@NotNull private Boolean businessStatus;
@NotBlank private String industry;
@NotBlank private String region;
private String annualRevenueBand;
private String employeeCountBand;
```

`PolicyDiagnosisResponse` exposes nested `PolicyInfo`, `Evidence`, and `DataQuality` classes and these top-level properties:

```java
private PolicyInfo policy;
private String verdict;
private String verdictLabel;
private Integer score;
private String confidence;
private String summary;
private List<Evidence> evidence;
private List<String> unknownConditions;
private List<String> hardFailReasons;
private boolean hardFail;
private DataQuality dataQuality;
private String disclaimer;
```

`Evidence.status` is `MatchStatus`; its remaining fields are `key`, `label`, `userValue`, `policyValue`, `reason`, and `source`.

`PolicyInfo` fields are `id`, `title`, `category`, `agency`, `applicationPeriod`, and `detailUrl`. `DataQuality` fields are `policyCardAvailable`, `extractionStatus`, `sourceSystem`, `verifiedFactCount`, and `totalFactCount`.

Add `sourceSystem` to `PolicyDetailResponse` and map it directly from `Policy.sourceSystem`. Cover this with `PolicyDetailResponseTest`:

```java
@Test
void exposesTheStoredSourceSystem() {
    Policy policy = Policy.builder()
            .externalId("PBLN_TEST")
            .title("테스트 정책")
            .sourceSystem("bizinfo")
            .build();
    assertThat(PolicyDetailResponse.from(policy).getSourceSystem()).isEqualTo("bizinfo");
}
```

- [ ] **Step 4: Implement six deterministic checks**

Evaluation order is `region`, `industry`, `date`, `business_stage`, `revenue`, `employees`.

- Card data takes priority only when JSON parses and extraction status is `extracted` or `partially_extracted`.
- Region falls back to `Policy.region`; `전국` passes and a specific mismatch is a hard fail.
- Industry falls back to a specific `Policy.industry`; text fallback may create PASS only, never FAIL.
- Date falls back to entity dates; expired is hard fail, currently open is PASS, future start is PARTIAL, and no period is UNKNOWN.
- Stage/revenue/employees stay UNKNOWN without a usable card.
- A usable fully extracted card with no condition for a field may emit NOT_REQUIRED.

Score weights and multipliers must exactly match the spec. Return `score=null` when fewer than two checks are known. Use fixed Korean templates and never compose an AI prompt.

- [ ] **Step 5: Expose the public stateless endpoint**

```java
@PostMapping("/{id}/diagnosis")
public ApiResponse<PolicyDiagnosisResponse> diagnose(
        @PathVariable Long id,
        @Valid @RequestBody PolicyDiagnosisRequest request) {
    return ApiResponse.success(policyDiagnosisService.diagnose(id, request), "진단 완료");
}
```

Add `.requestMatchers(HttpMethod.POST, "/api/v1/policies/*/diagnosis").permitAll()` before `.anyRequest()`. Do not broaden other POST permissions.

- [ ] **Step 6: Run focused and full backend tests**

Run:

```bash
cd api
GRADLE_USER_HOME="$PWD/.gradle" ./gradlew test --tests '*DiagnosisInputNormalizerTest' --tests '*PolicyDiagnosisServiceTest' --tests '*PolicyDetailResponseTest'
GRADLE_USER_HOME="$PWD/.gradle" ./gradlew test
```

Expected: all tests pass with zero failures.

- [ ] **Step 7: Commit the task**

```bash
git add api/src/main/java/com/runab/api/dto/policy/PolicyDiagnosisRequest.java api/src/main/java/com/runab/api/dto/policy/PolicyDiagnosisResponse.java api/src/main/java/com/runab/api/dto/policy/PolicyDetailResponse.java api/src/main/java/com/runab/api/service/diagnosis/PolicyDiagnosisService.java api/src/main/java/com/runab/api/controller/PolicyController.java api/src/main/java/com/runab/api/config/SecurityConfig.java api/src/test/java/com/runab/api/service/diagnosis/PolicyDiagnosisServiceTest.java api/src/test/java/com/runab/api/dto/policy/PolicyDetailResponseTest.java
git commit -m "feat: add evidence based policy diagnosis API"
```

---

### Task 3: Add Testable Frontend Presentation Models

**Files:**
- Create: `apps/run-a-b-fe/src/features/diagnosis/model.ts`
- Create: `apps/run-a-b-fe/src/features/policy/presentation.ts`
- Create: `apps/run-a-b-fe/test/diagnosis-model.test.ts`
- Create: `apps/run-a-b-fe/test/policy-presentation.test.ts`
- Modify: `apps/run-a-b-fe/package.json`

**Interfaces:**
- Consumes: the Task 2 JSON contract and existing Korean form option labels.
- Produces: shared `PolicyDetailData`, `DiagnosisForm`, `DiagnosisResponse`, `validateDiagnosisForm`, `toDiagnosisRequest`, `statusPresentation`, `buildPolicyBrief`, and `buildPolicyQuality`.

- [ ] **Step 1: Write failing pure-model tests**

```typescript
test('preparing businesses omit operating-only values', () => {
  const request = toDiagnosisRequest({
    businessStatus: false,
    industry: '카페/음료',
    region: '서울특별시',
    annualRevenueBand: '5,000만원~1억원',
    employeeCountBand: '1~4명',
  })
  assert.deepEqual(request, {
    businessStatus: false,
    industry: '카페/음료',
    region: '서울특별시',
    annualRevenueBand: null,
    employeeCountBand: null,
  })
})

test('missing support scale is an explicit unknown brief item', () => {
  const items = buildPolicyBrief(policy({ supportScale: null }))
  assert.equal(items[1].value, '지원 금액·방식 정보가 아직 수집되지 않았어요.')
  assert.equal(items[1].available, false)
})
```

- [ ] **Step 2: Run Node tests and confirm RED**

Run: `cd apps/run-a-b-fe && node --experimental-strip-types --test test/diagnosis-model.test.ts test/policy-presentation.test.ts`

Expected: module-not-found failure for the new pure modules.

- [ ] **Step 3: Implement pure models without React or browser globals**

`validateDiagnosisForm` returns a field-keyed record and requires revenue/employee bands only when `businessStatus === true`. `toDiagnosisRequest` nulls both operating-only fields for preparing businesses. Status presentation maps every backend status and verdict to full literal Tailwind class strings so Tailwind discovers them at build time.

`buildPolicyBrief` always returns target, support, and period items with explicit unknown copy. `buildPolicyQuality` returns available count, total count, missing labels, and a `rich|partial|basic` level using only stored fields.

- [ ] **Step 4: Update the test command and confirm GREEN**

Set:

```json
"test": "node --experimental-strip-types --test test/*.test.mjs test/*.test.ts"
```

Run: `cd apps/run-a-b-fe && npm test`

Expected: login regression plus both pure-model test files pass.

- [ ] **Step 5: Commit the task**

```bash
git add apps/run-a-b-fe/src/features/diagnosis/model.ts apps/run-a-b-fe/src/features/policy/presentation.ts apps/run-a-b-fe/test/diagnosis-model.test.ts apps/run-a-b-fe/test/policy-presentation.test.ts apps/run-a-b-fe/package.json
git commit -m "test: define policy diagnosis presentation contracts"
```

---

### Task 4: Improve the Policy Explanation Without Changing Its Concept

**Files:**
- Create: `apps/run-a-b-fe/src/components/policy/PolicyBriefing.tsx`
- Create: `apps/run-a-b-fe/src/components/policy/DiagnosisEntryCard.tsx`
- Create: `apps/run-a-b-fe/test/policy-briefing.test.mjs`
- Modify: `apps/run-a-b-fe/src/pages/PolicyDetail.tsx`

**Interfaces:**
- Consumes: Task 3 `PolicyDetailData`, `buildPolicyBrief`, and `buildPolicyQuality`.
- Produces: reusable briefing and diagnosis-entry components consumed by policy detail.

- [ ] **Step 1: Write a failing SSR test for the briefing component**

```javascript
test('renders explicit unknowns and source quality without changing card language', async (t) => {
  const vite = await createServer({
    root: process.cwd(),
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true },
    optimizeDeps: { noDiscovery: true },
    ssr: { optimizeDeps: { noDiscovery: true } },
  })
  t.after(() => vite.close())
  const { PolicyBriefing } = await vite.ssrLoadModule('/src/components/policy/PolicyBriefing.tsx')
  const stream = await renderToReadableStream(React.createElement(PolicyBriefing, {
    detail: {
      id: 42,
      title: '테스트 정책',
      targetGroup: '서울 소상공인',
      supportScale: null,
      applicationPeriod: '',
      description: '저장된 정책 설명',
      purposeText: null,
      applicationMethod: null,
      sourceSystem: 'bizinfo',
      announcementNo: null,
      department: null,
    },
  }))
  await stream.allReady
  const html = await new Response(stream).text()
  assert.match(html, /서울 소상공인/)
  assert.match(html, /지원 금액·방식 정보가 아직 수집되지 않았어요/)
  assert.match(html, /기간 정보 없음 — 원문 공고 확인/)
  assert.match(html, /기업마당/)
})
```

- [ ] **Step 2: Run the component test and confirm RED**

Run: `cd apps/run-a-b-fe && node --test test/policy-briefing.test.mjs`

Expected: module-not-found failure because `PolicyBriefing.tsx` does not exist.

- [ ] **Step 3: Add the briefing component**

Render three compact inner cards under the policy title. Missing values use the exact spec copy and gray state; present values use existing `primary-50`, blue, and neutral treatments. Add a quality panel showing source, available count, missing labels, structure status, and original-notice disclaimer.

- [ ] **Step 4: Replace misleading summary and missing-section states**

- Rename the visible panel heading to `핵심 요약` while retaining the existing AI completion badge when AI content exists.
- On summary request failure, show stored description facts and the exact unavailable message from the spec.
- Always render application-method status; use factual missing copy and original URL instead of hiding the section.
- Preserve current metadata, document card, report card, dimensions, gutters, and token classes.

- [ ] **Step 5: Add the diagnosis launch card above AI report**

Use `DiagnosisEntryCard` with `to={`/policies/${policyId}/diagnosis`}`. Copy:

- Heading: `60초 정책 진단`
- Body: `내 사업 정보와 공고 조건을 비교하고, 항목별 판정 근거를 확인해 보세요.`
- CTA: `내 조건으로 진단하기`

- [ ] **Step 6: Run frontend tests, lint, and build**

Run:

```bash
cd apps/run-a-b-fe
npm test
npm run lint
npm run build
```

Expected: all commands exit zero.

- [ ] **Step 7: Commit the task**

```bash
git add apps/run-a-b-fe/src/components/policy/PolicyBriefing.tsx apps/run-a-b-fe/src/components/policy/DiagnosisEntryCard.tsx apps/run-a-b-fe/src/pages/PolicyDetail.tsx apps/run-a-b-fe/test/policy-briefing.test.mjs
git commit -m "feat: make policy explanations source transparent"
```

---

### Task 5: Build and Connect the 60-Second Diagnosis Page

**Files:**
- Create: `apps/run-a-b-fe/src/pages/PolicyDiagnosis.tsx`
- Create: `apps/run-a-b-fe/test/policy-diagnosis-shell.test.mjs`
- Modify: `apps/run-a-b-fe/src/routes/AppRoutes.tsx`
- Modify: `apps/run-a-b-fe/src/pages/Policies.tsx`

**Interfaces:**
- Consumes: Task 2 endpoint and Task 3 form/response/status models.
- Produces: `/policies/:id/diagnosis`, card-level diagnosis entry actions, and result CTAs.

- [ ] **Step 1: Write a failing route SSR test**

Use the Vite SSR harness with `MemoryRouter initialEntries={['/policies/42/diagnosis']}`. Assert the rendered initial shell includes `60초 정책 진단` and `정책 정보를 불러오고 있어요`.

- [ ] **Step 2: Run the route test and confirm RED**

Run: `cd apps/run-a-b-fe && node --test test/policy-diagnosis-shell.test.mjs`

Expected: the route falls through and does not include the diagnosis heading.

- [ ] **Step 3: Implement the route and data-loading states**

- Fetch `/api/v1/policies/{id}`.
- When an access token exists, best-effort fetch `/api/v1/users/me/business` and prefill matching fields; failure must not block guest diagnosis.
- Keep `idle|analyzing|result|error` as a single discriminated state rather than independent booleans.
- Preserve form values on request error.

- [ ] **Step 4: Implement the five-field form and audit animation**

Use existing option labels and card tokens. For operating businesses show revenue and employee controls; for preparing businesses hide them. Submit `toDiagnosisRequest(form)` to `POST /api/v1/policies/{id}/diagnosis` and combine the request with a 1,500ms minimum display promise. Show the three audit steps in order and disable repeat submission while analyzing.

- [ ] **Step 5: Implement the evidence result**

Render verdict, optional score, confidence, summary, six evidence rows, unknown-condition chips, quality details, and disclaimer. Each evidence row shows both user and policy values and uses both text and an icon for status. Add these actions:

- Original: `result.policy.detailUrl`
- Checklist: `/policies/{id}/checklist`
- Retry: return to the same preserved form
- Detail: `/policies/{id}`

- [ ] **Step 6: Add diagnosis actions to both policy card layouts**

Add a `60초 진단` button to grid and list cards. Its handler calls `event.stopPropagation()` and navigates to `/policies/{policy.id}/diagnosis`; existing whole-card detail navigation remains unchanged.

- [ ] **Step 7: Run route test, full frontend tests, lint, and build**

Run:

```bash
cd apps/run-a-b-fe
node --test test/policy-diagnosis-shell.test.mjs
npm test
npm run lint
npm run build
```

Expected: all commands exit zero.

- [ ] **Step 8: Commit the task**

```bash
git add apps/run-a-b-fe/src/pages/PolicyDiagnosis.tsx apps/run-a-b-fe/src/routes/AppRoutes.tsx apps/run-a-b-fe/src/pages/Policies.tsx apps/run-a-b-fe/test/policy-diagnosis-shell.test.mjs
git commit -m "feat: add 60 second policy diagnosis flow"
```

---

### Task 6: Make the Checklist API-First and Truthful

**Files:**
- Create: `api/src/test/java/com/runab/api/service/checklist/PolicyChecklistServiceTest.java`
- Modify: `api/src/main/java/com/runab/api/service/checklist/PolicyChecklistService.java`
- Create: `apps/run-a-b-fe/src/features/policy/checklist.ts`
- Create: `apps/run-a-b-fe/test/policy-checklist-loader.test.ts`
- Modify: `apps/run-a-b-fe/src/pages/PolicyChecklist.tsx`

**Interfaces:**
- Consumes: existing policy and checklist GET endpoints.
- Produces: generic documents as recommendations, exact no-period copy, and API-first loading for every policy ID.

- [ ] **Step 1: Write failing backend truthfulness tests**

```java
@Test
void missingCardReturnsRecommendationsNotRequiredDocuments() {
    given(policyRepository.findById(1L)).willReturn(Optional.of(policyWithoutDates()));
    given(policyCardRepository.findByPolicyId(anyString())).willReturn(Optional.empty());

    var result = service.getChecklist(1L);

    assertThat(result.getApplicationPeriod()).isEqualTo("기간 정보 없음 — 원문 공고 확인");
    assertThat(result.getApplicationChecklist()).allMatch(item -> !item.isRequired());
}

@Test
void requiredFlagsAreNotMisrepresentedAsDocuments() {
    Policy policy = policyWithoutDates();
    PolicyCard card = PolicyCard.builder()
            .policyId(PolicyCard.toPolicyId("PBLN_TEST"))
            .externalId("PBLN_TEST")
            .extractionStatus("extracted")
            .fullJson("""
                    {"eligibility": {
                      "documents": ["매출 증빙"],
                      "required_flags": ["국세 완납"]
                    }}
                    """)
            .build();
    given(policyRepository.findById(1L)).willReturn(Optional.of(policy));
    given(policyCardRepository.findByPolicyId(PolicyCard.toPolicyId("PBLN_TEST")))
            .willReturn(Optional.of(card));

    var result = service.getChecklist(1L);

    assertThat(result.getApplicationChecklist())
            .extracting(PolicyChecklistResponse.ChecklistItem::getLabel)
            .contains("매출 증빙")
            .doesNotContain("국세 완납");
    assertThat(result.getApplicationChecklist()).allMatch(item -> !item.isRequired());
}
```

Define the fixture exactly as follows:

```java
private Policy policyWithoutDates() {
    Policy policy = Policy.builder()
            .externalId("PBLN_TEST")
            .title("테스트 정책")
            .category("경영")
            .region("전국")
            .industry("전체")
            .description("테스트 설명")
            .agency("테스트 기관")
            .detailUrl("https://www.bizinfo.go.kr/test")
            .sourceSystem("bizinfo")
            .build();
    ReflectionTestUtils.setField(policy, "id", 1L);
    return policy;
}
```

- [ ] **Step 2: Run focused backend test and confirm RED**

Run: `cd api && GRADLE_USER_HOME="$PWD/.gradle" ./gradlew test --tests '*PolicyChecklistServiceTest'`

Expected: current common docs are required and missing dates return the old `상시` text.

- [ ] **Step 3: Implement backend corrections**

- Set every generic common document to `required=false` and describe it as a common preparation suggestion.
- Read `documents` only; do not turn `required_flags` into documents.
- Mark string-form card documents non-required with `공고문에 언급된 서류예요. 필수 여부는 원문을 확인해 주세요.`
- Change the no-date period to `기간 정보 없음 — 원문 공고 확인`.

- [ ] **Step 4: Write and run the failing frontend loader test**

```typescript
test('policy id 1 still loads live policy and checklist APIs', async () => {
  const calls: string[] = []
  const client = {
    async get(url: string) {
      calls.push(url)
      return url.endsWith('/checklist')
        ? { data: { data: checklistFixture } }
        : { data: { data: policyFixture } }
    },
  }
  await loadPolicyChecklist(client, 1)
  assert.deepEqual(calls, ['/api/v1/policies/1', '/api/v1/policies/1/checklist'])
})
```

Run: `cd apps/run-a-b-fe && node --experimental-strip-types --test test/policy-checklist-loader.test.ts`

Expected: module-not-found failure for the loader.

- [ ] **Step 5: Replace mock precedence with the loader**

`loadPolicyChecklist(client, id)` calls both real endpoints with `Promise.all` and returns `{ policy, detail, sourceUrl }`. Remove `MOCK_POLICIES` and `POLICY_DETAILS` imports and all ID-based branches from `PolicyChecklist.tsx`.

When `required.length === 0`, label the document group `일반 준비 권장`, show `필수 여부는 원문 공고에서 확인해 주세요`, keep the original-application CTA available when a URL exists, and do not state that all required documents are complete.

- [ ] **Step 6: Run focused and full tests**

Run:

```bash
cd api
GRADLE_USER_HOME="$PWD/.gradle" ./gradlew test --tests '*PolicyChecklistServiceTest'
GRADLE_USER_HOME="$PWD/.gradle" ./gradlew test
cd ../apps/run-a-b-fe
npm test
npm run lint
npm run build
```

Expected: all commands exit zero.

- [ ] **Step 7: Commit the task**

```bash
git add api/src/main/java/com/runab/api/service/checklist/PolicyChecklistService.java api/src/test/java/com/runab/api/service/checklist/PolicyChecklistServiceTest.java apps/run-a-b-fe/src/features/policy/checklist.ts apps/run-a-b-fe/test/policy-checklist-loader.test.ts apps/run-a-b-fe/src/pages/PolicyChecklist.tsx
git commit -m "fix: keep policy checklist claims source accurate"
```

---

### Task 7: Verify the Integrated Exhibition Flow

**Files:**
- Modify only if verification exposes a covered defect: files and tests from Tasks 1–6.

**Interfaces:**
- Consumes: all implemented endpoints, pages, and tests.
- Produces: verified local policy-list → detail → diagnosis → result → checklist workflow.

- [ ] **Step 1: Run the complete automated verification**

Run from repository root:

```bash
make check
```

Expected: backend tests, frontend tests, ESLint, and production build all exit zero.

- [ ] **Step 2: Restart changed local services if hot reload did not apply**

Use the existing `make dev` workflow and keep MySQL data. Do not remove volumes or overwrite `.env`.

- [ ] **Step 3: Browser-test the real-data flow**

At `http://localhost:5173/policies`:

1. Open a real policy.
2. Confirm the three briefing cards, explicit missing information, source quality, and original link.
3. Open 60-second diagnosis.
4. Submit an operating profile and inspect the loading steps.
5. Confirm evidence rows, low confidence when cards are absent, and no official-eligibility wording.
6. Open the checklist and confirm live title, general recommendations, unknown period wording where applicable, and original link.
7. Return to policy list and ensure filters and card navigation still work.

- [ ] **Step 4: Inspect browser errors and narrow-screen overflow**

Confirm no uncaught console errors or failed local API requests. Check that the preserved desktop layout remains intact at the current app viewport; fix only overflow introduced by these changes.

- [ ] **Step 5: Re-run affected test first, then full verification after every fix**

For a defect, first add a failing regression test that reproduces it. Run that focused test RED, apply the smallest fix, run it GREEN, then run `make check` again.

- [ ] **Step 6: Commit verified integration fixes if any**

```bash
git add api/src/main/java/com/runab/api/dto/policy/PolicyDiagnosisRequest.java api/src/main/java/com/runab/api/dto/policy/PolicyDiagnosisResponse.java api/src/main/java/com/runab/api/dto/policy/PolicyDetailResponse.java api/src/main/java/com/runab/api/service/diagnosis/DiagnosisInputNormalizer.java api/src/main/java/com/runab/api/service/diagnosis/PolicyDiagnosisService.java api/src/main/java/com/runab/api/controller/PolicyController.java api/src/main/java/com/runab/api/config/SecurityConfig.java api/src/main/java/com/runab/api/service/checklist/PolicyChecklistService.java api/src/test/java/com/runab/api/service/diagnosis/DiagnosisInputNormalizerTest.java api/src/test/java/com/runab/api/service/diagnosis/PolicyDiagnosisServiceTest.java api/src/test/java/com/runab/api/dto/policy/PolicyDetailResponseTest.java api/src/test/java/com/runab/api/service/checklist/PolicyChecklistServiceTest.java apps/run-a-b-fe/src/features/diagnosis/model.ts apps/run-a-b-fe/src/features/policy/presentation.ts apps/run-a-b-fe/src/features/policy/checklist.ts apps/run-a-b-fe/src/components/policy/PolicyBriefing.tsx apps/run-a-b-fe/src/components/policy/DiagnosisEntryCard.tsx apps/run-a-b-fe/src/pages/PolicyDetail.tsx apps/run-a-b-fe/src/pages/PolicyDiagnosis.tsx apps/run-a-b-fe/src/pages/Policies.tsx apps/run-a-b-fe/src/pages/PolicyChecklist.tsx apps/run-a-b-fe/src/routes/AppRoutes.tsx apps/run-a-b-fe/test/diagnosis-model.test.ts apps/run-a-b-fe/test/policy-presentation.test.ts apps/run-a-b-fe/test/policy-briefing.test.mjs apps/run-a-b-fe/test/policy-diagnosis-shell.test.mjs apps/run-a-b-fe/test/policy-checklist-loader.test.ts apps/run-a-b-fe/package.json
git commit -m "fix: stabilize policy diagnosis exhibition flow"
```

If no defects required code changes, do not create an empty commit.
