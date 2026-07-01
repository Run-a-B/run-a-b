# 작업 지시서 G: 업종 필터 실연결 + 관련도 높은순 정렬 구현

## 배경
- Run a B, 발표 2026.07.03. 독립 브랜치로 작업. EC2 배포는 팀원이 수동으로 — 이 작업은 커밋+push(+가능하면 PR)까지만.
- 전제조건: 배윤성의 rule-based extractor(`seed_policy_card_from_policy_v2.py`)가 이미 실행되어 `policy_card.full_json`의 `eligibility.regions`/`industries`가 대부분 채워진 상태(1,479건 중 regions 1,116건, industries 1,243건 확인됨). Task F(PR #40, `IndustryHierarchy` 업종 동의어 매핑 + "전업종" 예외처리)도 이미 머지됨.
- 이번 작업은 이 두 가지가 준비된 상태를 전제로, **아직 안 쓰이고 있던 policy_card 데이터를 목록 페이지 필터/정렬에도 연결**하는 작업.

---

## 문제 G-1: 정책 목록의 "업종" 드롭다운 필터가 실질적으로 작동 안 함

### 배경 (Task E에서 진단 완료된 내용)
`Policy.industry` 컬럼은 bizinfo 동기화 시점에 전부 `"전체"`로 하드코딩됨(`PolicySyncService` 주석: `// 자격요건 매핑은 policy_card 단계에서 처리`). 그래서 사용자가 업종을 뭘 선택하든 결과가 안 줄어듦 (Task E 작업 로그에 SQL 검증 기록 있음, 코드 버그 아니라 데이터 부재 문제였음).

### 수정 방향
정책 목록 API(`PolicyController.getPolicies` → `PolicyService.getPolicies`)의 업종 필터링을 `Policy.industry` 컬럼 대신 **`policy_card.eligibility.industries`를 `IndustryHierarchy`로 판정**하도록 변경:

1. `PolicySpecification.industryMatches()`(SQL 레벨 필터)는 더 이상 이 필터에 안 쓰거나, `Policy.industry`가 "전체"인 건 그대로 두고 특정 업종 값이 채워진 것도 있으면 그건 유지하는 정도로만 남겨둘 것(향후 대비, 완전히 제거하지 말 것).
2. `PolicyService`에서 업종 필터가 지정된 경우(`"전체 업종"`이 아닌 경우), 지역/카테고리/검색어로 1차 필터링된 `Policy` 목록에 대해 각각의 `PolicyCard`(`PolicyCard.toPolicyId()` 패턴 재사용, `PolicyCardRepository.findByPolicyId()`)를 조회해서 `IndustryHierarchy.sameIndustry(선택된업종, card의 industries)`로 2차 필터링(자바 레벨)을 수행.
   - **주의**: 이 필터는 로그인 사용자의 사업정보와 무관하게, **드롭다운에서 명시적으로 선택한 업종값** 기준으로 판정하는 것임 (관련도 계산과는 별개 로직).
   - `IndustryHierarchy`에 관련도 계산용 메서드(`sameIndustry(userIndustry, cardIndustries)` 형태일 것)가 이미 있다면 그대로 재사용, 시그니처가 안 맞으면 오버로드 추가.
3. `PolicyCard`가 없거나 `industries`가 비어있는 정책은 필터에서 제외 (업종을 특정해서 선택했다는 건 "이 업종에 해당하는 것만 보고 싶다"는 의도이므로, 판단 불가한 건 안 보여주는 게 맞음 — 관련도 계산 때의 UNKNOWN 처리와는 다른 맥락).

---

## 문제 G-2: "관련도 높은순" 정렬이 실제로는 작동 안 함 (최신순과 동일하게 동작)

### 원인
`PolicySpecification.sortBy()`에 이미 남아있던 TODO 주석에서 확인됨:
```java
// TODO: "관련도 높은순"은 관련도(Relevance) 도메인 구현 후 실제 관련도 정렬로 교체 예정
```
관련도는 DB 컬럼이 아니라 **요청 시점에 자바 코드(`EligibilityMatcher`)가 계산하는 값**이라, 지금 구조(SQL이 먼저 페이지네이션까지 끝낸 다음 그 페이지 안의 항목만 채점)로는 SQL 레벨에서 관련도 순 정렬이 불가능함. 그래서 "관련도 높은순"을 선택해도 실제로는 `publishedDate DESC`(최신순)로 폴백되고 있음.

### 수정 방향 — 페이지네이션 순서를 바꿔야 함
정렬 기준이 `"관련도 높은순"`이고 **로그인 + 사업정보 등록된 사용자**인 경우에 한해:

1. 지역/업종/카테고리/검색어로 1차 필터링된 `Policy` 후보군 **전체**를 (페이지네이션 없이) 조회
2. 후보군 전체에 대해 `EligibilityMatcher.match()`로 채점 (해당 정책들의 `PolicyCard`를 `IN` 쿼리로 한 번에 조회 — `PolicyService`에 이미 있는 N+1 방지 패턴 재사용)
3. 점수 기준 내림차순 정렬
4. 그 다음에 **자바 레벨에서** 요청된 페이지(`page`, `size`)만큼 잘라서 반환

**성능 고려**: 현재 정책 전체가 약 1,500건 수준이라 이 방식(전체 로드 후 자바 정렬)이 지금 규모에서는 문제없음. 다만 데이터가 수만 건 이상으로 늘어나면 이 방식은 안 맞으므로, 코드에 주석으로 "정책 수가 크게 늘어나면(예: 1만 건 이상) DB 레벨 정렬이나 별도 점수 캐싱 테이블 고려 필요"라고 남겨둘 것.

**비로그인 사용자이거나 사업정보 미등록**인 경우 "관련도 높은순"을 선택하면 → 관련도 자체가 0으로 고정이라 정렬 의미가 없으므로, 이 경우엔 최신순으로 폴백하는 현재 동작을 유지해도 됨 (다만 프론트에 "로그인하면 관련도순 정렬을 쓸 수 있어요" 같은 안내를 붙일지는 판단해서 가볍게 처리, 큰 구조 변경 필요하면 넘어가도 됨).

---

## 문제 G-3 (사소함, 여유 있으면): "최신순" 동일 날짜 내 정렬 불안정

`published_date`가 같은 날짜인 정책이 많음(예: 2026-06-26에 55건). `PolicySpecification.sortBy()`의 "최신순" 케이스에 `publishedDate DESC` 다음 2차 정렬 키로 `id DESC` 정도만 추가해서, 같은 날짜 내에서도 매번 일관된 순서가 나오게 할 것. 아주 작은 수정이라 여유 되면 같이 처리.

---

## 컨벤션
- `ErrorCode(String code, int status, String message)` 형식
- 엔티티 세터 금지, 비즈니스 메서드로만
- API 응답 `{ success, data, message, error }`, `ApiResponse.success()`
- 최신 main 기준으로 브랜치 딸 것 (Task F, PR #40 머지 확인 후 진행)

## Git 워크플로우
- 브랜치: `feat/industry-filter-and-relevance-sort`
- 커밋: 한글, `feat: ...` 형식
- push까지, 가능하면 PR 생성 (리뷰어 jaeseong09), **main 직접 머지 금지**

## 완료 조건
- [ ] 업종 드롭다운에서 특정 업종 선택 시 실제로 결과 개수가 줄어듦 (curl/브라우저 검증, 몇 건에서 몇 건으로 줄었는지 작업 로그에 기록)
- [ ] 로그인 + 사업정보 등록 사용자가 "관련도 높은순" 선택 시, 실제로 관련도 % 내림차순으로 정렬되어 나옴 (1페이지 첫 항목이 실제로 최고점인지 검증)
- [ ] (여유 있으면) 최신순 정렬이 동일 날짜 내에서도 안정적으로 동일한 순서 유지
- [ ] 기존 테스트 통과, `./gradlew bootRun` / `npx tsc -b` 정상 (프론트 변경 있다면)
- [ ] 커밋 + push 완료

## 작업 로그
> 발견한 문제, 진단 결과, 수정 내역을 시간순으로 기록.

### 2026-07-01 — 브랜치 `feat/industry-filter-and-relevance-sort`

**[사전] Task F(PR #40) MERGED 확인** → 최신 main(IndustryHierarchy 포함) 기준으로 브랜치 생성.

**[G-1] 업종 필터를 policy_card 기반 자바 필터로 전환**
- `PolicyService.getPolicies()` 재구성: 특정 업종 선택 시(≠"전체 업종") 지역/카테고리/검색어로 1차 필터한 후보 전체를 로드하고, 각 정책의 `PolicyCard`(IN 조회, 기존 `loadCards` N+1 방지 재사용)에 대해 `matchesIndustryFilter`로 2차 필터(자바 레벨) 후 자바 페이지네이션.
- `EligibilityMatcher.matchesIndustryFilter(card, selectedIndustry)` 신규: 기존 `evalIndustry`(전업종 all + IndustryHierarchy 동의어)를 재사용해 `== PASS`만 통과. 카드 없음/industries 빈 배열 → 제외(지시서 G-1-3).
- `PolicySpecification.industryMatches()`는 삭제하지 않고 남겨둠(Policy.industry 실데이터 대비). 단 getPolicies 스펙 체인에서는 미사용 처리(주석 명시).

**[G-2] "관련도 높은순" 실제 정렬 구현**
- 로그인 + 사업정보 등록 사용자가 "관련도 높은순" 선택 시: 후보 전체 로드 → 카드 IN 조회 → 전체 채점(EligibilityMatcher.match) → 점수 내림차순(동점 시 publishedDate desc → id desc) 정렬 → 자바 레벨 페이지네이션.
- 비로그인/사업정보 미등록이면 관련도 0 고정이라 정렬 의미 없음 → 최신순 폴백(기존 동작 유지). 프론트 안내 문구는 선택사항이라 이번엔 생략(백엔드가 기능 완비, 큰 구조변경 회피).
- 성능 주석 추가: 정책 ~1,500건 규모에선 전체 로드 후 자바 정렬 OK, 1만 건↑이면 DB 정렬/점수 캐싱 테이블 고려.

**[G-3] 최신순 동일 날짜 안정 정렬**
- `PolicySpecification.sortBy()`의 최신순/마감임박순 정렬에 마지막 키로 `id DESC` 추가 → 동일 날짜 내 일관된 순서.

**[검증] 로컬 MySQL(3306)이 검증 도중 InnoDB 손상(ibdata1)으로 다운 → 별도 임시 MySQL(3307, 새 datadir)로 앱 구동해 검증(실 데이터 미손상, 검증 후 임시 인스턴스/데이터 삭제). 정책 8건 + 합성 카드 8개 + 사용자(서울/IT) 시드.**
- G-1 업종 필터(총 8건 기준):
  - 전체 업종 → 8, **뷰티/미용 → 4**(미용업 3 + 전업종 1), **제조업 → 2**(제조업 1 + 전업종 1), **IT/소프트웨어 → 4**(정보통신업 3 + 전업종 1), **카페/음료 → 1**(전업종만). → 선택 시 실제로 개수 감소 + 동의어/전업종 정상.
- G-2 관련도 높은순(사용자 서울/IT):
  - `(id,관련도)=[(5,85),(6,85),(7,85),(1,57),(3,57),(2,57),(4,57),(8,57)]` → **내림차순, 1페이지 첫 항목=최고점 85**.
  - 대조) 같은 사용자 최신순=`[1,3,2,4,5,6,7,8]`(날짜순, 85점들이 중간에 섞임) → 관련도 정렬이 실제로 재정렬함을 확인.
  - 비로그인 관련도순 → 관련도 전부 0, 최신순 폴백 순서 유지.
- G-3 최신순: p2·p3 동일 날짜(2026-06-29)에서 `id DESC`로 3→2 순서 일관 확인(`[1,3,2,...]`).
- 단위테스트: `EligibilityMatcherTest`에 `matchesIndustryFilter` 계약 테스트 1개 추가 → 총 **11개 전부 통과**.
- `./gradlew bootRun` 정상 기동(임시 DB). 프론트 변경 없음 → tsc 불필요. 스키마 변경 없음(마이그레이션 불필요).

**⚠️ 환경 이슈(코드 무관): 로컬 MySQL(3306)의 InnoDB 시스템 테이블스페이스(ibdata1)가 손상돼 기동 불가 상태.** 검증 중 부하로 크래시하며 발생한 것으로 보임. 실 데이터 보존을 위해 강제복구/데이터 삭제는 하지 않음 — 팀에서 백업 복원 또는 `innodb_force_recovery`로 덤프 후 재초기화 필요(별도 조치).
