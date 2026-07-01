# 작업 지시서 D(개정): "신청 준비하기" 체크리스트 페이지 — 기존 UI 유지, 데이터만 실연결

## 배경
- Run a B, 발표 2026.07.03. 독립 브랜치로 작업. EC2 배포는 팀원이 수동으로 — 이 작업은 커밋+push(+가능하면 PR)까지만.
- **가장 중요한 원칙: `apps/run-a-b-fe/src/pages/PolicyChecklist.tsx`의 기존 UI(위재성이 만든 디자인 — 진행률 원형 그래프, 필수/선택 서류 체크리스트, 팁 박스, "신청 페이지로 이동" CTA 버튼 등)를 절대 새로 만들거나 갈아엎지 말 것. 이 UI 컴포넌트/스타일은 그대로 두고, 지금 mock 데이터(`MOCK_POLICIES`, `POLICY_DETAILS`)로만 채워지던 부분을 실제 API 데이터로 교체하는 작업.**

## 현재 상태 (확인됨)
`PolicyChecklist.tsx`가 `MOCK_POLICIES.find(...)`, `POLICY_DETAILS[policyId]`로 데이터를 찾는데, 실제 동기화된 정책(거의 전부)은 이 mock 데이터에 없어서 아래 분기로 빠져 "신청 체크리스트 준비 중이에요"라는 안내 화면만 보여줌(44번째 줄 `if (!mockPolicy || !mockDetail)`):
```jsx
if (!mockPolicy || !mockDetail) {
  // "준비 중" 안내만 표시, 실제 체크리스트 UI 진입 불가
}
```

## Mock 데이터 구조 (그대로 맞춰서 API를 설계할 것)
`POLICY_DETAILS[policyId]`가 갖고 있는 형태:
```ts
{
  applicationPeriod: string,        // 예: "2026.03.01 ~ 2026.04.30"
  department: string,               // 담당 부서/기관
  applicationUrl: string,           // 신청 페이지 URL
  applicationChecklist: {
    id: string,
    label: string,                  // 서류명
    required: boolean,              // 필수 여부
    description?: string,           // 부가 설명
  }[],
}
```
`policy.category`(뱃지 색상용)는 `MOCK_POLICIES`에서 옴 — 실 데이터는 이미 `PolicyDetailResponse.category`로 있음.

**목표: 새 백엔드 엔드포인트가 정확히 이 모양(shape)의 JSON을 반환하도록 만들어서, 프론트에서는 `mockPolicy`/`mockDetail`을 API 응답으로 교체하는 정도의 최소 변경만 하면 되게 할 것.** UI(JSX, 스타일, 인터랙션 로직인 `toggle`, `requiredChecked`, `allRequiredDone`, 진행률 원형 그래프 계산 등)는 전혀 손대지 않아도 되는 구조로 설계.

---

## 백엔드: `applicationChecklist` 데이터 소스 — AI 자유생성 금지, 조립 방식

지원사업 신청 서류 안내는 틀리면 실제 사용자에게 피해를 주는 정보이므로, **AI가 서류를 지어내지 않도록** 아래 우선순위로 조립:

1. **공통 서류(정적 템플릿)**: 대부분의 소상공인/중소기업 지원사업에서 공통적으로 요구되는 서류를 하드코딩된 기본 항목으로 제공 — 사업자등록증, 신청서(양식은 공고문에서 확인), 사업계획서, 통장사본, 개인정보 수집·이용 동의서 등. `required: true`로 설정.
2. **정책별 특이사항(데이터 기반, AI 아님)**: `PolicyCard.fullJson`의 `eligibility.required_flags`, `eligibility.documents`(배윤성 extractor가 채운 필드, `EligibilityMatcher`에서도 참조 중)가 있으면 그 항목들을 추가 (`required: true` 또는 상황에 따라 `false`, `description`에 "공고문 자격요건에서 추출됨" 정도로 출처 표기).
3. **`Policy.applicationMethod`**를 `applicationPeriod`/신청방법 관련 필드로 매핑.

### 신규 엔드포인트
`GET /api/v1/policies/{id}/checklist` — 인증 불필요(`GET /api/v1/policies/**` permitAll 패턴에 이미 포함).

응답 예시 (mock 구조와 최대한 동일하게):
```json
{
  "applicationPeriod": "2026.06.24 ~ 2026.07.14",
  "department": "충청북도",
  "applicationUrl": "https://...",
  "applicationChecklist": [
    { "id": "biz_reg", "label": "사업자등록증", "required": true, "description": "최근 3개월 이내 발급본" },
    { "id": "biz_plan", "label": "사업계획서", "required": true },
    { "id": "bank_copy", "label": "통장사본", "required": true },
    { "id": "flag_xxx", "label": "...", "required": true, "description": "공고문 자격요건에서 추출됨" }
  ]
}
```
- `PolicyCard`가 없거나 `required_flags`가 비어있어도 공통 서류(1번)는 항상 나와야 함 — 절대 빈 리스트가 되면 안 됨.
- `applicationUrl`은 `Policy.applicationUrl` 없으면 `detailUrl`로 폴백.

---

## 프론트: mock 대신 API 연결 (최소 변경)

`PolicyChecklist.tsx`에서:
1. `MOCK_POLICIES.find(...)`, `POLICY_DETAILS[policyId]` 대신, `GET /api/v1/policies/{id}`(정책 기본 정보 — 이미 있는 엔드포인트)와 `GET /api/v1/policies/{id}/checklist`(신규) 두 개를 호출해서 각각 `policy`, `detail` 변수에 대입.
2. 44번째 줄의 `if (!mockPolicy || !mockDetail)` 분기 — API 로딩 실패/404인 경우에만 기존 "준비 중" 화면으로 폴백, **API가 정상 응답하면(대부분의 경우) 기존 체크리스트 UI(98번째 줄 이후)를 그대로 사용.**
3. **98번째 줄부터 끝까지(체크리스트 UI 전체)는 변수명(`policy`, `detail`)만 API 응답으로 채워지면 되도록 최대한 그대로 유지.** 새 컴포넌트 만들지 말 것.
4. `CATEGORY_COLORS` 매핑(6~16번째 줄)에 실제 카테고리 값("기술","경영","수출" 등, Task A에서 이미 정리된 카테고리 목록)이 빠져있으면 추가할 것 — 지금 매핑은 mock 전용 카테고리("최저임금", "노동·복지" 등)라 실 데이터 카테고리와 안 맞을 수 있음, 기본값(`bg-gray-100 text-gray-600`)으로는 항상 폴백되니 깨지진 않지만 실 카테고리에 맞는 색상 매핑도 보강할 것.

---

## 안내 문구(disclaimer)

기존 UI의 "신청 전 확인 사항" 팁 박스(170~185번째 줄)에 이미 안내 문구 자리가 있음 — 여기에 **"정확한 제출 서류는 반드시 원문 공고를 확인하세요"** 문구를 자연스럽게 추가할 것 (기존 3개 항목에 하나 더 추가하는 정도, UI 구조 안 바꿈).

---

## 컨벤션
- `ErrorCode(String code, int status, String message)` 형식
- 엔티티 세터 금지, 비즈니스 메서드로만
- API 응답 `{ success, data, message, error }`, `ApiResponse.success()`
- 최신 main 기준으로 브랜치 딸 것

## Git 워크플로우
- 브랜치: `feat/application-checklist`
- 커밋: 한글, `feat: ...` 형식
- push까지, 가능하면 PR 생성 (리뷰어 jaeseong09), **main 직접 머지 금지**

## 완료 조건
- [ ] 실제 동기화된 정책(mock 아닌 것) 클릭 → "신청 준비하기" → **기존 디자인 그대로**(진행률 원형그래프, 필수/선택 서류 체크리스트, CTA 버튼) 실제 데이터로 표시됨
- [ ] 체크박스 클릭 시 진행률 원형그래프·CTA 버튼 활성화 등 기존 인터랙션이 그대로 작동함 (UI 로직 안 건드렸으니 자동으로 되어야 함)
- [ ] `PolicyCard` 데이터가 비어있는 정책이어도 공통 서류는 항상 표시됨(빈 리스트 없음)
- [ ] 팁 박스에 "원문 공고 확인" disclaimer 문구 추가됨
- [ ] mock 데이터(`MOCK_POLICIES`)가 여전히 존재하는 정책은 기존처럼 mock으로도 잘 작동함(회귀 없음) — 이번 변경이 mock 경로를 깨지 않는지 확인
- [ ] 기존 테스트 통과, `./gradlew bootRun` / `npx tsc -b` 정상
- [ ] 커밋 + push 완료

## 작업 로그
> 발견한 문제, 진단 결과, 수정 내역을 시간순으로 기록.

### 2026-07-01 — 브랜치 `feat/application-checklist` (최신 main 기준)

**[백엔드] 체크리스트 조립 API 신규 (AI 자유생성 없음)**
- `PolicyChecklistResponse` DTO: 프론트 mock `POLICY_DETAILS[policyId]` 구조(applicationPeriod/department/applicationUrl/applicationChecklist[{id,label,required,description}])와 1:1.
- `PolicyChecklistService.getChecklist(policyId)`:
  - (1) **공통 서류 정적 템플릿 5개**(사업자등록증/신청서/사업계획서/통장 사본/개인정보 동의서, 전부 required) — 카드 없어도 항상 반환(빈 리스트 불가).
  - (2) `PolicyCard.fullJson`의 `eligibility.documents` + `required_flags`가 있으면 항목 추가(description="공고문 자격요건에서 추출됨"). 공통 서류와 라벨 중복은 정규화 비교로 제거.
  - applicationPeriod = start~end(없으면 "상시 / 공고문 참조"), department = department→agency→region 폴백, applicationUrl = applicationUrl→detailUrl 폴백.
- `GET /api/v1/policies/{id}/checklist` (PolicyController) — 인증 불필요(기존 permitAll 포함), 없는 정책은 404.

**[프론트] mock → API 최소 교체 (기존 UI/인터랙션 무변경)**
- 기존 위재성 체크리스트 UI(진행률 원형그래프, 필수/선택 서류, 팁 박스, CTA)는 그대로. `policy`/`detail` 변수 채우는 데이터 소스만 교체.
- **회귀 방지**: `mockPolicy && mockDetail`이면 기존처럼 mock 사용(API 호출 안 함). 그 외에는 `GET /policies/{id}` + `GET /policies/{id}/checklist` 병렬 호출로 채움. 둘 다 없거나 실패/404면 기존 "준비 중" 화면 폴백.
- `CATEGORY_COLORS`에 실 카테고리(기술/금융/인력/경영/창업/수출/내수/기타) 색상 매핑 보강(기존 mock 카테고리도 유지).
- 팁 박스에 disclaimer "• 정확한 제출 서류는 반드시 원문 공고를 확인하세요." 추가(4번째 항목, 구조 무변경).
- `ChecklistRow`, `toggle`, `requiredChecked`, `allRequiredDone`, 진행률 계산 등 UI 로직 전혀 손대지 않음.

**[검증]** 로컬 MySQL(3306) InnoDB 손상 지속 → 임시 MySQL(3311)로 검증. 실 데이터 미접촉, 검증 후 삭제.
- policy 1(카드에 documents 2개+required_flags 1개): 응답 = **공통 5 + 카드 documents 2 = 7항목**. `required_flags`의 "사업자등록증"은 공통과 중복이라 **정상 제거됨**. applicationPeriod "2026.06.24 ~ 2026.07.14", department="디지털산업과", applicationUrl=신청URL, 카드 항목 description="공고문 자격요건에서 추출됨" 확인.
- policy 2(카드 없음): **공통 5항목만**(빈 리스트 아님), applicationPeriod="상시 / 공고문 참조", department=agency 폴백("중소벤처기업부"), applicationUrl=detailUrl 폴백 확인.
- 없는 정책(999) → 404(프론트 준비중 UI 폴백).
- 인증 없이 200(permitAll). item id 전부 유니크(React key 안전).
- `EligibilityMatcherTest` 등 기존 테스트 통과, `bootRun` 정상, `npx tsc -b` 통과. 스키마 변경 없음(신규 테이블/컬럼 없음, 기존 데이터 조립만).
- mock 정책 회귀: 프론트에서 `mockPolicy && mockDetail` 우선 분기라 mock 존재 정책은 API 호출 없이 기존 경로 그대로 → 회귀 없음.
