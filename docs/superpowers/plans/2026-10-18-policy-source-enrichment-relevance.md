# Policy Source Enrichment and Relevance Accuracy Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or superpowers:subagent-driven-development to implement this plan task by task.

**Goal:** Make relevance ordering semantically honest for region, industry and dates, and preserve the richest official Bizinfo notice content already supplied by the API.

**Architecture:** Keep the existing score weights, but improve evidence classification and place hard failures after eligible/uncertain results. Extend the Bizinfo ingestion boundary with explicit source fields, parse source HTML once into safe whitespace-preserving text, persist raw source content and expose a typed detail contract. Render official text and files separately from AI content while retaining legacy fallbacks.

**Tech Stack:** Spring Boot 3, Java 21, Jackson, JPA/Hibernate, jsoup 1.23.2, JUnit 5/Mockito, React 19, TypeScript, Vite, Node test runner.

**Design:** `docs/superpowers/specs/2026-10-18-policy-source-enrichment-relevance-design.md`

---

## Global constraints

- [ ] Preserve existing score weights and the current visual design language.
- [ ] Treat a confident local-region mismatch, expired notice, or explicit unrelated industry as honest negative evidence; retain UNKNOWN when evidence is genuinely absent or ambiguous.
- [ ] Never rewrite, reorder, deduplicate, or inject raw HTML in the original-source view.
- [ ] Do not bulk-download or claim to parse PDF/HWP files during list sync; expose official HTTP(S) links.
- [ ] Use tests-first RED → GREEN → REFACTOR for every behavior change.
- [ ] Preserve user changes and keep unrelated files untouched.

### Task 1: Correct relevance evidence and hard-fail ordering

**Files:**
- Modify: `api/src/main/java/com/runab/api/service/matcher/RegionHierarchy.java`
- Modify: `api/src/main/java/com/runab/api/service/matcher/PolicyIndustryFallbackMatcher.java`
- Modify: `api/src/main/java/com/runab/api/service/matcher/EligibilityMatcher.java`
- Modify: `api/src/main/java/com/runab/api/service/PolicyService.java`
- Test: `api/src/test/java/com/runab/api/service/matcher/RegionHierarchyTest.java`
- Test: `api/src/test/java/com/runab/api/service/matcher/PolicyIndustryFallbackMatcherTest.java`
- Test: `api/src/test/java/com/runab/api/service/matcher/EligibilityMatcherTest.java`
- Test: `api/src/test/java/com/runab/api/service/PolicyServiceIndustryFilterTest.java`

- [ ] Write failing tests proving an explicit `부산광역시 강서구` resolves to Busan, while an ambiguous district stays multi-valued/unknown.
- [ ] Write failing tests proving explicit unrelated supported and exclusive industries return FAIL, while absent evidence returns UNKNOWN.
- [ ] Write failing tests proving effective card dates win, stored policy dates fill missing bounds, and dates override stale `open` state.
- [ ] Write a failing service test proving a higher numerical hard-fail result sorts after a lower non-hard-fail result.
- [ ] Implement the smallest hierarchy, tri-state, effective-date, and comparator changes that make the tests pass.
- [ ] Run the focused matcher and service tests and inspect the diff for unintended score-formula changes.
- [ ] Commit the relevance slice.

### Task 2: Preserve official Bizinfo source fields during sync

**Files:**
- Modify: `api/build.gradle`
- Modify: `api/src/main/java/com/runab/api/dto/policy/BizinfoPolicyItem.java`
- Modify: `api/src/main/java/com/runab/api/entity/Policy.java`
- Modify: `api/src/main/java/com/runab/api/service/PolicySyncService.java`
- Create: `api/src/main/java/com/runab/api/service/source/BizinfoSourceTextParser.java`
- Create: `api/src/test/java/com/runab/api/dto/policy/BizinfoPolicyItemTest.java`
- Create: `api/src/test/java/com/runab/api/service/source/BizinfoSourceTextParserTest.java`
- Create: `api/src/test/java/com/runab/api/service/PolicySyncServiceTest.java`

- [ ] Write a failing deserialization test using official field names including `description`, live `hashtags`/compatible aliases, executing agency, contact and both file roles.
- [ ] Write failing parser tests for paragraphs, `<br>`, lists, table rows, entities, repeated clauses, and removal of script/style content.
- [ ] Write failing sync tests for long source preservation, raw period, compact dates, idempotent updates, attachment metadata and `전남광주` disambiguation.
- [ ] Add jsoup and explicit DTO aliases/fields.
- [ ] Persist raw source HTML plus safe original text, original method/period, agency, contact and two typed file roles without guessing absent facts.
- [ ] Parse `yyyy-MM-dd` and `yyyyMMdd`; infer a reliable region from explicit title/district evidence and leave unresolved combined regional tags unknown rather than nationwide.
- [ ] Run focused ingestion tests and verify an update never erases prior enriched values merely because a transient payload field is blank.
- [ ] Commit the ingestion slice.

### Task 3: Expose and render exact official source content

**Files:**
- Modify: `api/src/main/java/com/runab/api/dto/policy/PolicyDetailResponse.java`
- Modify: `api/src/test/java/com/runab/api/dto/policy/PolicyDetailResponseTest.java`
- Modify: `apps/run-a-b-fe/src/features/policy/presentation.ts`
- Modify: `apps/run-a-b-fe/src/components/policy/PolicyNoticeDetails.tsx`
- Modify: `apps/run-a-b-fe/src/pages/PolicyDetail.tsx`
- Modify: `apps/run-a-b-fe/test/policy-briefing.test.mjs`

- [ ] Write a failing backend DTO test for complete source text, agency/contact/raw period and ordered attachment/full-notice roles.
- [ ] Write failing frontend tests proving source order, repetitions and newlines survive, unsafe URLs are not anchors, and AI method text cannot replace the original method.
- [ ] Add the typed detail response and frontend model with legacy-null compatibility.
- [ ] Render official source text as escaped JSX with `whitespace-pre-wrap` and wrap long values/URLs at mobile width.
- [ ] Keep AI expansion under an explicit AI label and always show the stored original method separately.
- [ ] Run focused backend/frontend tests and the frontend type build.
- [ ] Commit the detail slice.

### Task 4: Backfill and verify the running application

**Files:**
- Modify only if verification reveals a covered defect.

- [ ] Run all backend tests.
- [ ] Run all frontend tests, lint and production build.
- [ ] Restart backend if the dependency/schema changed while preserving the frontend service.
- [ ] Invoke the existing policy sync once and confirm row count remains stable with no external-ID duplicates.
- [ ] Authenticate with the demo account and inspect score distribution and top results; confirm local, expired and explicit unrelated examples no longer occupy recommendation slots incorrectly.
- [ ] Open one enriched policy and verify the final source clause, original method, period, contact, agency and both file roles at desktop and narrow width.
- [ ] Request an independent code review and address every material finding.
- [ ] Run final verification commands again and report exact evidence, known source limitations and service URLs.
