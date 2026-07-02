# AGENTS.md

`meet-or-solo` 모노레포에서 Codex/AI가 작업 전에 반드시 읽어야 하는 최상위 규칙 문서입니다.

## 필수 확인 문서

작업을 시작하기 전에 이 파일과 `docs/*.md`를 먼저 확인합니다.

- [프로젝트 개요](docs/00_PROJECT_OVERVIEW.md)
- [아키텍처](docs/01_ARCHITECTURE.md)
- [개발환경](docs/02_DEV_ENVIRONMENT.md)
- [프론트엔드 가이드](docs/03_FRONTEND_GUIDE.md)
- [백엔드 가이드](docs/04_BACKEND_GUIDE.md)
- [매칭 정책](docs/05_MATCHING_POLICY.md)
- [보안 정책](docs/06_SECURITY_POLICY.md)
- [배포 방향](docs/07_DEPLOYMENT.md)
- [AI 작업 규칙](docs/08_AI_WORKING_RULES.md)
- [테스트/품질 전략](docs/09_TEST_AND_QUALITY_STRATEGY.md)

## 문서 작성 언어

- 이 프로젝트의 기본 문서 언어는 한국어로 한다.
- `README.md`, `docs/*.md`, `AGENTS.md`, `CLAUDE.md`의 본문은 한국어로 작성한다.
- 기술 용어, 파일명, 클래스명, 함수명, 환경변수명, 명령어, 경로는 영어 원문을 유지한다.
- 필요한 경우 한국어 설명 뒤에 영어 기술명을 병기한다.
- 코드 주석은 한국어를 우선하되, 표준 설정이나 외부 예시는 영어를 유지해도 된다.
- 새 문서를 생성하거나 기존 문서를 수정할 때 영어로 전체 작성하지 않는다.
- 사용자가 별도로 요청하지 않는 한 문서, 설명, 작업 요약은 한국어로 작성한다.

## 핵심 규칙

- 작업 전 `AGENTS.md`와 `docs/*.md`를 먼저 확인한다.
- 파일 생성/수정 전에는 변경 계획을 먼저 제안한다.
- 사용자가 "진행해줘"라고 승인한 범위만 작업한다.
- 1단계에서는 비즈니스 기능을 구현하지 않는다.
- Redis는 MVP 1단계에 추가하지 않는다.
- Redis는 추후 `MatchingStateStore`를 통해 확장 가능하게만 문서화한다.
- 자유 채팅 기능은 구현하지 않는다.
- `MatchRoomPage`는 자유 채팅방이 아니라 상태 동기화용 매칭방이다.
- WebSocket STOMP는 자유 채팅이 아니라 상태 동기화 전용이다.
- 운영 환경에서 PostgreSQL `5432`와 backend `8080`은 외부에 직접 노출하지 않는다.
- GitHub 원격 저장소는 아직 미연결 상태이므로 GitHub Actions, 서버 IP, 도메인, SSH 키, Secrets는 placeholder로만 작성한다.
- API Key, 비밀번호, SSH Key, 실제 도메인/IP를 하드코딩하지 않는다.
- `README.md`와 `docs`를 업데이트하지 않고 구조를 임의로 바꾸지 않는다.

## 테스트 작성 규칙

- 핵심 비즈니스 로직을 구현할 때는 가능한 한 테스트 코드를 함께 작성한다.
- 특히 매칭 알고리즘, 동시성 처리, 인증/인가, 패널티/쿨타임, 신고/제재 로직은 테스트 우선 대상이다.
- 테스트 전략은 [docs/09_TEST_AND_QUALITY_STRATEGY.md](docs/09_TEST_AND_QUALITY_STRATEGY.md)를 따른다.
- 단순 CRUD보다 장애 시 영향이 큰 로직을 우선 테스트한다.

## 현재 1단계 범위

사용자가 승인하면 1단계에서는 개발환경 세팅만 진행한다.

- 모노레포 구조 정리
- Spring Boot 실행 구조 정리
- React + TypeScript + Vite + PWA 스캐폴딩
- PostgreSQL Docker Compose
- Flyway 기본 설정
- `/api/health`
- React에서 `/api/health` 호출
- nginx reverse proxy 초안
- GitHub Actions 초안
- README 실행 방법

비즈니스 기능은 명시적 승인 전까지 placeholder 또는 TODO로만 남긴다.
