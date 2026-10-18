# Policy Detail Enrichment and Honest Relevance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace identical fallback relevance scores with source-grounded policy-specific matching and turn dense stored notice text into a detailed, scan-friendly policy page.

**Architecture:** Extend the existing pure matcher with a `Policy` metadata fallback for only the region, industry, and date criteria when card evidence is unknown. Expose missing relevance as nullable. Add pure TypeScript presentation helpers for nullable relevance and source-marker parsing, then consume them in the existing list and detail UI.

**Tech Stack:** Java 21, Spring Boot 3.5, Jackson, JUnit 5/AssertJ/Mockito, React 19, TypeScript 6, Vite 8, Node test runner.

**Spec:** `docs/superpowers/specs/2026-10-16-policy-detail-relevance-design.md`

## Global Constraints

- Write each production behavior only after its covering test fails for the expected missing behavior.
- Preserve current `PolicyCard` precedence and all existing score weights.
- Missing evidence must never become a fabricated pass, failure, or numeric fact.
- Preserve the current visual system and avoid unrelated refactors.
- Do not stop or replace the already-running local service session except when its existing development reload behavior handles changes.

---

### Task 1: Make Relevance Policy-Aware and Nullable When Unavailable

**Files:**
- Modify: `api/src/test/java/com/runab/api/service/matcher/EligibilityMatcherTest.java`
- Modify: `api/src/main/java/com/runab/api/service/matcher/EligibilityMatcher.java`
- Modify: `api/src/main/java/com/runab/api/service/PolicyService.java`
- Modify: `api/src/main/java/com/runab/api/dto/policy/PolicyListResponse.java`
- Add or modify a focused `PolicyService` test if needed for the nullable response boundary.

**Interfaces:**
- Produces: `EligibilityMatcher.match(BusinessInfo, PolicyCard, Policy)` while preserving `match(BusinessInfo, PolicyCard)`.
- Produces: `PolicyListResponse.relevance: Integer`, null when no business comparison occurred.
- Consumed by Task 2: the frontend list model accepts `number | null`.

- [ ] Add matcher tests for nationwide/current, same-region/current, explicit-industry mismatch, upcoming, expired, and wholly missing metadata with a null card.
- [ ] Run `cd api && GRADLE_USER_HOME="$PWD/.gradle" ./gradlew test --tests '*EligibilityMatcherTest'` and confirm the new tests fail because the policy-aware overload is absent.
- [ ] Implement the minimal overload and metadata fallback helpers; card results remain authoritative.
- [ ] Update both `PolicyService` scoring paths to pass the policy and update anonymous DTO relevance to null.
- [ ] Run the focused matcher and policy-service tests and confirm green.
- [ ] Commit with `fix: derive relevance from policy evidence`.

**Task verification:** `cd api && GRADLE_USER_HOME="$PWD/.gradle" ./gradlew test`

---

### Task 2: Render Honest Relevance and Structured Notice Details

**Files:**
- Modify: `apps/run-a-b-fe/test/policy-presentation.test.ts`
- Modify: `apps/run-a-b-fe/test/policy-briefing.test.mjs`
- Modify: `apps/run-a-b-fe/src/features/policy/presentation.ts`
- Modify: `apps/run-a-b-fe/src/data/policies.ts`
- Modify: `apps/run-a-b-fe/src/pages/Policies.tsx`
- Modify if required: `apps/run-a-b-fe/src/pages/HomePage.tsx`
- Create: `apps/run-a-b-fe/src/components/policy/PolicyNoticeDetails.tsx`
- Modify: `apps/run-a-b-fe/src/pages/PolicyDetail.tsx`

**Interfaces:**
- Consumes: nullable backend relevance from Task 1.
- Produces: `relevancePresentation(number | null)` and `buildPolicyNotice(PolicyDetailData)` pure presentation helpers.
- Produces: a source-grounded detail component using those parsed sections.

- [ ] Add pure tests proving null relevance is labeled as unavailable and numeric relevance retains its percentage.
- [ ] Add pure tests proving `☞` clauses split in order, duplicates/blanks disappear, and unmarked text remains unchanged.
- [ ] Add SSR component coverage proving the stored overview, clauses, target, period, method, and source disclaimer render without invented values.
- [ ] Run the focused frontend tests and confirm RED for missing helpers/component.
- [ ] Implement the helpers, nullable model, list rendering, and structured detail component with the existing visual tokens.
- [ ] Replace the dense duplicated detail paragraph with the component while preserving AI-expanded text when genuinely available.
- [ ] Run frontend tests, type build, and lint; confirm green.
- [ ] Commit with `feat: enrich policy notice details`.

**Task verification:** `cd apps/run-a-b-fe && npm test && npm run build && npm run lint`

---

### Task 3: Verify the Live Behavior and Regression Suite

**Files:** No production files unless a failing verification first receives a regression test.

- [ ] Run the complete backend and frontend test suites.
- [ ] Log in with the local demo account and query the relevance-sorted API; verify visible policies have more than one score when their metadata differs.
- [ ] Verify the anonymous API emits null relevance rather than zero.
- [ ] Open a real policy detail containing `☞` markers and verify every source clause is visible and the layout remains responsive.
- [ ] Run a fresh whole-branch code review; fix Critical/Important findings with RED→GREEN tests and ledger any deferred minors.

**Task verification:** backend tests, frontend tests/build/lint, authenticated API distribution, anonymous API sample, browser smoke check.

## Review Focus

- Card evidence must not be silently overridden by weaker metadata.
- Metadata mismatch must not become a hard region rejection.
- Closed dates must remain a hard failure and future dates must not look open.
- Null relevance must not break Home or Policies rendering.
- The detail parser must preserve Korean source text exactly and never generate facts.
- Long descriptions and mobile widths must not create overflow.

