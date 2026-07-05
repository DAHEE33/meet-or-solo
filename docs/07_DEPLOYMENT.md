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
3. [8단계] GitHub Actions CI/CD 초안

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

dev 배포 초안에는 다음 연결을 목표로 합니다.

- frontend `dist`
- backend app
- postgres
- nginx

dev 배포 기준 docker-compose를 먼저 정리하고, prod용 docker-compose는 추후 제출/운영 단계에서 분리합니다.

현재 단계에서는 사용자가 명시적으로 승인하기 전까지 nginx 설정 파일이나 docker-compose dev/prod 파일을 생성 또는 수정하지 않습니다.

7단계에서 만들 파일 후보:

- `docker-compose.dev.yml`
- `infra/nginx/dev/default.conf` 또는 이에 준하는 nginx dev 설정 파일
- dev 서버용 `.env` 템플릿 후보. 실제 Secret 값은 포함하지 않음
- 필요 시 dev 배포/재시작 스크립트 후보

7단계에서도 `prod`용 docker-compose는 만들지 않고, dev 배포 초안을 먼저 검증합니다.

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

## 8단계: GitHub Actions CI/CD 초안

GitHub 원격 저장소는 아직 미연결 상태입니다. workflow는 remote, server, domain, Secrets가 확정되기 전까지 placeholder 초안으로만 작성합니다.

초기 workflow 방향:

1. Checkout
2. backend build
3. frontend build
4. `develop` 또는 수동 workflow 기준 dev 배포
5. SSH로 dev server 접속
6. service restart 또는 docker compose 재기동
7. `/api/health` 확인

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
