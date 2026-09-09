# 통합테스트 계획과 완료 기준

WBS 10-B까지 구현된 기능을 dev 환경에서 처음부터 끝까지 검증하고, **공모전 제출 가능한 상태**로
만들기 위한 단일 실행 문서다.

진행 순서는 이 문서의 목차 순서와 같다.

| 부 | 내용 | 끝나면 |
| --- | --- | --- |
| 1부 | 사전 조건 — 지금 상태로는 테스트가 불가능한 항목 해소 | 시나리오를 실행할 수 있는 환경 |
| 2부 | 인프라 점검 | 서버·DB·배포가 정상임을 확인 |
| 3부 | 화면별 체크리스트 (라우트 19개) | 기능 결함 목록 확보 |
| 4부 | 화면 간 종단 흐름·동시성 | 흐름 연결 확인 |
| 5부 | 재배포 후 스모크 | 수정본 반영 확인 |
| 6부 | 제출 전 준비 | 제출 가능 상태 |

## 완료 판정 (Definition of Done)

완료는 **세 단계로 나눠 판정**한다. 하나로 뭉치면 "개발서버에서는 됐는데 실기기에서 안 되는"
상태가 완료로 기록된다.

| 단계 | 완료 기준 |
| --- | --- |
| **1단계. 개발서버 검증 완료** | 1부 차단 항목 전부 해소 · 2부 인프라 전 항목 확인 · 3부 **`[B]` 항목 전부 `PASS`** · 4부 X-1~X-8 전부 `PASS` · **미실행 0건, `BLOCKED` 0건** |
| **2단계. 실기기 PWA 검증 완료** | 6.1의 W-1~W-12 전부 `PASS` (Android·iOS 각 1대 이상) |
| **3단계. 배포·제출 완료** | 검증한 커밋이 배포됨 · 5부 스모크 `PASS` · **복구 절차 확인 완료** · 6.2 시연 준비 완료 |

### 판정 규칙

- **`[B]` = 제출 차단(Blocker).** 하나라도 `FAIL`이면 그 단계는 완료가 아니다. 화면 단위가 아니라
  **항목 단위**로 붙인다. 화면 전체를 `P1`로 묶어 실패를 이월하면 "로그아웃 후 접근 차단"처럼
  중요한 결함이 함께 넘어간다.
- **`[ ]` = 일반 항목.** `FAIL`이어도 이슈 등록 후 다음 단계로 진행할 수 있다.
- **미실행 0건.** 체크하지 않은 항목이 남아 있으면 완료가 아니다. `[B]`가 아니어도 판정을 남긴다.
- **`BLOCKED` 0건.** 환경 문제를 해소하고 다시 실행한다. 미해소 상태로는 어떤 단계도 완료가 아니다.
- **`N/A`는 제외 사유를 반드시 기록한다.** 사유 없는 `N/A`는 미실행으로 본다.

### 검증 증거 (7부 기록표에 필수)

판정만 남기면 어떤 버전을 검증했는지 알 수 없다. 각 회차마다 다음을 기록한다.

`테스트 일시` · `담당자` · `배포 커밋 해시` · `기기/OS/브라우저` · `실제 결과` · `이슈 링크`

---

# 1부. 사전 조건

코드를 읽고 확인한, **지금 상태로 dev에 올리면 시나리오 자체가 실행되지 않는** 항목이다.
여기부터 정리하지 않으면 대부분의 실패가 "구현 문제"가 아니라 "환경 문제"가 된다.

## 1.1 차단 항목

| # | 항목 | 현재 상태 | 영향 |
| --- | --- | --- | --- |
| 1 | **매칭 Scheduler 3종이 dev에서만 꺼져 있음** | 로컬은 정상. `application-local.yml`이 루트 `.env`를 읽어 켜진다. dev 컨테이너는 `.env`를 읽지 않고 `docker-compose.dev.yml`의 `environment:`만 보는데 거기에 `MATCHING_*`이 없어 기본값 `false` 적용 | 제안 30초 타임아웃·노쇼·배치 매칭 미기동. **제안이 영원히 timeout되지 않아 회원이 `PROPOSED`로 잠긴다** |
| 2 | **dev 서버가 HTTP** | TLS 없음, `AUTH_COOKIE_SECURE=false` | 브라우저가 insecure origin에서 **`navigator.geolocation` 차단 → 체크인 불가**, **Service Worker 등록 차단 → PWA 검증 불가** |
| 3 | **nginx `client_max_body_size` 미설정** | 기본 1MB | backend는 프로필 이미지 5MB 허용인데 nginx가 1MB 초과를 413 HTML로 거절 |
| 4 | **frontend build에 Kakao Maps key 미주입** | `deploy-dev.yml`에 없었음 | 만남 지점 지도, 관리자 좌표 선택 미표시 |
| 5 | **CI가 테스트를 돌리지 않음** | `build -x test`, frontend `npm test` 없음 | 통테 중 회귀를 PR에서 못 잡음 |
| 6 | **`TOUR_API_KEY` 이름 불일치** | 로컬 `.env`는 `TOURISM-API-KEY`(Spring이 직접 읽어 fallback 동작). dev는 compose가 `TOUR_API_KEY`만 전달 | 서버 `.env`에 `TOUR_API_KEY`가 없으면 축제 sync 인증 실패 |
| 7 | **dev 체크인 GPS 검증 기본 우회** | `FESTIVAL_CHECKIN_BYPASS_RADIUS_CHECK` dev 기본 `true` | 의도된 설정이나 **반경 검증 자체가 검증 대상에서 빠진다** |

### 1.1.1 항목 1을 왜 먼저 해야 하는가

매칭은 현재 **pool 진입 시점의 즉시 매칭**만 동작하고, pool 탐색 창은 **60초** 고정이다
(`MatchPoolEntryService.enter`의 `now.plusSeconds(60)`).

Scheduler가 꺼져 있으면:

- 60초 안에 상대가 안 오면 만료되고 **재시도 경로가 없다**
- 매칭 후 한 명이 무응답이면 **영원히 대기**. 그 회원은 새 매칭도 못 건다
- 라운드 1/2 penalty·cooldown 정책을 **전혀 검증할 수 없다**
- 도착 30분 창 경과 후 노쇼 처리가 동작하지 않는다

## 1.2 저장소 수정 (완료)

| 파일 | 변경 |
| --- | --- |
| `infra/docker/docker-compose.dev.yml` | `MATCHING_*`, `OPENAI_API_KEY`, `SUPPORT_CONTACT_EMAIL` 등 backend에 전달 |
| `infra/env/.env.dev.example` | 같은 키 추가 |
| `.github/workflows/deploy-dev.yml` | 프론트 빌드 step에 `VITE_KAKAO_MAPS_APP_KEY` 주입 |

> `VITE_` 변수는 **빌드 시점에 번들로 인라인**된다. 서버 `.env`에 넣어도 반영되지 않는다.
> 빌드가 GitHub Actions에서 일어나므로 **GitHub Secrets**가 유일한 주입 경로다.
> 로컬은 `frontend/.env.local`이 그 역할을 한다.

**남은 수동 작업**

- [ ] GitHub Secrets에 `VITE_KAKAO_MAPS_APP_KEY` 등록
- [ ] 서버 `.env`에 `TOUR_API_KEY`, `OPENAI_API_KEY`, `SUPPORT_CONTACT_EMAIL` 추가
- [ ] 위 3개 파일을 `dev`에 병합해 재배포

## 1.3 서버 `.env` 검증

**서버 `.env`는 로컬 `.env` 복사가 아니라 `infra/env/.env.dev.example` 기준으로 만든다.**
로컬에는 `DB_URL`·`DB_USERNAME`·`DB_PASSWORD`·`TOUR_API_KEY`가 없고, 그 값들이 없으면
backend가 기동조차 하지 못한다.

```bash
cd <DEV_DEPLOY_PATH>
for k in SPRING_PROFILES_ACTIVE DB_URL DB_USERNAME DB_PASSWORD \
         POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD \
         JWT_SECRET ADMIN_REPORT_CURSOR_HMAC_SECRET PROFILE_ENCRYPTION_KEY \
         TOUR_API_KEY FRONTEND_BASE_URL CORS_ALLOWED_ORIGINS \
         KAKAO_CLIENT_ID KAKAO_CLIENT_SECRET KAKAO_REDIRECT_URI \
         NAVER_CLIENT_ID NAVER_CLIENT_SECRET NAVER_REDIRECT_URI \
         OCI_OBJECT_STORAGE_ENDPOINT SERVER_PORT OPENAI_API_KEY; do
  grep -q "^$k=" .env || echo "MISSING: $k"
done
grep "^SPRING_PROFILES_ACTIVE=" .env      # dev 여야 한다
```

**더 확실한 검증** — compose가 실제로 해석한 결과에 WARN이 하나도 없어야 한다.

```bash
docker compose --env-file .env -f infra/docker/docker-compose.dev.yml ps
```

> `--env-file .env`를 반드시 붙인다. `-f`로 다른 디렉터리의 compose를 지정하면 프로젝트
> 디렉터리가 `infra/docker/`가 되어 `.env`를 못 찾는다. **`--env-file` 없이 `up -d`를
> 실행하면 모든 환경변수가 빈 값인 컨테이너가 만들어져 backend가 죽는다.**

## 1.4 HTTPS·도메인 전환

차단 항목 2 해소. 이걸 해야 GPS·PWA 검증이 가능하고, 제출 가능한 주소가 생긴다.
Let's Encrypt는 IP에 발급되지 않으므로 **도메인 확보가 선행**이다. 아래에서 도메인은 `example.kr`로 표기한다.

**① DNS**

```bash
dig +short example.kr        # 서버 공인 IP가 나와야 한다
```

**② 포트 개방 — Oracle Cloud는 두 군데 모두 열어야 한다**

1. 콘솔 → VCN → Security List → Ingress: TCP 80, TCP 443
2. VM 방화벽:

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

**③ compose 포트 변경** — `18080:80` → `80:80`, `443:443`, 그리고 nginx 볼륨에 인증서 추가

```yaml
      - /etc/letsencrypt:/etc/letsencrypt:ro
```

**④ 인증서 발급**

```bash
sudo apt-get install -y certbot
docker compose -f infra/docker/docker-compose.dev.yml stop nginx
sudo certbot certonly --standalone -d example.kr --agree-tos -m <이메일> --no-eff-email
sudo ls /etc/letsencrypt/live/example.kr/     # fullchain.pem, privkey.pem
```

실패 시: `Timeout during connect` → 포트 미개방(②), `NXDOMAIN` → DNS 미전파(①),
`Problem binding to port 80` → nginx를 안 내림.

**⑤ nginx TLS 설정** — `infra/nginx/default.dev.conf`

```nginx
server {
    listen 80;
    server_name example.kr;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    http2 on;
    server_name example.kr;

    ssl_certificate     /etc/letsencrypt/live/example.kr/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/example.kr/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;

    root /usr/share/nginx/html;
    index index.html;

    # 차단 항목 3. 기본값 1MB면 프로필 이미지 업로드가 413으로 잘린다.
    client_max_body_size 6m;

    location /api/ {
        proxy_pass http://backend:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws {
        proxy_pass http://backend:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

**⑥ 서버 `.env` 동시 변경** — 하나라도 빠지면 로그인이 깨진다

| 키 | 변경 후 |
| --- | --- |
| `FRONTEND_BASE_URL` | `https://example.kr` |
| `CORS_ALLOWED_ORIGINS` | `https://example.kr` |
| `AUTH_COOKIE_SECURE` | **`true`** (HTTPS 전환과 **동시에**. HTTP에서 켜면 로그인 즉시 깨짐) |
| `KAKAO_REDIRECT_URI` | `https://example.kr/api/auth/kakao/callback` |
| `NAVER_REDIRECT_URI` | `https://example.kr/api/auth/naver/callback` |

**⑦ 외부 콘솔 재등록** — Kakao 콘솔에는 성격이 다른 설정이 두 군데 있다

| 위치 | 용도 |
| --- | --- |
| 제품 설정 → 카카오 로그인 → **Redirect URI** | OAuth 콜백. 문자 단위로 정확히 일치해야 함 |
| 앱 설정 → 플랫폼 → **Web 사이트 도메인** | 지도 SDK 허용 도메인 |
| Naver Developers → 서비스 URL / Callback URL | OAuth 콜백 |

**⑧ 인증서 자동 갱신** — 90일. 갱신 실패로 심사 중 사이트가 죽는 것이 가장 흔한 사고다

```bash
sudo certbot renew --dry-run \
  --pre-hook  "docker compose -f <PATH>/infra/docker/docker-compose.dev.yml stop nginx" \
  --post-hook "docker compose -f <PATH>/infra/docker/docker-compose.dev.yml start nginx"
systemctl list-timers | grep certbot
```

`--dry-run` 성공 후 같은 hook을 `/etc/letsencrypt/renewal/example.kr.conf`의
`[renewalparams]`에 `pre_hook`/`post_hook`으로 기록한다.

**⑨ 전환 확인**

- [ ] `https://example.kr` 접속, 인증서 유효
- [ ] `http://` 접근이 301 리다이렉트
- [ ] 기존 `18080` 포트 폐쇄 (구 URL로 접속하면 GPS가 막힌다)
- [ ] Kakao·Naver 로그인 성공, 쿠키에 `Secure`
- [ ] **Service Worker 등록됨** (HTTP에서 실패했던 항목)
- [ ] **모바일 위치 권한 팝업이 뜬다** (HTTP에서 안 뜨던 항목)

**롤백**: `.env` 되돌리기 + `AUTH_COOKIE_SECURE=false` + nginx conf 복구 + 포트 `18080:80` +
`up -d --force-recreate` + **Kakao/Naver 콘솔 Redirect URI 복구**(빠뜨리면 로그인이 계속 깨진다).

---

# 2부. 인프라 점검

## 2.1 컨테이너·시간대

- [ ] 3개 컨테이너 `Up`, postgres `healthy`
- [ ] 재시작 정책 동작 (`docker restart` 후 자동 복구)
- [ ] 서버 `date` = KST, postgres `SHOW TIME ZONE` = `Asia/Seoul`
- [ ] 디스크 여유 (`df -h`) — `data/postgres`가 VM 디스크를 쓴다

## 2.2 네트워크 (프로젝트 규칙 준수)

- [ ] postgres가 `127.0.0.1:15432`에만 바인딩 — 외부에서 `psql -h <HOST> -p 15432` 접속 **실패**해야 정상
- [ ] backend `8080` 외부 미노출
- [ ] 외부에서 `curl https://example.kr/api/health` 200

## 2.3 DB

- [ ] `SELECT version, success FROM flyway_schema_history ORDER BY installed_rank;` → 전부 `success = true`
- [ ] `ddl-auto: validate` 통과 (기동 로그에 schema 오류 없음)
- [ ] `SELECT * FROM pg_extension WHERE extname='vector';` (임베딩 컬럼용)
- [ ] **`pg_dump` 스냅샷 1회** — 통테 중 데이터가 쌓이므로 복구 지점을 만들어 둔다
- [ ] 통테 데이터 초기화 방법 결정 (회원·축제는 유지, 매칭 관련만 비우는 편이 반복에 유리)

## 2.4 스케줄러 기동 확인 (차단 항목 1)

```bash
docker exec meet-or-solo-backend-dev env | grep -E "MATCHING_|SPRING_PROFILES_ACTIVE"
docker logs meet-or-solo-backend-dev --tail=100 | grep -iE "Started MeetOrSolo|ERROR"
```

- [ ] `MATCHING_SCHEDULER_ENABLED=true`
- [ ] `SPRING_PROFILES_ACTIVE=dev`
- [ ] 기동 성공 (`MATCHING_SCORING_*` 합이 1이 아니면 여기서 실패한다)

## 2.5 외부 연동

- [ ] Kakao/Naver Redirect URI 등록 (1.4 ⑦)
- [ ] Kakao Maps 플랫폼 도메인 등록
- [ ] 관광공사 OpenAPI 키 유효·호출 한도
- [ ] OCI Object Storage 버킷 권한
- [ ] OpenAI 키·과금 한도

## 2.6 배포·로그

- [ ] 배포는 `--force-recreate`로 컨테이너를 교체한다 → **무중단 아님.** 통테 중 배포 시점 공지
- [ ] 배포 후 health check 게이트·롤백 절차 없음 → `/api/health` 수동 확인을 규칙에 포함
- [ ] `logs/backend`, `logs/nginx` 적재 확인. **로그 로테이션 미설정** → 디스크 증가 감시
- [ ] dev 프로필이 `org.hibernate.SQL: debug`라 로그량이 많다. 유지 여부 판단

---

# 3부. 화면별 체크리스트

`App.tsx` 라우트 19개 기준. 위에서부터 순서대로 실행한다.

## 3.0 준비

**계정**

| 표기 | 상태 | 준비 |
| --- | --- | --- |
| A, B, C, D | 프로필 완료(`ACTIVE`) | 가입 후 프로필 완료까지 |
| ADMIN | `role='ADMIN'` | 가입 후 `UPDATE members SET role='ADMIN' WHERE id=?;` (승격 API 없음) |
| SUSP | 정지 | ADMIN이 `/admin/members`에서 부여 |
| NEW | 미가입 | 제출 직전 신규 가입 흐름 재확인용 |

**데이터**

- [ ] 축제 sync 완료 — `SELECT count(*) FROM festivals WHERE status='ACTIVE';`
- [ ] 관광지 sync 완료 (솔로 코스용)
- [ ] **테스트 축제에 ACTIVE 만남 지점 2개 이상** — 없으면 매칭 신청이 `MATCHING_MEETING_POINT_NOT_READY`로 즉시 거부된다
- [ ] 계정별 여행스타일을 다르게 설정, 일부 계정만 취향 임베딩 등록(폴백 경로 확인용)

**판정 표기**: `PASS` / `FAIL` / `BLOCKED`(환경 문제) / `N/A`(제외 사유 필수)

### 3.0.1 제출 차단 항목 `[B]`

아래 ID는 **하나라도 `FAIL`이면 1단계 완료가 아니다.** 화면 단위가 아니라 항목 단위로 지정한다.
나머지 항목은 `FAIL`이어도 이슈 등록 후 진행할 수 있지만, **판정은 반드시 남긴다.**

| 영역 | 제출 차단 항목 |
| --- | --- |
| 인증 | `L-1` `L-2` `L-5` `L-6` `S-1` `H-1` `C-9` `F-14` |
| 세션 | `Y-5` `Y-6` `X-6` |
| 권한 | `Y-3` `AD-1` `AD-2` `AD-3` |
| 체크인 | `K-2` `K-9` `K-11` |
| 매칭 | `M-8` `M-10` `M-12` `M-14` `M-16` `M-21` |
| 매칭방 | `R-4` `R-6` `R-7` `R-8` `R-12` |
| 안전 | `R-19` `R-20` `R-21` `Q-3` `AR-4` |
| 솔로 코스 | `O-1` |
| 관리자 | `AM-5` `AM-10` `AP-4` `AP-7` |
| 탈퇴 | `WD-1` `WD-3` `WD-5` `WD-7` `WD-9` |
| 프로필 | `P-3` |
| 종단 | `X-1` ~ `X-8` 전부 |

## 3.1 모든 화면 공통

| ID | 항목 | 기대 |
| --- | --- | --- |
| C-1 | 360px 폭에서 가로 스크롤 | 없음 |
| C-2 | 하단 탭바 이동 | 정상 |
| C-3 | 딥링크 새로고침(`/mypage/matches`에서 F5) | 404 없이 로드 |
| C-4 | 뒤로가기 | 상태 유실 없음 |
| C-5 | 로딩 상태 | 스피너 표시, 빈 화면 없음 |
| C-6 | 네트워크 오류 | 에러 문구 표시, 화면 안 깨짐 |
| C-7 | 브라우저 콘솔 | 에러 없음 (특히 Kakao SDK) |
| C-8 | 정지 계정(SUSP) 조회 | **조회는 되고 활동만 차단** |
| C-9 `[B]` | **비로그인으로 아래 URL 직접 진입** — `/`, `/spots`, `/spots/:id`, `/festivals/:id`, `/matching`, `/mypage` | **전부 `/login`으로 이동.** 목록·상세 어느 것도 보이지 않아야 한다 |
| C-10 | 비로그인 상태로 `GET /api/festivals/{id}`를 **직접 호출**(curl) | `200`이 온다. **제출 차단 아님** — 아래 참조 |

> **C-10은 알려진 동작이다.** 화면은 로그인으로 튕기지만 API는 비로그인에게 `200`을 준다.
> `SecurityConfig`가 `anyRequest().permitAll()`이고 `FestivalController`·`TourPlaceController`·
> `HealthController` 셋에는 `@CookieValue`가 없기 때문이다. 이 셋이 인증 없는 controller의 전부이고,
> 노출되는 것은 관광공사 OpenAPI 공개 데이터다. 댓글 조회는 닉네임·본문까지 열리지만
> `ContentCommentResponse`가 `memberId`와 `profileImageUrl`을 제외하므로 개인 식별 정보는 나가지 않는다.
> **`FAIL`로 잡지 않는다.**

## 3.2 `/login` — LoginPage `P0`

- [ ] L-1 비로그인으로 `/` 접근 → `/login` 이동
- [ ] L-2 카카오 로그인 → 홈 진입
- [ ] L-3 네이버 로그인 → 홈 진입
- [ ] L-4 신규 계정은 `/signup`으로 유도
- [ ] L-5 쿠키가 **HttpOnly**, HTTPS면 **Secure**
- [ ] L-6 영구정지 계정 로그인 → `?oauthError=account_restricted` + **사유·기간 표시**
- [ ] L-7 OAuth 취소 → 오류 문구, 무한 대기 없음
- [ ] L-8 로그인 상태로 `/login` 진입 → 홈 리다이렉트

## 3.3 `/signup` — SignupPage `P0`

- [ ] S-1 프로필 저장 → 상태가 `PROFILE_REQUIRED` → **`ACTIVE`**
- [ ] S-2 필수 동의 미체크 → 진행 차단
- [ ] S-3 AI 동의 동의/미동의 각각 저장
- [ ] S-4 닉네임 중복·길이 검증 메시지
- [ ] S-5 여행스타일 1~3개 저장
- [ ] S-6 취향 입력 → 임베딩 생성 (**`OPENAI_API_KEY` 없으면 실패**)
- [ ] S-7 프로필 미완료 상태로 `/matching` 접근 → 매칭 거부
- [ ] S-8 가입 완료 → 홈

## 3.4 `/profile/edit` — ProfileEditPage `P1`

- [ ] P-1 닉네임·소개 수정 → 마이페이지 반영
- [ ] P-2 이미지 업로드 **1MB 미만** → 성공
- [ ] P-3 이미지 업로드 **2~5MB** → 성공 (**413이면 차단 항목 3 미해소**)
- [ ] P-4 5MB 초과 → 용량 초과 안내
- [ ] P-5 이미지 아닌 파일 → 거부
- [ ] P-6 여행스타일 변경
- [ ] P-7 취향 수정 → 임베딩 재생성
- [ ] P-8 취향 삭제 → 점수가 Jaccard 단독 폴백
- [ ] P-9 AI 동의 철회 (**정지 중에도 가능**)
- [ ] P-10 SUSP 계정 프로필 수정 → **허용**

## 3.5 `/` — HomePage `P0`

- [ ] H-1 진입 시 `/api/members/me`, 비로그인이면 `/login`
- [ ] H-2 진행 중 축제 히어로 카드
- [ ] H-3 다가오는 축제 카드
- [ ] H-4 위치 기반 주변 축제 (**HTTPS 아니면 실패**)
- [ ] H-5 위치 권한 거부 시 대체 화면 (빈 화면 아님)
- [ ] H-6 체크인 상태면 매칭 배너
- [ ] H-7 체크인 없으면 체크인 유도
- [ ] H-8 축제 카드 → 상세 이동
- [ ] H-9 이미지 없는 축제 → 플레이스홀더

## 3.6 `/spots` — ExploreListPage `P1`

- [ ] E-1 축제/관광지 탭 전환
- [ ] E-2 지역 필터
- [ ] E-3 일정 필터(진행중/예정)
- [ ] E-4 정렬 변경
- [ ] E-5 무한 스크롤 → **중복 항목 없음**
- [ ] E-6 필터 변경 시 처음부터 재로드 (이전 커서 재사용 안 함)
- [ ] E-7 결과 0건 → 빈 상태
- [ ] E-8 상세 다녀온 뒤 스크롤·필터 유지

## 3.7 `/festivals/:festivalId` — FestivalDetailPage `P0`

- [ ] F-1 축제 정보(기간·장소·소개)
- [ ] F-2 긴 소개 더보기 접기/펼치기
- [ ] F-3 행사 프로그램·상세 정보
- [ ] F-4 찜 토글 → 찜 목록 반영
- [ ] F-5 댓글 등록 → 즉시 반영
- [ ] F-6 댓글 좋아요 → 카운트 일치
- [ ] F-7 내 댓글 삭제
- [ ] F-8 남의 댓글 삭제 버튼 미노출
- [ ] F-9 스크립트 태그 입력 → 무해화 저장
- [ ] F-10 체크인 버튼 → 체크인 흐름
- [ ] F-11 체크인 상태 표시·취소
- [ ] F-12 주변 관광지 목록
- [ ] F-13 공유
- [ ] F-14 `[B]` **비로그인으로 축제 상세 URL 직접 진입(공유 링크 포함)** → **`/login`으로 이동**
- [ ] F-15 만료·위조된 `access_token` 쿠키로 진입 → **`/login`으로 이동**
- [ ] F-16 SUSP 계정 댓글 작성 → **403 + 사유·기간 팝업**

> **이 서비스는 로그인 필수다. 비로그인은 목록도 상세도 볼 수 없다.**
>
> 화면이 그렇게 동작하는 구조: `FestivalDetailPage`는 마운트 시 `useCurrentCheckin()`을 무조건
> 호출하고, 그 훅이 인증 필수인 `GET /api/festivals/checkin/me`를 부른다. 비로그인이면 `401`이
> 나고 `apiClient`의 `redirectToLoginIfUnauthorized`가 `window.location.replace('/login')`을
> 실행해 **화면 전체가 로그인으로 이동**한다.
>
> **문서 정합성 이슈(별도 처리 필요)**: `docs/27` 2.1의 `OptionalMemberResolver`와 `docs/10`의
> "공개 열람은 공유 링크 경로에만 존재한다"는 서술은 **공개 열람을 전제로 쓰였으나 현재 동작과
> 다르다.** 확정된 정책(로그인 필수)에 맞춰 두 문서를 정정해야 한다.

## 3.8 `/spots/:spotId` — TourSpotDetailPage `P1`

- [ ] T-1 관광지 정보·이미지
- [ ] T-2 찜 토글
- [ ] T-3 댓글 등록·삭제·좋아요
- [ ] T-4 좌표 없는 데이터 → 지도 영역 대체
- [ ] T-5 공유

## 3.9 `/check-in` — CheckInPage `P0`

> **HTTPS 미적용 시 이 절 전체 BLOCKED**

- [ ] K-1 위치 권한 요청 팝업
- [ ] K-2 체크인 성공 → ACTIVE 생성, `expires_at` = 체크인 +1시간
- [ ] K-3 남은 유효 시간 표시
- [ ] K-4 권한 거부 → `GPSPermissionModal`
- [ ] K-5 위치 시간 초과 → 재시도 안내
- [ ] K-6 **반경 밖 체크인 거부** (`BYPASS_RADIUS_CHECK=false` 회차에서만)
- [ ] K-7 **정확도 초과 거부** (같은 회차)
- [ ] K-8 체크인 취소 → ACTIVE pool도 정리
- [ ] K-9 1시간 경과 후 매칭 신청 → "유효한 체크인이 필요합니다"
- [ ] K-10 다른 축제 중복 체크인 → 정책대로
- [ ] K-11 SUSP 체크인 → **403 + 팝업**

## 3.10 `/matching` — MatchingConditionPage `P0`

> pool 탐색 창 **60초**. 두 계정이 그 안에 신청해야 한다.

**진입·조건**

- [ ] M-1 체크인 없이 진입 → "축제 체크인이 필요해요" + 체크인 버튼
- [ ] M-2 체크인 상태 → 체크인된 축제명 표시
- [ ] M-3 인원 선택 2/3/4명
- [ ] M-4 "최소 2명 허용" 토글
- [ ] M-5 취향 미입력 안내
- [ ] M-6 만남 지점 없는 축제 → "만남 장소 준비 중이에요"

**탐색·제안**

- [ ] M-7 신청 → "주변 여행자를 찾고 있어요"
- [ ] M-8 A·B 60초 내 신청 → 양쪽에 **"매칭 상대를 찾았어요"**
- [ ] M-9 상대 프로필(닉네임·여행스타일) 표시
- [ ] M-10 수락 → "함께할 분을 확정하고 있어요" → `/match-room`
- [ ] M-11 거절 → "매칭 제안을 거절했어요" + 30초 제한
- [ ] M-12 **무응답 30초** → 타임아웃 + **2분 cooldown** (*Scheduler 미기동이면 영원히 멈춤*)
- [ ] M-13 3인 신청에 2명만 → "인원이 조금 부족해요" + **"현재 인원으로 시작"**
- [ ] M-14 60초 내 상대 없음 → **솔로 코스 전환 안내**
- [ ] M-15 탐색 중 취소 → pool 정리, 재신청 가능

**제한·예외**

- [ ] M-16 cooldown 중 재신청 → 거부 + 남은 시간
- [ ] M-17 매칭 완료 1시간 내 재신청 → `MATCHING_COMPLETION_LOCKED`
- [ ] M-18 진행 중 매칭 있을 때 재신청 → 충돌 안내
- [ ] M-19 서로 차단한 A·D → 같은 그룹 안 됨
- [ ] M-20 **명시적으로 거절한 상대** → 같은 체크인 회차 안에서 재매칭 제외
- [ ] M-20-1 거절이 아니라 **타임아웃**으로 끝난 상대 → **제외되지 않음**(재매칭 후보에 남는다)
- [ ] M-20-2 **다른 체크인 회차**(체크인을 새로 한 뒤) → 이전에 거절한 상대와도 다시 매칭 가능

> M-20의 기준은 "만난 상대"가 아니라 **"명시적으로 거절한 상대"** 다.
> `MatchOpponentExclusionService.createForExplicitRejection`이 유일한 기록 경로이고,
> `match_opponent_exclusions`는 `(회원쌍, 체크인쌍)`을 키로 하므로 **체크인 회차가 바뀌면 초기화**된다.
> M-20-1과 M-20-2가 이 범위를 넘겨 짚지 않았는지 확인하는 항목이다.
- [ ] M-21 SUSP 매칭 신청 → **403 + 팝업**
- [ ] M-22 탐색 중 체크인 취소 → pool 정리
- [ ] M-23 탐색 중 앱 종료 후 재진입 → 상태 복원

## 3.11 `/match-room` — MatchRoomPage `P0`

**진입·정보**

- [ ] R-1 "매칭이 확정됐어요" 표시
- [ ] R-2 참여자 목록·프로필
- [ ] R-3 만남 지점 이름·주소
- [ ] R-4 **만남 지점 지도 렌더링** (Kakao key 필요)
- [ ] R-5 주소 없는 지점 → "주소 정보 없음"

**상태 동기화 (기기 2대)**

- [ ] R-6 도착 예정 시간(5~25분) 선택 → **상대 화면 즉시 반영**
- [ ] R-7 "도착했어요" → 상대에게 "도착 완료"
- [ ] R-8 **전원 도착** → 매칭 완료 전이
- [ ] R-9 상대 취소 → 즉시 전파
- [ ] R-10 취소 사유(일정 발생/이동 어려움/기타) 기록
- [ ] R-11 3인 이상에서 1명 이탈 → 잔여 그룹 정책대로
- [ ] R-12 **네트워크 끊고 30초 후 복구** → 자동 재연결(5초) + 상태 복원
- [ ] R-13 백그라운드 → 복귀 시 상태 일치
- [ ] R-14 시간 미선택 → "도착 시간 미정"
- [ ] R-15 **30분 경과 + 미도착** → "예정 시간이 지났어요" → 노쇼, penalty +3

**안전**

- [ ] R-16 신고 사유 선택(무례한 행동/성희롱/사기 의심/나타나지 않음/안전 문제/기타)
- [ ] R-17 사유 미선택 제출 → "신고 사유를 선택해주세요"
- [ ] R-18 내용 미입력 → "신고 내용을 확인해주세요"
- [ ] R-19 신고 접수 → 관리자 목록 노출
- [ ] R-20 상대 차단 → 매칭 후보 상호 제외
- [ ] R-21 SUSP의 신고·차단 → **허용**

## 3.12 `/solo-course` — SoloCoursePage `P0`

- [ ] O-1 매칭 실패 후 진입 → 코스 생성
- [ ] O-2 지점별 체류 시간
- [ ] O-3 지점 → 관광지 상세
- [ ] O-4 주변 관광지 부족 → 빈 상태 안내
- [ ] O-5 체크인 없이 직접 진입 → 안내

## 3.13 `/mypage` — MyPage `P1`

- [ ] Y-1 프로필·매너온도
- [ ] Y-2 찜/매칭 기록/차단 진입
- [ ] Y-3 **ADMIN에만 관리자 메뉴 노출**
- [ ] Y-4 정지 계정에서 관리자 여부 조회 시 **제재 팝업이 뜨지 않아야 함**
- [ ] Y-5 `[B]` 로그아웃 → 쿠키 만료, `/login`
- [ ] Y-6 `[B]` 로그아웃 후 뒤로가기 → 보호 화면 접근 불가
- [ ] Y-7 회원 탈퇴 진입점 노출 (로그아웃보다 약한 위계로 배치) → **3.22**로 이어서 확인

## 3.14 `/mypage/matches` — MatchHistoryPage `P1`

- [ ] Q-1 종료된 매칭 목록
- [ ] Q-2 커서 페이징
- [ ] Q-3 **종료 후 14일 이내** 신고 버튼 노출
- [ ] Q-4 **14일 경과** 신고 불가
- [ ] Q-5 0건 → 빈 상태
- [ ] Q-6 목록에서 신고 → 관리자 반영

## 3.15 `/mypage/blocks` — BlockedMembersPage `P1`

- [ ] B-1 차단 목록
- [ ] B-2 차단 해제 → 목록 제거
- [ ] B-3 해제 후 재매칭 후보 포함
- [ ] B-4 0건 → 빈 상태

## 3.16 `/mypage/favorites` — FavoritesPage `P1`

- [ ] V-1 찜한 축제·관광지 목록
- [ ] V-2 목록에서 찜 해제 → 즉시 제거
- [ ] V-3 항목 → 상세 이동
- [ ] V-4 0건 → 빈 상태

## 3.17 관리자 접근 제어 `P0`

- [ ] AD-1 비로그인 `/admin` → 차단
- [ ] AD-2 일반 계정 **URL 직접 입력** → 차단
- [ ] AD-3 정지된 관리자 → 차단
- [ ] AD-4 ADMIN → 정상 진입

## 3.18 `/admin/members` — AdminMembersPage `P0`

- [ ] AM-1 목록·검색·상태 필터
- [ ] AM-2 커서 페이징
- [ ] AM-3 **필터 변경 후 이전 커서 재사용 → 거부**
- [ ] AM-4 상세 — 신고 이력·제재 이력
- [ ] AM-5 정지 부여(사유+기간) → 이력 기록
- [ ] AM-6 정지 해제
- [ ] AM-7 영구정지
- [ ] AM-8 **활성 매칭 중 회원 정지 → 거부**(`ADMIN_MEMBER_ACTIVE_MATCH_CONFLICT`)
- [ ] AM-9 자기 자신·다른 관리자 제재 → 거부
- [ ] AM-10 정지 후 **해당 사용자 화면에 사유·기간 노출**
- [ ] AM-11 정지 만료 자동 복구(60초 주기) → `ACTIVE`

## 3.19 `/admin/reports` — AdminReportsPage `P0`

- [ ] AR-1 목록·필터·커서 페이징
- [ ] AR-2 상세 — 신고자/피신고자/매칭 정보
- [ ] AR-3 상태 전이
- [ ] AR-4 **유효 판정** → penalty +5, 매너온도 -5
- [ ] AR-5 매너온도 하한 20
- [ ] AR-6 **30일 내 유효 3건** → 안전 알림 생성
- [ ] AR-7 무효 판정 → 페널티 미적용

## 3.20 `/admin/meeting-points` — AdminMeetingPointsPage `P0`

- [ ] AP-1 축제 선택 → 지점 목록
- [ ] AP-2 **지도에서 좌표 선택** (Kakao key 필요)
- [ ] AP-3 **장소 검색**으로 좌표 입력
- [ ] AP-4 등록 → 반영
- [ ] AP-5 수정
- [ ] AP-6 상태 변경(ACTIVE/INACTIVE)
- [ ] AP-7 ACTIVE 0개 축제 → 매칭 신청 거부 연동 확인
- [ ] AP-8 ACTIVE 2개 이상 → round-robin 분산

## 3.21 `/admin` — AdminDashboardPage `P2`

- [ ] AB-1 대시보드 진입
- [ ] AB-2 안전 알림 표시·확인 처리
- [ ] AB-3 각 관리 화면 이동

## 3.22 회원 탈퇴 · 재가입 (docs/19 4.4)

진입점은 `/mypage` → 회원 탈퇴(`WithdrawalConfirmDialog`), 관리자는
`/admin/members` → 강제 탈퇴(`POST /api/admin/members/{memberId}/forced-withdrawal`).

**정책 요약** — 물리 삭제하지 않고 **익명화**한다(FK 31개가 `ON DELETE RESTRICT`, 신고·제재·매칭
감사 이력이 탈퇴 회원을 참조). 재가입 쿨오프는 **7일**(`MemberRejoinCooldownPolicy.COOLDOWN`).

**본인 탈퇴**

- [ ] WD-1 `[B]` 탈퇴 확인 dialog → "정말 탈퇴하시겠어요?" 표시, 취소 시 아무 일 없음
- [ ] WD-2 **정지 중 탈퇴 시도** → dialog에 "남은 이용정지 기간은 탈퇴로 사라지지 않아요" 경고 노출
- [ ] WD-3 `[B]` 탈퇴 실행 → 상태 `WITHDRAWN`, **세션 종료 후 `/login` 이동**
- [ ] WD-4 탈퇴 직후 이전 쿠키로 API 호출 → 접근 불가
- [ ] WD-5 `[B]` **익명화 확인** — 닉네임을 포함한 개인정보 컬럼 전부 `NULL`.
      화면에 보이는 `탈퇴한 회원`은 컬럼 값이 아니라 조회 SQL이 만드는 표시 문구다(`V30`)

```sql
SELECT status, nickname, email, intro, profile_image_url,
       gender_encrypted, age_range_encrypted, withdrawn_at, withdrawn_by_admin
FROM members WHERE id = ?;
-- nickname을 포함한 개인정보 컬럼이 전부 NULL 이어야 한다 (chk_members_withdrawn_anonymized)
```

- [ ] WD-6 취향·취향 임베딩·찜 **물리 삭제**, 댓글은 `DELETED`로 숨김, 동의는 row 유지 + `revoked_at` 기록
- [ ] WD-7 `[B]` **진행 중 매칭 정리** — pool/proposal 취소, 그룹은 남은 인원에게
      `MEMBER_CANCELLED`/`MATCH_CANCELLED` 전파. `cancel_reason = 'WITHDRAWN'`
- [ ] WD-8 **탈퇴로 penalty·cooldown이 부과되지 않음** (부과 대상이 익명화되므로 무의미)
- [ ] WD-9 `[B]` **활성 매칭 중에도 탈퇴가 거부되지 않음** — 관리자 제재는 `ACTIVE_MATCH_CONFLICT`로
      막지만 탈퇴는 개인정보 삭제 요구라 막지 않는다
- [ ] WD-10 체크인 취소됨
- [ ] WD-11 **탈퇴 반복 호출** → 조용히 성공(멱등)
- [ ] WD-12 **탈퇴자가 남는 화면에서 표시 문구 확인** — 매칭 기록, 매칭방 이벤트, 차단 목록,
      관리자 회원 목록·신고 목록에 `탈퇴한 회원`이 뜨고 **빈 칸이나 오류 화면이 아니어야 한다**
      (컬럼이 `NULL`이라 치환이 빠진 경로는 렌더링이 죽는다)
- [ ] WD-13 **재가입 후 닉네임** — 쿨오프 경과 후 재로그인하면 닉네임이 OAuth 값으로 채워지고
      `탈퇴한 회원`이 남아 있지 않아야 한다. 정지를 이어받아 부활한 경우도 같다
- [ ] WD-12 신고·제재·매칭 감사 이력은 **보존**됨

**재가입**

- [ ] WD-13 탈퇴 **7일 이내** 같은 소셜 계정 로그인 → 거부 + **재가입 가능 시각 안내**
- [ ] WD-14 7일 경과 후 로그인 → **계정 부활**, 프로필 재입력 요구(`PROFILE_REQUIRED`)
- [ ] WD-15 **정지 중 탈퇴한 회원이 재가입** → **잔여 정지 기간을 이어받음**
      (탈퇴가 제재 세탁 수단이 되면 안 됨)
- [ ] WD-16 거부 안내가 "소셜 로그인에 실패했습니다"가 **아니라** 사유·시각을 담고 있음

**관리자 강제 탈퇴**

- [ ] WD-17 강제 탈퇴 실행 → 익명화, `withdrawn_by_admin = true`
- [ ] WD-18 `admin_actions`에 `FORCED_WITHDRAWAL` 감사 로그 기록
- [ ] WD-19 **재가입 차단(`blockRejoin=true`)** → 7일이 지나도 **영구 거부**
- [ ] WD-20 재가입 차단 없이 강제 탈퇴(`false`) → 7일 후 재가입 가능
- [ ] WD-21 **이미 탈퇴한 회원에게 강제 탈퇴** → 조용히 넘기지 않고 `MEMBER_ALREADY_WITHDRAWN` 알림
- [ ] WD-22 강제 탈퇴는 `AdminMemberActionType`(정지/해제/차단)과 **별도 진입점**임을 확인
      (BAN은 되돌릴 수 있고 강제 탈퇴는 익명화라 되돌릴 수 없다)

> **주의**: 이 기능은 마이그레이션 `V28`을 동반한다. 익명화와 강제 탈퇴는 **되돌릴 수 없으므로**
> 2.3의 `pg_dump` 스냅샷을 먼저 확보한 뒤 실행한다. 테스트 계정으로만 수행한다.

---

# 4부. 종단 흐름과 동시성

개별 화면이 멀쩡해도 **연결**이 끊길 수 있다. 3부가 끝난 뒤 실행한다.

| ID | 흐름 | 통과 조건 |
| --- | --- | --- |
| X-1 | 신규 가입 → 프로필 완료 → 홈 | NEW 계정으로 막힘 없이 도달 |
| X-2 | 홈 → 축제 상세 → 체크인 → 매칭 신청 → 제안 → 수락 → 매칭방 → 도착 → **완료** | 2계정 동시, 끊김 없음 |
| X-3 | 매칭 실패 → 솔로 코스 → 관광지 상세 → 찜 | 전환 링크 정상 |
| X-4 | 만남 완료 → 매칭 기록 → 신고 → 관리자 처리 → **제재** → 사용자에게 사유 노출 → 만료 복구 | 신고 한 건이 끝까지 |
| X-5 | 차단 → 재매칭 → 상대 제외 확인 | 같은 그룹 안 됨 |
| X-6 | 로그인 → 30분 방치(토큰 만료) → 화면 조작 | 자동 갱신, 로그아웃 안 됨 |
| X-7 | **4명 동시 매칭 신청** | 한 명이 두 그룹에 안 들어감 |
| X-8 | **진행 중 매칭 상태에서 탈퇴** → 남은 인원 화면 전파 → 익명화 → 7일 후 재가입 → 프로필 재입력 | 그룹이 깨지지 않고 정리되며, 재가입 판정이 정책대로 동작 |

X-7 확인:

```sql
SELECT member_id, count(*)
FROM match_pools
WHERE status IN ('WAITING','LOCKED','PROPOSED')
GROUP BY member_id HAVING count(*) > 1;
-- 0행이어야 정상
```

화면으로 확인이 어려운 상태 전이는 DB로 본다.

```sql
SELECT id, status, round, created_at FROM match_proposals ORDER BY id DESC LIMIT 10;
SELECT member_id, status, starts_at, expires_at FROM match_cooldowns ORDER BY id DESC LIMIT 10;
SELECT member_id, reason, score_delta, created_at FROM match_penalty_events ORDER BY id DESC LIMIT 10;
```

---

# 5부. 재배포 후 스모크

결함을 고쳐 재배포할 때마다 **20분 안에** 도는 최소 확인이다. 전체를 반복하지 않는다.

- [ ] SM-1 배포 성공, 컨테이너 3개 `Up`
- [ ] SM-2 `curl https://example.kr/api/health` 200
- [ ] SM-3 **Service Worker 새 버전 활성화 확인** (DevTools → Application)
- [ ] SM-4 로그인 1회
- [ ] SM-5 체크인 1회
- [ ] SM-6 X-2 흐름 1회 완주 (2계정)
- [ ] SM-7 이번에 고친 항목 재확인
- [ ] SM-8 `docker logs ... | grep -i error` 신규 오류 없음

> **PWA 캐시 함정**: Service Worker 때문에 재배포해도 옛 화면이 뜰 수 있다.
> `registerType: 'autoUpdate'`라 결국 갱신되지만 새로고침 한 번으로는 안 바뀔 수 있다.
> "고쳤는데 그대로네?"의 가장 흔한 원인이므로 SM-3을 먼저 확인한다.

## 5.2 배포 복구 기준

`health 200`과 백업 존재만으로는 배포 완료를 판정할 수 없다. **되돌릴 수 있어야** 완료다.

현재 파이프라인의 한계를 먼저 인지한다.

- 배포는 `--force-recreate`로 컨테이너를 교체한다 → **무중단이 아니다**
- **이전 jar를 보관하지 않는다** → 되돌리려면 재빌드가 필요하다
- 배포 후 **health check 게이트가 없다** → 실패해도 워크플로는 성공으로 끝난다

### 5.2.1 배포 전 (매 배포)

- [ ] RC-1 현재 배포본 보존 — 되돌릴 대상을 만든다

```bash
cd <DEV_DEPLOY_PATH>
cp backend/app.jar backend/app.jar.prev
cp -r frontend/dist frontend/dist.prev
git -C <저장소> rev-parse HEAD > deployed_commit.prev
```

- [ ] RC-2 **DB 변경 포함 여부 확인** — 이번 배포에 새 마이그레이션이 있는가

```bash
jar tf backend/app.jar | grep db/migration | sort | tail -5
```

- [ ] RC-3 마이그레이션이 있으면 **배포 직전 `pg_dump`** 실행

```bash
docker exec meet-or-solo-postgres-dev pg_dump -U <USER> -d <DB> -F c \
  > backup_$(date +%Y%m%d_%H%M).dump
```

### 5.2.2 복구 판단 기준

| 상황 | 복구 방법 |
| --- | --- |
| **DB 변경 없음** | `app.jar.prev`·`dist.prev`로 되돌리고 `up -d --force-recreate`. 수 분 내 복구 |
| **DB 변경 있음 (전방 호환)** — 컬럼 추가처럼 이전 코드도 동작 | 코드만 되돌린다. 스키마는 그대로 둔다 |
| **DB 변경 있음 (파괴적)** — 컬럼 삭제·제약 추가·데이터 변형 | **코드 롤백만으로는 복구되지 않는다.** `pg_dump` 복원이 필요하고 **복원 시점 이후 데이터는 소실**된다 |

> `V28`처럼 **데이터를 변형하는 마이그레이션**(기존 `WITHDRAWN` row 익명화)은 세 번째에 해당한다.
> 되돌릴 수 없으므로 배포 전 백업이 유일한 안전장치다.

### 5.2.3 코드 롤백 절차

```bash
cd <DEV_DEPLOY_PATH>
mv backend/app.jar.prev backend/app.jar
rm -rf frontend/dist && mv frontend/dist.prev frontend/dist
docker compose --env-file .env -f infra/docker/docker-compose.dev.yml up -d --force-recreate
curl -s https://example.kr/api/health
```

- [ ] RC-4 롤백 후 `/api/health` 200
- [ ] RC-5 롤백 후 로그인 1회 성공

### 5.2.4 백업 복원 확인 (분기 1회 이상 · 제출 전 필수)

**복원해 본 적 없는 백업은 백업이 아니다.** 실제 DB에 덮어쓰지 말고 임시 DB로 복원해 검증한다.

```bash
docker exec meet-or-solo-postgres-dev createdb -U <USER> restore_test
docker exec -i meet-or-solo-postgres-dev pg_restore -U <USER> -d restore_test < backup_YYYYMMDD_HHMM.dump
docker exec meet-or-solo-postgres-dev psql -U <USER> -d restore_test \
  -c "select count(*) from members; select count(*) from festivals; select max(installed_rank) from flyway_schema_history;"
docker exec meet-or-solo-postgres-dev dropdb -U <USER> restore_test
```

- [ ] RC-6 복원 성공, 주요 테이블 건수가 원본과 일치
- [ ] RC-7 `flyway_schema_history`가 백업 시점 버전과 일치
- [ ] RC-8 검증 후 임시 DB 삭제

### 5.2.5 배포 완료 판정

- [ ] RC-9 5부 스모크 `PASS`
- [ ] RC-10 롤백 대상 보존됨(RC-1) · DB 변경 시 백업 존재(RC-3)
- [ ] RC-11 복구 절차를 **실제로 1회 실행해 확인**함(RC-4·RC-5 또는 RC-6~RC-8)
- [ ] RC-12 배포된 커밋 해시를 7부 기록표에 기록

---

# 6부. 제출 전 준비

## 6.1 PWA

PWA는 **별도 배포 대상이 아니다.** `VitePWA`가 build 때 `sw.js`·`manifest.webmanifest`를
`dist`에 만들어 넣으므로, 지금 배포되는 그 프론트엔드가 곧 PWA다. HTTPS가 붙는 순간부터 동작한다.

**설치 · 표시**

- [ ] W-1 Service Worker 등록
- [ ] W-2 홈 화면에 추가 → 아이콘·앱 이름 정상
- [ ] W-3 설치된 앱에서 standalone 실행 (주소창 없음)
- [ ] W-4 오프라인에서 앱 셸 로드
- [ ] W-5 **아이콘 교체** — 현재 `public/icons/placeholder.svg` 하나뿐이고 manifest도 그것만 참조한다. 설치 시 홈 화면에 placeholder가 뜬다. `index.html`에 `apple-touch-icon`도 없어 iOS에서는 화면 축소본이 아이콘이 된다
- [ ] W-6 iOS Safari / Android Chrome **양쪽 각 1대 이상**

**설치 앱에서의 동작 — 브라우저와 다르게 깨지는 지점**

- [ ] W-7 `[B]` **설치 앱에서 소셜 로그인** → 외부 브라우저/커스텀 탭으로 나갔다가 **앱으로 정상 복귀**하고 세션이 유지됨
- [ ] W-8 `[B]` 설치 앱에서 **위치 권한 허용** → 체크인 성공
- [ ] W-9 위치 권한 **거부** 후 → 안내 표시, 재요청 경로 존재. 앱이 멈추지 않음
- [ ] W-10 `[B]` **매칭 중 화면 잠금 / 백그라운드 전환 후 복귀** → 최신 상태로 복원 (WebSocket 재연결 + REST 보정)
- [ ] W-11 `[B]` **통신 끊김 → 복구** → 중복 매칭 신청이 생기지 않고, 잘못된 완료 표시가 없음
- [ ] W-12 `[B]` **이전 버전이 설치된 상태에서 재배포** → 업데이트 반영 후 핵심 흐름(X-2) 정상

> W-10·W-11은 모바일에서만 재현된다. 데스크톱 브라우저는 백그라운드 전환 시 타이머·소켓을
> 훨씬 관대하게 유지하므로, 여기서 통과했다고 실기기에서 통과한다고 볼 수 없다.
> W-11은 **중복 신청**과 **잘못된 완료 표시**를 각각 확인한다. `match_pools` 중복 여부는 X-7의 SQL로 본다.

## 6.2 심사자 시연 문제 ⚠️

**이 서비스는 심사자가 혼자서 볼 수 없다.**

| 장벽 | 내용 |
| --- | --- |
| 로그인 필수 | 홈이 `/api/members/me`를 부르므로 비로그인은 홈에 못 들어간다 |
| 체크인 필수 | 매칭의 전제. 강원도 축제 현장 위치가 필요하다 |
| **매칭은 2명 필요** | **60초 안에 두 계정이 동시 신청해야 성사된다. 혼자서는 매칭 화면을 절대 볼 수 없다** |

- [ ] D-1 **시연 영상 준비** — 매칭 성사~완료 구간은 영상 외에 보여줄 방법이 사실상 없다
- [ ] D-2 심사 기간에 ACTIVE 축제가 존재하는지 확인
- [ ] D-3 제출 환경의 `FESTIVAL_CHECKIN_BYPASS_RADIUS_CHECK` 값 결정 — `true`면 심사자가 어디서든 체크인 가능, `false`면 현장 밖에서 아무것도 못 본다
- [ ] D-4 데모 계정 안내 여부 결정 (OAuth라 계정 공유가 어렵다)

## 6.3 환경·운영

- [ ] D-5 제출 환경을 dev로 할지 결정. `docs/07`은 `prod`를 "추후 분리"로 두고 현재 배포 대상은 dev뿐이다. dev URL을 제출하면 그것이 곧 제출 환경이 된다
- [ ] D-6 인증서 자동 갱신 `--dry-run` 성공 (1.4 ⑧)
- [ ] D-7 제출 직전 X-2 흐름 1회 완주
- [ ] D-8 CI 테스트 활성화 (차단 항목 5) — 제출 후 수정 시 회귀 방지선

---

# 7부. 기록

## 7.1 회차 정보 (항목표보다 먼저 채운다)

| 항목 | 값 |
| --- | --- |
| 회차 | 예: 2026-09-15 1회차 |
| 테스트 일시 | |
| 담당자 | |
| **배포 커밋 해시** | `git rev-parse HEAD` |
| 대상 URL | |
| 기기 / OS / 브라우저 | 예: Galaxy S22 / Android 14 / Chrome 131 |
| 서버 프로필 | `SPRING_PROFILES_ACTIVE` |
| `BYPASS_RADIUS_CHECK` | `true` / `false` (GPS 회차 구분) |

기기가 여러 대면 **기기마다 회차를 나눈다.** 한 표에 섞으면 어느 기기에서 실패했는지 남지 않는다.

## 7.2 항목 판정

| ID | 화면/항목 | `[B]` | 판정 | 실제 결과 | 이슈 링크 |
| --- | --- | --- | --- | --- | --- |
| L-1 | /login 비로그인 리다이렉트 | B | | | |
| … | | | | | |

## 7.3 단계별 완료 서명

| 단계 | 판정일 | 담당자 | 커밋 | 미실행 | BLOCKED | `[B]` FAIL |
| --- | --- | --- | --- | --- | --- | --- |
| 1단계 개발서버 검증 | | | | 0건 | 0건 | 0건 |
| 2단계 실기기 PWA 검증 | | | | 0건 | 0건 | 0건 |
| 3단계 배포·제출 | | | | 0건 | 0건 | 0건 |

**미실행·`BLOCKED`·`[B]` FAIL이 하나라도 0이 아니면 그 단계는 완료가 아니다.**

## 7.4 기록 규칙

- `FAIL`은 **재현 절차**를 남긴다. "안 됨"만 적으면 재현할 수 없다
- `BLOCKED`는 **환경 사유**를 명시한다 (예: "HTTPS 미적용으로 GPS 불가")
- `N/A`는 **제외 사유**를 명시한다. 사유 없는 `N/A`는 미실행으로 본다
- 구현 결함과 환경 결함을 섞지 않는다. 섞이면 재실행 범위를 판단할 수 없다
- 재배포 후에는 **회차를 새로 열고** 커밋 해시를 갱신한다. 이전 회차를 덮어쓰지 않는다

## 부록. 자동 테스트와의 경계

backend test class 124개(통합테스트 34개), frontend vitest 60개가 이미 서비스 단위를 덮고 있다.
이 문서는 자동 테스트가 닿지 못하는 부분만 다룬다.

| 영역 | 자동 테스트 | 여기서 확인할 것 |
| --- | --- | --- |
| 매칭 점수·그룹 구성 | `MatchGroupComposerTest` 등 | 실제 데이터에서 점수가 납득 가능한지 |
| pool claim 동시성 | Testcontainers 통합테스트 | 다중 기기 동시성 |
| 제안 생성·응답·타임아웃 | 통합테스트 3종 | **Scheduler가 실제로 기동되는지** |
| 제재 정책 | `SuspendedActivityPolicyCoverageTest` | 화면에 사유·기간이 보이는지 |
| 외부 API | Mock 기반 | **실제 키·한도·응답 형식** |
| WebSocket | interceptor 단위 | 실제 연결·재연결·nginx 경유 |
| 브라우저 | vitest(DOM stub) | 실기기 레이아웃·GPS·PWA |

`HealthCheckPage.tsx`는 `App.tsx`에 라우트가 없어 접근 경로가 없다. 점검 대상에서 제외한다.
