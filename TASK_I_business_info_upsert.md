# 작업 지시서 I: 사업정보 최초 저장 시 404 버그 수정 (구글 로그인 사용자 영향)

## 배경
- Run a B, 발표 2026.07.03. 독립 브랜치로 작업. EC2 배포는 팀원이 수동으로 — 이 작업은 커밋+push(+가능하면 PR)까지만.
- 실사이트에서 구글 로그인 계정으로 마이페이지 > 내 사업정보 입력 후 "저장" 시 실패, 브라우저 콘솔에 `PATCH /api/v1/users/me/business` 404, alert에 "사업 정보를 찾을 수 없습니다" 표시됨. (Cross-Origin-Opener-Policy 경고는 구글 로그인 팝업 관련 브라우저 표준 경고로 무관하니 무시할 것)

## 원인 (코드 확인 완료)
`api/src/main/java/com/runab/api/service/UserService.java`의 `updateMyBusinessInfo()`:
```java
BusinessInfo info = businessInfoRepository.findByUserId(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_INFO_NOT_FOUND));
```
**"기존 사업정보 수정"만 처리하고 "최초 생성"은 처리하지 않음.** 일반 이메일 회원가입은 2단계(`POST /api/v1/auth/signup/business`)에서 `BusinessInfo`가 미리 생성되지만, **구글 로그인 가입 플로우는 이 생성 단계를 거치지 않아 `BusinessInfo` row 자체가 없는 상태로 남음.** 이 상태에서 마이페이지에서 처음 저장을 시도하면 "수정할 대상이 없다"며 404가 발생.

즉 **구글 로그인으로 가입한 사용자는 전부 사업정보를 영구적으로 저장할 수 없는 상태**였음 (배윤성 계정 한정 문제 아님).

## 요구사항 — Upsert(없으면 생성, 있으면 수정)로 변경

`UserService.updateMyBusinessInfo()`를 아래처럼 수정:
```java
BusinessInfo info = businessInfoRepository.findByUserId(userId)
        .orElseGet(() -> businessInfoRepository.save(
                BusinessInfo.builder().user(/* userId로 User 조회 or 참조 */).build()
        ));
```
(정확한 구현은 `BusinessInfo` 엔티티의 `@Builder` 생성자와 세터 금지 컨벤션에 맞춰서, `UserRepository`로 `User` 엔티티를 조회한 뒤 새 `BusinessInfo`를 생성 후 저장하는 방식으로 작성할 것. 이후 기존 `info.update(...)` 로직은 그대로 재사용)

**주의할 점:**
- `businessStatus`, `jobCategory`, `region`은 `BusinessInfo` 엔티티상 `nullable = false`임. 최초 생성 시 프론트에서 필수값이 다 채워져서 오는지 확인(현재 `MyBusinessPage.tsx`의 `handleSave()`가 `status`, `industry`, `region`을 항상 보내는지 재확인 — 비어있는 채로 저장 시도하면 어떻게 되는지도 체크).
- 이미 있는 `getMyBusinessInfo()`(GET, 조회용)는 **그대로 두고 손대지 말 것** — 이건 "사업정보 없으면 404"가 맞는 정상 동작(프론트도 `.catch(() => {})`로 조용히 무시하고 빈 폼 보여주는 용도로 이미 설계되어 있음). **PATCH(저장)만 upsert로 바꾸는 것.**
- `POST /api/v1/auth/signup/business`(이메일 가입 2단계) 로직은 이번 범위 아님, 손대지 말 것.

## 검증
- (가능하면) 합성 테스트로 `BusinessInfo`가 없는 신규 유저 상태에서 PATCH 호출 시 정상적으로 새로 생성되고 200 응답 오는지 확인
- 기존에 `BusinessInfo`가 있던 유저가 PATCH 호출 시 기존과 동일하게 수정되는지(회귀 없는지) 확인
- 로컬 MySQL이 손상된 상태면 Task F/G/H 때처럼 임시 DB로 검증하고 그 사실을 작업 로그에 남길 것

## 컨벤션
- `ErrorCode(String code, int status, String message)` 형식
- 엔티티 세터 금지, 비즈니스 메서드/빌더로만 생성·변경
- API 응답 `{ success, data, message, error }`, `ApiResponse.success()`

## Git 워크플로우
- 브랜치: `fix/business-info-upsert`
- 커밋: 한글, `fix: ...` 형식
- push까지, 가능하면 PR 생성 (리뷰어 jaeseong09), **main 직접 머지 금지**

## 완료 조건
- [ ] `BusinessInfo`가 없는 사용자(구글 로그인 등)가 마이페이지에서 최초 저장 시 정상적으로 생성됨 (404 안 남)
- [ ] 기존에 `BusinessInfo` 있던 사용자의 수정 동작은 회귀 없음
- [ ] `getMyBusinessInfo()`(GET) 동작은 변경 없음
- [ ] 기존 테스트 통과, `./gradlew bootRun` 정상
- [ ] 커밋 + push 완료

## 작업 로그
> 발견한 문제, 진단 결과, 수정 내역을 시간순으로 기록.

### 2026-07-01 — 브랜치 `fix/business-info-upsert` (main 기준)

**[원인 재확인]** `UserService.updateMyBusinessInfo()`가 `findByUserId(...).orElseThrow(BUSINESS_INFO_NOT_FOUND)`로 수정만 처리 → BusinessInfo row가 없는 사용자(구글 로그인 가입: signup 2단계 `POST /auth/signup/business`를 안 거침)는 최초 저장 시 404. 확정.

**[수정] PATCH를 upsert로 변경 (백엔드 UserService만 수정)**
- `updateMyBusinessInfo()`: `findByUserId(...).orElseGet(() -> createBusinessInfo(userId, request))`로 없으면 생성. 생성/수정 공통으로 기존 `info.update(...)` 재사용(세터 금지 컨벤션 유지, businessStatus=false면 매출/직원수 null 규칙 그대로 적용).
- `createBusinessInfo()` 신규 private: `businessStatus/jobCategory/region`이 엔티티상 `nullable=false`라 **최초 생성 시에만** 필수값 검증 후 `UserRepository`로 User 조회→`BusinessInfo.builder().user(user)...build()` 저장. 검증 실패 시 기존 ErrorCode(`INVALID_BUSINESS_STATUS`/`INVALID_JOB_CATEGORY`/`INVALID_REGION`, 400)로 명확히 응답(널 businessStatus로 인한 500·NOT NULL 위반 방지).
- `getMyBusinessInfo()`(GET)와 `POST /auth/signup/business`는 미변경(요구사항대로).

**[프론트 확인]** `MyBusinessPage.tsx handleSave()`는 `businessStatus`(항상 boolean), `jobCategory`, `region`을 항상 전송(빈 미선택 시 `""` 가능). 빈 필수값으로 최초 저장 시 이제 500이 아니라 400(INVALID_*)로 안내됨. 프론트 변경 없음.

**[검증]** 로컬 MySQL(3306) InnoDB 손상 지속 → 임시 MySQL(3309, 새 datadir)로 검증. 실 데이터 미접촉, 검증 후 삭제. (JWT는 앱 시크릿으로 발급)
- (1) 9301(구글, business_info 없음) GET → **404**(기존 조회 동작 유지).
- (2) 9301 최초 PATCH(필수값+매출/직원수 포함) → **200, row 신규 생성**(이전엔 404 나던 케이스 해결).
- (3) 9301 GET 재조회 → **200**(생성 확인).
- (4) 9301 재-PATCH(region 경기도로) → **200 수정**(생성된 row에 대한 수정 경로 정상).
- (5) 9302(기존 business_info 보유) PATCH businessStatus=false → **200**, 매출/직원수 `null`로 정리됨(false→null 규칙 회귀 없음).
- (6) 9303 최초 저장인데 jobCategory `""` → **400 INVALID_JOB_CATEGORY**("직군값이 올바르지 않습니다"), row 미생성 확인.
- DB 최종: business_info에 9301·9302만 존재(9303 미생성). `EligibilityMatcherTest` 등 기존 테스트 통과, `./gradlew bootRun` 정상 기동.

**⚠️ 참고**: 로컬 MySQL(3306)은 이전 작업 중 InnoDB(ibdata1) 손상으로 여전히 기동 불가 — 이번에도 임시 DB로 검증(실 데이터 미접촉).
