# 개발환경

## 목표 기술 스택

| 영역 | 도구 |
| --- | --- |
| Backend language | Java 17 |
| Backend framework | Spring Boot 3.x |
| Frontend runtime | Node.js LTS |
| Frontend framework | React + TypeScript |
| Frontend build | Vite |
| PWA | Vite PWA plugin 또는 동등한 구성 |
| Database | PostgreSQL |
| Migration | Flyway |
| Local DB runtime | Docker Compose |

현재 `backend`에 복사된 신규 Spring Boot 프로젝트의 버전은 구현 단계에서 확인하고 정렬합니다. 프로젝트 방향은 별도 결정이 없는 한 Java 17과 Spring Boot 3.x를 기준으로 합니다.

## MVP 1단계 로컬 서비스

1단계 로컬 컨테이너는 PostgreSQL만 둡니다.

Redis는 1단계 `docker-compose`에 추가하지 않습니다.

예정 서비스:

```text
postgres:
  image: postgres
  port: 5432
```

로컬 개발에서는 `5432`를 열 수 있지만, 운영에서는 PostgreSQL을 외부에 공개하지 않습니다.

## local/prod Docker Compose 차이

예정 방향:

- `docker-compose.yml` 또는 `docker-compose.local.yml`: 로컬 PostgreSQL과 개발 편의 구성
- `docker-compose.prod.yml`: 운영 서비스와 내부 네트워크 구성

1단계에서는 최소 구성만 작성합니다. 환경 검증에 필요하지 않은 서비스는 추가하지 않습니다.

## 로컬 실행 순서 예정

1단계에서 목표로 하는 로컬 실행 흐름:

1. PostgreSQL 시작
2. backend 실행
3. Flyway migration 실행 확인
4. `/api/health` 확인
5. frontend 실행
6. React에서 `/api/health` 호출 확인

예정 명령어:

```bash
docker compose up -d
cd backend
./gradlew bootRun
cd ../frontend
npm run dev
```

위 명령은 구현 후 확정합니다. 현재 저장소가 이미 모든 명령을 지원한다는 의미는 아닙니다.

## backend 실행 방향

예정 profile:

- `local`: 로컬 PostgreSQL, 로컬 CORS, 개발 로그
- `prod`: 환경변수 기반 DB 설정, 제한된 CORS, 보안 헤더

예정 health endpoint:

```text
GET /api/health
```

예정 응답:

```json
{
  "status": "ok",
  "service": "meet-or-solo-backend"
}
```

## frontend 실행 방향

예정 script:

```text
npm run dev
npm run build
npm run preview
```

개발 환경:

- Vite dev server가 React를 서빙한다.
- API base URL은 로컬 backend를 가리킨다.

운영 환경:

- `npm run build`로 `dist`를 만든다.
- Nginx가 `dist`를 정적 파일로 서빙한다.

## Flyway 방향

Flyway는 초기부터 사용합니다.

예정 위치:

```text
db/migration/
```

또는 Spring Boot classpath migration 위치를 사용할 수 있습니다. 실제 구현 전 어떤 방식을 사용할지 문서에 명시해야 합니다.

1단계 초기 migration은 최소화합니다. 비즈니스 테이블을 과도하게 만들지 않습니다.

## `/api/health` 확인 방법 예정

로컬 확인:

```bash
curl http://localhost:8080/api/health
```

frontend는 1단계에서 개발환경 검증 용도로 `/api/health` 결과를 표시하거나 로그로 확인합니다. 이는 비즈니스 기능이 아닙니다.

## Secret 관리

placeholder와 환경변수만 사용합니다.

커밋 금지 항목:

- API Key
- 비밀번호
- SSH private key
- 실제 운영 도메인
- 실제 서버 IP
- OAuth client secret
