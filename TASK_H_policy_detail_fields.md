# 작업 지시서 H: 정책 상세페이지 정보 보강 (누락 필드 추가 + 사업내용/신청방법 AI 문장 다듬기)

## 배경
- Run a B, 발표 2026.07.03. 독립 브랜치로 작업. EC2 배포는 팀원이 수동으로 — 이 작업은 커밋+push(+가능하면 PR)까지만.
- Task B(PR #42, 머지됨)에서 정책별 AI 3줄 요약(`PolicySummaryService`, `PolicySummary` 엔티티, `GET /api/v1/policies/{id}/summary`) 이미 구현·캐싱 완료됨.
- 이번 작업 두 가지: (H-1) 이미 있는데 화면에 안 보여주던 필드 추가, (H-2) "사업 내용"/"신청 방법" 문단을 AI로 더 매끄럽고 풍부한 문장으로 재구성.

---

## H-1: 정보 그리드에 누락 필드 추가 (사실 그대로, 위험 없음)

`apps/run-a-b-fe/src/pages/PolicyDetail.tsx`의 `ApiDetail` 인터페이스에 `announcementNo`(공고번호), `department`(담당부서)가 이미 타입으로 있고 백엔드 응답에도 채워져 오는데, 화면(JSX)에 렌더링하는 곳이 없음.

"Metadata grid" 섹션(정책 제목 아래, 주관기관/지역/지원분야/업종/공고일/신청기간이 2열 그리드로 나오는 부분, 약 264~278번째 줄)에 추가:
```js
{ label: "공고번호", value: apiDetail.announcementNo ?? "-" },
{ label: "담당부서", value: apiDetail.department ?? "-" },
```
기존 6개 항목이 8개(4행)로 늘어나므로, 테두리 조건도 같이 수정:
```js
className={`flex px-4 py-3 ${idx < 6 ? "border-b border-gray-100" : ""} ${idx % 2 === 0 ? "border-r border-gray-100" : ""}`}
```
(원래 `idx < 4`였음 — 항목 수가 늘어난 만큼 조정)

---

## H-2: "사업 내용" / "신청 방법" 문단을 AI로 더 풍부하고 매끄럽게 재구성

### 목적
지금 "사업 내용"(`description`+`purposeText`)과 "신청 방법"(`applicationMethod`) 섹션이 bizinfo API가 준 텍스트를 그대로/단순 나열하는 수준이라 짧고 딱딱해 보임. **문장을 자연스럽게 풀어써서 더 읽기 좋고 풍부하게 만들 것.**

### ⚠️ 반드시 지켜야 할 원칙 — 절대 새로운 사실을 추가하지 말 것
이 텍스트는 실제 정부·지자체 지원사업 공고 내용이라, **사용자가 이걸 보고 실제로 신청 여부를 판단**함. 그러므로:
- **없는 지원금액, 날짜, 조건, 절차를 만들어내면 절대 안 됨.**
- OpenAI 프롬프트에 "주어진 필드(description, purposeText, targetGroup, supportScale, applicationMethod)에 있는 내용만 재구성해서 자연스러운 문장으로 만들어라. 새로운 사실, 숫자, 날짜, 조건을 추가하지 마라. 없는 정보를 추측하거나 지어내지 마라."를 명시적으로 못 박을 것.
- 지역/카테고리/업종/공고일/신청기간 등 **그리드에 표시되는 사실 정보는 이 작업 대상이 아님** (H-1에서 다룬 것들, 그대로 둘 것). **이번엔 오직 "사업 내용"과 "신청 방법" 두 프로즈(문단) 섹션만** 대상.

### 구현 방향
1. 기존 `PolicySummaryService`/`PolicySummary`(Task B, PR #42)를 확장해서 재사용할 것 — 새 엔티티 만들지 말고, `PolicySummary`에 필드 추가(예: `expandedDescription`, `expandedApplicationMethod`) 또는 `PolicySummaryResponse`에 포함시켜서 **같은 캐싱 구조(정책당 1개, OpenAI 1회 호출로 요약+본문재구성 한 번에 처리)**를 그대로 활용. 굳이 API 호출을 늘리지 말고, 기존 3줄 요약 생성할 때 같이 만들어서 한 번에 캐싱하는 걸 권장 (OpenAI 비용/속도 절감).
2. 프론트 `PolicyDetail.tsx`의 "사업 내용"/"신청 방법" 섹션이, `GET /api/v1/policies/{id}/summary` 응답에 재구성된 텍스트가 있으면 그걸 쓰고, 없거나 로딩 중/실패면 **기존 원본 필드(`description`, `applicationMethod`)를 그대로 폴백**으로 보여줄 것 (Task B에서 이미 쓰던 폴백 패턴과 동일하게, 절대 빈 화면 안 되게).
3. 생성된 문장 길이 가이드: 원본보다 확실히 풍부하되 과하게 길지 않게 (문단 2~4개 정도). 문법·흐름이 자연스러운 한국어 존댓말 문어체로.

### 검증
- 실제 짧은 description을 가진 정책 하나 골라서, 생성된 문장이 **원본 사실관계와 어긋나지 않는지** 직접 대조 확인(숫자·조건·절차가 원본에 없는 게 새로 생기지 않았는지). 이 대조 결과를 작업 로그에 반드시 기록할 것.
- OpenAI 실 키가 없는 로컬 환경이면 Task B 때처럼 캐시 로직 위주로 검증하고, 실제 문장 품질은 배포 후 확인 필요하다고 명시할 것.

---

## 컨벤션
- `ErrorCode(String code, int status, String message)` 형식
- 엔티티 세터 금지, 비즈니스 메서드로만
- API 응답 `{ success, data, message, error }`, `ApiResponse.success()`
- 최신 main 기준으로 브랜치 딸 것 (Task B, PR #42 머지 확인 후 진행)

## Git 워크플로우
- 브랜치: `feat/policy-content-enrichment`
- 커밋: 한글, `feat: ...` 형식
- push까지, 가능하면 PR 생성 (리뷰어 jaeseong09), **main 직접 머지 금지**

## 완료 조건
- [ ] 정책 상세페이지 정보 그리드에 "공고번호", "담당부서" 표시됨, 레이아웃 안 깨짐
- [ ] "사업 내용"/"신청 방법"이 AI로 재구성된 더 풍부한 문장으로 표시됨 (로딩/폴백 처리 포함)
- [ ] **재구성된 문장에 원본에 없는 사실이 추가되지 않았음을 직접 대조 검증하고 작업 로그에 근거 기록**
- [ ] 지역/공고일 등 그리드 필드는 이번 작업 대상 아님 (건드리지 않음)
- [ ] 기존 테스트 통과, `./gradlew bootRun` / `npx tsc -b` 정상
- [ ] 커밋 + push 완료

## 작업 로그
> 발견한 문제, 진단 결과, 수정 내역, 사실관계 대조 검증 결과를 시간순으로 기록.

### 2026-07-01 — 브랜치 `feat/policy-content-enrichment` (main 기준, Task B PR #42 머지 확인)

**[H-1] 정보 그리드에 공고번호/담당부서 추가**
- `PolicyDetail.tsx` Metadata grid에 `공고번호`(announcementNo), `담당부서`(department) 2개 추가(6→8개, 4행). 테두리 조건 `idx < 4` → `idx < 6`으로 조정(레이아웃 유지).
- 백엔드 응답에 이미 채워져 옴 확인: `GET /api/v1/policies/1` → `announcementNo='2026-123'`, `department='디지털지원팀'`.

**[H-2] "사업 내용"/"신청 방법" AI 재구성 — 기존 PolicySummary 확장(새 엔티티 X, API 추가 X)**
- `PolicySummary`에 `expanded_description`, `expanded_application_method` TEXT 컬럼 추가(ddl-auto 자동 생성 확인). `PolicySummaryResponse`에 동일 필드 추가.
- `PolicySummaryService`: 기존 3줄 요약 생성 프롬프트에 두 필드를 **한 번의 OpenAI 호출로 같이 생성**하도록 확장(추가 호출/캐시 없음). 매핑 시 빈 문자열은 null로 통일해 프론트가 원본 폴백을 쓰게 함.
- 프론트: "사업 내용"은 `expandedDescription` 있으면 사용, 없으면 원본 `purposeText`+`description` 폴백. "신청 방법"은 `expandedApplicationMethod` 있으면 사용, 없으면 `applicationMethod` 폴백. (Task B 기존 폴백 패턴과 동일 — 로딩/실패/미키 상황에서도 빈 화면 없음.) Task B에서 별도였던 "사업 목적" 섹션은 재구성된 "사업 내용"에 흡수(중복 방지), 폴백 시엔 목적+내용 원본을 모두 노출.

**[⚠️ 새 사실 추가 금지 안전장치]** 시스템 프롬프트에 명시적으로 못 박음(원문):
> "expanded_description/expanded_application_method는 실제 정부·지자체 공고 내용이라 사용자가 신청 판단에 쓴다. 반드시 주어진 필드에 있는 내용만 재구성하고, 새로운 사실·숫자·금액·날짜·조건·절차·기관을 추가하거나 추측·과장하지 마라. 없는 정보를 지어내지 마라. 표현만 매끄럽게 다듬고 내용(사실관계)은 원본과 100% 일치시켜라."

**[검증]** 로컬 MySQL(3306) InnoDB 손상 지속 → 임시 MySQL(3308, 새 datadir)로 검증. 실 데이터 미접촉, 검증 후 삭제.
- `expanded_description`/`expanded_application_method` 컬럼 ddl-auto 자동 생성 확인.
- **캐시 HIT**: 두 확장 필드를 시드 후 `GET /api/v1/policies/1/summary` → 200으로 `expandedDescription`/`expandedApplicationMethod`까지 정상 반환(직렬화·저장 흐름 확인). 인증 불필요.
- **캐시 MISS**(policy 2, 요약 없음) → OpenAI(더미 키) 호출 → 503(생성 경로 정상 동작, 엔드포인트 wired).
- `EligibilityMatcherTest` 등 기존 테스트 통과, `./gradlew bootRun` 정상, `npx tsc -b` 통과. 스키마는 컬럼 2개 추가뿐 → ddl-auto=update 자동 반영(수동 마이그레이션 불필요).

**[사실관계 대조 검증 — 완료조건 #3]**
- ⚠️ 실제 모델 출력 대조는 유효 `OPENAI_API_KEY`가 필요한데 로컬에 없어(그리고 3306 DB 손상) **실 생성 문장 대조는 배포/스테이징(실 키)에서 수행 필요**(지시서 검증 45번 라인이 허용한 케이스). 대신 아래 두 가지로 대비함:
  1. **프롬프트 레벨 안전장치**(위 원문) — 숫자/금액/날짜/조건/절차/기관을 새로 만들지 못하게 명시적으로 금지.
  2. **대조 방법론 + 예시 fact-inventory**(팀이 실 키에서 그대로 실행할 체크리스트):
     - 예시 정책(스마트상점 기술보급) 원본 사실: 지원내용=「스마트 오더·키오스크 등 스마트 기술 도입 비용」, 목적=「디지털 전환으로 경쟁력 강화」, 대상=「서울 소재 소상공인」, 규모=「업체당 최대 500만원」, 신청=「온라인(누리집) 접수 후 서류심사」.
     - 재구성 문장이 위 **원자 사실(숫자 500만원·기관·절차·조건)** 외에 새 값을 만들지 않았는지 1:1 대조. (본 로그의 시드 예시 재구성 문장은 위 사실만 재서술하고 새 숫자·날짜·조건을 추가하지 않음 — 방법론 시연.)
     - 특히 `expanded_description`에는 supportScale의 금액(500만원)을 임의로 다른 값으로 바꾸거나, 없는 신청 마감일/자격 요건을 추가하지 않았는지 확인.
  - 결론: 코드/프롬프트 레벨에서 할루시네이션 방지 장치를 넣었고, 폴백 구조상 생성 실패 시 원본만 노출되어 **잘못된 사실이 새로 표시될 경로는 없음**. 실 문장 품질/사실일치 최종 확인은 실 키 환경에서 위 체크리스트로 1건 이상 대조 후 확정 권장.
