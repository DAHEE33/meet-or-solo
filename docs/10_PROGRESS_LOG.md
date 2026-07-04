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

다만 실제 작업 안정성을 위해 이 저장소에서는 Backend 공통 코드화와 Frontend 공통 코드화를 먼저 마무리한 뒤, Oracle VM dev 서버/dev DB, nginx/docker-compose dev 배포, GitHub Actions CI/CD 초안을 잡고 기능 분업으로 넘어갑니다.

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

## 3. 다음 작업 단계

### [4단계] Backend 공통 코드화

상태: 다음 작업

목표:

- 공통 응답 포맷
- 공통 예외 처리
- `ErrorCode`
- `BusinessException`
- `GlobalExceptionHandler`
- validation 에러 응답
- CORS local/dev/prod 기반 설정

주의:

- 아직 비즈니스 기능은 구현하지 않는다.
- 실제 서비스 DB 테이블은 만들지 않는다.
- DB migration은 추가하지 않는다.
- 인증, 매칭, 축제, 체크인, 신고 기능은 구현하지 않는다.

## 4. 이후 단계 순서

### [5단계] Frontend 공통 코드화

상태: 예정

- API client
- 공통 fetch wrapper
- loading/error UI
- route 구조
- layout 구조
- 환경변수 처리
- 실제 서비스 화면은 아직 구현하지 않음

### [6단계] Oracle VM dev 서버/dev DB 구축

상태: 예정

- `/home/ubuntu/meet-or-solo` 기준 dev 서버 배포 구조
- `SPRING_PROFILES_ACTIVE=dev`
- PostgreSQL dev DB
- PostgreSQL `5432` 외부 전체 공개 금지
- 필요 시 SSH tunnel 사용
- `prod`는 아직 만들지 않음

### [7단계] nginx + docker-compose dev 배포 초안

상태: 예정

- dev 배포 기준 docker-compose 구성
- frontend `dist`, backend app, postgres, nginx 연결
- prod용 docker-compose는 추후 분리
- 현재는 dev 배포 초안만 작성

### [8단계] GitHub Actions CI/CD 초안

상태: 예정

- backend build
- frontend build
- `develop` 또는 수동 workflow 기준 dev 배포 초안
- GitHub Secrets 사용
- 실제 운영/prod 자동 배포는 아직 하지 않음

### [9단계] 실제 서비스 DB 테이블/Flyway migration

상태: 예정

- `V2` 이후 migration 작성
- `member`, `festival`, `checkin`, `matching`, `report` 등 실제 테이블 생성
- 이미 적용된 migration은 수정하지 않고 새 버전으로 추가

### [10단계] 풀스택 A/B 기능 분업 시작

상태: 예정

- A/B가 공통 환경 기준으로 기능 개발 시작
- A 예시: 관광 API, 축제 목록/상세, 추천/솔로코스, 매칭 일부
- B 예시: Kakao OAuth, JWT, 회원/프로필, 체크인, 신고/평가
- 실제 담당 범위는 WBS에 맞춰 조정

## 5. 기능 분업 전까지 남은 작업

기능 분업을 시작하기 전에 아래 순서를 완료합니다.

1. [4단계] Backend 공통 코드화
2. [5단계] Frontend 공통 코드화
3. [6단계] Oracle VM dev 서버/dev DB 구축
4. [7단계] nginx + docker-compose dev 배포 초안
5. [8단계] GitHub Actions CI/CD 초안

## 6. 현재 아직 하지 않은 것

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
- docker-compose dev/prod 배포 구성
- prod 배포

## 7. 현재까지 생성/수정된 주요 파일

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

## 8. 작업 규칙

- 새 작업을 시작하기 전 `docs/10_PROGRESS_LOG.md`를 먼저 확인한다.
- 현재 완료 단계와 다음 작업 단계를 확인한 뒤, 현재 단계에 맞는 작업만 수행한다.
- 기능 구현 전 작업 범위를 먼저 제안하고 사용자 승인을 받는다.
- 파일 생성/수정 전에는 변경 계획을 먼저 제안한다.
- 이미 적용된 migration 파일은 수정하지 않는다.
- `V1__init.sql`은 불필요하게 수정하지 않는다.
- 실제 비밀번호, API Key, Secret, 서버 IP, 도메인은 하드코딩하지 않는다.
- 사용자가 문서만 요청했다면 backend, frontend, DB migration, nginx, docker-compose, GitHub Actions, test 파일을 수정하지 않는다.
