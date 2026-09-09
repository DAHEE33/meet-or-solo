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
ADMIN_REPORT_CURSOR_HMAC_SECRET
KAKAO_CLIENT_ID
KAKAO_CLIENT_SECRET
NAVER_CLIENT_ID
NAVER_CLIENT_SECRET
TOUR_API_KEY
VAPID_PUBLIC_KEY
VAPID_PRIVATE_KEY
```

실제 값은 GitHub Secrets에만 저장하고 repository file에는 넣지 않습니다.

관리자 신고 목록의 opaque cursor는 `ADMIN_REPORT_CURSOR_HMAC_SECRET`을 전용
HMAC-SHA256 키로 사용합니다. JWT 서명 키와 목적을 분리하고 자동 재사용하지 않으며,
UTF-8 기준 32바이트 이상의 서로 다른 난수 Secret을 dev/prod 환경에 별도로 주입합니다.
기본값이나 예측 가능한 fallback은 두지 않고 누락·blank·짧은 값이면 Backend 시작을
실패시킵니다. 실제 값은 source, example, 문서와 로그에 기록하지 않습니다. 키를 회전하면
기존에 발급한 cursor가 무효화될 수 있으며 DB migration은 필요하지 않습니다.

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

임베딩 API 사업자인 OpenAI는 미국 소재이므로 `AI_PROCESSING`과 `OVERSEAS_TRANSFER` 두 동의를
모두 보유한 회원의 `preference_text`만 외부로 전송합니다. 하나라도 없으면
`AI_CONSENT_REQUIRED`로 거절하며 외부 호출 자체를 하지 않습니다.

국외 이전 고지에는 다음 항목을 화면에 표시합니다. 문구는
`frontend/src/components/consent/consentNotice.ts` 한 곳에서 관리합니다.

- 이전받는 자
- 이전되는 국가
- 이전하는 항목
- 이전 시점과 방법
- 이전 목적
- 보유·이용 기간
- 동의를 거부할 권리와 거부 시 불이익

동의 기록은 `member_consents`에 저장하며 `(member_id, consent_type, version)`이 UNIQUE입니다.
철회는 `agreed`를 `FALSE`로 바꾸지 않고 `revoked_at`만 기록합니다. "동의한 적이 있다"는 사실
자체가 감사 기록이고, `agreed = FALSE`는 "처음부터 거부"라는 다른 상태로 남겨둡니다. 철회 후
재동의는 새 row가 아니라 같은 row의 갱신입니다.

가입 시에는 `TERMS`와 `PRIVACY` 동의를 필수로 받고 기록합니다. 서버는 최초 가입 완료
(`PROFILE_REQUIRED` -> `ACTIVE`) 시점에 두 동의를 확인하고 없으면 `SIGNUP_CONSENT_REQUIRED`로
거절합니다. 기존 `ACTIVE` 회원의 프로필 수정에는 적용하지 않습니다. 동의 기록 구조가 생기기
전에 가입한 회원까지 소급해 막으면 프로필 수정 자체가 불가능해지기 때문입니다. 소급 동의
수집은 별도 작업으로 다룹니다.

현재 동의 여부 조회는 `version`을 보지 않습니다. 따라서 고지 문구를 개정해 `currentVersion`을
올려도 기존 동의자에게 재동의가 강제되지 않습니다. 재동의 강제가 필요해지면 조회 조건과
마이그레이션 방식을 함께 설계합니다.

처리 원칙:

- 필요한 동의가 없는 회원의 `preference_text`를 외부 API로 전송하지 않는다.
- `preference_text`가 없으면 임베딩 API를 호출하지 않는다.
- 취향 문장이 실제로 변경된 경우에만 임베딩을 다시 생성한다.
- 닉네임, 성별, 연령대, OAuth 식별자 등 불필요한 회원정보를 임베딩 API 요청에 포함하지 않는다.
- 임베딩 API Key는 Secret으로 관리하고 로그에 남기지 않는다.
- 임베딩 실패가 전체 매칭 실패로 이어지지 않도록 정형 태그 기반 fallback을 유지한다.
- 탈퇴 또는 취향 삭제 시 원문과 임베딩의 삭제·익명화 정책을 함께 적용한다.
- 원문 계속 보관 여부와 보관 기간은 개인정보처리방침 반영 전에 확정한다.

삭제 정책(확정):

- `AI_PROCESSING` 또는 `OVERSEAS_TRANSFER` 중 하나라도 철회하면 저장된
  `member_preference_embeddings` row를 같은 transaction에서 즉시 삭제한다. 두 동의가 모두
  있어야 전송이 허용되므로 하나만 철회해도 보관 근거가 사라진다.
- 원문 `preference_text`와 벡터는 같은 row에 있으므로 한 번의 삭제로 함께 지워진다. 원문만
  남기거나 벡터만 남기지 않는다.
- 사용자가 취향을 직접 삭제하는 경우에도 같은 row를 삭제한다. 동의 자체는 유지되므로 다시
  입력하면 별도 재동의 없이 저장할 수 있다.

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
- logout 시 Refresh Token 무효화 (구현 완료)
- 회원 탈퇴 시 개인정보 삭제 또는 익명화 (구현 완료)

cookie/header 전략은 인증 구현 단계에서 확정합니다.

### 로그아웃

`POST /api/auth/logout`이 로그아웃을 처리합니다.

- `access_token` cookie의 회원을 찾아 refresh token을 폐기합니다(`revokeByMemberId`).
- transaction commit 이후 `MemberLoggedOutEvent`로 해당 회원의 WebSocket session을 종료합니다.
  관리자 제재와 같은 순서입니다.
- `access_token`과 `refresh_token` cookie를 `Max-Age=0`으로 만료시킵니다. 발급 때와 동일한
  `Path=/`, `HttpOnly`, `Secure`, `SameSite=Lax` 속성을 유지해야 브라우저가 실제로 삭제합니다.
- 인증 여부와 무관하게 항상 `204`를 반환하는 멱등 endpoint입니다. 토큰이 없거나 만료·변조된
  경우 폐기만 생략하고 cookie 만료 헤더는 그대로 내려줍니다.
- 진행 중인 매칭 pool/proposal/group은 정리하지 않습니다. 로그아웃은 매칭 취소가 아니며,
  로그아웃으로 매칭을 종료시키면 penalty 회피 경로가 됩니다. 미응답은 기존 proposal timeout과
  penalty 정책이 처리합니다.

알려진 한계로, access token은 stateless JWT(기본 30분)이므로 서버가 강제로 무효화하지
않습니다. 브라우저는 cookie가 사라져 즉시 `401`이 되지만, 이미 유출된 raw token은 남은 만료
시간까지 유효합니다. 즉시 무효화가 필요해지면 회원별 `logout_at`(또는 token version) denylist를
`MemberAccessInterceptor`에서 검증하는 방식을 별도 단계로 검토합니다.

### 제재별 접근 허용 범위

정지와 영구정지의 허용 범위가 다르다(`docs/19` 4.8).

| 상태 | 로그인·token 갱신 | 조회 | 활동 |
| --- | --- | --- | --- |
| `SUSPENDED` | 허용 | 허용 | 차단 |
| `BANNED` | 차단 | 차단 | 차단 |
| `WITHDRAWN`·`DELETED` | 차단 | 차단 | 차단 |

- 판정은 `MemberAccessPolicy`의 `requireSignedIn`(로그인), `requireBrowsable`(조회),
  `requireAccessible`(활동)로 나뉜다.
- 어떤 요청이 활동인지는 `SuspendedActivityPolicy`의 차단 목록이 정한다. 등재되지 않은
  요청은 조회로 취급된다. 목록 누락은 `SuspendedActivityPolicyCoverageTest`가 상태 변경
  endpoint 전수 분류 검사로 막는다. **새 endpoint를 만들면 차단·허용 중 하나로 분류해야 한다.**
- 관리자 기능(`AdminAuthorizationService`)과 WebSocket 연결
  (`WebSocketAuthenticationInterceptor`)은 `requireAccessible`을 쓴다. 관리자 권한을 정지
  중에 유지할 이유가 없고, STOMP는 매칭 상태 동기화 전용이라 활동에 준한다.
- **자기 정보 관리는 정지 중에도 허용한다.** 프로필 수정·프로필 이미지·찜·취향 등록은 다른
  사용자와의 상호작용이 아니다. 단 프로필 수정이 `status`를 `ACTIVE`로 덮으면 제재가 조용히
  풀리므로, `Member.completeProfile`의 승격은 `PROFILE_REQUIRED`일 때만 일어난다.
  DB의 `chk_members_suspension_period`가 이 실수를 저장 단계에서 한 번 더 막는다.
- **정지 회원에게는 활동 화면 자체를 내주지 않는다.** `GET /api/members/me`가 제재 안내를
  함께 내려주고, 매칭·체크인 화면이 활동 UI 대신 안내를 보여준다. `403`을 받은 뒤에만
  알리면, 지난 완료 매칭 카드에서 새 매칭 신청 외에 빠져나갈 길이 없는 정지 회원이 화면에
  갇힌다.
- **신고 접수·상대 차단·동의 철회는 정지 중에도 허용한다.** 정지는 신고 권리를 박탈하는
  조치가 아니며, 만남 종료 후 14일(`MatchReportWindowPolicy`) 안에 정지되면 신고 경로가
  사라지는 문제가 생긴다. 개인정보 동의·철회도 제재로 막을 수 없다.

### 제재 안내 조회 token

제재로 접근이 막힌 사용자에게 사유·기간을 알리기 위한 단일 목적 token입니다
(`docs/19` 4.8).

경로가 두 갈래입니다. **영구정지는 로그인 자체가 막히므로** OAuth callback(302)이 이 cookie를
내려주고 로그인 화면이 조회해 안내합니다. **정지는 로그인 상태로 조회를 계속하므로** 활동
시도 시의 `403` body에 담긴 안내를 프론트엔드가 그 자리에서 dialog로 띄웁니다(화면을
로그인으로 이동시키지 않습니다). `403` 응답에도 같은 cookie가 함께 실리므로, 정지 회원이
어떤 이유로 로그인 화면에 도달해도 같은 안내를 읽을 수 있습니다.

- `typ: sanction_notice`, 만료 5분 고정. `JwtProvider`가 access/refresh와 같은 secret으로
  서명하지만 type이 달라 서로 교차 사용할 수 없습니다.
- `sanction_notice` cookie는 `HttpOnly`, `SameSite=Lax`, `Path=/api/auth/sanction-notice`로
  조회 endpoint 밖으로 전송되지 않게 좁혔습니다.
- **session이 아닙니다.** 이 token으로 부를 수 있는 것은 `GET /api/auth/sanction-notice`
  하나이고, 그 endpoint는 **현재 제재 중인 회원일 때만** 안내를 반환합니다. 유출되어도
  "그 회원이 제재 상태인지" 외에는 얻을 수 있는 것이 없습니다.
- 안내를 읽은 뒤 cookie를 `Max-Age=0`으로 즉시 만료시킵니다.
- 제재 사유·기간을 query parameter로 넘기지 않습니다. URL·nginx access log·브라우저
  history에 제재 정보가 남고, 누구나 URL을 위조해 안내 화면을 띄울 수 있습니다.
- 안내에 담는 값은 `status`, `suspendedUntil`, `reasonCode`, `reasonMessage`,
  `contactEmail`뿐입니다. 신고자 보호를 위해 제재 시작 시각(`suspendedAt`)과 관리자 자유 입력
  note(`admin_actions.reason`)는 담지 않습니다. 제재 시점은 신고 시점을 좁히는 단서입니다.

회원 탈퇴는 구현 완료입니다. 정책은
[관리자·회원·안전 로드맵](19_ADMIN_MEMBER_SAFETY_ROADMAP.md)의 4.4를 따릅니다.
refresh token 폐기와 session 종료는 로그아웃이 만든 `AuthService.revokeSession(memberId)`을
재사용합니다.

- 물리 삭제하지 않습니다. `members`를 참조하는 FK 31개가 전부 `ON DELETE RESTRICT`이고
  신고·제재 감사 이력이 탈퇴 회원을 참조합니다. 개인정보만 익명화하고 이력은 보존합니다.
- 익명화 누락은 `V28`의 `chk_members_withdrawn_anonymized`가 DB 수준에서 거부합니다.
- 동의(`member_consents`) row는 남기고 `revoked_at`만 기록합니다. "동의를 받았다"는 사실이
  개인정보 처리 근거의 증빙이므로 지우지 않고, 근거 종료만 남깁니다.
- 탈퇴 후 **7일간 같은 소셜 계정으로 재가입할 수 없습니다**(`MemberRejoinCooldownPolicy`).
  쿨오프가 지나면 계정을 되살려 프로필을 다시 입력받습니다.
- 재가입은 제재 면제 수단이 아닙니다. 정지 중 탈퇴한 회원은 잔여 정지 기간을 이어받습니다.
- 관리자 강제 탈퇴는 재가입을 영구 거부할 수 있습니다. 영구차단 회원은 로그인이 막혀 본인
  탈퇴 경로에 닿을 수 없으므로, 그 회원의 개인정보 삭제 요청은 고객센터를 통해 이 경로로
  처리합니다.
- `provider_user_id`는 익명화하지 않습니다. 지우면 재가입 쿨오프 판정 자체가 불가능합니다.

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

## 찜과 공개 댓글 보안 정책

설계 근거는 `docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md` 6장이다.

- 댓글은 비로그인 사용자를 포함해 전체 공개되는 콘텐츠다. 따라서 본문을 암호화하지 않는다.
  `member_reviews.comment_encrypted`와 반대인 이유는 그쪽이 비공개 상호 평가라서다.
- **댓글 작성자는 닉네임과 닉네임 이니셜 아바타로만 표시하고 프로필 이미지를 노출하지 않는다.**
  타인 프로필 이미지 노출 선례는 같은 매칭 그룹의 인증 회원 한정이며, 카카오·네이버 OAuth 프로필
  사진을 공개 웹에 노출하려면 별도 동의·정책 검토가 필요하다. 직접 업로드한 이미지는 private
  bucket 본인 전용 중계라 애초에 타인에게 쓸 수 없다.
- 공개 응답에 `memberId`, `profileImageUrl`, 이메일, OAuth 식별자, 회원 상태, penalty/cooldown,
  내부 댓글 `status`를 담지 않는다. 본인 여부는 `mine` boolean으로만 전달한다.
- 좋아요를 누른 사람이 누구인지 공개하지 않는다. 응답은 총 개수와 본인 여부만 담는다.
- 찜은 개인 데이터다. 공개 찜 수를 제공하지 않고 본인 조회만 허용하며, 목록 SQL은 인증 회원을
  `member_id`에 고정한다. 타인의 찜 목록을 지정하는 path·query·body 계약을 제공하지 않는다.
- 댓글 삭제는 인증 회원을 `member_id`에 고정한 조건부 update로만 수행한다. 없는 댓글과 이미
  삭제된 댓글을 같은 `204`로 처리해 존재 여부 추론을 막고, 타인 댓글은 `FORBIDDEN`으로 구분한다.
- 공개 조회 endpoint는 만료·무효 토큰에서도 예외를 던지지 않고 비로그인으로 취급한다. 이는
  인증 우회가 아니다 — 쓰기 endpoint는 여전히 유효한 토큰을 요구하고, 공개 조회는 비로그인에게도
  같은 데이터를 주기 때문에 노출되는 정보가 늘지 않는다.
- 관리자 숨김은 `AdminAuthorizationService.requireAdmin`을 service 첫 문장에서 통과해야 한다.
  관리자 여부는 서버가 판단해 `viewer.admin`으로만 내려주며 클라이언트 주장을 신뢰하지 않는다.
- 댓글 신고는 이번 범위에 없다. `reports`에 target 개념이 없어 확장하면 30일 유효 신고 누적 자동
  제재 파이프라인까지 영향을 주므로 별도 설계가 필요하다. 그동안의 모더레이션 수단은 작성자 삭제와
  관리자 숨김이다.
