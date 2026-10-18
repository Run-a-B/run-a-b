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

의존성 취약점은 `npm audit`로 별도 확인하세요. 현재 React Router v6 관련 advisory는 v7 메이저 업그레이드와 회귀 검증이 필요하므로 자동 `--force` 수정은 적용하지 않습니다.

## 운영 환경 준비

운영 서비스 환경에 `SPRING_PROFILES_ACTIVE=prod`와 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`를 필수로 주입하세요. 로컬 `.env`는 `make dev`에서 읽으며, 운영 JAR가 자동으로 읽지 않습니다. JWT 키는 32바이트 이상의 무작위 비밀값을 사용하고 DB는 최소 권한 계정과 사설망으로 접근하세요.

`prod` 프로필은 스키마를 변경하지 않고 `validate`하므로 배포 전에 검토한 마이그레이션을 적용해야 합니다. 현재 저장소에는 운영용 마이그레이션과 백업/복원 자동화가 없습니다. 기존 DB 복제본의 스키마 검증과 별도 DB로의 백업 복원 확인을 먼저 수행하세요. Compose의 MySQL은 로컬 개발용이며 `127.0.0.1:3307`에만 게시됩니다. 이 변경은 컨테이너를 다시 만들 때 적용됩니다.

프론트엔드는 production build에서 같은 origin의 `/api/`를 호출합니다. 웹서버에 `/api/`의 백엔드 프록시와 SPA deep link fallback, TLS를 구성하세요. Google 로그인은 빌드 시 `VITE_GOOGLE_CLIENT_ID`와 백엔드 `GOOGLE_CLIENT_ID`를 같은 앱으로 맞춰야 합니다. 이메일은 `MAIL_USERNAME`/`MAIL_APP_PASSWORD`, AI는 `OPENAI_API_KEY`/`OPENAI_MODEL`, 정책 수집은 `BIZINFO_API_KEY`를 설정하고 기능별 실제 호출을 검증하세요.

GitHub Actions는 PR에서 백엔드 테스트·빌드와 프론트 테스트·lint·빌드를 검사하고 main push에서 해당 검사 통과 후 배포합니다. 배포용 GitHub secrets는 `EC2_HOST`, `EC2_USER`, `EC2_PASSWORD`, `EC2_KNOWN_HOSTS`, `VITE_GOOGLE_CLIENT_ID`입니다. `EC2_KNOWN_HOSTS`에는 `EC2_HOST`와 일치하는 SSH known_hosts 항목을 넣고 AWS 콘솔 등 별도로 신뢰할 수 있는 경로에서 서버 공개키 지문을 대조하세요. 배포 시 네트워크에서 받은 키를 검증 없이 등록하지 마세요. 호스트 키가 없거나 변경되면 배포가 중단되므로 계획된 키 교체 시 지문을 다시 검증한 뒤 secret을 갱신해야 합니다. 배포 사전 검사는 필수 자격 증명과 고정 호스트 항목의 존재를 확인하며 실제 접속은 `StrictHostKeyChecking=yes`로 검증합니다.

저장소 branch protection의 필수 검사 지정은 별도로 설정해야 합니다. 운영 systemd unit/Nginx 설정, 원자적 배포/rollback, 정기 수집·추출기, 장애 알림도 별도로 준비해야 합니다. 배포 워크플로는 운영 프로필/비밀값을 서버에 자동 설정하지 않습니다.

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
