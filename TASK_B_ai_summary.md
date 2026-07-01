# 작업 지시서 B: 정책 상세페이지 - AI 요약 구현 + 콘텐츠 재배치

## 배경
- Run a B, 발표 2026.07.03. 4개 작업지시서(A/B/C/D) 중 B. 독립 브랜치로 작업.
- 배포 사이트 https://run-a-b.com — 가능하면 직접 접속해서 확인.
- EC2 배포는 팀원이 수동으로. 이 작업은 커밋+push(+가능하면 PR)까지만.

홈페이지(`HomePage.tsx`)에 이미 "서비스 주요 기능"으로 **"공고문 AI 3줄 요약 — 수십 페이지 공고문을 핵심만 뽑아 읽기 쉽게 정리해 드려요"**가 광고되어 있는데, 실제로는 구현이 안 되어 있고 정책 상세페이지 우측 상단 "AI 요약" 패널이 계속 "준비 중" 하드코딩 상태임. 이번에 실제로 구현할 것.

---

## 문제 B-1: "AI 요약" 패널이 하드코딩 스텁 (미구현)

### 현재 상태
`apps/run-a-b-fe/src/pages/PolicyDetail.tsx`:
```js
const AI_DEFAULTS = {
  ...
  aiSummaryText: "AI 요약 기능이 준비 중이에요. 곧 이 정책에 대한 맞춤 요약을 제공할 예정입니다.",
  ...
};
```
이 텍스트가 항상 그대로 렌더링됨 — 백엔드 호출 자체가 없음.

### 요구사항
1. **백엔드**: 정책 공고문 내용(`Policy.description`, `purposeText`, `targetGroup`, `supportScale`, `applicationMethod` 등 이미 DB에 있는 필드들)을 바탕으로 OpenAI로 **3줄 요약**을 생성하는 엔드포인트 신규 추가.
   - `PolicyReportService`(`api/src/main/java/com/runab/api/service/report/`) 옆에 유사한 패턴으로 `PolicySummaryService` 신규 작성 — 기존 `OpenAiClient`(`api/src/main/java/com/runab/api/service/ai/OpenAiClient.java`) 재사용
   - 응답 스키마 예: `{ summary_lines: ["...", "...", "..."], highlights: [{icon, label, content}] }` — `PolicyDetail.tsx`의 `aiHighlights`(아이콘: money/check/calendar) 형식과 맞출 것
   - 신규 엔드포인트: `GET /api/v1/policies/{id}/summary` — **인증 불필요**(정책 상세 조회 자체가 비로그인도 가능하므로 동일하게), `SecurityConfig`의 `GET /api/v1/policies/**` permitAll 패턴에 이미 포함되므로 별도 설정 불필요
   - **캐싱 고려**: 같은 정책을 여러 사용자가 볼 때마다 매번 OpenAI 호출하면 비용·속도 낭비. 정책별로 한 번 생성한 요약을 DB에 캐싱하는 구조 권장 (`Policy`에 컬럼 추가하거나 별도 `PolicySummary` 엔티티 — 리포트처럼 user_id 스코프는 필요 없음, 정책 하나당 결과 하나면 됨). `ddl-auto=update`로 필드/테이블 추가는 자동 반영됨.
2. **프론트**: `PolicyDetail.tsx`에서 정책 상세 로드 시(또는 별도 호출로) `GET /api/v1/policies/{id}/summary` 호출해서 `AI_DEFAULTS.aiSummaryText`/`aiHighlights` 자리를 실제 응답으로 교체. 로딩 중엔 스켈레톤 또는 "AI가 요약 중이에요" 표시, 실패 시 현재 문구를 폴백으로 유지(에러로 전체 페이지가 깨지면 안 됨).
   - 우측 상단 "준비 중" 배지(현재 회색 고정)도 로딩 완료되면 없어지거나 "완료" 상태로 바뀌게.

---

## 문제 B-2: "정책 원문" 박스에 실제로는 짧은 요약이 들어가 있고, AI 요약은 따로 없어서 정보가 부실해 보임

### 현재 상태
"사업 내용" 섹션(`정책 원문` 카드 안)이 `apiDetail.description`을 그대로 보여주는데, 이건 bizinfo 동기화 시 채워지는 요약성 필드라 실제 공고 원문만큼 상세하지 않음.

### 요구사항
1. "정책 원문" 카드 영역에는 **DB에 있는 모든 원문성 필드를 최대한 조합**해서 보여주기: `description` + `purposeText`(사업목적) + `targetGroup`(지원대상) + `supportScale`(지원규모) + `applicationMethod`(신청방법) 를 구조화해서 표시 (지금도 일부는 이미 섹션별로 나눠서 보여주고 있음 — `PolicyDetail.tsx` 265번째 줄 근처 확인. 이미 있는 구조를 최대한 활용하고, 빠진 필드 있으면 추가).
   - 이미 "더 자세한 내용은 원문 공고에서 확인" + `detailUrl`/`applicationUrl` 링크가 있으니, 실제 원문 전체를 우리가 대신 보여줄 필요는 없음 — **DB에 있는 필드를 최대한 다 보여주는 것**이 목표.
2. **"AI 요약" 박스**(우측 패널)에는 문제 B-1에서 만든 **진짜 AI 생성 3줄 요약**을 넣기. 즉 "원문 쪽(왼쪽)"은 우리가 가진 구조화 데이터를 최대한 노출, "AI 요약(오른쪽)"은 AI가 압축한 핵심만 — 이렇게 역할을 명확히 분리.

---

## 컨벤션
- `ErrorCode(String code, int status, String message)` 형식
- 엔티티 세터 금지, 비즈니스 메서드로만
- API 응답 `{ success, data, message, error }`, `ApiResponse.success()`
- 프론트: `src/lib/api.ts`의 `api` 인스턴스 사용, 로딩/에러 상태 처리 필수

## Git 워크플로우
- 브랜치: `feat/policy-ai-summary`
- 커밋: 한글, `feat: ...` 형식
- push까지, 가능하면 PR 생성 (리뷰어 jaeseong09), **main 직접 머지 금지**

## 완료 조건
- [ ] 정책 상세페이지 접속 시 "AI 요약" 패널에 실제 생성된 3줄 요약이 뜸 ("준비 중" 문구 사라짐)
- [ ] 같은 정책 재방문 시 캐싱되어 있어 빠르게 뜸 (OpenAI 재호출 안 함)
- [ ] "정책 원문" 카드에 description 외 purposeText/targetGroup/supportScale/applicationMethod 등이 구조화되어 표시됨
- [ ] 기존 테스트 통과, `./gradlew bootRun` / `npx tsc -b` 정상
- [ ] 커밋 + push 완료

## 작업 로그
> 발견한 문제, 진단 결과, 수정 내역을 시간순으로 기록.

### 2026-07-01 — 브랜치 `feat/policy-ai-summary` (main 기준)

**[B-1] AI 요약 백엔드 신규 (OpenAI + 정책별 캐싱)**
- `PolicySummary` 엔티티 신규(`policy_summary` 테이블, `policy_id` 유니크 = 정책당 1개, user 스코프 없음). summaryLines/highlights는 JSON 컬럼(Report·PolicyCard 패턴).
- `PolicySummaryRepository.findByPolicyId`, `PolicySummaryResponse`(summaryLines + highlights{icon,label,content}) 신규.
- `PolicySummaryService.getOrGenerate(policyId)`: **캐시 히트면 OpenAI 미호출로 즉시 반환**, 미스면 정책 필드(title/category/agency/region/targetGroup/supportScale/purposeText/description/applicationMethod/기간)로 프롬프트 구성 → `OpenAiClient.chatJson` → 3줄 요약 + highlights 파싱(icon은 money/check/calendar로 클램프) → DB 캐싱. 동시 첫 조회 유니크 충돌은 catch 후 생성분 반환(트랜잭션은 메서드에 안 걸어 save 실패가 오염 안 되게 함).
- `GET /api/v1/policies/{id}/summary` 엔드포인트 추가 — 인증 불필요(기존 `GET /api/v1/policies/**` permitAll에 포함, SecurityConfig 무수정).

**[B-1 프론트] `PolicyDetail.tsx` AI 요약 패널 실연결**
- 상세 로드와 함께 `GET /api/v1/policies/{id}/summary` 호출(별도 useEffect). 상태: loading(스켈레톤 + "AI가 공고문을 요약하고 있어요") / done(3줄 요약 번호목록 + highlights 카드) / error(기존 "준비 중" 문구 폴백 → 페이지 안 깨짐).
- 우측 배지: 로딩 중 "요약 중", 완료 시 "완료"(primary), 실패 시 "준비 중"(회색). 하드코딩 `AI_DEFAULTS.aiSummaryText`는 에러 폴백으로만 잔존.

**[B-2] "정책 원문" 카드에 원문성 필드 최대 노출**
- 기존엔 description(사업 내용) + applicationMethod(신청 방법) + targetGroup(배지)만 표시.
- 추가: **지원 규모(supportScale)** 강조 박스(💲 아이콘), **사업 목적(purposeText)** 섹션. 이제 description/purposeText/targetGroup/supportScale/applicationMethod가 구조화되어 표시됨. 원문 전체는 기존 detailUrl/applicationUrl 링크로 위임(요구사항대로).
- 역할 분리: 왼쪽(원문 카드)=우리가 가진 구조화 데이터 최대 노출, 오른쪽(AI 요약)=AI가 압축한 3줄 핵심.

**[검증] 로컬 MySQL(3306)은 이전 작업 중 InnoDB 손상으로 사용 불가 → 임시 MySQL(3307, 새 datadir)로 앱 구동 검증. 실 데이터 미접촉, 검증 후 삭제.**
- `policy_summary` 테이블 ddl-auto 자동 생성 확인(policy_id UNI, JSON 컬럼).
- **캐시 MISS**: 요약 없는 정책 GET → OpenAI(더미 키) 호출 시도 → `503 AI_SERVICE_UNAVAILABLE`, **인증 없이 접근됨(permitAll 확인)**, 실패 시 캐시 미저장(rows=0) 확인.
- **캐시 HIT**: `policy_summary` 행 시드 후 GET → **200**, `summaryLines`(3줄) + `highlights`(icon/label/content) 정상 역직렬화 반환, **OpenAI 미호출**(완료조건 "재방문 시 캐싱, 재호출 안 함" 충족). 응답 스키마가 프론트 `PolicySummaryData`와 일치.
- ⚠️ 실제 OpenAI 3줄 생성(캐시 미스→생성→저장 성공 경로)은 유효한 `OPENAI_API_KEY`가 필요해 로컬 미검증. 생성 로직은 검증된 `PolicyReportService`(chatJson→map→save) 패턴과 동일. 실 키가 있는 배포/스테이징에서 최초 조회 시 생성·캐싱됨.
- `EligibilityMatcherTest` 등 기존 테스트 통과, `./gradlew bootRun` 정상 기동(임시 DB), `npx tsc -b` 통과. 스키마는 테이블 추가뿐이라 ddl-auto=update로 자동 반영(수동 마이그레이션 불필요).
