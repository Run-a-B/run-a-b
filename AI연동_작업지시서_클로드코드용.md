# AI 추천 연동 작업 지시서 (Claude Code 작업용)

## ⚠️ 최우선 규칙
- **기존 백엔드 로직은 절대 수정하지 않는다.** (EligibilityMatcher, 인증/JWT, SecurityConfig, 회원가입/로그인, 정책 동기화 등 기존 도메인 코드 일체 손대지 말 것)
- 이번 작업 범위는 **AI Gateway 연동 + 연동 테스트 검증**으로 한정한다.
- 시간이 매우 급한 상황(시연 영상 촬영 직전)이므로, 새로운 기능을 추가하거나 리팩토링하지 말고 **연동 확인에만 집중**한다.
- 막히면 추측해서 임의로 코드를 바꾸지 말고, 에러 로그와 현재 상태를 그대로 보고할 것.

---

## 1. 현재 상태 (이미 완료된 작업)

다음 파일들은 이미 작성 완료되어 main에 머지되어 있다 (또는 머지 예정):

- `api/src/main/java/com/runab/api/service/recommend/AiGatewayClient.java`
  - `RestClient`로 AI Gateway에 POST 요청을 보내는 클라이언트
  - 생성자에서 `${ai.gateway.base-url}`, `${ai.gateway.api-key}`, `${ai.gateway.timeout-seconds:180}` 주입받음
  - `recommend(AiRecommendRequest request)` 메서드가 `/v1/policy/recommendations`로 POST 후 `JsonNode` 응답 반환
  - 실패 시 `BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE)` throw

- `api/src/main/java/com/runab/api/controller/RecommendController.java`
  - `POST /api/v1/recommendations/preview` — 조립된 요청 미리보기 (기존)
  - `POST /api/v1/recommendations` — 실제 AI 호출까지 수행하는 신규 엔드포인트 (인증된 사용자, ACCESS 토큰 필요)

- `api/src/main/resources/application.properties`에 다음 설정 추가됨:
  ```properties
  ai.gateway.base-url=${AI_GATEWAY_BASE_URL}
  ai.gateway.api-key=${RUNAB_AI_API_KEY}
  ai.gateway.timeout-seconds=180
  ```

- `RecommendRequestAssembler`, `CandidateSelector`, `AiRecommendRequest` 등 요청 조립 로직은 이전 단계에서 이미 완성되어 있고 정상 동작 중 (수정 금지).

---

## 2. AI 서버 접속 정보 (이번 세션 한정, RunPod 켜져있는 동안만 유효)

```
AI_GATEWAY_BASE_URL=https://iz5tf8t98nexil-8888.proxy.runpod.net
RUNAB_AI_API_KEY=mFR20Wuv0ogB_Q_TpRl8QihJdEG6pKyOJ-T987lN9MU
```

- 엔드포인트: `POST {AI_GATEWAY_BASE_URL}/v1/policy/recommendations`
- 인증 헤더: `Authorization: Bearer {RUNAB_AI_API_KEY}`
- AI 서버 헬스체크: `GET {AI_GATEWAY_BASE_URL}/health` (단, 모델 로딩 완료 여부와 무관하게 항상 200 ok를 반환하므로 신뢰하지 말 것 — 실제 추천 호출로 확인해야 함)
- AI 서버는 Qwen3.6-27B 베이스 모델 + LoRA 어댑터(ranker, reporter) 2개를 Transformers+PEFT 4bit로 직접 로드하는 구조라서, **서버 기동 직후 첫 호출까지 모델 로딩 시간이 걸릴 수 있고, 추론 자체도 느릴 수 있음 (수십 초 이상 가능)**. 타임아웃은 이미 180초로 넉넉하게 잡혀있음.

---

## 3. 이번 작업에서 해야 할 일 (순서대로)

### Step 1. 현재 브랜치 상태 확인
```bash
git status
git log --oneline -5
```
`feat/ai-recommend` 브랜치에 위 3개 파일 변경사항이 커밋되어 있는지 확인. 안 되어 있으면 커밋부터 진행.

### Step 2. PR 생성 및 main 머지
- 이 레포는 main에 직접 push가 금지되어 있고 (`Changes must be made through a pull request` 규칙), merge commit도 금지되어 있다 (`This branch must not contain merge commits`).
- 따라서 반드시: `git push origin feat/ai-recommend` → GitHub에서 PR 생성 (`feat/ai-recommend → main`) → 승인 후 머지.
- 머지되면 GitHub Actions(`.github/workflows/deploy.yml`)가 자동으로 트리거되어 EC2에 새 jar가 배포되고 `systemctl restart runab-api`가 실행된다. **단, 이 자동 배포만으로는 AI 연동이 동작하지 않는다 — Step 3이 별도로 필요하다.**

### Step 3. EC2 systemd 서비스에 AI 환경변수 추가 (가장 중요, 누락 시 서버가 아예 기동 안 됨)

EC2 SSH 접속 (비밀번호 방식, 비밀번호는 별도 전달):
```bash
ssh -o ServerAliveInterval=60 ec2-user@13.209.82.137
```

systemd 서비스 파일 수정:
```bash
sudo nano /etc/systemd/system/runab-api.service
```

`[Service]` 섹션에 기존 `Environment=` 라인들(SPRING_DATASOURCE_PASSWORD, GOOGLE_CLIENT_ID, MAIL_APP_PASSWORD)이 있을 것이다. **이 라인들은 절대 삭제하거나 수정하지 말고**, 그 아래에 다음 두 줄을 추가한다:

```ini
Environment="AI_GATEWAY_BASE_URL=https://iz5tf8t98nexil-8888.proxy.runpod.net"
Environment="RUNAB_AI_API_KEY=mFR20Wuv0ogB_Q_TpRl8QihJdEG6pKyOJ-T987lN9MU"
```

저장 후:
```bash
sudo systemctl daemon-reload
sudo systemctl restart runab-api
sudo systemctl status runab-api
```

`Active: active (running)`인지 확인. 만약 `failed`나 `activating (auto-restart)`로 반복되면, 환경변수 placeholder(`${AI_GATEWAY_BASE_URL}` 등)를 못 찾아서 Spring이 기동 실패한 것이니 systemd 파일에 오타가 없는지 다시 확인할 것.

로그 확인:
```bash
sudo journalctl -u runab-api -n 50 --no-pager
```
`Started ApiApplication in ...` 로그가 보이면 정상.

### Step 4. 실제 AI 연동 테스트

테스트 대상 사용자는 사업정보(BusinessInfo)가 이미 등록되어 있어야 한다. 테스트 계정으로 로그인 후 JWT access token을 발급받는다.

```bash
# 1) 로그인해서 access token 발급
curl -X POST https://run-a-b.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"테스트계정이메일","password":"테스트계정비밀번호"}'
```

응답에서 `access_token` 값을 복사한다.

```bash
# 2) 조립 미리보기로 먼저 후보가 정상 조립되는지 확인 (AI 호출 전 단계)
curl -X POST https://run-a-b.com/api/v1/recommendations/preview \
  -H "Authorization: Bearer {access_token}" \
  -H "Content-Type: application/json"
```

`candidate_policies` 배열이 비어있지 않은지 확인. 비어있으면 그 사용자의 BusinessInfo가 등록 안 되어 있는 것이니, 사업정보 먼저 등록해야 한다 (기존 마이페이지/회원가입 플로우 그대로 사용, 새로 만들지 말 것).

```bash
# 3) 실제 AI 추천 호출 (진짜 테스트)
curl -X POST https://run-a-b.com/api/v1/recommendations \
  -H "Authorization: Bearer {access_token}" \
  -H "Content-Type: application/json" \
  --max-time 200
```

### Step 5. 결과 검증 체크리스트

- [ ] HTTP 200 응답이 오는가
- [ ] 응답 JSON에 `recommended_policies` 배열이 있는가
- [ ] 각 추천 항목에 `rank`, `policy_id`, `fit_score`, `reason`이 채워져 있는가
- [ ] `business_impact_report`, `preparation_order`, `document_checklist_backend_payload`가 있는가 (AI 서버 fallback 로직이 작동했다면 이 구조 그대로 채워짐 — fallback이어도 시연용으로는 정상)
- [ ] 응답 시간이 너무 오래 걸려서 타임아웃(180초) 안에 끝나는지

### Step 6. 실패 시 진단 순서

1. **EC2 백엔드 로그 확인**
   ```bash
   sudo journalctl -u runab-api -n 100 --no-pager | grep -i "AiGateway"
   ```
   `[AiGateway] 추천 요청 전송`, `[AiGateway] 추천 응답 수신 완료` 또는 `[AiGateway] 추천 요청 실패` 로그를 확인. 실패 로그의 `error=` 메시지를 그대로 보고할 것.

2. **AI 서버 자체가 응답하는지 직접 확인** (EC2 안에서, 또는 로컬에서)
   ```bash
   curl -i https://iz5tf8t98nexil-8888.proxy.runpod.net/health
   ```
   이게 응답 안 하면 AI 서버(RunPod) 자체 문제이니 AI 담당자(배윤성)에게 즉시 알려야 한다 — 백엔드 코드 문제가 아님.

3. **401 Unauthorized가 뜨면** → API 키 불일치. AI 서버 쪽 `RUNAB_AI_API_KEY` 환경변수와 백엔드 systemd의 값이 정확히 일치하는지 다시 확인 (공백, 따옴표 오타 주의).

4. **타임아웃(504/timeout)이 뜨면** → AI 서버 추론이 180초를 넘긴 것. 일단 시연용으로는 어쩔 수 없는 부분이니, 무리하게 코드를 고치지 말고 "이번 호출은 시간 초과되었다"고 그대로 보고할 것. (코드의 timeout-seconds 값을 임의로 늘리는 것은 괜찮으나, 그 외 로직 변경은 하지 말 것)

---

## 4. 하지 말아야 할 것 (재확인)

- `EligibilityMatcher`, `CandidateSelector`, `RecommendRequestAssembler` 등 기존 매칭/조립 로직 수정 금지
- `SecurityConfig`의 인증 규칙 변경 금지 (이미 `/api/v1/recommendations`는 ACCESS 토큰 필요하도록 잘 잡혀 있음)
- AI 응답을 DB에 저장하는 기능(Report/Application 엔티티)은 이번 작업 범위 아님 — 만들지 말 것. 지금은 AI 응답을 그대로 JSON으로 반환만 하면 된다.
- 프론트엔드 코드 수정 금지 (프론트 연동은 별도 단계)
- `.env`나 systemd 환경변수에 들어간 비밀번호/API 키를 로그에 출력하거나 커밋에 포함시키지 말 것

---

## 5. 작업 완료 후 보고 형식

다음 내용을 정리해서 보고할 것:
1. PR 머지 완료 여부 + PR 번호
2. EC2 systemd 환경변수 추가 + 서비스 재시작 결과 (`active (running)` 여부)
3. Step 4의 curl 테스트 결과 (성공 시 응답 JSON 일부, 실패 시 에러 로그 전문)
4. 추천 1회 호출에 걸린 시간 (대략적인 초 단위)
5. 시연 영상 촬영에 바로 사용 가능한 상태인지 최종 확인
