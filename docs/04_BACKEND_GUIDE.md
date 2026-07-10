# 백엔드 가이드

## 백엔드 방향

백엔드는 Spring Boot 기반 서비스입니다. 비즈니스 규칙, 영속성, 보안, 매칭 상태, 상태 동기화를 담당합니다.

제공 예정 인터페이스:

- `/api` 하위 REST API
- `/ws` 하위 WebSocket STOMP endpoint
- Scheduler 기반 background job
- Flyway 기반 DB schema 관리

## 패키지 방향

권장 패키지 구조:

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

각 domain은 controller, service, repository, DTO, entity 책임을 명확히 나눕니다. 예외, 공통 응답, 보안 utility, 암호화, audit 등은 `common`에 둡니다.

현재 4단계 Backend 공통 코드화에서는 실제 domain 기능을 만들지 않고 아래 공통 패키지만 정리합니다.

```text
com.survey.meetorsolo
├─ global
│  ├─ config
│  ├─ error
│  ├─ exception
│  ├─ health
│  └─ response
└─ domain
```

## 공통 응답 포맷

REST API 응답은 `ApiResponse`로 감싸는 것을 기본으로 합니다.

성공 응답:

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

실패 응답:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "요청 값 검증에 실패했습니다.",
    "fields": []
  }
}
```

현재 공통 구조는 MVP 수준으로 유지합니다. trace id, error detail, debug field 같은 운영 확장 필드는 필요해질 때 별도 승인 후 추가합니다.

## 공통 예외 처리

공통 예외 구조:

- `ErrorCode`: HTTP status, 에러 코드, 기본 메시지를 정의한다.
- `ErrorResponse`: 실패 응답의 `error` 객체를 표현한다.
- `BusinessException`: domain/service에서 명시적으로 던질 비즈니스 예외의 기본형이다.
- `GlobalExceptionHandler`: validation, `BusinessException`, 예상하지 못한 예외를 공통 응답으로 변환한다.

`GlobalExceptionHandler`는 응답에 stack trace, DB URL, 환경변수, 내부 예외 상세를 노출하지 않습니다. 예상하지 못한 예외는 서버 로그에만 기록하고, 클라이언트에는 `INTERNAL_SERVER_ERROR` 공통 메시지만 반환합니다.

## Validation 에러 응답

`spring-boot-starter-validation` 기반 validation 실패는 공통 실패 응답으로 반환합니다.

예시:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "요청 값 검증에 실패했습니다.",
    "fields": [
      {
        "field": "name",
        "message": "must not be blank"
      }
    ]
  }
}
```

현재 단계에서는 실제 비즈니스 DTO를 만들지 않습니다.

## CORS

CORS는 `global/config/CorsConfig`에서 `/api/**` 기준으로 설정합니다.

기본 방향:

- local 기본 허용 origin: `http://localhost:5173`
- 현재 local frontend는 Vite proxy를 우선 사용한다.
- dev/prod는 `CORS_ALLOWED_ORIGINS` 환경변수로 확장 가능하게 둔다.
- 실제 IP, 실제 도메인, Secret은 하드코딩하지 않는다.
- credential 기반 CORS는 아직 사용하지 않는다.

## 설정 profile

설정은 YAML 중심으로 관리합니다.

예정 파일:

```text
application.yml
application-local.yml
application-dev.yml
application-prod.yml
```

`local`:

- 로컬 PostgreSQL URL
- 로컬 CORS origin
- 개발 로그

`dev`:

- Oracle Cloud VM의 개발/시연용 PostgreSQL 연결
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경변수 기반 설정
- 초기 서버 배포 대상 profile
- 실제 서버 IP, DB 계정, 비밀번호는 저장소에 기록하지 않음

`prod`:

- 환경변수 기반 Secret
- 제한된 CORS
- 보안 header
- 저장소에 DB credential 미포함
- 추후 제출/운영 단계에서 dev와 분리하기 위한 profile

`application.yml`은 기본 profile을 `local`로 둡니다. 서버 배포 시에는 환경변수로 `SPRING_PROFILES_ACTIVE=dev`를 명시합니다.

## Flyway

Flyway는 초기부터 활성화합니다.

사용 이유:

- DB schema도 애플리케이션 동작의 일부이다.
- 매칭 상태는 constraint와 index가 중요하다.
- 배포가 반복 가능해야 한다.
- schema 변경 이력이 남아야 한다.

Migration 파일은 영속 데이터에 영향을 주므로 신중히 검토합니다.

Spring Boot 실행 시 Flyway는 `flyway_schema_history` 테이블을 확인하고, 아직 적용되지 않은 migration SQL을 자동 실행합니다.

표준 migration 위치는 `backend/src/main/resources/db/migration`입니다. Spring Boot/Flyway 기본 classpath 경로인 `classpath:db/migration`을 사용하며, migration SQL은 backend jar에 포함됩니다. dev 배포 시 migration SQL을 별도로 서버에 복사하거나 컨테이너에 mount하지 않습니다.

현재 `V1__init.sql`은 DB/Flyway 연결 확인용 초기 migration입니다. 실제 서비스 테이블은 `V2`, `V3`, `V4` 파일로 추가되어 있습니다.

DB volume을 초기화하면 `flyway_schema_history`도 함께 사라집니다. DB 초기화 후에는 Spring Boot를 다시 실행해야 Flyway migration이 적용됩니다.

`flyway_schema_history`가 없으면 먼저 backend를 `local` profile로 실행합니다. Spring Boot 시작 과정에서 Flyway가 migration을 적용하고 `flyway_schema_history`를 다시 생성합니다.

이미 적용된 `V1`~`V4` migration 파일은 수정하지 않습니다. 변경이 필요하면 `V5__...sql`처럼 새 migration 파일을 추가합니다.

`V1__init.sql`은 DB/Flyway 연결 확인용 초기 migration이므로 불필요하게 수정하지 않습니다. 루트 `db/migration`은 더 이상 사용하지 않습니다.

## PostgreSQL

PostgreSQL은 MVP의 단일 신뢰 원천입니다.

### 날짜·시간 저장 및 API 기준

- Flyway의 기존 `TIMESTAMPTZ` 컬럼을 유지합니다.
- Entity의 `OffsetDateTime` 값은 `Asia/Seoul` 기준 `+09:00` offset으로 생성합니다.
- JVM 기본 timezone과 `hibernate.jdbc.time_zone`을 `Asia/Seoul`로 고정합니다.
- HikariCP가 연결을 만들 때 `SET TIME ZONE 'Asia/Seoul'`을 실행해 애플리케이션 DB session 기준도 고정합니다.
- Jackson은 `Asia/Seoul` 기준 ISO-8601 문자열과 `+09:00` offset을 사용하며 epoch timestamp로 직렬화하지 않습니다.
- frontend는 API의 절대 시점을 KST 형식으로 렌더링할 뿐 9시간을 수동으로 더하지 않습니다.
- PostgreSQL server/session timezone은 Flyway migration으로 관리하지 않습니다.

관리 대상:

- 사용자 데이터
- 축제와 관광공사 데이터
- 매칭풀 상태
- 매칭 시도
- 매칭 제안과 응답
- 매칭 그룹과 이벤트
- 신고와 관리자 조치

MVP 매칭은 다음을 활용합니다.

- `status`
- `expires_at`
- `responded_at`
- transaction lock
- unique constraint
- Scheduler cleanup

## Health API

현재는 개발환경 검증용 최소 endpoint만 둡니다.

```text
GET /api/health
```

응답:

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

이 endpoint는 Secret, host 상세 정보, DB URL, 환경변수를 노출하지 않습니다.

4단계에서 `HealthController`는 공통 `ApiResponse` 포맷을 적용했습니다. 현재 frontend `healthApi`와 `HealthCheckPage`는 기존 health 응답 형태를 기준으로 작성되어 있으므로, 5단계 Frontend 공통 코드화에서 새 `ApiResponse` 포맷에 맞게 수정해야 합니다.

## 추후 보안 기능

1단계 이후 구현 예정:

- Spring Security 설정
- Kakao OAuth2 로그인
- JWT Access Token
- Refresh Token rotation/storage
- 관리자 role 기반 권한
- local/prod CORS 분리
- Rate Limiting
- token 기반 인증에 맞는 CSRF 정책

## WebSocket과 Scheduler

WebSocket STOMP는 상태 동기화 전용입니다.

자유 채팅 기능으로 확장하지 않습니다.

Scheduler 예정 작업:

- 매칭 탐색 시간 만료
- 매칭 제안 timeout 처리
- penalty/cooldown 정리
- 관광공사 API 데이터 갱신
- 운영 로그 정리

## MatchingStateStore

매칭 상태 작업은 추상화합니다.

초기 구현체:

```text
PostgresMatchingStateStore
```

추후 선택 구현체:

```text
RedisMatchingStateStore
```

예상 책임:

- 매칭풀 진입
- 후보 lock
- 매칭 시도 생성
- 매칭 제안 생성
- 응답 기록
- 제안 만료
- 그룹 확정
- 그룹 취소

## Redis

Redis는 MVP 1단계에 추가하지 않습니다.

추후 Redis 활용 가능 영역:

- TTL 기반 매칭 제안
- 중복 요청 방지
- 분산 Rate Limiting
- 관광공사 API cache
- 매칭 대기열 최적화

Redis를 명시적으로 도입하기 전까지 backend는 Redis 전용 동작에 의존하지 않습니다.
