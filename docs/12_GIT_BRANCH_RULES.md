# Git 브랜치 규칙

이 문서는 `meet-or-solo` 개발기간 동안 사용할 Git 브랜치 생성, 작업, PR 기준을 정리합니다.

## 1. 기본 브랜치 역할

### main

`main`은 운영 또는 제출 안정 버전 기준 브랜치입니다.

개발기간에는 일반 기능 작업을 `main`으로 직접 병합하지 않습니다. `main`은 추후 운영 배포, 제출본 고정, 안정 버전 태깅이 필요할 때 사용합니다.

현재 개발기간의 기본 병합 대상은 main이 아니라 dev입니다.

### dev

`dev`는 개발 통합 브랜치이자 개발계 서버 반영 기준 브랜치입니다.

작업 브랜치는 PR을 통해 `dev`로 병합합니다. 개발기간의 기본 흐름은 아래와 같습니다.

```text
작업 브랜치 -> PR -> dev
```

`dev`에 push 또는 PR merge가 발생하면 GitHub Actions의 `Deploy Dev` workflow가 실행되고, 개발계 서버에 자동 배포되는 것을 기준으로 합니다.

### 작업 브랜치

작업 브랜치는 기능, 수정, 문서, 설정 등 하나의 목적을 기준으로 짧게 생성합니다.

작업 브랜치는 `dev`에서 분기하고, 작업 완료 후 PR로 `dev`에 병합합니다.

## 2. 브랜치 이름 형식

브랜치 이름은 아래 형식을 기본으로 합니다.

```text
<type>/wbs-<단계>-<세부단계>-<짧은-설명>
```

예시:

```text
feature/wbs-09-2-flyway-core-tables
feature/wbs-10-a-festival-api
feature/wbs-10-b-auth-profile
fix/wbs-09-2-dev-flyway-location
docs/wbs-08-4-branch-rules
chore/wbs-08-2-deploy-dev-workflow
test/wbs-10-b-auth-token-tests
refactor/wbs-10-a-festival-service
```

## 3. type 선택 기준

### feature

새로운 기능, 화면, API, DB migration, 비즈니스 흐름을 추가할 때 사용합니다.

사용 예시:

```text
feature/wbs-09-2-flyway-core-tables
feature/wbs-10-a-festival-feed
feature/wbs-10-b-kakao-oauth
feature/wbs-10-b-checkin-api
```

`feature`는 사용자나 시스템 동작에 새로운 기능이 추가되는 경우를 기준으로 합니다.

### fix

버그 수정, 배포 실패 수정, 설정 오류 수정, 잘못된 동작을 바로잡을 때 사용합니다.

사용 예시:

```text
fix/wbs-09-2-dev-db-migration
fix/wbs-08-2-deploy-path
fix/wbs-10-a-festival-date-filter
```

`fix`는 기존 의도와 다르게 동작하는 것을 정상 동작으로 되돌리는 경우를 기준으로 합니다.

### docs

문서만 수정할 때 사용합니다.

사용 예시:

```text
docs/wbs-08-4-branch-rules
docs/wbs-09-1-database-design
docs/wbs-10-a-api-notes
```

`README.md`, `AGENTS.md`, `CLAUDE.md`, `docs/*.md`만 수정하는 작업은 `docs`를 우선 사용합니다.

### chore

기능 동작 자체보다는 개발환경, 빌드, 배포, 의존성, 저장소 관리 작업에 사용합니다.

사용 예시:

```text
chore/wbs-08-1-ci-build
chore/wbs-08-2-deploy-dev-workflow
chore/wbs-07-dev-compose
chore/wbs-05-frontend-env-example
```

`chore`는 사용자가 보는 기능 변화보다 프로젝트 운영과 개발 편의에 가까운 작업을 기준으로 합니다.

### refactor

외부 동작은 유지하면서 코드 구조를 개선할 때 사용합니다.

사용 예시:

```text
refactor/wbs-10-a-festival-service
refactor/wbs-10-b-auth-token-package
```

동작이 바뀌거나 새 기능이 추가되면 `refactor`가 아니라 `feature` 또는 `fix`를 사용합니다.

### test

테스트 코드 추가, 테스트 설정, 테스트 보강에 사용합니다.

사용 예시:

```text
test/wbs-10-b-auth-token-tests
test/wbs-10-a-festival-api-tests
test/wbs-10-matching-concurrency
```

기능 코드와 테스트 코드를 함께 작성하는 경우에는 작업의 주 목적에 따라 `feature` 또는 `fix`를 사용해도 됩니다.

## 4. WBS 번호 작성 기준

브랜치명에는 가능한 한 `docs/10_PROGRESS_LOG.md`의 WBS 단계를 반영합니다.

단계가 명확한 경우:

```text
wbs-09-2
wbs-10-a
wbs-10-b
```

세부 단계가 아직 문서에 없지만 현재 WBS 범위 안에서 진행하는 경우:

```text
wbs-10-a-festival-api
wbs-10-b-auth-profile
```

단계가 불명확하면 먼저 `docs/10_PROGRESS_LOG.md`를 확인하고, 필요하면 문서를 갱신한 뒤 브랜치를 생성합니다.

## 5. 짧은 설명 작성 기준

짧은 설명은 영어 소문자와 하이픈을 사용합니다.

권장:

```text
flyway-core-tables
dev-flyway-location
festival-api
auth-profile
branch-rules
```

비권장:

```text
my-work
update
temp
test
final
```

설명은 너무 길게 쓰지 않고, PR 목록에서 작업 목적을 바로 알 수 있을 정도로 작성합니다.

## 6. 개발 흐름

기본 흐름:

```text
dev 최신화
-> 작업 브랜치 생성
-> 작업 및 로컬 확인
-> PR 생성
-> 리뷰 또는 자체 점검
-> dev 병합
-> 개발계 자동 배포 확인
```

개발기간에는 작업 브랜치를 `main`으로 직접 병합하지 않습니다.

## 7. dev 자동 배포 주의사항

`dev`에 병합되면 개발계 서버 자동 배포가 실행됩니다.

따라서 `dev`에 병합하기 전 아래를 확인합니다.

- Secret, 비밀번호, API Key, 실제 서버 IP, 실제 도메인을 커밋하지 않았는지 확인한다.
- Flyway migration 파일은 이미 적용된 버전을 수정하지 않고 새 버전으로 추가한다.
- DB schema 변경이 포함되면 `backend/src/main/resources/db/migration` 파일이 backend jar에 포함되는지 확인한다.
- 문서만 수정한 PR도 `dev` 병합 시 배포 workflow가 실행될 수 있음을 인지한다.
- 개발계 DB 확인은 SSH tunnel 기준 DB에 접속 중인지 확인한다.

## 8. Flyway와 DB 작업 브랜치 기준

DB migration 작업은 `feature` 또는 `fix`를 사용합니다.

새 테이블이나 새 schema를 추가하는 경우:

```text
feature/wbs-09-2-flyway-core-tables
```

이미 작성된 migration의 배포, 경로, 적용 문제를 고치는 경우:

```text
fix/wbs-09-2-dev-flyway-location
```

이미 DB에 적용된 Flyway migration 파일은 수정하지 않습니다. 변경이 필요하면 다음 버전의 migration 파일을 새로 추가합니다.

## 9. A/B 분업 브랜치 기준

10단계 풀스택 A/B 기능 분업 이후에는 담당 축을 브랜치명에 포함합니다.

A 담당 예시:

```text
feature/wbs-10-a-festival-api
feature/wbs-10-a-solo-course
fix/wbs-10-a-tour-api-error
```

B 담당 예시:

```text
feature/wbs-10-b-kakao-oauth
feature/wbs-10-b-member-profile
fix/wbs-10-b-token-refresh
```

공통 작업은 `a` 또는 `b`를 억지로 붙이지 않고 작업 성격을 드러냅니다.

```text
chore/wbs-10-common-api-client
refactor/wbs-10-common-error-response
test/wbs-10-common-health-check
```

## 10. AI/Codex 작업 기준

Codex 또는 다른 AI 도구가 브랜치를 생성할 때도 이 문서의 규칙을 따릅니다.

브랜치를 만들기 전에는 아래를 확인합니다.

- 현재 브랜치가 `dev`인지 확인한다.
- `docs/10_PROGRESS_LOG.md`에서 현재 WBS 단계를 확인한다.
- 작업 목적에 맞는 `type`을 선택한다.
- Secret이나 실제 서버 정보가 브랜치명, 커밋 메시지, 문서에 들어가지 않도록 한다.

브랜치명은 작업 범위가 드러나게 작성하되, 개인정보나 서버 정보를 포함하지 않습니다.
