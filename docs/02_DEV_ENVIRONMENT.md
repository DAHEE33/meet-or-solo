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
6. Oracle VM dev 서버/dev DB 구축
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

현재는 Oracle Cloud VM 리소스와 작업 안정성을 고려해 `dev`만 VM에 배포합니다. `prod`는 추후 제출/운영 필요가 생기면 분리합니다. `dev`와 `prod`를 처음부터 같은 VM에서 동시에 띄우지 않습니다.

기능 분업 전에 `dev` 서버와 `dev` DB를 구축하는 이유:

- A/B가 같은 API base URL과 같은 DB schema 기준으로 기능을 확인할 수 있다.
- local PC 차이로 발생하는 환경 문제를 공유 dev 환경에서 빠르게 분리할 수 있다.
- frontend/backend 연동 이슈를 각자 로컬 설정 문제가 아니라 배포 경계 기준으로 검증할 수 있다.
- 시연 가능한 기준 환경을 먼저 만든 뒤 기능 단위로 병합 여부를 판단할 수 있다.
- DB, CORS, 환경변수, reverse proxy 같은 공통 경계를 먼저 고정해 기능 구현 중 충돌을 줄일 수 있다.

Docker Compose 방향:

- `docker-compose.local.yml`: 로컬 PostgreSQL과 개발 편의 구성
- dev 배포용 docker-compose: Oracle VM dev 서버에서 frontend `dist`, backend app, postgres, nginx를 연결하는 초안
- prod 배포용 docker-compose: 추후 제출/운영 단계에서 dev와 분리해 별도 작성

현재까지는 local PostgreSQL 중심의 최소 구성만 사용합니다. nginx, docker-compose dev/prod, GitHub Actions는 해당 단계에서 별도 승인 후 작성합니다.

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

예정 응답:

```json
{
  "status": "ok",
  "service": "meet-or-solo-backend"
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
