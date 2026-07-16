# 아키텍처

## 모노레포 구조

```text
meet-or-solo/
├─ .github/
├─ backend/
├─ frontend/
├─ infra/
├─ docs/
├─ AGENTS.md
├─ CLAUDE.md
└─ README.md
```

## 디렉터리 역할

| 경로 | 역할 |
| --- | --- |
| `frontend/` | React + TypeScript + Vite + PWA 소스. 운영 빌드 결과는 `dist`로 생성한다. |
| `backend/` | Spring Boot API, 인증, 매칭, Scheduler, WebSocket STOMP, Flyway migration을 담당한다. |
| `infra/` | nginx reverse proxy, certbot, 운영 배포 설정 초안을 둔다. |
| `.github/` | GitHub Actions workflow 초안을 둔다. GitHub 원격 연결 전까지 placeholder만 사용한다. |
| `docs/` | 프로젝트 규칙, 아키텍처, 정책, 구현 방향 문서를 둔다. |

이 구조를 변경하려면 `README.md`와 관련 `docs/*.md`를 함께 갱신해야 합니다.

## 운영 트래픽 구조

Nginx가 단일 공개 진입점입니다.

```text
Client
  |
  | HTTPS 443
  v
Nginx
  ├─ /       -> React static dist
  ├─ /api    -> Spring Boot backend:8080
  └─ /ws     -> Spring Boot WebSocket STOMP endpoint
```

공개 포트 정책:

- `80`: HTTPS redirect 및 Let's Encrypt challenge 용도
- `443`: 실제 HTTPS 서비스 트래픽
- `8080`: 외부 직접 노출 금지
- `5432`: 외부 직접 노출 금지

## 런타임 구성

MVP 런타임 구성:

- Nginx
- Spring Boot backend
- React static assets
- PostgreSQL
- Certbot/Let's Encrypt

Redis는 MVP 1단계 구성에 포함하지 않습니다.

## 백엔드 아키텍처 방향

Spring Boot는 다음을 담당합니다.

- `/api` 하위 REST API
- `/ws` 하위 WebSocket STOMP endpoint
- 매칭 탐색, 제안 만료, 정리 작업을 위한 Scheduler
- PostgreSQL 영속성
- Flyway migration
- 추후 Security, OAuth2, JWT, Refresh Token 처리

권장 패키지 방향:

```text
com.survey.meetorsolo
├─ auth
├─ user
├─ festival
├─ checkin
├─ matching
├─ notification
├─ safety
├─ admin
└─ common
```

## 프론트엔드 아키텍처 방향

프론트엔드는 모바일 우선 PWA입니다.

개발 환경:

- Vite dev server 사용
- 로컬 backend API 연결

운영 환경:

- `vite build`로 `dist` 생성
- Nginx가 `dist` 정적 파일 서빙
- 브라우저 라우팅은 `index.html` fallback 처리
- `/api`, `/ws`는 Spring Boot로 proxy

## PostgreSQL 중심 MVP

PostgreSQL은 MVP의 단일 신뢰 원천입니다.

PostgreSQL이 관리하는 데이터:

- 사용자
- 회원 자연어 취향과 임베딩
- 축제 및 관광공사 API 캐시 데이터
- GPS 체크인 결과
- 매칭풀
- 매칭 시도
- 매칭 제안과 응답
- 매칭 그룹
- 시스템 이벤트
- 신고와 관리자 조치

매칭 TTL과 상태는 다음 컬럼을 중심으로 관리합니다.

- `status`
- `entered_at`
- `expires_at`
- `responded_at`
- `locked_at`
- `confirmed_at`

Spring Scheduler가 만료와 상태 전이를 처리합니다.

매칭 후보 선점은 PostgreSQL 비관적 행 잠금인 `SELECT ... FOR UPDATE SKIP LOCKED`를 사용합니다. DB 전체나 테이블 전체를 장시간 잠그지 않고, 최종 후보로 선택된 `match_pools` row만 짧은 transaction 안에서 잠급니다.

`match_pools.lock_token`, `match_pools.locked_at`은 낙관적 락 구현이 아닙니다. transaction lock 위에서 어느 실행이 row를 선점했는지 추적하고, 죽은 worker가 남긴 stale lock을 회수하기 위한 애플리케이션 레벨 소유권 표시입니다.

회원 자연어 취향은 매칭 시도마다 `match_pools`에 복제하지 않고 회원 레벨의 `member_preference_embeddings`에 최신 원문과 임베딩을 1건 저장합니다. `member_travel_styles`는 정형 코드 점수로, 자연어 `preference_text`는 임베딩 유사도 점수로 각각 계산한 뒤 매칭 점수에서 결합합니다.

## Redis 전략

Redis는 MVP 1단계에서 제외합니다.

이유:

- Oracle Cloud 무료 VM의 메모리 여유가 크지 않다.
- 초기 매칭 규모에서는 PostgreSQL만으로 정확성을 확보할 수 있다.
- MVP 검증 단계에서는 운영 복잡도를 낮추는 것이 중요하다.

추후 Redis 활용 가능 영역:

- 매칭 제안 TTL
- 중복 요청 방지
- 분산 Rate Limiting
- 관광공사 API 응답 캐시
- 매칭 대기열 최적화
- 백엔드 수평 확장 시 WebSocket session 보조

Redis를 추후 도입하더라도 PostgreSQL을 최종 상태의 단일 신뢰 원천으로 유지하고, Redis는 캐시, TTL 보조, pub/sub 등으로 제한합니다.

확장 지점은 다음 추상화로 둡니다.

```text
MatchingStateStore
├─ PostgresMatchingStateStore
└─ RedisMatchingStateStore
```

## Oracle Cloud VM 고려사항

초기 운영 대상은 Oracle Cloud Ubuntu VM 1대입니다.

설계 제약:

- 상시 실행 서비스 수를 최소화한다.
- 불필요한 Redis 등 메모리 사용 서비스를 추가하지 않는다.
- frontend는 Nginx 정적 서빙을 우선한다.
- backend는 Nginx 뒤에 둔다.
- PostgreSQL은 외부에 직접 노출하지 않는다.
- 로그와 cron 작업은 디스크 사용량을 고려한다.
- IP, 도메인, SSH Key, GitHub Secrets는 확정 전까지 placeholder로만 작성한다.
