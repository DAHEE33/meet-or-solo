# meet-or-solo

강원도 축제 현장에서 혼자 방문한 사용자를 2~4인 소그룹으로 즉석 매칭하는 PWA 서비스입니다.

이 저장소는 모노레포 구조로 관리합니다.

```text
meet-or-solo/
├─ .github/
├─ backend/
├─ db/
├─ frontend/
├─ infra/
├─ docs/
├─ AGENTS.md
└─ CLAUDE.md
```

## 현재 상태

현재 완료된 단계는 0~4단계입니다.

- 0단계: 프로젝트 방향/문서화 완료
- 1단계: Backend + Local PostgreSQL + Flyway 확인 완료
- 2단계: local/dev/prod 실행 전략 정리 완료
- 3단계: Frontend PWA 기본 스캐폴딩 + `/api/health` 연동 완료
- 4단계: Backend 공통 코드화 완료

다음 작업은 5단계 Frontend 공통 코드화입니다. 이후 6단계 Oracle VM dev 서버/dev DB 구축, 7단계 nginx/docker-compose dev 배포, 8단계 GitHub Actions CI/CD 초안 순서로 진행합니다.

자세한 단계 순서와 남은 작업은 [docs/10_PROGRESS_LOG.md](docs/10_PROGRESS_LOG.md)를 확인합니다.

아직 다음 항목은 실제 구현 범위가 아닙니다.

- 실제 서비스 화면
- Kakao OAuth2 로그인
- JWT 인증
- 관광공사 OpenAPI 실제 연동
- GPS 체크인
- 자동 매칭
- WebSocket STOMP 이벤트 구현
- 관리자 기능
- Redis 구성
- 운영 배포 자동화 완성

## WBS 진행 기준

새 작업을 시작하기 전에는 [docs/10_PROGRESS_LOG.md](docs/10_PROGRESS_LOG.md)를 확인하고 현재 단계에 맞는 작업만 진행합니다.

기능 분업 전까지 남은 작업:

1. Frontend 공통 코드화
2. Oracle VM dev 서버/dev DB 구축
3. nginx + docker-compose dev 배포 초안
4. GitHub Actions CI/CD 초안

## 문서

작업 전 반드시 아래 문서를 확인합니다.

- [AGENTS.md](AGENTS.md)
- [CLAUDE.md](CLAUDE.md)
- [docs/00_PROJECT_OVERVIEW.md](docs/00_PROJECT_OVERVIEW.md)
- [docs/01_ARCHITECTURE.md](docs/01_ARCHITECTURE.md)
- [docs/02_DEV_ENVIRONMENT.md](docs/02_DEV_ENVIRONMENT.md)
- [docs/03_FRONTEND_GUIDE.md](docs/03_FRONTEND_GUIDE.md)
- [docs/04_BACKEND_GUIDE.md](docs/04_BACKEND_GUIDE.md)
- [docs/05_MATCHING_POLICY.md](docs/05_MATCHING_POLICY.md)
- [docs/06_SECURITY_POLICY.md](docs/06_SECURITY_POLICY.md)
- [docs/07_DEPLOYMENT.md](docs/07_DEPLOYMENT.md)
- [docs/08_AI_WORKING_RULES.md](docs/08_AI_WORKING_RULES.md)
- [docs/09_TEST_AND_QUALITY_STRATEGY.md](docs/09_TEST_AND_QUALITY_STRATEGY.md)
- [docs/10_PROGRESS_LOG.md](docs/10_PROGRESS_LOG.md)

## 문서 작성 언어

이 프로젝트의 기본 문서 언어는 한국어입니다.

`README.md`, `docs/*.md`, `AGENTS.md`, `CLAUDE.md`의 본문은 한국어로 작성합니다. 기술 용어, 파일명, 클래스명, 함수명, 환경변수명, 명령어, 경로는 영어 원문을 유지합니다.

## 초기 개발환경 범위

완료된 초기 개발환경 범위는 다음과 같습니다.

- 모노레포 구조 정리
- backend Spring Boot 실행 구조 정리
- frontend React + TypeScript + Vite + PWA 스캐폴딩
- PostgreSQL docker-compose 구성
- Flyway 기본 설정
- `/api/health`
- React에서 `/api/health` 호출
- README 실행 방법 정리

Backend/Frontend 공통 코드화 단계에서는 비즈니스 기능을 구현하지 않습니다.

## 로컬 backend 개발환경

이번 단계에서는 backend, PostgreSQL, Flyway, `/api/health`, frontend PWA 기본 스캐폴딩과 `/api/health` 연동 확인 화면만 구성합니다.
nginx, `docker-compose.prod.yml`, GitHub Actions는 아직 생성하거나 수정하지 않습니다.

### 필요 도구

- Java 17
- Docker Compose
- backend Gradle Wrapper: `backend/gradlew`

backend는 Spring Boot 3.5, Java 17, Gradle 기반입니다. 현재 의존성은 Spring Web, Validation, Spring Data JPA, PostgreSQL Driver, Flyway, Lombok을 포함합니다.

### 환경변수 준비

루트의 `.env.example`을 참고해 로컬 전용 `.env`를 만듭니다.

```bash
cp .env.example .env
```

`.env`의 `POSTGRES_PASSWORD`는 로컬 개발용 값으로 변경해서 사용합니다. 실제 운영 비밀번호, API Key, Secret, 서버 IP, 도메인은 저장소에 커밋하지 않습니다.

필요한 로컬 환경변수:

```text
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD
DB_HOST
DB_PORT
SPRING_PROFILES_ACTIVE
```

`dev`와 `prod` profile은 다음 환경변수를 사용하도록 placeholder로만 구성되어 있습니다.

```text
DB_URL
DB_USERNAME
DB_PASSWORD
FLYWAY_LOCATIONS
SERVER_PORT
```

profile 용도:

- `local`: 개인 PC의 Docker PostgreSQL에 연결합니다.
- `dev`: Oracle Cloud VM의 개발/시연용 DB에 연결합니다. 초기 서버 배포는 이 profile을 사용합니다.
- `prod`: 추후 제출/운영 단계에서 별도 DB와 도메인으로 분리하기 위해 유지합니다. 현재 VM 배포 대상은 아닙니다.

### 로컬 실행 주의사항

- `docker compose`는 프로젝트 루트에서 실행합니다.
- `docker compose`는 `--env-file .env`를 통해 `.env`를 읽습니다.
- Spring Boot `bootRun`은 `.env`를 자동으로 읽지 않습니다.
- PowerShell과 Git Bash의 환경변수는 서로 공유되지 않습니다.
- Git Bash에서 `source .env`를 했다면 같은 Git Bash 터미널에서 `./gradlew bootRun`까지 실행해야 합니다.
- PowerShell에서 실행할 경우 `application-local.yml` fallback 값으로 실행하거나, PowerShell 환경변수를 직접 설정해야 합니다.

로컬 기본값은 아래 기준으로 둡니다.

```text
DB_URL=jdbc:postgresql://localhost:5432/meet_or_solo_local
DB_USERNAME=meet_user
DB_PASSWORD=meet_password
```

### PostgreSQL 컨테이너 실행

로컬 개발용 PostgreSQL만 실행합니다. Redis는 MVP 초기 단계에서 제외합니다.

```bash
docker compose -f docker-compose.local.yml --env-file .env up -d
```

상태 확인:

```bash
docker compose -f docker-compose.local.yml --env-file .env ps
```

중지:

```bash
docker compose -f docker-compose.local.yml down
```

### backend 실행

backend는 기본 profile이 `local`입니다. `.env`에 작성한 DB 값을 backend 실행 환경에도 export한 뒤 실행합니다.

```bash
cd backend
set -a
source ../.env
set +a
./gradlew bootRun
```

명시적으로 local profile을 지정하려면 다음처럼 실행합니다.

```bash
cd backend
set -a
source ../.env
set +a
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Spring Boot 실행 시 Flyway가 `db/migration`의 migration을 적용합니다. 현재 `V1__init.sql`은 실제 도메인 테이블이 아니라 schema 검증용 placeholder 테이블만 생성합니다.

실행 로그에서 `Flyway` 또는 `Migrating schema` 관련 메시지를 확인해 migration 적용 여부를 확인합니다.

### `/api/health` 확인

backend 실행 후 다음 명령으로 확인합니다.

```bash
curl http://localhost:8080/api/health
```

예상 응답:

```json
{
  "success": true,
  "data": {
    "status": "OK",
    "service": "meet-or-solo-backend"
  },
  "error": null
}
```

4단계에서 backend `HealthController`는 공통 `ApiResponse` 포맷을 적용했습니다. 현재 frontend `healthApi`와 `HealthCheckPage`는 기존 health 응답 형태를 기준으로 작성되어 있으므로, 5단계 Frontend 공통 코드화에서 새 `ApiResponse` 포맷에 맞게 수정해야 합니다.

## 로컬 frontend 개발환경

frontend는 React + TypeScript + Vite 기반 PWA로 구성합니다. 현재 화면은 실제 서비스 UI가 아니라 backend `/api/health` 응답을 확인하는 개발용 화면입니다.

### 필요 도구

- Node.js LTS
- npm

### frontend 환경변수 준비

`frontend/.env.local.example`을 참고해 로컬 전용 `frontend/.env.local`을 만듭니다.

```bash
cd frontend
cp .env.local.example .env.local
```

로컬 기본값:

```text
VITE_API_BASE_URL=
```

local 개발에서는 `VITE_API_BASE_URL`을 비워두고 Vite dev server proxy를 사용합니다. 운영 또는 서버 환경 예시는 `frontend/.env.production.example`에 placeholder로만 둡니다. 실제 IP, 도메인, API Key, Secret은 저장소에 커밋하지 않습니다.

### frontend 의존성 설치

```bash
cd frontend
npm install
```

### frontend dev server 실행

```bash
cd frontend
npm run dev
```

Vite dev server가 안내하는 URL로 접속하면 개발용 HealthCheck 화면이 표시됩니다.

local 개발에서 frontend dev server는 `/api` 요청을 backend로 전달합니다.

```text
Browser -> http://localhost:5173/api -> Vite proxy -> http://localhost:8080/api
```

`frontend/vite.config.ts`의 proxy 설정을 바꾸면 `npm run dev`를 재시작해야 합니다.

## 팀원 로컬 실행 가이드

처음 repository를 받은 팀원은 아래 순서로 local 개발환경을 실행합니다. 개인 `.env` 파일은 로컬에서만 사용하고 커밋하지 않습니다.

### 1. repository 준비

```bash
git clone <REPOSITORY_URL>
cd meet-or-solo
```

`<REPOSITORY_URL>`은 실제 원격 저장소가 확정된 뒤 사용하는 값입니다. 실제 서버 IP, 도메인, Secret은 문서나 코드에 기록하지 않습니다.

### 2. 환경변수 파일 준비

PowerShell:

```powershell
Copy-Item .env.example .env
Copy-Item frontend/.env.local.example frontend/.env.local
```

Git Bash:

```bash
cp .env.example .env
cp frontend/.env.local.example frontend/.env.local
```

`frontend/.env.local`의 local 기본값은 `VITE_API_BASE_URL=`입니다. local 개발에서는 frontend가 `/api/health` 상대 경로를 호출하고 Vite proxy가 backend로 전달합니다.

### 3. Docker PostgreSQL 실행

Docker Desktop이 실행 중이어야 합니다. 프로젝트 루트에서 local PostgreSQL을 시작합니다.

```bash
docker compose -f docker-compose.local.yml --env-file .env up -d
```

`5432` 포트가 이미 사용 중이면 기존 PostgreSQL을 끄거나 `docker-compose.local.yml`과 `.env` 기준 포트 구성을 조정해야 합니다.

### 4. backend 실행

PowerShell에서는 `application-local.yml` fallback 값으로 실행하거나 필요한 환경변수를 PowerShell에 직접 설정합니다.

```powershell
cd backend
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

Git Bash에서는 `.env`를 export한 터미널에서 backend를 실행합니다.

```bash
cd backend
set -a
source ../.env
set +a
./gradlew bootRun --args='--spring.profiles.active=local'
```

backend 직접 확인:

```bash
curl http://localhost:8080/api/health
```

예상 응답:

```json
{
  "success": true,
  "data": {
    "status": "OK",
    "service": "meet-or-solo-backend"
  },
  "error": null
}
```

### 5. frontend 실행

backend와 다른 터미널에서 실행합니다.

```bash
cd frontend
npm install
npm run dev
```

브라우저에서 다음 주소로 접속합니다.

```text
http://localhost:5173/
```

HealthCheck 카드에 `연결 성공`, `status=OK`, `service=meet-or-solo-backend`가 표시되면 frontend-backend 연동 확인이 완료된 것입니다.

frontend 화면에서 404가 나면 `frontend/vite.config.ts`의 Vite proxy 설정과 frontend dev server 재시작 여부를 확인합니다. proxy 설정을 바꾼 뒤에는 반드시 `npm run dev`를 다시 실행합니다.

### backend + frontend health 연동 확인

1. 로컬 PostgreSQL 컨테이너를 실행합니다.

```bash
docker compose -f docker-compose.local.yml --env-file .env up -d
```

2. backend를 `local` profile로 실행합니다.

```bash
cd backend
set -a
source ../.env
set +a
./gradlew bootRun --args='--spring.profiles.active=local'
```

3. 다른 터미널에서 frontend를 실행합니다.

```bash
cd frontend
cp .env.local.example .env.local
npm install
npm run dev
```

4. 브라우저에서 `http://localhost:5173/`에 접속해 `연결 성공`, `status`, `service` 값이 표시되는지 확인합니다.

현재 frontend는 local 개발에서 상대 경로 `/api/health`를 호출하고, Vite proxy가 backend `http://localhost:8080/api/health`로 전달합니다.

### 로컬 DB와 Flyway 확인

로컬 PostgreSQL 컨테이너 상태를 확인합니다.

```bash
docker compose -f docker-compose.local.yml --env-file .env ps
```

backend를 `local` profile로 실행합니다.

```bash
cd backend
set -a
source ../.env
set +a
./gradlew bootRun --args='--spring.profiles.active=local'
```

health endpoint를 확인합니다.

```bash
curl http://localhost:8080/api/health
```

Flyway는 Spring Boot 실행 시 `flyway_schema_history` 테이블을 확인하고, 아직 적용되지 않은 migration SQL을 자동 실행합니다. 현재 `V1__init.sql`은 DB/Flyway 연결 확인용 초기 migration이며, 실제 서비스 테이블은 이후 `V2`, `V3` 파일로 추가합니다.

PostgreSQL 컨테이너에 접속해 Flyway 적용 이력을 확인합니다.

```bash
docker exec -it meet-or-solo-postgres-local psql -U <LOCAL_DB_USERNAME> -d <LOCAL_DB_NAME>
```

```sql
select * from flyway_schema_history;
```

이미 적용된 `V1`, `V2` 같은 migration 파일은 수정하지 않습니다. 변경이 필요하면 `V3`, `V4`처럼 새 migration 파일을 추가합니다.

### 아직 구현하지 않은 기능

- nginx reverse proxy
- `docker-compose.prod.yml`
- GitHub Actions
- 실제 서비스 화면
- Kakao OAuth2 로그인
- JWT 인증/인가
- 관광공사 OpenAPI 실제 연동
- GPS 체크인
- 자동 매칭
- WebSocket STOMP 상태 동기화
- 관리자 기능
- Redis 구성
- 테스트 코드 추가
