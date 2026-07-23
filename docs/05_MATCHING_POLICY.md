# 매칭 정책

## 목적

매칭 시스템은 같은 강원도 축제에 GPS 체크인한 혼행 사용자를 2~4인 소그룹으로 연결합니다. 빠르고, 제한적이며, 감사 가능하고, 안전해야 합니다.

이 기능은 자유 채팅이나 개방형 친구 찾기가 아닙니다.

## 매칭풀 진입 조건

사용자는 아래 조건을 모두 만족해야 매칭풀에 들어갈 수 있습니다.

- 로그인 상태이다.
- 필수 프로필 설정을 완료했다.
- 선택한 축제에 GPS 체크인했다.
- 축제 허용 반경 안에 있다.
- 이미 매칭 중이거나 매칭 확정 상태가 아니다.
- penalty/cooldown 상태가 아니다.
- 해당 축제가 매칭 가능한 상태이다.

원본 GPS 좌표는 검증에만 사용하고 기본적으로 즉시 폐기합니다.

## 사용자 매칭 조건

사용자가 선택하는 조건:

- 희망 인원: `2`, `3`, `4`
- 매칭 태그 또는 선호 조건
- 3명/4명 희망 시 2명으로도 진행할지 여부

3명 또는 4명을 선택한 사용자는 다음 값을 갖습니다.

```text
allow_minimum_two = true | false
```

이 값은 인원 미달 흐름에서 사용합니다.

## 시간 정책

매칭 탐색 시간:

```text
60 seconds
```

매칭 제안 응답 시간:

```text
30 seconds
```

30초 안에 응답하지 않으면 자동 거절로 처리합니다.

## 후보 제외 규칙

아래 사용자는 후보에서 제외합니다.

- 같은 축제에 체크인하지 않은 사용자
- 이미 매칭 중이거나 매칭 확정된 사용자
- 내가 차단한 사용자
- 나를 차단한 사용자
- penalty/cooldown 중인 사용자
- 직전 거절/미응답 정책상 즉시 재시도 대상이 아닌 사용자
- 매칭풀 entry가 만료된 사용자

차단 관계는 양방향으로 검사합니다.

## 후보 선정 기준

초기 MVP scoring은 다음을 고려할 수 있습니다.

- 같은 축제 여부
- 희망 인원 호환성
- `member_travel_styles`와 매칭 태그 같은 정형 코드 교집합
- 회원 레벨 `preference_text` 임베딩 cosine similarity
- 희망 시간 유사성
- 체크인 시각 근접성
- 매너온도
- 관광공사 데이터 기반 축제/동선 태그

첫 구현에서는 정교한 scoring보다 상태 정확성과 중복 방지가 중요합니다.

자연어 임베딩은 정형 여행 스타일을 대체하지 않는 보조 점수입니다. `preference_text`가 없거나 임베딩 생성에 실패하면 정형 태그 점수만으로 매칭을 계속합니다. 임베딩 API는 취향 문장의 최초 입력 또는 실제 수정 시 호출하고, Scheduler 실행 때마다 다시 호출하지 않습니다.

## 매칭 흐름

```text
1. 사용자가 매칭풀에 진입한다.
2. match_pool row가 WAITING 상태로 생성된다.
3. Scheduler가 eligible pool entry를 조회한다.
4. 후보 row를 transaction lock으로 선점한다.
5. match_attempt를 생성한다.
6. 후보 전원에게 match_proposal을 생성한다.
7. 각 후보에게 MATCH_PROPOSED를 발행한다.
8. 사용자는 30초 안에 수락/거절한다.
9. 미응답 사용자는 자동 거절된다.
10. 목표 인원이 모두 수락하면 매칭을 확정한다.
11. 목표 인원에는 미달하지만 2명 이상 수락했고 수락자가 모두 2명 진행을 허용하면 같은 attempt 안에서 인원 미달 재확인 proposal을 새로 생성한다.
12. 현재 인원으로 시작을 선택하면 매칭을 확정한다.
13. 조건을 만족하지 못하면 기존 attempt를 실패 처리하고 재대기, cooldown, penalty를 적용한다.
14. 새로운 상대를 다시 탐색하는 완전한 재매칭에서는 새 attempt를 생성한다.
```

## 상태값 후보

매칭풀 상태:

```text
WAITING
LOCKED
PROPOSED
MATCHED
EXPIRED
CANCELLED
COOLDOWN
```

매칭 시도 상태:

```text
CREATED
PROPOSING
WAITING_RESPONSES
INSUFFICIENT_MEMBERS
CONFIRMED
FAILED
CANCELLED
EXPIRED
```

매칭 제안 상태:

```text
SENT
ACCEPTED
REJECTED
TIMEOUT
EXPIRED
```

매칭 제안 유형:

```text
INITIAL_MATCH
INSUFFICIENT_MEMBERS_CONFIRMATION
```

매칭 그룹 상태:

```text
CONFIRMED
IN_PROGRESS
COMPLETED
CANCELLED
```

## 인원 미달 정책

목표 인원이 3명 또는 4명인데 목표보다 적게 수락한 경우:

- 최소 2명 이상 수락해야 한다.
- 수락한 사용자 모두 `allow_minimum_two = true`여야 한다.
- 서버는 `MATCH_INSUFFICIENT_MEMBERS`를 발행한다.
- frontend는 `InsufficientMembersModal`을 표시한다.
- 사용자는 "현재 인원으로 시작" 또는 "취소"를 선택한다.
- 필요한 사용자가 현재 인원 시작을 승인하면 match group을 생성한다.
- 그렇지 않으면 attempt를 실패 처리한다.

인원 미달 팝업에서 몇 명의 추가 동의가 필요한지는 구현 시 확정합니다. 기본 원칙은 수락자 전원 동의입니다.

구현 정책은 다음과 같이 확정합니다.

- 목표 인원이 3명 또는 4명인 최초 제안만 인원 미달 재확인 대상이다.
- 최초 제안의 모든 회원이 `ACCEPTED`, `REJECTED`, `TIMEOUT` 중 하나가 된 뒤 수락자 집합을 확정한다.
- 수락자가 2명 이상이고 목표 인원보다 적으며 수락자 전원의 `allow_minimum_two`가 `true`일 때만 round 2를 생성한다.
- round 2는 같은 `attempt_id`, 새로운 `proposal_id`, `proposal_round=2`, `INSUFFICIENT_MEMBERS_CONFIRMATION`을 사용한다.
- round 2 timeout은 최초 제안과 같은 30초를 사용하고 `match_attempts.expires_at`을 round 2 만료 시각으로 갱신한다.
- `responded_at < expires_at`만 유효하며 같은 시각은 timeout이다.
- 전원이 `START_WITH_CURRENT_MEMBERS`를 선택하면 실제 수락 인원으로 group을 확정한다.
- 한 명이라도 `CANCEL_CURRENT_MEMBERS`를 선택하거나 timeout이면 attempt를 `FAILED`로 종료한다.
- round 2 취소·timeout 회원의 pool은 `CANCELLED`, 비귀책 회원의 pool은 검색 시간이 남으면 `WAITING`, 만료됐으면 `EXPIRED`로 전환한다.
- 최초 제안의 거절·timeout 회원 pool도 `CANCELLED`로 전환하며 수락자 pool은 round 2 동안 `PROPOSED`를 유지한다.
- 검색 만료 시각은 연장하지 않고 penalty/cooldown은 별도 정책 확정 전까지 생성하지 않는다.

인원 미달 재확인은 최초 제안과 다른 질문이므로 새로운 `proposal_id`를 사용합니다. 다만 기존 후보 구성의 후속 단계이므로 `attempt_id`는 유지합니다.

```text
동일 후보의 인원 미달 재확인
-> 같은 attempt_id
-> 새로운 proposal_id와 다음 proposal_round

기존 attempt 종료 후 새로운 상대 탐색
-> 새로운 attempt_id
-> 새로운 proposal_id
```

`match_proposals`는 `(attempt_id, member_id, proposal_round)` 단위로 유일하게 저장합니다. `match_responses`는 각 질문에 한 번만 답하도록 `(proposal_id, member_id)` 유일성을 유지합니다.

## penalty/cooldown

penalty 또는 cooldown 적용 대상:

- 반복 거절
- timeout/미응답
- 매칭 확정 후 취소
- No-show
- 관리자에 의해 유효하다고 판단된 신고

MVP는 단순 cooldown window로 시작하고, 이후 매너온도 scoring으로 확장할 수 있습니다.

최초 구현 정책은 다음과 같이 확정합니다.

| 원인 | cooldown 시작 | 기간 | penalty score |
| --- | --- | ---: | ---: |
| round 1 `REJECTED` | round 1 전체 응답이 terminal이 되어 최종 집계되는 시각 | 30초 | 없음 |
| round 1 `TIMEOUT` | timeout 처리 시각 | 2분 | `+1` |
| round 2 `CANCEL_CURRENT_MEMBERS` | 취소 처리 시각 | 2분 | `+1` |
| round 2 `TIMEOUT` | timeout 처리 시각 | 5분 | `+2` |

- 첫 1회 면제는 두지 않는다.
- 반복 window, 가중치, score decay는 운영 데이터 확인 후 별도 정책으로 이월한다.
- 귀책 pool은 `CANCELLED`로 유지하고, 회원의 재신청 제한은 `match_cooldowns`로 분리한다.
- 비귀책 회원에게는 cooldown과 penalty score를 적용하지 않는다.
- cooldown은 `starts_at <= now AND expires_at > now`일 때 active로 판단한다.
- 신규 cooldown 생성 전 같은 회원의 `expires_at <= now`인 `ACTIVE` row를 `EXPIRED`로 lazy 전환한다.
- 각 귀책 proposal의 `proposal_id`를 cooldown과 penalty event의 멱등성 원인 key로 사용한다.
- response, cooldown, penalty event, `members.penalty_score`, pool, attempt 변경은 같은 transaction에서 처리한다.
- 외부 API와 WebSocket 호출은 이 transaction에 포함하지 않는다.

## 최초 proposal 응답 처리 정책

`INITIAL_MATCH`, `proposal_round=1` 응답은 동일 attempt의 `match_attempts` row를 먼저 잠가 직렬화합니다. 잠금 순서는 attempt, proposal, attempt member 순서로 고정합니다.

- 사용자 응답은 `responded_at < expires_at`일 때만 허용하며 같은 시각이면 timeout이다.
- 동일한 수락 또는 거절 반복은 기존 성공 결과를 반환하고, 최초 응답 이후 다른 응답으로 변경하지 않는다.
- 거절 또는 timeout이 한 건이라도 발생하면 attempt를 즉시 `FAILED`로 종료한다.
- 귀책 회원의 pool은 `CANCELLED`, 비귀책 회원의 pool은 기존 검색 시간이 유효하면 `WAITING`, 만료됐으면 `EXPIRED`로 전환한다.
- 비귀책 pool의 `search_expires_at`은 연장하지 않고 임시 lock 정보는 제거한다.
- 전원이 수락하면 마지막 응답 transaction에서 group, group member, pool `MATCHED`, attempt `CONFIRMED`를 원자적으로 생성·전환한다.
- timeout Scheduler는 기존 matching Scheduler의 활성화 조건, fixed delay, batch size를 재사용하되 attempt별 독립 transaction으로 처리한다.
- 거절·timeout cooldown과 penalty는 위 확정 정책과 proposal 기반 멱등성 계약에 따라 생성한다.
- 인원 미달 round 2와 `allow_minimum_two`는 후속 단계로 분리한다.

## PostgreSQL 기반 상태 관리

Redis는 MVP 1단계에 필요하지 않습니다.

PostgreSQL 관리 대상:

- pool entry
- attempt
- proposal
- response
- group
- event
- 만료 시각

Spring Scheduler 처리 대상:

- 60초 탐색 만료
- 30초 제안 timeout
- 실패 attempt 정리

## 동시성 처리

PostgreSQL transaction lock을 사용합니다.

후보 선점 예시:

```sql
SELECT *
FROM match_pools
WHERE festival_id = :festivalId
  AND status = 'WAITING'
  AND search_expires_at > now()
FOR UPDATE SKIP LOCKED;
```

이 잠금은 DB 전체나 `match_pools` 테이블 전체가 아니라 대상 후보 row에만 적용합니다. 짧은 claim transaction에서 후보가 여전히 `WAITING`인지 확인하고 `LOCKED`로 전환한 뒤 잠금을 해제합니다. 후보 조회, 점수 계산과 그룹 조합은 row lock transaction 밖에서 수행합니다. 그룹별 최종 `REQUIRES_NEW` transaction에서는 pool을 다시 잠그고 `LOCKED` 상태와 `lock_token`, 안전 조건을 재검증한 뒤 attempt/proposal 생성과 `PROPOSED` 전환을 원자적으로 처리합니다.

`lock_token`, `locked_at`은 비관적 transaction lock을 대체하지 않습니다. 선점 실행 추적과 stale lock 복구에 사용하는 보조 정보입니다.

## 매칭풀 정리 정책

정리 작업은 호출자가 전달한 `now`, `staleBefore`를 기준으로 하나의 짧은 transaction에서 수행합니다.

- `WAITING`이고 `search_expires_at <= now`이면 `EXPIRED`로 전환한다.
- `LOCKED`이고 `locked_at <= staleBefore`이면서 아직 검색 시간이 유효하면 `WAITING`으로 복구한다.
- stale `LOCKED`이면서 `search_expires_at <= now`이면 `EXPIRED`로 전환한다.
- stale lock을 회수할 때 `locked_at`, `lock_token`을 모두 `NULL`로 정리한다.
- `LOCKED`이지만 `locked_at` 또는 `lock_token`이 `NULL`인 비정상 row는 자동 복구하지 않는다.
- 상태 조건을 포함한 update로 반복 실행 시 추가 변경이 없는 멱등성을 보장한다.

현재 기본값은 stale timeout 30초, `@Scheduled` fixed delay 5초입니다. 운영 환경에서는 `MATCHING_STALE_TIMEOUT`, `MATCHING_SCHEDULER_FIXED_DELAY` 환경변수로 조정할 수 있습니다.

## 정형 여행 스타일 점수

첫 버전은 `member_travel_styles`의 `TravelStyleCode` 집합에 Jaccard 점수를 적용합니다.

```text
교집합 코드 수 / 합집합 코드 수 * 100
```

- 점수 범위는 `0.00`~`100.00`이다.
- 코드 순서와 중복은 점수에 영향을 주지 않는다.
- 한쪽 또는 양쪽 입력이 비어 있으면 `0.00`이다.
- `BigDecimal`을 사용하고 소수점 둘째 자리에서 `HALF_UP`으로 반올림한다.
- 외부 API와 embedding 없이 계산한다.

## 최초 그룹 조합 정책

- 같은 축제와 같은 `preferred_group_size`를 선택한 후보끼리만 그룹을 구성한다.
- 그룹의 실제 인원은 후보들이 선택한 `preferred_group_size`와 정확히 같아야 한다.
- `allow_minimum_two`는 최초 그룹 조합에 사용하지 않고 인원 미달 재확인 단계에서만 사용한다.
- 그룹 점수는 그룹 내부 모든 2인 pair의 정형 여행 스타일 점수 평균이다.
- 모든 호환 조합을 계산한 뒤 그룹 점수 내림차순, 오래된 `entered_at`, 작은 `pool_id` 순으로 정렬한다.
- 정렬된 조합부터 동일 회원과 pool의 중복 배정을 막으며 결정적 greedy 방식으로 선택한다.
- 동일 입력은 입력 collection 순서와 관계없이 같은 그룹 결과를 생성해야 한다.

후보 수 증가에 따른 전체 조합 생성 비용과 후보 batch 상한은 실제 부하를 확인한 뒤 보완합니다.

## Scheduler와 최초 proposal 생성 정책

- Scheduler는 기본 비활성화하며 `MATCHING_SCHEDULER_ENABLED=true`를 명시한 환경에서만 실행한다.
- 기본 실행 간격은 5초, stale timeout은 30초, proposal timeout은 30초, 단일 tick batch 상한은 20이다.
- 한 tick은 주입된 `Clock`에서 기준 시각을 한 번만 읽고 UUID 기반 실행 token 하나를 사용한다.
- cleanup과 Scheduler batch claim은 각각 독립된 짧은 transaction이다.
- claim은 유효한 `WAITING` pool만 `FOR UPDATE SKIP LOCKED`로 제한 선점하며 전역 requester를 만들지 않는다.
- 여행 스타일과 차단 관계는 batch 조회하고 scoring과 그룹 조합은 row lock transaction 밖에서 수행한다.
- 그룹 생성 직전 pool ID 오름차순으로 row를 잠그고 상태, token, pool/check-in 만료, cooldown과 모든 pair의 양방향 차단 관계를 재검증한다.
- 그룹별 transaction에서 attempt, attempt member, 최초 proposal과 `LOCKED -> PROPOSED` 전이를 원자적으로 처리한다.
- 최초 attempt는 `WAITING_RESPONSES`, 최초 proposal은 `INITIAL_MATCH`, round 1, `SENT`이다.
- attempt와 proposal의 만료 시각은 같은 `now + proposalTimeout`이며, 성공한 pool의 `locked_at`, `lock_token`은 제거한다.
- `member_score`는 해당 회원과 나머지 구성원 사이 pair 점수 평균을 소수점 둘째 자리 `HALF_UP`으로 저장한다.
- 그룹 미사용 또는 생성 실패로 남은 동일 token의 `LOCKED` pool은 즉시 release한다. 유효하면 `WAITING`, 만료됐으면 `EXPIRED`이며 다른 token은 변경하지 않는다.
- Scheduler 전체를 감싸는 transaction, JVM 전역 lock, 장시간 DB advisory lock은 사용하지 않는다.

동시 실행 및 재실행 안전성은 PostgreSQL row lock, `SKIP LOCKED`, pool 상태, `lock_token`과 그룹별 단일 생성 transaction을 기준으로 한다. 정상적인 중복 tick과 다중 인스턴스 실행에서는 같은 pool의 attempt/proposal 중복 생성을 막는다. 커밋 성공 여부가 불명확한 장애 후 기존 attempt를 명시적 key로 찾아 반환하는 기능은 제공하지 않는다. 명시적 idempotency key와 V12 migration은 완전 재매칭 정책과 함께 다음 단계로 이월한다.

동시 안전 상태 변경의 한계:

- proposal 생성 직전에 check-in, cooldown, 그룹 내부 모든 pair의 차단 관계를 다시 검증한다.
- 생성 transaction은 대상 pool row를 잠그지만 block/cooldown 테이블 전체를 직렬화하지는 않는다.
- 최종 검증 직후 다른 transaction에서 block 또는 cooldown이 생성되는 극단적인 race를 강하게 직렬화하는 정책은 후속 보안·동시성 설계로 이월한다.
- 후속 설계에서는 isolation level 강화, PostgreSQL advisory lock, 회원 단위 직렬화와 schema 변경의 처리량·교착·운영 복잡도 tradeoff를 함께 검토한다.

추가 안전장치:

- 사용자별 active pool unique constraint
- proposal response unique constraint
- attempt/member 상태 전이를 하나의 transaction에서 처리
- `match_events`에 append-only audit 기록

## 주요 DB 테이블 후보

```text
festival_checkins
user_blocks
match_pools
match_attempts
match_attempt_members
match_proposals
match_responses
match_groups
match_group_members
match_events
match_penalties
match_cooldowns
member_preference_embeddings
```

## MatchRoomPage 상태방 구조

매칭 확정 후 사용자는 `MatchRoomPage`로 이동합니다.

포함 요소:

- 매칭 확정 안내 카드
- 참여자 상태 목록
- 만남 포인트 지도
- Kakao Maps 핀
- 도착 시간 선택
- 도착했어요 버튼
- 취소 버튼
- 시스템 이벤트 타임라인
- 상대 도착/취소 알림
- 안전 리마인드
- 신고 버튼
- 긴급 도움 버튼

자유 텍스트 채팅은 구현하지 않습니다.

## WebSocket STOMP 이벤트

| Event | 발생 시점 | 서버 처리 | Frontend UI | DB 상태 | Topic/Queue 예시 |
| --- | --- | --- | --- | --- | --- |
| `MATCH_PROPOSED` | 후보 그룹 생성 | proposal 생성 및 30초 만료 설정 | `MatchProposalModal` | `SENT` | `/queue/users/{userId}/match` |
| `MATCH_ACCEPTED` | 사용자 수락 | response 저장 및 attempt 재계산 | `MatchResponseWaitingModal` | `ACCEPTED` | `/topic/match-attempts/{attemptId}` |
| `MATCH_REJECTED` | 사용자 거절 | rejection 저장 및 필요 시 cooldown 적용 | 대기 또는 실패 UI | `REJECTED` | `/topic/match-attempts/{attemptId}` |
| `MATCH_TIMEOUT` | 30초 응답 만료 | 자동 거절 처리 | `MatchingFailedPage` 또는 재시도 상태 | `TIMEOUT` | `/queue/users/{userId}/match` |
| `MATCH_INSUFFICIENT_MEMBERS` | 목표 미달이나 최소 인원 수락 | 현재 인원 진행 여부 요청 | `InsufficientMembersModal` | `INSUFFICIENT_MEMBERS` | `/queue/users/{userId}/match` |
| `MATCH_CONFIRMED` | 목표 충족 또는 최소 인원 진행 확정 | group/member 생성 | `MatchRoomPage` | `CONFIRMED` | `/topic/match-groups/{groupId}` |
| `ARRIVAL_TIME_SELECTED` | 도착 시간 선택 | event 저장 및 member state 갱신 | Timeline update | `ARRIVAL_TIME_SELECTED` | `/topic/match-groups/{groupId}` |
| `MEMBER_ARRIVED` | 도착했어요 클릭 | arrived 상태 저장 | `MemberArrivedModal` | `ARRIVED` | `/topic/match-groups/{groupId}` |
| `MEMBER_CANCELLED` | 사용자가 취소 | cancellation 저장 및 정책 적용 | `MemberCancelledModal` | `MEMBER_CANCELLED` | `/topic/match-groups/{groupId}` |
| `MATCH_CANCELLED` | 그룹 유지 불가 | group 취소 | `MatchingFailedPage` | `CANCELLED` | `/topic/match-groups/{groupId}` |
| `SAFETY_REMINDER` | 안전 안내 시점 | reminder event 저장 | `SafetyReminderModal` | `SAFETY_REMINDER_SENT` | `/queue/users/{userId}/safety` |
