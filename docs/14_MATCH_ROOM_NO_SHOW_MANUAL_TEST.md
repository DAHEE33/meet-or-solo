# MatchRoom 취소·NO_SHOW 수동 테스트

## 1. 문서 목적

이 문서는 `feature/wbs-10-b-match-room-no-show` 브랜치의 수동 검증 기준과
실행 결과를 한곳에 기록합니다.

대상 범위는 다음과 같습니다.

- `못 갈 것 같아요`를 통한 확정 후 자발적 취소
- 확정 후 3분 기준 penalty/cooldown
- `confirmed_at + 30분` 도착 마감
- `NO_SHOW` Scheduler
- 취소·NO_SHOW 이후 group 유지 또는 종료
- `confirmedMemberCount`와 `currentMemberCount`
- REST 복원, polling, WebSocket 알림 이후 화면 동기화
- 도착 예정 시간 선택·변경·마감 경계 UX

meeting point, Kakao Maps, `COMPLETED`, 자유 채팅, Redis와 운영 배포는 이
수동 테스트 범위에 포함하지 않습니다.

이 문서는 테스트 중 발견한 문제를 즉시 고치기 위한 작업 지시서가 아닙니다.
먼저 증거와 재현 절차를 누적하고, 항목별 판정이 끝난 뒤 수정 범위를 한꺼번에
요청하기 위한 기준 문서로 사용합니다.

## 2. 판정 표기

| 표기 | 의미 |
| --- | --- |
| `PASS` | 기대 결과와 API/DB/화면 결과가 모두 일치함 |
| `FAIL` | 재현 가능한 불일치가 있음 |
| `BLOCKED` | 환경 또는 테스트 데이터 문제로 실행하지 못함 |
| `PENDING` | 아직 실행하지 않음 |
| `N/A` | 현재 수동 환경에서는 검증 대상이 아님 |

각 시나리오 결과에는 가능하면 다음 증거를 남깁니다.

- 실행 시각(KST)
- 일반/시크릿 브라우저의 실제 member ID
- 관련 `group_id`, `pool_id`, `proposal_id`
- 실패한 Request URL, Method, HTTP status와 response body
- 실행 전후 DB 조회 결과
- 화면 캡처 파일명
- 최종 판정과 수정 메모

## 3. 테스트 환경

### 3.1 수동 화면 테스트

```text
Backend: Windows 개발 PC, localhost:8080
Frontend: localhost:5173
PostgreSQL: Oracle dev 서버의 dev DB
SSH tunnel: 127.0.0.1:15432 -> dev 서버 localhost:15432 -> PostgreSQL 5432
테스트 회원: member 2, member 27
테스트 축제: festival 144
```

- `.env`의 `DB_HOST=127.0.0.1`, `DB_PORT=15432`를 유지합니다.
- 수동 화면 테스트에서 local Docker PostgreSQL을 사용하지 않습니다.
- dev DB 프로필 복호화에는 기존 `.env`의 `PROFILE_ENCRYPTION_KEY`를 사용합니다.
- 비밀값은 화면 캡처, 문서, SQL 결과에 기록하지 않습니다.

### 3.2 자동 테스트

Backend PostgreSQL 통합 테스트는 Docker Desktop/Testcontainers의 별도 DB를
사용합니다. dev DB와 SSH tunnel을 사용하지 않습니다.

이 브랜치의 Gradle 자동 테스트는 앞선 작업에서 수행했습니다. frontend 변경이
생기면 관련 Vitest, TypeScript 검사와 production build를 다시 실행합니다.

## 4. 공통 사전 점검

### MT-ENV-01 서비스 연결

| 확인 항목 | 기대 결과 | 결과 | 메모 |
| --- | --- | --- | --- |
| backend health | `/api/health` 200 | `PASS` | 2026-08-03 확인 |
| frontend | `localhost:5173` 접근 | `PASS` | |
| SSH tunnel | `127.0.0.1:15432` 연결 | `PASS` | |
| dev DB 사용 | DB 이름과 연결 대상 확인 | `PASS` | local Docker DB 금지 |

### MT-ENV-02 Flyway V14

```sql
SELECT
    installed_rank,
    version,
    description,
    script,
    installed_on,
    success
FROM flyway_schema_history
WHERE version = '14';
```

기대 결과:

```text
version = 14
success = true
```

실제 V14 컬럼 확인:

```sql
SELECT
    table_name,
    column_name,
    data_type,
    is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND (
      (table_name = 'match_groups'
       AND column_name IN ('cancel_reason', 'cancelled_at'))
      OR
      (table_name = 'match_group_members'
       AND column_name IN (
           'cancel_reason',
           'cancelled_at',
           'no_show_at',
           'allow_minimum_two'
       ))
      OR
      (table_name = 'match_cooldowns'
       AND column_name = 'related_group_id')
  )
ORDER BY table_name, column_name;
```

취소 사유 조회에는 V14의 `cancel_reason` 컬럼을 사용합니다.

| 판정 | 실행 시각 | 메모 |
| --- | --- | --- |
| `PENDING` | | |

### MT-ENV-03 회원과 축제

```sql
SELECT
    id,
    nickname,
    status,
    penalty_score,
    manner_temperature
FROM members
WHERE id IN (2, 27)
ORDER BY id;

SELECT id, title, status, event_start_date, event_end_date
FROM festivals
WHERE id = 144;
```

기대 결과:

- member 2, 27의 `status`가 `ACTIVE`입니다. 현재 schema에서는
  `status=ACTIVE`가 프로필 완료 후 활성 상태를 나타냅니다.
- festival 144가 `ACTIVE`입니다.

### MT-ENV-04 유효 체크인

```sql
SELECT
    id,
    member_id,
    festival_id,
    status,
    checked_in_at,
    expires_at,
    current_timestamp AS db_now,
    expires_at > current_timestamp AS unexpired
FROM festival_checkins
WHERE member_id IN (2, 27)
  AND festival_id = 144
ORDER BY member_id, checked_in_at DESC, id DESC;
```

각 회원의 최신 테스트 row가 다음을 만족해야 합니다.

```text
status = ACTIVE
unexpired = true
```

`status=ACTIVE`여도 `expires_at <= current_timestamp`이면 매칭 신청에는
유효하지 않습니다.

| 판정 | 실행 시각 | 메모 |
| --- | --- | --- |
| `PASS` | 2026-08-03 | 만료 check-in을 새 수동 테스트 check-in으로 교체 |

### MT-ENV-05 Scheduler 설정

| 설정 | 목적 | 기대값 | 결과 |
| --- | --- | --- | --- |
| `MATCHING_SCHEDULER_ENABLED` | pool 탐색과 proposal 처리 | `true` | `PENDING` |
| `MATCHING_NO_SHOW_SCHEDULER_ENABLED` | 30분 마감 NO_SHOW | `true` | `PENDING` |

## 5. 공통 Network 확인 방법

두 브라우저에서 `F12 > Network`를 열고 `Preserve log`를 활성화합니다.
필터는 `matching`을 사용합니다.

초기 `/matching` REST snapshot:

```text
GET /api/matching/pools/me/current
GET /api/matching/proposals/me/active
GET /api/matching/groups/me/current
GET /api/matching/me/restrictions
```

상태방 REST:

```text
GET /api/matching/groups/me/current
GET /api/matching/groups/me/current/events
PUT /api/matching/groups/me/current/arrival-time
PUT /api/matching/groups/me/current/arrival
PUT /api/matching/groups/me/current/cancellation
```

실패 시 다음을 기록합니다.

- Request URL
- Request Method
- Request Payload
- HTTP Status
- Response body
- 요청 호출 순서
- REST 또는 WebSocket 여부
- 같은 동작을 다시 수행했을 때 동일 요청이 발생하는지

## 6. 시나리오별 dev DB 초기화

자발적 취소나 NO_SHOW가 한 번 발생하면 다음 상태가 남습니다.

- 종료된 `MATCHED` pool
- attempt, proposal, response, group과 group member 이력
- `match_events`
- 귀책 회원의 `match_penalty_events`, `match_cooldowns`
- `members.penalty_score`
- 이미 사용했거나 만료된 `festival_checkins`

따라서 독립 시나리오는 같은 DB 상태에서 연속 실행하지 않습니다. 각 테스트 ID의
사전 조건에 따라 아래 초기화 유형을 먼저 선택합니다.

| 초기화 유형 | 용도 | penalty 이력 | cooldown | member penalty score |
| --- | --- | --- | --- | --- |
| `RESET-A` | 각 시나리오를 첫 발생 기준으로 독립 검증 | 삭제 | 삭제 | 0으로 초기화 |
| `RESET-B` | 같은 날 두 번째 취소/NO_SHOW 횟수 검증 | 보존 | 이전 active만 종료 | 보존 |
| 초기화 없음 | 동일 요청 멱등성, Scheduler 재실행 검증 | 보존 | 보존 | 보존 |

`RESET-A`, `RESET-B`는 dev DB의 테스트 전용 festival 144와 member 2, 27만
대상으로 합니다. members, OAuth 계정, festival 144 자체는 삭제하지 않습니다.
실행 전에 SSH tunnel과 현재 DB 이름을 다시 확인합니다.

### 6.1 초기화 전 대상 확인

아래 조회 결과에서 festival 144가 수동 테스트 축제인지 먼저 확인합니다.

```sql
SELECT current_database(), current_user, current_timestamp;

SELECT id, title, status
FROM festivals
WHERE id = 144;

SELECT id, nickname, status, penalty_score
FROM members
WHERE id IN (2, 27)
ORDER BY id;
```

예상 festival:

```text
id = 144
title = Matching UI test festival
status = ACTIVE
```

대상이 다르면 아래 초기화 SQL을 실행하지 않습니다.

### 6.2 RESET-A: 독립 시나리오 완전 초기화

적용 대상:

- `MT-BASE-01`
- `MT-ARRIVAL-*`
- `MT-CANCEL-01`
- `MT-CANCEL-02`
- `MT-NOSHOW-01`
- 서로 영향을 주면 안 되는 첫 발생 테스트

이 SQL은 matching 관련 테스트 이력과 member 2, 27의 penalty/cooldown을
초기화한 뒤 2시간짜리 새 check-in을 만듭니다.

```sql
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM festivals
        WHERE id = 144
          AND title = 'Matching UI test festival'
    ) THEN
        RAISE EXCEPTION 'festival 144 is not the expected manual-test festival';
    END IF;

    IF (SELECT count(*) FROM members WHERE id IN (2, 27)) <> 2 THEN
        RAISE EXCEPTION 'manual-test members 2 and 27 are required';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM match_pools
        WHERE member_id IN (2, 27)
          AND festival_id <> 144
          AND status IN ('WAITING', 'LOCKED', 'PROPOSED')
    ) THEN
        RAISE EXCEPTION 'a manual-test member has an active pool for another festival';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM match_group_members gm
        JOIN match_groups g ON g.id = gm.group_id
        WHERE gm.member_id IN (2, 27)
          AND g.festival_id <> 144
          AND gm.status IN ('JOINED', 'ARRIVAL_TIME_SELECTED', 'ARRIVED')
          AND g.status IN ('CONFIRMED', 'IN_PROGRESS')
    ) THEN
        RAISE EXCEPTION 'a manual-test member has an active group for another festival';
    END IF;
END
$$;

DELETE FROM match_penalty_events
WHERE member_id IN (2, 27);

DELETE FROM match_cooldowns
WHERE member_id IN (2, 27);

UPDATE members
SET penalty_score = 0,
    updated_at = current_timestamp
WHERE id IN (2, 27);

DELETE FROM match_events
WHERE group_id IN (
        SELECT id
        FROM match_groups
        WHERE festival_id = 144
    )
   OR attempt_id IN (
        SELECT id
        FROM match_attempts
        WHERE festival_id = 144
    );

DELETE FROM match_responses
WHERE attempt_id IN (
        SELECT id
        FROM match_attempts
        WHERE festival_id = 144
    );

DELETE FROM match_group_members
WHERE group_id IN (
    SELECT id
    FROM match_groups
    WHERE festival_id = 144
);

DELETE FROM match_groups
WHERE festival_id = 144;

DELETE FROM match_proposals
WHERE attempt_id IN (
        SELECT id
        FROM match_attempts
        WHERE festival_id = 144
    );

DELETE FROM match_attempt_members
WHERE attempt_id IN (
        SELECT id
        FROM match_attempts
        WHERE festival_id = 144
    );

DELETE FROM match_attempts
WHERE festival_id = 144;

DELETE FROM match_pools
WHERE festival_id = 144;

UPDATE festival_checkins
SET status = 'EXPIRED',
    updated_at = current_timestamp
WHERE member_id IN (2, 27)
  AND festival_id = 144
  AND status = 'ACTIVE';

INSERT INTO festival_checkins (
    member_id,
    festival_id,
    distance_meters,
    status,
    checked_in_at,
    expires_at,
    created_at,
    updated_at
)
SELECT
    target.member_id,
    144,
    100,
    'ACTIVE',
    current_timestamp,
    current_timestamp + INTERVAL '2 hours',
    current_timestamp,
    current_timestamp
FROM (VALUES (2::bigint), (27::bigint)) AS target(member_id);

COMMIT;
```

중간에 오류가 발생하면 transaction을 종료합니다.

```sql
ROLLBACK;
```

### 6.3 RESET-A 실행 후 검증

```sql
SELECT id, member_id, festival_id, status
FROM match_pools
WHERE festival_id = 144
   OR member_id IN (2, 27);

SELECT id, member_id, event_type, score_delta, related_group_id
FROM match_penalty_events
WHERE member_id IN (2, 27);

SELECT id, member_id, reason, status, starts_at, expires_at, related_group_id
FROM match_cooldowns
WHERE member_id IN (2, 27);

SELECT id, nickname, status, penalty_score
FROM members
WHERE id IN (2, 27)
ORDER BY id;

SELECT
    id,
    member_id,
    festival_id,
    status,
    checked_in_at,
    expires_at,
    expires_at > current_timestamp AS unexpired
FROM festival_checkins
WHERE member_id IN (2, 27)
  AND festival_id = 144
ORDER BY member_id, id DESC;
```

기대 결과:

- pool, penalty event, cooldown 조회는 0건입니다.
- member 2, 27의 `penalty_score=0`입니다.
- 각 회원의 최신 check-in은 `ACTIVE`, `unexpired=true`입니다.
- members, OAuth 계정, festival 144는 그대로 유지됩니다.

### 6.4 RESET-B: 당일 반복 횟수 테스트 준비

`MT-NOSHOW-04`처럼 같은 KST 날짜의 두 번째 귀책 횟수를 검증할 때는
`match_penalty_events`와 `members.penalty_score`를 지우면 안 됩니다. 이전
시나리오의 group/pool만 정리하고 active cooldown을 종료한 뒤 새 check-in을
준비합니다.

아래 SQL은 먼저 `RESET-B` 대상 이력이 정확한지 확인한 뒤에만 실행합니다.

```sql
SELECT id, member_id, event_type, score_delta, related_group_id, created_at
FROM match_penalty_events
WHERE member_id IN (2, 27)
ORDER BY id;

SELECT id, nickname, penalty_score
FROM members
WHERE id IN (2, 27)
ORDER BY id;
```

반복 테스트에서는 이전 penalty event가 정확히 1회 존재하는 회원을 기록합니다.
그 다음 다음 차이를 적용합니다.

- `match_penalty_events`를 삭제하지 않습니다.
- `members.penalty_score`를 0으로 바꾸지 않습니다.
- 기존 `match_cooldowns`는 삭제하지 않고 테스트 목적상 `EXPIRED`로 전환합니다.
- 그 외 group/pool/attempt/proposal/response/event 정리와 새 check-in 생성은
  `RESET-A`와 같은 순서로 실행합니다.

cooldown 종료 SQL:

```sql
UPDATE match_cooldowns
SET status = 'EXPIRED'
WHERE member_id IN (2, 27)
  AND status = 'ACTIVE';
```

`RESET-B` 전체 실행은 첫 귀책 결과의 member와 event를 확인한 뒤 테스트 기록에
대상을 적고 수행합니다. 잘못된 회원의 당일 이력을 보존하지 않도록 독립된
고정 SQL로 자동 실행하지 않습니다.

### 6.5 초기화하지 않는 테스트

다음 검증 중에는 DB를 초기화하지 않습니다.

- 같은 취소 요청을 반복했을 때의 멱등성
- 같은 NO_SHOW Scheduler를 재실행했을 때의 멱등성
- WebSocket 수신 뒤 REST snapshot 복원
- 새로고침 전후 동일 group 상태 비교
- 취소 직후 penalty/cooldown과 종료 화면 확인

이 검증이 끝나기 전에 `RESET-A`를 실행하면 필요한 증거가 사라집니다.

## 7. 기본 매칭과 상태방 진입

### MT-BASE-01 2명 자동 매칭

사전 조건:

- member 2, 27 모두 festival 144 유효 check-in 보유
- active pool/group/cooldown이 테스트를 방해하지 않음

절차:

1. 두 브라우저에서 희망 인원 2명을 선택합니다.
2. 필요하면 `2명만 모여도 진행`을 활성화합니다.
3. 양쪽에서 `자동 매칭 신청`을 누릅니다.
4. 양쪽 proposal을 수락합니다.
5. 동일한 상태방으로 진입합니다.

통과 기준:

- [ ] 두 `POST /api/matching/pools`가 200입니다.
- [ ] 양쪽에 proposal이 표시됩니다.
- [ ] 먼저 수락한 회원은 응답 대기 화면을 봅니다.
- [ ] 마지막 수락 뒤 양쪽 current group이 200입니다.
- [ ] 양쪽 `groupId`가 같습니다.
- [ ] `confirmedMemberCount=2`, `currentMemberCount=2`입니다.
- [ ] 새로고침 후 같은 상태를 복원합니다.

| 판정 | group_id | 실행 시각 | 메모 |
| --- | ---: | --- | --- |
| `PASS` | | 2026-08-03 | 두 회원 매칭과 상태방 진입 확인 |

## 8. 도착 예정 시간

### MT-ARRIVAL-01 허용 선택지와 저장

절차:

1. 본인 도착 예정 시간에서 5분을 선택합니다.
2. 10분, 20분 또는 25분으로 변경합니다.
3. 상대 화면과 새로고침 후 snapshot을 확인합니다.

통과 기준:

- [ ] 신규 선택지는 5/10/20/25분만 제공합니다.
- [ ] PUT 성공 응답의 `arrivalMinutes`가 선택값과 같습니다.
- [ ] 본인 화면에 `선택한 도착 시간`이 표시됩니다.
- [ ] 현재 선택 option이 시각적으로 구분됩니다.
- [ ] 상대 목록은 실시간 잔여시간이 아니라 `선택한 도착 시간: N분`으로 표시합니다.
- [ ] 상대 목록에 선택 시각과 선택값으로 계산한 실제 `예상 도착 시각`을 함께 표시합니다.
- [ ] 상대 화면이 polling/WebSocket 또는 새로고침으로 변경값을 반영합니다.
- [ ] timeline event가 한 번 생성됩니다.

| 판정 | 실행 시각 | 증거/메모 |
| --- | --- | --- |
| `PASS` | 2026-08-03 | 변경 동기화 확인. 문구 개선 반영 후 재검증 필요 |

### MT-ARRIVAL-02 동일 값 재선택

정책:

- 동일한 `arrivalMinutes` 반복은 멱등 성공입니다.
- `arrival_time_selected_at`과 예상 도착 시각을 연장하지 않습니다.
- event와 알림을 중복 생성하지 않습니다.

통과 기준:

- [x] 현재 선택 option은 `현재 선택`으로 표시되고 다시 누를 수 없습니다.
- [x] 현재 선택 option이 비활성화되어 같은 값으로 예정 시각을 연장할 수 없습니다.
- [x] 비활성화된 현재 선택 option에서는 PUT 요청과 event가 추가되지 않습니다.
- [x] 사용자가 무반응을 오류로 오해하지 않도록 설명 문구가 표시됩니다.

| 구분 | 판정 | 실행 시각 | 증거/메모 |
| --- | --- | --- | --- |
| 화면 | `PASS` | 2026-08-03 | `N분 · 현재 선택` 표시와 option 비활성화 확인 |
| 정책 | `PASS` | 2026-08-03 | 같은 값으로 예정 시각을 연장하지 않는 안내 확인 |
| 자동 회귀 | `PASS` | 2026-08-03 | 관련 frontend focused test 통과 |
| 최종 | `PASS` | 2026-08-03 | 기존 무반응 UX 재현되지 않음 |

### MT-ARRIVAL-03 개인 예정 시각 경과 후 변경

정책:

- 개인 예상 도착 시각 경과만으로 `NO_SHOW`가 되지 않습니다.
- 전체 마감 전에는 다른 허용값으로 변경하거나 도착 완료할 수 있습니다.
- `NO_SHOW` 기준은 `confirmed_at + 30분`입니다.

절차:

1. 5분 또는 10분을 선택합니다.
2. 개인 예정 시각이 지날 때까지 기다립니다.
3. `예정 시간이 지났어요`를 확인합니다.
4. 기존 값과 다른, 전체 마감 안의 선택지를 누릅니다.
5. PUT status/body와 화면 snapshot을 확인합니다.

판정 기준:

| 결과 | 해석 |
| --- | --- |
| PUT 요청 없음 | frontend click 처리 결함 |
| 200, 새 `arrivalMinutes` 반환 | backend 성공. 화면 미반영이면 frontend 결함 |
| 409 `MATCHING_ARRIVAL_DEADLINE_EXCEEDED` | 전체 마감 경계 거절. 오류 안내와 REST refresh 확인 |
| 200, 기존 값 유지 | 동일 값 멱등 또는 backend snapshot 확인 필요 |

통과 기준:

- [x] 개인 예정 시각 경과 안내가 표시됩니다.
- [x] 같은 값은 연장되지 않는다는 설명이 표시됩니다.
- [x] 전체 마감 전 다른 허용값으로 변경됩니다.
- [x] 성공한 새 선택값과 예상 시각이 즉시 표시됩니다.
- [x] 상대 화면에도 새 선택값이 표시됩니다.
- [ ] 409 실패 경계의 오류 안내는 `MT-ARRIVAL-04`에서 별도로 확인합니다.

| 구분 | 판정 | 실행 시각 | 증거/메모 |
| --- | --- | --- | --- |
| 경과 안내 | `PASS` | 2026-08-03 | `예정 시간이 지났어요`와 후속 행동 안내 확인 |
| 다른 값 변경 | `PASS` | 2026-08-03 | 5분/10분 선택 변경과 본인 snapshot 반영 확인 |
| 상대 동기화 | `PASS` | 2026-08-03 | 양쪽 선택값 5분/10분 반영 확인 |
| 최종 | `PASS` | 2026-08-03 | 409 마감 경계는 MT-ARRIVAL-04로 분리 |

### MT-ARRIVAL-04 전체 마감 선택 차단

통과 기준:

- [X] 남은 전체 시간보다 긴 선택지만 비활성화됩니다.
- [X] `now + arrivalMinutes == deadline`은 허용됩니다.
- [X] `now >= deadline`이면 모든 시간 선택을 차단합니다.
- [X] deadline은 선택 변경으로 연장되지 않습니다.
- [X] deadline 이후 도착 API 거절과 NO_SHOW Scheduler 처리를 확인합니다.

| 판정     | 실행 시각      | 증거/메모 |
|--------|------------| --- |
| `PASS` | 2026-08-04 | deadline 이후 도착 API 거절과 NO_SHOW Scheduler의 양쪽 종료 화면·첫 NO_SHOW 30분 cooldown 확인. DB 검증은 12.4에서 별도 확인 필요. 마감 후 `도착했어요` action이 남는 UX는 ISSUE-MR-006으로 분리 |

추가 확인:

- [X] 12.4 SQL로 두 회원 `NO_SHOW`, group `CANCELLED`, 회원별 penalty `+3`, 30분 cooldown과 event 중복 여부를 확인합니다.
- [X] ISSUE-MR-006 수정 후 deadline부터 `도착했어요` action이 사라지는지 확인합니다.
- [X] Scheduler 처리 전 짧은 대기 구간에는 `노쇼 처리 결과를 확인하고 있어요`와 같은 안내가 표시되는지 확인합니다.
- [X] polling 또는 WebSocket refresh 뒤 양쪽 화면이 종료 snapshot으로 이동하는지 재확인합니다.

### MT-ARRIVAL-05 도착 완료

통과 기준:

- [X] 도착 회원 상태가 `ARRIVED`입니다.
- [X] 첫 도착이면 group이 `IN_PROGRESS`입니다.
- [X] 본인의 도착 예정 선택 UI가 사라집니다.
- [X] `도착했어요` action이 사라집니다.
- [X] 실제 도착 시각이 표시됩니다.
- [X] 상대 화면과 새로고침 snapshot에 반영됩니다.
- [X] 반복 요청으로 event가 중복되지 않습니다.

| 판정 | 실행 시각 | 증거/메모 |
| --- | --- | --- |
| `PASS` | 2026-08-04| |

## 9. 자발적 취소

### MT-CANCEL-01 확정 후 3분 이내 취소

통과 기준:

- [X] 취소 API가 200입니다.
- [X] 취소 회원은 `CANCELLED`입니다.
- [X] `cancel_reason`, `cancelled_at`이 저장됩니다.
- [X] penalty event가 없습니다.
- [X] cooldown이 없습니다.
- [X] 2명 group은 `CANCELLED`입니다.
- [X] 남은 비귀책 회원은 `LEFT`입니다.
- [X] `MEMBER_CANCELLED`, `MATCH_CANCELLED`가 각각 한 번 생성됩니다.
- [X] 양쪽 화면이 종료 상태로 복원됩니다.
- [X] 종료 뒤 “다른 참여자의 응답 대기” 화면을 표시하지 않습니다.

| 판정     |   group_id | 실행 시각 | 증거/메모 |
|--------|-----------:| --- | --- |
| `PASS` | 2026-08-04 | | |

### MT-CANCEL-02 확정 후 3분 이후, deadline 전 취소

통과 기준:

- [X] 취소 API가 200입니다.
- [X] 취소 회원은 `CANCELLED`입니다.
- [X] `members.penalty_score`가 1 증가합니다.
- [X] `match_penalty_events.event_type=CANCEL` 1건이 생성됩니다.
- [X] 첫 당일 귀책 취소 cooldown은 10분입니다.
- [X] cooldown의 `reason=CANCEL`, `related_group_id`가 일치합니다.
- [X] 비귀책 회원에게 penalty/cooldown이 없습니다.
- [X] group과 남은 회원 상태가 정책대로 종료됩니다.
- [X] 취소 회원 재신청이 cooldown 동안 차단됩니다.
- [X] 양쪽 종료 화면이 서로 모순되지 않습니다.

| 판정     | group_id | 실행 시각 | 증거/메모 |
|--------| ---: | --- | --- |
| `PASS` | | 2026-08-04 | 한 회원 ARRIVED 후 상대가 3분 이후 취소. 취소 회원만 penalty score +1·첫 CANCEL 10분 cooldown, 비귀책 회원 즉시 재신청, 양쪽 종료 화면 확인 |

재검증 메모:

- 2026-08-03 양쪽 모두 종료 카드로 전환되는 것을 확인했습니다.
- 비귀책 회원은 즉시 `다시 신청하기`가 가능합니다.
- 취소 회원은 약 10분 cooldown과 비활성화된 재신청 버튼이 표시됩니다.
- 화면 복원 결함 수정과 DB penalty/cooldown을 재검증하여 최종 `PASS`로 판정했습니다.

### MT-CANCEL-03 취소 멱등성

수동 UI에서는 성공 후 action이 사라지므로 자동 통합 테스트를 주 검증으로
사용합니다. 필요하면 Network 재전송으로 보조 확인합니다.

- [X] 동일 group/member/cause penalty event는 1건입니다.
- [X] cooldown은 1건입니다.
- [X] member penalty score는 한 번만 증가합니다.
- [X] `MEMBER_CANCELLED` event는 1건입니다.
- [X] `MATCH_CANCELLED` event는 1건입니다.

| 판정     | 실행 시각 | 증거/메모 |
|--------| --- | --- |
| `PASS` | 2026-08-04 | 동일 cancellation 요청을 Copy as fetch로 재전송. 최초/재전송 모두 HTTP 200, penalty·cooldown·MEMBER_CANCELLED·MATCH_CANCELLED 각각 1건 유지, penalty score 1회만 증가 |

## 10. NO_SHOW Scheduler

### MT-NOSHOW-01 30분 마감 기본 처리

사전 조건:

- 새로운 active group을 준비합니다.
- `MATCHING_NO_SHOW_SCHEDULER_ENABLED=true`입니다.
- 한 회원은 미도착 상태로 둡니다.
- 테스트 목적의 시간 조정은 별도 승인된 dev DB 절차로만 수행합니다.

통과 기준:

- [X] deadline 전에는 NO_SHOW로 바뀌지 않습니다.
- [X] deadline 정각부터 도착 API를 거절합니다.
- [X] Scheduler가 `JOINED`/`ARRIVAL_TIME_SELECTED`를 `NO_SHOW`로 바꿉니다.
- [X] `no_show_at`이 저장됩니다.
- [X] `MEMBER_NO_SHOW` event가 한 번 생성됩니다.
- [X] penalty score가 3 증가합니다.
- [X] 첫 당일 NO_SHOW cooldown은 30분입니다.
- [X] `reason=NO_SHOW`, `related_group_id`가 일치합니다.
- [X] group 유지/취소와 비귀책 회원 상태가 정책과 일치합니다.
- [X] 양쪽 화면이 REST snapshot으로 종료 상태를 복원합니다.

| 판정     | group_id | 실행 시각 | 증거/메모 |
|--------| ---: | --- | --- |
| `PASS` | | 2026-08-04 | 화면·Network·Scheduler 종료, `no_show_at`, 회원별 MEMBER_NO_SHOW·penalty·cooldown 각 1건, related_group_id와 첫 NO_SHOW 30분 확인 |

### MT-NOSHOW-02 ARRIVED 제외

- [X] `ARRIVED` 회원은 NO_SHOW 대상이 아닙니다.
- [X] `CANCELLED`, `NO_SHOW`, `LEFT`도 다시 처리하지 않습니다.
- [X] 비귀책 회원에게 penalty/cooldown을 만들지 않습니다.

| 판정     | 실행 시각 | 증거/메모 |
|--------| --- | --- |
| `PASS` | | |

### MT-NOSHOW-03 재실행 멱등성

- [X] Scheduler 재실행 후 member 상태가 추가 변경되지 않습니다.
- [X] `MEMBER_NO_SHOW` event는 1건입니다.
- [X] penalty event는 1건입니다.
- [X] cooldown은 1건입니다.
- [X] penalty score는 한 번만 증가합니다.

| 판정 | 실행 시각 | 증거/메모 |
| --- | --- | --- |
| `PASS` | 2026-08-04 | Scheduler 반복 실행 후 회원별 MEMBER_NO_SHOW·penalty·cooldown 각각 1건 유지 확인 |

### MT-NOSHOW-04 당일 반복 제한

- [X] 첫 NO_SHOW cooldown은 30분입니다.
- [X] 같은 KST 날짜의 두 번째 이상 NO_SHOW cooldown은 60분입니다.
- [X] 기존 active cooldown보다 새 만료가 짧으면 기존 만료를 보존합니다.
- [X] `manner_temperature`는 변경하지 않습니다.

| 판정 | 실행 시각 | 증거/메모 |
| --- | --- | --- |
| `PASS` | 2026-08-04 | member 2에 2시간 `REPORT` cooldown을 준비한 뒤 NO_SHOW 처리. 기존 row는 `EXPIRED`, 새 `NO_SHOW` row는 `ACTIVE`이며 `expires_at`이 동일하게 보존됨. 화면에서도 약 116분 재신청 제한 확인 |

## 11. 인원 감소와 group 유지

### MT-GROUP-01 2명 group에서 1명 이탈

- [X] group은 `CANCELLED`입니다.
- [X] 귀책 회원은 `CANCELLED` 또는 `NO_SHOW`를 유지합니다.
- [X] 남은 비귀책 회원은 `LEFT`입니다.
- [X] `confirmedMemberCount=2` 이력은 유지합니다.
- [X] current group 응답은 종료 group을 반환하지 않습니다.

| 판정 | 실행 시각 | 증거/메모 |
| --- | --- | --- |
| `PASS` | 2026-08-04 | 한 회원 ARRIVED 후 상대 취소/NO_SHOW에서 2명 group 종료, 귀책 상태 유지, 비귀책 LEFT와 양쪽 current group 종료 확인 |

### MT-GROUP-02 3명 group에서 2명 유지

두 계정 수동 환경만으로는 모든 경우를 만들기 어려우므로 자동 통합 테스트를
주 검증으로 사용합니다.

- [X] 남은 2명 모두 `allow_minimum_two=true`이면 group을 유지합니다.
- [X] 한 명이라도 false이면 group을 취소합니다.
- [X] 유지 group은 `confirmedMemberCount=3`, `currentMemberCount=2`입니다.
- [X] 취소/NO_SHOW 회원은 current group 공개 목록에서 제외합니다.

| 판정 | 실행 시각 | 증거/메모 |
| --- | --- | --- |
| `PASS` | 2026-08-04 | 3명 확정 후 남은 두 명 true이면 3→2로 유지하고 취소 회원 제외·무패널티 확인. 남은 두 명 중 false가 있으면 양쪽 종료 확인. 별도로 초기 제안에서 false 회원 취소 후 true 회원 2명의 현재 인원 시작과 2인 상태방 생성 확인 |

## 12. 테스트별 PASS 판정 실행 시트

이 절은 테스트를 수행하면서 그대로 복사해 사용하는 실행용 체크리스트입니다.
공통 SQL만 보고 검증 쿼리를 다시 조합하지 않습니다.

사용 방법:

1. 테스트를 시작하기 전에 해당 시나리오의 초기화 조건을 확인합니다.
2. 매칭이 확정되면 아래 SQL로 `group_id`를 확인해 기록합니다.
3. 화면 동작과 Network 응답을 확인합니다.
4. 해당 테스트 바로 아래의 DB 검증 SQL을 실행합니다.
5. 모든 체크박스를 확인한 뒤 최종 판정의 `PENDING`을 `PASS` 또는 `FAIL`로 직접 변경합니다.

변수 표기:

- `:group_id`: 이번 테스트에서 생성된 `match_groups.id`
- `:actor_member_id`: 도착 또는 취소를 실행한 회원 ID
- `:other_member_id`: 상대 회원 ID
- SQL 도구가 이름 있는 변수를 지원하지 않으면 실제 숫자로 바꿉니다.

현재 테스트 group 확인:

```sql
SELECT
    g.id AS group_id,
    g.status AS group_status,
    g.confirmed_at,
    g.confirmed_at + INTERVAL '3 minutes' AS penalty_starts_at,
    g.confirmed_at + INTERVAL '30 minutes' AS arrival_deadline_at,
    current_timestamp AS db_now,
    array_agg(gm.member_id ORDER BY gm.member_id) AS member_ids
FROM match_groups g
JOIN match_group_members gm ON gm.group_id = g.id
WHERE g.festival_id = 144
  AND gm.member_id IN (2, 27)
GROUP BY g.id
HAVING count(*) FILTER (WHERE gm.member_id IN (2, 27)) = 2
ORDER BY g.id DESC
LIMIT 1;
```

### 12.1 MT-ARRIVAL-05 도착 완료 판정

화면과 Network:

- [ ] `도착했어요` 요청의 HTTP status가 `200`입니다.
- [ ] 본인의 도착 예정 선택 UI와 `도착했어요` action이 사라집니다.
- [ ] 실제 도착 시각이 표시됩니다.
- [ ] 상대 화면과 양쪽 새로고침 snapshot에 반영됩니다.

상태 검증:

```sql
SELECT
    g.id AS group_id,
    g.status AS group_status,
    g.started_at,
    gm.member_id,
    gm.status AS member_status,
    gm.arrival_minutes,
    gm.arrival_time_selected_at,
    gm.arrived_at,
    (gm.status = 'ARRIVED') AS pass_member_arrived,
    (gm.arrived_at IS NOT NULL) AS pass_arrived_at_saved,
    (g.status = 'IN_PROGRESS') AS pass_group_in_progress
FROM match_groups g
JOIN match_group_members gm ON gm.group_id = g.id
WHERE g.id = :group_id
ORDER BY gm.member_id;
```

판정 방법:

- `actor_member_id` 행의 `pass_member_arrived`, `pass_arrived_at_saved`가 `true`여야 합니다.
- 첫 도착이면 모든 행의 `pass_group_in_progress`가 `true`여야 합니다.
- 아직 도착하지 않은 상대 회원은 `JOINED` 또는 `ARRIVAL_TIME_SELECTED` 상태여야 합니다.

도착 event 중복 검증:

```sql
SELECT
    :actor_member_id AS actor_member_id,
    count(*) FILTER (
        WHERE event_type = 'MEMBER_ARRIVED'
          AND member_id = :actor_member_id
    ) AS member_arrived_event_count,
    count(*) FILTER (
        WHERE event_type = 'MEMBER_ARRIVED'
          AND member_id = :actor_member_id
    ) = 1 AS pass_single_arrived_event
FROM match_events
WHERE group_id = :group_id;
```

| 확인 구분 | 판정 | 실행 시각 | 실제 값/증거 |
| --- | --- | --- | --- |
| 화면·Network | `PENDING` | | |
| member `ARRIVED`, `arrived_at` | `PENDING` | | |
| group `IN_PROGRESS` | `PENDING` | | |
| `MEMBER_ARRIVED` 1건 | `PENDING` | | |
| **MT-ARRIVAL-05 최종** | **`PENDING`** | | |

최종 체크: [ ] `PASS` / [ ] `FAIL` / [ ] `BLOCKED`

### 12.2 MT-CANCEL-01 확정 후 3분 이내 취소 판정

취소 전에 경계를 확인합니다. `penalty_applies_now=false`일 때만 이 테스트를
계속합니다.

```sql
SELECT
    id AS group_id,
    confirmed_at,
    confirmed_at + INTERVAL '3 minutes' AS penalty_starts_at,
    current_timestamp AS db_now,
    current_timestamp >= confirmed_at + INTERVAL '3 minutes'
        AS penalty_applies_now
FROM match_groups
WHERE id = :group_id;
```

화면과 Network:

- [ ] 취소 API의 HTTP status가 `200`입니다.
- [ ] 양쪽 화면이 종료 상태로 전환됩니다.
- [ ] 취소 회원과 비귀책 회원 모두 응답 대기 화면으로 돌아가지 않습니다.

group/member 검증:

```sql
SELECT
    g.id AS group_id,
    g.status AS group_status,
    g.cancelled_at AS group_cancelled_at,
    g.cancel_reason AS group_cancel_reason,
    gm.member_id,
    gm.status AS member_status,
    gm.cancelled_at AS member_cancelled_at,
    gm.cancel_reason AS member_cancel_reason,
    (g.status = 'CANCELLED') AS pass_group_cancelled,
    (g.cancelled_at IS NOT NULL) AS pass_group_cancelled_at,
    CASE
        WHEN gm.member_id = :actor_member_id
            THEN gm.status = 'CANCELLED'
                 AND gm.cancelled_at IS NOT NULL
                 AND gm.cancel_reason IS NOT NULL
        WHEN gm.member_id = :other_member_id
            THEN gm.status = 'LEFT'
        ELSE false
    END AS pass_member_state
FROM match_groups g
JOIN match_group_members gm ON gm.group_id = g.id
WHERE g.id = :group_id
ORDER BY gm.member_id;
```

패널티와 cooldown 미생성 검증:

```sql
SELECT
    count(*) AS penalty_event_count,
    count(*) = 0 AS pass_no_penalty_event
FROM match_penalty_events
WHERE related_group_id = :group_id;

SELECT
    count(*) AS cooldown_count,
    count(*) = 0 AS pass_no_cooldown
FROM match_cooldowns
WHERE related_group_id = :group_id;
```

event 검증:

```sql
SELECT
    count(*) FILTER (
        WHERE event_type = 'MEMBER_CANCELLED'
          AND member_id = :actor_member_id
    ) AS member_cancelled_count,
    count(*) FILTER (WHERE event_type = 'MATCH_CANCELLED')
        AS match_cancelled_count,
    count(*) FILTER (
        WHERE event_type = 'MEMBER_CANCELLED'
          AND member_id = :actor_member_id
    ) = 1
      AND count(*) FILTER (WHERE event_type = 'MATCH_CANCELLED') = 1
        AS pass_event_counts
FROM match_events
WHERE group_id = :group_id;
```

| 확인 구분 | 판정 | 실행 시각 | 실제 값/증거 |
| --- | --- | --- | --- |
| 취소 전 3분 이내 | `PENDING` | | `penalty_applies_now=` |
| 화면·Network | `PENDING` | | |
| group/member 상태 | `PENDING` | | |
| penalty 0건 | `PENDING` | | |
| cooldown 0건 | `PENDING` | | |
| 취소 event 각각 1건 | `PENDING` | | |
| **MT-CANCEL-01 최종** | **`PENDING`** | | |

최종 체크: [ ] `PASS` / [ ] `FAIL` / [ ] `BLOCKED`

### 12.3 MT-CANCEL-02 확정 후 3분 이후 취소 판정

취소 전에 12.2의 경계 SQL을 실행하여 `penalty_applies_now=true`인지 확인합니다.

group/member 상태는 12.2의 group/member SQL로 확인합니다. 추가로 아래 SQL을
실행합니다.

패널티 검증:

```sql
SELECT
    pe.id,
    pe.member_id,
    pe.event_type,
    pe.score_delta,
    pe.reason,
    pe.related_group_id,
    pe.created_at,
    (pe.member_id = :actor_member_id) AS pass_actor,
    (pe.event_type = 'CANCEL') AS pass_cancel_type,
    (pe.score_delta = 1) AS pass_score_delta
FROM match_penalty_events pe
WHERE pe.related_group_id = :group_id
ORDER BY pe.id;
```

예상 결과는 취소 회원의 `CANCEL`, `score_delta=1` 한 행입니다. 상대 회원 행은
없어야 합니다.

회원 누적 점수와 cooldown 검증:

```sql
SELECT id, nickname, penalty_score, manner_temperature
FROM members
WHERE id IN (:actor_member_id, :other_member_id)
ORDER BY id;

SELECT
    id,
    member_id,
    reason,
    status,
    starts_at,
    expires_at,
    related_group_id,
    (member_id = :actor_member_id) AS pass_actor,
    (reason = 'CANCEL') AS pass_cancel_reason,
    (status = 'ACTIVE') AS pass_active,
    (expires_at - starts_at = INTERVAL '10 minutes') AS pass_first_duration,
    (starts_at <= current_timestamp AND expires_at > current_timestamp)
        AS effective_active
FROM match_cooldowns
WHERE related_group_id = :group_id
ORDER BY id;
```

event는 12.2의 event SQL로 확인합니다.

| 확인 구분 | 판정 | 실행 시각 | 실제 값/증거 |
| --- | --- | --- | --- |
| 취소 전 3분 이후 | `PENDING` | | `penalty_applies_now=` |
| 화면·Network | `PENDING` | | |
| group/member 상태 | `PENDING` | | |
| 취소 회원 penalty `+1` 1건 | `PENDING` | | |
| 취소 회원 cooldown 10분 1건 | `PENDING` | | |
| 비귀책 회원 penalty/cooldown 없음 | `PENDING` | | |
| 취소 event 각각 1건 | `PENDING` | | |
| **MT-CANCEL-02 최종** | **`PENDING`** | | |

최종 체크: [ ] `PASS` / [ ] `FAIL` / [ ] `BLOCKED`

### 12.4 MT-NOSHOW-01 30분 마감 판정

Scheduler 처리 전에 마감 상태를 확인합니다.

```sql
SELECT
    id AS group_id,
    status AS group_status,
    confirmed_at,
    confirmed_at + INTERVAL '30 minutes' AS arrival_deadline_at,
    current_timestamp AS db_now,
    current_timestamp >= confirmed_at + INTERVAL '30 minutes'
        AS no_show_due_now
FROM match_groups
WHERE id = :group_id;
```

`no_show_due_now=true`이고 Scheduler 실행 주기가 지난 다음 검증합니다.

```sql
SELECT
    g.id AS group_id,
    g.status AS group_status,
    g.cancelled_at,
    g.cancel_reason AS group_cancel_reason,
    gm.member_id,
    gm.status AS member_status,
    gm.arrived_at,
    gm.no_show_at,
    gm.cancel_reason AS member_cancel_reason
FROM match_groups g
JOIN match_group_members gm ON gm.group_id = g.id
WHERE g.id = :group_id
ORDER BY gm.member_id;

SELECT
    id,
    member_id,
    event_type,
    score_delta,
    related_group_id,
    created_at
FROM match_penalty_events
WHERE related_group_id = :group_id
ORDER BY id;

SELECT
    id,
    member_id,
    reason,
    status,
    starts_at,
    expires_at,
    related_group_id,
    (expires_at - starts_at = INTERVAL '30 minutes') AS pass_first_duration
FROM match_cooldowns
WHERE related_group_id = :group_id
ORDER BY id;

SELECT
    event_type,
    member_id,
    count(*) AS event_count
FROM match_events
WHERE group_id = :group_id
  AND event_type IN ('MEMBER_NO_SHOW', 'MATCH_CANCELLED')
GROUP BY event_type, member_id
ORDER BY event_type, member_id;
```

예상 결과:

- 마감까지 도착하지 않은 회원은 `NO_SHOW`, `no_show_at IS NOT NULL`입니다.
- `ARRIVED` 회원은 그대로 유지되며 패널티와 cooldown이 없습니다.
- NO_SHOW 회원마다 `event_type=NO_SHOW`, `score_delta=3` 패널티가 한 건입니다.
- 첫 당일 NO_SHOW라면 `reason=NO_SHOW`인 30분 cooldown이 한 건입니다.
- `MEMBER_NO_SHOW`는 대상 회원마다 한 건이며, 2명 유지가 불가능해 종료되면
  `MATCH_CANCELLED`도 한 건입니다.

| 확인 구분 | 판정 | 실행 시각 | 실제 값/증거 |
| --- | --- | --- | --- |
| 30분 마감 경과 | `PENDING` | | `no_show_due_now=` |
| member `NO_SHOW`, `no_show_at` | `PENDING` | | |
| ARRIVED 회원 제외 | `PENDING` | | |
| penalty `+3` | `PENDING` | | |
| 첫 cooldown 30분 | `PENDING` | | |
| event 중복 없음 | `PENDING` | | |
| group/비귀책 회원 상태 | `PENDING` | | |
| 양쪽 화면 종료 snapshot | `PENDING` | | |
| **MT-NOSHOW-01 최종** | **`PENDING`** | | |

최종 체크: [ ] `PASS` / [ ] `FAIL` / [ ] `BLOCKED`

### 12.5 MT-CANCEL-03·MT-NOSHOW-03 멱등성 판정

반복 요청 또는 Scheduler 재실행 전과 후에 같은 SQL을 각각 실행하여 개수를
비교합니다.

```sql
SELECT
    event_type,
    member_id,
    count(*) AS event_count
FROM match_events
WHERE group_id = :group_id
  AND event_type IN (
      'MEMBER_CANCELLED',
      'MEMBER_NO_SHOW',
      'MATCH_CANCELLED'
  )
GROUP BY event_type, member_id
ORDER BY event_type, member_id;

SELECT
    member_id,
    event_type,
    count(*) AS penalty_event_count,
    sum(score_delta) AS score_delta_sum
FROM match_penalty_events
WHERE related_group_id = :group_id
GROUP BY member_id, event_type
ORDER BY member_id, event_type;

SELECT
    member_id,
    reason,
    count(*) AS cooldown_count,
    min(starts_at) AS first_starts_at,
    max(expires_at) AS last_expires_at
FROM match_cooldowns
WHERE related_group_id = :group_id
GROUP BY member_id, reason
ORDER BY member_id, reason;
```

| 확인 구분 | 판정 | 실행 시각 | 반복 전/후 값 |
| --- | --- | --- | --- |
| member event 개수 동일 | `PENDING` | | |
| `MATCH_CANCELLED` 개수 동일 | `PENDING` | | |
| penalty 개수·합계 동일 | `PENDING` | | |
| cooldown 개수·만료 동일 | `PENDING` | | |
| member 상태·시각 동일 | `PENDING` | | |
| **멱등성 최종** | **`PENDING`** | | |

최종 체크: [ ] `PASS` / [ ] `FAIL` / [ ] `BLOCKED`

## 13. 공통·상황별 DB 참고 SQL

### 13.1 최신 group과 전체 member

active group 조건을 넣지 않아 취소된 group도 조회합니다.

```sql
SELECT
    g.id AS group_id,
    g.status AS group_status,
    g.confirmed_member_count,
    g.confirmed_at,
    g.cancelled_at,
    g.cancel_reason AS group_cancel_reason,
    gm.id AS group_member_id,
    gm.member_id,
    gm.status AS member_status,
    gm.cancelled_at AS member_cancelled_at,
    gm.cancel_reason AS member_cancel_reason,
    gm.no_show_at,
    gm.allow_minimum_two,
    gm.arrival_minutes,
    gm.arrival_time_selected_at,
    gm.arrived_at
FROM match_groups g
JOIN match_group_members gm
  ON gm.group_id = g.id
WHERE gm.member_id IN (2, 27)
ORDER BY g.id DESC, gm.id;
```

### 13.2 3분과 30분 경계

```sql
SELECT
    g.id AS group_id,
    g.status,
    g.confirmed_at,
    g.confirmed_at + INTERVAL '3 minutes' AS penalty_starts_at,
    g.confirmed_at + INTERVAL '30 minutes' AS arrival_deadline_at,
    current_timestamp AS db_now,
    current_timestamp >= g.confirmed_at + INTERVAL '3 minutes'
        AS penalty_applies_now,
    current_timestamp >= g.confirmed_at + INTERVAL '30 minutes'
        AS no_show_due_now
FROM match_groups g
JOIN match_group_members gm
  ON gm.group_id = g.id
WHERE gm.member_id IN (2, 27)
ORDER BY g.id DESC
LIMIT 1;
```

### 13.3 pool, attempt, proposal과 response

```sql
SELECT *
FROM match_pools
WHERE member_id IN (2, 27)
ORDER BY id DESC;

SELECT a.*
FROM match_attempts a
WHERE a.id IN (
    SELECT DISTINCT attempt_id
    FROM match_attempt_members
    WHERE member_id IN (2, 27)
)
ORDER BY a.id DESC;

SELECT *
FROM match_attempt_members
WHERE member_id IN (2, 27)
ORDER BY id DESC;

SELECT *
FROM match_proposals
WHERE member_id IN (2, 27)
ORDER BY id DESC;

SELECT response.*
FROM match_responses response
JOIN match_proposals proposal
  ON proposal.id = response.proposal_id
WHERE proposal.member_id IN (2, 27)
ORDER BY response.id DESC;
```

### 13.4 penalty와 회원 누적 점수

```sql
SELECT id, nickname, penalty_score, manner_temperature
FROM members
WHERE id IN (2, 27)
ORDER BY id;

SELECT *
FROM match_penalty_events
WHERE member_id IN (2, 27)
ORDER BY id DESC;
```

### 13.5 cooldown

```sql
SELECT
    *,
    status = 'ACTIVE'
      AND starts_at <= current_timestamp
      AND expires_at > current_timestamp AS effective_active
FROM match_cooldowns
WHERE member_id IN (2, 27)
ORDER BY id DESC;
```

### 13.6 group event

```sql
SELECT *
FROM match_events
WHERE group_id = :group_id
ORDER BY id;
```

확인 대상:

- `MEMBER_CANCELLED`
- `MEMBER_NO_SHOW`
- `MATCH_CANCELLED`
- 동일 원인 event 중복 여부

### 13.7 current group 정합성

```sql
SELECT
    g.id AS group_id,
    g.confirmed_member_count,
    count(*) FILTER (
        WHERE gm.status IN ('JOINED', 'ARRIVAL_TIME_SELECTED', 'ARRIVED')
    ) AS active_member_count,
    array_agg(gm.member_id ORDER BY gm.id) FILTER (
        WHERE gm.status IN ('JOINED', 'ARRIVAL_TIME_SELECTED', 'ARRIVED')
    ) AS active_member_ids
FROM match_groups g
LEFT JOIN match_group_members gm
  ON gm.group_id = g.id
WHERE g.id = :group_id
GROUP BY g.id, g.confirmed_member_count;
```

## 14. 발견 문제 기록

### ISSUE-MR-001 유효하지 않은 check-in을 일반 연결 오류로 표시

| 항목 | 내용 |
| --- | --- |
| 상태 | `FIXED_PENDING_RETEST` |
| 재현 | 만료된 `ACTIVE` check-in으로 pool 신청 |
| 실제 결과 | 400 `MATCHING_INVALID_REQUEST`를 일반 연결 오류 카드로 표시 |
| 기대 결과 | 체크인 만료 안내와 체크인 화면 이동 제공 |
| 증거 | `해당 축제의 유효한 체크인이 필요합니다.` |
| 후보 파일 | `useMatchingSession.ts`, `MatchingConditionPage.tsx` |
| 수정 메모 | backend 오류 메시지를 유지하고 유효 체크인 오류에는 `체크인하기` 동작을 표시하도록 수정 |

### ISSUE-MR-002 취소 후 무한 응답 대기

| 항목 | 내용 |
| --- | --- |
| 상태 | `CLOSED` |
| 재현 | 2명 group에서 한 명 자발적 취소 후 `/matching` 복귀 |
| 실제 결과 | 종료 안내와 `내 응답을 보냈어요`가 동시에 표시되고 polling 지속 |
| 원인 | active group이 없는 `MATCHED` pool을 `RESPONSE_PENDING`으로 해석 |
| 기대 결과 | 종료 카드와 cooldown/retry 상태 표시 |
| 후보 파일 | `useMatchingSession.ts`, 관련 테스트 |
| 수정 메모 | `MATCHED` pool만 남은 snapshot을 `CANCELLED`로 복원하도록 수정 |

### ISSUE-MR-003 도착 시간 선택값과 동일 값 멱등 UX 불명확

| 항목 | 내용 |
| --- | --- |
| 상태 | `CLOSED` |
| 재현 | 10분 선택 후 같은 10분 재선택, 또는 개인 예정 시각 경과 후 조작 |
| 실제 결과 | 현재 선택값과 no-op 이유가 보이지 않아 무반응으로 인식 |
| 기대 결과 | 현재 선택 표시, 동일 값 비활성화, 예정 시각 미연장 설명 |
| 후보 파일 | `MatchRoomPage.tsx`, 관련 테스트 |
| 수정 메모 | `현재 선택` 표시와 경과 안내 문구 추가 |

### ISSUE-MR-004 상대 도착 시간 문구가 실시간 countdown처럼 보임

| 항목 | 내용 |
| --- | --- |
| 상태 | `CLOSED` |
| 재현 | 상대가 10분을 선택한 뒤 시간이 경과함 |
| 실제 결과 | 상대 목록에 계속 `10분 후 도착 예정` 표시 |
| 원인 | 실시간 잔여시간이 아니라 선택 snapshot을 표시하지만 문구가 모호함 |
| 기대 결과 | `선택한 도착 시간: 10분`으로 의미를 명확히 표시 |
| 후보 파일 | `MatchRoomPage.tsx`, 관련 테스트 |
| 수정 메모 | 상대 목록 문구 변경 |

### ISSUE-MR-005 상대의 실제 예상 도착 시각 미표시

| 항목 | 내용 |
| --- | --- |
| 상태 | `FIXED_PENDING_RETEST` |
| 재현 | 두 회원이 각각 5분, 10분 도착 시간을 선택함 |
| 실제 결과 | 상대 목록에 `선택한 도착 시간: N분`만 표시 |
| 기대 결과 | 선택값과 함께 계산된 `예상 도착 시각`을 표시 |
| 계산 기준 | `arrival_time_selected_at + arrival_minutes` |
| 주의 | 실시간 `9분 뒤`, `8분 뒤` countdown과 구분하고 절대 예정 시각을 표시 |
| 후보 파일 | `MatchRoomPage.tsx`, 관련 frontend 테스트 |
| 수정 메모 | 수동 테스트 이슈를 모은 뒤 일괄 수정 |

### ISSUE-MR-006 deadline 이후 도착 action 노출

| 항목 | 내용 |
| --- | --- |
| 상태 | `FIXED` |
| 발견 시각(KST) | 2026-08-04 |
| 재현 | 두 회원이 도착하지 않은 상태에서 `arrivalDeadlineAt` 경과까지 MatchRoom 유지 |
| 실제 결과 | 도착 예정 시간 선택은 차단되지만 `도착했어요` action은 계속 노출되고, 실행하면 deadline 초과 API 오류를 일반 저장 실패로 표시 |
| 기대 결과 | deadline부터 도착 예정 선택과 `도착했어요` action을 모두 차단하고, Scheduler 결과를 기다리는 안내를 표시한 뒤 종료 snapshot으로 이동 |
| 서버 계약 | frontend가 임의로 `NO_SHOW`를 만들지 않고 REST polling/WebSocket refresh로 Scheduler 처리 결과를 복원 |
| 후보 파일 | `MatchRoomPage.tsx`, 관련 frontend 테스트 |
| 수정 메모 | action 노출 조건에 deadline을 반영하고 Scheduler 처리 대기 UI를 추가함. frontend 자동 테스트와 두 브라우저 재검증 완료 |

### ISSUE-MR-007 종료 안내가 새 매칭에도 유지됨

| 항목 | 내용 |
| --- | --- |
| 상태 | `FIXED` |
| 발견 시각(KST) | 2026-08-04 |
| 재현 | MatchRoom 취소·NO_SHOW 종료 후 `/matching`에서 다시 신청하기, 새 조건 선택, 새 매칭 신청 또는 새로고침 |
| 실제 결과 | 이전 그룹의 `matchRoomNotice`가 Router history state에 남아 새 제안·새 그룹 화면에도 계속 표시 |
| 기대 결과 | 종료 안내는 최초 `/matching` 진입에서만 표시하고 재신청·새 pool 신청과 새로고침에서는 제거 |
| 원인 | `MatchingConditionPage`가 `location.state.matchRoomNotice`를 읽기만 하고 history state를 소비하지 않음 |
| 수정 메모 | 안내를 local 일회성 상태로 옮긴 뒤 Router state에서 즉시 제거하고 재신청·새 pool 신청 시 화면 안내도 제거. frontend 자동 테스트와 브라우저 재검증 완료 |

## 15. 신규 문제 기록 템플릿

```text
### ISSUE-MR-NNN 제목

- 상태: OPEN / FIXED_PENDING_RETEST / CLOSED
- 발견 시각(KST):
- 브라우저/member:
- group_id/pool_id/proposal_id:
- 사전 조건:
- 재현 절차:
- 실제 화면 결과:
- 기대 결과:
- Request URL/Method/Status:
- Response body:
- DB 조회 결과:
- 화면 캡처:
- 가장 가능성 높은 원인:
- 예상 수정 파일:
- 수정 요청 메모:
```

## 16. 테스트 실행 기록

| Test ID | 실행 시각 | member/group | 판정 | 증거 | 수정 요청 메모 |
| --- | --- | --- | --- | --- | --- |
| MT-BASE-01 | 2026-08-03 | member 2/27 | `PASS` | 두 브라우저 상태방 진입 | |
| MT-ARRIVAL-01 | 2026-08-03 | member 2/27 | `PASS` | 선택 변경과 상대 반영 | 실제 상대 예상 시각 표시는 ISSUE-MR-005 |
| MT-ARRIVAL-02 | 2026-08-03 | member 2/27 | `PASS` | 현재 선택 표시·비활성화·미연장 안내 확인 | |
| MT-ARRIVAL-03 | 2026-08-03 | member 2/27 | `PASS` | 예정 시각 경과 안내, 다른 값 변경, 상대 동기화 확인 | 409 경계는 MT-ARRIVAL-04 |
| MT-ARRIVAL-04 | 2026-08-04 | member 2/27 | `PASS` | deadline 이후 API 거절, NO_SHOW Scheduler 양쪽 종료 화면과 첫 NO_SHOW 30분 cooldown 확인 | DB 검증은 12.4에서 추가 확인, deadline 이후 action 노출은 ISSUE-MR-006 |
| MT-CANCEL-02 | 2026-08-03 | member 2/27 | `FAIL` | 종료 안내와 응답 대기 동시 표시 | 상태 복원 수정 후 재검증 |
| MT-CANCEL-02-R1 | 2026-08-04 | member 2/27 | `PASS` | 한 회원 ARRIVED 후 상대 취소, 양쪽 종료 카드, 취소 회원만 +1·10분 cooldown, 비귀책 회원 즉시 재신청 확인 | 기존 FAIL 재검증 완료 |
| MT-CANCEL-03 | 2026-08-04 | member 2/27 | `PASS` | 동일 cancellation Copy as fetch 재전송, HTTP 200/200 | penalty·cooldown·member/group event 각 1건과 penalty score 1회 증가 유지 |
| MT-NOSHOW-01 | 2026-08-04 | member 2/27 | `PASS` | deadline 거절, Scheduler 종료, `no_show_at`, penalty/event/cooldown 각 1건, related_group_id와 양쪽 종료 snapshot 확인 | |
| MT-NOSHOW-03 | 2026-08-04 | member 2/27 | `PASS` | Scheduler 반복 실행 뒤 회원별 MEMBER_NO_SHOW·penalty·cooldown 각 1건 유지 | |
| MT-NOSHOW-04 | 2026-08-04 | member 2/27 | `PASS` | 첫 NO_SHOW 30분, 같은 KST 날짜 두 번째 NO_SHOW 60분, manner_temperature 불변과 기존 2시간 active cooldown 만료 보존 확인 | 기존 REPORT row EXPIRED, 새 NO_SHOW row ACTIVE 및 expires_at 동일 |
| MT-GROUP-01 | 2026-08-04 | member 2/27 | `PASS` | 2명 group에서 취소/NO_SHOW 후 group 종료, 귀책 상태 유지와 비귀책 LEFT 확인 | |
| MT-GROUP-02 | 2026-08-04 | member 1/2/27 | `PASS` | true+true 잔존 시 confirmed 3/current 2 유지, false 포함 잔존 시 group 종료, 취소 회원 제외와 무패널티 확인 | 초기 미달 제안에서 false 회원 취소 후 true 회원 2명의 2인 상태방 생성도 확인 |

## 17. 완료 조건

이 브랜치의 수동 검증 완료는 다음을 모두 만족할 때 선언합니다.

- focused frontend와 backend 테스트가 통과합니다.
- PostgreSQL Testcontainers 통합 테스트가 실제 assertion까지 통과합니다.
- V14가 dev DB에 성공 적용됐습니다.
- 자발적 취소 3분 전/후를 각각 검증했습니다.
- NO_SHOW deadline, penalty/cooldown과 멱등성을 검증했습니다.
- 두 브라우저에서 WebSocket/polling/새로고침 복원이 일치합니다.
- `ISSUE-MR-*`의 `OPEN`, `FIXED_PENDING_RETEST` 항목을 모두 재판정했습니다.
- 최종 결과를 `docs/13_MATCHING_ENGINE_IMPLEMENTATION.md`와
  `docs/10_PROGRESS_LOG.md`에 요약 반영합니다.
