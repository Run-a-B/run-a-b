# 작업 지시서 E: 업종(industry) 필터 진단 및 수정

## 배경
- Run a B, 발표 2026.07.03. 독립 브랜치로 작업. EC2 배포는 팀원이 수동으로 — 이 작업은 커밋+push(+가능하면 PR)까지만.
- 실사이트(https://run-a-b.com/policies)에서 "전체 업종" 드롭다운을 "카페/음료"로 바꿔도 **총 정책 개수(1,479개)가 전혀 안 줄어드는 현상** 발견됨. 카테고리 필터(기술/경영/수출 등)는 이미 별도 작업(Task A, PR #38)에서 고쳤고 그건 이 문서 범위 아님 — **이번엔 업종 드롭다운(전체 업종/음식점업/카페음료/의류패션/뷰티미용/교육학원/IT소프트웨어/제조업/도소매업)만 다룸.**

---

## 진단 절차 (반드시 먼저 수행, 추측으로 고치지 말 것)

Task A 때 카테고리 필터가 컬럼 자체를 잘못 연결한 명백한 버그였던 것과 달리, 이번엔 **버그인지 데이터 특성인지 아직 불확실함.** 먼저 확인:

1. `api/src/main/java/com/runab/api/repository/PolicySpecification.java`의 `industryMatches()` 로직 재확인:
   ```java
   public static Specification<Policy> industryMatches(String industry) {
       return (root, query, cb) -> {
           if (industry == null || industry.equals("전체 업종")) {
               return cb.conjunction();
           }
           return cb.or(
                   cb.equal(root.get("industry"), "전체"),
                   cb.equal(root.get("industry"), industry)
           );
       };
   }
   ```
   코드 자체는 `Policy.industry` 컬럼과 정상적으로 비교하는 것처럼 보임(Task A의 category처럼 컬럼이 잘못 연결된 건 아닌 듯). **다만 `업종="전체"인 정책은 어떤 업종을 선택해도 항상 노출**되도록 되어 있는데, 실제 DB에 `industry` 컬럼 값 분포를 확인할 것:
   ```sql
   SELECT industry, COUNT(*) FROM policy GROUP BY industry ORDER BY COUNT(*) DESC;
   ```
2. 결과 해석:
   - **"전체"가 압도적 다수(예: 1,400개 이상)이고 "카페/음료" 같은 특정 값은 소수**라면 → 코드 버그 아님. `OR industry='전체'` 조건 때문에 대부분의 정책이 항상 노출되는 게 **의도된 동작**(업종 무관 정책은 모든 업종 필터에서 다 보이게). 이 경우 총 개수가 안 줄어드는 게 아니라 "1,479개에서 아주 조금만" 줄어드는 게 정상 — 실제로 몇 개 줄어드는지(`카페/음료` 선택 시 정확히 몇 건인지) 확인해서 캡처/기록.
   - **"카페/음료"로 명확히 태깅된 정책이 꽤 있는데도(예: 50개 이상) 필터링 후에도 전체 개수(1,479)가 그대로**라면 → 진짜 버그. 이 경우 아래로 진행.
3. 프론트도 재확인: `apps/run-a-b-fe/src/pages/Policies.tsx`의 `INDUSTRIES` 배열 값("카페/음료" 등)과 백엔드로 실제 전송되는 파라미터 값이 정확히 일치하는지(공백, 슬래시 표기 차이 등 — 예를 들어 프론트는 "카페/음료"인데 DB엔 "카페음료" 또는 "카페 및 음료"처럼 다르게 저장돼 있을 가능성) 확인:
   ```sql
   SELECT DISTINCT industry FROM policy;
   ```
   이 목록과 `Policies.tsx`의 `INDUSTRIES` 배열(`["전체 업종", "음식점업", "카페/음료", "의류/패션", "뷰티/미용", "교육/학원", "IT/소프트웨어", "제조업", "도소매업"]`)을 정확히 대조. 문자열 불일치가 있으면 그게 원인.

---

## 수정 방향 (진단 결과에 따라 분기)

- **버그로 확인된 경우**: 원인(컬럼 오연결, 문자열 불일치, 로직 오류 등)에 맞춰 수정. Task A의 카테고리 필터 수정 패턴을 참고.
- **데이터 특성으로 확인된 경우**: 코드는 건드리지 말고, 작업 로그에 "정상 동작 확인, 대부분 정책이 업종 무관(전체)으로 등록되어 있어 필터링 효과가 작게 느껴지는 것"이라고 명확히 기록. 이 경우 UX 개선으로 "총 N개 중 M개가 업종 무관 정책입니다" 같은 안내 문구를 추가할지는 판단해서 제안만 남기고(구현은 선택), 실제 구현 여부는 사용자(팀원)에게 물어보지 말고 **일단 안내 문구 정도는 가볍게 추가해도 좋음** (UI 텍스트 한 줄 추가 수준이면 바로 진행, 큰 구조 변경이 필요하면 제안만 남기고 진행하지 말 것).

---

## 컨벤션
- `ErrorCode(String code, int status, String message)` 형식
- 엔티티 세터 금지, 비즈니스 메서드로만
- API 응답 `{ success, data, message, error }`, `ApiResponse.success()`

## Git 워크플로우
- 브랜치: `fix/industry-filter`
- 커밋: 한글, `fix: ...` 형식 (버그가 아니었다면 커밋 없이 작업 로그만 남기고 종료해도 됨)
- push까지, 가능하면 PR 생성 (리뷰어 jaeseong09), **main 직접 머지 금지**

## 완료 조건
- [ ] 업종 필터가 버그인지 데이터 특성인지 명확히 진단되고 근거(SQL 결과 등)가 작업 로그에 남음
- [ ] 버그였다면 수정 후 실제로 업종 선택 시 결과가 유의미하게 줄어드는지 curl/브라우저로 검증
- [ ] 기존 테스트 통과, `./gradlew bootRun` / `npx tsc -b` 정상 (코드 변경이 있었다면)
- [ ] 커밋 + push 완료 (코드 변경이 있었던 경우만)

## 작업 로그
> 진단 결과, SQL 확인 내용, 수정 여부와 근거를 시간순으로 기록.

### 2026-07-01 — 브랜치 `fix/industry-filter` (main 기준 독립 브랜치)

**[진단] 결론: 코드 버그 아님. 데이터 특성(모든 정책 industry="전체").**

1. `industry` 컬럼 값 분포 (로컬 DB, 프로덕션과 동일 적재 로직):
   ```
   SELECT industry, COUNT(*) FROM policy GROUP BY industry;
   → 전체  1571
   SELECT DISTINCT industry FROM policy;
   → 전체   (단 한 종류)
   총 정책 수 = 1571
   ```
   **1571개 전부 industry="전체"** — 특정 업종("카페/음료" 등)으로 태깅된 정책이 0건.

2. 원인: `PolicySpecification.industryMatches()`는 `Policy.industry` 컬럼과 정상적으로 비교함(Task A의 category처럼 컬럼이 오연결된 게 아님). 다만 조건이 `industry='전체' OR industry=:선택값`인데, **모든 행이 '전체'라 어떤 업종을 골라도 OR 앞쪽 조건(`='전체'`)에서 전부 매칭** → 결과가 1건도 안 줄어듦.
   - 상류 원인: `PolicySyncService`가 bizinfo 동기화 시 `.industry("전체")`로 **하드코딩**(코드 주석: "자격요건 매핑은 policy_card 단계에서 처리"). 즉 정책별 업종은 애초에 채워진 적이 없음. bizinfo 원본이 프론트의 8개 업종 분류로 깔끔히 매핑되지 않아 자격요건(policy_card/extractor) 단계로 미룬 것.

3. 프론트 대조: `Policies.tsx`의 `INDUSTRIES`(음식점업/카페음료/의류패션/...)와 전송 파라미터명(`industry`)은 정상. 다만 DB엔 대응하는 특정 업종 값이 아예 없으므로 문자열 불일치 이슈 이전에 **필터 대상 데이터 자체가 없음**.

4. 실측(로컬 부팅 curl) — 완료조건상 "실제로 몇 건 줄어드는지" 기록:
   ```
   industry=전체 업종     total=1571
   industry=음식점업      total=1571
   industry=카페/음료     total=1571
   industry=의류/패션     total=1571
   industry=뷰티/미용     total=1571
   industry=교육/학원     total=1571
   industry=IT/소프트웨어  total=1571
   industry=제조업        total=1571
   industry=도소매업      total=1571
   ```
   → 어떤 업종을 골라도 **정확히 0건 감소**. Task A의 category(361/206/99로 줄던 것)와 대조적. 코드 로직은 의도대로 동작하지만 데이터에 특정 업종 행이 없어 필터 효과가 없음.

**[조치]**
- 필터/매칭 **백엔드 코드는 손대지 않음**(정상 동작 확인됨). 진짜 해결은 정책별 업종 데이터를 채우는 것(bizinfo→8개 업종 매핑 or policy_card 기반)인데, 이는 데이터 파이프라인/extractor 영역이라 이번 범위 밖 — 아래 "제안"에 남김.
- 지시서에서 허용한 범위 내 **가벼운 UI 안내 문구 한 줄만 추가**: `Policies.tsx`에서 "전체 업종"이 아닌 특정 업종을 선택했을 때, 결과 개수 아래에 "ⓘ 등록된 정책 대부분이 업종 무관으로 분류돼 있어, 업종 필터로는 결과가 거의 좁혀지지 않을 수 있어요."를 조건부 노출. (구조 변경 없음, `industry !== "전체 업종"`일 때만 표시.)

**[제안 — 이번 범위 밖, 팀 참고용]**
- 근본 해결책 A: `PolicySyncService`에서 bizinfo 응답의 업종/대상 필드를 프론트 8개 업종으로 매핑해 `Policy.industry`를 채운다(매핑표 필요).
- 근본 해결책 B: policy_card의 `eligibility.industries`가 채워지면 그걸 기준으로 정책 목록 업종 필터를 재설계(현재 매칭 점수엔 쓰이나 목록 필터엔 미연동).
- 데이터가 채워지기 전까지는 업종 드롭다운을 숨기거나 "베타" 표기하는 것도 대안(판단은 팀).

**[검증]**
- 백엔드 변경 없음 → 기존 테스트 영향 없음. `./gradlew bootRun` 정상 기동 확인(진단 curl 수행).
- 프론트 `npx tsc -b` 통과.
