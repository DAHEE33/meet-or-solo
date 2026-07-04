# 진행 상태 기록

이 문서는 `meet-or-solo`의 현재 진행 상태와 다음 작업 후보를 기록합니다. 새 작업을 시작하기 전에 반드시 이 문서를 확인합니다.

## 1. 현재 완료된 단계

### [0단계] 프로젝트 방향/문서화 완료

- `README.md`, `AGENTS.md`, `CLAUDE.md` 작성
- `docs/00_PROJECT_OVERVIEW.md`부터 `docs/09_TEST_AND_QUALITY_STRATEGY.md`까지 문서 작성
- 문서는 한국어 중심으로 작성하고, 기술명/명령어/경로/env 이름은 영어 원문을 유지한다.
- Redis는 MVP 1단계에서 제외한다.
- WebSocket STOMP는 상태 동기화용이며 자유 채팅이 아니다.

### [1단계] Backend + Local PostgreSQL + Flyway 확인 완료

- backend Spring Boot 실행 확인
- `GET /api/health` 브라우저 응답 확인
- `docker-compose.local.yml` 기반 local PostgreSQL 컨테이너 실행 확인
- `.env` 기반 PostgreSQL 컨테이너 환경변수 확인
- `psql`로 local PostgreSQL 접속 확인
- Flyway 적용 이력 확인
- `select * from flyway_schema_history;` 조회 성공
- 현재 `V1__init.sql`은 DB/Flyway 연결 확인용 초기 migration이다.
- 실제 서비스 테이블은 아직 만들지 않았다.

### [2단계] local/dev/prod 방향 정리 및 마무리 완료

- `local`: 개인 PC Docker PostgreSQL
- `dev`: Oracle Cloud VM 개발/시연용 서버
- `prod`: 추후 제출/운영 단계에서 분리
- 현재 VM에는 `dev`만 배포하는 방향으로 결정
- `prod`는 추후 별도 디렉토리, 별도 DB, 별도 도메인 또는 외부 DB 서비스로 분리 가능하게 설계
- 초기 서버 배포는 `SPRING_PROFILES_ACTIVE=dev`를 사용
- PostgreSQL `5432`는 외부 전체 공개하지 않는다.
- DB 직접 접속이 필요하면 SSH tunnel 방식을 우선 고려한다.
- local 실행 시 `docker compose`는 프로젝트 루트에서 `--env-file .env`와 함께 실행한다.
- Spring Boot `bootRun`은 `.env`를 자동으로 읽지 않는다.
- PowerShell과 Git Bash의 환경변수는 서로 공유되지 않는다.
- Git Bash에서 `source .env`를 했다면 같은 Git Bash 터미널에서 `./gradlew bootRun`까지 실행한다.
- PowerShell에서 실행할 경우 `application-local.yml` fallback 값으로 실행하거나 PowerShell 환경변수를 직접 설정한다.
- 로컬 기본값은 `DB_URL=jdbc:postgresql://localhost:5432/meet_or_solo_local`, `DB_USERNAME=meet_user`, `DB_PASSWORD=meet_password` 기준으로 둔다.

### [3단계] Frontend PWA 기본 스캐폴딩 및 health 연동 확인 구성 완료

- `frontend`에 React + TypeScript + Vite 기본 구조를 구성했다.
- `vite-plugin-pwa` 기반 PWA 기본 설정을 추가했다.
- `manifest`의 앱 이름은 `meet-or-solo`로 설정했다.
- 아이콘은 `public/icons/placeholder.svg` placeholder로 두었다.
- `frontend/.env.local.example`, `frontend/.env.production.example`에 `VITE_API_BASE_URL` 예시를 추가했다.
- `src/api/healthApi.ts`에서 상대 경로 `/api/health`로 backend health API를 호출하도록 구성했다.
- `src/pages/HealthCheckPage.tsx`에서 loading, success, error 상태를 표시하도록 구성했다.
- local 개발에서 `/api/health`가 Vite dev server `localhost:5173`으로 요청되어 404가 발생하는 이슈가 있었고, `frontend/vite.config.ts`의 Vite proxy로 `/api` 요청을 backend `localhost:8080`으로 전달하도록 정리했다.
- frontend 화면에서 backend `GET /api/health` 연동을 확인했다.
- local 개발에서는 `VITE_API_BASE_URL`을 비워두고 상대 경로 `/api/health`와 Vite proxy를 사용한다.
- 현재 PWA는 기본 shell, manifest, service worker 생성 설정, placeholder icon 수준이다.
- `frontend/dist/`는 build 결과물이므로 커밋하지 않는다.
- 현재 frontend 화면은 개발 연결 확인용이며 실제 서비스 UI가 아니다.

## 2. 현재까지 생성/수정된 주요 파일

- `.env.example`
- `docker-compose.local.yml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-local.yml`
- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/resources/application-prod.yml`
- `backend/src/main/java/.../global/health/HealthController.java`
- `db/migration/V1__init.sql`
- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/vite.config.ts`
- `frontend/index.html`
- `frontend/src/App.tsx`
- `frontend/src/main.tsx`
- `frontend/src/vite-env.d.ts`
- `frontend/src/api/healthApi.ts`
- `frontend/src/pages/HealthCheckPage.tsx`
- `frontend/src/styles/global.css`
- `frontend/public/icons/placeholder.svg`
- `frontend/.env.local.example`
- `frontend/.env.production.example`
- `README.md`
- `AGENTS.md`
- `CLAUDE.md`
- `docs/*.md`

## 3. 현재 아직 하지 않은 것

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
- GitHub Actions
- nginx 설정
- Oracle VM 실제 배포
- prod 배포

## 4. 다음 작업 후보

다음 작업은 아래 순서로 진행한다.

### [4단계]

- backend 공통 응답/예외/validation/CORS 구조 정리
- 아직 인증/매칭 비즈니스 로직은 구현하지 않음

### [5단계]

- 실제 DB 설계 검토
- `V2__create_member_tables.sql` 등 실제 Flyway migration 추가

## 5. 작업 규칙

- 새 작업을 시작하기 전 `docs/10_PROGRESS_LOG.md`를 먼저 확인한다.
- 기능 구현 전 작업 범위를 먼저 제안하고 사용자 승인을 받는다.
- 이미 적용된 migration 파일은 수정하지 않는다.
- `V1__init.sql`은 불필요하게 수정하지 않는다.
- 실제 비밀번호, API Key, Secret, 서버 IP, 도메인은 하드코딩하지 않는다.
- `frontend`/`nginx`/GitHub Actions/test는 요청 범위에 있을 때만 수정한다.
