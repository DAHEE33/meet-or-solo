# 관리자·회원·안전 기능 로드맵

## 1. 문서 목적과 상태

- 상태: `IN_PROGRESS` — 4.1 관리자 신고 검토 완료, 4.2 관리자 회원 조회·제재 완료,
  4.2 후속 UNSUSPEND 완료
- 목적: 풀스택 A의 관광 API·솔로 코스 구현을 기다리지 않고 풀스택 B가 독립적으로
  진행할 관리자 신고 처리, 회원 제재, 안전 자동화와 회원 탈퇴 범위를 정리합니다.
- 기준 문서: `meet-or-solo_planning.pdf` v5.0, `docs/05_MATCHING_POLICY.md`,
  `docs/06_SECURITY_POLICY.md`, `docs/09_TEST_AND_QUALITY_STRATEGY.md`,
  `docs/10_PROGRESS_LOG.md`, `docs/11_DATABASE_DESIGN.md`
- 이 문서는 구현 계획입니다. 실제 구현·자동 테스트·수동 검증을 수행하기 전에는
  완료 또는 `PASS`로 표시하지 않습니다.
- 다음 CLI 세션에서는 이 문서와 `docs/10_PROGRESS_LOG.md`를 함께 읽고
  미완료 단계(4.3 이후)부터 작업합니다.

## 2. 기획서 기준 전체 관리자 범위

기획서 v5.0의 최소 관리자 기능은 다음과 같습니다.

| 영역 | 요구 기능 | 우선순위 | 담당 경계 | 상태 |
| --- | --- | --- | --- | --- |
| 신고·제재 처리 | 신고 목록·카테고리 확인 | 필수 | B | 완료 (4.1) |
| 신고·제재 처리 | 경고·이용정지·영구차단 | 필수 | B | 완료 (4.2) |
| 신고·제재 처리 | 신고 3회 누적 자동 알림 | 필수 | B | 미착수 (4.3) |
| 회원 관리 | penalty score·매너온도·제재 이력 확인 | 필수 | B | 완료 (4.2) |
| 회원 관리 | blacklist 관리 | 필수 | B | 완료 (4.2) |
| 축제 데이터 관리 | 관광 API 데이터 활성·비활성 및 수동 등록 | 필수 | A | A 담당 |
| 배치 작업 관리 | 관광 API 갱신 수동 실행과 로그 확인 | 필수 | A | A 담당 |
| 1:1 문의 센터 | 문의 목록·답변·긴급 신고 처리 | 중요 | B 후속, 별도 설계 | 미착수 (4.5) |
| 기본 통계 | 일별 체크인·매칭·신고와 축제별 활성 사용자 | 중요 | 공통 또는 담당 분리 | 미착수 |

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
  `WARNING`, `SUSPEND`, `BAN`, `UNBAN`, `UNSUSPEND`, `REPORT_RESOLVE`, `REPORT_REJECT`,
  `MANUAL_PENALTY`, `DATA_CORRECTION`
- `match_penalty_events`의 `REPORT_CONFIRMED`, `ADMIN_ADJUST` 후보
- Frontend `AdminDashboardPage`와 mock 통계
- 관리자 회원 목록·상세·제재 UI (`/admin/members`)
- 정지 만료 lazy 복구와 Scheduler
- 정지·차단 회원의 로그인·refresh·matching 제한

현재 신고 접수만으로 penalty, cooldown, `penalty_score`, `manner_temperature`, group과
MatchRoom 상태를 변경하지 않습니다. 피신고자에게 신고 사실과 신고자 identity를 알리는
WebSocket/application event도 발행하지 않습니다.

## 4. 단계별 구현 순서

### 4.1 관리자 신고 검토 1차 — 완료

브랜치: `feature/wbs-10-b-admin-report-review` (PR #34, dev 병합 완료)

구현 범위:

- 관리자 신고 목록·상세 API
- 상태·사유·기간 filter와 keyset cursor pagination
- `SUBMITTED -> REVIEWING -> RESOLVED/REJECTED` 상태 전이
- report row `SELECT FOR UPDATE`와 동시 관리자 처리 방어
- `admin_actions` 감사 로그 원자 저장
- 동일 목표 상태 재전송 멱등성
- `/admin/reports` Frontend (filter, cursor 이전·다음, 상세·확인 dialog)
- ADMIN/USER/미인증 권한 경계
- cursor HMAC을 JWT Secret에서 분리해 `ADMIN_REPORT_CURSOR_HMAC_SECRET` 전용 키 사용

검증 결과:

- Backend 64 suites/416 tests, Frontend 22 files/184 tests 통과
- 2026-08-16 브라우저·dev DB 수동 검증 PASS

### 4.2 관리자 회원 조회·제재 2차 — 완료

브랜치: `feature/wbs-10-b-admin-member-sanctions` (PR #35, dev 병합 완료)

구현 범위:

- `V19__add_admin_member_sanctions.sql`로 `BANNED`, 정지 시작·종료 시각,
  제재 전 상태, `admin_actions.reason_code`, `idempotency_key` 추가
- 관리자 회원 목록·닉네임 검색·상태/역할 filter·cursor pagination
- 회원 상세와 신고·제재 이력 조회
- `WARNING`, `SUSPEND`, `BAN`, `UNBAN` action
- 필수 `Idempotency-Key`, member → optional report 고정 row lock
- 신고 `ACTION_TAKEN`과 감사 로그 원자 저장
- active pool/proposal/group 회원의 `SUSPEND`·`BAN` 409 거절
- 정지 만료 lazy 복구와 Scheduler
- 로그인·refresh·기존 access token 요청 제한
- refresh token 폐기와 commit 후 WebSocket session 종료
- `/admin/members` Frontend (목록·검색·filter·상세·제재 dialog)

검증 결과:

- Backend 전체 426 tests, 관리자 제재 Testcontainers 7건 통과
- Frontend 24 files/189 tests, production build 성공
- 2026-08-17 브라우저 수동 검증에서 기본 조회·검색·filter·상세·제재 흐름 확인

#### 4.2 후속: UNSUSPEND 조기 해제 — 완료

브랜치: `feature/wbs-10-b-admin-unsuspend` (PR #36, dev 병합 완료)

- `UNSUSPEND` action type 추가, `SUSPENDED -> statusBeforeSanction` 복원
- `V20__allow_unsuspend_action_type.sql`로 CHECK 제약 갱신
- 제재 이력에 `정지 해제` 기록, Frontend 회원 상세 dialog에 teal "정지 해제" 버튼
- Backend 비-컨테이너 187건 통과, Frontend 24 files/189 tests 통과
- 2026-08-18 브라우저 수동 검증 PASS

### 4.3 신고 누적·안전 자동화 3차 — 미착수

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

### 4.4 회원 본인 탈퇴 4차 — 미착수

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

### 4.5 1:1 문의 센터 후속 — 미착수

권장 브랜치:

```text
feature/wbs-10-b-inquiry-center
```

현재 DB 설계에서 `inquiries`는 보류된 table입니다. 사용자 문의 생성, 긴급 문의 표시,
관리자 답변, 공개 범위, 암호화, 첨부파일과 보관 정책을 먼저 확정하고 신규 migration으로
별도 구현합니다. 신고·회원 관리 브랜치에 함께 넣지 않습니다.

### 4.6 로그아웃 — 미착수

권장 브랜치:

```text
feature/wbs-10-b-logout
```

현재 상태:

- `frontend/src/pages/MyPage.tsx`의 "로그아웃" 버튼은 `navigate('/login')`만 호출합니다.
  `access_token` cookie와 refresh token, WebSocket session이 모두 그대로 남습니다.
- backend에 로그아웃 endpoint가 없습니다.
- `docs/06_SECURITY_POLICY.md`의 "logout 시 Refresh Token 무효화"는 아직 계획이며 구현이
  아닙니다.

예상 계약:

```http
POST /api/auth/logout
```

필수 범위:

- `access_token` HttpOnly cookie 만료 처리
- refresh token 폐기. `RefreshTokenRepository.revokeByMemberId()`가 이미 있으므로 재사용한다.
- transaction commit 이후 WebSocket session 종료. 관리자 제재(`AdminMemberService`)가 이미
  같은 순서를 구현했으므로 그 패턴을 재사용한다.
- 미인증 요청과 중복 로그아웃의 멱등 처리
- 진행 중인 pool·proposal·group이 있는 회원의 로그아웃 허용 여부와 상태 정리 정책 확정
- 로그아웃 후 기존 access token으로 보호 endpoint에 접근되지 않는지 검증

회원 탈퇴(4.4)와 refresh token 폐기·session 종료 로직이 겹칩니다. 4.6을 먼저 구현해 공통
경로를 만든 뒤 4.4에서 재사용하면 중복 구현을 피할 수 있습니다.

### 4.7 동의·개인정보 후속 — 미착수

권장 브랜치:

```text
feature/wbs-10-b-consent-followup
```

10-B 4단계(동의 API·회원가입 취향 입력)에서 확인된 남은 과제입니다. 4단계 구현 내용은
`docs/10_PROGRESS_LOG.md`의 `[10-B AI 임베딩]` 4-1절을 참고합니다.

- **동의 버전 무시**: `MemberConsentQueryRepository.hasAgreedConsent()`가 `version`을 보지
  않으므로 고지 문구를 개정해 `MemberConsentType.currentVersion()`을 올려도 기존 동의자에게
  재동의가 강제되지 않습니다. 강제하려면 조회 조건 변경과 기존 동의자 마이그레이션 방식을
  함께 설계해야 합니다. 조회 조건만 바꾸면 기존 동의자가 일괄로 취향을 잃습니다.
- **약관 소급 동의 수집**: 동의 기록 구조 이전에 가입한 `ACTIVE` 회원은 `TERMS`·`PRIVACY`
  기록이 없습니다. 현재는 최초 가입(`PROFILE_REQUIRED`)에만 동의를 요구하므로 이들에게는
  수집 경로가 없습니다.
- **`PROFILE_REQUIRED` 상태의 프로필 수정**: 해당 상태에서 `/profile/edit` 저장을 시도하면
  약관 동의 검사에 걸려 `SIGNUP_CONSENT_REQUIRED`로 거절됩니다. 정상 흐름에서는 해당 상태의
  회원이 `/signup`으로 유도되므로 발생하지 않지만, 상태를 수동으로 되돌려 검증할 때 마주칩니다.
- **동의 조회 범위**: `GET /api/members/me/consents`는 AI 관련 2종만 반환합니다.
  `TERMS`·`PRIVACY`는 기록만 하고 조회로 노출하지 않습니다.
- **임베딩 재시도 경로 부재**: `embedding_status = FAILED`를 회복하는 유일한 방법이 사용자가
  같은 취향 글을 다시 저장하는 것입니다.
- **외부 호출과 transaction 분리**: `MemberPreferenceEmbeddingService.createOrUpdate()`가
  `@Transactional` 안에서 OpenAI를 호출합니다. read timeout이 10초이므로 그동안 DB
  커넥션을 점유합니다. 회원가입 완료가 느리게 느껴지는 원인이기도 하므로 외부 호출을
  transaction 밖으로 분리하거나 비동기화하는 방안을 검토합니다.
- **점수 분해 저장**: 매칭 점수가 총점 하나만 저장되어 임베딩의 기여도를 사후 분석할 수
  없습니다. `jaccard`, `cosine`, 임베딩 사용 여부를 함께 남기려면 컬럼 추가가 필요합니다.

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

## 7. 권장 브랜치 순서

```text
feature/wbs-10-b-admin-report-review          — 완료 (PR #34)
feature/wbs-10-b-admin-member-sanctions       — 완료 (PR #35)
feature/wbs-10-b-admin-unsuspend              — 완료 (PR #36)
feature/wbs-10-b-report-safety-automation     — 미착수 (4.3)
feature/wbs-10-b-member-withdrawal            — 미착수 (4.4)
feature/wbs-10-b-inquiry-center               — 미착수 (4.5)
feature/wbs-10-b-logout                       — 미착수 (4.6)
feature/wbs-10-b-consent-followup             — 미착수 (4.7)
```

4.6 로그아웃은 4.4 회원 탈퇴와 refresh token 폐기·session 종료를 공유하므로 4.4보다 먼저
진행하는 편이 유리합니다.

각 브랜치는 `dev`에서 분기하고 작업 완료 후 PR로 `dev`에 병합합니다. 앞 단계 PR이
병합되기 전에 다음 단계를 같은 작업 트리에 누적하지 않습니다.

## 8. 다음 CLI 세션 인수인계 프롬프트

아래는 4.3 신고 누적·안전 자동화를 시작할 때 사용합니다.

```text
AGENTS.md와 docs/*.md를 모두 확인하고,
docs/19_ADMIN_MEMBER_SAFETY_ROADMAP.md 4.3절을 기준으로
신고 누적·안전 자동화 3차 작업 계획을 제시해줘.

현재 목표 브랜치는 feature/wbs-10-b-report-safety-automation이며,
dev에서 새 브랜치를 만들었는지 확인해줘.

4.1 관리자 신고 검토, 4.2 관리자 회원 조회·제재, 4.2 후속 UNSUSPEND는
모두 완료되어 dev에 병합됐다. 기존 reports, admin_actions, members,
match_penalty_events schema와 관리자 인가·UI 패턴을 재사용해줘.

구현 전 4.3절의 확정 필요 사항을 먼저 조사하고 정책 선택지와 권장안을
제안해줘. 파일 수정 전에 승인을 기다려줘.
```
