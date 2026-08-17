# 체크인 ↔ 매칭 풀 연동 설계

## 0. 이 문서의 성격

`docs/14-2_MATCHING_ENGINE_DESIGN.md`는 "체크인과 매칭 풀 등록을 하나의 API로 묶고, 매칭 엔진은 3장(매칭 풀 등록)까지만 구현된다"는 전제로 작성됐습니다. 그러나 `dev` 브랜치에는 이미 매칭 시도/스코어링/제안·응답/그룹 확정/쿨다운·페널티까지 전체 매칭 엔진이 구현·테스트 완료된 상태이고, 실제 체크인·매칭 풀 연동 방식도 그 문서의 가정과 다릅니다. 그래서 14-2 문서는 폐기하고, **현재 `dev`에 실제로 존재하는 코드를 기준점(as-is)으로 삼아** 이번 GPS 체크인 작업 범위와 설계를 이 문서로 새로 정리합니다.

이 문서는 매칭 엔진 전체를 다시 설계하지 않습니다. 매칭 엔진 구현 기준 문서는 [`docs/13_MATCHING_ENGINE_IMPLEMENTATION.md`](13_MATCHING_ENGINE_IMPLEMENTATION.md)이고, 이 문서는 그 경계에 걸쳐 있는 **체크인 쪽 책임**만 다룹니다.

## 1. 현재 실제 구현 현황 (as-is)

### 1.1 체크인

| 구성요소 | 파일 |
| --- | --- |
| Controller | [`FestivalCheckinController`](../backend/src/main/java/com/survey/meetorsolo/domain/festival/controller/FestivalCheckinController.java) — `POST /api/festivals/{festivalId}/checkin` |
| Service | [`FestivalCheckinService`](../backend/src/main/java/com/survey/meetorsolo/domain/festival/service/FestivalCheckinService.java) |
| Entity | [`FestivalCheckin`](../backend/src/main/java/com/survey/meetorsolo/domain/festival/entity/FestivalCheckin.java) — 원본 위경도는 저장하지 않고 축제 좌표와의 `distanceMeters`만 저장 |
| 설정 | [`FestivalCheckinProperties`](../backend/src/main/java/com/survey/meetorsolo/domain/festival/config/FestivalCheckinProperties.java) — `app.festival.checkin.valid-duration`(기본 6h, `FESTIVAL_CHECKIN_VALID_DURATION`), `accuracy-threshold-meters`(기본 100m) |

`checkIn()`이 하는 일:

1. 축제 존재·`ACTIVE` 상태 확인, 좌표 없는 축제는 `FESTIVAL_LOCATION_UNAVAILABLE`.
2. `accuracyMeters`가 임계값 초과면 `LOW_LOCATION_ACCURACY`.
3. 거리 계산 후 `checkinRadiusMeters` 초과면 `CHECKIN_OUT_OF_RANGE`.
4. **같은 회원의 기존 `ACTIVE` 체크인(다른 축제 포함)을 전부 `CANCELLED`로 정리** — "한 사람은 한 곳에만 있을 수 있다" 원칙. `saveAll` + 명시적 `flush()`로 취소 UPDATE가 새 INSERT보다 먼저 반영되게 함(`uq_festival_checkins_member_festival_active` 부분 unique index 위반 방지).
5. 새 `FestivalCheckin` row 생성(`status=ACTIVE`, `expiresAt = checkedInAt + validDuration`).

4번 단계에 있는 주석이 이번 설계의 출발점입니다:

```java
// TODO(matching engine): domain/matching이 구현되면 이때 해당 회원의 활성
// match_pools row도 함께 취소해야 한다. 아직 매칭 엔진 코드가 없어 이번 범위에서는
// festival_checkins만 정리한다.
```

이 TODO는 매칭 엔진이 없던 시절 쓰였고, 지금은 매칭 엔진이 존재하는데도 그대로 남아 있습니다.

### 1.2 매칭 풀 등록

| 구성요소 | 파일 |
| --- | --- |
| Controller | [`MatchingController`](../backend/src/main/java/com/survey/meetorsolo/domain/matching/controller/MatchingController.java) — `POST /api/matching/pools` |
| Service | [`MatchPoolEntryService`](../backend/src/main/java/com/survey/meetorsolo/domain/matching/service/MatchPoolEntryService.java) |
| 후보 조회 | [`MatchPoolRepository`](../backend/src/main/java/com/survey/meetorsolo/domain/matching/repository/MatchPoolRepository.java) |

`MatchPoolEntryService.enter()`는 `FestivalCheckinService.checkIn()`을 **호출하지 않습니다.** 대신 `MatchPoolRepository.findValidCheckinId(memberId, festivalId, now)`로 이미 존재하는 유효한 체크인만 조회하고, 없으면 `MATCHING_INVALID_REQUEST`("해당 축제의 유효한 체크인이 필요합니다")로 거절합니다. 즉 체크인을 대신 만들어주지 않습니다.

풀 등록 성공 시 `MatchingPoolEnteredEvent`를 발행하고, `MatchingPoolEnteredEventHandler`가 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 받아 `PoolEntryMatchingOrchestrationService`를 실행합니다(즉시 매칭 시도). 핸들러 실패는 로그만 남기고 삼켜서 풀 등록 자체의 성공 여부에는 영향을 주지 않습니다.

## 2. 체크인 ↔ 매칭 풀 관계 계약

**체크인과 매칭 풀 등록은 완전히 분리된 두 개의 API입니다.** 14-2 문서처럼 하나의 엔드포인트로 묶여 있지 않습니다.

```
1) POST /api/festivals/{festivalId}/checkin   → festival_checkins row 확보 (독립 액션)
2) POST /api/matching/pools                    → 1)의 유효한 체크인이 있어야 성공 (독립 액션)
```

프론트에서 "매칭 시작하기" 버튼 하나로 사용자 경험을 묶을 수는 있지만, 그건 프론트가 두 API를 순차 호출하는 방식으로 처리할 문제이고 백엔드 계약은 바뀌지 않습니다. **이번 체크인 작업은 매칭 풀을 만들 책임이 없습니다.**

### 2.1 체크인 유효 기간과 매칭 자격 상한 통일

과거에는 체크인 row 자체의 `expires_at`(`FestivalCheckinProperties.validDuration`, 기본 6시간 설정값)과 매칭 자격의 실제 마감(체크인 후 1시간 하드코딩)이 서로 다른 기준이었습니다. 화면에 "체크인 6시간 유효"라고 뜨는데 실제로는 1시간 뒤부터 매칭 신청이 막히는 모순이 있었습니다.

이번 작업에서 **체크인 row의 `expires_at`도 1시간으로 통일**했습니다. `FestivalCheckinService.checkIn()`은 더 이상 설정 가능한 `properties.validDuration()`을 쓰지 않고, `domain/checkin/CheckinValidityPolicy.VALIDITY`(1시간, 상수)를 사용합니다. `FestivalCheckinProperties`에서 `validDuration` 필드와 `FESTIVAL_CHECKIN_VALID_DURATION` 환경변수를 제거했습니다.

`MatchPoolRepository`의 매칭 자격 쿼리는 여전히 다음처럼 `LEAST(checkin.expires_at, checkin.checked_in_at + INTERVAL '1 hour')`를 사용합니다(네이티브 SQL이라 Java 상수를 직접 참조할 수 없음). 통일 이후에는 `checkin.expires_at`이 이미 `checked_in_at + 1시간`이므로 이 `LEAST`는 사실상 `checkin.expires_at`과 동일하게 동작하는 방어적 상한으로 남습니다.

```sql
LEAST(checkin.expires_at, checkin.checked_in_at + INTERVAL '1 hour') > :now
```

`FestivalCheckinResponse.expiresAt()`과 `GET /api/festivals/checkin/me`의 `CurrentCheckinResponse.expiresAt()`은 이제 매칭 자격 마감과 같은 값을 반환하므로, "매칭 신청 가능 남은 시간"으로 그대로 노출해도 됩니다.

### 2.2 인증 회원 현재 체크인 조회·취소 API

`/matching` 화면이 실제 체크인 상태를 몰라 새로고침·재진입 시 이미 체크인되어 있어도 "체크인하기" 버튼이 다시 뜨는 문제가 있었습니다. 이를 위해 신규 API 2개를 추가했습니다.

| API | 설명 |
| --- | --- |
| `GET /api/festivals/checkin/me` | 인증 회원의 현재 유효한(ACTIVE, 만료 전) 체크인 1건을 `festivalId`, `festivalName`, `checkedInAt`, `expiresAt`로 반환. 없으면 `200 data:null`. |
| `DELETE /api/festivals/checkin/me` | 현재 활성 체크인을 취소(`CANCELLED`)하고 `204`를 반환. 활성 체크인이 없으면 `404`. |

취소는 `checkIn()`이 기존에 하던 "기존 ACTIVE 체크인 취소 + `FestivalCheckinCancelledEvent` 발행" 로직을 `cancelAndPublish()`로 추출해 그대로 재사용한다. 따라서 `/matching`에서 사용자가 직접 취소해도 4장의 이벤트 흐름을 통해 matching 도메인이 남아있는 `WAITING` match_pool을 정리한다.

Frontend는 매칭 신청 전(`IDLE`) 화면에서만 이 취소 버튼을 노출한다. `WAITING` 이상(제안 대기·확정 등) 상태에서는 노출하지 않으며, 그 상태에서의 체크인 취소 처리 정책은 여전히 4.4절 미해결 이슈로 남아 있다.

## 3. 문제 정의 — 다른 축제 재체크인 시 기존 match_pool 미정리

### 3.1 재현 시나리오

```
1. 회원이 축제 A에 체크인 → festival_checkins(A, ACTIVE)
2. 축제 A에서 매칭 풀 등록 → match_pools(festival=A, status=WAITING)
3. 회원이 실제로 축제 B로 이동해 축제 B에 체크인
   → FestivalCheckinService.checkIn()이 축제 A의 festival_checkins만 CANCELLED로 변경
   → 축제 A의 match_pools row는 그대로 WAITING(혹은 스케줄러가 이미 LOCKED/PROPOSED로 전환했을 수도 있음)
4. 회원이 축제 B에서 매칭 풀을 등록하려 하면
   MatchPoolEntryService.enter()의 existsActiveByMemberId(memberId) 체크에 걸려
   MATCHING_CONFLICT("이미 진행 중인 match pool이 있습니다")로 거절됨
```

사용자 입장에서는 이미 축제 A를 떠나 축제 A 체크인도 취소됐는데, 축제 B에서 매칭 신청이 막히는 버그로 보입니다. `MatchPoolEntryService`에는 "다른 축제 활성 풀을 취소하고 새로 등록" 같은 자동 정리 로직이 없고, 그냥 활성 풀이 있으면 통째로 거절만 합니다.

### 3.2 왜 지금까지 안 걸렸는가

프론트가 아직 매칭 화면에 연결되지 않았고(`docs/13_MATCHING_ENGINE_IMPLEMENTATION.md` 17장), PostgreSQL 통합 테스트들은 각 서비스를 격리해서 검증하기 때문에 "체크인 서비스가 만든 side effect를 매칭 쪽이 어떻게 받는가"라는 두 도메인에 걸친 시나리오는 지금까지 커버되지 않았습니다.

## 4. 수정 설계 — 이벤트 기반 취소 전파

### 4.1 방향 원칙

`matching` 도메인은 이미 `festival` 도메인을 참조합니다(`MatchPoolEntryService`가 `FestivalMeetingPointRepository`를 직접 의존). 반대로 `festival` 도메인이 `matching` 도메인을 직접 참조하게 만들면 의존 방향이 꼬입니다. 그래서 기존에 이미 쓰이고 있는 패턴 — **`festival`이 이벤트를 발행하고 `matching`이 구독** — 을 그대로 재사용합니다. `MatchingPoolEnteredEvent` → `MatchingPoolEnteredEventHandler`(`AFTER_COMMIT`) → `PoolEntryMatchingOrchestrationService`와 동일한 모양입니다.

```
FestivalCheckinService.checkIn()
  → 기존 ACTIVE 체크인들을 CANCELLED로 변경 (기존 로직 유지)
  → FestivalCheckinCancelledEvent(memberId, festivalId) 발행 (취소된 축제별로 1건씩, 또는 목록 1건)
  → commit
  → (AFTER_COMMIT) matching 도메인의 핸들러가 구독
      → 해당 memberId·festivalId의 활성 match_pool을 정리
```

### 4.2 신규 이벤트

`festival` 도메인에 `FestivalCheckinCancelledEvent(Long memberId, Long festivalId, OffsetDateTime cancelledAt)`를 신설합니다. 기존 `existingActive.forEach(FestivalCheckin::cancel)` 루프에서 취소되는 건마다 발행합니다.

### 4.3 matching 쪽 핸들러

새 `FestivalCheckinCancelledEventHandler`(`matching` 패키지)가 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 받아 해당 `memberId`+`festivalId`의 활성 `match_pool`을 조회해 정리합니다. 실패는 기존 패턴과 동일하게 로그만 남기고 삼켜서, 매칭 쪽 정리 실패가 체크인 자체를 되돌리지 않게 합니다.

### 4.4 상태별 취소 가능 여부 — 반드시 구현 시점에 결정 필요

`MatchPool` 엔티티는 현재 `PROPOSED` 상태에서만 `cancel(now)`(`CANCELLED`로 전이)를 허용합니다(`transitionFromProposed`). `WAITING`이나 `LOCKED` 상태를 취소하는 메서드가 아직 없습니다.

| 풀 상태 | 취소 난이도 | 비고 |
| --- | --- | --- |
| `WAITING` | 낮음 | 아직 아무 후보에게도 제안되지 않음. 신규 전이 메서드(예: `cancelWhileWaiting(now)`)만 추가하면 됨 |
| `LOCKED` | **위험** | Scheduler/pool-entry claim이 이 row를 선점해 scoring·조합 중일 수 있음. `lock_token` 소유권과 무관하게 상태를 바꾸면 진행 중인 매칭 시도와 경합 가능 |
| `PROPOSED` | **위험** | 이미 다른 회원들에게 제안이 나가 응답 대기 중. 이 시점에 체크인 취소만으로 조용히 풀을 지우면 상대방 회원들의 proposal이 붕 뜨는 문제 발생 |

이번 체크인 작업에서는 **`WAITING` 상태만 자동 취소 대상으로 한정**하는 것을 기본 방침으로 제안합니다. `LOCKED`/`PROPOSED`는 이미 매칭 엔진이 가진 만료·stale lock 정리(`MatchPoolCleanupService`)와 proposal 조기 종료 로직(`docs/18_PROPOSAL_TERMINATION_TIMER_SYNC.md`)에 맡기고, 이번 범위에서는 손대지 않습니다. `LOCKED`/`PROPOSED` 상태에서의 체크인 취소 처리 방침은 11장 미해결 이슈로 남겨 별도 승인 후 진행합니다.

## 5. 이번 체크인 작업 범위

- `festival` 도메인: 기존 `checkIn()`의 취소 루프에 `FestivalCheckinCancelledEvent` 발행 추가.
- `matching` 도메인: 신규 이벤트 구독 핸들러 + `WAITING` 상태 전용 취소 처리(엔티티에 새 전이 메서드 추가 포함).
- 체크인 row `expires_at`을 매칭 자격 상한과 같은 1시간으로 통일(`CheckinValidityPolicy.VALIDITY` 사용, `FestivalCheckinProperties.validDuration`/`FESTIVAL_CHECKIN_VALID_DURATION` 제거).
- `GET /api/festivals/checkin/me`, `DELETE /api/festivals/checkin/me` 신규 추가. `/matching` IDLE 화면이 실제 체크인 상태(축제명·만료 시각)를 보여주고 취소할 수 있게 한다.
- `LOCKED`/`PROPOSED` 상태의 취소 처리는 이번 범위에서 제외(7장 참고). Frontend 취소 버튼도 `IDLE` 상태에서만 노출한다.

## 6. 테스트 우선순위

- 축제 A `WAITING` 풀이 있는 상태에서 축제 B 재체크인 → 축제 A 풀이 `CANCELLED`(또는 정의할 종료 상태)로 정리되는지 — **실제 PostgreSQL 통합 테스트 필수**(체크인 트랜잭션 커밋 이후 `AFTER_COMMIT` 이벤트 타이밍이라 mock으로는 검증이 약함).
- 같은 축제로 재체크인(기존 3.2절 케이스, 이번 이벤트와 무관) 시 기존 동작이 깨지지 않는지 회귀 확인.
- 축제 A 풀이 `LOCKED`/`PROPOSED`인 상태에서 축제 B 재체크인 시 **아무것도 자동으로 취소되지 않는지**(이번 범위 제외를 검증하는 네거티브 테스트).
- 이벤트 핸들러 실패 시 체크인 자체는 정상 커밋 상태로 유지되는지.

## 7. 미해결 이슈

- `LOCKED`/`PROPOSED` 상태의 match_pool을 재체크인 또는 회원이 직접 누른 체크인 취소 시점에 어떻게 처리할지(즉시 취소 vs 기존 만료·stale 정리에 위임 vs 별도 회원 통지) — 구현 시 별도 승인 필요. Frontend는 우선 `IDLE`(매칭 신청 전)에서만 취소 버튼을 노출해 이 문제를 피해간다.
- `FestivalCheckinCancelledEvent`를 축제별로 여러 건 발행할지, 회원 단위로 묶어 한 번만 발행할지는 구현 시점에 확정.
