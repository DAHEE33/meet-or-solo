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

GPS는 축제 체크인 검증에 사용합니다.

원칙:

- 원본 좌표는 즉시 검증에만 사용한다.
- 기본적으로 원본 GPS 좌표를 저장하지 않는다.
- 매칭과 감사에 필요한 체크인 성공 metadata만 저장한다.
- 추후 위치 저장 기능이 필요하면 정책 문서를 먼저 갱신하고 별도 승인을 받는다.

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

## 관리자 보안

관리자 endpoint는 명시적 admin role이 필요합니다.

관리자 조치 로그 대상:

- 신고 처리
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
