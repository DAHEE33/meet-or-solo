# 관리자·회원·안전 기능 로드맵

## 1. 문서 목적과 상태

- 상태: `IN_PROGRESS` — 4.1 관리자 신고 검토 완료, 4.2 관리자 회원 조회·제재 완료,
  4.2 후속 UNSUSPEND 완료, 4.3 신고 누적·안전 자동화 완료(PR #50 dev 병합),
  4.6 로그아웃 완료(PR #52 dev 병합), 4.10 만남 종료 후 신고 진입점 완료(PR #53 dev 병합),
  4.8 회원 제재 사유·기간 통보 구현 완료(수동 검증 대기),
  4.5 1:1 문의 센터 구현 완료(수동 검증 대기).
  남은 항목은 4.4·4.7·4.9
- 목적: 풀스택 A의 관광 API·솔로 코스 구현을 기다리지 않고 풀스택 B가 독립적으로
  진행할 관리자 신고 처리, 회원 제재, 안전 자동화와 회원 탈퇴 범위를 정리합니다.
- 기준 문서: `meet-or-solo_planning.pdf` v5.0, `docs/05_MATCHING_POLICY.md`,
  `docs/06_SECURITY_POLICY.md`, `docs/09_TEST_AND_QUALITY_STRATEGY.md`,
  `docs/10_PROGRESS_LOG.md`, `docs/11_DATABASE_DESIGN.md`
- 이 문서는 구현 계획입니다. 실제 구현·자동 테스트·수동 검증을 수행하기 전에는
  완료 또는 `PASS`로 표시하지 않습니다.
- 다음 CLI 세션에서는 이 문서와 `docs/10_PROGRESS_LOG.md`를 함께 읽고
  미완료 단계부터 작업합니다.

## 2. 기획서 기준 전체 관리자 범위

기획서 v5.0의 최소 관리자 기능은 다음과 같습니다.

| 영역 | 요구 기능 | 우선순위 | 담당 경계 | 상태 |
| --- | --- | --- | --- | --- |
| 신고·제재 처리 | 신고 목록·카테고리 확인 | 필수 | B | 완료 (4.1) |
| 신고·제재 처리 | 경고·이용정지·영구차단 | 필수 | B | 완료 (4.2) |
| 신고·제재 처리 | 신고 3회 누적 자동 알림 | 필수 | B | 구현·dev 검증 완료, 병합 전 (4.3) |
| 회원 관리 | penalty score·매너온도·제재 이력 확인 | 필수 | B | 완료 (4.2) |
| 회원 관리 | blacklist 관리 | 필수 | B | 완료 (4.2) |
| 축제 데이터 관리 | 관광 API 데이터 활성·비활성 및 수동 등록 | 필수 | A | A 담당 |
| 배치 작업 관리 | 관광 API 갱신 수동 실행과 로그 확인 | 필수 | A | A 담당 |
| 1:1 문의 센터 | 문의 목록·답변·긴급 신고 처리 | 중요 | B 후속, 별도 설계 | 구현 완료, 수동 검증 대기 (4.5) |
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

### 4.3 신고 누적·안전 자동화 3차 — 완료 (PR #50, dev 병합 완료)

권장 브랜치:

```text
feature/wbs-10-b-report-safety-automation
```

기획서 후보 정책과 확정 결과는 다음과 같습니다. 확정 근거는 아래 표와
`docs/05_MATCHING_POLICY.md`의 `관리자 유효 판정 신고 (REPORT_CONFIRMED)` 절을
함께 참고합니다.

| 기획서 후보 | 확정 결과 |
| --- | --- |
| 유효 판정 신고의 manner temperature `-10` | `-5`로 조정, 하한 `20.00` |
| 신고 3회 누적 시 자동 이용 제한 후보 및 관리자 알림 | 관리자 알림까지 채택. 자동 제한은 도입하지 않음 |
| manner temperature 30도 이하 매칭 제한 | 이번 단계 미도입. 후기 기능과 함께 설계 |

#### 확정 사항 8건

| # | 항목 | 확정값 |
| --- | --- | --- |
| 1 | 누적 기준 | 유효 신고(`RESOLVED`, `ACTION_TAKEN`) 중 `(reporter_member_id, group_id)` distinct 개수 |
| 2 | 같은 reporter 반복 사유 | 같은 group에서는 사유가 달라도 1건. 다른 group의 재신고는 별건 |
| 3 | 누적 window·보관 | 30일 rolling(`created_at`, `Asia/Seoul`). row는 감사용으로 보존하며 삭제하지 않음 |
| 4 | 자동 제한 | 회원 `status`를 자동으로 변경하지 않음. 관리자 알림과 "제한 검토 대상" 표시까지만 |
| 5 | `REPORT_CONFIRMED` 수치 | penalty score `+5`, cooldown 미생성 |
| 6 | manner temperature | `-5`, 하한 `20.00`, 증분 적용. 재계산 batch 없음 |
| 7 | 우선순위·멱등성 key | 관리자 `RESOLVED` transaction 내 동기 적용. 멱등성 key는 `related_report_id`. 관리자 수동이 항상 우선 |
| 8 | 관리자 알림 | `admin_safety_alerts` DB queue + 조회·확인 API + `AdminNav` badge |

#### 확정 근거로 남겨두는 조사 결과

- **manner temperature `-10`을 조정한 이유**: 시작값이 `36.50`이므로 `-10`이면
  유효 신고 1건에 `26.50`이 되어 기획서의 "30도 이하 매칭 제한"에 즉시 걸린다.
  `member_reviews` table은 `V4`에 있으나 코드가 없어 온도를 올릴 경로가 하나도
  없으므로, 두 정책을 그대로 합치면 유효 신고 1건이 영구 매칭 제한이 된다.
  또 `-10`은 1건과 3건의 차이를 지워 지표 해상도를 잃는다.
- **cooldown을 만들지 않는 이유**: `uq_match_cooldowns_member_active`가 회원당
  `ACTIVE` cooldown을 하나만 허용해 기존 매칭 cooldown과 충돌한다.
- **자동 제한을 넣지 않는 이유**: 2장 표의 필수 요구는 "신고 3회 누적 자동 알림"이며
  자동 제한은 후보다. 또 `AdminMemberService.act()`가 active pool/proposal/group
  회원의 `SUSPEND`를 `409`로 거절하는데 자동 경로는 이 거절을 사용자에게 전달할
  곳이 없다. 그리고 트리거가 관리자의 `RESOLVED` 클릭이라 동기 자동 정지는 관리자
  클릭 한 번을 줄이는 대신 의도하지 않은 정지 위험만 떠안는다.
- **Scheduler를 추가하지 않는 이유**: 기존 Scheduler는 모두 시간 경과로 조건이
  바뀌는 대상(`MemberSuspensionExpiryScheduler`, `MatchNoShowScheduler`,
  `MatchProposalTimeoutScheduler`)을 처리한다. 신고 누적은 관리자 행위로만 변한다.
- **lock 순서**: `AdminMemberService.act()`는 member -> report 순서로 잠그는데
  `AdminReportService.changeStatus()`는 report만 잠근다. 후자에 member 갱신을
  추가하면 순서가 역전되어 관리자 두 명 사이에 deadlock이 가능하다. 5장 원칙에
  따라 두 service를 member -> report로 통일한다.
- **`admin_actions`를 재사용할 수 없는 이유**: `admin_member_id`가 `NOT NULL`이라
  관리자 없이 생성되는 자동 알림 row를 담을 수 없다.

#### 구현 범위

- `V25`: `match_penalty_events`에 `related_report_id`와
  `manner_temperature_delta NUMERIC(5,2)` 추가 및 부분 unique index,
  신규 `admin_safety_alerts` table
- `ReportConfirmationPolicy`: 유효 신고 판정, 30일 distinct 누적 집계, 임계 3
- `AdminReportService.changeStatus()`: lock 순서 통일, `RESOLVED` 경로에 penalty
  event·`penalty_score`·`manner_temperature` 적용, 임계 돌파 시 알림 생성
- `AdminMemberService.act()`: `SUSPEND`/`BAN` 시 `OPEN` 알림 `CLOSED` 처리
- 관리자 알림 조회·확인 API. 기존 `Idempotency-Key`와 cursor pagination 패턴 재사용
- 관리자 회원 상세에 30일 누적 유효 신고 수와 "제한 검토 대상" 표시
- `AdminNav` 미확인 알림 badge와 `/admin/reports` 내 알림 목록·확인 UI

### 4.4 회원 탈퇴와 관리자 강제 탈퇴 4차 — 완료

브랜치:

```text
feature/wbs-10-b-member-withdrawal
```

회원 탈퇴는 관리자 회원 관리가 아니라 인증·회원 요구사항 `AUTH-04`입니다. 본인 탈퇴와
관리자 강제 탈퇴를 한 브랜치에서 구현했습니다. 코어를 공유하지만 진입점을 분리합니다.

```http
DELETE /api/members/me                                 본인 탈퇴
POST   /api/admin/members/{memberId}/forced-withdrawal  관리자 강제 탈퇴
```

관리자 `BAN`과 회원 `WITHDRAWN`은 목적과 복구 가능성이 다르므로 같은 상태 전이나 API로
처리하지 않습니다. 그래서 강제 탈퇴를 `AdminMemberActionType`에 넣지 않고 별도 endpoint로
뒀습니다. **`BAN`은 계정이 남아 되돌릴 수 있고 강제 탈퇴는 익명화라 되돌릴 수 없습니다.**

#### 4.4.1 물리 삭제하지 않는다

`members`를 참조하는 FK 31개가 전부 `ON DELETE RESTRICT`입니다. 신고·제재 감사 이력과 매칭
이력이 탈퇴 회원을 참조하므로 개인정보만 익명화하고 이력은 남깁니다.

| 대상 | 처리 |
| --- | --- |
| `nickname` | `NULL`. 표시 문구는 조회 SQL이 만든다(아래) |
| `email`, `intro`, `profile_image_url`, `profile_image_object_key` | `NULL` |
| `gender_encrypted`, `age_range_encrypted` | `NULL` |
| Object Storage 프로필 이미지 실물 | commit 이후 삭제. 실패는 로그만 남기고 탈퇴를 되돌리지 않는다 |
| `member_travel_styles`, `member_preference_embeddings`, `content_bookmarks` | 물리 삭제 |
| `content_comments` | `VISIBLE` → `DELETED` |
| `content_comment_likes` | 유지. 댓글이 목록에서 빠져 노출되지 않는다 |
| `member_consents` | row 유지 + `revoked_at` 기록 |
| `refresh_tokens` | `AuthService.revokeSession` 재사용 |
| `reports`, `admin_actions`, `match_penalty_events`, `admin_safety_alerts`, `user_blocks`, `match_opponent_exclusions`, 매칭 이력 | 보존 |

##### 닉네임은 컬럼에 저장하지 않고 조회 시점에 만든다 (`V30`)

`V28`은 `nickname`을 표시용 고정 문구(`탈퇴한 회원`)로 덮었습니다. 조회 경로를 건드리지 않고
표시가 맞아떨어진다는 이유였지만, 문구를 컬럼에 두면 두 가지가 깨집니다.

1. **재가입한 계정이 그 문구를 그대로 들고 살아납니다.** `Member.updateSocialProfile`이 소셜
   로그인 때 닉네임을 채우지만, OAuth가 닉네임을 주지 않으면(카카오는 닉네임 제공이 선택
   동의입니다) 채울 값이 없어 문구가 남습니다. 살아 있는 계정이 댓글·매칭 기록·차단 목록에서
   탈퇴한 것처럼 보입니다.
2. **표시 문구가 곧 "익명화됐는지"를 뜻하는 상태 flag가 됩니다.** 문구를 바꾸는 순간 기존 행이
   판정에서 빠져 영구히 복구되지 않습니다.

그래서 `V30`부터 컬럼에는 `NULL`을 저장하고, 표시 문구는 `members`를 join하는 조회 SQL이
`status = 'WITHDRAWN'`일 때 만들어 냅니다.

| 조회 경로 | 쿼리 |
| --- | --- |
| 매칭방 활성 멤버, 완료 멤버, 매칭 기록 | `MatchGroupMemberRepository` 3개 |
| 매칭방 이벤트 actor | `MatchEventRepository` |
| 차단 목록 | `MemberBlockRepository` |
| 관리자 신고 목록·상세(신고자/피신고자) | `AdminReportRepository` 2개 |
| 관리자 안전 알림 | `AdminSafetyAlertRepository` |
| 관리자 회원 목록·상세 | `AdminMemberRepository` |

축제·관광지 댓글(`ContentCommentRepository`)은 치환하지 않습니다. 탈퇴가
`softDeleteAllOnWithdrawal`로 작성 댓글을 `VISIBLE` → `DELETED`로 내리고 목록은 `VISIBLE`만
조회하므로 탈퇴 회원이 애초에 결과에 들어오지 않습니다.

**응답 DTO와 프론트엔드는 바꾸지 않았습니다.** 프론트 4곳이 `nickname.slice(0, 1)`로 첫 글자를
뽑으므로(`ContentCommentItem`, `BlockedMembersPage`, `MatchHistoryPage`, `MatchingConditionPage`)
`null`을 내려보내면 빈 칸이 아니라 렌더링 자체가 죽습니다. 치환은 서버에서 끝냅니다.

문구가 SQL 리터럴로 흩어져 있어 `Member.WITHDRAWN_NICKNAME`만 바꾸면 화면이 조용히 옛 문구를
계속 보여줍니다. `WithdrawnNicknameLabelConsistencyTest`가 상수와 SQL 8곳, `V30`의 일치를
고정합니다.

관리자 회원 검색(`m.nickname ILIKE`)은 그대로 뒀습니다. 컬럼이 `NULL`이 되어 `탈퇴한 회원`으로
검색해도 탈퇴 회원이 나오지 않지만, 상태 filter(`status=WITHDRAWN`)가 이미 있어 대체 수단이
있습니다.

`festival_checkins`에는 GPS 좌표 컬럼이 없습니다(`distance_meters`만). 파기할 위치정보가
따로 없습니다.

#### 4.4.2 탈퇴 스냅샷 컬럼을 따로 둔다 (`V28`)

`V19`의 `chk_members_suspension_period`는 `status <> 'SUSPENDED'`이면 `suspended_at`과
`suspended_until`을 `NULL`로 강제하고, `V27`의 `chk_members_sanction_reason_presence`도 제재
상태가 아니면 사유를 금지합니다. **그래서 정지 회원이 탈퇴하면 잔여 정지 기간을 기존 제재
컬럼에 그대로 둘 수 없습니다.**

두 제약을 `WITHDRAWN`까지 허용하도록 완화하는 대신 스냅샷 컬럼을 분리했습니다. 완화하면 정지
해제와 만료 복구의 버그를 잡아온 불변식 두 개가 동시에 헐거워집니다. `V27`이 사용자 노출용
사유와 관리자 내부용 사유를 컬럼 수준에서 나눈 것과 같은 방식입니다.

| 컬럼 | 역할 |
| --- | --- |
| `withdrawn_from_status` | 탈퇴 시점 상태 |
| `withdrawn_by_admin` | 관리자 강제 탈퇴인지(사실) |
| `withdrawn_rejoin_blocked` | 재가입 영구 거부 여부(관리자 선택). 로그인 경로가 읽는 유일한 값 |
| `withdrawn_suspended_until` | 잔여 정지 종료 시각 |
| `withdrawn_sanction_reason_code` | 재가입 시 `SUSPENDED` 복원에 필요한 사유 code |

CHECK 제약 4개를 함께 둡니다.

- `chk_members_withdrawal_snapshot` — 탈퇴가 아니면 스냅샷이 하나도 남아 있으면 안 된다.
  재가입에서 스냅샷을 지우는 것을 잊으면 여기서 걸린다
- `chk_members_withdrawn_anonymized` — 탈퇴 회원에게 개인정보가 남아 있으면 거부한다
- `chk_members_withdrawn_from_status`, `chk_members_withdrawn_sanction_reason_code` — 값 목록

`chk_admin_actions_type`에는 `FORCED_WITHDRAWAL`을 추가했습니다(`V20`과 같은 방식).

#### 4.4.3 재가입 정책 — 7일 쿨오프

`provider_user_id`는 **익명화하지 않습니다.** 지우면 누가 돌아왔는지 알 수 없어 쿨오프 판정
자체가 불가능합니다.

| 탈퇴 경로 | 7일 이내 | 7일 경과 후 |
| --- | --- | --- |
| 본인 탈퇴 (`ACTIVE`/`PROFILE_REQUIRED`) | 거부 + 재가입 가능 시각 안내 | 허용. 계정 부활 → `PROFILE_REQUIRED` |
| 본인 탈퇴 (`SUSPENDED`) | 거부 | 허용. **잔여 정지 기간을 이어받는다** |
| 강제 탈퇴 `blockRejoin=true` (기본) | 거부 | **영구 거부** |
| 강제 탈퇴 `blockRejoin=false` (탈퇴 대행) | 거부 | 허용. 계정 부활 |

쿨오프 값은 `MemberRejoinCooldownPolicy.COOLDOWN`(7일) 상수입니다. 환경변수로 빼지 않습니다.
정책값이고 환경별로 달라야 할 이유가 없습니다.

**잔여 정지 기간 이어받기가 핵심입니다.** 없으면 30일 정지가 "탈퇴하고 7일 뒤 재가입"으로
23일 세탁됩니다. 재가입 시 `status_before_sanction`은 `PROFILE_REQUIRED`로 둡니다. 프로필이
익명화되어 비어 있으므로 정지 해제 후 돌아갈 곳이 가입 화면인 것이 맞습니다.

이용정지 회원의 탈퇴 확인 dialog는 **"남은 이용정지 기간은 탈퇴로 사라지지 않고 재가입 시
이어진다"** 를 종료 시각과 함께 먼저 보여줍니다. 이 안내가 없으면 사용자가 탈퇴를 제재 해제
수단으로 오해하고 누릅니다.

이 조치가 완벽한 차단은 아닙니다. 새 소셜 계정을 만들면 서버는 다른 사람으로 봅니다. 소셜
로그인만 쓰는 서비스에서는 근본적으로 막을 수 없고, **"탈퇴 버튼 누르고 일주일 기다리기"라는
가장 쉬운 우회 경로**를 닫는 것이 목적입니다.

##### 개인정보는 지우지만 평가와 제재는 승계한다

`Member.withdraw`는 `manner_temperature`와 `penalty_score`를 건드리지 않습니다. `withdraw`가
지우는 것은 개인정보 컬럼뿐이고, 두 집계값과 `match_penalty_events`·`match_cooldowns`·
`member_reviews`·`reports` row는 그대로 남아 재가입 계정이 물려받습니다.

| 재가입 시 | 항목 |
| --- | --- |
| 지워지고 안 돌아옴 | 닉네임, 이메일, 소개, 프로필 사진(OCI 실물 포함), 성별, 연령대, 취향, 취향 임베딩, 찜 |
| 승계됨 | 매너온도, `penalty_score`, penalty 이벤트, 쿨타임, 받은 후기, 신고 이력, 관리자 조치 이력, 잔여 정지 기간 |

**기준은 "누구의 것인가"입니다.** 개인정보는 본인 것이므로 삭제 요구가 성립하지만, 승계되는
값은 전부 **다른 사람이 겪은 일**에서 나옵니다. 매너온도는 관리자가 유효하다고 판정한 신고와
받은 후기에서 깎이고, `penalty_score`는 상대를 기다리게 만든 노쇼·매칭방 취소에서 오릅니다.
탈퇴는 자기 개인정보에 대한 권리이지 **타인이 남긴 평가를 지우는 권리가 아닙니다.**

두 값을 초기화한다면 **둘 다 함께** 해야 합니다. 노쇼는 `penalty_score`만 올리고 신고는
매너온도만 깎으므로, 한쪽만 리셋하면 "노쇼는 세탁 안 되는데 신고는 세탁되는" 정책이 됩니다.
현재는 둘 다 승계로 확정합니다.

매너온도는 관리자 화면에만 노출되고(`AdminMembersPage`) 사용자 화면에는 없습니다.
`penalty_score`는 매칭 제한 안내에 노출되지만 그 자체로 매칭을 막지는 않습니다. 실제 차단은
`match_cooldowns`와 완료 lock이 담당합니다.

#### 4.4.4 영구차단 회원은 본인 탈퇴를 할 수 없다

`MemberAccessInterceptor`가 모든 `/api/**` 요청을 `requireAccessible` 또는 `requireBrowsable`로
통과시키는데 둘 다 `BANNED`를 던집니다. 로그인도 OAuth callback에서 막힙니다. 그래서 영구차단
회원은 `DELETE /api/members/me`에 도달할 수 없고, **자기 개인정보를 지울 방법이 없습니다.**

관리자 강제 탈퇴가 그 경로입니다. 4.8에서 제재 안내 화면에 고객센터 이메일을 이미 노출하고
있으므로 흐름이 이어집니다.

```text
영구차단 회원이 안내 화면의 고객센터 이메일로 삭제 요청
  → 관리자가 /admin/members에서 강제 탈퇴
  → 개인정보 익명화 완료, 재가입은 계속 차단
```

`withdrawn_from_status = 'BANNED'`는 이 경우에만 생깁니다.

#### 4.4.5 강제 탈퇴의 두 용도와 `blockRejoin`

같은 endpoint가 성격이 다른 두 용도로 쓰입니다.

| 용도 | `blockRejoin` |
| --- | --- |
| 제재성 강제 탈퇴(영구차단 회원 정리, 악성 회원) | `true` |
| 탈퇴 대행(로그인이 막힌 정상 회원의 고객센터 요청 처리) | `false` |

서버가 사유 code로 추측하지 않습니다. 되돌릴 수 없는 조치라 관리자가 체크박스로 의식적으로
고르게 하고, 값이 없으면 `true`(차단)로 둡니다.

#### 4.4.6 진행 중 매칭은 거부하지 않고 정리한다

제재(`SUSPEND`/`BAN`)는 활성 매칭이 있으면 `ADMIN_MEMBER_ACTIVE_MATCH_CONFLICT`로 거부하지만,
탈퇴는 정리하고 진행합니다. 거부하면 "만남이 확정된 사람은 탈퇴할 수 없다"가 되어 개인정보
삭제 요구와 충돌합니다.

`MemberWithdrawalMatchCleanupService`가 처리합니다.

| 대상 | 처리 |
| --- | --- |
| `match_pools` (`WAITING`/`LOCKED`/`PROPOSED`) | `CANCELLED` |
| `match_proposals` (`SENT`) | `EXPIRED`. 미응답 timeout 경로를 타지 않게 |
| `match_group_members` (활성) | `cancel("WITHDRAWN")`. `V29`가 이 사유 값을 허용한다 |
| 그룹 계속·취소 판정 | 기존 `MatchGroupContinuationPolicy` |
| 남은 그룹원 통보 | 기존 `MEMBER_CANCELLED`/`MATCH_CANCELLED` 이벤트 |
| 활성 체크인 | `CANCELLED` |

**penalty와 cooldown은 부과하지 않습니다.** 부과 대상이 익명화되므로 의미가 없습니다. "탈퇴로
노쇼 penalty를 피한다"는 우회는 penalty가 아니라 4.4.3의 재가입 정책이 막습니다.

`MatchCancellationService.cancel`과 `MatchPoolCancellationService.cancel`은 재사용하지 않습니다.
전자는 도착 마감이 지나면 예외를 던지고 후자는 쿨타임·penalty를 매깁니다. 그대로 쓰면 탈퇴가
그 시점에 실패해 회원이 탈퇴할 수 없게 됩니다.

새 이벤트 타입을 만들지 않은 것은 `MatchRoomPage`가 이미 처리하는 값을 쓰면 프론트엔드를
건드릴 필요가 없기 때문입니다.

#### 4.4.7 재가입 거부 안내는 4.8 인프라를 재사용한다

OAuth callback은 302 redirect라 body가 없습니다. 여기서 새 경로를 만들면 4.8이 고친
"소셜 로그인에 실패했습니다" 오안내가 재발합니다.

그래서 재가입 거부도 `MemberSanctionException`(`MEMBER_REJOIN_BLOCKED`)으로 던집니다.
`AuthController`의 `catch (MemberSanctionException)`과 `GlobalExceptionHandler`가 이미 안내
cookie와 `403` body를 만들어 주므로 **두 파일을 수정하지 않고** 302 경로와 403 경로가 모두
동작합니다. `MemberSanctionNotice`에 `rejoinAvailableAt`을 추가해 제재 안내와 구분합니다.

사유 문구는 `MemberSanctionReason` enum에 넣지 않습니다. 그 enum은 신고자 보호 전수 검사
(`MemberSanctionReasonTest`)의 대상이고 탈퇴는 제재 사유가 아닙니다.

클래스 이름이 `MemberSanctionException`인데 제재가 아닌 상황에 쓰이는 부조화는 남습니다.
`MemberLoginRestriction*`으로 rename하면 정확해지지만 8개 파일과 프론트엔드 타입, 테스트가
함께 흔들려 이번에는 javadoc으로 범위를 명시하는 선택을 했습니다.

#### 4.4.8 멱등성

`findByIdForUpdate`의 row lock으로 동시 요청을 직렬화합니다.

- 본인 탈퇴는 이미 `WITHDRAWN`이면 조용히 성공합니다. 다만 첫 호출로 상태가 바뀌면
  `MemberAccessInterceptor`가 다음 요청을 먼저 막으므로, 실제로 재도달하는 경우는 같은 access
  token으로 동시에 들어온 요청뿐입니다.
- 관리자 강제 탈퇴는 이미 탈퇴한 회원이면 `ADMIN_MEMBER_STATUS_CONFLICT`로 알립니다. 관리자는
  조치가 실제로 적용됐는지 알아야 합니다. 같은 `Idempotency-Key` 재요청은 기존 조치 API와 같은
  규약으로 한 번만 적용됩니다.

#### 4.4.9 정지 회원의 탈퇴는 허용한다

`SuspendedActivityPolicy.ALLOWED`에 `DELETE /api/members/me`를 등재했습니다. 탈퇴는 개인정보
권리이고, 정지 중이라고 계정 삭제를 막을 수 없습니다. 잔여 정지 기간은 4.4.3대로 이어집니다.

#### 4.4.10 탈퇴 회원은 운영자가 특정할 수 없다

관리자 회원 검색은 `AdminMemberRepository.findPage`에서 `m.nickname ILIKE :query` 하나만
봅니다. 탈퇴 회원의 `nickname`은 `NULL`이므로 `NULL ILIKE '...'`가 항상 거짓이 되어 **어떤
검색어로도 조회되지 않습니다.** 이메일도 `NULL`이고 목록에는 전원이 `탈퇴한 회원`으로 표시되어
여러 명이 탈퇴하면 서로 구분되지 않습니다.

따라서 "고객센터로 연락이 왔는데 어느 계정이었는지 찾아달라"는 요청은 **처리할 수 없습니다.**
`provider_user_id`는 남아 있지만(4.4.3) 사용자가 자기 값을 알 수 없고 검색 조건도 아닙니다.

**이것은 결함이 아니라 익명화의 결과입니다.** 삭제 요청을 처리한 계정을 운영자가 쉽게
역식별할 수 있으면 익명화가 아니게 됩니다. 본인 확인이 필요하면 **본인이 같은 소셜 계정으로
로그인을 시도하는 것**이 유일하고 충분한 경로입니다. 쿨오프 중이면 거부 안내에 재가입 가능
시각이 뜨고, 지났으면 계정이 부활합니다. `provider_user_id`로 매칭되므로 본인만 도달합니다.

운영상 식별이 필요해지면 관리자 목록에 `provider`와 탈퇴일을 노출하는 선택지가 있으나,
역식별 가능성을 다시 여는 변경이므로 별도 결정이 필요합니다.

### 4.5 1:1 문의 센터 후속 — 구현 완료 (수동 검증 대기)

설계와 확정 사항 10건은 `docs/29_MEMBER_INQUIRY_DESIGN.md`, 구현 결과는
`docs/10_PROGRESS_LOG.md`의 `[10-B 문의] 1:1 문의 센터`를 따릅니다.

권장 브랜치는 `feature/wbs-10-b-inquiry-center`였으나, **사용자 결정에 따라
`feature/wbs-10-a-festival-course`에서 구현했습니다.** 7절의 브랜치 규칙과는 다른 예외입니다.

보류 항목으로 남겨두었던 결정은 모두 확정됐습니다.

| 항목 | 확정값 |
| --- | --- |
| 사용자 문의 생성 | `POST /api/members/me/inquiries`. `priority`를 받지 않는다 |
| 긴급 문의 표시 | 관리자만 지정한다 |
| 관리자 답변 | `POST /api/admin/inquiries/{id}/messages`. 스레드형(추가 질문 허용) |
| 공개 범위 | 관리자와 작성자 본인만. 관리자 개인은 "운영팀"으로만 노출 |
| 암호화 | 하지 않는다. 관리자 검색과 `char_length` CHECK를 잃는 대가가 크다 |
| 첨부파일 | 제외. 타인이 남의 파일을 보는 중계 경로가 새로 필요하다 |
| 보관 정책 | 종결 후 1년, 제목·본문만 익명화. `InquiryRetentionScheduler` |

**4.8이 남긴 구멍 중 일부만 메웠습니다.** 영구정지(`BANNED`) 회원은 `requireBrowsable`에 막혀
`/api/**`에 도달할 수 없고 로그인 자체가 안 되므로, **인앱 이의제기 경로를 쓸 수 없습니다.**
그 경로는 고객센터 이메일(`SUPPORT_CONTACT_EMAIL`) 안내를 그대로 유지합니다. 정지
(`SUSPENDED`) 회원은 `SuspendedActivityPolicy` 허용 목록에 등재해 인앱으로 이의를 제기할 수
있습니다(`docs/29` 2.1, 2.3).

### 4.6 로그아웃 — 완료

브랜치: `feature/wbs-10-b-logout`

이전 상태:

- `frontend/src/pages/MyPage.tsx`의 "로그아웃" 버튼이 `navigate('/login')`만 호출했습니다.
- backend에 로그아웃 endpoint가 없어 `access_token` cookie, refresh token, WebSocket session이
  모두 그대로 남았습니다.

#### 확정 사항 4건

| 항목 | 확정값 | 근거 |
| --- | --- | --- |
| 미인증 요청 응답 | `204` 멱등 | 로그아웃은 상태를 없애는 요청이므로 이미 없으면 성공으로 본다. access token 만료 후 버튼을 눌러도 오류 화면 대신 로그인으로 보낸다 |
| WebSocket session | 함께 종료 | 관리자 제재가 이미 revoke → `AFTER_COMMIT` event → `closeAll` 순서를 쓰고 있어 일관된다. 끊지 않으면 공용 기기에서 MatchRoom 상태 이벤트가 계속 흐른다 |
| access token 무효화 | cookie 만료만, 한계 문서화 | stateless JWT(기본 30분)라 서버가 강제 무효화할 수 없다. denylist는 migration과 인증 경로 전체 변경이 필요해 별도 단계로 미룬다 |
| 진행 중 매칭 | 허용하되 정리하지 않음 | 로그아웃은 매칭 취소가 아니다. 로그아웃으로 매칭을 종료시키면 penalty 회피 경로가 된다. 미응답은 기존 proposal timeout·penalty가 처리한다 |

구현 범위:

- `POST /api/auth/logout` — 항상 `204`, `access_token`·`refresh_token` cookie를 `Max-Age=0`으로
  만료. 발급용 `tokenCookie(...)`를 그대로 재사용해 `Path`, `HttpOnly`, `Secure`, `SameSite`
  속성 불일치 가능성을 차단했습니다.
- `AuthService.logout(rawAccessToken)` — 토큰이 없거나 만료·변조되면 조용히 종료합니다.
- `AuthService.revokeSession(memberId)` — `revokeByMemberId` 폐기와 `MemberLoggedOutEvent` 발행.
  4.4 회원 탈퇴가 이 경로를 재사용합니다.
- `domain/auth/event/MemberLoggedOutEvent`와 `MemberLoggedOutEventHandler`(`AFTER_COMMIT` →
  `WebSocketSessionRegistry.closeAll`). 관리자 제재 이벤트를 auth 도메인이 발행하지 않도록
  분리했습니다.
- Frontend `src/api/auth.ts` 신설, `MyPage`의 로그아웃 버튼이 API 호출 후 `/login`으로 이동.
  실패해도 이동하되 오류 문구를 노출하고 중복 클릭을 막습니다.
- `SecurityConfig`, `WebMvcConfig`, migration은 변경하지 않았습니다. `/api/auth/**`는 이미
  `MemberAccessInterceptor` 제외 경로여서 정지된 회원도 로그아웃할 수 있습니다.

검증 결과:

- Backend 전체 726 tests 실패 0건(기존 716 + 신규 10). 신규는 `AuthServiceTest` 4건,
  `AuthControllerTest` 2건, `AuthLogoutIntegrationTest` 4건입니다.
- 통합 테스트는 실제 PostgreSQL에서 refresh token 폐기 후 `refresh` 거절, 재호출 멱등성,
  변조 토큰의 무영향, commit 이후 WebSocket session 종료를 확인합니다.
- Frontend 43 files/370 tests 통과, `npx tsc --noEmit` 통과.

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
- **점수 분해 저장**: 해소됐습니다. `V24`로 `match_attempt_members`에 `jaccard_score`,
  `cosine_score`, `embedding_applied`, `embedding_pair_count`를 추가해 제안 생성 시점에
  함께 저장합니다. 설계 결정과 저장 정의는 `docs/10_PROGRESS_LOG.md` 4-4절을 참고합니다.

### 4.8 회원 제재 사유·기간 통보 후속 — 완료

브랜치:

```text
feature/wbs-10-b-member-sanction-notice
```

구현 결과와 판단 근거는 `docs/10_PROGRESS_LOG.md`의
`[10-B 안전 후속] 회원 제재 사유·기간 통보와 제재 범위 정리 (docs/19 4.8)`을 따른다.

**작업 중 사용자 결정으로 제재 범위 자체가 확정되었다.** 원래 구현은 정지 회원을 전면
차단했는데, 정지는 조회를 막지 않고 활동만 막는 것으로 바꿨다.

| 상태 | 로그인 | 조회 | 활동(체크인·동행 매칭·댓글 작성) |
| --- | --- | --- | --- |
| `SUSPENDED` 이용정지 | 가능 | 가능 | 차단 |
| `BANNED` 영구정지 | 차단 | 차단 | 차단 |

이 서비스는 **로그인 필수**다. 홈(`/`)이 로드 시점에 `GET /api/members/me`를 부르므로
비로그인 사용자는 홈을 볼 수 없다. 정지 회원의 "조회 가능"은 이 전제 안의 범위다.

활동 판정은 `SuspendedActivityPolicy`의 차단 목록으로 한다. 목록 누락은
`SuspendedActivityPolicyCoverageTest`가 상태 변경 endpoint 전수 분류 검사로 막는다.
**신고·차단·동의 철회는 정지 중에도 허용한다.** 정지는 신고 권리를 박탈하는 조치가 아니고,
만남 종료 후 14일 안에 정지되면 신고 경로 자체가 사라지기 때문이다.

**아래 조사 내용 중 한 가지가 틀렸다.** "`403` 응답에 담는 방식이 유일한 저비용 경로"라고
적었지만 제재로 막히는 경로는 세 개이고, 그중 **사용자가 가장 먼저 부딪히는 OAuth 로그인은
302 redirect라 응답 body가 없다.** 게다가 callback의 `catch (RuntimeException)`이 제재
예외를 삼켜 `oauthError=oauth_failed`로 보내고 있었으므로 로그인 화면은 "잠시 후 다시
시도해 주세요"라는 **틀린 안내**를 띄웠다.

그래서 실제 구현은 세 경로에 같은 단기 notice cookie를 내려주고 화면이
`GET /api/auth/sanction-notice` 하나로 사유·기간을 읽는 방식으로 통일했다. 사유·기간을
query parameter로 넘기지 않은 이유는 URL·access log·브라우저 history에 제재 정보가 남고
누구나 URL을 위조해 안내 화면을 띄울 수 있기 때문이다.

4.3 정책 확정 과정에서 분리한 항목입니다. **현재는 관리자가 수동으로 정지해도
사용자가 이유와 기간을 전혀 알 수 없습니다.** 자동 제한 여부와 무관하게 그 자체로
필요한 기능이므로 독립 항목으로 둡니다.

현재 상태 조사 결과:

- `MemberAccessPolicy.requireAccessible()`이 `SUSPENDED`를 `MEMBER_SUSPENDED`
  `403`으로 막고, 메시지는 "이용이 일시 정지된 계정입니다."로 고정이다. 기간과
  사유가 없다.
- 사용자 알림 채널이 없다. STOMP는 `useMatchingSession`(`/matching`)과
  `useMatchRoom`(`/match-room`)에서만 연결되므로 다른 화면의 사용자에게 도달하지
  못한다. Web Push는 VAPID 키·구독 table·권한 UI가 전무하고
  `vite-plugin-pwa`가 `generateSW` 전략이라 custom service worker 파일 자체가 없다.
  메일 발송 인프라도 없다.
- 인앱 알림 목록 방식은 정지 통보에 쓸 수 없다. `MemberAccessPolicy`가 `SUSPENDED`
  회원의 요청을 `403`으로 막으므로 정지된 사용자는 알림 조회 API 자체를 부를 수 없다.

그래서 정지 통보는 **접근 시 응답에 담는 방식**이 유일한 저비용 경로입니다.

필수 범위(전부 구현 완료):

- `403` 응답에 `suspendedUntil`과 `reasonCode`를 포함 — `ErrorResponse.sanction`에 담는다.
- 로그인·차단 화면에 제재 기간과 사유를 표시하는 안내 card —
  `AccountRestrictionNotice`. 제재 안내에서는 로그인 버튼을 감춘다.
- 문의 경로 — 문의센터 화면(4.5)이 보류라 **고객센터 이메일**을 안내에 표시한다.
  주소는 환경변수 `SUPPORT_CONTACT_EMAIL`로만 주입하고 저장소에는 넣지 않는다. 영구정지
  사용자가 이의를 제기할 유일한 경로다.
  - **4.5 구현 후에도 영구정지 사용자에게는 여전히 유일한 경로다.** `BANNED`는
    `MemberAccessPolicy.requireBrowsable`에 막혀 `/api/**`에 도달할 수 없고 로그인 자체가
    안 되므로 인앱 문의 폼을 쓸 수 없다. 정지(`SUSPENDED`) 사용자만 인앱 경로가 열렸다
    (`docs/29_MEMBER_INQUIRY_DESIGN.md` 2.1).
- **신고자 보호 제약**: `docs/05_MATCHING_POLICY.md`의 신고 정책과 5장 원칙에 따라
  신고자 identity를 노출하지 않는다. `reasonCode` 수준(`COMMUNITY_GUIDELINE` 등)
  까지만 노출하고 "신고 3건 누적"처럼 신고자 수를 추정할 수 있는 문구는 사용하지
  않는다. 이 제약 설계가 이 항목의 핵심이다.

신고자 보호를 위해 구현에서 추가로 정한 것:

- 사용자 노출 문구는 `MemberSanctionReason` enum 한 곳에만 둔다. 프론트가 code를 문구로
  바꾸면 노출 심사를 두 곳에서 해야 한다.
- 관리자 자유 입력 note(`admin_actions.reason`)는 사용자 응답에 담지 않는다. 사용자
  노출용은 `members.sanction_reason_code`로 컬럼 수준에서 분리했다.
- 제재 시작 시각(`suspendedAt`)은 노출하지 않는다. 제재 시점이 신고 시점을 좁히는 단서가
  된다. `suspendedUntil`만 담는다.
- 위 세 제약은 `MemberSanctionReasonTest`, `AdminMemberIntegrationTest`,
  `GlobalExceptionHandlerSanctionTest`가 테스트로 고정한다.

Web Push는 이 항목 범위 밖입니다. iOS Safari가 홈화면에 추가한 PWA만 push를
지원해 축제 현장 사용자 상당수에 도달하지 못하는 문제도 함께 검토해야 합니다.

### 4.9 manner temperature 회복과 매칭 제한 후속 — 미착수

권장 브랜치:

```text
feature/wbs-10-b-manner-temperature-recovery
```

4.3에서 미도입으로 확정한 항목입니다.

- `member_reviews` table은 `V4`에 있으나 Backend/Frontend 코드가 없다. 후기 기능이
  없으면 `manner_temperature`를 올릴 경로가 없다.
- 따라서 "30도 이하 매칭 제한"은 후기 기반 상승 경로와 함께 도입한다. 단독 도입 시
  하강 전용 지표에 제한을 거는 구조가 되어 복구 수단이 관리자 수동뿐이다.
- 도입 시 `MatchPoolEntryService.enter()`의 검증과
  `MatchingRestrictionResponse`에 온도 조건을 함께 추가해야 한다. 현재 Frontend는
  `useMatchingSession`이 restriction 응답에서 화면 상태를 파생하므로, 새 제한
  상태를 추가할 때 상태 파생과 라우팅까지 따라가 실제로 도달 가능한지 확인한다.
- 4.3에서 확인한 비대칭도 함께 정리한다. 누적 유효 신고 카운트는 30일 window로
  자동 감소하지만 `manner_temperature`는 영구 하강이다.

**관리자 온도 수동 조정을 이 절에 함께 넣는다.** 2026-09-09 사용자 확인 사항이다. 현재
`manner_temperature`는 신고 확정으로만 내려가는 하강 전용 지표라 복구 수단이 아예 없다.
`AdminMemberService`에 조정 진입점과 `admin_actions` 감사 로그를 추가하면 후기 기능이 없어도
복구 경로가 생긴다. 후기 기반 상승과 함께 구현한다.

### 4.10 만남 종료 후 신고 진입점 후속 — 완료

브랜치: `feature/wbs-10-b-match-report-entry`

이전 상태:

- 접수 API는 만남 종료 후 30일까지 신고를 허용했지만 그 기간에 신고할 화면 경로가 없었다.
  **실질 신고 가능 기간이 0이었다.**
- `신고하기` 버튼이 `MatchRoomPage`에만 있고, 그 화면은
  `matching_group.status IN ('CONFIRMED','IN_PROGRESS')`인 현재 그룹에만 의존했다.

#### 확정 사항 4건

| 항목 | 확정값 | 근거 |
| --- | --- | --- |
| 신고 가능 기간 | 14일, `completed_at`/`cancelled_at` 기준 | 7일은 성희롱·안전 사안을 놓치고 축제가 주말에 열리는 패턴과 맞지 않는다. 기준 시각은 기존 컬럼을 유지해 변경 범위를 줄였다 |
| 화면 위치 | MyPage "매칭 기록" 카드 → `/mypage/matches` 별도 화면 | `/mypage/blocks`와 같은 패턴이고 MyPage가 길어지지 않으며 pagination을 붙이기 쉽다 |
| 노출 범위 | `COMPLETED` + `CANCELLED` 전체 이력, 기간 지난 건은 버튼만 비활성화 | 목록이 매칭 기록 열람을 겸한다. 취소 과정에서 생긴 문제도 신고할 수 있어야 하고 접수 API도 두 상태를 받는다 |
| 이미 신고한 상대 | 버튼 비활성화 + `신고됨` 배지 | 접수는 이미 멱등이지만 안내가 없으면 접수 여부를 확인할 수 없다 |

구현 범위:

- `MatchReportWindowPolicy` 신설. 기간 상수와 판정을 접수 API와 목록이 공유한다. 화면에서
  "신고 가능"으로 보인 항목이 접수에서 거절되는 어긋남을 구조적으로 막는다.
- `GET /api/members/me/match-history` 신설. 조회 대상을 JWT의 회원으로 고정하고 회원 ID를
  요청에서 받지 않는다. `reportable`·`reportableUntil`·`reported`를 서버가 판정해 내려준다.
- `MatchGroupRepository.findHistoryByMemberId` — (종료 시각, group id) 복합 cursor. 완료
  판정에 쓰는 `findLatestCompletedByMemberId`는 목적이 달라 건드리지 않았다.
- `MatchGroupMemberRepository.findHistoryMembersByGroupIds` — 기존 완료 참가자 조회는
  `status = 'COMPLETED'`로 좁혀져 취소 그룹을 담지 못해 별도로 추가했다. 본인은 제외한다.
- `MatchReportRepository.findReportedPairs` — 사유를 접어 신고 이력 여부만 판정한다.
- Frontend `/mypage/matches` 화면 신설. 신고 dialog는 `useMatchReport`와 `ReportDialog`를
  그대로 재사용했다. MyPage의 "준비 중" 매칭 기록 카드를 실제 진입점으로 연결했다.
- migration 없음. 신고 접수 API와 매칭 transaction 경계는 변경하지 않았다.

기간을 30일에서 14일로 줄였지만 **신고 누적 집계 window**
(`ReportConfirmationService.AGGREGATION_WINDOW_DAYS`)와 **차단 허용 기간**
(`MatchBlockService.BLOCK_WINDOW_DAYS`)은 목적이 달라 30일로 유지했다.

검증 결과:

- `MatchHistoryIntegrationTest` 10건(PostgreSQL Testcontainers) — 종료·취소 함께 반환,
  진행 중 제외, 본인 제외, 미참여자 차단, 14일 경계, 사유 무관 신고됨 표시, 타인 신고
  비노출, cursor 연속성, 동일 시각 tiebreaker, 위조 cursor 거절.
- 기존 `MatchReportIntegrationTest`의 30일 경계 테스트를 14일로 갱신했다.
- Backend 전체 737 tests 실패 0건(기존 727 + 신규 10).
- Frontend 45 files/379 tests, `npx tsc --noEmit` 통과.
- 2026-09-08 브라우저 수동 검증 PASS. 실제 계정 2개로 매칭 확정 → 양쪽 도착 → `COMPLETED`
  전환 후, `/mypage/matches` 노출·신고 접수·`신고됨` 배지 전환·관리자 화면 노출을 확인했다.
  14일이 지난 기존 기록의 버튼 잠금과 취소된 만남의 `취소됨` 배지도 함께 확인했다.

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
feature/wbs-10-b-report-safety-automation     — 완료 (PR #50)
feature/wbs-10-b-member-withdrawal            — 미착수 (4.4)
feature/wbs-10-b-inquiry-center               — 미사용 (4.5는 10-a 브랜치에서 구현)
feature/wbs-10-b-member-sanction-notice       — 완료 (PR #56, 4.8)
feature/wbs-10-b-token-refresh                — 완료 (PR #57, 프론트엔드 token 갱신)
feature/wbs-10-b-member-withdrawal            — 완료 (4.4, 본인 탈퇴 + 관리자 강제 탈퇴)
feature/wbs-10-b-logout                       — 완료 (4.6)
feature/wbs-10-b-match-report-entry           — 완료 (4.10)
feature/wbs-10-b-inquiry-center               — 미착수 (4.5)
feature/wbs-10-b-consent-followup             — 미착수 (4.7)
feature/wbs-10-b-manner-temperature-recovery  — 미착수 (4.9, 온도 수동 조정 포함)
```

4.6 로그아웃이 먼저 끝났으므로 4.4 회원 탈퇴는 `AuthService.revokeSession(memberId)`을 그대로
재사용했습니다. 남은 항목은 4.5, 4.7, 4.9입니다.

각 브랜치는 `dev`에서 분기하고 작업 완료 후 PR로 `dev`에 병합합니다. 앞 단계 PR이
병합되기 전에 다음 단계를 같은 작업 트리에 누적하지 않습니다.

## 8. 다음 CLI 세션 인수인계 프롬프트

4.3 정책은 확정됐습니다. 아래는 4.3 구현을 이어갈 때 사용합니다.

```text
AGENTS.md와 docs/*.md를 확인하고, docs/19_ADMIN_MEMBER_SAFETY_ROADMAP.md 4.3절과
docs/05_MATCHING_POLICY.md의 "관리자 유효 판정 신고 (REPORT_CONFIRMED)" 절을
기준으로 4.3 구현을 이어가줘.

정책 8항목은 이미 확정됐다. 다시 논의하지 말고 확정값 그대로 구현해줘.
자동 제한은 회원 status를 바꾸지 않고 관리자 알림까지만이다.

현재 목표 브랜치는 feature/wbs-10-b-report-safety-automation이고 dev에서 분기했다.
기존 reports, admin_actions, members, match_penalty_events schema와
관리자 인가·cursor pagination·Idempotency-Key·UI 패턴을 재사용해줘.

주의:
- report와 member를 함께 잠그는 모든 service는 member -> report 순서를 쓴다.
  AdminReportService.changeStatus()의 기존 lock 순서를 바꿔야 한다.
- dev DB는 공유 자원이다. 조회만 하고 데이터를 쓰지 마.
- 새 migration 번호는 저장소 파일 목록과 공유 dev DB flyway_schema_history를
  함께 확인해서 정해줘.
- FestivalControllerTest 9건, TourPlaceControllerTest 5건,
  FestivalCheckinCancelledEventHandlerIntegrationTest 1건은 origin/dev 단독에서도
  실패하는 기존 이슈다. 내 변경 탓으로 오판하지 마.
- Testcontainers를 돌리려면 Docker Desktop이 필요하고 JDK 17로 실행해야 한다.
```
