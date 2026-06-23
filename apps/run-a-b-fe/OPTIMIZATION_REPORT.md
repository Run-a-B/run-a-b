# 웹 최적화 보고서

**작업일**: 2026-06-23

---

## 결과 요약

| 항목 | 최적화 전 | 최적화 후 | 개선율 |
|---|---|---|---|
| 초기 JS 번들 (raw) | 423.42 kB | ~199 kB | **-53%** |
| 초기 JS 번들 (gzip) | 119.75 kB | ~63 kB | **-47%** |
| JS 청크 수 | 1개 | 21개 | 페이지별 분리 |
| 빌드 시간 | 215ms | 186ms | -14% |

---

## 최적화 전 빌드 결과

```
dist/assets/index-Ds-tU8F1.css    34.90 kB │ gzip:   7.41 kB
dist/assets/index-CFxIwO2j.js    423.42 kB │ gzip: 119.75 kB
```

모든 페이지 코드가 단일 번들로 묶여 있어, 홈 접속만 해도 전체 앱 코드를 다운로드.

---

## 최적화 후 빌드 결과

```
dist/assets/index-Ds-tU8F1.css                34.90 kB │ gzip:  7.41 kB
dist/assets/visited-_9WJhUUw.js                0.22 kB │ gzip:  0.17 kB
dist/assets/api-MBsCqaqj.js                    0.42 kB │ gzip:  0.29 kB
dist/assets/reports-Bg2AcAHW.js                0.43 kB │ gzip:  0.27 kB
dist/assets/rolldown-runtime-S-ySWqyJ.js       0.69 kB │ gzip:  0.42 kB
dist/assets/vendor-oauth-CPQsqDGm.js           1.92 kB │ gzip:  0.90 kB
dist/assets/Login-XLWudfhF.js                  5.55 kB │ gzip:  2.28 kB
dist/assets/index-qSxbhKEy.js                  7.22 kB │ gzip:  2.85 kB
dist/assets/PolicyChecklist-oayAKvLj.js        9.53 kB │ gzip:  2.82 kB
dist/assets/ReportList-DEjNgJrq.js            11.14 kB │ gzip:  3.22 kB
dist/assets/HomePage-C6i5gXED.js              12.12 kB │ gzip:  3.27 kB
dist/assets/Policies-CHpT4pLO.js              16.20 kB │ gzip:  4.21 kB
dist/assets/PolicyDetail-CU7YDpbt.js          17.69 kB │ gzip:  4.40 kB
dist/assets/Signup-DDqYalNV.js                18.38 kB │ gzip:  5.41 kB
dist/assets/vendor-B2Y5Hvh-.js                20.51 kB │ gzip:  8.05 kB
dist/assets/MyPage-DWJHDum8.js                28.51 kB │ gzip:  6.28 kB
dist/assets/vendor-axios-DsxJVSlv.js          42.08 kB │ gzip: 16.25 kB
dist/assets/policies-BEttZIYI.js              44.78 kB │ gzip: 10.96 kB
dist/assets/vendor-react-zZgowX5K.js         191.23 kB │ gzip: 60.39 kB
```

### 초기 로드 시 다운로드되는 청크 (앱 진입)
| 파일 | 역할 | gzip |
|---|---|---|
| vendor-react | React + React Router | 60.39 kB |
| index | 앱 진입점 + 라우터 | 2.85 kB |
| rolldown-runtime | 모듈 런타임 | 0.42 kB |
| **합계** | | **~63 kB** |

---

## 적용된 최적화 기법

### 1. Route-based Lazy Loading (`src/routes/AppRoutes.tsx`)
- `React.lazy` + `Suspense`로 모든 페이지 컴포넌트를 동적 import로 전환
- 페이지 전환 시 해당 청크만 추가 다운로드
- 로딩 중 스피너 표시 (`PageSpinner` 컴포넌트)

```tsx
const Policies     = lazy(() => import('@/pages/Policies'))
const PolicyDetail = lazy(() => import('@/pages/PolicyDetail'))
// ...
```

### 2. Vendor Chunk 분리 (`vite.config.ts`)
- `manualChunks`로 라이브러리를 별도 청크로 분리
- 앱 코드 변경 시에도 라이브러리 캐시 유지
  - `vendor-react`: react, react-dom, react-router-dom
  - `vendor-axios`: axios
  - `vendor-oauth`: @react-oauth/google

### 3. `useMemo` for Pagination (`src/pages/Policies.tsx`)
- `getPageNumbers()` 함수를 `useMemo`로 메모이제이션
- `page`, `totalPages` 변경 시에만 재계산 (매 렌더 → 변경 시만)

```tsx
const pageNumbers = useMemo(() => { ... }, [page, totalPages])
```

### 4. TypeScript 에러 정리
- `MyPageSidebar.tsx`: 미사용 `ReportIcon`, `SearchIcon` 제거
- `Signup.tsx`: 미사용 `generateCode` 함수 제거, `setMockCode` 제거
- `Policies.tsx`: `useRef` 타입 명시 (`| undefined`)
