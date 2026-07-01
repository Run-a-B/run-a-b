# 작업 지시서 J: "내 사업 영향도" 막대그래프 색상 안 보이는 버그 수정

## 배경
- Run a B, 발표 2026.07.03. 독립 브랜치로 작업. EC2 배포는 팀원이 수동으로 — 이 작업은 커밋+push(+가능하면 PR)까지만.
- 작업 시작 전 최신 main pull 받을 것 (Task C, PR #46 반영된 상태여야 함).
- 실사이트에서 AI 리포트의 "내 사업 영향도" 막대그래프가 렌더링은 되는데(막대 형태는 있음) **색깔이 전혀 안 보이고 흰색/회색으로만 나오는** 문제 발견됨.

## 원인 (확인 완료)
`api/src/main/java/com/runab/api/service/report/PolicyReportService.java`의 `mapToResponse()`가 색상 클래스를 이렇게 생성함:
```java
.barColor(up ? "bg-green-500" : "bg-red-500")
.tagColor(up ? "text-green-600" : "text-red-600")
```
**Tailwind CSS(v4, `@tailwindcss/vite`)는 빌드 시점에 프론트엔드 소스 파일을 스캔해서 실제로 리터럴로 등장하는 클래스명만 CSS로 생성한다.** `bg-green-500`, `bg-red-500`, `text-red-600`이라는 문자열이 프론트엔드(`apps/run-a-b-fe/src/`) 어디에도 리터럴로 존재하지 않아서, 이 클래스에 대응하는 CSS 자체가 빌드에 포함되지 않음. 그래서 백엔드가 런타임에 이 클래스명을 API로 보내줘도 브라우저에서 아무 스타일도 적용 안 됨(투명하게 렌더링).

확인: `apps/run-a-b-fe/src/data/policies.ts`(목업 데이터)에는 `bg-green-400`, `bg-red-400`, `text-green-600`이 각각 다수 등장해서 정상적으로 Tailwind가 스캔·생성함. 반면 `bg-green-500`/`bg-red-500`/`text-red-600`은 등장 횟수가 0~1회 이하라 빌드에서 누락됨.

## 수정 방향
`PolicyReportService.mapToResponse()`에서 사용하는 색상 클래스를, **이미 프론트 소스에 충분히 등장해서 Tailwind가 확실히 생성하는 클래스**로 교체:
```java
.barColor(up ? "bg-green-400" : "bg-red-400")
.tagColor(up ? "text-green-600" : "text-red-500")
```
(정확한 클래스명은 `apps/run-a-b-fe/src/data/policies.ts`에 실제로 몇 번 등장하는지 `grep`으로 재확인하고, 가장 안전하게 다수 등장하는 조합으로 선택할 것 — 위 예시는 참고용이며 직접 카운트 확인 후 결정.)

**추가로 확인할 것**: 이 문제가 이 파일에만 있는지, 다른 곳(예: `OpenAiClient`를 거치는 다른 서비스, `PolicySummaryService` 등)에서도 백엔드가 동적으로 Tailwind 클래스명을 생성해서 보내는 패턴이 더 있는지 전체 검색해서 같은 문제가 더 있으면 같이 고칠 것:
```bash
grep -rn "bg-.*-500\|text-.*-600\|bg-.*-400" api/src/main/java
```
찾은 모든 색상 클래스가 프론트 소스에 실제로 등장하는지 하나씩 대조 확인.

## 검증
- 수정 후 실제로 프론트 빌드(`npx tsc -b` 및 가능하면 `npm run build`까지)해서 해당 클래스가 CSS에 포함되는지 확인
- (배포 후) 실사이트에서 긍정 리포트 생성해서 막대그래프에 실제로 초록/빨강 색이 칠해지는지 확인 필요하다고 작업 로그에 남길 것 (로컬에서 시각적 확인이 어려우면 최소한 CSS 클래스 존재 여부는 빌드 결과물에서 검증)

## 컨벤션
- 백엔드 전용 변경(색상값 문자열만), 로직 변경 없음
- `ErrorCode`, `ApiResponse` 등 기존 컨벤션 영향 없음

## Git 워크플로우
- 브랜치: `fix/report-bar-color`
- 커밋: 한글, `fix: ...` 형식
- push까지, 가능하면 PR 생성 (리뷰어 jaeseong09), **main 직접 머지 금지**

## 완료 조건
- [ ] `PolicyReportService`의 barColor/tagColor가 프론트 소스에 실제 등장하는(Tailwind가 생성 보장하는) 클래스로 교체됨
- [ ] 같은 패턴의 문제가 다른 백엔드 코드에 더 없는지 검색 확인, 있었으면 같이 수정
- [ ] 프론트 빌드 정상, 관련 테스트 통과
- [ ] 커밋 + push 완료

## 작업 로그
> 원인 확인 근거(grep 결과), 수정 내역을 기록.

### 2026-07-01 — 브랜치 `fix/report-bar-color` (Task C PR #46 머지된 최신 main 기준)

**[원인 확정 — grep 근거]** 백엔드가 동적 생성하는 색상 클래스는 `PolicyReportService.mapToResponse()` 3곳뿐(`grep -rn "bg-.*-[0-9]\|text-.*-[0-9]" api/src/main/java` 결과: line 193 impactStyle, 209 barColor, 210 tagColor). 프론트(`apps/run-a-b-fe/src`)에서 각 클래스가 **리터럴로 실재하는지** 대조:
- `bg-green-500`/`bg-red-500`: 프론트엔 오직 `styles/global.css`의 **주석**(`/* bg-green-500 */`)에만 존재 → Tailwind v4 유틸 미생성. (막대 무색 원인)
- `text-red-600`(down 태그): `policies.ts`에 1회(실제 className) → 생성되긴 하나 여유롭게 다수 등장하는 `text-red-500`(23회)로 교체.
- **추가 발견**: `impactStyle` negative = `"bg-red-100 text-red-700"`도 같은 문제. `bg-red-100`은 프론트에 `hover:bg-red-100`(변형)으로만 존재해 **plain 유틸 미생성**, `text-red-700`은 **0회** → 부정 영향 뱃지도 색이 안 칠해지고 있었음.

**[수정] `PolicyReportService.mapToResponse()` 색상 클래스만 교체(로직 무변경)**
- barColor: `bg-green-500/bg-red-500` → `bg-green-400`(27회)/`bg-red-400`(4회)
- tagColor: `text-green-600`(30회, 유지)/`text-red-600` → `text-red-500`(23회)
- impactStyle negative: `bg-red-100 text-red-700` → `bg-red-50`(4회)/`text-red-600`(1회). positive(`bg-green-100 text-green-700`)는 둘 다 실재해 유지.
- 다른 백엔드 파일(PolicySummaryService 등)엔 동적 Tailwind 색상 생성 없음(하이라이트는 icon 키만 보내고 색은 프론트에서 처리) → 추가 수정 대상 없음.

**[검증 — 빌드 산출 CSS 대조]** `npm run build` 후 `dist/assets/index-*.css`에서 클래스 selector 존재 확인:
- 신규/유지 클래스 **전부 PRESENT**: `.bg-green-400 .bg-red-400 .text-green-600 .text-red-500 .bg-red-50 .text-red-600 .bg-green-100 .text-green-700` ✓
- 기존 깨진 클래스 **전부 ABSENT**(진단 재확인): `.bg-green-500 .bg-red-500 .text-red-700` ✗(미생성). `bg-red-100`은 `hover\:bg-red-100`로만 존재.
- `npx tsc -b` 통과, 백엔드 컴파일·`EligibilityMatcherTest` 통과.
- ⚠️ 실제 화면에서 막대/뱃지에 초록·빨강이 칠해지는 최종 시각 확인은 배포 후 필요(로컬 시각 확인 어려움). 대신 위와 같이 **빌드 결과 CSS에 해당 클래스가 실제로 포함됨을 검증**함(누락이 원인이었으므로 이게 핵심 근거).
