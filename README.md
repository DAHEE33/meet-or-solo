# meet-or-solo

강원도 축제 현장에서 혼자 방문한 사용자를 2~4인 소그룹으로 즉석 매칭하는 PWA 서비스입니다.

## 네이버 OAuth 환경변수

네이버 로그인은 backend의 `GET /api/auth/naver/login`, `GET /api/auth/naver/callback`을 사용합니다. frontend는 네이버 authorize URL을 직접 만들지 않습니다.

```text
NAVER_CLIENT_ID
NAVER_CLIENT_SECRET
NAVER_REDIRECT_URI
NAVER_AUTHORIZATION_URI
NAVER_TOKEN_URI
NAVER_USER_INFO_URI
NAVER_CONNECT_TIMEOUT
NAVER_READ_TIMEOUT
```

local callback은 `http://localhost:8080/api/auth/naver/callback`입니다. dev는 현재 공개 도메인 없이 `http://<DEV_SERVER_HOST>:18080/api/auth/naver/callback` placeholder를 사용하며, prod URL은 운영 도메인이 확정된 뒤 정확히 등록합니다. 실제 Secret과 서버 주소는 저장소에 기록하지 않습니다.

이 저장소는 모노레포 구조로 관리합니다.

```text
meet-or-solo/
├─ .github/
├─ backend/
├─ frontend/
├─ infra/
├─ docs/
├─ AGENTS.md
└─ CLAUDE.md
```

## 현재 상태

현재 완료된 단계는 0~8-3단계입니다.

- 0단계: 프로젝트 방향/문서화 완료
- 1단계: Backend + Local PostgreSQL + Flyway 확인 완료
- 2단계: local/dev/prod 실행 전략 정리 완료
- 3단계: Frontend PWA 기본 스캐폴딩 + `/api/health` 연동 완료
- 4단계: Backend 공통 코드화 완료
- 5단계: Frontend 공통 코드화 완료
- 6단계: Oracle VM dev 서버/dev DB 구축 준비 문서화 완료
- 7단계: nginx + docker-compose dev 배포 초안 완료
- 8-1단계: GitHub Actions CI 초안 완료
- 8-2단계: GitHub Actions dev CD 초안 완료
- 8-3단계: Oracle VM dev 배포 수동 검증 완료

9-1단계 실제 서비스 DB 설계 검토/확정 문서화가 완료되었습니다. 다음 작업은 승인된 설계 기준의 9-2단계 Flyway migration SQL 작성입니다.

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

기능 분업 전까지 공통 개발환경, dev 배포 초안, CI/CD 초안은 정리된 상태입니다. 실제 기능 구현 또는 DB migration을 시작하기 전에는 [docs/10_PROGRESS_LOG.md](docs/10_PROGRESS_LOG.md)를 확인하고 별도 승인 후 진행합니다.

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
- [docs/11_DATABASE_DESIGN.md](docs/11_DATABASE_DESIGN.md)

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

현재 local 개발은 backend, PostgreSQL, Flyway, `/api/health`, frontend PWA 기본 스캐폴딩과 `/api/health` 연동 확인 화면을 기준으로 합니다.
dev nginx/docker-compose 템플릿은 7단계에서 추가했고, GitHub Actions CI/dev CD 초안은 8단계에서 추가했습니다. `docker-compose.prod.yml`, prod nginx 설정, prod 배포 workflow는 아직 생성하지 않습니다.

### 날짜·시간 기준

- PostgreSQL 날짜 컬럼은 `TIMESTAMPTZ`를 유지하고 Database/Session timezone은 `Asia/Seoul`을 사용합니다.
- JVM, Hibernate JDBC, Jackson, local/dev container의 timezone은 `Asia/Seoul`로 통일합니다.
- REST API는 ISO-8601과 `+09:00` offset을 사용합니다.
- 사용자 화면은 frontend 공통 formatter에서 동일한 절대 시점을 `yyyy-MM-dd HH:mm:ss` 형식으로 표시합니다.
- 날짜에 9시간을 직접 더하지 않습니다.

기존 local/dev DB의 기본 timezone은 각각 `scripts/set-local-db-timezone.sql`, `scripts/set-dev-db-timezone.sql`을 실행한 뒤 새 연결에서 확인합니다. 이 SQL은 기존 `TIMESTAMPTZ` 값을 변경하지 않습니다.

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
CORS_ALLOWED_ORIGINS
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

Spring Boot 실행 시 Flyway가 `backend/src/main/resources/db/migration`의 migration을 classpath에서 적용합니다. Flyway location은 `classpath:db/migration` 기준이며, migration SQL은 backend jar 내부에 포함됩니다. 현재 `V1__init.sql`은 실제 도메인 테이블이 아니라 schema 검증용 placeholder 테이블만 생성합니다.

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

4단계에서 backend `HealthController`는 공통 `ApiResponse` 포맷을 적용했습니다. 5단계에서 frontend `apiClient`, `ApiResponse<T>` 타입, `healthApi`, `HealthCheckPage`를 새 응답 구조에 맞게 수정했습니다.

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

local 개발에서는 `VITE_API_BASE_URL`을 비워두고 Vite dev server proxy를 사용합니다. frontend API 호출은 기본적으로 `/api/...` 상대 경로를 사용합니다. 운영 또는 서버 환경 예시는 `frontend/.env.production.example`에 placeholder로만 둡니다. 실제 IP, 도메인, API Key, Secret은 저장소에 커밋하지 않습니다.

frontend는 backend 공통 응답인 `ApiResponse<T>`를 기준으로 API 응답을 처리합니다.

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

## Oracle VM dev 서버/dev DB 준비 기준

6단계에서는 실제 Oracle VM 접속이나 배포 자동화를 수행하지 않고, 팀원이 함께 확인할 dev 서버와 dev DB 구성 기준만 문서화했습니다. 현재 서버 배포 대상은 `dev`만이며, `prod`는 추후 제출/운영 단계에서 별도 디렉터리, 별도 DB, 별도 도메인 또는 외부 DB 서비스로 분리합니다.

예정 dev 서버 구조:

```text
/home/ubuntu/meet-or-solo/
├─ backend/
│  └─ app.jar
├─ frontend/
│  └─ dist/
├─ nginx/
│  └─ default.conf
├─ data/
│  └─ postgres/
├─ logs/
└─ .env
```

위 구조는 6단계 기준 문서화용입니다. 7단계에서 dev nginx/docker-compose 템플릿을 추가했고, GitHub Actions workflow 파일은 8단계에서 별도 승인 후 초안으로 작성합니다.

dev DB 기준:

- DB 이름 예시는 `meet_or_solo_dev`를 사용합니다.
- DB user/password는 실제 값을 저장소에 기록하지 않고 `.env` 또는 GitHub Secrets에서 주입합니다.
- PostgreSQL `5432`는 외부 전체 공개를 하지 않습니다.
- backend와 PostgreSQL은 같은 VM 내부 네트워크 또는 localhost 경계에서 통신합니다.
- 팀원이 dev DB에 직접 접근해야 하면 SSH tunnel을 사용합니다.
- dev compose는 PostgreSQL을 외부 공개 없이 서버 내부 `127.0.0.1:15432`에만 publish합니다.

backend `dev` profile 기준 환경변수:

```text
SPRING_PROFILES_ACTIVE=dev
DB_URL
DB_USERNAME
DB_PASSWORD
CORS_ALLOWED_ORIGINS
SERVER_PORT
```

frontend 기준:

- local 개발은 `npm run dev`와 Vite proxy를 사용합니다.
- dev 서버 배포는 `npm run build` 결과물인 `frontend/dist`를 사용합니다.
- `frontend/dist/`는 build 결과물이므로 Git에 커밋하지 않습니다.
- dev 서버에서는 nginx가 `frontend/dist`를 서빙하고 `/api` 요청은 backend로 reverse proxy하는 방향입니다.

## nginx + docker-compose dev 배포 초안

7단계에서는 Oracle VM dev 서버에서 사용할 수 있는 dev 배포 템플릿만 repository 안에 준비했습니다. 실제 Oracle VM 접속, 실제 배포, GitHub Actions 생성, prod 설정 생성은 아직 하지 않습니다.

추가된 dev 배포 템플릿:

```text
infra/
├─ docker/
│  └─ docker-compose.dev.yml
├─ env/
│  └─ .env.dev.example
└─ nginx/
   └─ default.dev.conf
```

dev compose 구성:

- `postgres`: `postgres:16-alpine`, compose 내부 network 전용, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` 환경변수 주입
- `backend`: Spring Boot jar를 `/app/app.jar`로 mount해 실행, `SPRING_PROFILES_ACTIVE=dev`, `DB_URL`은 `postgres` service name 기준
- `nginx`: `frontend/dist` 정적 파일 서빙, `/api` 요청을 `backend:8080`으로 reverse proxy, 외부 `18080` 포트만 publish

포트 기준:

- 외부 공개: nginx `18080`
- 외부 직접 공개 금지: backend `8080`, PostgreSQL `5432`
- 서버 내부 loopback 전용: PostgreSQL tunnel 목적지 `127.0.0.1:15432`
- 기존 Ubuntu nginx 또는 다른 서비스가 host `80`을 사용할 수 있으므로 현재 meet-or-solo dev는 host `80`을 사용하지 않습니다.

배포 산출물 기준:

- backend는 `backend/build/libs/*.jar` 산출물을 dev 서버의 `backend/app.jar`로 배치합니다.
- frontend는 `npm run build` 결과물인 `frontend/dist`를 dev 서버의 `frontend/dist`로 배치합니다.
- `app.jar`, `frontend/dist`, logs, PostgreSQL data는 Git에 커밋하지 않습니다.
- 실제 서버 `.env`는 Oracle VM에서 서버 관리자가 직접 생성합니다.

dev 환경변수 예시는 `infra/env/.env.dev.example`에만 둡니다. 실제 비밀번호, 실제 IP, 실제 도메인, API Key, Secret은 repository에 기록하지 않습니다.

## GitHub Actions CI 초안

8-1단계에서는 자동 배포 없이 backend와 frontend build를 검증하는 CI workflow만 추가했습니다.

파일:

```text
.github/workflows/ci.yml
```

trigger:

- `pull_request` to `main`
- `push` to `main`

현재 `develop` 브랜치는 확정 전이므로 `main` 중심으로 작성했습니다. 추후 `develop` 브랜치 운영이 확정되면 trigger 대상에 `develop`을 추가할 수 있습니다.

CI job:

- `backend-build`: Java 17 설정 후 `backend` 디렉터리에서 `./gradlew build -x test` 실행
- `frontend-build`: Node.js 20 설정 후 `frontend` 디렉터리에서 `npm ci`, `npm run build` 실행

현재 CI는 compile/build 검증만 수행합니다. `bootRun`, DB 연결, PostgreSQL 컨테이너 실행, Oracle VM 접속, SSH 배포, docker compose 실행은 하지 않습니다. GitHub Secrets, 서버 IP, SSH Key, 실제 배포 Secret은 8-2단계 dev CD 초안에서 placeholder 기준으로 검토합니다.

## GitHub Actions dev CD 초안

8-2단계에서는 `workflow_dispatch` 수동 실행 기준의 dev CD workflow 초안을 추가했습니다. 실제 Oracle VM 접속이나 배포 성공은 수행하거나 가정하지 않습니다.

파일:

```text
.github/workflows/deploy-dev.yml
```

trigger:

- `workflow_dispatch`

필요한 GitHub Secrets 이름:

```text
DEV_SERVER_HOST
DEV_SERVER_USER
DEV_SSH_KEY
DEV_DEPLOY_PATH
```

workflow 동작 방향:

1. backend를 Java 17로 `bootJar -x test` 빌드합니다.
2. frontend를 Node.js 20으로 `npm ci`, `npm run build` 합니다.
3. `backend/app.jar`, `frontend/dist`, `infra/docker/docker-compose.dev.yml`, `infra/nginx/default.dev.conf`를 배포 패키지로 묶습니다.
4. GitHub Secrets의 SSH 정보로 dev 서버에 패키지를 업로드합니다.
5. `DEV_DEPLOY_PATH`에서 압축을 풀고 서버 `.env` 존재 여부를 확인합니다.
6. 서버 `.env`가 있으면 `docker compose --env-file .env -f infra/docker/docker-compose.dev.yml up -d`를 실행하는 초안입니다.

서버 `.env`는 GitHub Actions가 만들지 않습니다. Oracle VM에서 서버 관리자가 직접 생성해야 하며 repository와 workflow에 실제 Secret 값을 기록하지 않습니다. CD 실행 전 Oracle VM에는 Docker, Docker Compose, 배포 디렉터리, 서버 `.env`, 방화벽 정책이 준비되어 있어야 합니다.

## Oracle VM dev 수동 검증 결과

8-3단계에서 Oracle VM dev 배포를 수동 검증했습니다. 실제 서버 IP, DB 비밀번호, Secret 값은 문서에 기록하지 않습니다.

접속 기준:

```text
dev 서버: http://<DEV_SERVER_HOST>:18080
health API: http://<DEV_SERVER_HOST>:18080/api/health
CORS_ALLOWED_ORIGINS=http://<DEV_SERVER_HOST>:18080
```

검증 결과:

- `postgres` 컨테이너: Healthy
- `backend` 컨테이너: Running
- `nginx` 컨테이너: Started
- 서버 내부 `curl http://localhost:18080/api/health` 성공
- 외부 브라우저 `http://<DEV_SERVER_HOST>:18080/api/health` 성공

health 응답:

```json
{"success":true,"data":{"status":"OK","service":"meet-or-solo-backend"},"error":null}
```

현재 Oracle VM에서는 기존 Ubuntu nginx 또는 기존 서비스가 host `80`을 사용할 수 있으므로 meet-or-solo dev는 host `18080`을 사용합니다. Oracle Cloud Ingress에서 `18080` 포트가 열려 있어야 하며, backend `8080`과 PostgreSQL `5432`는 외부에 직접 공개하지 않습니다.

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

현재 frontend는 local 개발에서 상대 경로 `/api/health`를 호출하고, Vite proxy가 backend `http://localhost:8080/api/health`로 전달합니다. `healthApi`는 공통 `apiClient`를 통해 backend `ApiResponse<T>` wrapper를 해석하고, `HealthCheckPage`는 `data.status`, `data.service` 값을 표시합니다.

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

Flyway는 Spring Boot 실행 시 `flyway_schema_history` 테이블을 확인하고, 아직 적용되지 않은 migration SQL을 자동 실행합니다. 표준 migration 위치는 `backend/src/main/resources/db/migration`이며 Spring Boot/Flyway 기본 classpath 경로인 `classpath:db/migration`을 사용합니다. 현재 `V1__init.sql`은 DB/Flyway 연결 확인용 초기 migration이며, 실제 서비스 테이블은 `V2`, `V3`, `V4` 파일로 추가되어 있습니다. DB 설계 기준은 [docs/11_DATABASE_DESIGN.md](docs/11_DATABASE_DESIGN.md)를 따릅니다.

backend jar에 migration SQL이 포함되는지 확인합니다.

```bash
cd backend
./gradlew clean build -x test
jar tf build/libs/*.jar | grep db/migration
```

기대 결과:

```text
BOOT-INF/classes/db/migration/V1__init.sql
BOOT-INF/classes/db/migration/V2__create_core_tables.sql
BOOT-INF/classes/db/migration/V3__create_matching_tables.sql
BOOT-INF/classes/db/migration/V4__create_safety_admin_recommendation_tables.sql
```

dev 서버 재배포 후 backend 로그에서 Flyway 인식 여부를 확인합니다.

```bash
docker logs meet-or-solo-backend-dev --tail=300
docker logs meet-or-solo-backend-dev 2>&1 | grep -i flyway
docker logs meet-or-solo-backend-dev 2>&1 | grep -i "migrating schema"
```

PostgreSQL 컨테이너에 접속해 Flyway 적용 이력을 확인합니다.

```bash
docker exec -it meet-or-solo-postgres-local psql -U <LOCAL_DB_USERNAME> -d <LOCAL_DB_NAME>
```

```sql
select * from flyway_schema_history;
```

dev DB에서 Flyway 적용 이력과 테이블 목록은 아래 SQL로 확인합니다.

```sql
select installed_rank, version, description, script, success
from flyway_schema_history
order by installed_rank;
```

```sql
select table_schema, table_name
from information_schema.tables
where table_schema not in ('pg_catalog', 'information_schema')
order by table_schema, table_name;
```

이미 적용된 `V1`~`V4` migration 파일은 수정하지 않습니다. 변경이 필요하면 `V5__...sql`처럼 새 migration 파일을 추가합니다. 루트 `db/migration`은 더 이상 사용하지 않으며, dev 배포 시 migration SQL을 별도로 서버에 복사하거나 mount하지 않습니다.

### 아직 구현하지 않은 기능

- `docker-compose.prod.yml`
- 실제 Oracle VM 배포
- prod nginx 설정
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
