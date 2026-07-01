# 작업 지시서: 관련도 / 리포트 계정별 미분리 버그 진단 및 수정

## 배경
- 프로젝트: Run a B (소상공인 AI 정책 매칭 플랫폼), 팀 캡스톤, 최종 발표 2026.07.03
- 레포: https://github.com/Run-a-B/run-a-b (모노레포: `api/` 백엔드 Spring Boot, `apps/run-a-b-fe/` 프론트 React)
- 배포: EC2(13.209.82.137), systemd 서비스명 `runab-api`, GitHub Actions로 main push 시 자동배포
- 최근 머지된 작업: 관련도 실제 계산(EligibilityMatcher 연동), 지역 계층 매칭(RegionHierarchy), OpenAI 기반 AI 리포트 생성(`POST /api/v1/policies/{id}/report`), PolicyCard 조회 시 `policyId` 컬럼 기준으로 수정(`external_id` 컬럼 데이터 오염 우회)

이 작업들을 실사이트에 배포하고 테스트 계정 2개("러너비", "러너비2")로 교차 검증한 결과 아래 문제들을 발견함. **이 문서에 명시된 문제 외에도, 관련 코드를 리뷰하다가 추가 버그나 논리적 결함을 발견하면 별도 승인 없이 바로 분석·수정하고 아래 "작업 로그" 섹션에 근거를 남길 것.**

---

## 문제 1: "내 리포트" 목록이 계정과 무관하게 동일하게 나옴 (확정 버그)

### 증상
서로 다른 계정("러너비", "러너비2")으로 `/reports` 페이지에 들어가도 완전히 동일한 리포트 3개가 뜸.

### 원인 (확인됨)
`apps/run-a-b-fe/src/data/reports.ts`가 리포트를 **브라우저 localStorage**(`rab_reports` 키)에 저장함. 로그인 계정과 무관하게 브라우저 단위로 공유되는 저장소라, 같은 브라우저에서 계정만 바꿔 로그인하면 이전 계정이 만든 리포트가 그대로 보임.

관련 파일:
- `apps/run-a-b-fe/src/data/reports.ts` — `getSavedReports`, `saveReport`, `deleteReport` 전부 localStorage 기반
- `apps/run-a-b-fe/src/pages/ReportList.tsx` — 목록 조회
- `apps/run-a-b-fe/src/pages/ReportDetail.tsx` — 상세/삭제
- `apps/run-a-b-fe/src/pages/PolicyDetail.tsx` — `handleGenerateReport()`에서 `saveReport()` 호출 (현재는 `POST /api/v1/policies/{id}/report` 응답을 받아서 localStorage에 저장하는 구조)

### 요구사항 (수정 방향)
리포트를 **백엔드 DB에 user_id로 스코프**해서 저장하도록 구조 변경.

1. **백엔드**
   - `Report` 엔티티 신규 (제안 스키마 — 필요시 조정 가능):
     - `id`, `user_id`(FK), `policy_id`(FK, Policy), `policy_title`, `category`, `impact_label`, `impact_style`, `summary`, `details`(JSON), `related_ids`(JSON), `business_impact`(JSON), `created_at`, `updated_at`
     - `(user_id, policy_id)` 유니크 제약 — 같은 정책 재생성 시 upsert(덮어쓰기), 프론트 기존 로직(`saveReport`의 `idx>=0 ? replace : unshift`)과 동일한 시맨틱 유지
   - `PolicyReportService.generate()`가 OpenAI 호출 후 결과를 반환만 하지 말고 **DB에 저장(upsert)까지** 하도록 수정
   - 신규 엔드포인트:
     - `GET /api/v1/reports` — 로그인 사용자의 리포트 목록 (ReportList.tsx가 요구하는 정렬/필터 — 최신순/오래된순/긍정영향/부정영향 — 는 `ReportList.tsx` 실제 코드를 먼저 읽고 그에 맞게 설계할 것)
     - `GET /api/v1/reports/{policyId}` 또는 `GET /api/v1/policies/{id}/report` (기존 생성 엔드포인트와 통합 검토) — 상세 조회
     - `DELETE /api/v1/reports/{policyId}` — 삭제
   - 인증 필요 (SecurityConfig에서 `anyRequest().hasRole("ACCESS")`로 자동 적용됨 — 별도 permitAll 추가하지 말 것)

2. **프론트**
   - `reports.ts`의 localStorage 함수들을 axios 기반 API 호출로 교체 (`src/lib/api.ts`의 `api` 인스턴스 사용 — 인터셉터가 JWT 자동 첨부함)
   - `ReportList.tsx`, `ReportDetail.tsx`, `PolicyDetail.tsx`의 `getSavedReports`/`saveReport`/`deleteReport` 호출부를 API 연동으로 교체
   - 로딩/에러 상태 처리 추가 (기존 정책 목록 페이지 패턴 참고: `useState` + `useEffect` + try/catch)

---

## 문제 2: 관련도 점수가 계정과 무관하게 동일하게 나옴 (진단 필요 — 버그 여부 미확정)

### 증상
"러너비", "러너비2" 두 계정으로 `/policies` 봤을 때 같은 정책들에 대해 완전히 동일한 관련도 %가 나옴.

### 진단 절차 (반드시 먼저 수행)
관련도는 `EligibilityMatcher.match(businessInfo, policyCard)` — 순수 함수로, **사업정보(지역/업종/사업상태/매출/직원수)와 정책 카드 데이터에만 의존**. 따라서:

```sql
SELECT u.email, bi.job_category, bi.region, bi.business_status, bi.annual_revenue, bi.employee_count
FROM business_info bi
JOIN users u ON bi.user_id = u.id
WHERE u.email IN ('<러너비 계정 이메일>', '<러너비2 계정 이메일>');
```

이 쿼리부터 돌려서 **두 계정의 사업정보가 실제로 다른지** 확인할 것.

- **사업정보가 동일하다면 → 버그 아님.** 순수 함수라 같은 입력에 같은 출력은 정상. 이 경우 작업 로그에 "정상 동작 확인됨"이라고 기록하고 종료.
- **사업정보가 다른데 점수가 동일하다면 → 진짜 버그.** 아래 순서로 원인 추적:
  1. `PolicyController.getPolicies()`의 `Authentication authentication`에서 `authentication.getPrincipal() instanceof Long id` 캐스팅이 계정별로 실제로 다른 `userId`를 반환하는지 로그 찍어서 확인
  2. `PolicyService.getPolicies()`의 `businessInfoRepository.findByUserId(userId)`가 실제로 계정별 다른 `BusinessInfo`를 반환하는지 확인
  3. 프론트 `src/lib/api.ts`의 axios 인터셉터가 계정 전환 시 **새 토큰으로 제대로 갱신**되는지 확인 (로그아웃 시 `localStorage.removeItem("access_token")` 되는지, 로그인 시 새 토큰이 덮어써지는지 — 브라우저 새로고침 없이 계정만 바꿨을 때 stale 토큰이 남아있을 가능성 포함)
  4. 브라우저 캐시/CDN 캐시 여부 (Nginx 설정에 `GET /api/v1/policies`가 캐싱되고 있지는 않은지)

---

## 추가 지시: 관련 로직 전체 감사

위 두 문제를 고치는 김에, 아래 파일들을 다시 리뷰하면서 **추가로 발견되는 문제가 있으면 즉시 분석하고 수정**할 것. 특히:

- `EligibilityMatcher.java`, `RegionHierarchy.java` — 지역/업종 매칭 로직에 놓친 엣지케이스 있는지
- `PolicyService.java`, `PolicyReportService.java` — `PolicyCard` 조회(`PolicyCard.toPolicyId()`)가 모든 케이스에서 정상 동작하는지, N+1 쿼리 문제는 없는지 (페이지당 최대 12개 정책 × 매번 카드 조회 — 성능 이슈 가능성)
- `OpenAiClient.java` — 에러 핸들링이 충분한지 (타임아웃, rate limit 등 실제 데모 중 발생 가능한 케이스)
- 관련도 점수가 특정 구간(현재 40~60%대)에 몰리는 현상 자체는 **알려진 한계**(goalScore/documentScore가 데이터 부족으로 중립값 50 고정)이므로 이번 작업 범위 아님 — 손대지 말 것

---

## 컨벤션 (반드시 준수)
- `ErrorCode` 생성자: `(String code, int status, String message)`
- 엔티티 상태 변경: 세터 금지, 비즈니스 메서드로만 (`update()`, `softDelete()` 같은 패턴)
- API 응답: `{ success, data, message, error }` 형식, `ApiResponse.success()` 사용
- userId 추출: `(Long) authentication.getPrincipal()`
- 토큰 필드만 snake_case (`access_token`), 나머지 응답 필드는 camelCase
- `ddl-auto=update`: 필드 추가는 자동 반영되지만 컬럼 삭제/타입 변경은 수동 SQL 필요 (마이그레이션 필요하면 명시할 것)
- 프론트: `src/lib/api.ts`의 `api` 인스턴스 사용 (JWT 자동 첨부), 에러 시 `alert(err.response?.data?.message || "...")` 패턴 (Login.tsx 참고)

## Git 워크플로우
- 브랜치: `fix/report-persistence-and-relevance-audit` (또는 문제별로 분리해도 됨)
- 커밋 메시지: 한글, `fix: ...` 또는 `feat: ...` 형식, 무엇을·왜 고쳤는지 명확히
- 작업 완료 후 **push까지**. 가능하면 PR 생성(리뷰어: jaeseong09)까지 진행.
- **main 브랜치에 직접 머지하지 말 것** — 팀 리뷰 프로세스 유지

## 완료 조건 (Definition of Done)
- [ ] 서로 다른 사업정보를 가진 두 계정에서 리포트를 각각 생성했을 때, 각 계정의 `/reports`에는 **자기가 만든 리포트만** 보임
- [ ] 문제 2 진단 결과가 작업 로그에 명시됨 (버그였는지 아닌지 + 근거)
- [ ] 문제 2가 진짜 버그였다면 수정 완료, 서로 다른 사업정보의 두 계정에서 관련도가 다르게 나옴을 확인
- [ ] 기존 테스트(`EligibilityMatcherTest` 등) 전부 통과
- [ ] 로컬에서 `./gradlew bootRun` 정상 기동 확인 (컴파일 에러 없음), 프론트는 `npx tsc -b` 통과 확인
- [ ] 위 내용대로 커밋 + push 완료

## 작업 로그
> Claude Code가 작업하면서 발견한 문제, 진단 결과, 수정 내역을 아래에 시간순으로 기록할 것.

### 2026-07-01 — 브랜치 `fix/report-persistence-and-relevance-audit`

**[문제 2 진단] 결론: 코드 버그 아님 (userId/인증 경로 정상). 관련도 동일은 PolicyCard 데이터 공백의 결과.**
- 지시된 SQL은 프로덕션 DB(EC2)라 이 개발 환경에서 직접 조회 불가 → 정적 코드 분석으로 진단.
- `EligibilityMatcher.match(businessInfo, card)`는 사용자 사업정보를 실제로 읽는 항목(region/industry/revenue/employees/business_stage)이 **PolicyCard.fullJson에 해당 조건이 있을 때만** 사용자 값과 비교한다. 카드가 비어 있으면(현 프로덕션 상태 — 엔티티 주석 "데이터는 배포 후 extractor가 채움", 작업지시서 line 81 "데이터 부족") 모든 사용자 의존 항목이 사용자 값을 **읽지도 않고** NOT_REQUIRED/UNKNOWN으로 처리된다.
  - 빈 카드 기준 점수: eligibility=66 → `round(66*0.7 + 50*0.2 + 50*0.1) = 61`. 사업정보가 달라도 **모든 사용자·모든 정책이 동일하게 61** (테스트 `noEligibilityData_allUnknown`와 일치).
- 인증/스코프 경로는 정상 확인:
  - `JwtAuthenticationFilter`가 principal=userId(Long)로 세팅 → `PolicyController`에서 계정별 다른 userId 추출 → `PolicyService`가 `businessInfoRepository.findByUserId(userId)`로 계정별 사업정보 조회. 계정 간 혼선 없음.
  - 토큰 stale 우려(진단절차 3): `Login.tsx`/`Signup.tsx`가 로그인 시 `localStorage.setItem("access_token", ...)`로 **덮어쓰고**, `api.ts` 인터셉터가 매 요청마다 localStorage에서 다시 읽음 → 계정 전환 시 이전 토큰이 남지 않음. 버그 없음.
- 따라서 이건 카드 데이터 sparse로 인한 **알려진 한계(작업지시서 line 81, 범위 외)**의 증상이며 관련도 매처는 손대지 않음. 카드 데이터가 채워지면 사용자별로 점수가 갈린다.

**[문제 1 수정] 리포트 localStorage → 백엔드 DB(user_id 스코프)로 이관 완료.**
- 백엔드
  - 신규 `Report` 엔티티(`report` 테이블, `(user_id, policy_id)` 유니크). details/relatedIds/businessImpact는 JSON 컬럼으로 보관(PolicyCard.fullJson 패턴). 상태 변경은 `update()` 비즈니스 메서드로만.
  - `ReportRepository`: `findByUserIdOrderByUpdatedAtDesc`, `findByUserIdAndPolicyId`, `deleteByUserIdAndPolicyId`.
  - `PolicyReportService.generate()`가 OpenAI 결과를 **user_id 스코프로 upsert** 후 반환(기존 프론트 `saveReport`의 replace/unshift 시맨틱 유지). `getReports/getReport/deleteReport` 추가.
  - 신규 `ReportController`: `GET /api/v1/reports`, `GET /api/v1/reports/{policyId}`, `DELETE /api/v1/reports/{policyId}`. 전부 `anyRequest().hasRole("ACCESS")`로 인증 필요(SecurityConfig 무수정).
  - `PolicyReportResponse`에 `savedAt` 추가.
- 프론트
  - `data/reports.ts`의 localStorage 함수를 axios API 호출로 교체(`getSavedReports`/`getSavedReport`/`deleteReport`, async).
  - `ReportList`(로딩/에러 상태 추가), `ReportDetail`(async 조회+삭제), `PolicyDetail`(생성=POST가 DB 저장까지 처리, 기존 여부는 백엔드 조회로 판정, 불필요해진 `saveReport` 제거).
- 런타임 검증(로컬 MySQL + JWT 2개, user 9001/9002):
  - 9001은 자기 리포트만, 9002는 자기 리포트만 조회됨. 타 계정 리포트 GET/DELETE는 404이고 실제로 삭제 안 됨. 자기 리포트 DELETE는 200. **완료 조건 #1 충족.**

**[추가 감사 — 발견 및 수정]**
- N+1 쿼리(`PolicyService.getPolicies`): 페이지당 정책 수만큼 `findByPolicyId`를 반복 호출하던 것을 `findByPolicyIdIn` 한 번(IN 쿼리) + policyId→card 맵으로 변경.
- `RegionHierarchy` 잠재 오매칭 버그: 구/군 이름이 여러 시/도에 중복 존재(중구·남구·동구·서구·북구·강서구·고성군 등)하는데 단일 값 `Map<String,String>`이라 마지막 put만 살아남음 → 예) 서울 강서구 정책이 부산으로 매핑돼 서울 사용자와 **오답 FAIL(hardFail)**. `Map<String,Set<String>>`으로 바꿔 "교집합이 하나라도 있으면 매칭"으로 수정(오탐 FAIL 방지). 카드 데이터가 비어 있는 현재는 dormant지만 데이터 적재 시 발현될 버그. 기존 매처 테스트(정식 시/도명 사용) 그대로 통과.
- `OpenAiClient`: 검토 결과 충분(connect/read 타임아웃 설정, catch-all → `AI_SERVICE_UNAVAILABLE` 503). 데모 중 타임아웃/rate limit도 503으로 안전하게 폴백됨. 미수정.

**[검증]**
- `EligibilityMatcherTest` 4개 전부 통과(`--rerun-tasks`).
- `./gradlew bootRun` 정상 기동(`report` 테이블 자동 생성 확인, ddl-auto=update — 추가 마이그레이션 불필요).
- 프론트 `npx tsc -b` 통과.
