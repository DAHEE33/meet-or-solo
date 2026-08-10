# 백엔드 가이드

## 만남 장소 확정 transaction

축제 만남 장소는 기존 proposal 응답 transaction 안에서 확정합니다. 잠금 순서는
attempt, proposal, attempt member, 정렬된 pool, festival이며 festival row lock 뒤
활성 후보 조회와 기존 snapshot group 수 계산을 수행합니다.

## 네이버 OAuth 로그인

- 기존 Kakao OAuth와 같은 `domain/auth`의 회원 조회, JWT, Refresh Token 발급 흐름을 사용한다.
- 회원은 이메일이 아니라 `(provider, provider_user_id)`로 식별한다.
- 네이버 `response.id`만 필수 식별값이며 선택 프로필 값의 null은 정상 처리한다.
- OAuth state는 provider별 HttpOnly callback 전용 쿠키로 5분간 보관하고 callback에서 즉시 제거한다.
- 네이버 외부 API는 connect/read timeout을 명시하며 token, code, secret, 프로필 원문을 로그에 남기지 않는다.
- Refresh Token은 회원당 1개를 유지하고 재로그인 시 원문이 아닌 hash와 만료시각을 rotation한다.
- OAuth email은 nullable, non-unique 참고 정보로만 저장하며 회원 식별이나 계정 병합에 사용하지 않는다.

## 백엔드 방향

백엔드는 Spring Boot 기반 서비스입니다. 비즈니스 규칙, 영속성, 보안, 매칭 상태, 상태 동기화를 담당합니다.

제공 예정 인터페이스:

- `/api` 하위 REST API
- `/ws` 하위 WebSocket STOMP endpoint
- Scheduler 기반 background job
- Flyway 기반 DB schema 관리

## 회원 프로필 validation

- 닉네임은 `2~12자`로 제한한다.
- 닉네임은 한글, 영문 대소문자, 숫자만 허용한다.
- 공백, 이모지, 특수문자는 backend validation에서 거절한다.
- frontend 입력 제한과 관계없이 backend의 `UpdateMemberProfileRequest` validation을 최종 기준으로 둔다.

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

## current match group 조회

현재 `PUT /api/matching/groups/me/current/arrival`은 body와 식별자를 받지 않고
인증 회원의 group/member를 `group row -> member row` 순서로 잠급니다. 최초
도착이면 group을 IN_PROGRESS로 전환하고 갱신된 전체 snapshot을 반환합니다.
도착은 `now < confirmedAt + 30분`에서만 허용하고 deadline 정각부터는
NO_SHOW Scheduler 판정 대상으로 넘깁니다.

후속 단말 위치 확인에서는 신고 완료와 위치정보 약관·동의 적용을 전제로 이
API가 브라우저에서 측정한 위도·경도, 정확도와 측정 시각을 받습니다. Backend는
인증 회원의 group snapshot에 저장된 만남 포인트와의 거리를 직접 계산하고,
정확도·측정값 유효시간·도착 허용 반경을 모두 만족할 때만 기존 상태 전이를
수행합니다. 클라이언트가 계산한 거리나 `verified` 값은 받거나 신뢰하지
않습니다. 원본 사용자 좌표는 DB, event와 log에 저장하지 않고 요청 처리 후
폐기합니다. GPS 조작 가능성이 남으므로 허위 도착 분쟁은 신고와 운영 검토로
보완합니다.

`PUT /api/matching/groups/me/current/cancellation`은 회원/group 식별자를 받지
않고 `SCHEDULE_CHANGED`, `TRANSPORTATION_ISSUE`, `OTHER` 중 하나만 받습니다.
취소 상세 사유는 다른 회원에게 공개하지 않으며 성공 응답은 group 유지 여부와
현재 유효 인원 수를 제공합니다.

`GET /api/matching/groups/me/current`는 path, query, body의 회원/group 식별자를
받지 않고 `access_token` HttpOnly cookie의 로그인 회원만 기준으로 조회합니다.
응답은 기존 group 필드와 함께 `festivals`의 최소 summary 및 active member의
공개 상태를 제공합니다.

- group: `groupId`, `festivalId`, `status`, `confirmedMemberCount`, `confirmedAt`
- festival: `festivalId`, `title`, `address`, `eventStartDate`, `eventEndDate`
- meeting point(후속): 장소 ID, 장소명, 주소, 좌표와 단말 확인 반경 안내
- member: `memberId`, `nickname`, `profileImageUrl`, `status`

group/festival projection 1회와 member/profile projection 1회로 조회해 N+1을
방지합니다. current group이 없으면 `200 OK`, `data:null`이고 정합성 충돌은
`MATCHING_CONFLICT`입니다.

### 정상 완료 후 재매칭 제한 계약

MVP의 체크인과 확정 매칭 유효시간은 기존 기획서의 2시간에서 각각 1시간으로
조정합니다. 정상 완료 group은 active current-group에서 계속 제외하되, 신규
pool 신청과 restriction 조회에서는 로그인 회원의 최근 `COMPLETED` group을
확인합니다.

```text
completion_lock_expires_at = match_groups.confirmed_at + 1시간
```

현재 시각이 이 값보다 이르면 신규 pool 신청을 거절하고 restriction 응답에
종료 시각과 남은 초를 제공합니다. 이 제한은 귀책 penalty가 아니므로
`match_cooldowns`에 정상 완료 row를 추가하지 않고 완료 group 이력에서
파생하는 방향을 우선합니다. 새 active group이 있으면 기존 active group 제한을
가장 먼저 적용합니다. 완료 횟수 최대 3회 같은 별도 횟수 제한은 MVP에 추가하지
않습니다.

구현은 최근 `COMPLETED` group과 로그인 회원의 `COMPLETED` member 관계만
조회하고 `confirmed_at + 1시간`을 계산합니다. restriction 응답의
`completionLock`은 `active`, `reason=MATCH_VALIDITY`, `groupId`, `startsAt`,
`expiresAt`, `remainingSeconds`를 제공하며 귀책 cooldown과 별도입니다. 신규 pool
신청은 active pool/group 검증을 먼저 적용한 뒤 cooldown과 완료 제한을 검증하고,
완료 제한 중에는 `MATCHING_COMPLETION_LOCKED`를 반환합니다. 정확한 경계에서는
제한이 종료됩니다.

도착 예정 시간은
`PUT /api/matching/groups/me/current/arrival-time`에서 변경합니다. request에는
`arrivalMinutes`만 포함하며 신규 요청은 `5`, `10`, `20`, `25`만 허용합니다.
DB CHECK와 조회 DTO/parser는 기존 row/event 호환을 위해 `0`, `30`도 계속
허용하지만 신규 PUT API에서는 거절합니다. 서버는
인증 회원의 active group과 member를 직접 찾고 group row, group member row
순서로 잠급니다. 실제 변경은 member update와 `match_events` insert를 같은
transaction에서 처리하고 알림은 `AFTER_COMMIT`에만 전송합니다.

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

## 프로필 이미지 Object Storage

- `members.profile_image_url`은 Kakao/Naver OAuth가 제공한 외부 URL 용도로 유지합니다.
- `V9__add_member_profile_image_object_key.sql`은 직접 업로드한 이미지의 object key를 저장하는 nullable `profile_image_object_key`를 추가합니다.
- object key 형식은 `{OCI_OBJECT_STORAGE_PROFILE_PREFIX}/{memberId}/{uuid}.{extension}`이며 local 기본값은 `profiles/local`입니다.
- `POST /api/members/me/profile-image`는 인증 cookie와 multipart `file`을 받아 JPEG, PNG, WEBP 및 최대 크기를 검증합니다. MIME 타입과 파일 시그니처를 함께 확인합니다.
- 새 object 업로드와 DB flush가 성공한 뒤 transaction commit 시 기존 object를 삭제합니다. DB transaction이 rollback되면 새 object를 정리합니다.
- `GET /api/members/me`의 `profileImageUrl`은 직접 업로드 object가 있으면 `/api/members/me/profile-image`, 없으면 OAuth URL, 둘 다 없으면 `null`입니다.
- `GET /api/members/me/profile-image`는 본인 object key만 조회해 private bucket의 bytes를 `no-store` 응답으로 중계합니다.
- Object Storage 장애 응답에는 endpoint, access key, secret key, object key 같은 내부 정보를 노출하지 않습니다.
- OCI S3 Compatibility API는 `aws-chunked` content encoding을 지원하지 않으므로 S3 client의 `chunkedEncodingEnabled`를 `false`, `requestChecksumCalculation`을 `WHEN_REQUIRED`로 설정합니다.

### 날짜·시간 저장 및 API 기준

- Flyway의 기존 `TIMESTAMPTZ` 컬럼을 유지합니다.
- Entity의 `OffsetDateTime` 값은 `Asia/Seoul` 기준 `+09:00` offset으로 생성합니다.
- JVM 기본 timezone과 `hibernate.jdbc.time_zone`을 `Asia/Seoul`로 고정합니다.
- HikariCP가 연결을 만들 때 `SET TIME ZONE 'Asia/Seoul'`을 실행해 애플리케이션 DB session 기준도 고정합니다.
- Jackson은 `Asia/Seoul` 기준 ISO-8601 문자열과 `+09:00` offset을 사용하며 epoch timestamp로 직렬화하지 않습니다.
- frontend는 API의 절대 시점을 KST 형식으로 렌더링할 뿐 9시간을 수동으로 더하지 않습니다.
- PostgreSQL server/session timezone은 Flyway migration으로 관리하지 않고 local/dev compose의 `postgres -c timezone=Asia/Seoul`과 기존 DB용 수동 `ALTER DATABASE` script로 관리합니다.

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

### JWT 만료시간 설정

- `JWT_ACCESS_TOKEN_EXPIRES_MINUTES`는 DB에 저장하지 않는 Access Token의 JWT `exp`와 cookie 수명을 설정합니다.
- `JWT_REFRESH_TOKEN_EXPIRES_MINUTES`는 DB `refresh_tokens.expires_at`과 Refresh Token cookie 수명을 설정합니다.
- dev/prod의 Access Token 기본값은 30분이고 Refresh Token 기본값 `20160`분은 14일입니다. 현재 local 기본값과 개인 `.env`는 만료 동작 확인을 위해 Access Token과 Refresh Token을 각각 30분으로 설정합니다.
- 만료시간 변경은 기존 token에 소급 적용되지 않으므로 backend 재시작 후 다시 로그인해 token을 재발급해야 합니다.

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

현재 matching WebSocket 계약:

- handshake endpoint는 `/ws`입니다.
- `access_token` HttpOnly cookie를 검증해 회원 ID 기반 `Principal`을 설정합니다.
- client는 본인의 `/user/queue/matching`만 구독할 수 있습니다.
- client `SEND` endpoint는 제공하지 않습니다.
- DB transaction에서는 내부 application event만 발행하고 실제 STOMP 알림은 `AFTER_COMMIT`에 전송합니다.
- payload는 상태 변경 이유와 발생 시각만 포함하며, client는 수신 후 REST로 최종 상태를 복원합니다.
- 단일 instance의 Spring simple broker를 사용하며 Redis나 외부 message broker를 추가하지 않습니다.

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

## Current group events 조회

- `GET /api/matching/groups/me/current/events`는 HttpOnly `access_token`의 회원을 기준으로 current active group event만 조회합니다.
- path/query/body에서 `memberId`, `groupId`를 받지 않으며 active group 부재는 `200 data:null`입니다.
- 최신 50건을 선택해 `created_at ASC, id ASC` 순서로 반환하고 raw JSON payload는 DTO에 포함하지 않습니다.
- actor는 event member가 같은 active group의 active member일 때만 `memberId`, `nickname`을 공개합니다.
- `ARRIVAL_TIME_SELECTED`는 허용된 `arrivalMinutes`만 파싱하며 malformed event는 해당 항목만 제외합니다.
- `MATCH_CONFIRMED` 저장은 group/member 확정과 같은 transaction이며 event insert 실패 시 확정도 rollback됩니다.
