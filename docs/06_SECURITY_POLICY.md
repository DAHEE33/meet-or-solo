# 보안 정책

## OAuth 계정 식별과 연결 정책

- 소셜 회원 식별 기준은 `(provider, provider_user_id)`이다.
- 동일 이메일이어도 Kakao/Naver 회원을 별도 생성하며 자동 병합하지 않는다.
- MVP에서는 계정 연결을 제공하지 않는다. 추후 `member_social_accounts`와 명시적 재인증 기반 연결을 검토한다.
- OAuth state는 HttpOnly, `SameSite=Lax`, 환경별 Secure 정책의 짧은 수명 쿠키로 검증하고 일회 사용 후 삭제한다.

## 핵심 원칙

- Secret을 source control에 넣지 않는다.
- 운영 서비스는 Nginx 뒤에 둔다.
- 개인정보 수집을 최소화한다.
- 원본 GPS 좌표는 필요한 순간에만 사용하고 장기 저장하지 않는다.
- PostgreSQL은 공개 인터넷에 직접 노출하지 않는다.
- 관리자 기능은 처음부터 권한이 필요한 기능으로 설계한다.

## HTTPS와 Nginx

운영 트래픽은 HTTPS/TLS를 사용합니다.

Nginx는 공개 진입점입니다.

```text
443 / HTTPS -> Nginx
Nginx /api -> backend:8080
Nginx /ws  -> backend:8080 WebSocket endpoint
Nginx /    -> frontend static dist
```

인증서는 Let's Encrypt와 Certbot 사용을 우선합니다.

## 공개 포트 정책

공개 허용:

- `80`: redirect 및 certificate challenge
- `443`: HTTPS application traffic
- `22`: SSH. 가능하면 접근 제한

외부 직접 노출 금지:

- PostgreSQL `5432`
- backend `8080`
- 추후 Redis 도입 시 Redis port

## Secret 처리

하드코딩 금지:

- API Key
- DB password
- OAuth client secret
- SSH Key
- 실제 server IP
- 실제 domain
- GitHub Secrets 값

문서와 예시 설정에서는 placeholder를 사용합니다.

```text
YOUR_DOMAIN
YOUR_SERVER_IP
YOUR_SSH_USER
YOUR_SECRET_NAME
```

## GitHub Secrets

GitHub 원격 저장소는 아직 미연결 상태입니다. Actions와 배포 관련 값은 모두 placeholder로 유지합니다.

추후 필요한 Secrets 이름:

```text
SERVER_HOST
SERVER_USER
SERVER_SSH_KEY
SERVER_PORT
APP_DOMAIN
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
KAKAO_CLIENT_ID
KAKAO_CLIENT_SECRET
NAVER_CLIENT_ID
NAVER_CLIENT_SECRET
TOUR_API_KEY
VAPID_PUBLIC_KEY
VAPID_PRIVATE_KEY
```

실제 값은 GitHub Secrets에만 저장하고 repository file에는 넣지 않습니다.

## 개인정보

예상 개인정보:

- OAuth provider ID
- 닉네임
- 연령대
- 성별 선택값
- 프로필 이미지 URL
- 자연어 여행 취향 문장
- 자연어 여행 취향의 임베딩 벡터
- 매칭 이력
- 신고 이력
- 매너온도

민감하거나 준민감한 필드는 필요 시 암호화합니다.

암호화 방향:

- 선택된 민감 DB 필드에 AES-256-GCM 적용
- 암호화 key는 환경변수 또는 Secret manager에서 제공
- 암호화 key는 저장소에 커밋하지 않음

## AI 임베딩과 국외 이전

`preference_text`를 외부 임베딩 API로 전송하기 전에 개인정보 처리방침, 이용 화면 고지, 동의 문구를 확정합니다.

동의 유형은 다음을 구분합니다.

```text
AI_PROCESSING
OVERSEAS_TRANSFER
```

AI 처리와 국외 이전은 법적 성격과 거부 선택이 다를 수 있으므로 하나의 동의로 합치지 않습니다. 실제 동의 요건과 고지 문구는 서비스 출시 전 법률 또는 개인정보 담당 검토를 거칩니다.

처리 원칙:

- 필요한 동의가 없는 회원의 `preference_text`를 외부 API로 전송하지 않는다.
- `preference_text`가 없으면 임베딩 API를 호출하지 않는다.
- 취향 문장이 실제로 변경된 경우에만 임베딩을 다시 생성한다.
- 닉네임, 성별, 연령대, OAuth 식별자 등 불필요한 회원정보를 임베딩 API 요청에 포함하지 않는다.
- 임베딩 API Key는 Secret으로 관리하고 로그에 남기지 않는다.
- 임베딩 실패가 전체 매칭 실패로 이어지지 않도록 정형 태그 기반 fallback을 유지한다.
- 탈퇴 또는 취향 삭제 시 원문과 임베딩의 삭제·익명화 정책을 함께 적용한다.
- 원문 계속 보관 여부와 보관 기간은 개인정보처리방침 반영 전에 확정한다.

## GPS와 위치정보

GPS는 축제 체크인과 확정된 만남 포인트의 도착 검증에 사용합니다.

원칙:

- 원본 좌표는 즉시 검증에만 사용한다.
- 기본적으로 원본 GPS 좌표를 저장하지 않는다.
- 매칭과 감사에 필요한 체크인 성공 metadata만 저장한다.
- 위치기반서비스사업 신고 완료와 위치정보 약관·명시적 동의 적용 후 도착 확인에
  필요한 원본 좌표, 정확도와 측정 시각만 서버로 전송한다.
- 서버는 group snapshot 좌표와의 거리를 일회성으로 계산하고 사용자 원본
  좌표를 DB에 저장하지 않으며 요청 처리 목적 달성 후 즉시 폐기한다.
- 클라이언트가 계산한 거리와 `verified` 값은 받거나 신뢰하지 않는다.
- GPS 좌표를 URL query, application/access log, error detail,
  `match_events.payload`와 WebSocket payload에 포함하지 않는다.
- 브라우저 위치 권한은 위치정보 이용에 관한 고지·동의를 대신하지 않는다.
- 공개 서비스 전 위치기반서비스사업 신고 완료, 위치기반서비스 이용약관,
  개인정보처리방침과 동의 철회 절차 반영을 확인한다.
- 추후 위치 저장 기능이 필요하면 정책 문서를 먼저 갱신하고 별도 승인을 받는다.

브라우저 Geolocation API 사용 자체에는 별도 API 신청과 API Key가 필요하지
않습니다. 다만 공개 서비스의 위치정보 이용과 위치기반서비스사업 신고 여부는
기술 API 신청과 별개입니다. 소상공인·1인 창조기업 특례를 포함한 실제 신고
의무와 시점은 서비스 주체와 공개 범위를 확정한 뒤 관할 기관에 최종
확인합니다.

## CORS

CORS는 profile별로 분리합니다.

`local`:

- 로컬 frontend dev server 허용

`prod`:

- 운영 domain만 허용
- credential을 사용하는 경우 wildcard origin 금지

## Token

예정 token 정책:

- Access Token: 짧은 만료 시간의 JWT
- Refresh Token: DB 저장
- Refresh Token 만료와 폐기 지원
- logout 시 Refresh Token 무효화
- 회원 탈퇴 시 개인정보 삭제 또는 익명화

cookie/header 전략은 인증 구현 단계에서 확정합니다.

## WebSocket 인증과 권한

- `/ws` handshake에서 `access_token` HttpOnly cookie의 서명, token 유형과 만료를 검증합니다.
- 인증된 회원 ID를 WebSocket `Principal` 이름으로 사용하며 client가 member ID를 전달하지 않습니다.
- client 구독은 `/user/queue/matching`만 허용하고 임의 회원, attempt, group topic 구독을 허용하지 않습니다.
- client STOMP `SEND`는 거절하며 자유 채팅 또는 상태 변경 command endpoint를 제공하지 않습니다.

## MatchRoomPage 조회 인가

- 도착 완료 API도 `memberId`와 `groupId`를 받지 않고 인증 회원 본인만 변경합니다.
- `MEMBER_ARRIVED` payload에는 위치, token과 개인정보를 저장하지 않습니다.

- `/match-room`은 URL에 `groupId`를 포함하지 않습니다.
- `GET /api/matching/groups/me/current`는 `access_token` HttpOnly cookie의 회원만 기준으로 조회합니다.
- 다른 회원 또는 임의 group을 지정하는 path, query, body 계약을 제공하지 않습니다.
- festival은 제목, 주소, 행사 기간만 공개하고 member는 ID, nickname, 공개 profile image, 참여 상태만 공개합니다.
- 이메일, OAuth 식별자, GPS, 성별, 연령대, penalty/cooldown과 private object key는 반환하지 않습니다.
- 도착 예정 시간 request는 `memberId`와 `groupId`를 받지 않고 인증 회원 본인만 변경합니다.
- `match_events.payload`에는 `arrivalMinutes`만 저장하고 token, GPS, 이메일, OAuth 식별자를 저장하지 않습니다.
- local/dev/prod의 handshake origin은 기존 `CORS_ALLOWED_ORIGINS` 경계를 재사용합니다.
- 알림에는 token, GPS, 이메일, OAuth 식별자와 다른 회원의 개인정보를 포함하지 않습니다.

## MatchRoom 신고 인가와 정보 최소화

- `POST /api/match-groups/{groupId}/reports`의 신고자는 request가 아니라 HttpOnly
  `access_token`에서 얻은 회원 ID로만 결정한다. client가 `reporterMemberId`를
  추가해도 저장 기준으로 사용하지 않는다.
- group row를 transaction에서 잠근 뒤 신고자와 피신고자의
  `match_group_members(group_id, member_id)` 전체 참여 이력을 모두 확인한다.
- group이 없거나 신고자가 참여하지 않았거나 피신고자가 참여하지 않은 경우 같은
  `REPORT_RESOURCE_NOT_FOUND`를 반환해 임의 group ID와 회원 ID 탐색을 막는다.
- 응답은 report ID, group ID, 피신고자 ID, 구조화 사유, 상태와 생성 시각만 포함한다.
  reporter ID, 회원 프로필, `detail_encrypted`와 내부 암호화 필드는 반환하지 않는다.
- 신고 접수 transaction은 report 이외의 회원·매칭 상태를 변경하지 않고
  WebSocket/application event도 발행하지 않아 피신고자에게 신고 사실과 신고자
  신원을 노출하지 않는다.
- 자유 입력 상세는 1차 API에서 받지 않으며, 관리자 조회·처리 API를 구현할 때
  `detail_encrypted` 접근 권한과 audit 정책을 별도로 확정한다.

## 관리자 보안

관리자 endpoint는 명시적 admin role이 필요합니다.

축제 만남 장소 관리 API는 access token의 회원 ID로 `members.role`을 다시 조회해
`ADMIN`인지 확인합니다. 인증 누락은 `401`, 일반 회원은 `403`이며 실제 장소 데이터와
API Key를 코드에 하드코딩하지 않습니다.

관리자 조치 로그 대상:

- 신고 처리
- 차단 API는 JWT cookie의 인증 회원만 blocker로 사용하고 request/response에 blocker ID를
  포함하지 않는다. group과 양쪽 참여 이력 중 하나라도 확인되지 않으면 같은 404를
  반환한다.
- 차단 응답은 block ID, blocked member ID, 생성 시각만 포함한다. 내부 reason, 회원
  개인정보, 양방향 매칭 제외 구현 상세는 반환하지 않는다.
- 동일 pair 요청은 DB UNIQUE와 `ON CONFLICT DO NOTHING`으로 멱등 처리하며 충돌 후 기존
  row를 조회한다. update/upsert 갱신으로 기존 생성 시각이나 내부 값을 초기화하지 않는다.
- 차단 transaction은 상대 알림, WebSocket/event, penalty/cooldown과 회원 점수 변경을
  수행하지 않는다.
- 회원 제재
- 수동 penalty
- blacklist 변경
- 데이터 보정

## API 남용 방지와 Rate Limiting

MVP 1단계에서는 운영 수준 Rate Limiting을 구현하지 않습니다.

MVP 초기 방향:

- 단일 instance in-memory limiter

추후 확장:

- Redis 기반 distributed rate limiting

## 로그 규칙

로그에 남기면 안 되는 값:

- password
- token
- OAuth secret
- private key
- 원본 GPS 좌표
- 불필요한 개인정보

운영 로그는 문제 해결에 필요한 정보를 제공하되 사용자 정보를 과도하게 노출하지 않아야 합니다.

## Private 프로필 이미지

- OCI bucket은 Private으로 유지하고 frontend에 OCI 자격 증명이나 직접 object URL을 제공하지 않습니다.
- 업로드 API는 인증된 본인에게만 허용하며 허용 MIME 타입, 파일 시그니처, 파일 크기를 검증합니다.
- 조회 API도 인증된 본인의 `profile_image_object_key`만 사용하고 요청에서 임의 object key를 받지 않습니다.
- 응답은 `X-Content-Type-Options: nosniff`, `Cache-Control: private, no-store`를 사용합니다.
- OCI Customer Secret Key와 endpoint의 실제 namespace는 코드, 문서, example 파일에 기록하지 않습니다.

## MatchRoomPage event 공개 경계

- current group events API는 HttpOnly `access_token`의 인증 회원과 current active group으로 인가합니다.
- 임의 `memberId`, `groupId` 조회 경로를 제공하지 않습니다.
- raw payload, GPS, 이메일, OAuth 식별자, token, penalty/cooldown과 Secret은 반환하지 않습니다.
- actor의 ID/nickname은 같은 active group의 active member 관계가 query에서 확인된 경우에만 공개합니다.
- malformed payload 원문을 응답이나 로그에 기록하지 않고 해당 event만 안전하게 제외합니다.
## 차단 목록 IDOR 방어

- 차단 목록의 `blockerMemberId`는 request body/query/path에서 받지 않고 JWT cookie의
  `access_token`에서만 결정한다.
- 조회와 삭제 SQL 모두 인증 회원을 `blocker_member_id`에 고정한다. 삭제 SQL은
  `blocker_member_id`와 `blocked_member_id`를 함께 조건으로 사용해 타인·역방향 row를 보호한다.
- 역방향 차단 여부, 다른 회원의 관계, `user_blocks.id`, reason과 삭제 row count는 외부에
  노출하지 않는다. 없는 row도 같은 `204`로 처리해 존재 여부 추론을 막는다.
- 해제는 정규화 member-pair advisory transaction lock 뒤 정방향 row만 물리 삭제한다.
  MVP는 차단 감사 이력을 별도로 저장하지 않으며 상대 알림, 현재 MatchRoom 변경,
  penalty/cooldown/event와 회원 점수 변경을 수행하지 않는다.
- 해제로 상대가 후보로 복귀할 수 있다는 사실은 해제한 본인에게만 안내한다. 역방향 차단이
  남아 있는지 또는 상대가 나를 차단했는지는 목록·DELETE 응답으로 구분할 수 없다.
