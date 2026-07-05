# 진행 상태 기록

이 문서는 `meet-or-solo`의 현재 진행 상태와 다음 작업 순서를 기록합니다. 새 작업을 시작하기 전에 반드시 이 문서를 확인하고, 현재 단계에 맞는 작업만 수행합니다.

## 1. WBS 기준 전체 단계

현재 WBS 흐름은 다음 순서를 기준으로 합니다.

```text
개발환경 세팅
-> CI/CD 세팅
-> Front 공통 코드화
-> Backend 공통 코드화
-> 이후 풀스택 A/B 기능 분업
```

다만 실제 작업 안정성을 위해 이 저장소에서는 Backend 공통 코드화와 Frontend 공통 코드화를 먼저 마무리한 뒤, Oracle VM dev 서버/dev DB 구축 준비, nginx/docker-compose dev 배포 초안, GitHub Actions CI와 dev CD 초안을 잡고 기능 분업으로 넘어갑니다.

## 2. 현재 완료된 단계

### [0단계] 프로젝트 방향/문서화 완료

상태: 완료

- `README.md`, `AGENTS.md`, `CLAUDE.md` 작성
- `docs/00_PROJECT_OVERVIEW.md`부터 `docs/09_TEST_AND_QUALITY_STRATEGY.md`까지 문서 작성
- 문서는 한국어 중심으로 작성하고, 기술명/명령어/경로/env 이름은 영어 원문을 유지한다.
- Redis는 MVP 초기 단계에서 제외한다.
- WebSocket STOMP는 상태 동기화용이며 자유 채팅이 아니다.

### [1단계] Backend + Local PostgreSQL + Flyway 확인 완료

상태: 완료

- backend Spring Boot 실행 확인
- `GET /api/health` 확인
- `docker-compose.local.yml` 기반 local PostgreSQL 컨테이너 실행 확인
- `.env` 기반 PostgreSQL 컨테이너 환경변수 확인
- `psql`로 local PostgreSQL 접속 확인
- `select * from flyway_schema_history;` 조회 성공
- 현재 `V1__init.sql`은 DB/Flyway 연결 확인용 초기 migration이다.
- 실제 서비스 DB 테이블은 아직 만들지 않았다.

### [2단계] local/dev/prod 실행 전략 정리 완료

상태: 완료

- `local`: 개인 PC Docker PostgreSQL
- `dev`: Oracle Cloud VM 개발/시연용 서버
- `prod`: 추후 제출/운영 단계에서 분리
- 현재 VM에는 `dev`만 배포하는 방향으로 결정
- `prod`는 추후 별도 디렉터리, 별도 DB, 별도 도메인 또는 외부 DB 서비스로 분리 가능하게 설계
- 초기 서버 배포는 `SPRING_PROFILES_ACTIVE=dev`를 사용
- PostgreSQL `5432`는 외부 전체 공개하지 않는다.
- DB 직접 접속이 필요하면 SSH tunnel 방식을 우선 고려한다.
- local 실행 시 `docker compose`는 프로젝트 루트에서 `--env-file .env`와 함께 실행한다.
- Spring Boot `bootRun`은 `.env`를 자동으로 읽지 않는다.
- PowerShell과 Git Bash의 환경변수는 서로 공유되지 않는다.
- Git Bash에서 `source .env`를 했다면 같은 Git Bash 터미널에서 `./gradlew bootRun`까지 실행한다.
- PowerShell에서 실행할 경우 `application-local.yml` fallback 값으로 실행하거나 PowerShell 환경변수를 직접 설정한다.

### [3단계] Frontend PWA 기본 스캐폴딩 + `/api/health` 연동 완료

상태: 완료

- `frontend`에 React + TypeScript + Vite 기본 구조를 구성했다.
- `vite-plugin-pwa` 기반 PWA 기본 shell을 구성했다.
- `manifest`의 앱 이름은 `meet-or-solo`로 설정했다.
- 아이콘은 `public/icons/placeholder.svg` placeholder로 두었다.
- `frontend/.env.local.example`, `frontend/.env.production.example`에 `VITE_API_BASE_URL` 예시를 추가했다.
- local 개발에서는 `VITE_API_BASE_URL`을 비워두고 상대 경로 `/api/health`와 Vite proxy를 사용한다.
- Vite proxy로 `/api` 요청을 backend `localhost:8080`으로 전달한다.
- `HealthCheckPage`에서 backend `GET /api/health` 연동을 확인했다.
- 현재 PWA는 기본 shell, manifest, service worker 생성 설정, placeholder icon 수준이다.
- `frontend/dist/`는 build 결과물이므로 커밋하지 않는다.
- 현재 frontend 화면은 개발 연결 확인용이며 실제 서비스 UI가 아니다.

### [4단계] Backend 공통 코드화 완료

상태: 완료

완료 항목:

- `ApiResponse` 기반 공통 응답 포맷 추가
- `ErrorResponse` 기반 공통 에러 응답 구조 추가
- `ErrorCode`
- `BusinessException`
- `GlobalExceptionHandler`
- validation 에러 응답 공통 포맷 적용
- `/api/**` CORS 설정 추가
- local 기본 CORS origin: `http://localhost:5173`
- dev/prod CORS origin은 `CORS_ALLOWED_ORIGINS` 환경변수 기반으로 확장 가능하게 구성
- `HealthController` 응답을 공통 `ApiResponse` 포맷으로 변경

주의:

- 아직 비즈니스 기능은 구현하지 않는다.
- 실제 서비스 DB 테이블은 만들지 않는다.
- DB migration은 추가하지 않는다.
- 인증, 매칭, 축제, 체크인, 신고 기능은 구현하지 않는다.
- 응답에 stack trace, DB URL, 환경변수, 내부 예외 상세를 노출하지 않는다.
- 5단계 Frontend 공통 코드화에서 frontend `healthApi`와 `HealthCheckPage`를 새 `ApiResponse` 포맷에 맞게 수정했다.

### [5단계] Frontend 공통 코드화 완료

상태: 완료

- `ApiResponse<T>`, `ApiError`, `FieldError` 타입 추가
- fetch 기반 공통 `apiClient` 추가
- local 개발에서 Vite proxy와 `/api/...` 상대 경로 사용 기준 유지
- 추후 dev/prod에서 `VITE_API_BASE_URL`을 사용할 수 있도록 구조 유지
- 새 backend `ApiResponse` 포맷에 맞춘 `healthApi` 수정
- 새 backend `ApiResponse` 포맷에 맞춘 `HealthCheckPage` 수정
- loading/error UI는 `HealthCheckPage` 안에서 최소 상태로 유지
- React Router, 디자인 시스템, 실제 서비스 화면은 도입하지 않음

주의:

- 실제 서비스 화면은 구현하지 않는다.
- Kakao OAuth, JWT, 축제/매칭/체크인/신고 기능은 구현하지 않는다.
- backend 코드, DB migration, nginx, docker-compose, GitHub Actions, 테스트 코드는 수정하지 않는다.

### [6단계] Oracle VM dev 서버/dev DB 구축 준비 완료

상태: 완료

완료 항목:

- `/home/ubuntu/meet-or-solo` 기준 dev 서버 폴더 구조 문서화
- `backend/app.jar`, `frontend/dist`, `nginx/default.conf`, `data/postgres`, `logs`, `.env` 역할 정리
- Oracle VM 내부 PostgreSQL dev DB 기준 정리
- DB 이름 예시 `meet_or_solo_dev` 문서화
- DB user/password는 `.env` 또는 GitHub Secrets에서 주입하고 실제 값을 하드코딩하지 않는 원칙 정리
- PostgreSQL `5432` 외부 전체 공개 금지 원칙 재확인
- backend와 PostgreSQL은 같은 VM 내부 네트워크 또는 localhost 경계에서 통신하는 방향 정리
- 팀원 dev DB 직접 접근은 SSH tunnel을 우선 사용하는 방향 정리
- backend `application-dev.yml` 기준 환경변수 목록 정리
- frontend local 개발은 `npm run dev`와 Vite proxy, dev 서버 배포는 `npm run build` 결과물인 `frontend/dist`를 사용하는 기준 정리
- `frontend/dist/`는 Git에 커밋하지 않는 원칙 재확인
- nginx가 `frontend/dist`를 서빙하고 `/api`를 backend로 reverse proxy하는 방향 정리
- 실제 nginx 설정 파일은 7단계에서 작성한다고 명시
- 7단계에서 만들 파일 후보만 문서화

주의:

- 실제 Oracle VM에 접속하지 않았다.
- 실제 파일을 서버에 배포하지 않았다.
- nginx 설정 파일을 만들지 않았다.
- docker-compose dev/prod 파일을 만들지 않았다.
- GitHub Actions 파일을 만들지 않았다.
- backend/frontend 코드, DB migration, 실제 서비스 테이블, 테스트 코드는 수정하지 않았다.
- 실제 IP, 도메인, DB 계정, 비밀번호, API Key, Secret은 작성하지 않았다.

## 3. 이후 단계 순서

### [7단계] nginx + docker-compose dev 배포 초안

상태: 완료

완료 항목:

- `infra/docker/docker-compose.dev.yml` 추가
- `postgres`, `backend`, `nginx` service를 compose 내부 network로 연결
- `postgres`는 `postgres:16-alpine` 기준으로 작성
- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`는 환경변수로 주입
- PostgreSQL data volume 후보를 `data/postgres`로 구성
- 팀원 dev DB 확인을 위한 SSH tunnel 고정 목적지로 PostgreSQL을 host loopback `127.0.0.1:15432`에만 publish
- `backend`는 Spring Boot jar를 `backend/app.jar`로 mount해 `java -jar`로 실행
- `backend`는 `SPRING_PROFILES_ACTIVE=dev` 기준으로 실행
- `DB_URL`은 compose 내부 service name `postgres` 기준으로 예시 구성
- `backend 8080`은 외부에 직접 publish하지 않음
- `postgres 5432`는 외부에 직접 publish하지 않고 서버 내부 `127.0.0.1:15432`에만 publish
- `nginx`는 기존 운영 nginx와 host `80` 충돌을 피하기 위해 외부 `18080` 포트로 publish
- `infra/nginx/default.dev.conf` 추가
- nginx가 `frontend/dist` 정적 파일을 서빙하고 SPA fallback을 적용하도록 구성
- `/api/` 요청을 `backend:8080`으로 reverse proxy
- `/ws/` 경로는 실제 구현 전 placeholder 주석으로만 남김
- HTTPS/Certbot/domain 설정은 추가하지 않음
- `infra/env/.env.dev.example` 추가
- 실제 서버 `.env`는 Oracle VM에서 서버 관리자가 직접 생성한다고 문서화
- backend jar와 frontend dist 산출물 배치 기준 문서화
- `.gitignore`에 실제 env, key, log, build/data 산출물 ignore 기준 보강

주의:

- 실제 Oracle VM에 접속하지 않았다.
- 실제 배포하지 않았다.
- GitHub Actions 파일을 만들지 않았다.
- prod docker-compose를 만들지 않았다.
- prod nginx 설정을 만들지 않았다.
- backend/frontend 기능 코드, DB migration, 실제 서비스 테이블, 테스트 코드는 수정하지 않았다.
- 실제 IP, 도메인, DB 계정, 비밀번호, API Key, Secret은 작성하지 않았다.

### [8-1단계] GitHub Actions CI 초안

상태: 완료

완료 항목:

- `.github/workflows/ci.yml` 추가
- `pull_request` to `main` trigger 추가
- `push` to `main` trigger 추가
- `backend-build` job 추가
- backend CI에서 Java 17 설정
- backend CI에서 Gradle cache 적용
- backend CI에서 `./gradlew build -x test` 실행
- backend CI에서 `bootRun`, DB 연결, PostgreSQL 컨테이너 실행 제외
- `frontend-build` job 추가
- frontend CI에서 Node.js 20 설정
- frontend CI에서 npm cache 적용
- frontend CI에서 `npm ci`, `npm run build` 실행
- CI는 compile/build 검증만 수행하고 자동 배포/CD는 하지 않음

주의:

- 실제 Oracle VM에 접속하지 않았다.
- SSH 배포를 구성하지 않았다.
- `docker compose up`을 실행하지 않았다.
- 서버 `.env`를 생성하지 않았다.
- GitHub Secrets를 사용하지 않았다.
- backend/frontend 기능 코드, DB migration, 실제 서비스 테이블, nginx/docker-compose prod, 테스트 코드는 수정하지 않았다.
- 실제 IP, 도메인, DB 계정, 비밀번호, API Key, Secret은 작성하지 않았다.

### [8-2단계] GitHub Actions dev CD 초안

상태: 완료

완료 항목:

- `.github/workflows/deploy-dev.yml` 추가
- `workflow_dispatch` 수동 실행 trigger만 사용
- 자동 `push` 배포는 제외
- backend를 Java 17로 `bootJar -x test` 빌드하는 단계 추가
- frontend를 Node.js 20으로 `npm ci`, `npm run build`하는 단계 추가
- backend jar를 `backend/app.jar` 이름으로 배포 패키지에 포함
- frontend `dist`를 배포 패키지에 포함
- `infra/docker/docker-compose.dev.yml`을 배포 패키지에 포함
- `infra/nginx/default.dev.conf`를 배포 패키지에 포함
- `db/migration`을 배포 패키지에 포함
- GitHub Secrets 이름 후보 사용
- `DEV_SERVER_HOST`
- `DEV_SERVER_USER`
- `DEV_SSH_KEY`
- `DEV_DEPLOY_PATH`
- 서버 `.env`는 GitHub Actions가 만들지 않고 Oracle VM에서 서버 관리자가 직접 생성하는 기준으로 문서화
- 서버 `.env`가 없으면 workflow가 실패하도록 초안 작성
- `docker compose --env-file .env -f infra/docker/docker-compose.dev.yml up -d` 실행 초안 포함
- CD 실행 전 Oracle VM 준비 항목과 실패 시 확인 항목 문서화
- Oracle VM에서 dev compose 수동 검증 중 `eclipse-temurin:17-jre-alpine`의 ARM64 manifest 문제를 확인해 `eclipse-temurin:17-jre-jammy` 기준으로 정리
- 기존 운영 nginx가 host `80`을 사용 중인 VM에서 dev compose nginx는 host `18080`으로 검증하는 기준으로 정리
- 서버 내부 `curl http://localhost:18080/api/health` 응답 성공 확인

주의:

- 실제 Oracle VM dev compose 수동 검증은 수행했으나, 실제 Secret 값은 문서화하지 않았다.
- 실제 Secret 값을 작성하지 않았다.
- prod 배포 성공을 가정하지 않았다.
- 실제 IP, 도메인, DB 계정, 비밀번호, API Key, Secret은 작성하지 않았다.
- backend/frontend 기능 코드, DB migration, 실제 서비스 테이블, prod 설정, 테스트 코드는 수정하지 않았다.
- prod workflow를 만들지 않았다.
- prod docker-compose를 만들지 않았다.
- prod nginx 설정을 만들지 않았다.

### [8-3단계] Oracle VM dev 배포 수동 검증 완료

상태: 완료

완료 항목:

- Oracle VM에서 meet-or-solo dev 배포 수동 검증 완료
- `postgres` 컨테이너 Healthy 상태 확인
- `backend` 컨테이너 Running 상태 확인
- `nginx` 컨테이너 Started 상태 확인
- 서버 내부 `curl http://localhost:18080/api/health` 성공 확인
- 외부 브라우저 `http://<DEV_SERVER_HOST>:18080/api/health` 성공 확인
- health 응답 확인

```json
{"success":true,"data":{"status":"OK","service":"meet-or-solo-backend"},"error":null}
```

dev 서버 기준:

- dev 서버 접속 주소는 `http://<DEV_SERVER_HOST>:18080`
- health API 확인 주소는 `http://<DEV_SERVER_HOST>:18080/api/health`
- dev `CORS_ALLOWED_ORIGINS` 기준은 `http://<DEV_SERVER_HOST>:18080`
- 기존 Ubuntu nginx 또는 다른 서비스가 host `80`을 사용할 수 있으므로 현재 meet-or-solo dev는 host `80`을 사용하지 않음
- Oracle Cloud Ingress에서 `18080` 포트가 열려 있어야 함
- backend `8080`과 PostgreSQL `5432`는 외부에 직접 공개하지 않음
- PostgreSQL dev DB 직접 확인은 SSH tunnel `local 15432 -> server localhost 15432 -> postgres 5432` 기준으로 사용

주의:

- 실제 IP는 문서에 기록하지 않고 `<DEV_SERVER_HOST>` placeholder를 사용한다.
- 실제 DB 비밀번호, Secret, API Key는 작성하지 않았다.
- backend/frontend 기능 코드, DB migration, 실제 서비스 테이블, docker-compose, nginx 설정, GitHub Actions workflow는 수정하지 않았다.
- prod 배포는 아직 하지 않았다.

### [9-1단계] 실제 서비스 DB 설계 검토/확정

상태: 완료

완료 항목:

- `docs/11_DATABASE_DESIGN.md` 추가
- 실제 서비스 DB 테이블 후보를 MVP 필수와 추후 분리 후보로 구분
- `members`, `festivals`, `festival_checkins`, `match_pools`, `match_attempts`, `match_proposals`, `match_groups`, `reports` 등 핵심 테이블 설계안 정리
- 각 테이블별 목적, 주요 컬럼, PK, FK, 상태값, CHECK constraint 후보, UNIQUE constraint 후보, INDEX 후보, 개인정보/보안 고려사항, MVP 필수 여부 정리
- PostgreSQL 기준으로 `VARCHAR` + `CHECK constraint` 상태값 전략 정리
- 원본 GPS 좌표를 저장하지 않는 체크인 설계 원칙 재확인
- 자유 채팅 테이블을 만들지 않는 기준 재확인
- Redis 없이 PostgreSQL `status`, `expires_at`, `locked_at`, transaction lock, partial unique index를 활용하는 방향 정리
- 다음 9-2단계 Flyway SQL 파일 분리안 정리

주의:

- 실제 Flyway migration SQL 파일은 생성하지 않았다.
- `db/migration/V2~` 파일은 생성하지 않았다.
- 이미 적용된 `db/migration/V1__init.sql`은 수정하지 않았다.
- backend/frontend 기능 코드, nginx, docker-compose, GitHub Actions workflow는 수정하지 않았다.
- 실제 Oracle VM에 접속하지 않았다.
- 실제 DB migration을 적용하지 않았다.
- 실제 IP, 도메인, DB 계정, 비밀번호, API Key, Secret은 작성하지 않았다.

### [9-2단계] 실제 서비스 DB 테이블/Flyway migration

상태: 예정

- 9-1단계에서 확정한 `docs/11_DATABASE_DESIGN.md` 기준으로 `V2` 이후 migration 작성
- `db/migration/V2__create_core_tables.sql` 생성 후보
- `db/migration/V3__create_matching_tables.sql` 생성 후보
- `db/migration/V4__create_safety_admin_recommendation_tables.sql` 생성 후보
- 이미 적용된 migration은 수정하지 않고 새 버전으로 추가
- local/dev DB 모두 Flyway로 동일한 schema를 적용

### [10단계] 풀스택 A/B 기능 분업 시작

상태: 예정

- A/B가 공통 환경 기준으로 기능 개발 시작
- A 예시: 관광 API, 축제 목록/상세, 추천/솔로코스, 매칭 일부
- B 예시: Kakao OAuth, JWT, 회원/프로필, 체크인, 신고/평가
- 실제 담당 범위는 WBS에 맞춰 조정

## 4. 기능 분업 전까지 남은 작업

기능 분업을 시작하기 전에 공통 개발환경, dev 배포 초안, CI/CD 초안 정리를 완료했습니다. 다음 작업은 별도 승인 후 아래 중 하나로 진행합니다.

1. 기능 분업 전 최종 점검
2. [9-2단계] 실제 서비스 DB 테이블/Flyway migration
3. [10단계] 풀스택 A/B 기능 분업 시작

## 5. 현재 아직 하지 않은 것

- 실제 서비스 DB 테이블 생성
- `V2` 이상의 Flyway migration 작성
- 실제 서비스 React 화면 구현
- Kakao OAuth 로그인
- JWT 인증/인가
- 축제 목록/상세 기능
- 체크인 기능
- 매칭 알고리즘
- WebSocket STOMP
- `MatchRoomPage`
- 신고/제재 기능
- 테스트 코드
- prod nginx 설정
- prod docker-compose 배포 구성
- prod 배포

## 6. 현재까지 생성/수정된 주요 파일

- `.env.example`
- `docker-compose.local.yml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-local.yml`
- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/resources/application-prod.yml`
- `backend/src/main/java/.../global/health/HealthController.java`
- `backend/src/main/java/.../global/response/ApiResponse.java`
- `backend/src/main/java/.../global/error/ErrorCode.java`
- `backend/src/main/java/.../global/error/ErrorResponse.java`
- `backend/src/main/java/.../global/exception/BusinessException.java`
- `backend/src/main/java/.../global/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/.../global/config/CorsConfig.java`
- `db/migration/V1__init.sql`
- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/vite.config.ts`
- `frontend/index.html`
- `frontend/src/App.tsx`
- `frontend/src/main.tsx`
- `frontend/src/vite-env.d.ts`
- `frontend/src/api/types.ts`
- `frontend/src/api/apiClient.ts`
- `frontend/src/api/healthApi.ts`
- `frontend/src/pages/HealthCheckPage.tsx`
- `frontend/src/styles/global.css`
- `frontend/public/icons/placeholder.svg`
- `frontend/.env.local.example`
- `frontend/.env.production.example`
- `infra/docker/docker-compose.dev.yml`
- `infra/nginx/default.dev.conf`
- `infra/env/.env.dev.example`
- `.github/workflows/ci.yml`
- `.github/workflows/deploy-dev.yml`
- `README.md`
- `AGENTS.md`
- `CLAUDE.md`
- `docs/*.md`
- `docs/11_DATABASE_DESIGN.md`

## 7. 작업 규칙

- 새 작업을 시작하기 전 `docs/10_PROGRESS_LOG.md`를 먼저 확인한다.
- 현재 완료 단계와 다음 작업 단계를 확인한 뒤, 현재 단계에 맞는 작업만 수행한다.
- 기능 구현 전 작업 범위를 먼저 제안하고 사용자 승인을 받는다.
- 파일 생성/수정 전에는 변경 계획을 먼저 제안한다.
- 이미 적용된 migration 파일은 수정하지 않는다.
- `V1__init.sql`은 불필요하게 수정하지 않는다.
- 실제 비밀번호, API Key, Secret, 서버 IP, 도메인은 하드코딩하지 않는다.
- 사용자가 문서만 요청했다면 backend, frontend, DB migration, nginx, docker-compose, GitHub Actions, test 파일을 수정하지 않는다.
