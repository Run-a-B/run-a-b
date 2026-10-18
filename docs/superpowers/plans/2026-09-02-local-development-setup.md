# Local Development Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a fresh Run-a-B clone reproducibly installable, testable, and runnable as a local full stack.

**Architecture:** Keep the existing Spring Boot API and Vite frontend processes native for fast development, while Docker Compose owns an isolated MySQL 8.4 database on host port 3307. A root `.env` contract supplies both Compose and Spring/Vite, and Make targets orchestrate setup, checks, and concurrent development processes.

**Tech Stack:** Java 21, Gradle 8.14.4, Spring Boot 3.5, MySQL 8.4, H2 (tests only), Node.js 22, React 19, TypeScript 6, Vite 8, Docker Compose v2, GNU/BSD Make

**Spec:** `docs/superpowers/specs/2026-09-02-local-development-setup.md`

## Global Constraints

- Do not commit real credentials.
- Preserve production/deployment behavior; local defaults may only make optional integrations non-blocking.
- Use `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SERVER_PORT`, `JWT_SECRET`, `GOOGLE_CLIENT_ID`, `MAIL_USERNAME`, `MAIL_APP_PASSWORD`, `BIZINFO_API_KEY`, `OPENAI_API_KEY`, and `OPENAI_MODEL` as the backend environment contract.
- Use `VITE_API_BASE_URL` and `VITE_GOOGLE_CLIENT_ID` as the frontend environment contract.
- Keep the MySQL Compose host port at `3307` to avoid the existing local MySQL service on `3306`.

---

### Task 1: Reproducible local environment

**Files:**
- Create: `.env.example`
- Create: `compose.yaml`
- Create: `Makefile`
- Modify: `.gitignore`
- Modify: `api/src/main/resources/application.properties`
- Modify: `apps/run-a-b-fe/src/lib/api.ts`

**Interfaces:**
- Consumes: Docker Compose v2, Node.js 22+, Java 21 Gradle toolchain.
- Produces: `make setup`, `make dev`, `make check`, `make db-up`, and `make db-down`; environment variables listed in Global Constraints.

- [x] **Step 1: Preserve the failing startup evidence**

Run:

```bash
cd api
GOOGLE_CLIENT_ID=dummy MAIL_APP_PASSWORD=dummy OPENAI_API_KEY=dummy \
  GRADLE_USER_HOME="$PWD/.gradle" ./gradlew test
```

Expected: `ApiApplicationTests.contextLoads()` fails with `Unknown database 'runab_db'` in the unmodified baseline.

- [x] **Step 2: Add the local environment template**

Create `.env.example` with non-secret local database defaults, a 32+ byte development JWT secret, blank optional integration credentials, and `VITE_GOOGLE_CLIENT_ID`.

- [x] **Step 3: Add MySQL Compose configuration**

Create `compose.yaml` with `mysql:8.4`, database `runab_db`, root password from `DB_PASSWORD`, host port `3307`, a named volume, and a `mysqladmin ping` health check.

- [x] **Step 4: Make backend properties environment-driven**

Use Spring placeholder defaults, for example:

```properties
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/runab_db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:mysql}
google.client-id=${GOOGLE_CLIENT_ID:}
spring.mail.username=${MAIL_USERNAME:}
spring.mail.password=${MAIL_APP_PASSWORD:}
bizinfo.api.key=${BIZINFO_API_KEY:P50RYC}
openai.api-key=${OPENAI_API_KEY:}
openai.model=${OPENAI_MODEL:gpt-4o-mini}
```

- [x] **Step 5: Add root Make targets**

`make setup` must copy `.env.example` only when `.env` is absent, install frontend packages with `npm ci`, start MySQL with `docker compose up -d --wait mysql`, and prime the Gradle build. `make dev` must source `.env`, start API and frontend concurrently, and clean up both child processes on exit. `make check` must run backend tests, frontend lint, and frontend build.

- [x] **Step 6: Validate setup artifacts**

Run:

```bash
docker compose config
make setup
```

Expected: Compose config is valid; the MySQL container becomes healthy; npm and Gradle setup finish successfully.

### Task 2: Backend test isolation and scoring expectation

**Files:**
- Modify: `api/build.gradle`
- Create: `api/src/test/resources/application-test.properties`
- Modify: `api/src/test/java/com/runab/api/ApiApplicationTests.java`
- Modify: `api/src/test/java/com/runab/api/service/recommend/RecommendRequestAssemblerTest.java`

**Interfaces:**
- Consumes: Spring profile `test`, H2 in MySQL compatibility mode, existing `EligibilityMatcher.normalizedScore()` formula.
- Produces: database-independent backend test suite with the hand-derived normalized score expectation `94`.

- [x] **Step 1: Verify the context test fails without MySQL**

Stop/omit MySQL and run `./gradlew test --tests com.runab.api.ApiApplicationTests`; expect a datasource failure in the baseline.

- [x] **Step 2: Add the H2 test runtime**

Add:

```groovy
testRuntimeOnly 'com.h2database:h2'
```

- [x] **Step 3: Add the test profile**

Create `application-test.properties` using `jdbc:h2:mem:runab;MODE=MySQL;DB_CLOSE_DELAY=-1`, `org.h2.Driver`, H2 dialect, `create-drop`, and harmless values for required external properties. Annotate `ApiApplicationTests` with `@ActiveProfiles("test")`.

- [x] **Step 4: Correct the stale normalized-score assertion**

The production formula added in commit `3cedf68` excludes `NOT_REQUIRED` weights. For the fixture, hand calculation is `(20+20+10+10+3.2+8+8) / (20+20+10+10+8+8+8) * 100 = 94.285...`, rounded to `94`. Update the assertion and its explanation from `95` to `94`; do not change production scoring code.

- [x] **Step 5: Run focused and full backend tests**

Run:

```bash
cd api
GRADLE_USER_HOME="$PWD/.gradle" ./gradlew test \
  --tests com.runab.api.ApiApplicationTests \
  --tests com.runab.api.service.recommend.RecommendRequestAssemblerTest
GRADLE_USER_HOME="$PWD/.gradle" ./gradlew test
```

Expected: focused tests pass, then all 14 tests pass without MySQL.

### Task 3: Frontend lint baseline repair

**Files:**
- Modify: `apps/run-a-b-fe/src/contexts/AuthContext.tsx`
- Modify: `apps/run-a-b-fe/src/data/reports.ts`
- Modify: `apps/run-a-b-fe/src/pages/Login.tsx`
- Modify: `apps/run-a-b-fe/src/pages/Policies.tsx`
- Modify: `apps/run-a-b-fe/src/pages/PolicyChecklist.tsx`
- Modify: `apps/run-a-b-fe/src/pages/PolicyDetail.tsx`
- Modify: `apps/run-a-b-fe/src/pages/ReportDetail.tsx`
- Modify: `apps/run-a-b-fe/src/pages/ReportList.tsx`
- Modify: `apps/run-a-b-fe/src/pages/Signup.tsx`
- Modify: `apps/run-a-b-fe/src/pages/mypage/MyBusinessPage.tsx`
- Modify: `apps/run-a-b-fe/src/pages/mypage/MyInfoPage.tsx`
- Modify: `apps/run-a-b-fe/package-lock.json`

**Interfaces:**
- Consumes: Axios `isAxiosError`, React hooks lint rules, current API response shape.
- Produces: the same user-visible behavior with zero ESLint errors or warnings.

- [x] **Step 1: Preserve the lint RED result**

Run `npm run lint`; expect 21 errors and 2 warnings in the baseline.

- [x] **Step 2: Remove explicit `any` error handling**

Change catch variables to `unknown`, use `axios.isAxiosError()` before reading `response`, and type the user update request body explicitly.

- [x] **Step 3: Make auth initialization render-safe**

Use a lazy `useState` initializer to read and parse local storage instead of synchronously calling `setUser` inside `useEffect`. Keep the `useAuth` export with the narrow Fast Refresh suppression required for the established single-file context pattern.

- [x] **Step 4: Stabilize policy fetching**

Declare `fetchPolicies` with `useCallback` before the debouncing effect and depend on that callback, eliminating the before-declaration and missing-dependency diagnostics.

- [x] **Step 5: Document intentional effect resets narrowly**

Where route changes intentionally reset loading/error state before an asynchronous request, use a single-line `react-hooks/set-state-in-effect` suppression adjacent to that state transition. Do not disable the rule globally.

- [x] **Step 6: Correct effect dependencies without loops**

Use functional/context-safe updates where needed so the user-loading effect does not depend on a stale `user` object, and include stable dependencies required by the lint rule.

- [x] **Step 7: Verify frontend checks**

Run:

```bash
cd apps/run-a-b-fe
npm run lint
npm run build
```

Expected: both commands exit 0 with no lint warnings.

- [x] **Step 8: Apply non-breaking dependency security updates**

Run `npm audit fix` without `--force`, then repeat lint and production build. High-severity advisories must be removed without forcing the React Router 7 breaking upgrade; document any remaining moderate advisories that require that migration.

### Task 4: Developer documentation and full-stack smoke test

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: Make targets and environment contract from Task 1.
- Produces: copy-paste setup, start, stop, test, integration-key, port-override, and troubleshooting instructions.

- [x] **Step 1: Replace the placeholder README**

Document prerequisites, `cp .env.example .env`, `make setup`, `make dev`, URLs (`http://localhost:5173`, `http://localhost:8082`), `make check`, `make db-down`, and the three optional external integrations.

- [x] **Step 2: Start the stack**

Run `make dev` in a PTY and retain the session while smoke tests execute.

- [x] **Step 3: Verify HTTP boundaries**

Run:

```bash
curl --fail 'http://localhost:8082/api/v1/policies?page=1&size=1'
curl --fail http://localhost:5173/
```

Expected: API returns a successful JSON envelope and frontend returns HTML.

- [x] **Step 4: Run the final check suite**

Run `make check` and `git diff --check`.

Expected: backend tests, frontend lint/build, and whitespace validation all pass.

- [x] **Step 5: Review scope**

Run `git status --short` and `git diff --stat`; verify every changed file traces to this specification and no `.env`, credentials, build products, database volume, or unrelated source changes are tracked.
