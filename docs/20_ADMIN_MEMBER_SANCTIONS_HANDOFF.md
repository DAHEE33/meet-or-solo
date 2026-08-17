# 관리자 회원 조회·제재 2차 인수인계

## 1. 목적과 시작 조건

- 상태: 구현·자동 검증 완료, 브라우저·dev DB 수동 검증 일부 수행
- 담당: 풀스택 B
- 목표: 관리자가 회원 상태와 안전 이력을 조회하고 `WARNING`, `SUSPEND`, `BAN`,
  `UNBAN`을 감사 가능하고 멱등하게 처리하는 흐름을 구현합니다.
- 선행 작업: [관리자 신고 검토 1차](19_ADMIN_MEMBER_SAFETY_ROADMAP.md)는 구현·자동 검증과
  dev DB·브라우저 수동 검증을 완료했습니다.
- 시작 조건: `feature/wbs-10-b-admin-report-review`를 `dev`에 병합한 뒤 `dev`에서 새
  브랜치를 생성합니다. 1차 브랜치에 2차 작업을 누적하지 않습니다.

권장 브랜치:

```text
feature/wbs-10-b-admin-member-sanctions
```

## 2. 먼저 재사용할 기반

- `GET /api/admin/me`와 DB `members.role` 재조회 기반 ADMIN 인가
- `AdminRoute`, 관리자 API client와 `ApiClientError`
- 관리자 신고 목록·상세의 cursor pagination, loading·empty·error·retry 상태
- report `SELECT FOR UPDATE`, terminal 상태 전이와 `admin_actions` 원자 저장
- Frontend abort/request identity, 동기 in-flight guard와 접근 가능한 확인 dialog
- 기존 `members.status`, `penalty_score`, `manner_temperature`
- 기존 `reports`, `admin_actions`, `match_penalty_events` schema
- 로그인, refresh token, matching pool 진입의 현재 회원 상태 검사 경계

1차 구현을 복사해 별도 권한 체계를 만들지 않고 공통 관리자 인가와 UI 패턴을 재사용합니다.

## 3. 구현 전 반드시 확정할 정책

다음 항목은 코드 작성 전에 현재 schema·인증·매칭 흐름을 조사하고 API 계약과 함께 제안합니다.

1. `SUSPEND`를 표현할 `members.status`, 정지 시작·종료 시각과 기간 단위
2. 정지 만료의 자동 복구 방식과 Scheduler 필요 여부
3. `BAN`과 `UNBAN`의 상태 전이 및 해제 후 복구 상태
4. `WARNING`이 회원 상태·점수에 미치는 영향과 감사 로그만 남길지 여부
5. 제재 요청의 idempotency key 또는 상태 기반 멱등 계약
6. 신고 연결 제재 시 허용 report 상태와 `ACTION_TAKEN` 전이 규칙
7. member와 report row의 고정 잠금 순서 및 두 관리자 경합 결과
8. 정지·차단 직후 기존 access token, refresh token과 WebSocket session 처리 시점
9. 정지·차단 회원의 로그인, token refresh와 신규 matching pool 진입 제한
10. 현재 확정 group 참여자를 제재할 때 group을 유지할지 종료할지
11. 관리자 action 사유의 구조화 여부, 길이·민감정보 금지와 보관 정책
12. blacklist가 별도 table인지 `members.status`의 표시 개념인지

특히 활성 match group 처리 정책이 확정되기 전에는 제재가 group, proposal, pool 또는
MatchRoom 상태를 임의로 변경하지 않습니다.

## 4. 예상 구현 범위

### Backend

- 관리자 회원 목록·검색·상태 filter와 안정적인 pagination
- 회원 상세의 공개 프로필, 회원 상태, penalty score, manner temperature
- 신고 이력과 관리자 제재 이력 조회
- `WARNING`, `SUSPEND`, `BAN`, `UNBAN`
- 신고 연결 제재 시 report `ACTION_TAKEN`, member 변경과 `admin_actions` 원자 저장
- member/report 고정 잠금 순서, 상태 재검증과 동시 관리자 처리 방어
- 동일 요청 재전송 멱등성
- 정지·차단 회원의 로그인, refresh와 신규 매칭 신청 제한
- ADMIN/USER/미인증 권한 경계와 민감정보 비노출

### Frontend

- `/admin/members` 목록·검색·상태 filter와 pagination
- 회원 상세와 신고·제재 이력
- 경고·정지·영구차단·해제 확인 dialog
- 위험 action 재확인, 중복 제출 방지와 실패 전 snapshot 유지
- 성공 뒤 대상 회원과 열린 상세만 갱신
- blacklist·정지 상태와 기간 표시
- 일반 화면 또는 마이페이지의 ADMIN 전용 관리자 진입 버튼 UX 검토

관리자 진입 버튼은 편의 기능이며 Backend의 DB role 재검증을 대체하지 않습니다.

## 5. 이번 단계 제외 범위

- 신고 3회 누적 자동 제한과 관리자 자동 알림
- `REPORT_CONFIRMED` 자동 penalty/cooldown 및 manner temperature 자동 변경
- 회원 본인 탈퇴와 `WITHDRAWN`
- 관리자에 의한 개인정보 임의 수정
- 자유 입력에 Secret, token, OAuth identifier, GPS 원본 좌표 저장
- 1:1 문의 센터와 관리자 통계 실데이터
- 관광 API·축제 데이터·관광 배치·솔로 코스
- Redis와 자유 채팅

## 6. 테스트 우선순위

### Backend

- ADMIN/USER/미인증 권한 경계
- 목록 검색·filter·pagination의 누락·중복 방지
- 응답의 암호문·OAuth identifier·token·Secret 비노출
- 모든 허용·금지 상태 전이와 동일 요청 멱등성
- 두 관리자의 동일·상반된 제재 동시 처리
- member/report 잠금 순서와 rollback 원자성
- 신고 연결 제재의 report `ACTION_TAKEN`과 감사 로그 단일성
- 제재 전후 로그인, refresh와 matching pool 진입 제한
- `UNBAN` 이후 허용 범위와 과거 감사 이력 보존
- PostgreSQL Testcontainers focused 테스트와 Backend 전체 회귀

### Frontend

- 관리자 route와 ADMIN 전용 진입 UX
- loading·empty·error·retry, 검색·filter·pagination
- 오래된 응답 차단과 빠른 이중 제출 방지
- 실패 전 snapshot 불변, 성공 뒤 대상 회원만 갱신
- 위험 action dialog의 focus, Escape와 Tab 순환
- 민감정보와 불필요한 내부 ID 비노출
- 전체 Vitest, `npx tsc --noEmit`, production/PWA build

### 수동 검증

- ADMIN, 일반 USER와 미인증 session의 접근 차이
- 회원 조회와 제재 전후 dev DB 정합성
- 두 관리자 동시 처리 또는 빠른 중복 제출
- 정지·차단 회원의 로그인·refresh·매칭 제한과 해제
- 피제재 회원에게 공개되는 정보가 확정 정책을 넘지 않는지 확인

실제로 수행하지 않은 수동 검증은 `PASS`로 기록하지 않습니다.

## 7. 구현·검증 결과와 후속 UX

- 승인 정책에 따라 `V19`, 관리자 회원 API·UI, 제재 transaction, 인증·refresh·WebSocket과
  matching 제한을 구현했고 Backend 전체 426 tests, 관리자 제재 PostgreSQL Testcontainers
  7건, Frontend 전체 24 files/189 tests와 production build를 통과했다.
- 사용자 브라우저 수동 검증은 2026-08-17 실제 확인한 항목까지만 인정한다. WebSocket session
  종료, active pool/proposal/group 회원 제재의 409, dialog keyboard 접근성과 자동 테스트 대체
  항목은 수동 미실행이며 `PASS`로 기록하지 않는다.
- 동시 관리자 경합, 동일 `Idempotency-Key` 재전송·payload 충돌, rollback 강제 실패, 정지
  Scheduler 경쟁과 같은 항목은 브라우저로 무리하게 재현하지 않고 통과한 자동 통합 테스트를
  검증 근거로 사용한다.
- 현재 관리자는 `SUSPENDED` 회원을 직접 조기 해제할 수 없다. `BAN -> UNBAN`은 테스트 계정
  복구에는 사용할 수 있지만 감사 의미와 운영 UX가 달라 정식 해제 기능으로 간주하지 않는다.
- 후속 작업은 `UNSUSPEND` 또는 동등한 action을 별도 정의하고, `SUSPENDED ->
  status_before_sanction` 전이, 감사 유형, `Idempotency-Key`, member row lock, 자동 만료와 수동
  해제 race, refresh·WebSocket 재허용 시점을 설계한 뒤 API와 확인 dialog를 추가한다.

## 8. 기존 구현 시작 프롬프트 이력

아래 프롬프트는 2차 구현 전 조사 단계에서 사용한 이력이며 현재 다음 작업 지시가 아닙니다.
조기 정지 해제는 위 7절의 정책을 별도 승인한 뒤 새 작업으로 진행합니다.

```text
AGENTS.md와 docs/*.md를 모두 확인하고,
docs/20_ADMIN_MEMBER_SANCTIONS_HANDOFF.md를 기준으로
관리자 회원 조회·제재 2차의 구현 전 조사와 계획을 제시해줘.

현재 목표 브랜치는 feature/wbs-10-b-admin-member-sanctions이다.
반드시 dev에서 새 브랜치를 만들었는지 확인하고, 관리자 신고 검토 1차 브랜치에
2차 구현을 누적하지 마라.

기존 members status·role schema, admin_actions, reports, 인증 login/refresh,
matching pool 진입 제한, AdminRoute와 관리자 신고 UI 패턴을 먼저 조사해줘.

특히 아래 정책은 추측해 구현하지 말고 선택지·영향·권장안을 먼저 제시해줘.

- SUSPEND 상태와 기간·만료 처리
- BAN/UNBAN 상태 전이와 복구 상태
- WARNING의 부수효과
- 신고 연결 제재와 ACTION_TAKEN 계약
- member/report row 잠금 순서와 동시 관리자 처리
- 멱등성 계약
- 기존 token과 WebSocket session 처리
- 로그인, refresh와 신규 matching 신청 제한
- active group 참여자 제재 정책
- 관리자 action 사유와 민감정보 금지
- blacklist 표현 방식

관리자 회원 목록·상세·신고/제재 이력, WARNING/SUSPEND/BAN/UNBAN,
/admin/members Frontend와 ADMIN 전용 관리자 진입 버튼을 계획 범위로 검토해줘.

신고 누적 자동화, 회원 탈퇴, 문의 센터, 통계 실데이터와 풀스택 A 담당 기능은 제외해줘.

파일을 수정하기 전에 다음을 제시하고 승인을 기다려줘.

1. 현재 구현과 schema 조사 결과
2. 확정이 필요한 정책과 권장안
3. API 계약 초안
4. transaction, row lock과 멱등성 설계
5. 인증·refresh·matching 제한 적용 지점
6. Backend/Frontend 변경 예정 파일
7. 신규 Flyway migration 필요 여부
8. 자동 테스트와 dev DB·브라우저 수동 검증 계획

기존 migration과 사용자 변경을 보존하고, 내가 진행을 승인하기 전에는 파일을 수정하지 마라.
```

