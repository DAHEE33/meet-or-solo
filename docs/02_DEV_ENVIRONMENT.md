# 개발환경

## 목표 기술 스택

| 영역 | 도구 |
| --- | --- |
| Backend language | Java 17 |
| Backend framework | Spring Boot 3.x |
| Frontend runtime | Node.js LTS |
| Frontend framework | React + TypeScript |
| Frontend build | Vite |
| PWA | Vite PWA plugin 또는 동등한 구성 |
| Database | PostgreSQL |
| Migration | Flyway |
| Local DB runtime | Docker Compose |

현재 `backend`에 복사된 신규 Spring Boot 프로젝트의 버전은 구현 단계에서 확인하고 정렬합니다. 프로젝트 방향은 별도 결정이 없는 한 Java 17과 Spring Boot 3.x를 기준으로 합니다.

## WBS 기준 개발환경 단계

현재 개발환경 단계는 아래 순서로 정리합니다.

1. Backend + Local PostgreSQL + Flyway 확인
2. local/dev/prod 실행 전략 정리
3. Frontend PWA 기본 스캐폴딩 + `/api/health` 연동
4. Backend 공통 코드화
5. Frontend 공통 코드화
6. Oracle VM dev 서버/dev DB 구축 준비
7. nginx + docker-compose dev 배포 초안
8. GitHub Actions CI/CD 초안
9. 실제 서비스 DB 테이블/Flyway migration
10. 풀스택 A/B 기능 분업

현재 완료 단계와 다음 작업은 [docs/10_PROGRESS_LOG.md](10_PROGRESS_LOG.md)를 기준으로 확인합니다.

## 로컬 서비스

로컬 컨테이너는 PostgreSQL만 둡니다.

Redis는 초기 개발환경과 MVP 초기 `docker-compose`에 추가하지 않습니다.

예정 서비스:

```text
postgres:
  image: postgres
  port: 5432
```

로컬 개발에서는 `5432`를 열 수 있지만, 운영에서는 PostgreSQL을 외부에 공개하지 않습니다.

## local/dev/prod 환경 역할

profile 기준:

- `local`: 각 팀원의 개인 PC에서 Docker PostgreSQL로 개발하고 빠르게 확인하는 환경이다.
- `dev`: Oracle Cloud VM에 배포하는 개발/시연용 공유 환경이다.
- `prod`: 추후 제출/운영 단계에서 별도 DB, 별도 도메인, 별도 디렉터리 또는 외부 DB 서비스로 분리할 운영 후보 환경이다.

`local`은 개인 개발 편의를 위해 `application-local.yml`에 fallback 기본값을 둘 수 있습니다.

`dev`와 `prod`는 서버 환경이므로 환경변수 주입을 원칙으로 합니다. 실제 DB URL, 계정, 비밀번호, 서버 IP, 도메인은 저장소에 기록하지 않습니다.

현재는 Oracle Cloud VM 리소스와 작업 안정성을 고려해 `dev`만 VM에 배포하는 방향으로 준비합니다. 6단계에서는 실제 서버 접속이나 배포 자동화를 수행하지 않고 dev 서버와 dev DB 구성 기준만 문서화합니다. `prod`는 추후 제출/운영 필요가 생기면 분리합니다. `dev`와 `prod`를 처음부터 같은 VM에서 동시에 띄우지 않습니다.

기능 분업 전에 `dev` 서버와 `dev` DB를 구축하는 이유:

- A/B가 같은 API base URL과 같은 DB schema 기준으로 기능을 확인할 수 있다.
- local PC 차이로 발생하는 환경 문제를 공유 dev 환경에서 빠르게 분리할 수 있다.
- frontend/backend 연동 이슈를 각자 로컬 설정 문제가 아니라 배포 경계 기준으로 검증할 수 있다.
- 시연 가능한 기준 환경을 먼저 만든 뒤 기능 단위로 병합 여부를 판단할 수 있다.
- DB, CORS, 환경변수, reverse proxy 같은 공통 경계를 먼저 고정해 기능 구현 중 충돌을 줄일 수 있다.

Docker Compose 방향:

- `docker-compose.local.yml`: 로컬 PostgreSQL과 개발 편의 구성
- `infra/docker/docker-compose.dev.yml`: Oracle VM dev 서버에서 frontend `dist`, backend app, postgres, nginx를 연결하는 dev 배포 초안
- prod 배포용 docker-compose: 추후 제출/운영 단계에서 dev와 분리해 별도 작성

현재까지는 local PostgreSQL 중심의 최소 구성과 dev 배포 템플릿만 사용합니다. prod docker-compose와 GitHub Actions는 해당 단계에서 별도 승인 후 작성합니다.

## Oracle VM dev 서버 준비 기준

6단계 dev 서버 준비는 문서와 템플릿 후보 정리까지만 진행합니다. 실제 Oracle VM에 접속하거나 파일을 배포하지 않습니다.

예정 서버 기준 경로:

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

디렉터리 역할:

| 경로 | 역할 |
| --- | --- |
| `/home/ubuntu/meet-or-solo/backend/` | Spring Boot build artifact인 `app.jar` 배치 후보 |
| `/home/ubuntu/meet-or-solo/frontend/dist/` | `npm run build` 결과물 배치 후보 |
| `/home/ubuntu/meet-or-solo/nginx/` | 7단계에서 작성할 nginx dev 설정 후보 위치 |
| `/home/ubuntu/meet-or-solo/data/postgres/` | PostgreSQL dev data volume 후보 |
| `/home/ubuntu/meet-or-solo/logs/` | backend/nginx 등 dev 로그 보관 후보 |
| `/home/ubuntu/meet-or-solo/.env` | dev 서버 환경변수 주입 후보. 실제 값은 저장소에 커밋하지 않음 |

6단계에서는 위 구조를 실제 파일로 만들지 않고 문서화만 했습니다. 7단계에서 dev nginx/docker-compose 템플릿을 추가했고, GitHub Actions workflow는 8단계에서 별도 승인 후 작성합니다.

7단계에서 repository 안에 dev 배포 초안 파일을 추가했습니다.

```text
infra/docker/docker-compose.dev.yml
infra/nginx/default.dev.conf
infra/env/.env.dev.example
```

이 파일들은 실제 Oracle VM 배포가 아니라 dev 배포 기준 템플릿입니다. 실제 서버 `.env`, backend jar, frontend `dist`, PostgreSQL data, logs는 Git에 커밋하지 않습니다.

## Oracle VM dev DB 기준

dev DB는 Oracle VM 내부 PostgreSQL을 기준으로 준비합니다.

기준:

- DB 이름 예시는 `meet_or_solo_dev`를 사용한다.
- DB user/password는 실제 값을 문서, 코드, 예시 파일에 하드코딩하지 않는다.
- DB 접속 정보는 VM의 `.env` 또는 추후 GitHub Secrets에서 주입한다.
- PostgreSQL `5432`는 외부 전체 공개를 하지 않는다.
- backend와 PostgreSQL은 같은 VM 내부 네트워크 또는 localhost 경계에서 통신한다.
- 팀원이 DB에 직접 접근해야 하는 경우 SSH tunnel을 사용한다.

dev DB URL 예시는 형식만 문서화합니다.

```text
DB_URL=jdbc:postgresql://<INTERNAL_DB_HOST>:5432/meet_or_solo_dev
```

`<INTERNAL_DB_HOST>`는 `localhost`, docker compose service name, VM 내부 host 중 7단계 배포 방식에서 확정합니다. 실제 서버 IP, 도메인, DB 계정, 비밀번호는 기록하지 않습니다.

팀원 SSH tunnel 접근 예시는 placeholder만 사용합니다.

```bash
ssh -L 15432:localhost:5432 <SSH_USER>@<DEV_SERVER_HOST>
```

터널 연결 후 개인 PC의 DB client는 아래처럼 접근하는 방향입니다.

```text
host=localhost
port=15432
database=meet_or_solo_dev
user=<DB_USERNAME>
password=<DB_PASSWORD>
```

`<SSH_USER>`, `<DEV_SERVER_HOST>`, `<DB_USERNAME>`, `<DB_PASSWORD>` 실제 값은 문서나 repository에 기록하지 않습니다.

## dev profile 환경변수 기준

backend `application-dev.yml`은 환경변수 주입을 기준으로 합니다.

필수 또는 권장 환경변수:

| 환경변수 | 용도 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | 서버 실행 profile. dev 서버에서는 `dev` 사용 |
| `DB_URL` | PostgreSQL dev DB JDBC URL |
| `DB_USERNAME` | PostgreSQL dev DB 사용자 |
| `DB_PASSWORD` | PostgreSQL dev DB 비밀번호 |
| `CORS_ALLOWED_ORIGINS` | dev frontend origin 허용 목록. 실제 domain/IP는 placeholder로만 관리 |
| `FLYWAY_LOCATIONS` | Flyway migration 위치. 기본 후보는 `filesystem:../db/migration` |
| `SERVER_PORT` | backend 실행 포트. 기본 후보는 `8080` |

예시 값에는 실제 IP, 실제 도메인, 실제 계정, 실제 비밀번호를 넣지 않습니다.

## 로컬 실행 순서

로컬 실행 흐름:

1. PostgreSQL 시작
2. backend 실행
3. Flyway migration 실행 확인
4. `/api/health` 확인
5. frontend 실행
6. React에서 `/api/health` 호출 확인

로컬 PostgreSQL은 프로젝트 루트에서 실행합니다.

```bash
docker compose -f docker-compose.local.yml --env-file .env up -d
```

backend는 `backend` 디렉터리에서 실행합니다.

```bash
cd backend
./gradlew bootRun
```

Spring Boot `bootRun`은 `.env`를 자동으로 읽지 않습니다. Git Bash에서 `source .env`를 했다면 같은 Git Bash 터미널에서 `./gradlew bootRun`까지 실행해야 합니다. PowerShell과 Git Bash의 환경변수는 서로 공유되지 않습니다.

PowerShell에서 실행할 경우 `application-local.yml` fallback 기본값으로 실행하거나, PowerShell 환경변수를 직접 설정합니다.

## backend 실행 방향

profile:

- `local`: 로컬 PostgreSQL, 로컬 CORS, 개발 로그
- `dev`: Oracle Cloud VM 개발/시연용 서버, 환경변수 기반 DB 설정
- `prod`: 추후 운영 환경, 환경변수 기반 DB 설정, 제한된 CORS, 보안 헤더

로컬 기본값:

```text
DB_URL=jdbc:postgresql://localhost:5432/meet_or_solo_local
DB_USERNAME=meet_user
DB_PASSWORD=meet_password
```

예정 health endpoint:

```text
GET /api/health
```

현재 응답:

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

## frontend 실행 방향

예정 script:

```text
npm run dev
npm run build
npm run preview
```

개발 환경:

- Vite dev server가 React를 서빙한다.
- API base URL은 로컬 backend를 가리킨다.

운영 환경:

- `npm run build`로 `dist`를 만든다.
- Nginx가 `dist`를 정적 파일로 서빙한다.
- `frontend/dist/`는 build 결과물이므로 Git에 커밋하지 않는다.
- `/api` 요청은 Nginx가 backend로 reverse proxy한다.

dev 서버 산출물 배치 기준:

```text
backend/build/libs/*.jar -> backend/app.jar
frontend/dist -> frontend/dist
```

위 산출물은 8단계 CI/CD 또는 수동 배포 시 서버로 복사합니다. 현재 단계에서는 실제 Oracle VM에 접속하거나 배포하지 않습니다.

8-2단계 dev CD 초안은 GitHub Actions `workflow_dispatch` 수동 실행 기준입니다. workflow는 산출물을 서버로 업로드하는 초안만 제공하며, 실제 서버 `.env`는 생성하지 않습니다. Oracle VM의 `DEV_DEPLOY_PATH/.env`는 서버 관리자가 직접 생성하고 관리합니다.

## 팀원 로컬 실행 가이드

처음 repository를 받은 팀원은 아래 순서로 local 개발환경을 실행합니다.

### 환경변수 파일

개인 `.env` 파일은 로컬에서만 사용하고 커밋하지 않습니다.

루트 환경변수는 `.env.example`을 기준으로 각자 `.env`를 만듭니다.

PowerShell:

```powershell
Copy-Item .env.example .env
```

Git Bash:

```bash
cp .env.example .env
```

frontend 환경변수는 `frontend/.env.local.example`을 기준으로 각자 `frontend/.env.local`을 만듭니다.

PowerShell:

```powershell
Copy-Item frontend/.env.local.example frontend/.env.local
```

Git Bash:

```bash
cp frontend/.env.local.example frontend/.env.local
```

local 개발에서는 `frontend/.env.local`의 `VITE_API_BASE_URL`을 비워두고 Vite proxy를 사용합니다.

```text
VITE_API_BASE_URL=
```

### 실행 순서

Docker Desktop이 실행 중이어야 합니다.

프로젝트 루트에서 PostgreSQL을 실행합니다.

```bash
docker compose -f docker-compose.local.yml --env-file .env up -d
```

`5432` 포트가 이미 사용 중이면 기존 PostgreSQL을 끄거나 포트를 조정해야 합니다.

backend는 `8080`, frontend는 `5173`을 사용합니다. backend와 frontend는 각각 다른 터미널에서 실행합니다.

PowerShell에서 backend를 실행합니다.

```powershell
cd backend
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

Git Bash에서 `.env`를 export하고 backend를 실행합니다.

```bash
cd backend
set -a
source ../.env
set +a
./gradlew bootRun --args='--spring.profiles.active=local'
```

frontend를 실행합니다.

```bash
cd frontend
npm install
npm run dev
```

브라우저에서 `http://localhost:5173/`에 접속합니다. HealthCheck 카드에 `연결 성공`, `status=OK`, `service=meet-or-solo-backend`가 표시되면 local 연동 확인이 완료된 것입니다.

frontend 화면에서 404가 나면 `frontend/vite.config.ts`의 Vite proxy 설정과 frontend dev server 재시작 여부를 확인합니다.

## Flyway 방향

Flyway는 초기부터 사용합니다.

예정 위치:

```text
db/migration/
```

또는 Spring Boot classpath migration 위치를 사용할 수 있습니다. 실제 구현 전 어떤 방식을 사용할지 문서에 명시해야 합니다.

초기 migration은 최소화합니다. 비즈니스 테이블을 과도하게 만들지 않습니다.

## `/api/health` 확인 방법 예정

로컬 확인:

```bash
curl http://localhost:8080/api/health
```

frontend는 초기 개발환경 검증 용도로 `/api/health` 결과를 표시하거나 로그로 확인합니다. 이는 비즈니스 기능이 아닙니다.

## Secret 관리

placeholder와 환경변수만 사용합니다.

커밋 금지 항목:

- API Key
- 비밀번호
- SSH private key
- 실제 운영 도메인
- 실제 서버 IP
- OAuth client secret
