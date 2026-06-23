# 정책 페이지 연동 현황 및 남은 작업

**마지막 업데이트**: 2026-06-23

---

## ✅ 완료된 작업 (프론트 + 백엔드)

| 항목 | 내용 |
|---|---|
| 정책 목록 API 연결 | `GET /api/v1/policies` 실제 연결, 20건/페이지, 스마트 페이지네이션 |
| 필터/검색 | 지역·업종·카테고리·정렬 파라미터 전달, 검색어 디바운스 400ms |
| 정책 상세 API 연결 | `GET /api/v1/policies/{id}` 실제 연결, 404 처리 |
| 상세 데이터 추가 | `PolicyDetailResponse`에 `description`, `date`, `detailUrl` 필드 추가 |
| 원문 공고 링크 | `detailUrl` → 기업마당 원문 페이지 이동 버튼 |
| 카테고리 동기화 | 실제 DB 카테고리 확인 후 `내수`, `기타` 추가 (8종 완성) |
| 스켈레톤 UI | 목록·상세·홈·마이페이지 전반 적용 |
| 홈 최신 정책 | 실제 API 연결, 카드 클릭 시 상세 이동 |
| description 전체 저장 | 1000자 제한 제거, `applicationMethod` TEXT 컬럼으로 변경 후 재동기화 |

---

## 🔧 백엔드에 요청해야 하는 것

### 1. 정책 상세 설명 보강 ⚠️
- **현상**: 상세 페이지의 "사업 내용" 설명이 300~500자 수준의 짧은 요약만 표시됨. 지원 금액, 세부 자격 조건, 심사 기준 등 핵심 정보가 없어 밋밋함
- **원인**: bizinfo API의 `bsnsSumryCn` 필드 자체가 요약 텍스트이고, 나머지 상세 내용은 각 공고의 원문 페이지(`detailUrl`)에만 존재함
- **요청**: 각 정책의 `detailUrl` (기업마당 상세 페이지)을 크롤링하여 다음 필드를 추출·저장
  - `purposeText` — 사업 목적 (현재 항상 null)
  - `supportScale` — 지원 규모/금액 (현재 항상 null)
  - `department` — 담당 부서 (현재 항상 null)
  - `announcementNo` — 공고 번호 (현재 항상 null)
  - `applicationDocuments` — 신청 서류 목록
- **참고**: 해당 필드들은 이미 `Policy` 엔티티와 `PolicyDetailResponse`에 컬럼이 준비되어 있음. 데이터만 채워지면 프론트에서 즉시 표시 가능
- **대안**: 크롤링이 어렵다면 bizinfo API의 상세 조회 엔드포인트(`/uss/siv/bizinfoPblancSiv.do?pblancId=...`) 활용 검토

---

### 2. 업종(industry) 필터 데이터 문제 ⚠️
- **현상**: DB의 모든 정책이 `industry = "전체"` 로 저장되어 있어 업종 필터가 사실상 동작하지 않음
- **원인**: bizinfo API의 `trgetNm` 필드가 "중소기업", "소상공인" 등 지원대상을 담고 있지만, 업종 정보(음식업/소매업 등)는 별도로 제공되지 않음
- **요청안 A**: bizinfo 해시태그나 제목에서 업종 키워드 추출하는 로직 추가
- **요청안 B**: 업종 필터를 UI에서 제거하고 AI 추천 단계에서 처리

---

### 2. AI 요약 API
- **위치**: `PolicyDetail.tsx` 우측 패널 "AI 요약" 섹션 (현재 "준비 중" 표시)
- **요청**: `GET /api/v1/policies/{id}/summary`
- **응답 예시**:
  ```json
  {
    "summaryText": "이 정책은 ...",
    "highlights": [
      { "icon": "money", "label": "지원 금액", "content": "최대 500만원" },
      { "icon": "calendar", "label": "신청 기간", "content": "2026.07.01 ~ 2026.08.31" }
    ]
  }
  ```
- **연결 파일**: `src/pages/PolicyDetail.tsx` → `AI_DEFAULTS.aiSummaryText`, `aiHighlights`

---

### 3. AI 리포트 생성/조회 API
- **위치**: `PolicyDetail.tsx` 우측 패널 "AI 리포트" 섹션 (현재 setTimeout 더미)
- **요청**:
  - `POST /api/v1/policies/{id}/report` — 리포트 생성
  - `GET /api/v1/reports` — 저장된 리포트 목록 조회
- **응답 예시** (생성):
  ```json
  {
    "impactLabel": "비용 증가 예상",
    "impactStyle": "bg-red-100 text-red-600",
    "summary": "이 정책은 귀사의 인건비에 영향을 미칩니다...",
    "details": ["항목1", "항목2"],
    "relatedIds": [12, 45]
  }
  ```
- **연결 파일**: `src/pages/PolicyDetail.tsx` → `handleGenerateReport()`, `src/pages/ReportList.tsx`

---

### 4. 서류 체크리스트 API
- **위치**: `/policies/:id/checklist` 페이지 (현재 전체 mock)
- **요청**: `GET /api/v1/policies/{id}/checklist`
- **응답 예시**:
  ```json
  [
    { "id": "biz-reg", "label": "사업자등록증", "required": true, "description": "개업일 기준 6개월 이상" },
    { "id": "tax-cert", "label": "납세증명서", "required": true }
  ]
  ```
- **연결 파일**: `src/pages/PolicyChecklist.tsx`

---

### 5. 맞춤 추천 정책 API
- **위치**: `HomePage.tsx` (현재 최신순 3건으로 대체 중)
- **요청**: `GET /api/v1/policies/recommended` (로그인 사용자의 업종·지역 기반)
- **응답**: 기존 정책 목록 응답과 동일한 형태, 3~5건
- **연결 파일**: `src/pages/HomePage.tsx` → `useEffect` API 호출 부분

---

## 📋 현재 mock/placeholder로 남은 기능

| 기능 | 파일 | 상태 | 필요한 API |
|---|---|---|---|
| AI 요약 | `PolicyDetail.tsx` | "준비 중" 배지 표시 | #2 |
| AI 리포트 생성 | `PolicyDetail.tsx` | setTimeout 더미 (1.6초 후 로컬 저장) | #3 |
| 내 사업 영향도 | `PolicyDetail.tsx` | 빈 배열 (섹션 미표시) | #3 |
| 리포트 목록 | `ReportList.tsx` | localStorage 기반 | #3 |
| 서류 체크리스트 | `PolicyChecklist.tsx` | 전체 mock | #4 |
| 홈 맞춤 추천 | `HomePage.tsx` | 최신 3건으로 대체 중 | #5 |
