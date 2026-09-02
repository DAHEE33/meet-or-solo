# 매칭 실패 → 솔로 코스 전환 설계 (`MATCH-09`)

이 문서는 기획서 `MATCH-09`(매칭 실패 시 솔로 코스 전환과 재매칭 타이밍 연결)의 남은 범위를
산정하고, 실제로 구현할 범위와 그렇게 정한 근거를 남기는 문서입니다.

## 1. 배경

`[10-A 후속 3·4·5]`로 다른 담당자가 아래를 이미 `dev`에 넣었습니다.

- `GET /api/festivals/{id}/solo-course?type=HALF|FULL` — 최근접 이웃 기반 코스 생성
  (`docs/23_SOLO_COURSE_ITINERARY_DESIGN.md`)
- `SoloCoursePage` — 체크인 축제 기준 타임라인 UI
- 매칭 실패 화면(`CancelledCard`)의 솔로 코스 링크

그래서 기획서 `MATCH-09`를 그대로 새 작업으로 잡으면 중복 구현이 됩니다. 착수 전에 남은 범위를
다시 산정했고, 그 결과가 이 문서입니다.

## 2. 요구사항 대조

기획서 v5.0 원문은 저장소에 없어, 저장소 문서에 남은 파생 요구사항을 기준으로 대조했습니다.

| # | 요구 | 근거 | 현재 `dev` | 판정 |
| --- | --- | --- | --- | --- |
| R1 | 매칭 실패 시 솔로 코스 추천으로 전환 | `docs/00_PROJECT_OVERVIEW.md` 13 | 링크는 있으나 화면에 도달하지 못함(3장) | 사실상 미완 |
| R2 | 관광공사 데이터 기반 코스 생성 | `docs/00_PROJECT_OVERVIEW.md` 17 | `SoloCourseService` | 완료 |
| R3 | 솔로 코스 화면 | `docs/03_FRONTEND_GUIDE.md` | `SoloCoursePage` 타임라인 | 완료 |
| R4 | "솔로 45분 코스" | `docs/11_DATABASE_DESIGN.md` | 실제는 `HALF` 240분 / `FULL` 480분 | 문서 정정 |
| R5 | `solo_courses` 전환 이력 저장 | `docs/11_DATABASE_DESIGN.md` | 코드 0건, dev DB 0건 | 미구현 (4장에서 제외 결정) |
| R6 | `recommendation_click_logs` KPI | `docs/11_DATABASE_DESIGN.md` | 코드 0건 | 미구현 (4장에서 제외 결정) |
| R7 | 재매칭 가능 시점 안내 | `docs/05_MATCHING_POLICY.md` | cooldown 카운트다운·버튼 비활성화 구현됨 | 완료 |
| R8 | 솔로 코스 유도 vs 재매칭 대기 판단 | — | 없음 | 5장에서 확정 |

## 3. 실제 공백 — 종료 카드가 화면에 뜨지 않는다

`[10-A 후속 5]`가 넣은 링크는 **매칭 실패의 대표 상황에서 화면에 뜨지 않습니다.**

`useMatchingSession.deriveMatchingState()`의 상태 파생 순서가 원인입니다.

```text
1. cooldown이 active면  → CANCELLED / EXPIRED / COOLDOWN
2. 아니면 terminal pool → IDLE (신청 화면)
```

링크가 붙어 있는 `CancelledCard`는 `CANCELLED`/`EXPIRED`/`COOLDOWN`에서만 렌더되는데, 위 순서상
그 세 상태는 **cooldown이 있을 때만** 만들어집니다.

그런데 60초 탐색 만료는 `MatchPoolRepository.expireWaitingPools()`가 `status = 'EXPIRED'`로만
바꾸고 cooldown을 만들지 않습니다(`docs/05_MATCHING_POLICY.md` "검색 만료 시각은 연장하지 않고
penalty/cooldown은 별도 정책 확정 전까지 생성하지 않는다").

결과적으로 링크가 실제로 노출되는 경우는 `TIMEOUT`(2분) / `REJECT`(30초) /
`POOL_CANCEL`(20초) cooldown이 걸린 경우뿐입니다. **"60초 안에 상대를 못 찾음"이라는 가장 흔한
실패에서는 사용자가 종료 카드를 한 번도 보지 못하고 신청 화면으로 되돌아갑니다.**

`MatchingConditionPage.test.ts`의 기존 링크 테스트는 `MatchBody({ status: 'EXPIRED' })`를 직접
호출해 `deriveMatchingState`를 우회하므로 이 구멍을 잡지 못했습니다.

### 3.1 왜 단순 롤백이 아닌가

cooldown 없는 terminal pool을 `IDLE`로 접는 동작은 `[10-A 후속 2]`가 "종료 화면 고착" 버그를
고치면서 **의도적으로** 만든 자리입니다. 되돌리면 `/matching`을 벗어났다 다시 들어올 때마다
"매칭이 종료됐어요" 카드가 다시 뜨는 문제가 재발합니다.

따라서 필요한 것은 롤백이 아니라, `[10-A 후속 2]`가 원래 서술했던 다음 구분을 실제로 구현하는
것입니다.

- **세션 중 실시간으로 종료를 관측한 pool** → 종료 카드를 한 번 보여준다
- **새 mount에서 발견한 과거 terminal pool** → 곧바로 신청 화면으로 돌려보낸다

## 4. 전환 이력(`solo_courses`)을 저장하지 않기로 한 결정

`solo_courses`는 V4에 이미 있고 `source_attempt_id` FK까지 잡혀 있는데 backend가 아무것도 쓰지
않습니다(dev DB 0건). 살릴지 여부를 판단했고, **이번 범위에서는 저장하지 않기로 결정했습니다.**

근거:

- **읽어가는 기능이 없습니다.** 저장된 코스 목록, 이어보기, 관리자 조회 중 어느 것도 없습니다.
  사용자에게 솔로 코스는 버튼을 눌러 보고 끝나는 화면입니다. 저장은 순수하게 전환율 통계 용도인데,
  MVP 단계에서 그 숫자를 볼 화면도 볼 사람도 없습니다.
- **나중에 넣어도 비용이 거의 같습니다.** 테이블과 FK가 그대로 남아 있어 새 migration이 필요 없고,
  Entity와 저장 시점만 추가하면 됩니다.
- 코사인 스케일 조정을 "데이터가 더 쌓인 뒤"로 미룬 것과 같은 판단입니다.

감수하는 비용:

- **전환 이력은 소급 생성이 불가능합니다.** 나중에 전환율을 보기로 하면 그 시점부터의 데이터만
  쌓이고, 그 전 기간은 영영 볼 수 없습니다.

`recommendation_click_logs`도 같이 제외합니다. 이 테이블의 CHECK 제약이
`tour_place_id IS NOT NULL OR solo_course_id IS NOT NULL`이라 `solo_courses` row 없이는 "실패
화면에서 코스로 넘어갔다"를 기록할 수단이 없어, 두 테이블은 함께 살거나 함께 빠집니다.

`docs/11_DATABASE_DESIGN.md`의 세 테이블 MVP 표기를 "필수"에서 "V4 선반영, MVP 범위에서 미사용"으로
정정했습니다.

### 4.1 다시 도입한다면

- 저장 시점은 `GET /solo-course` 조회가 아니라 **"실패 화면에서 코스로 넘어간 순간"**이어야 합니다.
  조회에 쓰기 부작용을 붙이면 홈에서 구경만 해도 전환으로 집계되어 지표가 오염되고,
  현재 조회 API는 인증이 없는데 `solo_courses.member_id`는 `NOT NULL`이라 애초에 성립하지 않습니다.
- `source_attempt_id`는 클라이언트가 보낸 값을 믿지 말고 서버에서
  `MatchAttemptMemberRepository.findFirstByPoolIdOrderByIdDesc(poolId)`로 역추적합니다.
  탐색 만료처럼 attempt가 만들어지지 않은 실패는 `NULL`입니다(컬럼이 nullable인 이유).
- 매칭 트랜잭션과 분리된 별도 트랜잭션이어야 합니다. 코스 계산을 매칭 종료 트랜잭션 안에 넣으면
  `MemberPreferenceEmbeddingService`가 `@Transactional` 안에서 OpenAI를 호출해 커넥션을 점유하는
  것과 같은 문제가 됩니다.

## 5. 재매칭 타이밍 안내 (R7·R8)

### 5.1 이미 되어 있는 것

`CancelledCard`가 `restriction.cooldown.remainingSeconds` 기반 카운트다운(`N:NN 후 재신청 가능`)을
표시하고 재신청 버튼을 비활성화하며, cooldown이 만료되면 자동으로 `refresh()`합니다.
`serverNow` 기준 offset 보정(`utils/serverClock.ts`)도 들어가 있습니다. 별도 구현이 필요 없습니다.

### 5.2 확정 정책 — 버튼 2개, 선택은 사용자에게

"솔로 코스로 유도할지 재매칭을 기다리게 할지"를 서버나 화면이 판단하지 않습니다.
**종료 카드에 `다시 신청하기`와 `솔로 코스 추천 보기`를 동등한 두 버튼으로 나란히 두고 사용자가
고르게 합니다.**

임계값 기반 유도(잔여 cooldown이 N초 이상이면 솔로 코스를 강조하는 식)는 검토했으나 채택하지
않았습니다. 기존 cooldown 동작이 이미 같은 일을 하기 때문입니다.

| 종료 사유 | cooldown | 화면에서 실제로 벌어지는 일 |
| --- | --- | --- |
| 탐색 만료(비귀책) | 없음 | 두 버튼 모두 활성 → 사용자가 고른다 |
| 상대 `TIMEOUT` | 2분 | 재신청은 비활성 + 카운트다운, 솔로 코스만 활성 |
| 본인 `REJECT` | 30초 | 30초 뒤 재신청이 다시 활성화된다 |
| 자발 취소 `POOL_CANCEL` | 20초 | 20초 뒤 재신청이 다시 활성화된다 |

즉 기다릴 이유가 있을 때는 cooldown이 알아서 재신청을 막고, 기다릴 이유가 없으면 둘 다 열립니다.
새 규칙을 추가하지 않고도 필요한 유도가 됩니다.

## 6. 구현 범위

Backend 변경 없음, Flyway migration 없음. Frontend만 수정합니다.

1. `deriveMatchingState()`에 "이 세션에서 진행 상태를 실제로 관측한 pool id"를 입력으로 추가하고,
   그 pool이 종료된 경우에만 cooldown 없이도 종료 상태를 반환한다.
   새 mount에서 발견한 과거 terminal pool은 기존대로 `IDLE`로 돌려보낸다(3.1절).
2. 관측 기록은 순수 함수 `observedActivePoolId(snapshot, current)`로 계산하고 hook의 ref에 보관한다.
   `WAITING`/`LOCKED`/`PROPOSED`는 그대로 관측으로 기록하고, `MATCHED`는 group이 실제로 있을 때만
   기록한다(그렇지 않으면 새 mount에서 이미 취소된 group을 관측으로 오인한다).
3. `CancelledCard`를 두 버튼 구조로 바꾼다(5.2절). `festivalId`가 없으면 솔로 코스 버튼은 숨긴다.
4. `deriveMatchingState`를 실제로 통과하는 회귀 테스트를 추가한다 — 기존 테스트가 `MatchBody`를
   직접 호출해 이 구멍을 놓쳤기 때문이다(3장).

## 7. 이번 범위에서 제외

- 전환 이력 저장(`solo_courses`, `solo_course_places`, `recommendation_click_logs`) — 4장
- 코스 알고리즘 파라미터 조정(`MAX_HOP_METERS`, `MAX_STOPS`, 체류시간 추정치) — `docs/23` 담당자 범위
- `[10-A 후속 3·4]`의 코스 품질 수동 검증 — 만든 담당자가 판단할 영역

## 8. 테스트 우선순위

- `deriveMatchingState`: 관측 기록이 있는 탐색 만료 pool은 `EXPIRED`, 없으면 `IDLE`
  (후자가 `[10-A 후속 2]` 회귀 방지)
- `observedActivePoolId`: `WAITING`/`LOCKED`/`PROPOSED` 기록, `MATCHED`는 group이 있을 때만 기록,
  다른 pool로 바뀌면 갱신, pool이 사라져도 기존 기록 유지
- `MatchBody`: 종료 카드에 두 버튼이 모두 있고, cooldown 중에도 솔로 코스 버튼은 활성이며,
  `festivalId`가 없으면 솔로 코스 버튼을 숨긴다
