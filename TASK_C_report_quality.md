# 작업 지시서 C(개정): AI 리포트 품질 개선

## 배경
- Run a B, 발표 2026.07.03. 독립 브랜치로 작업. EC2 배포는 팀원이 수동으로 — 이 작업은 커밋+push(+가능하면 PR)까지만.
- **작업 시작 전 반드시 최신 main을 pull 받을 것** — 팀원(위재성)이 메인 페이지 관련 별도 PR을 진행 중이라 그 사이 main이 갱신될 수 있음.
- 관련 기존 코드: `api/src/main/java/com/runab/api/service/report/PolicyReportService.java`, `PolicyReportResponse.java`, `ReportController.java`, `Report.java`, 프론트 `ReportDetail.tsx`, `ReportList.tsx`, `reports.ts`

---

## C-1: "긍정 영향"/"부정 영향" 필터 버그 (확정 버그)

### 원인
`apps/run-a-b-fe/src/pages/ReportList.tsx`:
```js
if (tab === "부정 영향" && !r.impactLabel.includes("부정")) return false;
if (tab === "긍정 영향" && !r.impactLabel.includes("긍정")) return false;
```
`impactLabel`은 OpenAI가 자유 생성하는 짧은 문구("R&D 지원으로 사업 성장" 등)라 "긍정"/"부정" 글자가 안 들어간 경우가 많아 필터가 제대로 안 걸러짐.

### 수정 방향
1. **백엔드**: `PolicyReportService`가 OpenAI에게 요청하는 스키마의 `overall_direction`을 **`"positive" | "negative"` 둘 중 하나만** 나오도록 프롬프트 수정 (`neutral` 제거). 판단 근거로 관련도 점수(matcher score)와 `business_impact` 항목들의 `up`/`down` 방향 비율도 함께 고려하도록 보강.
2. **DB/응답 스키마**: `Report` 엔티티와 `PolicyReportResponse`에 명시적으로 `direction: "positive"|"negative"` 필드 추가 (프론트가 텍스트 매칭이 아니라 이 필드로 필터링).
3. **프론트**: `ReportList.tsx`의 필터 로직을 `impactLabel.includes(...)` 대신 `report.direction === "positive"` / `"negative"`로 교체. `reports.ts`의 `SavedReport` 타입에도 `direction` 필드 추가.

---

## C-2: 리포트 "상세 분석" 내용을 최대한 풍부하고 깊이 있게 생성

### 요구사항 (강화됨 — 최대한 많이/자세히)
`PolicyReportService.buildSystemPrompt()`를 대폭 보강해서, 아래 관점들을 **각각 충분한 분량(각 항목당 2~3문장 이상)의 별도 문단**으로 생성하도록 요청할 것. 개수 제한을 두지 말고 "가능한 한 상세하게" 분석하도록 지시:

1. 이 정책의 핵심 지원 내용이 사용자 사업(업종·규모·지역)에 **구체적으로 어떻게 적용되는지**
2. 지원 자격 충족 여부에 대한 **판단 근거** — `EligibilityMatcher.match()` 결과(어떤 항목이 pass/fail/unknown인지, `card.getFullJson()`뿐 아니라 매칭 결과 자체)를 프롬프트에 같이 넘겨서 AI가 이걸 근거로 구체적으로 설명하게 할 것
3. 신청 시 예상되는 **경쟁률/난이도**에 대한 코멘트 (지원규모, 지원대상 범위 기반 추정 — 단정적 수치 말고 "~것으로 보임" 식 완곡한 표현 사용)
4. 놓치기 쉬운 **유의사항** (마감일, 신청방법, 필수 서류 등 — `applicationMethod`, `applicationPeriod` 활용)
5. **이 정책과 함께 고려하면 좋은 전략적 관점** (예: 이 정책 하나로 끝내지 말고 관련 정책들과 연계했을 때의 시너지 등, 있는 데이터 안에서 자연스럽게)

**주의**: 분량을 늘리되 Task H 때처럼 **없는 사실을 지어내면 안 됨** — 실제 정책 데이터(`policy_card`, `Policy` 필드, matcher 결과)에 근거해서 "더 깊이 풀어서 설명"하는 것이지, 없는 숫자·조건을 만들어내는 게 아님. 이 원칙은 그대로 유지.

---

## C-3: "내 사업 영향도"는 긍정 영향 리포트에만 표시 + 정량적 수치 명확히

### 변경된 요구사항 (기존 지시서에서 수정됨)
- **`overall_direction`이 `"positive"`인 리포트에만** "내 사업 영향도"(막대그래프 시각화) 섹션을 생성/표시.
- **`"negative"`인 리포트는 이 섹션 자체를 표시하지 않음** — 관련 없거나 부정적인 정책에 억지로 영향도 수치를 붙이는 게 오히려 혼란을 줄 수 있으므로, 부정 영향 리포트는 텍스트 설명(상세 분석)으로만 판단 근거를 전달.
- **백엔드**: `direction`이 `"negative"`일 때는 `business_impact` 배열을 생성하지 않거나 빈 배열로 반환. `"positive"`일 때는 기존처럼 **최소 2개 이상** 항목이 나오도록 프롬프트에서 강제.
- **프론트**: `ReportDetail.tsx`에서 `report.direction === "positive"`일 때만 "내 사업 영향도" 섹션을 렌더링하도록 조건 추가 (현재도 `businessImpact.length > 0`으로 조건부 렌더링 중이나, `direction` 체크를 명시적으로 추가).
- **수치 표현**: 각 `business_impact` 항목의 `level`(0~100, 막대 길이)과 `tag`(예: "+15%")가 실제로 의미 있게 계산되도록 프롬프트에서 요청하되, **이건 AI의 예측/추정치이지 확정된 수치가 아니라는 점을 화면에 명확히 표기**할 것 — `ReportDetail.tsx`의 "내 사업 영향도" 섹션 제목 옆이나 하단에 작은 문구로 "AI가 추정한 예상 영향이에요" 같은 안내를 추가 (사용자가 이 숫자를 확정된 보장치로 오해하지 않도록 — 실제 정부 지원사업의 재정적 효과를 사용자가 과신하지 않게 하는 안전장치).

---

## C-4: "함께 신청하면 좋아요" 관련 정책 배너에 제목 표시

### 현재 상태
`ReportDetail.tsx`에서 관련 정책 버튼이 전부 "관련 정책 보기"라는 고정 텍스트만 있고 실제 정책 제목이 안 보임.

### 수정 방향
- 백엔드 `PolicyReportResponse.relatedIds`를 `List<Long>`(ID만)에서 **`{id, title}` 형태의 객체 리스트**로 변경 (`PolicyReportService.mapToResponse()`에서 `policyRepository.findAllById(relatedIds)`로 제목까지 같이 조회).
- 프론트 `ReportDetail.tsx`의 관련 정책 버튼에 `{relatedPolicy.title}` 표시.
- `Report` 엔티티의 `related_ids`(JSON) 스키마도 `{id, title}` 배열로 맞춤 (기존 저장된 리포트 있으면 하위호환 고려, 없으면 무시해도 됨).

---

## 컨벤션
- `ErrorCode(String code, int status, String message)` 형식
- 엔티티 세터 금지, 비즈니스 메서드로만
- API 응답 `{ success, data, message, error }`, `ApiResponse.success()`

## Git 워크플로우
- 브랜치: `feat/report-quality-improvements`
- 커밋: 한글, `feat: ...` / `fix: ...` 형식
- push까지, 가능하면 PR 생성 (리뷰어 jaeseong09), **main 직접 머지 금지**

## 완료 조건
- [ ] "긍정 영향"/"부정 영향" 탭 클릭 시 실제로 올바르게 필터링됨 (여러 리포트로 검증)
- [ ] 새로 생성되는 리포트의 "상세 분석"이 이전보다 확실히 더 길고 깊이 있음 (문단 수·내용 비교를 작업 로그에 기록)
- [ ] **긍정 영향 리포트에만** "내 사업 영향도" 섹션이 표시되고, **부정 영향 리포트에는 안 보임** (직접 각각 하나씩 생성해서 확인)
- [ ] "내 사업 영향도"에 "AI 추정치" 안내 문구 표시됨
- [ ] 관련 정책 배너에 실제 정책 제목이 표시됨
- [ ] 기존 테스트 통과, `./gradlew bootRun` / `npx tsc -b` 정상
- [ ] 커밋 + push 완료

## 작업 로그
> 발견한 문제, 진단 결과, 수정 내역을 시간순으로 기록.

### 2026-07-01 — 브랜치 `feat/report-quality-improvements` (최신 main pull 후, 위재성 홈페이지 PR #45 포함)

**[C-1] 긍정/부정 필터 버그 수정**
- 백엔드: 프롬프트 `overall_direction`을 **positive|negative 이진**(neutral 제거)으로, 판단 근거에 매칭 점수·pass/fail·business_impact up/down 비율 종합하도록 지시. `mapToResponse`가 direction을 이진화하고 `Report`/`PolicyReportResponse`에 `direction` 필드 추가(응답·DB 저장).
- 프론트: `ReportList` 필터를 `impactLabel.includes("긍정/부정")` → `r.direction === "positive"/"negative"`로 교체. `reports.ts SavedReport`에 `direction` 추가.

**[C-2] 상세 분석 대폭 보강**
- `buildSystemPrompt`를 5개 관점(적용 방식/자격 판단 근거/경쟁률·난이도 완곡표현/유의사항/전략적 연계)을 각 2~3문장+ 별도 문단으로, 개수 제한 없이 "가능한 한 상세히" 생성하도록 재작성. details 예시도 3→5문단으로.
- 자격 판단 근거를 위해 `EligibilityMatcher.match()` 결과(관련도 점수, pass/fail/unknown 항목, hardFail)를 `PolicyReportService`에 주입해 `buildUserPrompt`에 함께 전달. 없는 사실 금지 원칙 명시.
- 이전(details 3문단, 근거 데이터 없음) → 이후(5관점 문단, 매칭 결과 근거 포함)로 분량·깊이 확대.

**[C-3] 사업 영향도 = positive 리포트 전용 + 추정치 안내**
- 백엔드: `mapToResponse`가 direction이 negative면 `business_impact`를 빈 배열로 반환(프롬프트에서도 negative면 [], positive면 최소 2개 강제).
- 프론트: `ReportDetail`의 "내 사업 영향도" 렌더 조건에 `report.direction === "positive"` 명시적 추가. 섹션에 "※ AI가 추정한 예상 영향이에요 (확정 수치 아님)" 안내 문구 추가.

**[C-4] 관련 정책 배너에 제목 표시**
- 백엔드: `PolicyReportResponse.relatedIds`를 `List<Long>` → `List<RelatedPolicy{id,title}>`로 변경. `mapToResponse`가 같은 카테고리 최신 정책(이미 Policy 엔티티)에서 제목까지 매핑(추가 쿼리 없음). `toResponse`는 옛 `[숫자]` 형식이면 파싱 실패→빈 리스트로 하위호환.
- 프론트: 관련 정책 버튼이 고정 "관련 정책 보기" → `{rel.title}` 표시.

**[검증]** 로컬 MySQL(3306) InnoDB 손상 지속 → 임시 MySQL(3310, 새 datadir)로 검증. 실 데이터 미접촉, 검증 후 삭제.
- `report.direction` 컬럼 ddl-auto 자동 생성 확인.
- 리포트 2건 시드(긍정: businessImpact 1개+관련정책 2개 / 부정: businessImpact 없음+관련정책 1개) 후 `GET /api/v1/reports`:
  - `direction` 정상 반환. impactLabel "R&D 지원으로 사업 성장"(긍정 글자 없음)인데도 **direction 기반 필터로 긍정 탭→[100], 부정 탭→[200] 정확 분류**(구 텍스트 매칭이면 실패할 케이스).
  - `relatedIds`가 `[{id,title}]`로 반환(제목 포함) — C-4.
  - businessImpact는 positive에만 존재(부정=0) — C-3 데이터 흐름 + 프론트 direction 게이팅.
- **생성 경로**: policy+businessInfo 시드 후 `POST /policies/100/report` → **503**(500 아님) = 신규 `EligibilityMatcher.match()` 통합 통과 후 OpenAI 단계 도달 확인.
- `EligibilityMatcherTest` 등 기존 테스트 통과, `bootRun` 정상, `npx tsc -b` 통과. 스키마는 `report.direction` 컬럼 추가뿐(ddl-auto 자동, 수동 마이그레이션 불필요).
- ⚠️ **프롬프트 산출물 품질(C-2 분량·깊이 실제 문단 수, C-1 direction 판정 정확도, C-3 negative→빈 businessImpact 생성)은 유효 `OPENAI_API_KEY` 필요 → 로컬 미검증, 배포/스테이징(실 키)에서 확인 필요**. 프롬프트·매핑 로직은 코드로 강제(negative면 백엔드에서 businessImpact 제거, direction 이진화)돼 있어 스키마/필터/게이팅은 키 없이도 보장됨.
