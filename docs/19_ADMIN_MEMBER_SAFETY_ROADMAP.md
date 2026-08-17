# 관리자·회원·안전 기능 로드맵

## 1. 문서 목적과 상태

- 상태: `IN_PROGRESS` — 4.1 관리자 신고 검토 완료, 4.2 관리자 회원 조회·제재 구현·자동
  검증 완료 및 사용자 수동 검증 일부 수행
- 목적: 풀스택 A의 관광 API·솔로 코스 구현을 기다리지 않고 풀스택 B가 독립적으로
  진행할 관리자 신고 처리, 회원 제재, 안전 자동화와 회원 탈퇴 범위를 인계합니다.
- 기준 문서: `meet-or-solo_planning.pdf` v5.0, `docs/05_MATCHING_POLICY.md`,
  `docs/06_SECURITY_POLICY.md`, `docs/09_TEST_AND_QUALITY_STRATEGY.md`,
  `docs/10_PROGRESS_LOG.md`, `docs/11_DATABASE_DESIGN.md`
- 이 문서는 구현 계획입니다. 실제 구현·자동 테스트·수동 검증을 수행하기 전에는
  완료 또는 `PASS`로 표시하지 않습니다.

## 2. 기획서 기준 전체 관리자 범위

기획서 v5.0의 최소 관리자 기능은 다음과 같습니다.

| 영역 | 요구 기능 | 우선순위 | 담당 경계 |
| --- | --- | --- | --- |
| 신고·제재 처리 | 신고 목록·카테고리 확인 | 필수 | B |
| 신고·제재 처리 | 경고·이용정지·영구차단 | 필수 | B |
| 신고·제재 처리 | 신고 3회 누적 자동 알림 | 필수 | B |
| 회원 관리 | penalty score·매너온도·제재 이력 확인 | 필수 | B |
| 회원 관리 | blacklist 관리 | 필수 | B |
| 축제 데이터 관리 | 관광 API 데이터 활성·비활성 및 수동 등록 | 필수 | A |
| 배치 작업 관리 | 관광 API 갱신 수동 실행과 로그 확인 | 필수 | A |
| 1:1 문의 센터 | 문의 목록·답변·긴급 신고 처리 | 중요 | B 후속, 별도 설계 |
| 기본 통계 | 일별 체크인·매칭·신고와 축제별 활성 사용자 | 중요 | 공통 또는 담당 분리 |

축제 데이터와 관광 API 배치는 풀스택 A의 데이터와 service에 의존하므로 B가 임의로
선행 구현하지 않습니다. B는 신고 접수부터 검토·제재·회원 제한까지의 안전 흐름을
독립적으로 완결합니다.

## 3. 현재 구현 기반

이미 존재하는 기반은 다음과 같습니다.

- `members.role`: `USER`, `ADMIN`
- `members.status`와 `penalty_score`, `manner_temperature`
- 관리자 만남 장소 API의 JWT cookie 인증과 DB role 재조회 방식
- 구조화 신고 접수 API와 `reports` table
- 신고 상태: `SUBMITTED`, `REVIEWING`, `RESOLVED`, `REJECTED`, `ACTION_TAKEN`
- `admin_actions` table과 action type:
  `WARNING`, `SUSPEND`, `BAN`, `UNBAN`, `REPORT_RESOLVE`, `REPORT_REJECT`,
  `MANUAL_PENALTY`, `DATA_CORRECTION`
- `match_penalty_events`의 `REPORT_CONFIRMED`, `ADMIN_ADJUST` 후보
- Frontend `AdminDashboardPage`와 mock 통계

현재 신고 접수만으로 penalty, cooldown, `penalty_score`, `manner_temperature`, group과
MatchRoom 상태를 변경하지 않습니다. 피신고자에게 신고 사실과 신고자 identity를 알리는
WebSocket/application event도 발행하지 않습니다.

## 4. 단계별 구현 순서

### 4.1 관리자 신고 검토 1차

권장 브랜치:

```text
feature/wbs-10-b-admin-report-review
```

목표는 신고 접수 이후 관리자가 검토 결과를 확정하고 감사 이력을 남기는 데까지입니다.

Backend 범위:

- 관리자 신고 목록 API
- 상태·사유·기간 filter와 안정적인 pagination
- 관리자 신고 상세 API
- `SUBMITTED -> REVIEWING`
- `SUBMITTED` 또는 `REVIEWING -> RESOLVED`
- `SUBMITTED` 또는 `REVIEWING -> REJECTED`
- 처리 transaction에서 `admin_actions` 감사 로그 생성
- report row 선잠금과 상태 재검증을 통한 동시 관리자 처리 방어
- 같은 목표 상태 재전송 멱등성
- 인증 누락 `401`, 일반 회원 `403`
- 관리자 응답에도 필요한 최소 회원 정보만 제공
- 피신고자 WebSocket/event 미발행

Frontend 범위:

- `/admin/reports` 목록
- 상태·사유 filter
- loading, 빈 목록, 오류와 재시도
- 신고 상세 dialog 또는 상세 화면
- 검토 시작, 유효 신고 확정, 기각 action
- 빠른 이중 제출 방지와 성공 전 화면 상태 불변
- 처리 성공 뒤 대상 row 또는 상세 snapshot 갱신
- 관리자 외 접근 차단

1차 제외 범위:

- `SUSPEND`, `BAN`, 자동 penalty/cooldown
- `penalty_score`, `manner_temperature` 변경
- 자유 입력 신고 상세
- 피신고자 알림
- 회원 후기
- 관리자 통계 실데이터 전환

1차의 `RESOLVED`는 유효 신고로 검토를 마쳤지만 제재를 적용하지 않은 상태입니다.
실제 제재까지 적용한 경우는 후속 단계에서 `ACTION_TAKEN`을 사용합니다.

구현 및 검증 상태:

- 4.1 Backend·Frontend 구현과 자동 검증을 완료했습니다.
- Backend 관리자 신고·인가 및 cursor 보안 focused 5 suites/30 tests, safety 전체
  7 suites/58 tests, 전체 64 suites/416 tests와 build가 성공했습니다.
- Frontend focused 4 files/14 tests, 전체 Vitest 22 files/184 tests,
  `npx tsc --noEmit`, production/PWA build가 성공했습니다.
- 2026-08-16 로컬 Backend/Frontend와 dev DB를 연결한 브라우저 수동 검증을 완료했습니다.
  ADMIN 목록·filter·상세·상태 변경, USER `403`, 미인증 로그인 이동, 동일 처리 멱등성 및
  terminal 충돌을 확인했고 `reports`와 `admin_actions`의 상태·감사 로그 단일성이 일치했습니다.
  penalty·cooldown·회원 상태·매칭 부수 상태는 변경되지 않았으며 피신고자에게 신고 상태,
  처리 결과와 신고자·관리자 identity가 노출되지 않았습니다.
- 수동 검증 전 보안 점검 결과 관리자 신고 cursor HMAC 키를 JWT 서명 키에서 분리했습니다.
  `ADMIN_REPORT_CURSOR_HMAC_SECRET`을 dev/prod에 별도 주입하며 실제 값은 저장소에 기록하지
  않습니다. 전용 키 회전 시 기존 cursor는 무효화될 수 있고 DB migration은 필요하지 않습니다.
- 4.1 최종 상태는 `PASS`입니다. 관리자는 일반 회원 화면을 함께 사용하고 `/admin`으로 직접
  진입합니다. 일반 화면의 ADMIN 전용 진입 버튼과 관리자 화면 상세 UX 보완은 4.1 완료 범위에
  포함하지 않고 후속 관리자 UX 작업으로 관리합니다.

### 4.2 관리자 회원 조회·제재 2차

상태: 구현·자동 검증 완료, 브라우저·dev DB 수동 검증 일부 수행 — 상세 인수인계와 후속 기록은
[docs/20_ADMIN_MEMBER_SANCTIONS_HANDOFF.md](20_ADMIN_MEMBER_SANCTIONS_HANDOFF.md)를 기준으로 합니다.

권장 브랜치:

```text
feature/wbs-10-b-admin-member-sanctions
```

Backend 범위:

- 관리자 회원 목록·검색·상태 filter와 pagination
- 회원 상세의 공개 프로필, 상태, penalty score, manner temperature 제공
- 신고 이력과 관리자 제재 이력 조회
- `WARNING`, `SUSPEND`, `BAN`, `UNBAN`
- 신고와 연결한 제재는 report `ACTION_TAKEN` 및 `admin_actions`를 같은 transaction에서 저장
- 회원 row와 report row의 고정 잠금 순서 및 동시 관리자 처리 방어
- 제재 요청 멱등성 key 또는 상태 기반 멱등 계약
- 정지·차단 회원의 로그인, token refresh와 신규 매칭 신청 제한
- 현재 확정 group을 관리자 제재만으로 즉시 변경할지 별도 정책 확정

Frontend 범위:

- `/admin/members` 목록·검색·filter
- 회원 상세, 신고·제재 이력
- 경고·정지·영구차단·해제 확인 dialog
- 위험 action 재확인, 중복 제출 방지와 오류 복구
- blacklist 상태 표시

정책 확정 전 금지:

- 관리자가 회원 개인정보를 임의 수정하는 기능
- 사용자 본인 탈퇴를 관리자 `BAN`과 같은 상태로 처리하는 기능
- 자유 사유에 Secret, token, GPS 원본 좌표 저장

구현 및 검증 상태:

- `V19` 신규 migration, 관리자 회원 목록·상세·이력, `WARNING`, `SUSPEND`, `BAN`,
  `UNBAN`, 멱등성·row lock·신고 `ACTION_TAKEN` 원자 처리와 인증·매칭 제한을 구현했다.
- Backend 전체 426 tests, 관리자 제재 Testcontainers 7건, Frontend 전체 24 files/189 tests와
  production build가 성공했다.
- 사용자 브라우저 수동 검증은 실제 확인한 범위만 인정한다. WebSocket session 종료, active
  matching 409, dialog 접근성과 자동 검증 대상은 수동 `PASS`로 올리지 않는다.
- `SUSPENDED` 조기 해제 action이 없어 `BAN -> UNBAN` 우회가 필요한 UX 누락을 확인했다.
  후속 제재 UX에서 `UNSUSPEND` 또는 동등 action의 권한, 감사 로그, 멱등성, 정지 만료와의
  race를 설계하며 기존 `UNBAN` 의미를 재사용하지 않는다.

### 4.3 신고 누적·안전 자동화 3차

권장 브랜치:

```text
feature/wbs-10-b-report-safety-automation
```

기획서 후보 정책:

- 관리자에게 유효 판정된 신고의 manner temperature `-10`
- 신고 3회 누적 시 자동 이용 제한 후보 및 관리자 알림
- manner temperature 30도 이하 매칭 제한

구현 전 반드시 확정할 사항:

- 누적 기준이 전체 신고인지, 서로 다른 group·reporter의 유효 신고인지
- 같은 reporter의 반복 사유 신고를 몇 건으로 계산할지
- 누적 window와 장기 보관 기간
- 자동 제한의 상태, 기간과 해제 방식
- `REPORT_CONFIRMED` penalty score 및 cooldown 수치
- manner temperature 하한과 재계산·복구 정책
- 자동 처리와 관리자 수동 처리의 우선순위 및 멱등성 key
- 관리자 알림을 DB queue, 화면 badge 또는 Web Push 중 무엇으로 제공할지

위 정책을 문서로 먼저 확정한 후 transaction, Scheduler와 통합 테스트를 구현합니다.

### 4.4 회원 본인 탈퇴 4차

권장 브랜치:

```text
feature/wbs-10-b-member-withdrawal
```

회원 탈퇴는 관리자 회원 관리가 아니라 인증·회원 요구사항 `AUTH-04`입니다.

예상 계약:

```http
DELETE /api/members/me
```

필수 범위:

- 본인 인증과 필요 시 재확인 정책
- soft delete와 `WITHDRAWN` 상태
- nickname, email, profile image, 성별, 연령대, intro 등 개인정보 즉시 익명화·제거
- provider identifier의 재가입·unique constraint 대응 방식
- refresh token 전체 폐기와 이후 access/refresh 차단
- Object Storage profile image 삭제 또는 실패 시 안전한 재시도·정리 정책
- active pool·proposal·group이 있을 때의 탈퇴 허용 시점과 상태 정리 정책
- 신고·penalty·matching 감사 이력 FK 보존
- 반복·동시 탈퇴 요청 멱등성
- 탈퇴 이후 다른 사용자 화면에서 개인정보가 복원되지 않는지 검증

관리자 `BAN`과 회원 `WITHDRAWN`은 목적과 복구 가능성이 다르므로 같은 상태 전이나 API로
처리하지 않습니다.

### 4.5 1:1 문의 센터 후속

권장 브랜치:

```text
feature/wbs-10-b-inquiry-center
```

현재 DB 설계에서 `inquiries`는 보류된 table입니다. 사용자 문의 생성, 긴급 문의 표시,
관리자 답변, 공개 범위, 암호화, 첨부파일과 보관 정책을 먼저 확정하고 신규 migration으로
별도 구현합니다. 신고·회원 관리 브랜치에 함께 넣지 않습니다.

## 5. 공통 보안·동시성 원칙

- 관리자 endpoint는 JWT cookie의 회원 ID로 `members.role=ADMIN`을 매 요청 다시 확인합니다.
- request body의 admin ID를 신뢰하지 않습니다.
- 인증 누락은 `401`, 일반 회원은 `403`으로 구분합니다.
- 목록과 상세 응답에서 성별·연령대 암호문, OAuth identifier, refresh token과 내부 Secret을
  노출하지 않습니다.
- 신고자 identity는 관리자 검토에 필요한 범위로만 제공하고 피신고자 API에는 제공하지 않습니다.
- report, member와 관련 row의 잠금 순서를 기능별로 문서화하고 모든 service에서 고정합니다.
- 상태 변경과 `admin_actions`, penalty/cooldown 및 member 변경은 필요한 경우 하나의
  transaction에서 commit 또는 rollback합니다.
- 외부 알림과 WebSocket 전송은 transaction commit 이후에만 수행합니다.
- 기존 Flyway migration을 수정하지 않고 schema 변경이 필요하면 새 migration을 추가합니다.
- 실제 관리자 권한 부여를 운영 API로 임의 제공하지 않습니다. 초기 ADMIN 계정 생성·승격은
  별도 운영 절차와 audit 대상으로 둡니다.

## 6. 테스트 우선순위

Backend:

- ADMIN/USER/미인증 권한 경계
- 목록 filter, 정렬, pagination 누락·중복 방지
- 존재하지 않거나 권한 없는 resource의 정보 비노출
- 허용 상태 전이와 잘못된 역전이 거절
- 동일 요청 멱등성
- 두 관리자 동시 처리에서 단일 최종 상태와 감사 로그
- transaction 중 insert/update 실패 전체 rollback
- 제재 전후 로그인, refresh, matching entry 제한
- 탈퇴 개인정보 제거와 FK 감사 이력 보존
- PostgreSQL 통합 테스트와 Backend 전체 회귀

Frontend:

- 관리자 route 접근 제어
- loading, empty, error, retry와 pagination
- filter 변경 시 오래된 응답 차단
- action 이중 제출 방지와 실패 전 snapshot 불변
- 성공 뒤 해당 항목만 정확히 갱신
- 위험 action 확인 dialog의 focus, Escape와 keyboard 순환
- 민감정보와 내부 ID의 불필요한 노출 방지
- 전체 Vitest, `npx tsc --noEmit`, production/PWA build

수동 검증:

- ADMIN과 일반 회원 두 session의 접근 차이
- 신고 접수부터 관리자 상태 처리까지 Network·DB 정합성
- 두 관리자 동시 처리 또는 빠른 중복 제출
- 피신고자 화면에 신고자·관리자 처리 사실 비노출
- 제재 회원의 인증·매칭 제한과 해제
- 탈퇴 직후 token 폐기, 개인정보 익명화와 재접속 차단

## 7. 권장 브랜치 순서

```text
feature/wbs-10-b-admin-report-review
feature/wbs-10-b-admin-member-sanctions
feature/wbs-10-b-report-safety-automation
feature/wbs-10-b-member-withdrawal
feature/wbs-10-b-inquiry-center
```

각 브랜치는 `dev`에서 분기하고 작업 완료 후 PR로 `dev`에 병합합니다. 앞 단계 PR이
병합되기 전에 다음 단계를 같은 작업 트리에 누적하지 않습니다.

## 8. 다음 Codex 채팅 인수인계 프롬프트

```text
AGENTS.md와 docs/*.md를 모두 확인하고,
docs/19_ADMIN_MEMBER_SAFETY_ROADMAP.md를 기준으로
관리자 신고 검토 1차 작업 계획을 제시해줘.

현재 작업 목표는 feature/wbs-10-b-admin-report-review이며,
풀스택 A의 관광 API·솔로 코스·축제 데이터·관광 배치에는 의존하지 않게 해줘.

기존 reports/admin_actions schema, 신고 접수 API, ADMIN 권한 검증 방식과
Frontend AdminDashboardPage를 먼저 조사해줘. 파일 수정 전 아래 범위를 검토해줘.

- 관리자 신고 목록·상세
- 상태·사유·기간 filter와 pagination
- SUBMITTED -> REVIEWING
- SUBMITTED/REVIEWING -> RESOLVED 또는 REJECTED
- admin_actions 감사 로그
- report row lock과 두 관리자 동시 처리
- 동일 목표 상태 재전송 멱등성
- ADMIN/USER/미인증 권한 경계
- /admin/reports Frontend
- Backend PostgreSQL 통합·전체 회귀
- Frontend Vitest·TypeScript·production/PWA build

회원 SUSPEND/BAN, penalty/cooldown, manner_temperature, 후기, 회원 탈퇴,
문의 센터, 관리자 통계와 A 담당 기능은 이번 1차에서 제외해줘.
먼저 구현·테스트·수동 검증 범위와 API 계약 초안을 제안하고,
내가 진행을 승인하기 전에는 파일을 수정하지 마라.
```
