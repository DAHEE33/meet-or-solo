# 배포 방향

## 배포 목표

초기 배포 대상은 Oracle Cloud Ubuntu VM 1대입니다.

이 문서는 `dev` 배포를 먼저 다루고, 이어서 같은 VM에 분리해 올리는 `prod` 배포와 수동 배포 절차를 다룹니다.

예정 dev 배포 구조:

```text
Nginx
├─ frontend dist 서빙
├─ /api -> Spring Boot proxy
└─ /ws  -> Spring Boot WebSocket STOMP proxy

Spring Boot
└─ PostgreSQL 연결

PostgreSQL
└─ private/local access only
```

Redis는 MVP 초기 배포 구성에 포함하지 않습니다.

## WBS 기준 배포 순서

기능 분업 전 배포 관련 작업은 다음 순서로 진행합니다.

1. [6단계] Oracle VM dev 서버/dev DB 구축 준비
2. [7단계] nginx + docker-compose dev 배포 초안
3. [8-1단계] GitHub Actions CI 초안
4. [8-2단계] GitHub Actions dev CD 초안

이 순서는 기능 구현 전에 팀이 같은 dev 환경에서 frontend/backend 연동, DB 연결, reverse proxy 경계를 확인하기 위한 것입니다.

현재 단계와 다음 작업은 [docs/10_PROGRESS_LOG.md](10_PROGRESS_LOG.md)를 기준으로 확인합니다.

## Oracle Cloud VM

대상 환경:

- Ubuntu VM
- Nginx
- Java runtime
- PostgreSQL
- Certbot
- 필요 시 frontend build artifact 배치

리소스 주의사항:

- 상시 실행 서비스 수를 줄인다.
- Redis는 필요해질 때까지 제외한다.
- disk usage를 모니터링한다.
- JVM memory 설정을 보수적으로 둔다.

## 서버 배포 profile

`local`, `dev`, `prod` profile을 코드에 유지합니다.

- `local`: 개인 PC Docker PostgreSQL 기준
- `dev`: Oracle Cloud VM의 개발/시연용 DB 기준
- `prod`: 같은 Oracle Cloud VM에서 dev와 **분리해 함께 운영**하는 환경

초기에는 `/home/ubuntu/meet-or-solo` 하나만 `dev`로 배포했습니다. 운영 배포 기반이 필요해지면서 같은 VM에 `prod`를 추가하는 방향으로 전환했습니다. 두 환경은 앱 경로, DB와 계정, 데이터 경로, compose project와 network, host 포트, 로그, 환경변수를 전부 분리합니다.

| 항목 | dev | prod |
| --- | --- | --- |
| 앱 경로 | `/home/ubuntu/meet-or-solo` | `/home/ubuntu/meet-or-solo-prod` |
| profile | `SPRING_PROFILES_ACTIVE=dev` | `SPRING_PROFILES_ACTIVE=prod` |
| compose project | `meet-or-solo-dev` | `meet-or-solo-prod` |
| 웹 host 포트 | `18080` (모든 인터페이스) | `127.0.0.1:28080` (loopback 전용) |
| DB | `meet_or_solo_dev` | `meet_or_solo_prod` (별도 계정) |
| DB host 포트 | `127.0.0.1:15432` | publish 없음. 필요 시 임시로 `127.0.0.1:25432` |
| 기준 브랜치 | `dev` | `main` |

운영 DB는 빈 DB에서 Flyway migration으로 구성하며 dev DB 전체를 복사하지 않습니다.

PostgreSQL `5432`는 외부 전체 공개를 하지 않습니다. 개발자가 DB에 직접 접속해야 하면 SSH tunnel 방식을 우선 고려합니다.

## 6단계: Oracle VM dev 서버/dev DB 구축 준비

6단계는 실제 서버 접속이나 파일 배포가 아니라 dev 서버와 dev DB 구성 기준을 팀원이 함께 확인할 수 있게 정리하는 단계입니다.

dev 서버 준비 기준:

- `/home/ubuntu/meet-or-solo` 기준 배포 구조를 사용한다.
- backend는 `SPRING_PROFILES_ACTIVE=dev`로 실행한다.
- PostgreSQL dev DB는 Oracle VM 내부 PostgreSQL을 기준으로 한다.
- PostgreSQL `5432`는 외부 전체 공개를 하지 않는다.
- DB 직접 확인이 필요하면 SSH tunnel을 우선 사용한다.
- `prod`는 아직 만들지 않는다.

실제 서버 IP, 계정, DB 비밀번호, SSH Key는 문서나 repository에 기록하지 않습니다.

예정 dev 서버 폴더 구조:

```text
/home/ubuntu/meet-or-solo/
├─ backend/
│  └─ app.jar
├─ frontend/
│  └─ dist/
├─ nginx/
│  └─ default.conf
├─ data/
│  └─ postgres/
├─ logs/
└─ .env
```

폴더별 기준:

- `backend/app.jar`: backend build artifact 후보입니다.
- `frontend/dist/`: frontend `npm run build` 결과물 후보입니다.
- `nginx/default.conf`: 7단계에서 작성할 nginx dev 설정 후보입니다. 6단계에서는 파일을 만들지 않습니다.
- `data/postgres/`: Oracle VM 내부 PostgreSQL dev data volume 후보입니다.
- `logs/`: dev 서버 로그 보관 후보입니다.
- `.env`: dev 서버 환경변수 파일 후보입니다. 실제 값은 repository에 커밋하지 않습니다.

dev DB 기준:

- DB 이름 예시는 `meet_or_solo_dev`를 사용합니다.
- DB user/password는 실제 값을 하드코딩하지 않고 `.env` 또는 GitHub Secrets에서 주입합니다.
- backend와 PostgreSQL은 같은 VM 내부 네트워크 또는 localhost 경계에서 통신합니다.
- 외부에서 PostgreSQL `5432`로 직접 접속하는 구성을 만들지 않습니다.
- 팀원이 dev DB를 확인해야 하면 SSH tunnel을 사용합니다.
- dev compose의 PostgreSQL은 외부 공개 없이 서버 내부 loopback에만 `127.0.0.1:15432 -> postgres:5432`로 publish합니다.
- `15432`는 SSH tunnel을 위한 서버 내부 고정 포트이며 Oracle Cloud Ingress에 열지 않습니다.

SSH tunnel 예시는 placeholder만 사용합니다.

```bash
ssh -L 15432:localhost:15432 <SSH_USER>@<DEV_SERVER_HOST>
```

backend `dev` profile 기준 환경변수:

```text
SPRING_PROFILES_ACTIVE=dev
DB_URL
DB_USERNAME
DB_PASSWORD
CORS_ALLOWED_ORIGINS
SERVER_PORT
```

`DB_URL` 형식 예시는 아래처럼만 둡니다.

```text
jdbc:postgresql://<INTERNAL_DB_HOST>:5432/meet_or_solo_dev
```

`<INTERNAL_DB_HOST>`는 7단계 nginx/docker-compose dev 배포 초안에서 배포 방식에 맞춰 확정합니다. 실제 IP, 실제 도메인, 실제 DB 계정, 실제 비밀번호는 문서에 쓰지 않습니다.

frontend dev/prod build 기준:

- local 개발은 `npm run dev`와 Vite proxy를 사용합니다.
- dev 서버 배포는 `npm run build` 결과물인 `frontend/dist`를 사용합니다.
- `frontend/dist/`는 build 결과물이므로 Git에 커밋하지 않습니다.
- nginx가 `frontend/dist`를 서빙하고 `/api` 요청은 backend로 reverse proxy합니다.
- 실제 nginx 설정 파일은 7단계에서 작성합니다.

## 7단계: nginx + docker-compose dev 배포 초안

7단계에서는 실제 Oracle VM 접속이나 배포 없이, repository 안에 dev 배포용 템플릿을 준비합니다.

dev 배포 초안에는 다음 연결을 목표로 합니다.

- frontend `dist`
- backend app
- postgres
- nginx

dev 배포 기준 docker-compose를 먼저 정리하고, prod용 docker-compose는 추후 제출/운영 단계에서 분리합니다.

7단계에서 준비한 파일:

- `infra/docker/docker-compose.dev.yml`
- `infra/nginx/default.dev.conf`
- `infra/env/.env.dev.example`

7단계에서도 `prod`용 docker-compose는 만들지 않고, dev 배포 초안을 먼저 검증합니다.

### dev docker compose 구성

`infra/docker/docker-compose.dev.yml`은 Oracle VM dev 서버에서 아래 서비스를 하나의 compose 내부 network로 연결하는 초안입니다.

| service | 역할 | 외부 포트 |
| --- | --- | --- |
| `postgres` | PostgreSQL dev DB | 공개하지 않음 |
| `backend` | Spring Boot jar 실행 | 공개하지 않음 |
| `nginx` | frontend 정적 파일 서빙, `/api` reverse proxy | `18080` |

기준:

- `postgres`는 PostgreSQL 16과 pgvector extension 파일을 포함하는 `pgvector/pgvector:pg16`을 사용합니다.
- 기존 `postgres:16-alpine`에서 이미지를 전환할 때는 기존 data volume을 삭제하지 않고 컨테이너만 재생성합니다. 재기동 후 `CREATE EXTENSION vector`와 Flyway `V11__add_member_preference_embeddings.sql` 적용 이력을 확인합니다.
- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`는 실제 서버 `.env`에서 주입합니다.
- PostgreSQL data는 `data/postgres` 경로를 volume으로 사용합니다.
- PostgreSQL은 팀원 SSH tunnel 접근을 위해 host loopback `127.0.0.1:15432`에만 publish합니다.
- `backend`는 서버에 배치된 `backend/app.jar`를 `java -jar`로 실행합니다.
- `backend`는 `SPRING_PROFILES_ACTIVE=dev`를 사용합니다.
- `DB_URL`은 compose 내부 service name인 `postgres`를 사용합니다.
- `backend`의 `8080`은 외부에 직접 publish하지 않습니다.
- `postgres`의 `5432`는 외부에 직접 publish하지 않고 서버 내부 `127.0.0.1:15432`에만 publish합니다.
- `nginx`는 dev 검증용으로 host `18080` 포트를 container `80`에 publish합니다.
- 같은 Oracle VM에 기존 운영 nginx나 다른 서비스가 host `80`을 사용 중일 수 있으므로, dev compose는 기본적으로 host `80`을 점유하지 않습니다.

`DB_URL` 예시:

```text
jdbc:postgresql://postgres:5432/meet_or_solo_dev
```

### nginx dev 설정

`infra/nginx/default.dev.conf`는 dev 환경용 nginx 설정 초안입니다.

기준:

- `frontend/dist`를 `/usr/share/nginx/html`로 mount해 정적 파일을 서빙합니다.
- SPA routing을 위해 `try_files $uri $uri/ /index.html`을 사용합니다.
- `/api/` 요청은 `backend:8080`으로 reverse proxy합니다.
- `/ws` WebSocket 경로는 `backend:8080`으로 Upgrade proxy합니다.
- `proxy_http_version 1.1`, `Upgrade`, `Connection` header를 전달합니다.
- HTTPS, Certbot, 실제 domain 설정은 이번 단계에서 하지 않습니다.

### 호스트 nginx(TLS) 설정

compose nginx는 TLS를 처리하지 않고 host `18080`에만 붙습니다. dev 도메인으로 접속하려면 Oracle VM에 직접 설치한 nginx가 인증서를 끊고 `127.0.0.1:18080`으로 넘겨야 합니다.

`infra/nginx/host-https.dev.conf.example`이 그 예시입니다.

기준:

- `/ws`를 `location /`보다 먼저 선언하고 `Upgrade`, `Connection` header를 전달합니다.
- `/ws` location이 없으면 요청이 `location /`로 떨어져 WebSocket handshake가 400으로 끊깁니다.
- STOMP 유휴 연결이 끊기지 않도록 `proxy_read_timeout`을 기본값보다 크게 둡니다.
- `CORS_ALLOWED_ORIGINS`에 브라우저가 실제로 여는 origin(`https://<DEV_DOMAIN>`)을 포함합니다. 이 값은 REST CORS와 WebSocket handshake origin 검사에 함께 쓰입니다.
- 실제 도메인, IP, 인증서 경로는 저장소에 커밋하지 않고 서버에서 치환합니다.

적용 후 아래로 확인합니다.

```bash
sudo nginx -t && sudo systemctl reload nginx
curl -i -o /dev/null -w '%{http_code}
'   -H 'Connection: Upgrade' -H 'Upgrade: websocket'   -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ=='   -H 'Origin: https://<DEV_DOMAIN>' https://<DEV_DOMAIN>/ws
```

`101`이면 정상입니다. `400`이면 Upgrade header가 전달되지 않은 것이고, `403`이면 `CORS_ALLOWED_ORIGINS`에 해당 origin이 없는 것입니다.

### dev 서버 환경변수 예시

`infra/env/.env.dev.example`은 커밋 가능한 예시 파일입니다. 실제 서버 `.env`는 Oracle VM에서 서버 관리자가 직접 생성하고 repository에 커밋하지 않습니다.

포함 항목:

```text
SPRING_PROFILES_ACTIVE=dev
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD
DB_URL
DB_USERNAME
DB_PASSWORD
CORS_ALLOWED_ORIGINS
SERVER_PORT
```

예시 파일의 `CHANGE_ME` 값은 실제 서버에서 반드시 별도 값으로 교체합니다. 실제 IP, 실제 도메인, 실제 DB 계정, 실제 비밀번호, API Key, Secret은 repository에 기록하지 않습니다.

### 배포 산출물 기준

backend 산출물:

```text
backend/build/libs/*.jar -> /home/ubuntu/meet-or-solo/backend/app.jar
```

frontend 산출물:

```text
frontend/dist -> /home/ubuntu/meet-or-solo/frontend/dist
```

기준:

- `backend/build/`는 build 결과물이므로 Git에 커밋하지 않습니다.
- `frontend/dist/`는 build 결과물이므로 Git에 커밋하지 않습니다.
- 산출물은 8단계 CI/CD 또는 수동 배포 시 Oracle VM으로 복사합니다.
- 이번 단계에서는 실제 서버 복사나 compose 실행을 하지 않습니다.

### 실제 Oracle VM에서 수행할 작업 후보

실제 서버 작업은 이 문서 기준을 확인한 뒤 별도 승인과 실제 서버 접근 권한이 있을 때 수행합니다.

1. Oracle VM에 `/home/ubuntu/meet-or-solo` 기준 디렉터리를 준비합니다.
2. 실제 서버 `.env`를 생성하고 Secret 값을 서버에만 기록합니다.
3. backend jar 산출물을 `backend/app.jar`로 배치합니다.
4. frontend build 산출물을 `frontend/dist`로 배치합니다.
5. `infra/docker/docker-compose.dev.yml`과 `infra/nginx/default.dev.conf`를 기준으로 dev compose를 실행합니다.
6. 외부에서는 nginx dev host port `18080`만 접근 가능하게 하고, backend `8080`과 PostgreSQL `5432`는 공개하지 않습니다.
7. DB 직접 확인은 `ssh -L 15432:localhost:15432 <SSH_USER>@<DEV_SERVER_HOST>` 방식의 SSH tunnel을 사용합니다.
7. `/api/health`가 nginx를 통해 backend로 proxy되는지 확인합니다.

## prod 배포 구성

dev 배포를 검증한 뒤 같은 VM에 `prod`를 분리해 추가했습니다. 준비한 파일은 다음과 같습니다.

| 파일 | 역할 |
| --- | --- |
| `infra/docker/docker-compose.prod.yml` | 운영 3-service(postgres/backend/nginx). project·컨테이너명·network·포트·데이터 경로를 dev와 분리 |
| `infra/nginx/default.prod.conf` | compose 내부 nginx. 정적 서빙, `/api`·`/ws` proxy, 업로드 상한 |
| `infra/nginx/host-https.prod.conf.example` | 운영 도메인 TLS를 끊는 호스트 nginx 예시. Basic Auth 초기 접근 제한 포함 |
| `infra/env/.env.prod.example` | 운영 환경변수 템플릿 |
| `scripts/backup-prod-db.sh` | 운영 DB 백업(`pg_dump -Fc`), 보관 기간 정리 |
| `scripts/restore-prod-db.sh` | 복원 검증(기본)과 실제 복원(`--target-prod`) |
| `scripts/logs.sh` | 운영 컨테이너 로그 조회(조회 전용) |

기준:

- `postgres`는 dev와 같은 `pgvector/pgvector:pg16`을 씁니다. `V11`이 `vector` extension을 요구합니다.
- 운영 DB는 host 포트를 publish하지 않습니다. 관리자 접속은 컨테이너 내부 `psql` 또는 임시 loopback publish + SSH tunnel을 씁니다.
- compose nginx는 `127.0.0.1:28080`에만 붙습니다. TLS는 호스트 nginx가 처리합니다.
- 세 service 모두 `mem_limit`과 `json-file` 로그 제한(`max-size 10m`, `max-file 3`)을 둡니다. 수치는 실부하 측정 전 초기값입니다.
- 축제·관광지 동기화는 `application-prod.yml`에서 기본 꺼져 있고 환경변수로만 켭니다.
- GPS 반경 검증 우회는 운영에서 `application-prod.yml`이 `false`로 고정합니다. 환경변수로 켤 수 없습니다.

운영 CD는 `main` 기준 수동 배포를 한 번 성공한 뒤 `.github/workflows/deploy-prod.yml`로 자동화했습니다. 아래 '운영 CD' 절을 참고합니다.

운영 요청 경로:

```text
브라우저
  └─ https://<PROD_DOMAIN>            호스트 nginx (80/443, Let's Encrypt)
       ├─ /ws   ─┐
       ├─ /api  ─┤
       └─ /     ─┴─> 127.0.0.1:28080  prod compose nginx
                          ├─ /api, /ws -> backend:8080 (host publish 없음)
                          └─ /         -> frontend/dist 정적 서빙
                     backend -> postgres:5432 (host publish 없음)
```

dev도 같은 호스트 nginx가 개발 도메인을 `127.0.0.1:18080`으로 넘깁니다. 운영과 개발은 **호스트 nginx의 server block 단위로 갈립니다.**

## 운영 수동 배포 절차

`main` 브랜치 기준입니다. 작업 브랜치 → `dev` 검증 → `main` 병합 → 이 절차 순서로 진행합니다.

### 1. 서버 준비 (최초 1회)

**폴더**

```bash
mkdir -p /home/ubuntu/meet-or-solo-prod/{backend,frontend,infra/docker,infra/nginx,data/postgres,releases}
cd /home/ubuntu/meet-or-solo-prod
```

dev(`/home/ubuntu/meet-or-solo`)와 다른 폴더여야 합니다. compose가 데이터 경로를 `../../data/postgres` 상대경로로 잡으므로 폴더를 나누면 DB 파일이 자동으로 분리됩니다.

**환경변수 파일**

```bash
cp infra/env/.env.prod.example .env
vi .env          # placeholder를 실제 값으로 교체
chmod 600 .env
```

`POSTGRES_PASSWORD`/`DB_PASSWORD`, `JWT_SECRET`, `PROFILE_ENCRYPTION_KEY`, `ADMIN_REPORT_CURSOR_HMAC_SECRET`, `ADMIN_LOCAL_PASSWORD`, `WEB_PUSH_VAPID_*`는 dev와 다른 값으로 새로 만듭니다(docs/06). `PROFILE_ENCRYPTION_KEY`는 데이터를 쓰기 시작하면 교체할 수 없습니다.

**산출물 배치**

```bash
# backend (JDK 17)
cd backend && ./gradlew bootJar -x test

# frontend
cd frontend
cp .env.production.example .env.production   # 값 채우기
npm ci && npm run build
```

`VITE_*`는 **빌드 시점에 번들로 박힙니다.** 서버 `.env`에 넣어도 반영되지 않습니다. 값을 바꿨으면 반드시 다시 빌드해서 `dist`를 다시 올립니다.

```text
build/libs/<app>.jar                 -> /home/ubuntu/meet-or-solo-prod/backend/app.jar
frontend/dist/                       -> /home/ubuntu/meet-or-solo-prod/frontend/dist/
infra/docker/docker-compose.prod.yml -> 같은 경로
infra/nginx/default.prod.conf        -> 같은 경로
scripts/backup-prod-db.sh            -> 같은 경로
scripts/restore-prod-db.sh           -> 같은 경로
scripts/logs.sh                      -> 같은 경로
```

```bash
chmod +x scripts/*.sh
```

**호스트 nginx**

`infra/nginx/host-https.prod.conf.example`의 placeholder를 채워 `/etc/nginx/sites-available/meet-or-solo-prod`에 두고 `sites-enabled/`로 symlink합니다.

```bash
sudo nginx -t && sudo systemctl reload nginx
```

`sites-enabled/`에 운영 도메인 이름을 딴 파일이 이미 있더라도 내용이 개발용일 수 있습니다. 파일명이 아니라 `server_name`과 `proxy_pass` 값을 보고 판단합니다.

### 2. 기동

```bash
cd /home/ubuntu/meet-or-solo-prod
docker compose --env-file .env -f infra/docker/docker-compose.prod.yml up -d
docker compose --env-file .env -f infra/docker/docker-compose.prod.yml ps
```

`postgres`가 healthy가 된 뒤 `backend`가 뜨고, `backend`가 healthy가 된 뒤 `nginx`가 뜹니다. 첫 기동은 Flyway migration 때문에 느립니다(healthcheck `start_period` 90초).

### 3. 정상 확인 — `/api/health`만으로 판단하지 않는다

**`/api/health`가 증명하는 것**

```bash
curl -s http://127.0.0.1:28080/api/health
# {"success":true,"data":{"status":"OK","service":"meet-or-solo-backend"},"error":null}
```

이 endpoint는 **고정 문자열을 돌려주는 controller**입니다. DB 연결도, Flyway 상태도 보지 않습니다. **DB가 죽어 있어도 `OK`가 나옵니다.** 아래를 반드시 함께 확인합니다.

**Flyway migration 성공 확인**

```bash
docker logs meet-or-solo-backend-prod 2>&1 | grep -i flyway
docker exec meet-or-solo-postgres-prod sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "
     select version, description, success
     from flyway_schema_history
     order by installed_rank desc limit 10;"'
```

- `success = false`인 행이 하나라도 있으면 실패입니다. 그 상태로 서비스하지 않습니다.
- 저장소 최신 migration은 `V40__add_push_subscriptions.sql`입니다. 마지막 행 버전을 확인합니다.

**DB 연결을 실제로 타는 요청**

```bash
curl -s "http://127.0.0.1:28080/api/festivals?page=0&size=1" | head -c 300
```

빈 DB라 목록은 비어 있는 것이 정상입니다. **응답이 오는 것 자체**가 DB 연결 확인입니다.

**기동 로그**

```bash
docker logs meet-or-solo-backend-prod --tail=300
```

`The following 1 profile is active: "prod"`, 매칭 점수 가중치 오류 없음(합이 1이 아니면 기동 실패), `ADMIN_REPORT_CURSOR_HMAC_SECRET` 관련 실패 없음, 슈퍼관리자 계정 생성 로그를 확인합니다.

**WebSocket**

```bash
curl -i -o /dev/null -w '%{http_code}\n' \
  -H 'Connection: Upgrade' -H 'Upgrade: websocket' \
  -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' \
  -H 'Origin: https://<PROD_DOMAIN>' https://<PROD_DOMAIN>/ws
```

`101` 정상, `400`이면 어느 한쪽 nginx에서 `Upgrade` header가 빠진 것, `403`이면 `CORS_ALLOWED_ORIGINS`에 그 origin이 없는 것입니다.

### 4. 초기 데이터 적재와 정기 동기화

운영 DB는 빈 상태로 시작합니다. dev DB를 복사하지 않으므로 **축제·관광지 데이터도 비어 있습니다.** 그 상태로는 체크인도 솔로 코스도 쓸 수 없습니다.

동기화는 `application-prod.yml`에서 기본 꺼져 있고 환경변수로만 켭니다.

**초기 적재 (1회)**

```bash
# .env
FESTIVAL_SYNC_ENABLED=true
TOUR_PLACE_SYNC_ENABLED=true

docker compose --env-file .env -f infra/docker/docker-compose.prod.yml up -d --force-recreate backend
```

기동 후 축제는 약 30초, 관광지는 약 60초 뒤 시작합니다(`*_INITIAL_DELAY`).

```bash
docker logs -f meet-or-solo-backend-prod | grep -i -E "festival sync|tour place sync"

docker exec meet-or-solo-postgres-prod sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "
     select (select count(*) from festivals)   as festivals,
            (select count(*) from tour_places) as tour_places;"'
```

건수가 더 이상 늘지 않으면 적재가 끝난 것입니다. 끝나면 두 값을 `false`로 되돌리고 다시 `--force-recreate`합니다.

**정기 동기화를 상시로 켤지**

| 판단 요소 | 내용 |
| --- | --- |
| TourAPI 일일 호출 한도 | dev와 운영이 같은 서비스 키를 쓰면 한도를 나눠 씁니다. 양쪽을 상시로 켜면 두 배로 소모합니다 |
| 소유자 고정 | dev 기준 문서에 "축제 Scheduler 소유자는 dev backend 한 인스턴스로 고정한다"고 적혀 있습니다. 운영을 상시로 켜려면 그 방침을 함께 갱신해야 합니다 |
| 주기 | 축제 6시간, 관광지 12시간이 기본값입니다 |

권장은 **운영을 데이터 소유자로 옮기고 dev 쪽을 끄는 방향**이지만 dev compose 변경을 수반하므로 별도 회차로 처리합니다. 그 전까지는 필요할 때 위 초기 적재를 반복하는 수동 운영으로 둡니다.

### 5. 운영 점검 항목

**GPS 반경 검증 우회가 꺼져 있는지**

운영에서 가장 위험한 항목입니다. dev compose는 체크인 우회 기본값이 `true`라서 그 줄을 복사해 오면 운영 위치 검증이 통째로 꺼집니다. 방어는 두 겹입니다.

1. `docker-compose.prod.yml`에 우회 환경변수를 넣지 않았습니다.
2. `application-prod.yml`이 `bypass-radius-check: false`를 **고정**합니다. `.env`에 써 넣어도 켜지지 않습니다.

```bash
docker exec meet-or-solo-backend-prod env | grep -i BYPASS || echo "우회 환경변수 없음(정상)"
```

현장에 가지 않고 확인해야 하면 `/admin/members`에서 그 계정을 테스트 계정으로 지정합니다(`members.test_account`).

**secure cookie**

```bash
docker exec meet-or-solo-backend-prod env | grep AUTH_COOKIE_SECURE   # true
```

로그인 후 `access_token` cookie에 `Secure`, `HttpOnly`, `SameSite=Lax`가 붙는지 확인합니다.

**이미지 업로드 크기**

nginx가 둘이라 **둘 중 작은 쪽이 실제 상한**입니다.

| 위치 | 값 |
| --- | --- |
| 호스트 nginx | `client_max_body_size 6m` |
| compose nginx (`default.prod.conf`) | `client_max_body_size 6m` |
| 앱 (`PROFILE_IMAGE_MAX_SIZE`) | `5MB` |

하나만 올리면 나머지에서 막힙니다. nginx에서 막히면 **413**이 나고 앱의 오류 처리에 닿지도 않습니다. 실제 5MB 근처 이미지로 한 번 업로드해 봅니다.

**HTTPS 전달 헤더**

- 호스트 nginx가 `X-Forwarded-Proto $scheme`를 붙입니다.
- compose nginx는 `map`으로 앞단이 준 값을 그대로 넘깁니다(`$forwarded_proto`). dev처럼 `$scheme`을 다시 쓰면 backend에는 항상 `http`가 전달됩니다.
- `application-prod.yml`의 `server.forward-headers-strategy: framework`가 이 header를 해석합니다. backend 8080이 host에 publish되지 않아 header를 위조할 외부 경로가 없습니다.

**WebSocket 양쪽 설정**

| 항목 | 호스트 nginx | compose nginx |
| --- | --- | --- |
| `/ws`를 `location /`보다 먼저 | 필요 | 필요 |
| `Upgrade`/`Connection` header | 필요 | 필요 |
| `proxy_read_timeout 3600s` | 필요 | 필요 |

dev에서는 compose 쪽에 timeout이 없었습니다. 짧은 쪽이 유휴 STOMP 연결을 끊습니다.

**환경변수·스케줄러 누락**

기본값이 `false`라 넘기지 않으면 조용히 꺼지는 것들입니다.

```bash
docker exec meet-or-solo-backend-prod env | grep -E \
  "MATCHING_SCHEDULER_ENABLED|MATCHING_NO_SHOW|MANNER_TEMPERATURE_RECOVERY|INQUIRY_RETENTION|ADMIN_MEMBER_SUSPENSION"
```

다섯 개가 모두 `true`여야 합니다.

- `MATCHING_SCHEDULER_ENABLED` — 제안 timeout(30초), 배치 매칭
- `MATCHING_NO_SHOW_SCHEDULER_ENABLED` — 노쇼 처리
- `MANNER_TEMPERATURE_RECOVERY_SCHEDULER_ENABLED` — 30도 매칭 제한 해제 경로
- `INQUIRY_RETENTION_SCHEDULER_ENABLED` — 문의 보관 기간 경과분 익명화
- `ADMIN_MEMBER_SUSPENSION_SCHEDULER_ENABLED` — 정지 만료 자동 복구

`ADMIN_LOCAL_USERNAME`/`ADMIN_LOCAL_PASSWORD`가 비면 슈퍼관리자 계정이 생성되지 않아 `/admin/login`으로 들어갈 수 없습니다.

```bash
docker exec meet-or-solo-postgres-prod sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select count(*) from admin_credentials;"'
```

**Web Push**

Web Push는 이미 구현돼 있습니다(`WebPushSender`, `push_subscriptions`(`V40`), `frontend/src/sw.ts`). 운영에서 할 일은 구현이 아니라 **키 주입과 확인**입니다.

- 키 쌍이 비면 push 발송만 꺼지고 WebSocket 알림과 서버 알림함은 그대로 동작합니다.
- 키 생성은 `npx web-push generate-vapid-keys`. private key는 저장소에 넣지 않습니다.
- 키를 교체하면 기존 구독이 전부 무효가 되어 사용자가 다시 권한을 허용해야 합니다.
- iOS는 홈 화면에 설치한 상태에서만 push가 옵니다(Safari 16.4+).

### 6. 초기 접근 제한

기능이 완성되지 않은 동안 운영 도메인을 열어 두지 않습니다. `host-https.prod.conf.example`에 Basic Auth 예시가 주석으로 들어 있습니다.

```bash
sudo apt-get install -y apache2-utils
sudo htpasswd -c /etc/nginx/.htpasswd-meet-or-solo-prod <USERNAME>
sudo chmod 640 /etc/nginx/.htpasswd-meet-or-solo-prod
sudo chown root:www-data /etc/nginx/.htpasswd-meet-or-solo-prod
# 설정에서 auth_basic 두 줄의 주석을 푼 뒤
sudo nginx -t && sudo systemctl reload nginx
```

`X-Robots-Tag: noindex, nofollow`는 이미 켜 두었습니다. 검색 노출만 막는 장치이고 접근 제한의 대체가 아닙니다.

저장소에서 판단할 수 없어 **실서버에서 확인해야 하는 항목**입니다.

| 항목 | 확인할 것 |
| --- | --- |
| OAuth | Kakao·Naver 로그인 시작과 콜백이 Basic Auth 뒤에서 끝까지 되는지 |
| PWA 설치 | 홈 화면 추가와 Service Worker 등록이 인증 뒤에서 되는지 |
| Web Push | 구독 등록과 실제 push 수신 |
| ACME | `/.well-known/acme-challenge/`가 인증 없이 열려 인증서 갱신이 되는지 |

제한을 켠 상태의 검증 결과를 "공개 상태에서도 된다"로 읽으면 안 됩니다. 공개 전환 시 같은 항목을 다시 확인합니다.

### 7. 운영 DB 접속

운영 DB는 host 포트를 publish하지 않습니다. compose 파일의 `ports`가 주석 처리돼 있습니다.

**평소 (권장)**

```bash
docker exec -it meet-or-solo-postgres-prod sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

**GUI 도구가 꼭 필요할 때 (임시)**

1. `docker-compose.prod.yml`의 `ports` 주석을 풉니다(`127.0.0.1:25432:5432`). `0.0.0.0`이 아니라 **loopback 바인딩**입니다.
2. `docker compose ... up -d postgres`
3. 로컬 PC에서 SSH 터널을 엽니다.

   ```bash
   ssh -L 25432:127.0.0.1:25432 <SSH_USER>@<PROD_SERVER_HOST>
   ```

4. **작업이 끝나면 주석을 다시 닫고 되돌립니다.**

Oracle Cloud Ingress에 `25432`를 열지 않습니다.

### 8. 백업 · 외부 보관 · 복원 검증

**백업**

```bash
cd /home/ubuntu/meet-or-solo-prod
./scripts/backup-prod-db.sh
```

- 출력: `/home/ubuntu/backups/meet-or-solo-prod/meet_or_solo_prod-<타임스탬프>.dump`
- 형식은 `pg_dump -Fc`. 덤프 직후 `pg_restore --list`로 목록(TOC)을 읽을 수 있는지 확인합니다.
- 7일 경과분을 지웁니다(`PROD_BACKUP_RETENTION_DAYS`로 조정).
- 스크립트는 비밀번호를 알 필요가 없습니다. 계정과 DB 이름을 컨테이너 환경변수에서 읽습니다.

> ⚠ **`pg_restore --list`는 무결성 검증이 아닙니다.**
> 아카이브 헤더와 목차만 읽습니다. 뒤쪽 데이터 블록이 잘려 있거나 깨져 있어도 목록은
> 정상으로 나올 수 있습니다. "파일이 덤프 형식이 맞는가"를 보는 1차 관문일 뿐입니다.
> 실제로 복원되는지는 아래 **복원 검증**으로 임시 DB에 복원해 봐야 알 수 있습니다.

cron 예시:

```bash
0 4 * * * cd /home/ubuntu/meet-or-solo-prod && ./scripts/backup-prod-db.sh >> /home/ubuntu/backups/backup.log 2>&1
```

**VM 외부 보관**

로컬 디스크에만 두면 VM이 사라질 때 백업도 함께 사라집니다. 최소 한 벌은 VM 밖에 둡니다.

**자동 업로드는 현재 미구성입니다.** 저장소에 둘 수 없는 값이 필요하기 때문입니다 — 원격지 주소, 계정, SSH key 경로(또는 Object Storage bucket과 자격증명). 값이 정해지면 서버 환경에 설정하면 스크립트가 바로 사용합니다.

```bash
export PROD_BACKUP_REMOTE="<USER>@<BACKUP_HOST>:/path/to/backups"
export PROD_BACKUP_SSH_KEY="/home/ubuntu/.ssh/<KEY>"
```

그 전까지는 수동으로 내려받습니다. 주기는 최소 주 1회, 그리고 **배포 직전에 반드시 1회**입니다.

**복원 검증 (기본 동작)**

백업이 있다는 것과 복원이 된다는 것은 다른 말이고, 후자는 해 봐야만 압니다.

```bash
./scripts/restore-prod-db.sh /home/ubuntu/backups/meet-or-solo-prod/<파일>.dump
```

이번 실행만의 임시 DB(`meet_or_solo_restore_check_<타임스탬프>_<PID>`)를 새로 만들어 복원하고, 테이블 수·Flyway 이력·주요 건수를 출력한 뒤 **그 임시 DB만** 지웁니다. 운영 DB는 열지도 않습니다. 최초 1회와 이후 분기마다 수행합니다.

안전장치:

| 상황 | 동작 |
| --- | --- |
| 임시 DB 이름이 운영 DB와 같음 | **어떤 DB 명령도 실행하기 전에 중단.** `PROD_RESTORE_VERIFY_DB_PREFIX`를 운영 DB 이름으로 잘못 설정한 경우가 여기 걸립니다 |
| 그 이름의 DB가 이미 존재 | 기존 DB를 건드리지 않고 중단합니다. 삭제로 시작하지 않습니다 |
| 덤프 목록을 읽을 수 없음 / 파일이 비어 있음 | DB 명령을 전혀 실행하지 않고 중단합니다 |
| 복원 실패 | 임시 DB를 조사용으로 **남기고** 이름을 안내합니다 |
| 정리 | 이번 실행이 직접 만든 DB만 지웁니다(`trap`) |

DB 이름은 컨테이너 안의 셸 문자열에 끼워 넣지 않고 위치 인자로 전달하며, 이름 자체도 `^[a-z_][a-z0-9_]*$`와 63자 한도로 미리 검증합니다.

**실제 복원 (장애 시)**

```bash
docker stop meet-or-solo-backend-prod
./scripts/restore-prod-db.sh <파일>.dump --target-prod   # RESTORE 입력 필요
docker compose --env-file .env -f infra/docker/docker-compose.prod.yml up -d backend
```

운영 DB를 덮어쓰기 전에 **4개 관문**을 순서대로 통과해야 합니다. 하나라도 실패하면 운영 DB를 손대지 않은 상태로 중단합니다.

| 단계 | 내용 | 실패 시 |
| --- | --- | --- |
| ① | 덤프 목록(TOC)을 읽을 수 있는가 | 중단. 운영 DB 미접촉 |
| ② | **임시 DB에 실제로 복원되는가** | 중단. 운영 DB 미접촉 |
| ③ | **현재 운영 DB의 사전 백업이 성공했는가** | 중단. 운영 DB 미접촉 |
| ④ | drop → create → restore | 아래 복구 절차 |

③의 사전 백업은 `<백업 디렉터리>/pre-restore-<타임스탬프>.dump`로 저장되고, 이것도 TOC를 읽어 확인한 뒤에야 ④로 넘어갑니다. **사전 백업 없이는 교체하지 않습니다.** 사전 백업이 없으면 되돌릴 수단이 사라지기 때문입니다.

④ 중간에 실패했을 때의 복구:

```bash
# create 실패 → 운영 DB가 없는 상태
docker exec meet-or-solo-postgres-prod sh -c 'createdb -U "$POSTGRES_USER" -- meet_or_solo_prod'
./scripts/restore-prod-db.sh <pre-restore 파일>.dump --target-prod

# restore 중간 실패 → DB가 불완전한 상태
./scripts/restore-prod-db.sh <pre-restore 파일>.dump --target-prod
```

스크립트가 실패 시 사전 백업 파일 경로와 위 명령을 그대로 출력합니다. **되돌리기 전에 backend를 다시 올리지 않습니다.**

backend가 DB에 붙어 있으면 drop이 실패하므로 먼저 멈춥니다. 복원 후 3절 확인을 다시 수행합니다.

**안전장치 테스트**

```bash
./scripts/test-restore-prod-db.sh
```

실제 Docker나 PostgreSQL 없이 `docker`를 흉내 내는 mock을 PATH 앞에 두고, **파괴적 명령이 나가면 안 되는 상황에서 나가지 않는지**를 확인합니다(이름 충돌, 기존 DB 보호, 잘못된 덤프, 사전 백업 실패, 확인 입력 불일치 등).

### 9. 이전 버전으로 되돌리기

**배포 전에 할 일**

```bash
cd /home/ubuntu/meet-or-solo-prod
STAMP=$(date +%Y%m%d-%H%M%S)
mkdir -p releases/$STAMP
cp backend/app.jar releases/$STAMP/app.jar
cp -R frontend/dist releases/$STAMP/dist
./scripts/backup-prod-db.sh
```

이것을 하지 않으면 되돌릴 대상이 없습니다.

**backend 되돌리기**

**JAR를 덮어쓰는 것만으로는 교체되지 않습니다.** JAR는 bind mount돼 있고 JVM은 기동 시점에 읽은 것을 계속 씁니다. 또 `docker compose up -d`는 compose 설정이 바뀌지 않으면 컨테이너를 다시 만들지 않습니다.

```bash
cp releases/<되돌릴STAMP>/app.jar backend/app.jar
docker compose --env-file .env -f infra/docker/docker-compose.prod.yml up -d --force-recreate backend
docker compose --env-file .env -f infra/docker/docker-compose.prod.yml ps
```

`--force-recreate`가 있어야 컨테이너가 새로 만들어지고 새 JAR로 기동합니다. `.env`를 고쳤을 때도 마찬가지입니다 — 환경변수는 **컨테이너 생성 시점**에 주입되므로 `docker compose restart`로는 반영되지 않습니다.

**frontend 되돌리기**

```bash
rm -rf frontend/dist
cp -R releases/<되돌릴STAMP>/dist frontend/dist
```

nginx는 정적 파일을 요청마다 읽으므로 재시작하지 않아도 반영됩니다. 다만 브라우저와 Service Worker 캐시 때문에 사용자 화면에는 바로 보이지 않을 수 있습니다. `default.prod.conf`가 `sw.js`와 `index.html`에 `no-cache`를 붙여 두었고, `registerType: 'autoUpdate'` + `skipWaiting()`이라 새 Service Worker는 즉시 활성화되지만, 이미 열려 있던 탭은 새로고침이 필요할 수 있습니다.

**되돌릴 수 없는 것 — DB migration**

**Flyway migration은 앱을 되돌려도 되돌아가지 않습니다.** 새 버전이 스키마를 바꿨다면 앱만 내렸을 때 "이전 JAR + 새 스키마" 조합이 되고, `ddl-auto: validate`라서 **기동 자체가 실패할 수 있습니다.**

| 상황 | 대응 |
| --- | --- |
| 새 버전에 migration이 없었다 | 앱만 되돌리면 안전합니다 |
| 컬럼·테이블 **추가**만 했다 | 대체로 이전 JAR도 뜹니다. 검증이 필요합니다 |
| 컬럼·테이블을 **바꾸거나 지웠다** | 앱만 되돌릴 수 없습니다. 배포 전 백업으로 DB까지 함께 되돌립니다. 그 사이 쌓인 운영 데이터는 사라집니다 |

그래서 **배포 직전 백업이 롤백 계획의 핵심**입니다. 스키마를 바꾸는 배포는 되돌리기가 훨씬 비싸다는 점을 배포 전에 인지합니다.

### 10. 자원과 로그 정책

| service | mem_limit | cpus | JVM |
| --- | --- | --- | --- |
| backend | 1500m | 1.5 | `-Xms256m -Xmx768m` |
| postgres | 1g | 제한 없음 | — |
| nginx | 128m | 제한 없음 | — |

**이 수치는 실부하 측정 전 초기값입니다.** 논리 CPU 2개, RAM 약 12GiB에서 dev·병원·study가 이미 상주한다는 조건으로 잡았습니다. 실제 트래픽을 받은 뒤 조정합니다.

```bash
docker stats --no-stream meet-or-solo-backend-prod meet-or-solo-postgres-prod meet-or-solo-nginx-prod
```

- backend가 상한에 붙어 OOM으로 재시작되면 `mem_limit`과 `-Xmx`를 **함께** 올립니다. `-Xmx`만 올리면 컨테이너가 죽고, `mem_limit`만 올리면 JVM이 쓰지 않습니다.
- **dev compose에는 자원 제한이 없습니다.** 운영을 보호하려면 dev에도 상한을 거는 편이 좋지만 dev 재기동을 수반하므로 별도 작업으로 둡니다.

**로그**

세 컨테이너 모두 `json-file` 드라이버에 `max-size: 10m`, `max-file: 3`을 걸었습니다. 컨테이너당 최대 30MB에서 자동 회전합니다.

운영 compose는 `logs/` 디렉터리를 **마운트하지 않습니다.**

- dev는 `logs/backend`를 마운트했지만 Spring에 `logging.file.name`이 없어 그 디렉터리는 비어 있었습니다.
- `logs/nginx`를 마운트하면 nginx가 stdout 대신 실제 파일에 쓰는데, 호스트 logrotate는 `/var/log/nginx/*.log`만 다루므로 그 파일들은 회전되지 않습니다.
- 마운트하지 않으면 두 로그가 모두 Docker json-file로 가고 위 제한이 그대로 적용됩니다. 별도 logrotate 설정이 필요 없습니다.

로그 조회는 `scripts/logs.sh`를 씁니다. 컨테이너 이름을 외우지 않아도 되고, 실패했을 때
원인(도커 접근 불가 / 컨테이너 미생성 / 중지 상태)을 구분해 알려줍니다.

```bash
cd /home/ubuntu/meet-or-solo-prod
./scripts/logs.sh backend               # 최근 100줄 + 실시간 추적
./scripts/logs.sh nginx --no-follow     # 최근 100줄만
PROD_LOGS_TAIL=300 ./scripts/logs.sh postgres --no-follow
```

- `backend` / `nginx` / `postgres`를 각각 `meet-or-solo-<서비스>-prod`로 찾아갑니다.
- 기본은 최근 100줄을 타임스탬프와 함께 보여준 뒤 실시간으로 따라갑니다. 줄 수는
  `PROD_LOGS_TAIL`로 조정합니다.
- **Ctrl+C는 조회만 끝냅니다.** `docker logs -f`는 컨테이너 밖에서 스트림을 읽는
  클라이언트라 중단해도 컨테이너 안 프로세스에는 신호가 가지 않습니다.
- 이 스크립트는 **조회 전용**입니다. `docker info`, `docker ps`, `docker logs`만 실행하고
  컨테이너를 만들거나 시작·재시작·중지하지 않습니다.
- 종료 코드: `0` 정상, `1` 사용법 오류, `2` docker 접근 실패, `3` 컨테이너 미생성,
  `130` Ctrl+C.

원시 명령이 필요하면 아래를 직접 씁니다.

```bash
docker logs meet-or-solo-backend-prod --tail=300
docker logs meet-or-solo-nginx-prod --tail=100
```

나중에 접근 로그를 파일로 분석해야 하면 그때 마운트와 logrotate를 함께 추가합니다.

### 11. 배포 후 최종 확인 목록

| # | 항목 | 절 |
| --- | --- | --- |
| 1 | 컨테이너 3개가 healthy | 2 |
| 2 | Flyway `success = true`, 마지막 버전 확인 | 3 |
| 3 | `/api/festivals` 응답 | 3 |
| 4 | WebSocket 101 | 3 |
| 5 | GPS 우회 환경변수 없음 | 5 |
| 6 | `AUTH_COOKIE_SECURE=true`, cookie에 Secure | 5 |
| 7 | 5MB 근처 이미지 업로드 성공 | 5 |
| 8 | 스케줄러 5개 모두 `true` | 5 |
| 9 | `admin_credentials` 1건 이상 | 5 |
| 10 | 백업 1회 + 복원 검증 1회 | 8 |
| 11 | 배포 전 `releases/<STAMP>` 보관 | 9 |
| 12 | Basic Auth 상태에서 OAuth·PWA·Push 동작 | 6 |

## 운영 CD (GitHub Actions)

`.github/workflows/deploy-prod.yml`. 첫 수동 배포가 성공한 뒤 그 절차를 그대로 옮겼습니다.

### 트리거

```text
push to main
workflow_dispatch
```

`dev` → `main` 병합이 곧 운영 배포입니다. 병합 자체가 의도된 행위라 자동으로 둡니다.
수동 재실행이 필요하면 Actions 화면에서 `workflow_dispatch`로 다시 돌립니다.

`concurrency: deploy-prod`로 동시 실행을 막습니다. 진행 중인 배포를 취소하지는 않습니다 —
도중에 끊기면 운영이 반쯤 갱신된 상태로 남습니다.

### 필요한 GitHub Secrets

```text
PROD_SERVER_HOST
PROD_SERVER_USER
PROD_SSH_KEY
PROD_DEPLOY_PATH
PROD_VITE_KAKAO_MAPS_APP_KEY
PROD_VITE_SUPPORT_CONTACT_EMAIL
```

dev와 값이 같더라도 이름을 나눠 둡니다. Kakao 지도 키를 운영 전용으로 분리할 때
Secret 값만 바꾸면 되고, 잘 돌고 있는 dev CD를 건드리지 않아도 됩니다.

⚠ SSH 키를 교체하면 `DEV_SSH_KEY`와 `PROD_SSH_KEY`를 **둘 다** 갱신해야 합니다.

### 동작 순서

| 단계 | 내용 |
| --- | --- |
| 1 | backend `bootJar -x test`, frontend `npm ci && npm run build` |
| 2 | 번들 검증 — `dist`에 `localhost` 흔적이 없는지, PWA 산출물이 있는지 |
| 3 | 배포 패키지 생성 |
| 4 | 업로드 |
| 5 | **배포 전 보호** — `releases/<타임스탬프>` 스냅샷 + DB 백업 |
| 6 | 배포 — `up -d` 후 `backend`만 `--force-recreate` |
| 7 | 검증 — healthy 대기, DB 읽는 API 호출, Flyway 실패 0건 |
| 8 | 실패 시 롤백 방법을 로그에 출력 |

### 서버 `.env`는 건드리지 않습니다

배포 패키지에 넣지 않고, 서버에 없으면 배포를 중단합니다. 운영 비밀번호·키는 서버에만
있고 workflow는 그 값을 모릅니다. GitHub Secrets는 **서버 접속 정보와 빌드용 `VITE_` 값**만
가집니다.

### 배포 전 보호 장치

자동 배포는 실수로 병합해도 그대로 나갑니다. 그래서 파괴적 단계 앞에 두 가지를 둡니다.

- `releases/<타임스탬프>/`에 이전 `app.jar`와 `dist` 보관. 3개만 남기고 정리합니다
  (82MB짜리라 쌓이면 디스크를 먹습니다)
- `scripts/backup-prod-db.sh` 실행. postgres 컨테이너가 떠 있을 때만 수행합니다

### 검증이 `/api/health`에서 끝나지 않는 이유

`/api/health`는 고정 문자열을 돌려주는 controller라 DB를 보지 않습니다. 여기서 `OK`만 보고
성공 처리하면 **DB가 끊긴 배포를 성공으로 넘기게** 됩니다. 그래서 세 가지를 확인합니다.

1. backend 컨테이너가 `healthy`가 될 때까지 대기 (최대 180초)
2. `GET /api/festivals` — 실제로 DB를 읽는 요청
3. `flyway_schema_history`에 `success = false`가 0건인지

### 자동 롤백을 하지 않는 이유

migration이 이미 적용된 상태에서 앱만 되돌리면 "이전 jar + 새 스키마"가 됩니다.
`ddl-auto: validate`라 entity와 스키마가 어긋나면 기동 자체가 실패합니다. 되돌리는 방법을
로그에 출력하고, 판단은 사람이 합니다. 절차는 '운영 수동 배포 절차' 9절과 같습니다.

## Nginx와 Let's Encrypt

Nginx 책임:

- HTTP를 HTTPS로 redirect
- React `dist` 서빙
- `/api` proxy
- `/ws` WebSocket upgrade proxy
- Certbot ACME challenge 처리

인증서 방향:

```text
Let's Encrypt + Certbot
```

실제 domain이 확정되기 전까지 placeholder를 사용합니다.

## 8-1단계: GitHub Actions CI 초안

8-1단계에서는 자동 배포 없이 GitHub Actions에서 backend와 frontend가 build 되는지만 검증합니다.

CI workflow:

```text
.github/workflows/ci.yml
```

trigger:

- `pull_request` to `dev`
- `pull_request` to `main`
- `push` to `dev`
- `push` to `main`

협업 브랜치 기준:

- `dev`: 기능 개발과 dev 서버 자동 배포 기준 브랜치입니다.
- `main`: 운영 또는 안정 버전 기준 브랜치입니다.
- 팀원은 기능별 작업 브랜치에서 작업하고 PR로 `dev`에 병합합니다.
- `dev`에서 충분히 검증한 뒤 필요한 시점에 `main`으로 병합합니다.

CI job:

| job | 목적 | 실행 명령 |
| --- | --- | --- |
| `backend-build` | backend compile/package 검증 | `./gradlew build -x test` |
| `frontend-build` | frontend TypeScript/Vite build 검증 | `npm ci`, `npm run build` |

backend CI 기준:

- Java 17을 사용합니다.
- Gradle cache를 사용합니다.
- `backend` 디렉터리에서 실행합니다.
- `bootRun`은 실행하지 않습니다.
- PostgreSQL 컨테이너를 띄우지 않습니다.
- Flyway/DB 연결이 필요한 애플리케이션 실행은 하지 않습니다.
- 실제 `.env`나 DB Secret 없이 동작해야 합니다.

frontend CI 기준:

- Node.js 20을 사용합니다.
- npm cache를 사용합니다.
- `frontend` 디렉터리에서 실행합니다.
- `npm ci` 후 `npm run build`를 실행합니다.
- `frontend/dist/`는 build 산출물이므로 Git에 커밋하지 않습니다.

8-1단계에서는 Oracle VM 접속, SSH 배포, `docker compose up`, 서버 `.env` 생성, GitHub Secrets 사용을 하지 않습니다.

## 8-2단계: GitHub Actions dev CD

8-2단계에서는 dev 서버 배포 자동화를 `dev` 브랜치 push 기준으로 작성합니다. 실제 server, domain, Secrets 값은 문서나 repository에 기록하지 않고 placeholder와 GitHub Secrets 이름만 다룹니다.

dev CD workflow:

```text
.github/workflows/deploy-dev.yml
```

trigger:

```text
push to dev
workflow_dispatch
```

`dev` 브랜치에 push하면 `Deploy Dev` workflow가 자동 실행됩니다. `workflow_dispatch`는 GitHub Actions 화면에서 수동 재배포가 필요할 때 사용합니다.

필요한 GitHub Secrets 이름:

```text
DEV_SERVER_HOST
DEV_SERVER_USER
DEV_SSH_KEY
DEV_DEPLOY_PATH
```

실제 Secret 값은 workflow, 문서, repository에 기록하지 않습니다. SSH private key는 `DEV_SSH_KEY` Secret에만 저장합니다.

workflow 동작 흐름:

1. Checkout
2. Java 17 설정
3. backend `bootJar -x test`
4. Node.js 20 설정
5. frontend `npm ci`
6. frontend `npm run build`
7. 배포 패키지 생성
8. GitHub Secrets에서 dev 서버 접속 정보 사용
9. SSH로 `DEV_DEPLOY_PATH` 생성
10. 배포 패키지 업로드
11. dev 서버에서 압축 해제
12. 서버 `.env` 존재 여부 확인
13. `docker compose --env-file .env -f infra/docker/docker-compose.dev.yml up -d --force-recreate`

배포 패키지 포함 항목:

- `backend/app.jar`
- `frontend/dist/`
- `infra/docker/docker-compose.dev.yml`
- `infra/nginx/default.dev.conf`

배포 패키지에 포함하지 않는 항목:

- 서버 `.env`
- 별도 `db/migration/` 디렉터리
- DB password
- SSH private key
- 실제 IP/domain
- API Key
- GitHub Secrets 실제 값

서버 `.env` 기준:

- GitHub Actions는 서버 `.env`를 생성하지 않습니다.
- 서버 관리자가 Oracle VM에서 `DEV_DEPLOY_PATH/.env`를 직접 생성합니다.
- `.env` 값은 `infra/env/.env.dev.example`을 참고하되 실제 값은 서버에만 둡니다.

CD 실행 전 Oracle VM 준비 항목:

1. Docker 설치
2. Docker Compose plugin 설치
3. `DEV_DEPLOY_PATH`로 사용할 디렉터리 결정
4. `DEV_DEPLOY_PATH/.env` 직접 생성
5. dev compose nginx 외부 `18080` 접근 허용
6. backend `8080` 외부 직접 접근 차단
7. PostgreSQL `5432` 외부 직접 접근 차단
8. GitHub Actions에서 접속할 SSH user와 key 준비

실패 시 확인 항목:

- GitHub Secrets 이름이 workflow와 일치하는지 확인합니다.
- `DEV_SERVER_HOST`, `DEV_SERVER_USER`, `DEV_SSH_KEY`, `DEV_DEPLOY_PATH` 값이 GitHub Secrets에 존재하는지 확인합니다.
- Oracle VM의 SSH 접근이 허용되어 있는지 확인합니다.
- `DEV_DEPLOY_PATH/.env`가 서버에 존재하는지 확인합니다.
- 서버에 Docker와 Docker Compose plugin이 설치되어 있는지 확인합니다.
- `frontend/dist`, `backend/app.jar`, dev compose, nginx 설정이 배포 패키지에 포함되었는지 확인합니다.
- `backend/app.jar` 안에 `BOOT-INF/classes/db/migration/V1__init.sql`부터 `V4__create_safety_admin_recommendation_tables.sql`까지 포함되었는지 확인합니다.
- dev compose nginx `18080` 접근이 가능하고 backend `8080`, PostgreSQL `5432`가 외부 공개되지 않았는지 확인합니다.
- 기존 운영 nginx가 host `80`을 사용 중인 VM에서는 운영 nginx를 중지하지 않고 dev compose를 별도 포트로 검증합니다.

backend jar 내부 Flyway migration 확인:

```bash
cd backend
./gradlew clean build -x test
jar tf build/libs/*.jar | grep db/migration
```

dev 서버 재배포 후 backend 로그 확인:

```bash
docker logs meet-or-solo-backend-dev --tail=300
docker logs meet-or-solo-backend-dev 2>&1 | grep -i flyway
docker logs meet-or-solo-backend-dev 2>&1 | grep -i "migrating schema"
```

dev DB 적용 이력 확인:

```sql
select installed_rank, version, description, script, success
from flyway_schema_history
order by installed_rank;
```

```sql
select table_schema, table_name
from information_schema.tables
where table_schema not in ('pg_catalog', 'information_schema')
order by table_schema, table_name;
```

테스트 자동화와 prod 자동 배포는 추후 단계에서 확장합니다. 실제 운영/prod 자동 배포는 현재 범위가 아닙니다.

실제 server IP, domain, SSH Key, Secret 값은 workflow에 직접 쓰지 않습니다.

## 8-3단계: Oracle VM dev 배포 수동 검증 완료

Oracle VM에서 meet-or-solo dev 배포를 수동 검증했습니다. 실제 서버 IP, DB 계정, DB 비밀번호, Secret 값은 문서에 기록하지 않고 `<DEV_SERVER_HOST>` placeholder를 사용합니다.

접속 기준:

```text
dev 서버: http://<DEV_SERVER_HOST>:18080
health API: http://<DEV_SERVER_HOST>:18080/api/health
CORS_ALLOWED_ORIGINS=http://<DEV_SERVER_HOST>:18080
```

컨테이너 상태:

- `postgres`: Healthy
- `backend`: Running
- `nginx`: Started

검증한 health endpoint:

```text
서버 내부: curl http://localhost:18080/api/health
외부 확인: http://<DEV_SERVER_HOST>:18080/api/health
```

응답:

```json
{"success":true,"data":{"status":"OK","service":"meet-or-solo-backend"},"error":null}
```

포트 정책:

- 기존 Ubuntu nginx 또는 다른 서비스가 host `80`을 사용할 수 있으므로 현재 meet-or-solo dev는 host `80`을 사용하지 않습니다.
- meet-or-solo dev 외부 접근은 host `18080`을 기준으로 합니다.
- Oracle Cloud Ingress에서 `18080` 포트가 열려 있어야 합니다.
- backend `8080`과 PostgreSQL `5432`는 외부에 직접 공개하지 않습니다.
- 팀원이 dev DB에 직접 접근해야 하면 PostgreSQL `5432`를 열지 않고 SSH tunnel을 사용합니다.

## 예정 GitHub Secrets

placeholder 이름:

```text
SERVER_HOST
SERVER_USER
SERVER_PORT
SERVER_SSH_KEY
APP_DOMAIN
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
KAKAO_CLIENT_ID
KAKAO_CLIENT_SECRET
TOURISM_API_KEY
VAPID_PUBLIC_KEY
VAPID_PRIVATE_KEY
```

실제 값은 GitHub Secrets에만 설정하고 repository file에는 넣지 않습니다.

## 포트 노출

운영 공개:

- `80`
- `443`
- 제한된 `22`

운영 private/internal only:

- `5432` PostgreSQL
- `8080` backend

Docker network를 사용한다면 backend와 DB는 internal network 또는 localhost 경계 안에서만 접근 가능해야 합니다.

## GHCR 확장 가능성

추후 GitHub Container Registry를 사용할 수 있습니다.

```text
GitHub Actions -> GHCR image -> server docker compose pull -> restart
```

이는 확장안이며 현재 범위가 아닙니다.

## 백업과 운영

추후 운영에는 다음이 필요합니다.

- PostgreSQL backup job
- log rotation
- uptime monitoring
- certificate renewal check
- health endpoint monitoring

배포 방식이 승인되기 전까지 운영 자동화는 추가하지 않습니다.
