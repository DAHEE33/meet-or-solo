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

현재 단계는 개발환경과 프로젝트 방향성을 정리하는 단계입니다.

아직 다음 항목은 실제 구현 범위가 아닙니다.

- Kakao OAuth2 로그인
- JWT 인증
- 관광공사 OpenAPI 실제 연동
- GPS 체크인
- 자동 매칭
- WebSocket STOMP 이벤트 구현
- 관리자 기능
- Redis 구성
- 운영 배포 자동화 완성

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

## 문서 작성 언어

이 프로젝트의 기본 문서 언어는 한국어입니다.

`README.md`, `docs/*.md`, `AGENTS.md`, `CLAUDE.md`의 본문은 한국어로 작성합니다. 기술 용어, 파일명, 클래스명, 함수명, 환경변수명, 명령어, 경로는 영어 원문을 유지합니다.

## 1단계 범위

사용자가 명시적으로 승인한 뒤 진행할 1단계 범위는 개발환경 세팅입니다.

- 모노레포 구조 정리
- backend Spring Boot 실행 구조 정리
- frontend React + TypeScript + Vite + PWA 스캐폴딩
- PostgreSQL docker-compose 구성
- Flyway 기본 설정
- `/api/health`
- React에서 `/api/health` 호출
- nginx reverse proxy 초안
- GitHub Actions 초안
- README 실행 방법 정리

1단계에서는 비즈니스 기능을 구현하지 않습니다.

## 로컬 backend 개발환경

이번 단계에서는 backend, PostgreSQL, Flyway, `/api/health`만 구성합니다.
frontend, nginx, `docker-compose.prod.yml`, GitHub Actions는 아직 생성하거나 수정하지 않습니다.

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

### PostgreSQL 컨테이너 실행

로컬 개발용 PostgreSQL만 실행합니다. Redis는 MVP 1단계에서 제외합니다.

```bash
docker compose -f docker-compose.local.yml up -d
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
  "service": "meet-or-solo-backend",
  "status": "OK"
}
```

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

- frontend PWA 스캐폴딩과 `/api/health` 호출
- nginx reverse proxy
- `docker-compose.prod.yml`
- GitHub Actions
- Kakao OAuth2 로그인
- JWT 인증/인가
- 관광공사 OpenAPI 실제 연동
- GPS 체크인
- 자동 매칭
- WebSocket STOMP 상태 동기화
- 관리자 기능
- Redis 구성
- 테스트 코드 추가
