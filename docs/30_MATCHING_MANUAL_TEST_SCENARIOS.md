# 매칭 수명주기 수동 검증 시나리오

## 1. 이 문서의 목적

자동 테스트가 덮는 것과 **사람이 직접 봐야 하는 것**은 다릅니다. 이 문서는 후자를 다룹니다.

`MatchMeetingLifecycleHarnessTest`가 같은 흐름을 HTTP 경로로 자동 검증하지만, 다음 세 가지는
코드로 확인할 수 없습니다.

- **화면에 실제로 그렇게 보이는가.** 버튼이 뜨는지, 문구가 맞는지, 비활성화되는지
- **환경 설정이 실제 값으로 도는가.** 하네스는 `bypass-radius-check=false`를 강제하지만
  dev는 `true`로 돕니다
- **스케줄러가 실제로 집어가는가.** 하네스는 배치를 직접 호출합니다

기준은 `docs/19` 4.11(만남 성립 판정)입니다. 정책 근거는 그쪽을, 구현 경위는
`docs/10_PROGRESS_LOG.md`를 봅니다.

## 2. 전제 조건

### 계정

| 별칭 | 용도 |
| --- | --- |
| A | 주 테스트 계정 |
| B | 상대역 |
| C | 3인 시나리오와 노쇼역 (선택) |

모두 프로필 완료(`ACTIVE`)여야 하고, 같은 축제에 **GPS 체크인**이 되어 있어야 합니다. 체크인은
`app.festival.checkin.bypass-radius-check`가 local·dev에서 `true`라 현장에 없어도 됩니다.

### 축제와 만남 장소

- 축제가 `ACTIVE`이고 좌표(`map_x`, `map_y`)가 있어야 합니다.
- **그 축제에 `ACTIVE` 만남 장소가 최소 1개** 있어야 합니다. 없으면 매칭 신청이
  `MATCHING_MEETING_POINT_NOT_READY`로 막힙니다. 관리자 화면 `/admin/meeting-points`에서 등록합니다.

### 환경 변수

```bash
MATCHING_SCHEDULER_ENABLED=true            # 매칭 성사와 만남 종료 배치
MATCHING_NO_SHOW_SCHEDULER_ENABLED=true    # 도착 마감 노쇼 처리
MATCHING_ARRIVAL_RADIUS_METERS=150         # 도착 인정 반경
MATCHING_ARRIVAL_BYPASS_RADIUS_CHECK=true  # 반경 검증 우회(시나리오 B에서만 false로)
```

**만남 종료 배치는 `MATCHING_SCHEDULER_ENABLED`에 걸려 있습니다.** 노쇼 배치와 플래그가 다릅니다.
그룹을 닫는 것은 수명주기의 일부여서, 꺼지면 참가자가 새 매칭을 신청하지 못한 채 남습니다.

### 시간 조작

30분·1시간을 실제로 기다릴 수 없으므로 `confirmed_at`을 과거로 당깁니다.

```sql
-- 도착 마감(30분)을 지나게 한다
UPDATE match_groups SET confirmed_at = confirmed_at - INTERVAL '31 minutes' WHERE id = :groupId;

-- 만남 종료(1시간)를 지나게 한다
UPDATE match_groups SET confirmed_at = confirmed_at - INTERVAL '61 minutes' WHERE id = :groupId;
```

> **공유 dev DB에서는 쓰지 마세요.** `docs/08`은 dev DB를 조회 전용으로 봅니다. 시간 조작이
>필요한 시나리오(D·E·J·K)는 **로컬 DB**에서 하고, dev에서는 화면 동작 위주로 확인합니다.

## 3. 시나리오

각 표의 "기대 — 화면"은 사람이 보는 것, "기대 — DB"는 SQL로 확인할 것입니다.

### A. 매칭 신청부터 확정까지

| | |
| --- | --- |
| 준비 | A·B 같은 축제 체크인 |
| 조작 | 양쪽에서 `/matching` 진입 → 인원 2명, "2명이어도 괜찮아요" 체크 → 신청 → 제안이 뜨면 **30초 안에** 양쪽 수락 |
| 기대 — 화면 | 상태방으로 이동. "매칭이 확정됐어요", 만남 장소, **최종 도착 마감**, 남은 시간 |
| 기대 — DB | `match_groups.status = 'CONFIRMED'`, 참가자 2명 `JOINED` |

**제안 응답은 30초입니다.** 놓치면 `penalty_score +1`과 쿨타임 2분이 붙으니, 양쪽 화면을 열어둔
채로 진행합니다.

### B. 도착 반경 검증 ⚠️ 설정 변경 필요

| | |
| --- | --- |
| 준비 | A 상태방 진입. **`MATCHING_ARRIVAL_BYPASS_RADIUS_CHECK=false`로 바꾸고 재기동** |
| 조작 | 만남 장소에서 150m 넘게 떨어진 곳에서 "도착했어요" (브라우저 개발자도구의 위치 재정의 사용) |
| 기대 — 화면 | "만남 장소 근처에서 도착을 인증해주세요" |
| 기대 — DB | 해당 참가자 `status`가 `JOINED` 그대로, `arrival_distance_meters`는 `NULL` |
| 이어서 | 반경 안 좌표로 다시 도착 → 성공, `arrival_distance_meters`에 거리(미터)가 남음 |

**좌표는 저장되지 않아야 합니다.** `match_group_members`에 위경도 컬럼이 없다는 것을 확인합니다.
검증 후에는 `bypass`를 다시 `true`로 되돌립니다.

### C. 전원 도착해도 방이 유지된다

| | |
| --- | --- |
| 준비 | A·B 확정 상태 |
| 조작 | 양쪽 "도착했어요" |
| 기대 — 화면 | **상태방이 사라지지 않는다.** "현재 상태"가 **`전원 도착 · 만남 진행 중`**, 시간 행이 "최종 도착 마감"에서 **"만남 종료 예정"**으로 바뀜 |
| 기대 — DB | `match_groups.status = 'IN_PROGRESS'`, `completed_at IS NULL`, 참가자 2명 `ARRIVED` |
| 기대 — 온도 | **아직 오르지 않음** |

예전에는 마지막 도착자가 버튼을 누르는 순간 방이 사라지고 "만남이 완료됐어요"가 떴습니다. 그
동작이 남아 있으면 실패입니다.

### D. 만남 종료(1시간)와 보상 🕐 시간 조작

| | |
| --- | --- |
| 준비 | C 직후. 양쪽 온도를 기록해 둠 |
| 조작 | `confirmed_at`을 61분 당기고 **5초 이상 대기**(배치 주기) |
| 기대 — 화면 | 상태방이 닫히고 `/matching`으로 이동, "만남이 끝났어요. 매너온도가 올랐어요." |
| 기대 — DB | `status = 'COMPLETED'`, `completed_at` 기록, 참가자 `COMPLETED` |
| 기대 — 온도 | 양쪽 **`+0.50`**. `manner_temperature_events`에 `MATCH_COMPLETED` 2행 |
| 기대 — 재매칭 | **바로 신청 가능.** 방이 닫히는 시각과 잠금이 풀리는 시각이 같음 |

### E. 노쇼와 페널티 🕐 시간 조작

| | |
| --- | --- |
| 준비 | A·B 확정. **A만 도착** |
| 조작 | `confirmed_at`을 31분 당기고 5초 이상 대기 |
| 기대 — 화면 | A의 상태방이 닫히며 "남은 인원으로 만남을 계속할 수 없어 그룹이 종료됐어요." |
| 기대 — DB | B `NO_SHOW`, 그룹 `CANCELLED`(사유 `INSUFFICIENT_ACTIVE_MEMBERS`) |
| 기대 — 페널티 | B `penalty_score +3`, 쿨타임 30분(당일 2회차부터 60분) |
| 기대 — 온도 | **A도 오르지 않음.** 혼자 도착은 만남이 아님 |

### F. 확정 3분 이내 취소 — 무페널티

| | |
| --- | --- |
| 조작 | 확정 직후 3분 안에 A가 "못 갈 것 같아요" → 사유 선택 |
| 기대 — DB | A `CANCELLED`, 그룹 `CANCELLED`, **`match_penalty_events` 0행** |
| 기대 — 상대 | B 화면에 그룹 종료 안내, B는 즉시 재신청 가능 |

### G. 확정 3분 이후 취소 — 페널티

| | |
| --- | --- |
| 준비 | `confirmed_at`을 5분 당김 |
| 조작 | A가 "못 갈 것 같아요" |
| 기대 — 페널티 | A `penalty_score +1`, 쿨타임 **10분**(2회차 30분, 3회차부터 60분) |
| 기대 — 화면 | A가 `/matching`에서 재신청 시 쿨타임 안내 |

### H. 도착했지만 아직 혼자일 때 나가기

| | |
| --- | --- |
| 준비 | A만 도착, B는 아직 안 옴. `confirmed_at`을 5분 당김 |
| 기대 — 화면 | A에게 **"먼저 갈게요"가 보이지 않고 "못 갈 것 같아요"**가 보임 |
| 조작 | "못 갈 것 같아요" → 사유 선택 |
| 기대 — 화면 | 안내에 "아직 만남이 성사되지 않아 참여 취소로 처리돼요" |
| 기대 — 페널티 | G와 같은 취소 페널티 |

**이게 이번 변경의 핵심 방어입니다.** 도착 버튼을 눌렀다 나가는 것으로 취소 페널티를 피할 수
있으면 안 됩니다. "먼저 갈게요"가 여기서 보이면 실패입니다.

### I. 만남이 성립한 뒤 먼저 나가기

| | |
| --- | --- |
| 준비 | A·B 둘 다 도착(C 상태) |
| 기대 — 화면 | A에게 **"먼저 갈게요"**가 보이고 "못 갈 것 같아요"는 사라짐 |
| 조작 | "먼저 갈게요" → 안내 확인 → "나갈게요" |
| 기대 — 안내 | "이미 만남이 성사돼서 매너온도는 그대로 올라가요. 불이익은 없어요." |
| 기대 — 화면(A) | "먼저 나왔어요. 남은 멤버는 만남을 계속해요." |
| 기대 — 화면(B) | **상태방이 유지됨.** 혼자 남아도 열림, 타임라인에 "OO님이 먼저 갔어요" |
| 기대 — DB | A `LEFT` + `left_at` 기록, 그룹 `IN_PROGRESS` 유지, `match_events`에 `MEMBER_LEFT` |
| 기대 — 페널티 | **0행** |
| 이어서 | 종료(1시간) 뒤 **A와 B 둘 다 `+0.50`**. 먼저 갔다고 깎지 않음 |
| 기대 — 재매칭 | A도 종료 전까지는 신청 불가(`MATCHING_COMPLETION_LOCKED`) |

### J. 도착 2명 + 노쇼 1명 🕐 시간 조작

| | |
| --- | --- |
| 준비 | A·B·C 3인 매칭. A·B 도착, C 미도착 |
| 조작 | 31분 당기고 대기 → 다시 61분 당기고 대기 |
| 기대 — DB | C `NO_SHOW` + 페널티, 그룹은 **`IN_PROGRESS` 유지** 후 `COMPLETED` |
| 기대 — 온도 | A·B `+0.50`, C 없음 |
| 기대 — 재매칭 | **A·B가 새 매칭을 신청할 수 있다** |

예전에는 이 조합에서 그룹이 영영 닫히지 않아 **A·B의 매칭 신청이 영구히 막혔습니다.** 종료 후
`/matching`에서 신청이 되는지 반드시 확인합니다.

### K. 도착자가 1명뿐인 채 종료 🕐 시간 조작

| | |
| --- | --- |
| 준비 | 3인 매칭에서 A만 도착, B·C 미도착 |
| 조작 | 31분 당기고 대기 |
| 기대 — DB | 그룹 `CANCELLED`, B·C `NO_SHOW` |
| 기대 — 온도 | A 변화 없음 |

### L. 만남이 없었던 매칭은 신고 불가

| | |
| --- | --- |
| 준비 | F(3분 이내 취소)로 만든 기록 |
| 조작 | 마이페이지 → 매칭 기록 |
| 기대 — 화면 | **"만남이 성사되지 않아 신고할 수 없어요"**, 신고 버튼 비활성화 |
| 확인 | "신고 기간 종료"가 아니어야 함. 두 문구는 뜻이 다름 |

### M. 노쇼로 취소된 건은 신고 가능

| | |
| --- | --- |
| 준비 | E로 만든 기록(A는 도착, B는 노쇼) |
| 조작 | A의 매칭 기록에서 B 신고 → 사유 `NO_SHOW` |
| 기대 — 화면 | 신고 버튼 활성, "신고 가능 (…까지)" |
| 기대 — DB | `reports` 1행 `SUBMITTED` |

**도착 여부를 기준으로 잡은 이유가 이 시나리오입니다.** 경과 시간 기준이었으면 이 신고가 함께
막혔을 수 있습니다.

### N. 매너온도 노출

| | |
| --- | --- |
| 조작 | 마이페이지 / 매칭 화면 |
| 기대 | 마이페이지에 게이지 + 숫자 + 구간 안내, 매칭 화면 우상단에 한 줄 |
| 확인 | **다른 회원의 온도는 어디에도 보이지 않아야 함**(`docs/19` 4.9) |

### O. 알림 — 현재 상태 기록용

| | |
| --- | --- |
| 조작 | A가 홈(`/`)에 머무는 동안 B가 도착·취소 |
| 현재 기대 | **아무 알림도 뜨지 않는다.** 헤더 종 아이콘을 눌러도 반응 없음 |

이것은 버그가 아니라 **미구현 상태의 확인**입니다(`docs/19` 4.11.5). 알림 작업이 끝나면 이
시나리오의 기대값이 바뀝니다.

## 4. 확인용 SQL

```sql
-- 그룹과 참가자 한눈에
SELECT g.id, g.status AS group_status, g.confirmed_at, g.completed_at, g.cancelled_at,
       g.cancel_reason, m.member_id, m.status AS member_status,
       m.arrived_at, m.arrival_distance_meters, m.left_at, m.no_show_at
FROM match_groups g
JOIN match_group_members m ON m.group_id = g.id
WHERE g.id = :groupId
ORDER BY m.id;

-- 타임라인
SELECT event_type, member_id, payload, created_at
FROM match_events WHERE group_id = :groupId ORDER BY id;

-- 매너온도 이력
SELECT member_id, event_type, delta, before_temperature, after_temperature,
       related_group_id, created_at
FROM manner_temperature_events WHERE related_group_id = :groupId ORDER BY id;

-- 페널티와 쿨타임
SELECT member_id, event_type, score_delta, reason, created_at
FROM match_penalty_events WHERE related_group_id = :groupId ORDER BY id;

SELECT member_id, reason, status, starts_at, expires_at
FROM match_cooldowns WHERE related_group_id = :groupId ORDER BY id;

-- 회원 현재 값
SELECT id, status, penalty_score, manner_temperature FROM members WHERE id IN (:a, :b);
```

## 5. 결과 기록

| 시나리오 | 결과 | 확인일 | 메모 |
| --- | --- | --- | --- |
| A 신청~확정 | | | |
| B 도착 반경 | | | |
| C 전원 도착 후 방 유지 | | | |
| D 만남 종료와 보상 | | | |
| E 노쇼와 페널티 | | | |
| F 3분 이내 취소 | | | |
| G 3분 이후 취소 | | | |
| H 혼자일 때 나가기 | | | |
| I 성립 후 먼저 나가기 | | | |
| J 도착 2 + 노쇼 1 | | | |
| K 도착 1명 종료 | | | |
| L 신고 불가 | | | |
| M 노쇼 신고 | | | |
| N 온도 노출 | | | |
| O 알림 미구현 | | | |

## 6. 아직 이 문서가 덮지 않는 것

- **관리자 기능** — 신고 검토, 제재, 온도 수동 조정(`docs/19` 4.1~4.3, 4.9 PR A)
- **탈퇴와 재가입**(4.4), **1:1 문의**(4.5), **동의 흐름**(4.7)
- **30도 매칭 제한**(4.9 PR C)과 **후기**(PR D) — 미착수
- **알림** — 구현 후 시나리오 O를 실제 기대값으로 바꾸고 항목을 늘립니다
