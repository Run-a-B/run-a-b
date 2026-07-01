# PolicyCard + EligibilityMatcher + AI 요청 조립 구현 지시서 (Run a B 백엔드)

> 목표: AI 추천 호출 직전까지의 백엔드 파이프라인 구현.
> [1] PolicyCard 엔티티(자격요건 저장) → [2] EligibilityMatcher(점수 계산) → [3] 30개 선별 → [4] AI 요청 JSON 조립
> 기존 Policy/User 도메인 컨벤션을 그대로 따른다.

---

## 0. 전체 맥락 (왜 이걸 만드는가)

```
사용자가 "추천받기" 요청
  → 백엔드: PolicyCard(자격요건)들과 사용자 BusinessInfo 비교 (EligibilityMatcher)
  → 각 정책에 match 점수 부여
  → 상위 30개 선별 (점수순 20 + 애매 5 + 목표카테고리 5)
  → user_business_info + candidate_policies(card + match) JSON 조립
  → [다음 단계] AI Gateway로 전송 (이번 지시서엔 전송 코드 미포함, 조립까지만)
```

**중요한 현재 상태:**
- PolicyCard(정책 자격요건) 데이터는 **아직 DB에 없음.** AI extractor가 batch로 채워줄 예정(배포 후).
- 그래서 지금은 **PolicyCard가 비어있어도 matcher가 동작**해야 함 → 자격요건 없으면 `unknown` 처리.
- BusinessInfo(사용자)도 필드가 5개뿐(아래) → 없는 항목은 `unknown`.
- **즉 지금은 "구조를 만들어 두는 것"이 목적.** 데이터가 채워지면 자동으로 의미있는 점수가 나오게.

---

## 1. 현재 가진 데이터 (실제 엔티티 기준)

### BusinessInfo (사용자) — 이것만 있음
| 필드 | 타입 | matcher 매핑 |
|---|---|---|
| `businessStatus` | Boolean (true=사업중) | business_stage |
| `jobCategory` | String | industry |
| `region` | String | region |
| `annualRevenue` | Long (nullable) | revenue |
| `employeeCount` | Integer (nullable) | employees |

→ owner_type, owner_age, business_months, tax_arrears, credit_guarantee, main_business_goal 등은 **BusinessInfo에 없음** → matcher에서 전부 `unknown` 처리.

### Policy (화면용) — 자격요건 없음
- region, industry, category, applicationStartDate, applicationEndDate 등만 있음.
- 자격요건(max_revenue, business_types 등)은 **PolicyCard에 별도 저장**(아래 신규).

---

## 2. [1] PolicyCard 엔티티 (신규)

policy_card_full(AI extractor 결과)을 저장. **JSON 통째 + 핵심 필드만 컬럼**.

### 파일: `entity/PolicyCard.java`

| 필드 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| id | id | Long | PK |
| policyId | policy_id | String(200), unique | 매칭키. `bizinfo_support_program:{pblancId}` 형식 |
| externalId | external_id | String(100) | 우리 Policy.externalId(`PBLN_...`)와 연결용 |
| applicationStatus | application_status | String(20) | open / closed / unknown (조회·필터용으로 빼둠) |
| extractionStatus | extraction_status | String(30) | extracted / partially_extracted / failed |
| fullJson | full_json | **JSON 타입** (`columnDefinition = "JSON"`) | policy_card_full 통째로 저장 |
| createdAt | created_at | LocalDateTime | @PrePersist |
| updatedAt | updated_at | LocalDateTime | @PrePersist/@PreUpdate |

- 컨벤션: `@Getter` + `@NoArgsConstructor(PROTECTED)` + `@Builder`, setter 금지.
- `fullJson`은 String으로 받아서 저장(JSON 문자열). MySQL JSON 컬럼.
- 비즈니스 메서드: `updateCard(String fullJson, String applicationStatus, String extractionStatus)` (extractor 재실행 시 갱신용).

### 파일: `repository/PolicyCardRepository.java`
- `extends JpaRepository<PolicyCard, Long>`
- `Optional<PolicyCard> findByPolicyId(String policyId)`
- `Optional<PolicyCard> findByExternalId(String externalId)`
- `List<PolicyCard> findByApplicationStatusNot(String status)` (closed 제외 조회용 — 선택)

### policy_card_full JSON 구조 (참고 — 파싱 대상)
matcher가 fullJson을 파싱해서 아래 eligibility를 읽는다. (없으면 unknown)
```json
{
  "policy_id": "bizinfo_support_program:PBLN_...",
  "category": "금융",
  "application_period": { "start": "...", "end": "...", "status": "open" },
  "benefit": { "benefit_type": "loan_low_interest", ... },
  "eligibility": {
    "regions": [], "region_condition_type": "unknown",
    "industries": [], "industry_condition_type": "unknown",
    "business_types": ["중소기업"],
    "max_revenue": null, "max_employees": null,
    "min_business_months": null, "max_business_months": null,
    "min_age": null, "max_age": null,
    "business_stages": [], "owner_types": [],
    "required_flags": [], "excluded_targets": []
  },
  "required_documents_from_notice": [],
  "attachment_analysis": { "status": "pending_hwp_parse" }
}
```

---

## 3. [2] EligibilityMatcher (핵심 — 점수 계산)

### 파일: `service/matcher/EligibilityMatcher.java` (또는 service 하위)

배윤성 확정 공식을 그대로 구현. **순수 계산 로직**(외부 의존성 없음 → 단위테스트 쉬움).

### 입력 / 출력
```
입력: BusinessInfo user, PolicyCard card (의 파싱된 eligibility)
출력: MatchResult (아래 구조)
```

### MatchResult DTO (`dto/recommend/MatchResult.java`)
```json
{
  "matcherVersion": "eligibility_matcher_v1",
  "scoringRuleVersion": "scoring_rule_v1",
  "hardFail": false,
  "score": 88,
  "eligibilityScore": 90,
  "goalScore": 85,
  "documentScore": 80,
  "pass": ["region", "industry", ...],
  "partial": [],
  "unknown": ["tax_arrears", "credit_guarantee", ...],
  "fail": [],
  "notRequired": ["age", "business_months"],
  "requiredFlagCheck": { },
  "hardFailReasons": []
}
```

### status enum (5종 고정 — 별도 enum 클래스로)
`MatchStatus`: `PASS, PARTIAL, FAIL, UNKNOWN, NOT_REQUIRED`
- PASS: 사용자 정보가 정책 조건 충족
- PARTIAL: 완전일치 아니나 넓은 범위 가능성 (예: 업종 대분류만 일치)
- FAIL: 명확히 불일치
- UNKNOWN: 판단에 필요한 사용자 정보 또는 정책 조건이 없음
- NOT_REQUIRED: 정책에 해당 조건 자체가 없음

### 최종 점수 공식
```
score = eligibilityScore * 0.70 + goalScore * 0.20 + documentScore * 0.10
```
(소수점 반올림하여 int)

### eligibilityScore — 항목별 배점 (합 100점)
| 항목 | 배점 | 비교 방법 |
|---|---|---|
| region | 20 | 아래 region 규칙 |
| industry | 20 | 아래 industry 규칙 |
| date | 10 | application_status가 closed면 FAIL(+hardFail), open이면 PASS, 없으면 UNKNOWN |
| business_stage | 10 | card business_stages에 user businessStatus(사업중/준비중) 포함되면 PASS, 비었으면 NOT_REQUIRED |
| business_type | 8 | card business_types 기준. user는 정보 없음→대부분 UNKNOWN (단 "중소기업"류는 소상공인 포함 위계 고려 시 PASS 가능, 아래 참고) |
| owner_type | 8 | user 정보 없음 → UNKNOWN (card에도 없으면 NOT_REQUIRED) |
| revenue | 8 | card max_revenue 있고 user annualRevenue 있으면 비교(이하면 PASS). 둘 중 없으면 UNKNOWN/NOT_REQUIRED |
| employees | 8 | card max_employees vs user employeeCount. 동일 로직 |
| business_months | 4 | user 정보 없음 → UNKNOWN (card 조건 없으면 NOT_REQUIRED) |
| age | 4 | user 정보 없음 → UNKNOWN (card 조건 없으면 NOT_REQUIRED) |

### status별 점수 환산
- PASS → 해당 항목 만점
- PARTIAL → 만점의 50% (배윤성: 40~70% 범위. MVP는 일괄 50%로 고정, 주석 남길 것)
- UNKNOWN → 만점의 40% (배윤성: 30~50%. 단 region/industry는 중요필수라 30%로 낮게. 나머지는 40%)
- NOT_REQUIRED → 해당 항목 만점
- FAIL → 0점

### ⚠️ 반드시 지킬 6규칙 (배윤성 명세)
1. `application_period.status`와 `match.date` 결과가 **반드시 일치** (closed면 date=FAIL)
2. `closed` 정책은 `hardFail=true` + hardFailReasons에 `"application_period.status=closed"`
3. `regions=[] + region_condition_type="unknown"`이면 region을 **PASS 금지** → UNKNOWN
4. `industries=[] + industry_condition_type="unknown"`이면 industry **PASS 금지** → UNKNOWN
5. `required_flags`가 있으면 user와 비교해 `requiredFlagCheck`에 결과 표시(user 정보 없으면 UNKNOWN)
6. `regions=["전국"]`(전국, region_condition_type="nationwide")과 `regions=[]`(미추출) **혼동 금지**

### region 비교 규칙
- card regions가 `["전국"]` (nationwide) → 무조건 PASS, 20점
- card regions에 user region 포함 → PASS, 20점
- card regions에 user region의 권역만 포함(예: card "수도권", user "서울") → PARTIAL (권역 매핑표는 추후, MVP는 단순 일치만 보고 나머지 UNKNOWN)
- card regions 있는데 user region 불포함 → FAIL, 0점, **hardFail=true** (지역 불일치는 hard)
- card regions=[] + unknown → UNKNOWN (규칙3)

### industry 비교 규칙
- card industries 비었고 industry_condition_type="unknown" → UNKNOWN (규칙4)
- card industries에 user jobCategory 포함 → PASS
- card industries 있는데 불포함 → FAIL (단 hardFail은 아님 — 업종은 soft)
- 동의어/표준산업분류 비교는 추후. MVP는 문자열 일치만.

### business_type 위계 (참고)
- "소상공인 ⊂ 중소기업" 위계: card가 "중소기업"이고 user가 소상공인이면 PASS 처리 가능.
- 단 현재 BusinessInfo에 business_type 필드 자체가 없으므로 **MVP에선 UNKNOWN 처리**, 위계 로직은 주석으로 TODO만 남김.

### goalScore — 매핑표 기반
- user main_business_goal이 **현재 BusinessInfo에 없음** → MVP는 goalScore를 **중립값(50)으로 고정**, TODO 주석.
- 매핑표 구조는 만들어 두되(예: Map<goal, Map<benefitType, score>>), 데이터는 배윤성에게 받아 채울 예정.
- card benefit.benefit_type을 읽을 수 있으면 기본 매핑(loan_low_interest 등)만 임시 적용 가능.

### documentScore — attachment_analysis.status 기반
| status | 점수 |
|---|---|
| documents_found | 90 |
| documents_not_found | 50 |
| pending_hwp_parse | 30 |
| attachments_found_text_extraction_failed | 40 |
| crawl_failed | 30 |
| (없음) | 50 |

---

## 4. [3] 30개 선별 (CandidateSelector)

### 파일: `service/recommend/CandidateSelector.java`

matcher로 모든 후보에 점수 매긴 뒤 30개 선별:
```
1. PolicyCard 중 신청 가능한 것 조회 (closed 제외 우선, 단 hard negative용 일부 포함 허용)
2. 각 card에 대해 EligibilityMatcher 실행 → MatchResult
3. hardFail=true인 것 분리
4. 선별:
   - score 상위 20개 (hardFail 아닌 것 중)
   - 애매/hard negative 5개 (partial 많거나 일부 fail 섞인 것)
   - 사용자 목표 카테고리 후보 5개 (goalScore 높은 것 — MVP는 category 기준 등 단순화 가능)
   = 총 30개
5. 30개 미만이면 있는 만큼만
```
- ⚠️ MVP 주의: PolicyCard 데이터가 아직 적으면(또는 0개면) 있는 만큼만 반환. 0개여도 빈 리스트로 정상 동작.
- hard negative 비중은 소수. 대부분은 추천 가능성 있는 후보.

---

## 5. [4] AI 요청 JSON 조립 (RecommendRequestAssembler)

### 파일: `service/recommend/RecommendRequestAssembler.java` + DTO들

최종 명세의 요청 형식대로 조립:

### 요청 DTO 구조 (`dto/recommend/`)
- `AiRecommendRequest` — 최상위 (request_id, reference_date, top_n, pipeline, user_business_info, candidate_policies, output_requirements)
- `UserBusinessInfoDto` — BusinessInfo에서 변환. 없는 필드는 "unknown"/null
- `CandidatePolicyDto` — PolicyCard의 fullJson(파싱) + MatchResult를 합친 것
- `PipelineDto`, `OutputRequirementsDto`

### user_business_info 변환 (BusinessInfo → DTO)
```
region ← businessInfo.region
industry ← businessInfo.jobCategory
annual_revenue ← businessInfo.annualRevenue
employees ← businessInfo.employeeCount
business_stage ← businessInfo.businessStatus ? "운영중" : "준비중"
나머지(district, owner_type, owner_age, tax_arrears_status, credit_guarantee_status,
       main_business_goal, preferred_support_type 등) → "unknown" 또는 null/빈 배열
```

### candidate_policies 조립
- 각 PolicyCard의 fullJson을 객체로 파싱 + 위에서 계산한 MatchResult를 `match` 필드로 붙임
- 결과가 최종 명세의 candidate_policy 형식과 일치해야 함

### request_id / reference_date
- request_id: `"req_" + yyyyMMdd + "_" + 시퀀스/랜덤`
- reference_date: 오늘 날짜 (LocalDate.now())
- top_n: 5
- pipeline.current_pass: "pass1_ranking", tasks: ["pass1_ranking", "pass2_report"]

### 이번 단계 산출물
- `RecommendRequestAssembler.assemble(Long userId)` → `AiRecommendRequest` 객체 반환 (또는 JSON 문자열)
- **AI Gateway 전송은 다음 단계.** 이번엔 조립된 요청을 **로그로 출력하거나 객체로 반환**하는 것까지만.
- (선택) 검증용 임시 엔드포인트 `POST /api/v1/recommendations/preview` — 조립된 JSON을 응답으로 돌려줘서 형식 확인. SecurityConfig는 인증 필요(로그인 사용자 기준).

---

## 6. 작업 순서 & 검증

1. PolicyCard 엔티티 + Repository
2. MatchStatus enum + MatchResult DTO
3. EligibilityMatcher (점수 로직) + **단위 테스트** (자격요건 있는 케이스/없는 케이스/closed 케이스)
4. CandidateSelector (30개 선별)
5. AI 요청 DTO들 + RecommendRequestAssembler
6. (선택) preview 엔드포인트
7. **빌드 확인** (./gradlew build)
8. **검증:**
   - PolicyCard에 테스트 데이터 1~2건 수동 insert (또는 테스트코드로) → matcher 점수 나오는지
   - 자격요건 없는(전부 null) card → 전부 UNKNOWN 처리되고 점수는 나오는지 (예외 안 터짐)
   - closed 정책 → hardFail=true, date=FAIL 확인
   - assemble 호출 → 최종 명세 형식의 JSON이 조립되는지 (필드명·구조 일치)

### 완료 보고
- 생성 파일 목록
- matcher 단위테스트 결과 (케이스별 점수)
- assemble된 샘플 JSON (형식이 최종 명세와 일치하는지)

---

## 7. 절대 하지 말 것

- ❌ PolicyCard에 setter → 비즈니스 메서드(updateCard)만
- ❌ 자격요건 없을 때 예외 던지기 → UNKNOWN으로 안전 처리 (데이터 아직 없는 게 정상)
- ❌ status를 임의 문자열로 → MatchStatus enum 5종만 (PASS/PARTIAL/FAIL/UNKNOWN/NOT_REQUIRED)
- ❌ regions=[]를 "전국"으로 처리 → region_condition_type 봐서 구분 (규칙3·6)
- ❌ closed인데 date를 PASS로 → 반드시 FAIL + hardFail (규칙1·2)
- ❌ ErrorCode 시그니처 변경 → (String, int, String) 유지
- ❌ ApiResponse 새로 만들기 → 기존 것 사용
- ❌ score 계산에서 goalScore/매핑표를 지금 완벽히 채우려 하기 → MVP는 중립값 50 + TODO. 배윤성 매핑표 오면 채움
- ❌ JSON 파싱에 직접 문자열 조작 → Jackson ObjectMapper 사용 (이미 Spring에 있음)
