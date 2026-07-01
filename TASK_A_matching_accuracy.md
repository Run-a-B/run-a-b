# 작업 지시서 A: 매칭 정확도 개선 (관련도 점수 분포 + 카테고리 필터 버그)

## 배경
- 프로젝트: Run a B, 최종 발표 2026.07.03, 레포 https://github.com/Run-a-B/run-a-b
- 배포 사이트: https://run-a-b.com (실제 접속 가능하면 직접 확인하면서 진행할 것. 접속 불가 환경이면 코드 정적분석 + curl로 API 응답 확인하며 진행)
- EC2 배포는 이번 작업 범위 아님 — 팀원이 수동으로 진행함. **이 문서 작업은 커밋 + push (+ 가능하면 PR)까지만.**
- 이 문서는 4개 작업지시서(A/B/C/D) 중 A. 각 문서는 독립적으로 별도 브랜치에서 작업.

이미 팀원이 실사이트를 테스트하며 아래 2개 문제를 발견함. **코드 확인해서 원인 재검증 후 수정할 것. 아래에 없는 추가 문제 발견 시 분석하고 즉시 수정, 작업 로그에 기록.**

---

## 문제 A-1: 카테고리 필터가 작동 안 함 (확정 버그, 원인 확인됨)

### 원인
`api/src/main/java/com/runab/api/repository/PolicySpecification.java`의 `categoryMatches()`가 `Policy.filterCategory` 컬럼으로 필터링하는데, 프론트 `Policies.tsx`의 `CATEGORIES` 드롭다운("기술","경영","수출","인력","창업","금융","내수","기타")이 보내는 값은 실제로 `Policy.category` 컬럼(카드 뱃지에 표시되는 값)과 매칭됨. **컬럼이 잘못 연결되어 있어서 카테고리를 뭘 선택해도 결과가 안 바뀜.**

```java
// 현재 (버그)
public static Specification<Policy> categoryMatches(String filterCategory) {
    return (root, query, cb) -> {
        if (filterCategory == null || filterCategory.equals("전체 카테고리")) {
            return cb.conjunction();
        }
        return cb.equal(root.get("filterCategory"), filterCategory);  // ← filterCategory 컬럼이 아니라 category 컬럼이어야 함
    };
}
```

### 수정 방향
1. `categoryMatches()`가 `root.get("category")`를 비교하도록 수정
2. `Policy.filterCategory` 컬럼이 실제로 어떤 용도인지 확인 (`PolicySyncService`에서 어떻게 채워지는지, 실제 DB 값이 뭔지) — 만약 프론트에 대응하는 UI가 없이 죽은 필드라면 그대로 둬도 됨(제거는 이번 범위 아님). 실수로 값이 뒤바뀐 건 아닌지도 확인.
3. `PolicyController`, `PolicyService`의 파라미터명(`category`)이 실제로 `Policy.category`를 가리키도록 일관되게 유지되는지 전체 흐름 재확인.
4. 수정 후 실제로 카테고리 선택 시 결과 필터링되는지 curl 또는 브라우저로 검증:
   ```
   curl "http://localhost:8080/api/v1/policies?category=기술&page=1&size=5"
   ```
   응답의 모든 `category` 값이 "기술"인지 확인.

---

## 문제 A-2: 관련도 점수가 특정 구간(40~70%대)에 몰려서 사업정보 차이를 체감하기 어려움

### 원인
`api/src/main/java/com/runab/api/service/matcher/EligibilityMatcher.java`의 채점 방식 때문에 구조적으로 점수가 좁은 구간에 몰림:

1. `goalScore`는 항상 50 고정 (`computeGoalScore()` — 배윤성 매핑표 미연동, 이번 범위 아님, 건드리지 말 것)
2. `documentScore`는 데이터 없으면 50 고정
3. **핵심 문제**: 정책 카드에 조건 자체가 없는 항목(`business_type`, `owner_type`, `revenue` 등)은 `NOT_REQUIRED` 처리되는데, 이게 `Scorer.add()`에서 `fraction = 1.0`(만점)으로 계산됨. 즉 "조건이 없어서 판단할 필요 없음"이 "완벽하게 일치함"과 똑같이 만점 처리되고 있음. 지금 대부분의 정책 카드가 아직 데이터가 부실해서(`owner_types: []`, `max_revenue: null` 등) 이런 항목이 대다수라, **거의 모든 사용자가 비슷하게 높은 베이스라인 점수를 깔고 시작**하게 됨. 그 결과 실제로 차이 나는 부분(region 20점 + industry 20점 + date 10점, 총 50점)만 변동 요인이 되고 나머지는 사실상 고정값처럼 작동함.

### 수정 방향 (중요 — 신중하게 접근)
이 채점 공식(`eligibilityScore×0.70 + goalScore×0.20 + documentScore×0.10`)은 팀원(배윤성)이 확정한 공식이므로 **가중치 비율 자체는 손대지 말 것.** 대신 "조건 없음(NOT_REQUIRED)"이 부당하게 만점으로 잡히는 부분만 통계적으로 올바르게 고친다:

- **제안하는 방식**: `NOT_REQUIRED` 항목은 만점(1.0)으로 채점하는 대신, **해당 항목의 배점 자체를 총점 계산에서 제외**(분모·분자 모두에서 제외)하도록 정규화. 즉 "조건이 없는 항목"은 점수에 영향을 주지 않게 하고, **실제로 조건이 존재하는 항목들끼리만** 가중 평균을 냄.
  - 예: region(20)+industry(20)만 조건이 있고 나머지는 전부 NOT_REQUIRED라면, 현재는 `(20×fraction + 20×fraction + 나머지 60×1.0) / 100`이지만, 수정 후에는 `(20×fraction + 20×fraction) / 40`으로 계산 — 실제 판단 가능한 항목 기준으로 순수하게 평가.
  - `Scorer` 클래스의 `weightedSum()`을 이런 정규화 방식으로 바꾸고, `NOT_REQUIRED`는 `pass`/`fail` 등 버킷 분류에서는 그대로 유지(리포트/디버깅용), 점수 계산에만 영향 주도록.
- `UNKNOWN`(판단 불가, 데이터 없어서 모름)은 계속 현재처럼 페널티(0.3~0.4)를 주는 게 맞음 — "모른다"는 "조건이 없다"와 다른 의미이므로 이건 그대로 둘 것.
- 이 변경으로 `eligibilityScore`의 분산이 커지고, 최종 `score`도 정책마다 더 뚜렷하게 갈릴 것으로 예상됨. `EligibilityMatcherTest`의 기존 테스트 케이스들이 이 변경으로 깨질 수 있는데, **기댓값이 통계적으로 더 정확해지는 방향이면 테스트를 갱신**하고(왜 값이 바뀌었는지 주석으로 근거 남길 것), 만약 이 변경이 기존 설계 의도와 명백히 충돌한다고 판단되면 진행하지 말고 작업 로그에 대안을 제시할 것.
- 코드에 주석으로 "이 정규화 로직은 2026.07.01 관련도 점수 분포 개선 목적으로 추가됨"이라고 남길 것 (배윤성이 나중에 보고 이해할 수 있도록).

### 검증
로컬에서 서로 다른 지역/업종 조합 사업정보 2~3개로 관련도 API 호출해서, 이전보다 점수 분포가 넓어졌는지(예: 30%~90% 정도로 퍼지는지) 확인. `TASK_relevance_report_bugfix.md` 작업 로그에 있던 이전 배경도 참고할 것.

---

## 컨벤션
- `ErrorCode(String code, int status, String message)` 형식
- 엔티티 세터 금지, 비즈니스 메서드로만
- API 응답 `{ success, data, message, error }`, `ApiResponse.success()`
- `ddl-auto=update`: 필드 추가는 자동, 컬럼 삭제/타입변경은 수동 SQL 필요시 명시

## Git 워크플로우
- 브랜치: `fix/matching-accuracy`
- 커밋: 한글, `fix: ...` 형식
- push까지, 가능하면 PR 생성 (리뷰어 jaeseong09), **main 직접 머지 금지**

## 완료 조건
- [ ] 카테고리 필터 선택 시 실제로 결과가 필터링됨 (curl 검증 포함)
- [ ] 서로 다른 사업정보로 관련도 조회 시 점수 분포가 이전보다 넓어짐 (구체적 수치 비교를 작업 로그에 남길 것)
- [ ] `EligibilityMatcherTest` 전체 통과 (필요시 기댓값 갱신 + 근거 주석)
- [ ] `./gradlew bootRun` 정상 기동, `npx tsc -b` 통과 (프론트 변경 있다면)
- [ ] 커밋 + push 완료

## 작업 로그
> 발견한 문제, 진단 결과, 수정 내역을 시간순으로 기록.

### 2026-07-01 — 브랜치 `fix/matching-accuracy` (main 기준 독립 브랜치)

**[A-1] 카테고리 필터 버그 수정 완료**
- `PolicySpecification.categoryMatches()`가 `root.get("filterCategory")`로 비교하던 것을 `root.get("category")`로 수정. 프론트 `Policies.tsx`의 `CATEGORIES`("기술","경영",...)는 카드 뱃지값 = `Policy.category`와 매칭되므로 이게 맞음. 파라미터명도 `filterCategory`→`category`로 정리.
- `filter_category` 컬럼 용도 확인: `PolicySyncService`가 신규/갱신 모두 `category`와 **동일 값**으로 채우고 있음(`Policy.builder().category(category).filterCategory(category)`, `updateFromBizinfo(..., category, category, ...)`). 원래 엔티티 주석상 "자금 지원/세금 감면" 같은 별도 분류용으로 설계됐으나 대응 UI가 없어 사실상 죽은 필드. 이번 수정 후엔 어디서도 읽지 않음 → 제거는 이번 범위 밖이라 그대로 둠(주석으로 명시).
  - 참고: 로컬 DB에선 `category`와 `filter_category`가 1571행 전부 동일해 컬럼만 바꿔도 결과는 같아 보이지만, 프론트가 의미상 가리키는 컬럼(category)로 맞추는 게 정확하고 프로덕션에서 두 컬럼이 어긋난 경우(구버전 sync/수동데이터)에도 견고함.
- curl 검증(로컬 부팅):
  - `category=기술` → total=361, 반환 5건 전부 category="기술"
  - `category=금융` → total=206 (전부 금융), `category=창업` → total=99 (전부 창업)
  - `category=전체 카테고리` → total=1571 (필터 없음)
  - → DB GROUP BY 카운트와 정확히 일치. **완료 조건 #1 충족.**

**[A-2] 관련도 점수 분포 개선(NOT_REQUIRED 정규화) 완료**
- `EligibilityMatcher.Scorer` 수정: "조건 없음(NOT_REQUIRED)" 항목을 만점(1.0)으로 채점하던 것을 **총점 계산에서 제외**(분모·분자 모두 제외)하고, 실제로 조건이 존재하는 항목들끼리만 가중 평균(`sum/totalWeight*100`)을 내도록 정규화. 가중치 비율(0.70/0.20/0.10)·goalScore(50 고정)는 손대지 않음. `UNKNOWN`은 "조건은 있으나 판단 불가"라 페널티(0.3~0.4)로 분모에 계속 포함(그대로). 버킷 분류(pass/fail/notRequired)는 리포트/디버깅용으로 유지. 코드에 "2026.07.01 관련도 점수 분포 개선 목적" 주석 명시.
- 테스트 갱신(근거 주석 포함):
  - `noEligibilityData_allUnknown`: eligibilityScore 66→**32**, score 61→**37** (빈 카드는 판단대상 region/industry/date 3개뿐: 획득16/배점50 → 32).
  - `closedPolicy_hardFail`: eligibilityScore 76→**52** (region PASS20 + industry UNKNOWN6 + date FAIL0 = 26/50 → 52).
  - `fullMatch_highScore`(100/89), `nationwide`, `regionMismatch`는 값 변화 없음/무영향 → 그대로 통과. 전체 4개 통과.
- 실측 검증(로컬 부팅 + 합성 카드 1개[지역=서울특별시·업종=IT/소프트웨어·open]를 정책 id=1에 부착, 서로 다른 사업정보 2계정):
  - 사용자 A(서울특별시/IT — 완전일치): 관련도 **85**
  - 사용자 B(부산광역시/제조업 — 지역·업종 불일치): 관련도 **29**
  - 동일 정책 A↔B 격차 = **56점**. 같은 입력을 **이전 공식**으로 계산하면 B는 eligibility 60(=region0+industry0+date10 + NOT_REQUIRED 50만점)→score **57**, A는 85 → 격차 겨우 28점.
  - → 정규화로 불일치 계정 점수가 57→29로 뚜렷이 내려가고 A/B 격차가 28→56으로 **2배** 벌어짐. 사업정보 차이가 점수에 명확히 반영됨. **완료 조건 #2 충족.** (검증용 합성 데이터·계정은 검증 후 전부 삭제.)
  - 주의(범위 외): 현재 프로덕션/로컬 대부분 policy_card가 비어 있어 region/industry가 UNKNOWN이면 여전히 정책 간 점수차는 작다(이건 카드 데이터 적재의 문제 — 배윤성 매핑표/extractor 영역). 본 변경은 "카드에 조건이 있는 정책"에서 분포가 확실히 넓어지도록 하는 구조 개선.

**[검증]**
- `EligibilityMatcherTest` 4개 전부 통과(`--rerun-tasks`).
- `./gradlew bootRun` 정상 기동(컴파일 에러 없음). 프론트 변경 없음(기존 `Policies.tsx`가 이미 `category`를 전송) → `tsc` 불필요.
- 스키마 변경 없음(컬럼 추가/삭제 없음) → 마이그레이션 불필요.
