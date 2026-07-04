# CLAUDE.md

이 파일은 `meet-or-solo` 모노레포에서 Claude Code가 작업 전에 참고해야 하는 프로젝트 메모리 문서입니다.

## 작업 시작 전 확인

Claude Code는 제안, 파일 생성, 파일 수정 전에 아래 문서를 먼저 확인해야 합니다.

1. `AGENTS.md`
2. 관련 `docs/*.md`
3. `docs/10_PROGRESS_LOG.md`

자세한 AI 작업 규칙은 [docs/08_AI_WORKING_RULES.md](docs/08_AI_WORKING_RULES.md)를 따른다.
테스트 전략은 [docs/09_TEST_AND_QUALITY_STRATEGY.md](docs/09_TEST_AND_QUALITY_STRATEGY.md)를 따른다.
새 작업을 시작하기 전에는 [docs/10_PROGRESS_LOG.md](docs/10_PROGRESS_LOG.md)를 확인하고, 현재 WBS 단계에 맞는 작업만 수행한다.

## 프로젝트 요약

`meet-or-solo`는 강원도 축제 현장에서 혼자 방문한 사용자를 2~4인 소그룹으로 즉석 매칭하는 PWA 서비스입니다. 같은 축제에 GPS 체크인된 사용자만 매칭풀에 들어갈 수 있고, 매칭 실패 시 관광공사 OpenAPI 기반 솔로 코스로 전환합니다.

## 문서 작성 언어

- 이 프로젝트의 기본 문서 언어는 한국어로 한다.
- `README.md`, `docs/*.md`, `AGENTS.md`, `CLAUDE.md`의 본문은 한국어로 작성한다.
- 기술 용어, 파일명, 클래스명, 함수명, 환경변수명, 명령어, 경로는 영어 원문을 유지한다.
- 필요한 경우 한국어 설명 뒤에 영어 기술명을 병기한다.
- 코드 주석은 한국어를 우선하되, 표준 설정이나 외부 예시는 영어를 유지해도 된다.
- 새 문서를 생성하거나 기존 문서를 수정할 때 영어로 전체 작성하지 않는다.
- 사용자가 별도로 요청하지 않는 한 문서, 설명, 작업 요약은 한국어로 작성한다.

## 반드시 지킬 규칙

- 파일 생성/수정 전에는 변경 계획을 먼저 제안한다.
- 사용자가 "진행해줘"라고 승인한 범위만 작업한다.
- `docs/10_PROGRESS_LOG.md`의 WBS 단계 순서를 벗어나 공통 코드화, 배포, CI/CD, 기능 구현을 임의로 앞당기지 않는다.
- Backend/Frontend 공통 코드화 단계에서는 비즈니스 기능을 구현하지 않는다.
- Redis는 MVP 초기 단계에 추가하지 않는다.
- Redis는 추후 `MatchingStateStore`를 통해 확장 가능하게만 문서화한다.
- 자유 채팅 기능은 구현하지 않는다.
- `MatchRoomPage`는 매칭, 도착, 취소, 안전, 시스템 이벤트를 동기화하는 상태방이다.
- WebSocket STOMP는 자유 채팅이 아니라 상태 동기화 전용이다.
- 운영 환경에서 PostgreSQL `5432`를 외부에 직접 노출하지 않는다.
- 운영 환경에서 backend `8080`을 외부에 직접 노출하지 않는다.
- GitHub 원격 저장소는 아직 미연결 상태이므로 Actions, 서버 IP, 도메인, SSH 키, Secrets는 placeholder로만 다룬다.
- API Key, 비밀번호, SSH Key, 실제 도메인/IP를 하드코딩하지 않는다.
- 저장소 구조를 바꾸려면 먼저 `README.md`와 `docs`를 함께 갱신해야 한다.

## 테스트 작성 규칙

- 핵심 비즈니스 로직을 구현할 때는 가능한 한 테스트 코드를 함께 작성한다.
- 특히 매칭 알고리즘, 동시성 처리, 인증/인가, 패널티/쿨타임, 신고/제재 로직은 테스트 우선 대상이다.
- 테스트 전략은 [docs/09_TEST_AND_QUALITY_STRATEGY.md](docs/09_TEST_AND_QUALITY_STRATEGY.md)를 따른다.
- 단순 CRUD보다 장애 시 영향이 큰 로직을 우선 테스트한다.

## WBS 진행 기준

현재 단계와 다음 작업은 [docs/10_PROGRESS_LOG.md](docs/10_PROGRESS_LOG.md)를 기준으로 한다.

현재 완료된 큰 흐름:

- 0단계 프로젝트 방향/문서화
- 1단계 Backend + Local PostgreSQL + Flyway 확인
- 2단계 local/dev/prod 실행 전략 정리
- 3단계 Frontend PWA 기본 스캐폴딩 + `/api/health` 연동

다음 작업은 4단계 Backend 공통 코드화이다. 이후 5단계 Frontend 공통 코드화, 6단계 Oracle VM dev 서버/dev DB 구축, 7단계 nginx/docker-compose dev 배포, 8단계 GitHub Actions CI/CD 초안 순서로 진행한다.

OAuth, JWT, 관광공사 OpenAPI 연동, GPS 체크인, 자동 매칭, WebSocket 이벤트, 관리자 기능, 배포 자동화는 해당 WBS 단계에서 별도 승인 후 진행한다.
