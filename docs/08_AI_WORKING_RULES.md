# AI 작업 규칙

이 문서는 `AGENTS.md`의 최상위 규칙을 더 자세히 설명합니다.

## 작업 시작 체크리스트

작업 전 반드시 다음을 수행합니다.

1. `AGENTS.md`를 읽는다.
2. 관련 `docs/*.md`를 읽는다.
3. 사용자의 현재 요청 범위를 확인한다.
4. 사용자가 계획만 요청했는지, 파일 수정을 요청했는지 구분한다.
5. 파일 수정이 필요하면 수정 전 변경 계획을 먼저 제안한다.

## 문서 작성 언어

- 이 프로젝트의 기본 문서 언어는 한국어로 한다.
- `README.md`, `docs/*.md`, `AGENTS.md`, `CLAUDE.md`의 본문은 한국어로 작성한다.
- 기술 용어, 파일명, 클래스명, 함수명, 환경변수명, 명령어, 경로는 영어 원문을 유지한다.
- 필요한 경우 한국어 설명 뒤에 영어 기술명을 병기한다.
- 코드 주석은 한국어를 우선하되, 표준 설정이나 외부 예시는 영어를 유지해도 된다.
- 새 문서를 생성하거나 기존 문서를 수정할 때 영어로 전체 작성하지 않는다.
- 사용자가 별도로 요청하지 않는 한 문서, 설명, 작업 요약은 한국어로 작성한다.

## 승인 규칙

사용자가 범위를 승인하기 전에는 파일을 생성하거나 수정하지 않습니다.

승인 표현:

```text
진행해줘
```

사용자가 계획만 요청하면 텍스트로만 답합니다.

## 범위 준수

승인된 범위 안에서만 작업합니다.

승인 범위가 1단계 개발환경 세팅이면 다음을 구현하지 않습니다.

- OAuth login
- JWT auth
- 관광공사 OpenAPI 실제 연동
- GPS check-in
- 자동 매칭
- WebSocket event logic
- 관리자 business function
- Redis
- 자유 채팅

비즈니스 기능은 명시적으로 필요한 경우 TODO 또는 placeholder로만 표현합니다.

## 문서 우선 원칙

저장소 구조를 바꾸려면 아래 문서를 함께 갱신합니다.

- `README.md`
- 관련 `docs/*.md`
- AI 규칙이 바뀌면 `AGENTS.md`
- Claude Code 메모리가 바뀌면 `CLAUDE.md`

## Redis 규칙

Redis는 MVP 1단계에 포함하지 않습니다.

추가 금지 항목:

- Redis Docker service
- Redis dependency
- Redis configuration
- Redis code path

Redis는 추후 확장안으로만 문서화합니다.

```text
MatchingStateStore
```

초기 구현은 PostgreSQL 기반입니다.

## 매칭 규칙

매칭 시스템은 프로젝트 정책을 따라야 합니다.

- 같은 축제에 GPS 체크인된 사용자만 매칭풀에 진입한다.
- 희망 인원은 2명, 3명, 4명이다.
- 3명/4명을 선택한 사용자는 2명 진행 허용 여부를 선택한다.
- 탐색 시간은 60초다.
- 제안 응답 시간은 30초다.
- timeout은 자동 거절이다.
- 인원 미달 시 명시적 팝업을 띄운다.
- 차단 관계는 양방향으로 제외한다.
- 이미 매칭 중인 사용자는 제외한다.
- MVP에서는 PostgreSQL 상태가 authoritative source다.
- `SELECT FOR UPDATE SKIP LOCKED` 같은 transaction lock을 사용한다.

## 자유 채팅 금지

자유 채팅을 구현하지 않습니다.

`MatchRoomPage`는 채팅방이 아니라 상태방입니다.

허용 인터랙션:

- 도착 시간 선택
- 도착했어요 버튼
- 취소 버튼
- 구조화된 취소 사유
- 구조화된 신고 사유
- 긴급 도움
- 안전 리마인드 확인

금지 인터랙션:

- 자유 텍스트 메시지 입력
- 사용자 간 채팅 메시지
- 제한 없는 대화처럼 보이는 채팅방 UX

## WebSocket STOMP 규칙

WebSocket STOMP는 상태 동기화 전용입니다.

허용 event category:

- match proposed
- match accepted/rejected/timeout
- insufficient members
- match confirmed/cancelled
- arrival time selected
- member arrived
- member cancelled
- safety reminder

chat topic이나 message broadcast endpoint를 추가하지 않습니다.

## 보안 규칙

하드코딩 금지:

- API Key
- password
- SSH private key
- 실제 GitHub Secrets 값
- 실제 운영 domain
- 실제 server IP
- OAuth client secret

문서와 예시에는 placeholder를 사용합니다.

운영 노출 규칙:

- PostgreSQL `5432`를 외부에 노출하지 않는다.
- backend `8080`을 외부에 직접 노출하지 않는다.
- Nginx를 공개 진입점으로 사용한다.
- 운영은 HTTPS/TLS를 사용한다.

## GitHub와 배포 placeholder

GitHub 원격 저장소는 아직 미연결 상태입니다.

따라서 다음은 placeholder로만 작성합니다.

- GitHub Actions
- server IP
- domain
- SSH Key
- Secrets

## 파일 안전 규칙

수정 전:

- 현재 파일 내용을 확인한다.
- 사용자 작업을 덮어쓰지 않는다.
- 요청된 파일만 수정한다.
- 사용자가 문서만 요청했다면 backend, frontend, Docker, nginx, GitHub Actions 파일을 수정하지 않는다.

## 1단계 개발환경 세팅 경계

승인 후 1단계에서 허용되는 작업:

- 모노레포 구조 정리
- backend Spring Boot 실행 구조
- frontend React + TypeScript + Vite + PWA 스캐폴딩
- PostgreSQL Docker Compose
- Flyway 기본 설정
- `/api/health`
- frontend에서 `/api/health` 호출
- nginx reverse proxy 초안
- GitHub Actions 초안
- README 실행 방법

1단계에서 금지되는 작업:

- 실제 매칭 구현
- 실제 인증 구현
- 관광공사 OpenAPI 실제 연동
- Redis
- 자유 채팅
- 운영 Secret
