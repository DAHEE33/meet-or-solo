# 매칭 수동 검증 시나리오

## 1. 이 문서의 목적

**매칭과 관련해 지금까지 만든 것 전부**를 사람이 직접 밟아 보는 문서입니다. 매칭 신청부터
제안·확정·도착·이탈·종료·보상·신고·차단·알림, 그리고 실패 시 솔로 코스 전환까지 다룹니다.

자동 테스트가 덮는 것과 사람이 봐야 하는 것은 다릅니다.

| 자동 | 사람이 봐야 하는 것 |
| --- | --- |
| `MatchMeetingLifecycleHarnessTest` — HTTP 경로로 만남 수명주기 | **화면에 실제로 그렇게 보이는가** — 버튼 노출, 문구, 비활성화 |
| `notificationFlow.harness.test.tsx` — 알림 흐름 | **환경 설정이 실제 값으로 도는가** — 하네스는 반경 검증을 강제로 켜지만 dev는 우회로 돈다 |
| 서비스·컴포넌트 단위 테스트 | **스케줄러가 실제로 집어가는가** — 하네스는 배치를 직접 호출한다 |

기준 문서는 `docs/19` 4.11(만남 성립 판정), `docs/05`(매칭 정책), `docs/26`(솔로 코스 전환)이고,
구현 경위는 `docs/10_PROGRESS_LOG.md`에 있습니다.

**관리자 기능은 이 문서에서 다루지 않습니다.** 신고 검토·제재·온도 수동 조정 등은
`docs/19` 4.1~4.3, 4.8, 4.9 PR A의 범위입니다.

## 2. 전제 조건

### 계정

| 별칭 | 용도 |
| --- | --- |
| A | 주 테스트 계정 |
| B | 상대역 |
| C | 3인 시나리오와 노쇼역 (선택) |

모두 프로필 완료(`ACTIVE`)여야 하고, 같은 축제에 **GPS 체크인**이 되어 있어야 합니다.
현장에 가지 않고 체크인하려면 그 계정을 **테스트 계정으로 지정**합니다(`members.test_account`,
아래 "환경 변수" 참고). 환경 전체를 끄는 설정은 쓰지 않습니다.

### 축제와 만남 장소

- 축제가 `ACTIVE`이고 좌표(`map_x`, `map_y`)가 있어야 합니다.
- **그 축제에 `ACTIVE` 만남 장소가 최소 1개** 있어야 합니다. 없으면 매칭 신청이
  `MATCHING_MEETING_POINT_NOT_READY`로 막힙니다. 관리자 화면 `/admin/meeting-points`에서 등록합니다.

### 환경 변수

```bash
MATCHING_SCHEDULER_ENABLED=true          # 매칭 성사와 만남 종료 배치
MATCHING_NO_SHOW_SCHEDULER_ENABLED=true  # 도착 마감 노쇼 처리
MATCHING_ARRIVAL_RADIUS_METERS=150       # 도착 인정 반경
```

**GPS 반경은 환경 전체로 끄지 않습니다.** 체크인·도착 모두 어느 환경에서나 검증이 켜져 있고,
현장에 가지 않고 테스트하려면 **`/admin/members`에서 그 계정을 테스트 계정으로 지정**합니다
(`members.test_account`). 계정 단위 면제라 같은 환경에서 일반 계정은 반경 검증을 그대로 받고,
그래서 **검증이 실제로 동작하는지도 함께 확인할 수 있습니다.**

그 화면에 들어가려면 관리자 계정이 필요합니다. 아래 둘을 채우고 재기동해야
`/admin/login`으로 들어갈 수 있습니다. **하나라도 비어 있으면 계정이 생성되지 않습니다**
(`SuperAdminAccountBootstrap`). 값이 없어 화면에 못 들어가는 것이 "GPS 면제가 안 걸린다"의
가장 흔한 원인입니다.

```bash
ADMIN_LOCAL_USERNAME=<ADMIN_LOCAL_USERNAME>   # 실제 값은 저장소에 넣지 않는다
ADMIN_LOCAL_PASSWORD=<ADMIN_LOCAL_PASSWORD>
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
| 기대 — 화면 | 대기(`WAITING`) → 제안(`INITIAL_PROPOSAL`) → 상태방. "매칭이 확정됐어요", 만남 장소, **최종 도착 마감**, 남은 시간 |
| 기대 — DB | `match_pools.status`가 `WAITING` → `PROPOSED` → `MATCHED`, `match_groups.status = 'CONFIRMED'`, 참가자 2명 `JOINED` |

**제안 응답은 30초입니다.** 놓치면 `penalty_score +1`과 쿨타임 2분이 붙으니, 양쪽 화면을 열어둔
채로 진행합니다.

### A-1. 체크인 없이 신청하면 막힌다

| | |
| --- | --- |
| 준비 | 체크인하지 않은 계정 |
| 조작 | `/matching`에서 신청 |
| 기대 — 화면 | "해당 축제의 유효한 체크인이 필요합니다" 계열 안내 |
| 확인 | 체크인은 **유효기간**이 있다. 만료된 체크인으로도 막히는지 함께 본다 |

### A-2. 만남 장소가 없는 축제는 신청이 막힌다

| | |
| --- | --- |
| 준비 | `ACTIVE` 만남 장소가 없는 축제에 체크인 |
| 조작 | 매칭 신청 |
| 기대 — 화면 | `MATCHING_MEETING_POINT_NOT_READY` — "만남 장소를 준비하고 있습니다" |

운영에서 가장 흔한 막힘입니다. 관리자 화면에서 장소를 먼저 등록해야 합니다.

### A-3. 대기 중 신청 취소

| | |
| --- | --- |
| 조작 | 신청 후 대기 화면에서 취소 |
| 기대 — DB | `match_pools.status = 'CANCELLED'` |
| 기대 — 화면 | 다시 신청 가능한 초기 화면 |

### A-4. 제안 거절과 응답 시간 초과

| | |
| --- | --- |
| 조작 ① | 제안이 뜨면 A가 **거절** |
| 기대 ① | 쿨타임 **30초**, `penalty_score` 변화 없음. B에게는 매칭 실패 안내 |
| 조작 ② | 다시 매칭해 제안이 뜨면 **아무것도 누르지 않고 30초 대기** |
| 기대 ② | `MATCH_TIMEOUT`. **`penalty_score +1`**, 쿨타임 **2분** |
| 확인 | 거절과 시간 초과의 페널티가 다르다. 거절은 의사 표시, 무응답은 방치로 본다 |

### A-5. 인원 부족 2차 확인

| | |
| --- | --- |
| 준비 | 3~4인으로 신청하되 실제 수락자는 2명 |
| 조건 | **수락자 전원이 "2명이어도 괜찮아요"에 체크**해야 이 흐름이 열린다 |
| 기대 — 화면 | "현재 인원으로 시작할까요 / 취소할까요" 2차 제안(`INSUFFICIENT_MEMBERS_PROPOSAL`) |
| 조작 | 한쪽이 "현재 인원으로 시작" → 다른 쪽도 수락 |
| 기대 | 2인으로 확정 |
| 확인 | 한 명이라도 체크하지 않았으면 **이 확인이 뜨지 않고 매칭이 그냥 실패**한다 |

### A-6. 도착 예정 시간 선택

| | |
| --- | --- |
| 조작 | 상태방에서 "몇 분 후 도착하나요?" → 시간 선택 |
| 기대 — 화면 | 예상 도착 시각과 남은 시간. 같은 값을 다시 골라도 **연장되지 않는다**는 안내 |
| 기대 — 상대 | 타임라인에 "OO님이 N분 후 도착할 예정이에요" |
| 확인 | 도착 마감(30분)을 넘는 시간은 선택할 수 없다 |

### A-7. 상태방 타임라인

| | |
| --- | --- |
| 조작 | 도착 시간 선택 → 도착 → 상대 취소·노쇼 등 여러 사건 발생 |
| 기대 — 화면 | 사건이 시간순으로 쌓이고 문구가 사람이 읽을 수 있게 나온다 |
| 기대 — DB | `match_events`의 행과 화면이 일치 |

### B. 도착 반경 검증 ⚠️ 설정 변경 필요

| | |
| --- | --- |
| 준비 | A 상태방 진입. **A를 테스트 계정에서 해제**(`/admin/members`) — 그래야 반경 검증을 받는다 |
| 조작 | 만남 장소에서 150m 넘게 떨어진 곳에서 "도착했어요" (브라우저 개발자도구의 위치 재정의 사용) |
| 기대 — 화면 | "만남 장소 근처에서 도착을 인증해주세요" |
| 기대 — DB | 해당 참가자 `status`가 `JOINED` 그대로, `arrival_distance_meters`는 `NULL` |
| 이어서 | 반경 안 좌표로 다시 도착 → 성공, `arrival_distance_meters`에 거리(미터)가 남음 |

**좌표는 저장되지 않아야 합니다.** `match_group_members`에 위경도 컬럼이 없다는 것을 확인합니다.

이어서 **A를 테스트 계정으로 지정**하고 같은 반경 밖 좌표로 다시 눌러보세요. 이번에는 통과해야
합니다. 같은 환경에서 계정에 따라 갈리는 것이 이 설계의 핵심입니다.

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

### N. 상대 차단

| | |
| --- | --- |
| 조작 | 상태방 또는 매칭 기록에서 상대 차단 |
| 기대 — 화면 | 차단 완료 안내. 마이페이지 → 차단 목록에 상대가 보임 |
| 이어서 | **차단한 상대와는 다시 매칭되지 않는다.** 둘 다 같은 축제에 체크인하고 신청해 확인 |
| 해제 | 차단 목록에서 해제하면 다시 매칭 후보가 된다 |

### O. 매칭 실패 후 솔로 코스 전환

| | |
| --- | --- |
| 준비 | 제안 거절이나 시간 초과로 매칭 실패(`CANCELLED` 화면) |
| 기대 — 화면 | 실패 카드에 **"솔로 코스 추천 보기"** 링크 |
| 조작 | 링크 진입 |
| 기대 | 체크인한 축제 기준 타임라인 코스가 보임 |
| 확인 | **쿨타임 중에도 솔로 코스는 열려 있어야 한다.** 재신청만 잠기고 코스는 막히지 않는다(`docs/05`) |

### P. 체크인을 바꾸면 대기 중 매칭이 정리된다

| | |
| --- | --- |
| 준비 | 축제 X에 체크인하고 매칭 신청(대기 상태) |
| 조작 | 축제 Y에 체크인(또는 체크인 취소) |
| 기대 — DB | 축제 X의 `match_pools`가 정리됨 |
| 기대 — 화면 | 매칭 화면이 대기 상태로 남아 있지 않음 |

한 사람이 동시에 두 곳에 있을 수 없으므로 새 체크인은 기존 체크인을 취소하고, 그 이벤트를
매칭 도메인이 받아 대기 중인 신청을 정리합니다(`docs/21`).

### Q. 매너온도 노출

| | |
| --- | --- |
| 조작 | 마이페이지 / 매칭 화면 |
| 기대 | 마이페이지에 게이지 + 숫자 + 구간 안내, 매칭 화면 우상단에 한 줄 |
| 확인 | **다른 회원의 온도는 어디에도 보이지 않아야 함**(`docs/19` 4.9) |

### R. 알림 (1단계 — 실시간)

#### 서버가 보내는 알림 14종

매칭 한 판을 밟으면 대부분 자연히 확인됩니다. 어떤 상황에 무엇이 오는지 미리 보고 시작하세요.

| # | 언제 | 누가 받나 | 표시 | 문구 | 누르면 |
| --- | --- | --- | --- | --- | --- |
| 1 | 매칭 상대를 찾아 제안 | 제안 대상 전원 | 🔴 **배너** | 매칭 상대를 찾았어요 / **30초 안에 수락해야 매칭이 이어져요** | `/matching` |
| 2 | 전원 수락해 확정 | 그룹 전원 | 🔴 **배너** | 매칭이 확정됐어요 | `/match-room` |
| 3 | 한 명이 수락 | 시도 참여자 | 토스트 | 상대가 수락했어요 | `/matching` |
| 4 | 한 명이 거절 | 시도 참여자 | 토스트 | 이번 매칭은 성사되지 않았어요 | `/matching` |
| 5 | 30초 무응답 | 시도 참여자 | 토스트 | 응답 시간이 지나 매칭이 종료됐어요 | `/matching` |
| 6 | 인원 미달로 종료 | 시도 참여자 | 토스트 | 인원이 모자라 매칭이 종료됐어요 | `/matching` |
| 7 | 도착 예정 시간 선택 | 활성 구성원 | 토스트 | 상대가 도착 예정 시간을 알렸어요 | `/match-room` |
| 8 | 한 명 도착 | 활성 구성원 | 토스트 | 상대가 만남 장소에 도착했어요 | `/match-room` |
| 9 | 전원 도착 | 활성 구성원 | 토스트 | 모두 도착했어요 / 만남이 시작됐어요 | `/match-room` |
| 10 | 한 명 참여 취소 | 활성 구성원 | 토스트 | 한 명이 참여를 취소했어요 | `/match-room` |
| 11 | 먼저 갈게요 | 활성 구성원 | 토스트 | 한 명이 먼저 갔어요 | `/match-room` |
| 12 | 노쇼 처리 | 활성 구성원 | 토스트 | 도착 마감까지 오지 않은 멤버가 있어요 | `/match-room` |
| 13 | 그룹 종료 | 활성·도착 구성원 | 토스트 | 만남이 종료됐어요 | `/matching` |
| 14 | 만남 완료 | **도착자만** | 토스트 | 만남이 끝났어요 / **매너온도가 올랐어요** | `/mypage` |

배너는 **1·2번뿐**입니다. 응답 시간이 30초라 놓치면 페널티가 붙거나 만남 준비를 못 하기
때문에, 이 둘만 저절로 사라지지 않습니다.

**내 행동이 실시간 알림으로는 나에게도 돌아옵니다.** WebSocket은 관련된 회원 전원에게 같은
사유를 보내므로, 내가 도착을 누르면 나에게도 8번이 갑니다. 그 행동을 한 화면에 있는 동안은
"같은 화면이면 토스트 생략" 규칙이 눌러주므로 실제로는 보이지 않아야 합니다.
**만약 보인다면 그건 결함**이니 기록해 주세요.

**알림함(2단계)에는 행위자 본인의 것이 남지 않습니다.** 서버가 `actorMemberId`를 보고 거르기
때문입니다. 내가 취소했는데 내 목록에 "한 명이 참여를 취소했어요"가 쌓이면 그건 결함입니다.

#### 시나리오와 알림 대조

| 밟는 시나리오 | 같이 확인되는 알림 |
| --- | --- |
| A 신청~확정 | 1(배너), 3, 2(배너) |
| A-4 거절·시간 초과 | 4, 5 |
| A-5 인원 부족 2차 확인 | 6 |
| A-6 도착 예정 시간 | 7 |
| C 전원 도착 | 8, 9 + **상태방에서 토스트가 안 뜨는지** |
| D 만남 종료 | 14 + 매너온도 안내 |
| E 노쇼 | 12, 13 |
| F·G 취소 | 10, 13 |
| I 먼저 갈게요 | 11 |

#### 확인 절차

**계정 두 개를 다른 브라우저(또는 시크릿 창)로 열고, 한쪽을 홈(`/`)에 두세요.** 그 상태에서
매칭을 돌려야 "화면을 보고 있지 않아도 알림이 오는가"가 확인됩니다. 그게 이 기능을 만든
이유입니다.

| # | 조작 | 기대 |
| --- | --- | --- |
| R-1 | A가 **홈(`/`)에 머무는 동안** 매칭 제안이 뜸 | 화면 위에 **배너** "매칭 상대를 찾았어요 / 30초 안에 수락해야 매칭이 이어져요". **저절로 사라지지 않음** |
| R-2 | 배너를 누름 | `/matching`으로 이동 |
| R-3 | 매칭 확정 | 배너 "매칭이 확정됐어요" → 누르면 상태방 |
| R-4 | 홈에서 상대가 도착 | **토스트** 5초 후 사라짐 |
| R-5 | **상태방을 보는 중** 상대가 도착 | **토스트가 뜨지 않는다.** 화면이 이미 갱신되므로 중복이다. 단 종 목록에는 남아야 함 |
| R-6 | 만남 종료 | 토스트 "만남이 끝났어요 / 매너온도가 올랐어요" → 누르면 마이페이지 |
| R-7 | 종 아이콘 | 읽지 않음 **뱃지 숫자**(10건 이상이면 `9+`) |
| R-8 | 종을 눌러 목록 열기 | 최근 알림 목록, 하단에 "최근 100건, 30일까지 보관해요". **여는 순간 뱃지가 사라짐** |
| R-9 | 새로고침 | 목록이 유지됨 |
| R-10 | 로그아웃 상태로 `/login` | 개발자도구 네트워크에 **WebSocket 재시도가 반복되지 않음** |

**R-1이 이 기능을 만든 이유입니다.** 제안 응답은 30초이고 놓치면 `penalty_score +1`과 쿨타임
2분이 붙는데, 예전에는 매칭 화면을 보고 있지 않으면 그대로 놓쳤습니다.

**R-5도 꼭 봐주세요.** 소켓이 둘로 갈라지면 같은 알림이 두 번 뜹니다. 자동 테스트가 막고 있지만
화면에서 한 번 더 확인할 가치가 있습니다.

### R-2단계. 서버 알림함

1단계와 달리 **서버가 목록을 갖습니다**(`notifications` 테이블). 기기를 바꿔도 남고, 로그인하면
못 본 알림이 그대로 있어야 합니다.

| # | 조작 | 기대 |
| --- | --- | --- |
| R2-1 | A로 매칭을 한 판 돌린 뒤 **다른 브라우저로 같은 계정 로그인** | 종 목록에 앞의 알림이 그대로 있음. 뱃지 숫자도 같음 |
| R2-2 | 한쪽에서 종을 열어 읽음 처리 → 다른 쪽 새로고침 | **뱃지가 사라져 있음**(읽음이 서버에 남는다) |
| R2-3 | 브라우저 저장소(localStorage)를 지우고 새로고침 | 목록이 그대로 복원됨 |
| R2-4 | 상태방에서 **내가** 도착·취소를 누름 | 종 목록에 내 행동에 대한 알림이 **쌓이지 않음** |
| R2-5 | 도착·예정 시간 선택 같은 중간 상태 | 토스트로는 뜨지만 **목록에는 남지 않음**(의도된 동작) |
| R2-6 | 목록 하단 | "최근 100건, 30일까지 보관해요" |

DB로 확인하려면:

```sql
SELECT reason, actor_member_id, occurred_at, read_at
FROM notifications WHERE member_id = :a ORDER BY created_at DESC LIMIT 20;
```

### R-3단계. Web Push (앱이 꺼져 있을 때)

**VAPID 키가 있어야 합니다.** `.env`의 `WEB_PUSH_VAPID_PUBLIC_KEY`·`_PRIVATE_KEY`가 비어 있으면
push만 꺼지고 1·2단계는 그대로 동작합니다(그 경우 이 절은 건너뜁니다).

| # | 조작 | 기대 |
| --- | --- | --- |
| R3-1 | A로 **처음** 매칭 신청 | 브라우저 알림 권한 요청이 **한 번** 뜸 |
| R3-2 | 허용 후 다시 신청 | 권한 요청이 다시 뜨지 않음 |
| R3-3 | **탭을 닫고** B가 매칭을 성사시킴 | 운영체제 알림으로 "매칭 상대를 찾았어요"가 뜸 |
| R3-4 | 그 알림을 누름 | 앱이 열리며 `/matching`으로 이동. 이미 열린 탭이 있으면 **새 탭을 만들지 않음** |
| R3-5 | 만남 완료·취소 같은 종결 알림 | **push로 오지 않음**(알림함에만 남는다. 의도된 동작) |
| R3-6 | 권한을 거절 | 매칭 신청은 그대로 진행됨. 다시 묻지 않음 |

**iOS Safari는 홈 화면에 설치해야 push가 옵니다**(16.4+). 설치하지 않은 상태에서는 권한 요청
자체가 뜨지 않는 것이 정상입니다.

구독이 저장됐는지 확인:

```sql
SELECT member_id, left(endpoint, 40) AS endpoint, created_at FROM push_subscriptions;
```

## 4. DB 조회와 조작

### 4.0 먼저 — 어느 DB인가

`.env`의 `DB_HOST`/`DB_PORT`/`POSTGRES_DB`를 확인하세요. **`meet_or_solo_dev`면 SSH 터널로
공유 dev DB에 붙어 있는 것**이고, 이 절의 `UPDATE`·`DELETE`는 **쓰면 안 됩니다**(`docs/08`).

조작이 필요한 시나리오(🕐 표시, 그리고 4.3의 초기화)는 **로컬 DB에서** 하세요.

```bash
docker compose -f docker-compose.local.yml up -d
# .env에서 DB_HOST=localhost, DB_PORT=5432, POSTGRES_DB=meet_or_solo_local 로 변경
```

### 4.1 시작 전 — id부터 찾는다

거의 모든 조회가 `memberId`와 `groupId`를 요구합니다. 매번 이 둘부터 찾으세요.

```sql
-- 1) 내 memberId (닉네임으로)
SELECT id, nickname, status, role, test_account, penalty_score, manner_temperature
FROM members
WHERE nickname IN ('테스터A', '테스터B');

-- 2) 지금 내가 속한 활성 그룹
SELECT g.id AS group_id, g.status, g.confirmed_at, g.confirmed_member_count
FROM match_groups g
JOIN match_group_members m ON m.group_id = g.id
WHERE m.member_id = :memberId
  AND g.status IN ('CONFIRMED', 'IN_PROGRESS')
ORDER BY g.id DESC;

-- 3) 방금 끝난 그룹 (완료 카드·신고 확인용)
SELECT g.id AS group_id, g.status, g.completed_at, g.cancelled_at, g.cancel_reason
FROM match_groups g
JOIN match_group_members m ON m.group_id = g.id
WHERE m.member_id = :memberId
ORDER BY g.id DESC
LIMIT 5;
```

### 4.2 시나리오별 — 무엇을 보는가

#### 매칭 신청·제안 (A, A-1 ~ A-5)

```sql
-- 내 매칭 신청. WAITING -> PROPOSED -> MATCHED 순으로 바뀐다.
-- search_expires_at이 지나면 EXPIRED가 되고 매칭은 실패한다(검색 창 60초).
SELECT id, status, preferred_group_size, allow_minimum_two,
       entered_at, search_expires_at, festival_id
FROM match_pools
WHERE member_id = :memberId
ORDER BY id DESC LIMIT 3;

-- 제안과 응답. SENT면 아직 응답 전이고 expires_at까지 30초다.
SELECT p.id, p.attempt_id, p.member_id, p.status, p.sent_at, p.expires_at, p.responded_at
FROM match_proposals p
WHERE p.attempt_id = (
    SELECT attempt_id FROM match_attempt_members WHERE member_id = :memberId
    ORDER BY attempt_id DESC LIMIT 1)
ORDER BY p.id;

-- 체크인이 유효한가. 유효한 ACTIVE 체크인이 없으면 신청 자체가 막힌다.
SELECT id, festival_id, status, distance_meters, checked_in_at, expires_at
FROM festival_checkins
WHERE member_id = :memberId
ORDER BY id DESC LIMIT 3;

-- 그 축제에 ACTIVE 만남 장소가 있는가. 없으면 MATCHING_MEETING_POINT_NOT_READY.
SELECT id, name, status FROM festival_meeting_points WHERE festival_id = :festivalId;
```

#### 도착·이탈·종료 (B, C, D, H, I, J, K)

```sql
-- 그룹과 참가자를 한 번에. 이 문서에서 가장 자주 쓰는 조회다.
SELECT g.id, g.status AS group_status, g.confirmed_at, g.completed_at,
       g.cancelled_at, g.cancel_reason,
       m.member_id, m.status AS member_status,
       m.arrival_minutes, m.arrived_at, m.arrival_distance_meters,
       m.left_at, m.no_show_at, m.allow_minimum_two
FROM match_groups g
JOIN match_group_members m ON m.group_id = g.id
WHERE g.id = :groupId
ORDER BY m.id;
```

읽는 법입니다.

| 보이는 것 | 뜻 |
| --- | --- |
| `member_status = 'ARRIVED'` + `arrived_at` | 도착 완료 |
| `arrival_distance_meters` 있음 | 반경 검증을 통과했거나 면제로 인정됐다. **좌표는 저장되지 않는다** |
| `member_status = 'LEFT'` + `left_at` 있음 | 본인이 "먼저 갈게요"로 나감 |
| `member_status = 'LEFT'` + `left_at` 없음 | 그룹 종료로 정리됨(본인 의사 아님) |
| `member_status = 'NO_SHOW'` + `no_show_at` | 도착 마감까지 안 옴 |
| `group_status = 'COMPLETED'` | 만남 성립(도착자 2명 이상) + 보상 지급 |
| `cancel_reason = 'INSUFFICIENT_ARRIVALS'` | 만남 시간이 끝났는데 도착자가 1명 이하 |
| `cancel_reason = 'INSUFFICIENT_ACTIVE_MEMBERS'` | 남은 인원이 1명 이하 |
| `cancel_reason = 'MINIMUM_TWO_NOT_ALLOWED'` | 2명 남았는데 동의하지 않은 사람이 있음 |

```sql
-- 타임라인. 화면에 보이는 순서와 같아야 한다.
SELECT event_type, member_id, payload, created_at
FROM match_events WHERE group_id = :groupId ORDER BY id;
```

#### 페널티·쿨타임 (A-4, E, F, G)

```sql
-- 이 그룹/풀에서 생긴 페널티
SELECT member_id, event_type, score_delta, reason,
       related_group_id, related_pool_id, created_at
FROM match_penalty_events
WHERE related_group_id = :groupId OR related_pool_id = :poolId
ORDER BY id;

-- 지금 걸려 있는 쿨타임. expires_at이 지나면 화면에서 풀린다.
SELECT id, member_id, reason, status, starts_at, expires_at
FROM match_cooldowns
WHERE member_id = :memberId AND status = 'ACTIVE'
ORDER BY id DESC;
```

| `reason` | 언제 | 길이 |
| --- | --- | --- |
| `REJECT` | 제안 거절 | 30초 |
| `TIMEOUT` | 제안 무응답 | 2분 (`penalty_score +1`) |
| `CANCEL` | 매칭 탐색 취소 | 20초 → 1분 → 5분 → 10분 |
| `NO_SHOW` | 도착 마감까지 미도착 | 30분 → 60분 (`penalty_score +3`) |

#### 매너온도 (D, I, Q)

```sql
SELECT member_id, event_type, delta, before_temperature, after_temperature,
       related_group_id, created_at
FROM manner_temperature_events
WHERE related_group_id = :groupId
ORDER BY id;

SELECT id, nickname, manner_temperature, penalty_score FROM members WHERE id IN (:a, :b);
```

완료 보상은 `MATCH_COMPLETED` / `+0.50`입니다. **도착자에게만** 들어가고 그룹당 한 번입니다.

#### 신고·차단 (L, M, N)

```sql
SELECT id, reporter_member_id, reported_member_id, group_id, reason_code, status, created_at
FROM reports WHERE group_id = :groupId ORDER BY id;

-- 신고 가능 여부의 근거. 이 그룹에 도착자가 있었는가(docs/19 4.11.1)
SELECT count(*) AS arrived_count
FROM match_group_members WHERE group_id = :groupId AND arrived_at IS NOT NULL;
```

`arrived_count`가 **0이면 신고 버튼이 비활성**이고 "만남이 성사되지 않아 신고할 수 없어요"가
떠야 합니다. 1 이상이면 신고할 수 있습니다.

### 4.3 조작 — 시간 당기기와 초기화

> **로컬 DB에서만 하세요.** 공유 dev DB에는 쓰지 않습니다.

```sql
-- 도착 마감(30분)을 지나게 한다. 노쇼 배치가 5초 안에 처리한다.
UPDATE match_groups SET confirmed_at = confirmed_at - INTERVAL '31 minutes' WHERE id = :groupId;

-- 만남 종료(1시간)를 지나게 한다. 종료 배치가 5초 안에 닫는다.
UPDATE match_groups SET confirmed_at = confirmed_at - INTERVAL '61 minutes' WHERE id = :groupId;

-- 취소 무페널티 구간(확정 후 3분)을 지나게 한다. G 시나리오용.
UPDATE match_groups SET confirmed_at = confirmed_at - INTERVAL '5 minutes' WHERE id = :groupId;
```

시나리오를 여러 번 돌리려면 아래를 **매번 초기화**해야 합니다. 그러지 않으면 쿨타임이 쌓여
신청이 막히고, 재매칭 잠금 때문에 새 매칭이 잡히지 않습니다.

```sql
-- 1) 남아 있는 활성 그룹·풀 정리 (테스트가 중간에 끊겼을 때)
UPDATE match_group_members SET status = 'LEFT'
WHERE member_id IN (:a, :b) AND status IN ('JOINED', 'ARRIVAL_TIME_SELECTED', 'ARRIVED');

UPDATE match_groups SET status = 'CANCELLED', cancelled_at = now(),
       cancel_reason = 'INSUFFICIENT_ACTIVE_MEMBERS'
WHERE status IN ('CONFIRMED', 'IN_PROGRESS');

UPDATE match_pools SET status = 'CANCELLED'
WHERE member_id IN (:a, :b) AND status IN ('WAITING', 'LOCKED', 'PROPOSED');

-- 2) 쿨타임 해제
UPDATE match_cooldowns SET status = 'EXPIRED'
WHERE member_id IN (:a, :b) AND status = 'ACTIVE';

-- 3) 재매칭 잠금 해제 — 지난 만남을 2시간 전으로 밀어낸다
UPDATE match_groups SET confirmed_at = confirmed_at - INTERVAL '2 hours'
WHERE id IN (SELECT group_id FROM match_group_members WHERE member_id IN (:a, :b));

-- 4) 매너온도·페널티 되돌리기 (여러 번 돌린 뒤 값이 헷갈릴 때)
UPDATE members SET manner_temperature = 36.50, penalty_score = 0 WHERE id IN (:a, :b);

-- 5) 테스트 계정 지정·해제 (관리자 화면 대신 SQL로)
UPDATE members SET test_account = TRUE  WHERE id IN (:a, :b);
UPDATE members SET test_account = FALSE WHERE id IN (:a, :b);
```

**1)을 먼저 하고 2)3)을 하세요.** 활성 그룹이 남아 있으면 잠금·쿨타임을 풀어도 새 신청이
`이미 활성 매칭 그룹에 참여 중입니다`로 막힙니다.

### 4.4 자주 막히는 지점

| 증상 | 확인할 것 |
| --- | --- |
| 매칭 신청 버튼이 회색 | 체크인이 `ACTIVE`이고 `expires_at`이 남았는가 / 쿨타임·완료 잠금이 있는가 |
| 눌리는데 반응 없음 | **화면에 사유 한 줄이 뜬다.** 안 뜨면 그건 결함이니 기록 |
| 신청은 되는데 매칭이 안 됨 | 상대와 `festival_id`가 같은가 / `match_pools.status`가 `WAITING`에서 안 바뀌는가 / **희망 인원이 다르면 작은 쪽에 맞춰 묶이므로 "인원이 적어도 진행"이 켜져 있는가** |
| 제안이 안 옴 | `MATCHING_SCHEDULER_ENABLED=true`인가 / 서버 로그에 스케줄러 tick이 보이는가 |
| 도착이 거절됨 | `test_account`인가 / 브라우저 위치 권한을 허용했는가 / 만남 장소에 좌표가 있는가 |
| 30분·1시간이 지나도 그대로 | `MATCHING_NO_SHOW_SCHEDULER_ENABLED` / `MATCHING_SCHEDULER_ENABLED`가 켜져 있는가 |

### 4.5 GPS로 막혔을 때 — 3단계 진단

"현장에 가지 않았는데 체크인·도착이 막힌다"는 **대부분 결함이 아니라 면제가 안 걸린 것**입니다.
위에서부터 순서대로 확인합니다. 조회는 [`scripts/diagnose-gps-bypass.sql`](../scripts/diagnose-gps-bypass.sql)이
[1]~[4]로 한 번에 해 줍니다.

| 단계 | 확인 | 아니면 |
| --- | --- | --- |
| ① 막힌 이유가 GPS인가 | 실패 응답의 `code`가 `CHECKIN_OUT_OF_RANGE`·`LOW_LOCATION_ACCURACY`·`MATCHING_ARRIVAL_OUT_OF_RANGE`인가 | 셋 다 아니면 GPS 경로가 아니다. 체크인 만료·쿨타임·활성 그룹을 본다(스크립트 [3]) |
| ② 면제가 켜져 있는가 | `members.test_account`가 `TRUE`인가(스크립트 [1]) | `/admin/members`에서 지정한다. 화면에 못 들어가면 ③ |
| ③ 관리자로 들어갈 수 있는가 | `admin_credentials`에 행이 있는가(스크립트 [2]) | **`.env`에 `ADMIN_LOCAL_USERNAME`·`ADMIN_LOCAL_PASSWORD`를 채우고 재기동**한다. 둘 중 하나라도 비면 계정이 생성되지 않는다 |

②까지 통과했는데도 거절되면 그때가 결함입니다. 면제가 실제로 걸리면 서버 로그에 `WARN`이 남습니다.

```text
GPS 반경 검증을 건너뛰고 체크인을 허용했습니다(사유=TEST_ACCOUNT)
도착 반경 검증을 건너뛰고 도착을 인정했습니다(사유=TEST_ACCOUNT)
```

이 줄이 없는데 거절됐다면 면제가 걸리지 않은 것이고, 있는데도 화면이 실패라면 다른 원인입니다.

## 5. 결과 기록

| 시나리오 | 결과 | 확인일 | 메모 |
| --- | --- | --- | --- |
| A 신청~확정 | | | |
| A-1 체크인 없이 신청 | | | |
| A-2 만남 장소 없는 축제 | | | |
| A-3 대기 중 취소 | | | |
| A-4 제안 거절·시간 초과 | | | |
| A-5 인원 부족 2차 확인 | | | |
| A-6 도착 예정 시간 | | | |
| A-7 상태방 타임라인 | | | |
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
| N 상대 차단 | | | |
| O 솔로 코스 전환 | | | |
| P 체크인 변경과 풀 정리 | | | |
| Q 온도 노출 | | | |
| R 알림 1단계 (R-1~R-10) | | | |

## 6. 아직 이 문서가 덮지 않는 것

매칭 바깥의 영역입니다. 필요해지면 별도 문서로 다룹니다.

- **관리자 기능** — 신고 검토, 제재(경고·정지·영구차단), 온도 수동 조정, 안전 알림
  (`docs/19` 4.1~4.3, 4.8, 4.9 PR A)
- **신고 확정 이후의 연쇄** — 매너온도 `-2.00`, 누적 3건 관리자 알림, 자동 제재
- **정지 회원 제한** — 조회는 되고 활동만 막히는지(4.8)
- **매너온도 시간 경과 회복** — 30일 주기(4.9 PR B)
- **탈퇴와 재가입**(4.4), **1:1 문의**(4.5), **동의 흐름**(4.7)
- **30도 매칭 제한**(4.9 PR C)과 **후기**(PR D) — 미착수
- **알림 2·3단계** — 서버 저장 알림함과 PWA push. 구현 후 시나리오 R을 늘립니다

## 7. 취향 임베딩 검증 2건

"임베딩이 매칭에 반영되는가"는 **성격이 다른 두 질문**입니다. 합치면 원인을 가릴 수 없습니다.
검증 1이 실패하면 검증 2는 의미가 없으므로 순서를 지킵니다.

### 검증 1. 임베딩이 정상 생성·저장·조회되는가

임베딩 실패는 회원 흐름을 막지 않고 조용히 `FAILED`로만 남습니다. 더 중요한 것은
`PairCompatibilityScorer.scoreDetailed()`가 **한쪽이라도 임베딩이 없으면 경고 없이 Jaccard
점수만 쓰고** `embedding_applied = false`로 기록한다는 점입니다. 그래서 "임베딩이 안 먹는
것 같다"는 증상은 데이터 문제로도 똑같이 나타납니다.

| 단계 | 확인 |
| --- | --- |
| 1 | `GET /api/admin/diagnostics/embedding` → `ok: true`, `dimensions: 1536` |
| 2 | 참가자 전원 `member_preference_embeddings.embedding_status = 'COMPLETED'`, `embedding IS NOT NULL` |
| 3 | 실패면 `embedding_error_reason` 확인 (`docs/02` 취향 임베딩 실패 진단) |
| 4 | 지난 매칭의 `match_attempt_members.embedding_applied` 확인 |

```sql
SELECT e.member_id, m.nickname, e.embedding_status, e.embedding_error_reason,
       (e.embedding IS NOT NULL) AS has_vector,
       split_part(e.preference_text, E'
', 1) AS activity,
       split_part(e.preference_text, E'
', 2) AS companion,
       e.updated_at
FROM member_preference_embeddings e
JOIN members m ON m.id = e.member_id
ORDER BY e.updated_at DESC
LIMIT 10;
```

`member_preference_embeddings`는 회원당 1행이므로 `updated_at DESC`가 곧 "방금 입력한
테스터 순"입니다. member id를 몰라도 됩니다.

판정:

- `embedding_applied = true` → 임베딩은 반영됐다. 결과가 취향과 안 맞으면 원인은 조합 단계다
- `embedding_applied = false` → **이 회원의 임베딩이 원인이다.** 검증 2로 넘어가지 말고 먼저 고친다

### 검증 2. 임베딩이 조합 선택 결과에 반영되는가

**희망 인원을 전원 2명으로 고정하는 것이 전제입니다.** 3인 이상을 허용하면 조합 우선순위가
점수보다 인원 수를 먼저 보므로 세 명이 한 그룹으로 묶이고, 점수를 검증할 수 없습니다.

| | |
| --- | --- |
| 준비 | 참가자 전원 같은 축제 체크인, **여행스타일 태그 전원 동일**, 취향 문장만 서로 다르게, 임베딩 전원 `COMPLETED` |
| 조작 | 전원 희망 인원 **2명**, "2명이어도 괜찮아요" 체크 → `MATCHING_SCHEDULER_FIXED_DELAY` 안에 **3명 이상**이 함께 신청 |
| 기대 | 코사인이 가장 높은 pair가 묶이고 남는 사람은 매칭되지 않는다(→ 솔로 코스 전환) |
| 기대 — DB | `match_attempt_members`의 `jaccard_score`가 전원 동일, `cosine_score`만 다름, `embedding_applied = true` |

태그를 전원 동일하게 두면 모든 pair의 Jaccard가 상수가 되므로, 총점 순위는 **코사인만으로**
결정됩니다. 가중치 값과 무관하게 예상 짝이 정해지므로 `.env`를 건드릴 필요가 없습니다.

매칭을 돌리기 **전에** 예상 짝을 적어두고 대조합니다. 이것이 "잘 되는지"를 검증 가능한
형태로 바꾸는 단계입니다.

```sql
WITH recent AS (
    SELECT e.member_id, e.embedding, m.nickname
    FROM member_preference_embeddings e
    JOIN members m ON m.id = e.member_id
    WHERE e.embedding_status = 'COMPLETED'
    ORDER BY e.updated_at DESC
    LIMIT 5
)
SELECT a.nickname AS m1, b.nickname AS m2,
       round(((1 - (a.embedding <=> b.embedding)) * 100)::numeric, 2) AS cosine
FROM recent a
JOIN recent b ON a.member_id < b.member_id
ORDER BY cosine DESC;
```

코사인 내림차순으로 사람이 겹치지 않게 골라 나가면 그것이 조합기의 예상 출력입니다.

**의도한 짝과 1순위 오답 짝의 차이가 2점 미만이면 취향 문장을 더 갈라서 다시 합니다.**
취향 입력은 가이드 2문항과 자유 입력을 라벨 붙여 한 문자열로 합쳐 임베딩하므로
(`하고 싶은 것:` / `편한 사람:`), 모든 회원 텍스트에 공통 접두어가 들어가 코사인 바닥값이
전반적으로 높게 깔립니다. 그래서 절대값이 아니라 **순서**로 판정합니다.
두 문항이 서로 다른 방향을 가리키면 노이즈가 되므로, 같은 방향을 강화하도록 씁니다.

### 임베딩 점수를 과대 해석하지 않는다

코사인 유사도는 **회원이 입력한 취향 문장의 의미적 유사도**입니다. 실제 사람 사이의 궁합이나
만남 만족도를 검증한 점수가 아닙니다. 궁합의 보조 신호로 쓰는 것은 타당하지만, 실제 만족도와의
관계는 별도 검증이 필요합니다. 가중치(태그 0.70 / 임베딩 0.30)도 실사용 데이터를 확보한 뒤
재조정할 값으로 `PairCompatibilityScorer` 주석에 명시돼 있습니다.

화면 문구와 문서에서 "AI가 궁합을 분석한다"로 표현하지 않습니다.
"입력한 취향의 유사도를 점수에 반영한다"가 사실에 맞습니다.
