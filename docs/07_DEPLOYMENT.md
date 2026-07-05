# 배포 방향

## 배포 목표

초기 배포 대상은 Oracle Cloud Ubuntu VM 1대입니다.

현재 배포 문서는 `dev` 환경을 먼저 대상으로 합니다. `prod`는 추후 제출/운영 단계에서 별도 DB, 별도 도메인, 별도 디렉터리 또는 외부 DB 서비스로 분리합니다.

예정 dev 배포 구조:

```text
Nginx
├─ frontend dist 서빙
├─ /api -> Spring Boot proxy
└─ /ws  -> Spring Boot WebSocket STOMP proxy

Spring Boot
└─ PostgreSQL 연결

PostgreSQL
└─ private/local access only
```

Redis는 MVP 초기 배포 구성에 포함하지 않습니다.

## WBS 기준 배포 순서

기능 분업 전 배포 관련 작업은 다음 순서로 진행합니다.

1. [6단계] Oracle VM dev 서버/dev DB 구축 준비
2. [7단계] nginx + docker-compose dev 배포 초안
3. [8-1단계] GitHub Actions CI 초안
4. [8-2단계] GitHub Actions dev CD 초안

이 순서는 기능 구현 전에 팀이 같은 dev 환경에서 frontend/backend 연동, DB 연결, reverse proxy 경계를 확인하기 위한 것입니다.

현재 단계와 다음 작업은 [docs/10_PROGRESS_LOG.md](10_PROGRESS_LOG.md)를 기준으로 확인합니다.

## Oracle Cloud VM

대상 환경:

- Ubuntu VM
- Nginx
- Java runtime
- PostgreSQL
- Certbot
- 필요 시 frontend build artifact 배치

리소스 주의사항:

- 상시 실행 서비스 수를 줄인다.
- Redis는 필요해질 때까지 제외한다.
- disk usage를 모니터링한다.
- JVM memory 설정을 보수적으로 둔다.

## 초기 서버 배포 profile

현재 MVP 초기 단계에서는 Oracle Cloud VM 리소스를 고려하여 `dev` 환경만 서버에 배포합니다.

MVP 초기에는 `/home/ubuntu/meet-or-solo` 하나만 `dev` 서버로 배포합니다. `dev`와 `prod`를 같은 VM에서 처음부터 동시에 운영하지 않습니다.

초기 서버 배포는 다음 환경변수를 사용합니다.

```text
SPRING_PROFILES_ACTIVE=dev
```

`local`, `dev`, `prod` profile은 코드에 유지합니다.

- `local`: 개인 PC Docker PostgreSQL 기준
- `dev`: Oracle Cloud VM의 개발/시연용 DB 기준
- `prod`: 추후 제출/운영 단계에서 별도 디렉터리, 별도 DB, 별도 도메인 또는 외부 DB 서비스로 분리

현재 VM에는 `dev`만 올립니다. `prod`는 지금 배포 대상이 아니며, 실제 운영 경계가 필요해질 때 분리합니다.

`prod`는 추후 운영 필요 시 별도 디렉터리, 별도 DB, 별도 도메인 또는 외부 DB 서비스로 분리합니다. 현재 서버 배포 profile은 `dev`를 기준으로 합니다.

PostgreSQL `5432`는 외부 전체 공개를 하지 않습니다. 개발자가 DB에 직접 접속해야 하면 SSH tunnel 방식을 우선 고려합니다.

## 6단계: Oracle VM dev 서버/dev DB 구축 준비

6단계는 실제 서버 접속이나 파일 배포가 아니라 dev 서버와 dev DB 구성 기준을 팀원이 함께 확인할 수 있게 정리하는 단계입니다.

dev 서버 준비 기준:

- `/home/ubuntu/meet-or-solo` 기준 배포 구조를 사용한다.
- backend는 `SPRING_PROFILES_ACTIVE=dev`로 실행한다.
- PostgreSQL dev DB는 Oracle VM 내부 PostgreSQL을 기준으로 한다.
- PostgreSQL `5432`는 외부 전체 공개를 하지 않는다.
- DB 직접 확인이 필요하면 SSH tunnel을 우선 사용한다.
- `prod`는 아직 만들지 않는다.

실제 서버 IP, 계정, DB 비밀번호, SSH Key는 문서나 repository에 기록하지 않습니다.

예정 dev 서버 폴더 구조:

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

폴더별 기준:

- `backend/app.jar`: backend build artifact 후보입니다.
- `frontend/dist/`: frontend `npm run build` 결과물 후보입니다.
- `nginx/default.conf`: 7단계에서 작성할 nginx dev 설정 후보입니다. 6단계에서는 파일을 만들지 않습니다.
- `data/postgres/`: Oracle VM 내부 PostgreSQL dev data volume 후보입니다.
- `logs/`: dev 서버 로그 보관 후보입니다.
- `.env`: dev 서버 환경변수 파일 후보입니다. 실제 값은 repository에 커밋하지 않습니다.

dev DB 기준:

- DB 이름 예시는 `meet_or_solo_dev`를 사용합니다.
- DB user/password는 실제 값을 하드코딩하지 않고 `.env` 또는 GitHub Secrets에서 주입합니다.
- backend와 PostgreSQL은 같은 VM 내부 네트워크 또는 localhost 경계에서 통신합니다.
- 외부에서 PostgreSQL `5432`로 직접 접속하는 구성을 만들지 않습니다.
- 팀원이 dev DB를 확인해야 하면 SSH tunnel을 사용합니다.

SSH tunnel 예시는 placeholder만 사용합니다.

```bash
ssh -L 15432:localhost:5432 <SSH_USER>@<DEV_SERVER_HOST>
```

backend `dev` profile 기준 환경변수:

```text
SPRING_PROFILES_ACTIVE=dev
DB_URL
DB_USERNAME
DB_PASSWORD
CORS_ALLOWED_ORIGINS
FLYWAY_LOCATIONS
SERVER_PORT
```

`DB_URL` 형식 예시는 아래처럼만 둡니다.

```text
jdbc:postgresql://<INTERNAL_DB_HOST>:5432/meet_or_solo_dev
```

`<INTERNAL_DB_HOST>`는 7단계 nginx/docker-compose dev 배포 초안에서 배포 방식에 맞춰 확정합니다. 실제 IP, 실제 도메인, 실제 DB 계정, 실제 비밀번호는 문서에 쓰지 않습니다.

frontend dev/prod build 기준:

- local 개발은 `npm run dev`와 Vite proxy를 사용합니다.
- dev 서버 배포는 `npm run build` 결과물인 `frontend/dist`를 사용합니다.
- `frontend/dist/`는 build 결과물이므로 Git에 커밋하지 않습니다.
- nginx가 `frontend/dist`를 서빙하고 `/api` 요청은 backend로 reverse proxy합니다.
- 실제 nginx 설정 파일은 7단계에서 작성합니다.

## 7단계: nginx + docker-compose dev 배포 초안

7단계에서는 실제 Oracle VM 접속이나 배포 없이, repository 안에 dev 배포용 템플릿을 준비합니다.

dev 배포 초안에는 다음 연결을 목표로 합니다.

- frontend `dist`
- backend app
- postgres
- nginx

dev 배포 기준 docker-compose를 먼저 정리하고, prod용 docker-compose는 추후 제출/운영 단계에서 분리합니다.

7단계에서 준비한 파일:

- `infra/docker/docker-compose.dev.yml`
- `infra/nginx/default.dev.conf`
- `infra/env/.env.dev.example`

7단계에서도 `prod`용 docker-compose는 만들지 않고, dev 배포 초안을 먼저 검증합니다.

### dev docker compose 구성

`infra/docker/docker-compose.dev.yml`은 Oracle VM dev 서버에서 아래 서비스를 하나의 compose 내부 network로 연결하는 초안입니다.

| service | 역할 | 외부 포트 |
| --- | --- | --- |
| `postgres` | PostgreSQL dev DB | 공개하지 않음 |
| `backend` | Spring Boot jar 실행 | 공개하지 않음 |
| `nginx` | frontend 정적 파일 서빙, `/api` reverse proxy | `80` |

기준:

- `postgres`는 `postgres:16-alpine`을 사용합니다.
- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`는 실제 서버 `.env`에서 주입합니다.
- PostgreSQL data는 `data/postgres` 경로를 volume으로 사용합니다.
- `backend`는 서버에 배치된 `backend/app.jar`를 `java -jar`로 실행합니다.
- `backend`는 `SPRING_PROFILES_ACTIVE=dev`를 사용합니다.
- `DB_URL`은 compose 내부 service name인 `postgres`를 사용합니다.
- `backend`의 `8080`과 `postgres`의 `5432`는 외부에 직접 publish하지 않습니다.
- `nginx`만 외부 `80` 포트를 publish합니다.

`DB_URL` 예시:

```text
jdbc:postgresql://postgres:5432/meet_or_solo_dev
```

### nginx dev 설정

`infra/nginx/default.dev.conf`는 dev 환경용 nginx 설정 초안입니다.

기준:

- `frontend/dist`를 `/usr/share/nginx/html`로 mount해 정적 파일을 서빙합니다.
- SPA routing을 위해 `try_files $uri $uri/ /index.html`을 사용합니다.
- `/api/` 요청은 `backend:8080`으로 reverse proxy합니다.
- `/ws/` WebSocket 경로는 아직 실제 구현 전이므로 주석 placeholder로만 남깁니다.
- HTTPS, Certbot, 실제 domain 설정은 이번 단계에서 하지 않습니다.

### dev 서버 환경변수 예시

`infra/env/.env.dev.example`은 커밋 가능한 예시 파일입니다. 실제 서버 `.env`는 Oracle VM에서 서버 관리자가 직접 생성하고 repository에 커밋하지 않습니다.

포함 항목:

```text
SPRING_PROFILES_ACTIVE=dev
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD
DB_URL
DB_USERNAME
DB_PASSWORD
CORS_ALLOWED_ORIGINS
FLYWAY_LOCATIONS
SERVER_PORT
```

예시 파일의 `CHANGE_ME` 값은 실제 서버에서 반드시 별도 값으로 교체합니다. 실제 IP, 실제 도메인, 실제 DB 계정, 실제 비밀번호, API Key, Secret은 repository에 기록하지 않습니다.

### 배포 산출물 기준

backend 산출물:

```text
backend/build/libs/*.jar -> /home/ubuntu/meet-or-solo/backend/app.jar
```

frontend 산출물:

```text
frontend/dist -> /home/ubuntu/meet-or-solo/frontend/dist
```

기준:

- `backend/build/`는 build 결과물이므로 Git에 커밋하지 않습니다.
- `frontend/dist/`는 build 결과물이므로 Git에 커밋하지 않습니다.
- 산출물은 8단계 CI/CD 또는 수동 배포 시 Oracle VM으로 복사합니다.
- 이번 단계에서는 실제 서버 복사나 compose 실행을 하지 않습니다.

### 실제 Oracle VM에서 수행할 작업 후보

실제 서버 작업은 이 문서 기준을 확인한 뒤 별도 승인과 실제 서버 접근 권한이 있을 때 수행합니다.

1. Oracle VM에 `/home/ubuntu/meet-or-solo` 기준 디렉터리를 준비합니다.
2. 실제 서버 `.env`를 생성하고 Secret 값을 서버에만 기록합니다.
3. backend jar 산출물을 `backend/app.jar`로 배치합니다.
4. frontend build 산출물을 `frontend/dist`로 배치합니다.
5. `infra/docker/docker-compose.dev.yml`과 `infra/nginx/default.dev.conf`를 기준으로 dev compose를 실행합니다.
6. 외부에서는 nginx `80`만 접근 가능하게 하고, backend `8080`과 PostgreSQL `5432`는 공개하지 않습니다.
7. `/api/health`가 nginx를 통해 backend로 proxy되는지 확인합니다.

## prod Docker Compose 방향

추후 prod용 docker-compose에는 다음이 포함될 수 있습니다.

- backend service
- postgres service 또는 외부 DB connection
- Nginx를 container화할 경우 nginx service

prod용 구성은 dev 배포 초안을 검증한 뒤 별도 단계에서 분리합니다. 운영 복잡도를 미리 늘리지 않습니다.

## Nginx와 Let's Encrypt

Nginx 책임:

- HTTP를 HTTPS로 redirect
- React `dist` 서빙
- `/api` proxy
- `/ws` WebSocket upgrade proxy
- Certbot ACME challenge 처리

인증서 방향:

```text
Let's Encrypt + Certbot
```

실제 domain이 확정되기 전까지 placeholder를 사용합니다.

## 8-1단계: GitHub Actions CI 초안

8-1단계에서는 자동 배포 없이 GitHub Actions에서 backend와 frontend가 build 되는지만 검증합니다.

CI workflow:

```text
.github/workflows/ci.yml
```

trigger:

- `pull_request` to `main`
- `push` to `main`

현재 `develop` 브랜치는 확정 전이므로 `main` 중심으로 작성합니다. 추후 `develop` 브랜치 운영이 확정되면 trigger 대상에 `develop`을 추가할 수 있습니다.

CI job:

| job | 목적 | 실행 명령 |
| --- | --- | --- |
| `backend-build` | backend compile/package 검증 | `./gradlew build -x test` |
| `frontend-build` | frontend TypeScript/Vite build 검증 | `npm ci`, `npm run build` |

backend CI 기준:

- Java 17을 사용합니다.
- Gradle cache를 사용합니다.
- `backend` 디렉터리에서 실행합니다.
- `bootRun`은 실행하지 않습니다.
- PostgreSQL 컨테이너를 띄우지 않습니다.
- Flyway/DB 연결이 필요한 애플리케이션 실행은 하지 않습니다.
- 실제 `.env`나 DB Secret 없이 동작해야 합니다.

frontend CI 기준:

- Node.js 20을 사용합니다.
- npm cache를 사용합니다.
- `frontend` 디렉터리에서 실행합니다.
- `npm ci` 후 `npm run build`를 실행합니다.
- `frontend/dist/`는 build 산출물이므로 Git에 커밋하지 않습니다.

8-1단계에서는 Oracle VM 접속, SSH 배포, `docker compose up`, 서버 `.env` 생성, GitHub Secrets 사용을 하지 않습니다.

## 8-2단계: GitHub Actions dev CD 초안

8-2단계에서는 dev 서버 배포 자동화 초안을 `workflow_dispatch` 수동 실행 기준으로 작성합니다. 실제 Oracle VM 접속이나 실제 배포 성공을 수행하거나 가정하지 않습니다. GitHub 원격 저장소, server, domain, Secrets가 확정되기 전까지 값은 placeholder로만 다룹니다.

dev CD workflow:

```text
.github/workflows/deploy-dev.yml
```

trigger:

```text
workflow_dispatch
```

자동 `push` 배포는 이번 단계에서 사용하지 않습니다. 처음에는 GitHub Actions 화면에서 수동으로 실행하는 기준만 둡니다.

필요한 GitHub Secrets 이름:

```text
DEV_SERVER_HOST
DEV_SERVER_USER
DEV_SSH_KEY
DEV_DEPLOY_PATH
```

실제 Secret 값은 workflow, 문서, repository에 기록하지 않습니다. SSH private key는 `DEV_SSH_KEY` Secret에만 저장합니다.

workflow 동작 흐름:

1. Checkout
2. Java 17 설정
3. backend `bootJar -x test`
4. Node.js 20 설정
5. frontend `npm ci`
6. frontend `npm run build`
7. 배포 패키지 생성
8. GitHub Secrets에서 dev 서버 접속 정보 사용
9. SSH로 `DEV_DEPLOY_PATH` 생성
10. 배포 패키지 업로드
11. dev 서버에서 압축 해제
12. 서버 `.env` 존재 여부 확인
13. `docker compose --env-file .env -f infra/docker/docker-compose.dev.yml up -d`

배포 패키지 포함 항목:

- `backend/app.jar`
- `frontend/dist/`
- `infra/docker/docker-compose.dev.yml`
- `infra/nginx/default.dev.conf`
- `db/migration/`

배포 패키지에 포함하지 않는 항목:

- 서버 `.env`
- DB password
- SSH private key
- 실제 IP/domain
- API Key
- GitHub Secrets 실제 값

서버 `.env` 기준:

- GitHub Actions는 서버 `.env`를 생성하지 않습니다.
- 서버 관리자가 Oracle VM에서 `DEV_DEPLOY_PATH/.env`를 직접 생성합니다.
- `.env` 값은 `infra/env/.env.dev.example`을 참고하되 실제 값은 서버에만 둡니다.

CD 실행 전 Oracle VM 준비 항목:

1. Docker 설치
2. Docker Compose plugin 설치
3. `DEV_DEPLOY_PATH`로 사용할 디렉터리 결정
4. `DEV_DEPLOY_PATH/.env` 직접 생성
5. nginx 외부 `80` 접근 허용
6. backend `8080` 외부 직접 접근 차단
7. PostgreSQL `5432` 외부 직접 접근 차단
8. GitHub Actions에서 접속할 SSH user와 key 준비

실패 시 확인 항목:

- GitHub Secrets 이름이 workflow와 일치하는지 확인합니다.
- `DEV_SERVER_HOST`, `DEV_SERVER_USER`, `DEV_SSH_KEY`, `DEV_DEPLOY_PATH` 값이 GitHub Secrets에 존재하는지 확인합니다.
- Oracle VM의 SSH 접근이 허용되어 있는지 확인합니다.
- `DEV_DEPLOY_PATH/.env`가 서버에 존재하는지 확인합니다.
- 서버에 Docker와 Docker Compose plugin이 설치되어 있는지 확인합니다.
- `frontend/dist`, `backend/app.jar`, `db/migration`이 배포 패키지에 포함되었는지 확인합니다.
- nginx `80`만 외부 접근 가능하고 backend `8080`, PostgreSQL `5432`가 외부 공개되지 않았는지 확인합니다.

테스트 자동화와 prod 자동 배포는 추후 단계에서 확장합니다. 실제 운영/prod 자동 배포는 현재 범위가 아닙니다.

실제 server IP, domain, SSH Key, Secret 값은 workflow에 직접 쓰지 않습니다.

## 예정 GitHub Secrets

placeholder 이름:

```text
SERVER_HOST
SERVER_USER
SERVER_PORT
SERVER_SSH_KEY
APP_DOMAIN
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
KAKAO_CLIENT_ID
KAKAO_CLIENT_SECRET
TOUR_API_KEY
VAPID_PUBLIC_KEY
VAPID_PRIVATE_KEY
```

실제 값은 GitHub Secrets에만 설정하고 repository file에는 넣지 않습니다.

## 포트 노출

운영 공개:

- `80`
- `443`
- 제한된 `22`

운영 private/internal only:

- `5432` PostgreSQL
- `8080` backend

Docker network를 사용한다면 backend와 DB는 internal network 또는 localhost 경계 안에서만 접근 가능해야 합니다.

## GHCR 확장 가능성

추후 GitHub Container Registry를 사용할 수 있습니다.

```text
GitHub Actions -> GHCR image -> server docker compose pull -> restart
```

이는 확장안이며 현재 범위가 아닙니다.

## 백업과 운영

추후 운영에는 다음이 필요합니다.

- PostgreSQL backup job
- log rotation
- uptime monitoring
- certificate renewal check
- health endpoint monitoring

배포 방식이 승인되기 전까지 운영 자동화는 추가하지 않습니다.
