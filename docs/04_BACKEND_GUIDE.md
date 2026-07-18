# 백엔드 가이드

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

## 한국관광공사 TourAPI Client

풀스택 A의 관광 API 연동은 `external/tourapi`에 둡니다. 현재 첫 오퍼레이션은 국문 `KorService2`의 `searchFestival2`입니다.

```text
external/tourapi
├─ client      # 외부 호출 인터페이스와 RestClient 구현
├─ config      # 설정값과 전용 RestClient bean
├─ dto         # 관광공사 요청·응답 계약
├─ exception   # 외부 연동 기술 예외와 오류 분류
├─ log         # 실제 API 호출 시도 이력 저장
└─ support     # JSON/XML 응답 해석
```

- HTTP client는 기존 Spring MVC 의존성의 `RestClient`를 사용합니다.
- 공통 파라미터인 `serviceKey`, `MobileOS`, `MobileApp`, `_type=json`은 client가 추가합니다.
- local에서는 루트 `.env`의 기존 `TOURISM-API-KEY`를 지원하고, 표준 환경변수는 `TOUR_API_KEY`를 사용합니다.
- 서비스키는 URI에 한 번만 인코딩하며 로그, 예외 메시지, `tour_api_call_logs`에 저장하지 않습니다.
- 성공 JSON의 `resultCode=0000`을 확인하고, 공공데이터포털이 HTTP 200으로 반환할 수 있는 XML 오류 응답도 구분합니다.
- `searchFestival2`의 실패를 빈 목록으로 바꾸지 않습니다. 정상 0건만 빈 목록으로 반환하고, 외부 API 실패는 `TourApiClientException`으로 전달합니다.
- 기술 오류의 기본 메시지는 `TourApiErrorType`에 모으고, 원격 오류 코드와 HTTP status는 `TourApiClientException`의 별도 필드로 보존합니다.
- `TourApiClientException`은 client 경계의 기술 예외입니다. 축제 service가 캐시 fallback, 재시도 또는 `BusinessException` 변환 여부를 결정합니다.
- 공통 client는 한 페이지 조회만 담당합니다. 전체 페이지 순회, DB upsert, 마지막 정상 데이터 유지와 Scheduler는 축제 domain이 담당합니다.
- 공통 client는 물리적인 호출 시도마다 `tour_api_call_logs`를 별도 transaction으로 저장합니다. 로그 저장 실패는 원래 API 성공/실패 결과를 덮어쓰지 않습니다.
- 호출 이력에는 operation, 안전한 요청 식별값, HTTP status, 성공 여부, 응답 시간, 결과 건수, 오류 분류만 기록하며 API Key, 전체 URL, 응답 본문, 원본 예외 메시지는 기록하지 않습니다.
- 강원도 축제 조회 시 법정동 시도 코드 `51`과 분류 `EV/EV01`을 사용하되, 해당 필터는 공통 client가 아니라 호출하는 service가 결정합니다.

실제 API smoke test는 기본 test 실행에서 제외하고 `TOUR_API_LIVE_TEST=true`일 때만 실행합니다.

## 축제 데이터 동기화

축제 화면 API는 관광공사 API를 사용자 요청마다 호출하지 않고 PostgreSQL의 `festivals`를 조회하는 방향으로 구성합니다. 관광공사 API 호출은 `FestivalSyncScheduler`가 `FestivalSyncService`를 통해 수행합니다.

```text
FestivalSyncScheduler
  → FestivalSyncService
  → TourApiClient.searchFestivals
  → FestivalSyncMapper
  → FestivalSyncWriter
  → FestivalRepository / FestivalImageRepository
  → PostgreSQL festivals / festival_images
```

- `FestivalSyncService`는 설정된 기간의 `searchFestival2` 전체 페이지를 먼저 메모리에 수집합니다.
- 한 페이지라도 실패하거나 페이지 계약이 불완전하면 `FestivalSyncWriter`를 호출하지 않습니다.
- 전체 페이지 수신 후에만 `FestivalSyncWriter`의 단일 transaction으로 `content_id` 기준 upsert합니다.
- `firstimage`, `firstimage2`는 축제별 `display_order=0` 대표 이미지로 저장·갱신합니다. 이미지 URL은 HTTP/HTTPS만 허용하며 응답에서 이미지가 누락되면 마지막 정상 이미지를 유지합니다.
- 성공한 동기화 transaction에서는 KST 오늘보다 `event_end_date`가 지난 `ACTIVE/INACTIVE` 축제를 `ENDED`로 일괄 정리합니다. 종료 당일은 `ACTIVE`로 유지하고 운영자가 숨긴 `HIDDEN`은 변경하지 않습니다.
- 동기화 실패 시 기존 `festivals` row를 삭제하거나 변경하지 않습니다. 기존 row가 있으면 `STALE_DATA`, 하나도 없으면 `NO_DATA` 상태로 Scheduler 로그에 기록하고 다음 주기에 다시 시도합니다.
- Scheduler는 `fixedDelay`를 사용하므로 한 인스턴스 안에서 이전 실행이 끝난 뒤 다음 실행 시간을 계산합니다.
- Scheduler 소유권은 `local=false`, `dev=true`, `prod=false`입니다. local과 dev가 같은 DB를 사용하더라도 자동 동기화는 dev backend 한 인스턴스만 수행합니다.
- 네트워크 오류, HTTP 5xx, 429는 페이지별로 최대 3회(최초 호출 포함) 재시도합니다. 기본 지연은 1초, 2초의 지수 증가이며 최대 10초로 제한합니다.
- 인증/권한, 그 외 HTTP 4xx, 설정 오류, 잘못된 응답은 재시도하지 않습니다. 재시도를 모두 소진하면 전체 동기화를 실패 처리하여 기존 DB 데이터를 유지합니다.
- 기본 조회 조건은 KST 오늘 기준 이전 30일부터 이후 365일까지, 강원 법정동 시도 코드 `51`, 축제 분류 `EV/EV01`, 페이지 크기 100입니다.
- API가 정상 0건을 반환한 경우 기존 데이터를 삭제하지 않습니다. 최초 실행도 0건이면 DB는 빈 상태로 유지됩니다.
- API가 정상 0건이어도 기존 데이터의 종료 상태 정리는 수행합니다.

## 축제 목록 조회 API

`GET /api/festivals?page=0&size=20`은 사용자 요청마다 관광공사 API를 호출하지 않고 PostgreSQL 캐시만 조회합니다.

- 공개 API이며 현재 Security 설정에서 별도 인증을 요구하지 않습니다.
- `status=ACTIVE`이고 `event_end_date`가 null이거나 KST 오늘 이상인 축제만 조회합니다.
- `event_start_date`, `id` 오름차순으로 고정 정렬합니다.
- `page`는 0 이상, `size`는 1~100으로 검증합니다.
- 목록과 대표 이미지는 두 번의 일괄 query로 조회하여 축제별 이미지 N+1 query를 만들지 않습니다.
- 응답은 공통 `ApiResponse<FestivalListResponse>` 형식이며 목록 항목에는 `id`, `contentId`, 제목, 주소, 지역 코드, 행사 기간, 상태, 원본/썸네일 URL을 포함합니다.
- 종료 상태 Scheduler 반영이 지연되더라도 조회 query에서 종료일을 한 번 더 확인하여 지난 축제를 노출하지 않습니다.

주요 환경변수:

```text
FESTIVAL_SYNC_ENABLED
FESTIVAL_SYNC_INITIAL_DELAY
FESTIVAL_SYNC_FIXED_DELAY
FESTIVAL_SYNC_PAGE_SIZE
FESTIVAL_SYNC_MAX_PAGES
FESTIVAL_SYNC_LOOKBACK_DAYS
FESTIVAL_SYNC_LOOKAHEAD_DAYS
FESTIVAL_SYNC_REGION_CODE
FESTIVAL_SYNC_CLASSIFICATION_SYSTEM_1
FESTIVAL_SYNC_CLASSIFICATION_SYSTEM_2
FESTIVAL_SYNC_RETRY_MAX_ATTEMPTS
FESTIVAL_SYNC_RETRY_INITIAL_DELAY
FESTIVAL_SYNC_RETRY_MAX_DELAY
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
