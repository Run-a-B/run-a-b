# Run-a-B

소상공인에게 맞는 정책과 지원사업을 추천하고 AI 요약·리포트를 제공하는 서비스입니다.

## 구성

- `apps/run-a-b-fe`: React 19 + TypeScript + Vite 프론트엔드
- `api`: Java 21 + Spring Boot 3.5 백엔드
- `compose.yaml`: 로컬 MySQL 8.4 (`localhost:3307`)

## 처음 실행하기

### 1. 준비물

- JDK 21
- Node.js 22 이상 및 npm
- Docker Desktop + Docker Compose v2
- Make

버전 확인:

```bash
java -version
node --version
npm --version
docker compose version
make --version
```

### 2. 설치

저장소 루트에서 실행합니다.

```bash
cp .env.example .env
make setup
```

`make setup`은 다음 작업을 수행합니다.

1. 프론트엔드 패키지 설치
2. MySQL 컨테이너 시작 및 `runab_db` 생성
3. 백엔드 Gradle 의존성 준비와 컴파일

`.env`가 없으면 `make setup`이 `.env.example`을 자동 복사하므로 첫 번째 명령은 생략해도 됩니다.

### 3. 개발 서버 시작

```bash
make dev
```

- 프론트엔드: <http://localhost:5173>
- 백엔드 API: <http://localhost:8082>
- MySQL: `127.0.0.1:3307`, database `runab_db`

종료할 때는 `Ctrl+C`를 누릅니다. 데이터베이스 컨테이너는 데이터 보존을 위해 계속 실행됩니다.

정책 데이터가 비어 있다면 백엔드 실행 후 기업마당 동기화를 한 번 호출할 수 있습니다.

```bash
curl -X POST http://localhost:8082/api/v1/policies/sync
```

## 환경 변수

로컬 설정은 루트의 `.env`에서 관리합니다. `.env`는 Git에 포함되지 않습니다.

기본 목록/상세 화면과 일반 API 서버는 외부 서비스 키 없이 실행됩니다. 아래 기능을 사용하려면 `.env`의 값을 채운 뒤 `make dev`를 다시 시작하세요.

| 기능 | 환경 변수 |
|---|---|
| 로컬 API 포트/주소 | `SERVER_PORT`, `VITE_API_BASE_URL` |
| Google 로그인 | `GOOGLE_CLIENT_ID`, `VITE_GOOGLE_CLIENT_ID` |
| 이메일 인증 | `MAIL_USERNAME`, `MAIL_APP_PASSWORD` |
| AI 정책 요약·리포트 | `OPENAI_API_KEY`, 선택 사항 `OPENAI_MODEL` |
| 기업마당 정책 동기화 | `BIZINFO_API_KEY` |

Gmail은 일반 비밀번호가 아닌 Google 계정의 앱 비밀번호를 사용해야 합니다. 실제 운영 환경에서는 `JWT_SECRET`, 데이터베이스 비밀번호, 모든 외부 서비스 키를 별도 비밀 관리 시스템으로 주입하세요.

## 검증

백엔드 전체 테스트, 프론트엔드 lint, 프론트엔드 production build를 한 번에 실행합니다.

```bash
make check
```

개별 실행:

```bash
cd api
GRADLE_USER_HOME="$PWD/.gradle" ./gradlew test

cd ../apps/run-a-b-fe
npm test
npm run lint
npm run build
```

백엔드 테스트는 H2 메모리 데이터베이스를 사용하므로 MySQL을 켜지 않아도 실행됩니다.

`npm audit` 기준으로 high 취약점은 제거했습니다. React Router v6 관련 moderate 취약점 2건은 v7 메이저 업그레이드가 필요하므로 자동 `--force` 수정은 적용하지 않았습니다.

## 데이터베이스 관리

```bash
# 시작
make db-up

# 컨테이너만 종료 (데이터 보존)
make db-down

# 컨테이너와 로컬 DB 볼륨까지 완전히 삭제
docker compose down -v
```

기존 로컬 MySQL의 기본 포트 `3306`과 충돌하지 않도록 Compose MySQL은 `3307`을 사용합니다. 포트가 이미 사용 중이라면 `.env`의 `DB_URL`과 `compose.yaml`의 공개 포트를 같은 값으로 변경하세요.

## 협업 규칙

브랜치, 커밋, Pull Request 규칙은 [.github/README.md](.github/README.md)를 참고하세요.
