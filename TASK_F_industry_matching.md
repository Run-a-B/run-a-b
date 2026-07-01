# 작업 지시서 F: 업종 매칭 정확도 개선 (동의어 매핑 + "전업종" 예외처리)

## 배경
- Run a B, 발표 2026.07.03. 독립 브랜치로 작업. EC2 배포는 팀원이 수동으로 — 이 작업은 커밋+push(+가능하면 PR)까지만.
- 배윤성이 `seed_policy_card_from_policy_v2.py`(rule-based extractor, 배윤성 개인 작업물, 이 레포 밖에서 실행)를 곧 돌려서 `policy_card.full_json`의 `eligibility.industries`, `eligibility.regions` 등을 실데이터로 채울 예정.
- 근데 이 스크립트가 뽑는 업종 이름표가 **우리 앱의 업종 taxonomy랑 안 맞아서**, 데이터가 채워져도 매칭이 제대로 안 될 수 있음이 사전에 확인됨. 이걸 우리 쪽(백엔드) 코드로 미리 대비해두는 작업.

## 참고: 배윤성 스크립트가 실제로 뽑는 업종 값들
`INDUSTRY_RULES` 키 기준: `음식점업, 도소매업, 제조업, 운수업, 숙박업, 미용업, 교육서비스업, 정보통신업, 바이오산업, 농식품업, 콘텐츠산업, 관광업`
업종 제한이 없는 정책은 `industries=["전업종"], industry_condition_type="all"`로 태깅됨.

## 우리 앱의 업종 드롭다운 (`apps/run-a-b-fe/src/pages/Policies.tsx`의 `INDUSTRIES`, `BusinessInfo.jobCategory`에 저장되는 값)
`음식점업, 카페/음료, 의류/패션, 뷰티/미용, 교육/학원, IT/소프트웨어, 제조업, 도소매업`

두 목록을 대조하면 `음식점업`/`제조업`/`도소매업` 3개만 문자열이 정확히 일치하고, 나머지는 이름이 다르거나(`미용업`↔`뷰티/미용`, `교육서비스업`↔`교육/학원`, `정보통신업`↔`IT/소프트웨어`) 아예 대응 항목이 없음(`카페/음료`).

---

## 요구사항 1: 업종 동의어 매핑 (RegionHierarchy와 동일 패턴)

`api/src/main/java/com/runab/api/service/matcher/RegionHierarchy.java`가 지역 상하위 관계를 처리하는 것과 같은 방식으로, **업종 동의어 매핑 클래스**를 신규로 만들 것 (예: `IndustryHierarchy.java`, 같은 패키지).

- 배윤성 스크립트가 뽑는 업종 이름(`INDUSTRY_RULES` 키) ↔ 우리 앱 드롭다운 업종 이름을 양방향 또는 정규화 매핑으로 연결:
  - `음식점업` ↔ `음식점업` (동일)
  - `미용업` ↔ `뷰티/미용`
  - `교육서비스업` ↔ `교육/학원`
  - `정보통신업` ↔ `IT/소프트웨어`
  - `제조업` ↔ `제조업` (동일)
  - `도소매업` ↔ `도소매업` (동일)
  - `카페/음료`(우리 드롭다운에만 있음, 스크립트엔 없음) — 스크립트의 `음식점업` 규칙에 "카페" 키워드가 포함되어 있으므로, `음식점업`으로 태깅된 카드는 사용자가 `카페/음료`를 선택했을 때도 매칭되도록 `음식점업` ↔ `카페/음료`도 동의어로 취급할 것 (완전히 다른 업종은 아니고 스크립트가 뭉뚱그려 분류한 것이므로).
  - 나머지 스크립트 전용 값(`운수업`, `숙박업`, `바이오산업`, `농식품업`, `콘텐츠산업`, `관광업`)은 우리 앱 드롭다운에 대응 항목이 없으므로 매핑 없이 그대로 두면 됨 (해당 값이 나온 카드는 어차피 우리 8개 드롭다운 중 어느 것도 정확히 대응 안 되니 UNKNOWN/FAIL로 남는 게 맞음 — 억지로 끼워맞추지 말 것).
- `EligibilityMatcher.evalIndustry()`가 `industries.contains(userIndustry)` 단순 비교 대신, 이 동의어 매핑을 거쳐서 비교하도록 수정 (region이 `RegionHierarchy.sameProvince()`를 쓰는 것과 같은 패턴).

## 요구사항 2: "업종 무관(전업종)" 예외처리 추가

`EligibilityMatcher.evalRegion()`에는 이미 "전국"이면 사용자 지역과 무관하게 무조건 `PASS` 처리하는 로직이 있음:
```java
if ("nationwide".equals(type) || regions.contains("전국")) {
    return MatchStatus.PASS;
}
```
`evalIndustry()`에는 이런 예외처리가 없어서, `industries=["전업종"]`(업종 제한 없다는 뜻)인 정책도 사용자 업종 문자열이랑 안 맞으면 그냥 `FAIL` 처리되는 버그가 있음. **지역의 "전국" 처리와 동일한 패턴으로 추가할 것**:
```java
// 예시 방향 — 실제 구현은 기존 evalIndustry 구조에 맞게
if ("all".equals(industryConditionType) || industries.contains("전업종")) {
    return MatchStatus.PASS;
}
```
`industry_condition_type` 필드(스크립트가 `"all"`로 채움)도 JSON에서 같이 읽어와야 함 — `evalRegion`이 `region_condition_type`을 읽는 것과 동일한 방식으로 `elig.path("industry_condition_type")` 참조.

---

## 검증

1. 배윤성 스크립트가 아직 안 돌았어도, **합성 테스트 카드**(`EligibilityMatcherTest`에 이미 있는 패턴 참고)로 아래 케이스들을 단위테스트로 추가해서 검증:
   - `industries=["미용업"]`, `userIndustry="뷰티/미용"` → `PASS` 나와야 함
   - `industries=["정보통신업"]`, `userIndustry="IT/소프트웨어"` → `PASS`
   - `industries=["음식점업"]`, `userIndustry="카페/음료"` → `PASS`
   - `industries=["전업종"], industry_condition_type="all"`, `userIndustry="아무거나"` → `PASS`
   - `industries=["운수업"]`, `userIndustry="뷰티/미용"` → `FAIL` (매핑 없는 값은 그대로 안 맞아야 정상)
2. 배윤성 스크립트가 이미 돌았다면, 실제 DB 데이터로 관련도 API 재호출해서 이전보다 점수 분포가 넓어졌는지(특히 뷰티/미용, IT/소프트웨어 계정 기준) 확인하고 수치를 작업 로그에 기록.

---

## 컨벤션
- `ErrorCode(String code, int status, String message)` 형식
- 엔티티 세터 금지, 비즈니스 메서드로만
- API 응답 `{ success, data, message, error }`, `ApiResponse.success()`
- `Task A`(`fix/matching-accuracy`, PR #38)에서 이미 `EligibilityMatcher`의 NOT_REQUIRED 정규화 로직을 수정했으므로, 그 변경 위에 이번 작업을 얹을 것 (최신 main 기준으로 브랜치 딸 것 — PR #38이 아직 머지 전이면 먼저 확인).

## Git 워크플로우
- 브랜치: `feat/industry-matching`
- 커밋: 한글, `feat: ...` 형식
- push까지, 가능하면 PR 생성 (리뷰어 jaeseong09), **main 직접 머지 금지**

## 완료 조건
- [ ] `IndustryHierarchy` 신규 클래스로 동의어 매핑 구현
- [ ] `evalIndustry()`에 "전업종" 예외처리 추가
- [ ] 위 5개 검증 케이스 모두 단위테스트로 추가되고 통과
- [ ] 기존 `EligibilityMatcherTest` 전체 통과
- [ ] `./gradlew bootRun` 정상 기동
- [ ] 커밋 + push 완료

## 작업 로그
> 발견한 문제, 진단 결과, 수정 내역을 시간순으로 기록.

### 2026-07-01 — 브랜치 `feat/industry-matching`

**[사전 확인] Task A(PR #38) 머지 상태**
- `gh pr view 38` → state=MERGED (2026-07-01 06:50Z). 최신 main에 `EligibilityMatcher.normalizedScore`(NOT_REQUIRED 정규화)와 category 필터 수정이 모두 반영됨 확인. → 최신 main 기준으로 `feat/industry-matching` 브랜치 생성, 그 위에 작업.

**[요구사항 1] 업종 동의어 매핑 — `IndustryHierarchy` 신규 (RegionHierarchy 패턴)**
- `api/src/main/java/com/runab/api/service/matcher/IndustryHierarchy.java` 신규. extractor 이름표 ↔ 앱 드롭다운 이름표를 동의어 그룹으로 묶고, `sameIndustry(userIndustry, cardIndustry)`로 그룹 일치 여부 판단(완전 동일 or 같은 그룹 → true).
  - 그룹: {음식점업, 카페/음료}, {미용업, 뷰티/미용}, {교육서비스업, 교육/학원}, {정보통신업, IT/소프트웨어}, {제조업}, {도소매업}
  - `음식점업`↔`카페/음료`는 extractor의 음식점업 규칙에 "카페" 키워드가 포함돼 있어 동의어로 묶음(지시서 근거).
  - extractor 전용 미대응 값(운수업/숙박업/바이오산업/농식품업/콘텐츠산업/관광업)은 **매핑하지 않음** — 자기 자신하고만 일치(우리 드롭다운과는 불일치)하도록 둠. 억지 매칭 금지.
- `EligibilityMatcher.evalIndustry()`가 `industries.contains(userIndustry)` 단순 비교 대신 `IndustryHierarchy.sameIndustry()`를 루프로 거치도록 수정(region이 `RegionHierarchy.sameProvince()` 쓰는 것과 동일 패턴).

**[요구사항 2] "전업종(all)" 예외처리**
- `evalIndustry()` 맨 앞에 `if ("all".equals(type) || industries.contains("전업종")) return PASS;` 추가. `industry_condition_type`은 `text(elig, "industry_condition_type", "unknown")`로 읽음(evalRegion이 `region_condition_type` 읽는 방식과 동일). "전업종"이면 사용자 업종 무관 PASS.
- 기존 UNKNOWN 처리(industries=[] → UNKNOWN, user blank → UNKNOWN)는 그대로 유지. "전업종"/all 체크가 빈 배열/불일치 판정보다 먼저 오도록 순서 배치(evalRegion의 nationwide-우선과 동일).

**[검증] 단위테스트 5개 신규 추가 (배윤성 스크립트 미실행 상태라 합성 카드로 검증)**
- industries=[미용업] + 사용자=뷰티/미용 → industry PASS ✅
- industries=[정보통신업] + 사용자=IT/소프트웨어 → industry PASS ✅
- industries=[음식점업] + 사용자=카페/음료 → industry PASS ✅
- industries=[전업종], industry_condition_type=all + 사용자=아무거나 → industry PASS ✅
- industries=[운수업] + 사용자=뷰티/미용 → industry FAIL ✅ (매핑 없는 값은 그대로 불일치)
- `EligibilityMatcherTest` 전체 **10개(기존 5 + 신규 5) 전부 통과**(failures=0). region은 nationwide 고정으로 industry 판정만 격리.
- 참고: 배윤성 스크립트(`seed_policy_card_from_policy_v2.py`)는 아직 미실행이라 실 DB 데이터 기반 점수 분포 재측정은 스크립트 실행 후 별도 확인 필요. 코드는 데이터가 채워지는 즉시 동의어 매칭이 동작하도록 준비 완료.

**[검증-빌드]**
- `./gradlew bootRun` 정상 기동(컴파일 에러 없음). 프론트 변경 없음(백엔드 매처 로직만 수정) → 스키마/마이그레이션 변경 없음.
