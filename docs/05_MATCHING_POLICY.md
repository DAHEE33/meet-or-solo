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

이 값은 두 단계에서 회원의 최소 2인 진행 의사를 판단하는 데 사용합니다.

- 매칭 확정 전 목표 인원 미달 round 2에서 수락자 2명으로 시작할 수 있는지 판단한다.
- 매칭 확정 후 취소 또는 `NO_SHOW`로 현재 참여자가 2명만 남았을 때 group을 유지할 수 있는지 판단한다.

group 확정 시 각 회원의 `allow_minimum_two`를 `match_group_members`에 snapshot으로
보존합니다. 이후 원본 pool이나 attempt 이력이 바뀌어도 확정 당시 동의는
변하지 않습니다.

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

체크인 유효시간과 확정 매칭 유효시간:

```text
check-in: checked_in_at + 1시간
confirmed match: confirmed_at + 1시간
```

기획서 v5.0의 2시간 계약은 MVP 현장 회전과 재검증 편의를 고려해 1시간으로
조정합니다. 이는 `confirmed_at + 30분`인 도착/NO_SHOW 마감과 별도입니다.
정상 완료가 1시간보다 먼저 발생해도 확정 매칭 유효 종료 시각까지 새 매칭을
신청할 수 없습니다. 제한 종료 뒤에는 유효한 체크인이 있어야 하며 체크인이
만료되었다면 현장에서 다시 체크인합니다.

매칭 신청·검색 실패 횟수를 일괄 차감하는 최대 3회 제한은 MVP에 두지 않습니다.
동시 active pool/group 단일성, 완료 후 1시간 제한과 기존 귀책 cooldown으로
먼저 운영하고 실제 남용 데이터가 확인되면 체크인당 확정 group 횟수를 별도
정책으로 검토합니다.

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

### 명시적 거절 상대의 check-in pair 제외

- 재매칭 횟수 제한은 두지 않으며 기획서의 최대 5회 제한은 구현하지 않는다.
- round 1 `INITIAL_MATCH`에서 사용자가 명시적으로 `REJECTED`를 선택한 경우에만 거절 회원과 같은 proposal의 다른 회원 사이를 양방향 제외한다.
- 3인 proposal에서 A가 거절하면 A-B와 A-C만 제외하고 B-C는 제외하지 않는다.
- `TIMEOUT`, round 2 취소, 인원 미달, 시스템 오류, 정상 완료와 MatchRoom 자발적 취소는 이 제외를 생성하지 않는다.
- 제외는 proposal 당시 두 pool이 사용한 `festival_checkins.id` 조합에만 적용한다. 어느 한 회원이라도 새 check-in을 사용하면 과거 제외는 적용하지 않는다.
- `user_blocks`는 영구 안전 차단이고 `match_opponent_exclusions`는 check-in 범위의 임시 재추천 제외이므로 저장과 조회 책임을 분리한다.
- 제외 pair와 거절 회원은 REST, WebSocket과 Frontend에 공개하지 않는다.

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
9. 응답 기한까지 미응답한 사용자는 timeout 처리하되, 조기 종료된 미응답자는 비귀책 종료한다.
10. 목표 인원이 모두 수락하면 매칭을 확정한다.
11. 목표 인원에는 미달하지만 2명 이상 수락했고 수락자가 모두 2명 진행을 허용하면 같은 attempt 안에서 인원 미달 재확인 proposal을 새로 생성한다.
12. 현재 인원으로 시작을 선택하면 매칭을 확정한다.
13. 조건을 만족하지 못하면 기존 attempt를 실패 처리하고 귀책 회원에게만 cooldown, penalty를 적용한다.
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
- round 1은 응답이 들어올 때마다 목표 인원 성사 가능성과 최소 2인 진행 가능성을 다시 계산한다.
- 2인 최초 제안은 한 명이 `REJECTED`를 제출하면 즉시 종료한다.
- 3~4인 최초 제안에서 목표 인원 성사가 불가능해진 뒤 수락자가 최소 2명이고 수락자 전원의 `allow_minimum_two`가 `true`로 확정되면, 아직 응답하지 않은 회원을 proposal `EXPIRED`, attempt member `EXCLUDED`로 비귀책 종료하고 즉시 round 2를 생성한다.
- 목표 인원 성사와 최소 2인 진행이 모두 불가능해진 경우에는 남은 미응답 회원을 같은 방식으로 비귀책 종료하고 attempt를 즉시 실패 처리한다.
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

- 매칭 탐색 자발적 취소
- 반복 거절
- timeout/미응답
- 매칭 확정 후 취소
- No-show
- 관리자에 의해 유효하다고 판단된 신고

MVP는 단순 cooldown window로 시작합니다. `penalty_score`는 단기 재매칭 제한과
운영 감사에 사용하고, `manner_temperature`는 후기·신고·관리자 정책이 함께
구현되는 후속 단계에서 연결합니다. 현재 확정 후 취소·노쇼 구현에서는
`manner_temperature`를 변경하지 않습니다.

### 매칭 탐색 자발적 취소 (WAITING/LOCKED)

사용자는 매칭 탐색 중(`WAITING` 또는 `LOCKED` 상태) proposal이 전송되기 전에
탐색을 자발적으로 취소할 수 있습니다.

```text
PUT /api/matching/pools/me/current/cancellation
```

- `WAITING` 또는 `LOCKED` 상태의 pool만 취소할 수 있다.
- `PROPOSED` 이후는 기존 proposal 응답(거절) 흐름을 사용하며 이 API로 취소할 수 없다.
  `409`를 반환하고 Frontend는 proposal 응답 화면으로 전환한다.
- `MATCHED`, `CANCELLED`, `EXPIRED`는 이미 종료된 상태이므로 종료 안내를 반환한다.
- pool row를 `SELECT FOR UPDATE`로 잠근 뒤 상태를 재검증한다.
- Scheduler가 같은 pool을 `LOCKED`로 선점한 직후에도, proposal 생성 전이면 취소 가능하다.
  Scheduler의 proposal 생성 transaction이 먼저 commit되어 `PROPOSED`로 전이됐으면
  취소 API는 `409`를 반환한다.

당일 자발적 취소 횟수에 따른 cooldown escalation:

| 당일 자발적 취소 횟수 | cooldown 시작 | 기간 | penalty score |
| ---: | --- | ---: | ---: |
| 1회 | 취소 처리 시각 | 20초 | 없음 |
| 2회 | 취소 처리 시각 | 1분 | 없음 |
| 3회 | 취소 처리 시각 | 5분 | `+1` |
| 4회 이상 | 취소 처리 시각 | 10분 | `+1` |

- 횟수는 현재 체크인의 `checked_in_at` 이후 생성된 `POOL_CANCEL` cooldown을 기준으로 계산한다.
- 새 체크인을 생성하면 이전 체크인에서 쌓인 취소 횟수는 이어받지 않는다.
- cooldown과 penalty의 멱등성 원인 key는 취소된 `pool_id`를 사용한다.
- pool `CANCELLED`, cooldown, penalty event, `members.penalty_score` 변경은 같은
  transaction에서 처리한다.
- 확인 dialog로 사용자 의사를 재확인한 뒤 API를 호출한다.
- WebSocket 알림은 본인만 영향을 받으므로 전송하지 않는다.

### Proposal 단계 penalty/cooldown

매칭 확정 전 proposal 단계의 기존 정책은 다음과 같습니다.

| 원인 | cooldown 시작 | 기간 | penalty score |
| --- | --- | ---: | ---: |
| round 1 `REJECTED` | 거절 응답 처리 시각 | 30초 | 없음 |
| round 1 `TIMEOUT` | timeout 처리 시각 | 2분 | `+1` |
| round 2 `CANCEL_CURRENT_MEMBERS` | 취소 처리 시각 | 2분 | `+1` |
| round 2 `TIMEOUT` | timeout 처리 시각 | 5분 | `+2` |

- 첫 1회 면제는 두지 않는다.
- 반복 window, 가중치, score decay는 운영 데이터 확인 후 별도 정책으로 이월한다.
- 귀책 pool은 `CANCELLED`로 유지하고, 회원의 재신청 제한은 `match_cooldowns`로 분리한다.
- 조기 종료된 미응답 비귀책 회원에게는 response와 timeout penalty/cooldown을 생성하지 않는다.
- cooldown은 `starts_at <= now AND expires_at > now`일 때 active로 판단한다.
- 신규 cooldown 생성 전 같은 회원의 `expires_at <= now`인 `ACTIVE` row를 `EXPIRED`로 lazy 전환한다.
- 각 귀책 proposal의 `proposal_id`를 cooldown과 penalty event의 멱등성 원인 key로 사용한다.
- response, cooldown, penalty event, `members.penalty_score`, pool, attempt 변경은 같은 transaction에서 처리한다.
- 외부 API와 WebSocket 호출은 이 transaction에 포함하지 않는다.

### 매칭 확정 후 자발적 취소

`못 갈 것 같아요`는 자유 입력 없이 다음 구조화된 사유만 사용합니다.

```text
SCHEDULE_CHANGED
TRANSPORTATION_ISSUE
OTHER
```

Frontend 문구는 각각 `갑자기 일정이 생겼어요`, `이동이 어려워졌어요`,
`다른 이유가 있어요`를 사용합니다. 사유는 운영 분석과 audit에 사용하며,
사유별 자동 면제는 두지 않습니다. 상대 회원에게는 상세 사유를 공개하지 않고
취소 사실만 안내합니다.

자발적 취소의 귀책 기준은 group `confirmed_at`을 기준으로 합니다.

| 취소 시점 | penalty score | 당일 귀책 취소 횟수 | cooldown |
| --- | ---: | ---: | ---: |
| 확정 후 3분 이내 | 없음 | 집계 제외 | 없음 |
| 확정 3분 후부터 `arrival_deadline_at` 전 | `+1` | 1회 | 10분 |
| 확정 3분 후부터 `arrival_deadline_at` 전 | `+1` | 2회 | 30분 |
| 확정 3분 후부터 `arrival_deadline_at` 전 | `+1` | 3회 이상 | 60분 |

- `arrival_deadline_at`은 저장값을 새로 만들지 않고 `confirmed_at + 30분`으로 파생한다.
- deadline 시각부터 자발적 취소 API로 상태를 바꾸지 않고 Scheduler의 `NO_SHOW` 판정 대상으로 넘긴다.
- 당일 횟수는 `Asia/Seoul` 날짜의 확정 후 `CANCEL` penalty event를 기준으로 계산한다.
- 같은 group/member의 동일 취소 요청은 member 상태와 DB unique constraint로 멱등 처리한다.
- 취소 member, penalty event, cooldown, group 유지·취소와 match event는 하나의 transaction에서 처리한다.
- 실제 WebSocket 전송은 commit 뒤 `AFTER_COMMIT`에만 수행한다.

### NO_SHOW와 반복 제한

`arrival_deadline_at`까지 도착하지 않은 active member는 Scheduler가 `NO_SHOW`로
전환합니다.

```text
대상 member 상태:
JOINED
ARRIVAL_TIME_SELECTED

제외 member 상태:
ARRIVED
CANCELLED
NO_SHOW
LEFT
```

| 원인 | penalty score | 당일 NO_SHOW 횟수 | cooldown |
| --- | ---: | ---: | ---: |
| `NO_SHOW` | `+3` | 1회 | 30분 |
| `NO_SHOW` | `+3` | 2회 이상 | 60분 |

- NO_SHOW의 당일 횟수도 `Asia/Seoul` 날짜의 penalty event를 기준으로 계산한다.
- `members.penalty_score`는 누적 audit 값으로 유지하고 실제 cooldown 단계는 당일 귀책 event 집계로 결정한다.
- penalty score decay와 장기 초기화는 운영 데이터 확인 후 후속 정책으로 이월한다.
- 이번 단계에서는 NO_SHOW로 `manner_temperature`를 차감하지 않는다.
- Scheduler 재실행과 다중 instance 실행에도 같은 group/member의 `NO_SHOW`,
  penalty, cooldown과 event가 중복되지 않아야 한다.

### 확정 후 인원 감소와 group 유지

`confirmed_member_count`는 최초 확정 인원으로 유지합니다. 취소·NO_SHOW 이후
현재 유효 인원은 `current_member_count`로 별도 계산해 응답합니다.

| 현재 유효 인원 | 유지 조건 | 결과 |
| ---: | --- | --- |
| 3명 이상 | 추가 조건 없음 | group 유지 |
| 2명 | 남은 두 회원 모두 `allow_minimum_two = true` | group 유지 |
| 2명 | 한 명이라도 `allow_minimum_two = false` | group `CANCELLED` |
| 1명 이하 | 유지 불가 | group `CANCELLED` |

- 현재 유효 인원은 `JOINED`, `ARRIVAL_TIME_SELECTED`, `ARRIVED` member만 계산한다.
- group이 유지되면 취소·NO_SHOW member만 current group 공개 대상에서 제외한다.
- group이 취소되면 귀책 member는 `CANCELLED` 또는 `NO_SHOW`를 유지한다.
- group 취소로 남은 비귀책 member는 `LEFT`로 전환하며 penalty와 cooldown을 적용하지 않는다.
- `confirmed_member_count == active member count`를 강제하지 않는다.
- current group 조회는 `current_member_count`와 실제 공개 member 수의 정합성을 검증한다.
- 2명 유지 판단에 사용하는 `allow_minimum_two`는 group 확정 당시 snapshot을 기준으로 한다.

### 확정 후 취소·NO_SHOW Scheduler와 잠금

NO_SHOW Scheduler는 기본 비활성화하고 명시적 환경 설정에서만 실행합니다.
기본 fixed delay는 5초, batch 상한은 20입니다.
현재 구현의 별도 활성화 환경변수는
`MATCHING_NO_SHOW_SCHEDULER_ENABLED=true`이며, 기존 matching 탐색 Scheduler의
활성화 여부만으로 NO_SHOW 처리를 시작하지 않습니다.

```text
1. deadline이 지난 active group을 제한 batch로 조회
2. group row 잠금
3. group member row를 ID 오름차순으로 잠금
4. group/member 상태와 deadline 재검증
5. 미도착 member NO_SHOW 전환
6. penalty/cooldown과 match event 저장
7. 현재 유효 인원과 allow_minimum_two snapshot으로 group 유지 여부 판단
8. 필요하면 group CANCELLED와 비귀책 member LEFT 처리
9. commit
10. AFTER_COMMIT WebSocket 알림
```

도착 API도 같은 `group row -> group member row` 잠금 순서를 사용하며
`now < arrival_deadline_at`일 때만 `ARRIVED`를 허용합니다. deadline 정각부터는
도착 요청을 거절하고 NO_SHOW Scheduler가 처리합니다.

확정 후 취소·NO_SHOW의 멱등성은 proposal ID가 아니라
`group_id + member_id + cause`를 원인 key로 사용합니다. PostgreSQL unique
constraint와 상태 재검증을 함께 사용하며 Redis와 JVM 전역 lock에 의존하지
않습니다.

## MatchRoom 구조화 신고 1차 정책

- 인증 회원은 자신이 실제 참여한 match group의 다른 실제 참여자만 신고할 수 있다.
- API는 reporter ID를 받지 않고 HttpOnly `access_token`의 회원 ID를 사용한다.
- 본인 신고와 양쪽 중 한 명이라도 group 참여 이력이 없는 요청은 거절한다.
- `CONFIRMED`, `IN_PROGRESS` group은 진행 중 신고를 허용한다.
- `COMPLETED`는 `completed_at`, `CANCELLED`는 `cancelled_at`부터 30일 이내 신고를
  허용하며 정확히 30일 경계도 허용한다. terminal timestamp가 없으면 임의 시각으로
  대체하지 않고 정합성 충돌로 거절한다.
- 허용 사유는 `RUDE`, `SEXUAL_HARASSMENT`, `NO_SHOW`, `SCAM`, `SAFETY`, `OTHER`이다.
  자유 입력 상세는 1차 범위에서 받거나 저장하지 않는다.
- 동일 reporter/reported/group/reason 요청은 기존 신고 snapshot을 반환하는 멱등
  성공이며 `SUBMITTED` 이후 관리 상태를 초기화하지 않는다.
- 신고 접수만으로 penalty, cooldown, `penalty_score`, `manner_temperature`를
  변경하지 않는다. 피신고자 WebSocket/event도 발행하지 않는다.
- 차단, 관리자 검토와 제재, MatchRoom 신고 UI는 후속 범위다.

## MatchRoom 상대 회원 차단 Backend 1차 정책

- 인증 회원은 자신과 상대가 모두 실제 참여한 match group에서만 상대를 차단할 수
  있다. blocker는 HttpOnly `access_token`으로만 결정하며 request body에서 받지 않는다.
- 본인 차단은 금지한다. group이 없거나 blocker 또는 blocked가 참여하지 않은 경우는
  모두 `404 BLOCK_RESOURCE_NOT_FOUND`로 처리해 IDOR 탐색을 막는다.
- `CONFIRMED`, `IN_PROGRESS`는 허용한다. `COMPLETED`는 `completed_at`, `CANCELLED`는
  `cancelled_at`부터 정확히 30일까지 허용하며 terminal timestamp가 없으면 거절한다.
- `(blocker_member_id, blocked_member_id)`는 group과 무관하게 멱등이다. 신규·반복 요청은
  모두 같은 resource snapshot과 `201 Created`를 반환하며 기존 `created_at`과 reason을
  갱신하지 않는다. reason은 개인정보 없는 `MATCH_ROOM_MEMBER_BLOCK` 고정 내부 값이다.
- 차단은 penalty, cooldown, `penalty_score`, `manner_temperature`, `match_events`를 변경하지
  않고 WebSocket/application event를 발행하지 않는다. 상대에게 차단 사실과 blocker를
  노출하지 않는다.
- 신고 접수나 차단 생성만으로 현재 확정 group을 종료하거나 참여자를 퇴장시키지 않는다.
  현재 MatchRoom 상태방과 상대 카드는 유지하고 기존 도착·취소·완료 정책을 계속 적용한다.
  신고는 관리자 검토 대상으로 남고, 차단의 양방향 제외 효과는 이후 신규 매칭 후보 선정부터
  적용한다.
- proposal 생성은 pool row를 ID 오름차순으로 잠근 뒤 정렬된 member pair별
  `pg_advisory_xact_lock(int,int)`을 획득하고 `user_blocks`를 다시 조회한다. 차단 API도
  같은 member-pair lock을 획득한 뒤 insert하므로, 먼저 lock을 얻은 transaction의 commit
  순서대로 차단 생성과 proposal 생성을 직렬화한다.
- member-pair lock은 `member-block:{lowerMemberId}:{higherMemberId}`의 SHA-256 앞 64비트를
  사용한다. 기존 check-in pair exclusion lock과 namespace가 다르며, proposal 경로는
  pool row lock → member-pair lock → check-in-pair lock 순서를 유지한다.
- 차단 해제 API와 관리 화면, 신고 후 자동 차단은 후속 범위다.

## 최초 proposal 응답 처리 정책

`INITIAL_MATCH`, `proposal_round=1` 응답은 동일 attempt의 `match_attempts` row를 먼저 잠가 직렬화합니다. 잠금 순서는 attempt, proposal, attempt member 순서로 고정합니다.

- 사용자 응답은 `responded_at < expires_at`일 때만 허용하며 같은 시각이면 timeout이다.
- 동일한 수락 또는 거절 반복은 기존 성공 결과를 반환하고, 최초 응답 이후 다른 응답으로 변경하지 않는다.
- 2인 proposal은 명시적 거절 또는 timeout 한 건으로 성사가 불가능해지는 즉시 attempt를
  `FAILED`로 종료한다. 아직 응답하지 않은 상대 proposal은 `EXPIRED`, attempt member는
  `EXCLUDED`로 비귀책 종료하며 response, penalty, cooldown을 만들지 않는다.
- 3~4인은 응답마다 `accepted + proposed == targetGroupSize`인지 먼저 확인해 목표 인원
  가능성이 남으면 기다린다. 목표 인원이 불가능해진 뒤에는 이미 수락한 회원 중
  `allowMinimumTwo=false`가 없어야 하며, 수락자와 해당 옵션을 허용한 미응답자를 합쳐
  최소 2명이 가능한 동안 필요한 응답을 기다린다.
- 목표 인원은 불가능하고 최소 2명 수락이 확정됐으며 수락자 전원이
  `allowMinimumTwo=true`이면 남은 미응답자를 비귀책 종료하고 같은 transaction에서 round 2
  `INSUFFICIENT_MEMBERS_CONFIRMATION`으로 즉시 전환한다. 목표·최소 인원이 모두 불가능하면
  즉시 `FAILED`로 종료한다.
- 귀책 회원의 pool은 `CANCELLED`, 비귀책 회원의 pool은 기존 검색 시간이 유효하면 `WAITING`, 만료됐으면 `EXPIRED`로 전환한다.
- 비귀책 pool의 `search_expires_at`은 연장하지 않고 임시 lock 정보는 제거한다.
- 명시적 round 1 `REJECTED`만 기존 상대 exclusion과 `REJECT` 30초 cooldown을 생성한다.
  조기 종료된 미응답자에게는 `TIMEOUT` 정책을 적용하지 않는다.
- 전원이 수락하면 마지막 응답 transaction에서 group, group member, pool `MATCHED`, attempt `CONFIRMED`를 원자적으로 생성·전환한다.
- timeout Scheduler는 기존 matching Scheduler의 활성화 조건, fixed delay, batch size를 재사용하되 attempt별 독립 transaction으로 처리한다.
- 거절·timeout cooldown과 penalty는 위 확정 정책과 proposal 기반 멱등성 계약에 따라 생성한다.
- 인원 미달 round 2는 앞의 확정된 인원 미달 정책에 따라 별도 proposal 회차로 처리한다.
- 최신 pool 조회는 회원 본인의 상태만으로 `SELF_REJECTED`, `NON_FAULT_TERMINATED`,
  `SELF_TIMEOUT`, `SYSTEM_TERMINATED` 종료 사유를 제공하며 상대 identity·응답·제한은 노출하지
  않는다.
- restriction 응답의 `serverNow`는 cooldown과 완료 제한 `remainingSeconds`를 계산한 동일
  `Clock` 시각이다. Frontend의 탐색·proposal·cooldown·완료 제한 countdown은 이 시각과
  client 수신 시각의 offset을 사용한다.

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
- `allow_minimum_two`는 최초 목표 인원 그룹 조합의 scoring에는 사용하지 않는다.
  목표 인원 미달 round 2와 확정 후 현재 인원이 2명으로 감소한 group의 유지
  판단에 사용한다.
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

### 전원 도착 완료 정책

- 별도 만남 완료 버튼 없이 기존 `도착했어요` 요청을 사용한다.
- `CANCELLED`, `NO_SHOW`, `LEFT`를 제외한 유효 참여자를 group row 선잠금 후 member ID 오름차순으로 모두 잠근 상태에서 판정한다.
- 마지막 도착 transaction에서 `MEMBER_ARRIVED`, group `COMPLETED`, 최초 `completed_at`, 유효 참여자 `COMPLETED`, group당 단일 `MATCH_COMPLETED`를 원자 처리한다.
- terminal member는 보존하며 `COMPLETED` member는 active unique index를 점유하지 않는다.
- 마지막 도착과 완료 후 반복 요청은 `COMPLETED` snapshot을 반환한다. 새 active group이 있으면 과거 완료 group보다 우선한다.
- current-group의 active 의미는 유지하므로 완료 후에는 `data: null`이다.
- `MATCH_COMPLETED` 신호는 기존 `TransactionalEventListener(AFTER_COMMIT)`으로 commit 뒤 전송한다.
- Frontend는 완료 응답 또는 완료 신호 뒤 REST `null`에서 `/matching`으로 이동하고 완료 안내를 한 번만 표시한다.
- 완료 후 재매칭 제한은 `completed_at + 1시간`이 아니라
  `confirmed_at + 1시간`까지 적용한다.
- 정상 완료 제한은 penalty/cooldown 이력이 아니며 완료 group에서 파생한다.
- 완료 화면은 취소·NO_SHOW terminal card와 분리하고 유효 종료 시각과 남은
  시간을 표시한다. 제한 중에는 새 매칭 신청을 비활성화한다.
- 완료 후기와 최근 완료 상세 조회 API는 후속 범위다.

위 완료 제한은 Backend restriction과 pool 신청 검증, Frontend 완료 전용 card에
구현되었습니다. 완료 이력 뒤 더 최신 pool이 생성되면 과거 완료 card를 현재
상태보다 우선하지 않습니다. 자동 테스트를 완료했으며 브라우저 수동 재검증은
별도로 남아 있습니다.

첫 active member 도착에서 `CONFIRMED -> IN_PROGRESS`로 전환하고
`started_at`을 한 번만 설정합니다. ARRIVED 반복은 멱등이며 마지막 유효 회원
도착에서 `COMPLETED`로 전환합니다.

매칭 확정 후 사용자는 `MatchRoomPage`로 이동합니다.

현재 상태방은 확정 group 조회, 도착 예정 시간, 도착 완료와 시스템 이벤트
타임라인을 제공합니다. active group은 `CONFIRMED`, `IN_PROGRESS`, current
참여자는 `JOINED`, `ARRIVAL_TIME_SELECTED`, `ARRIVED`로 제한합니다. 취소,
`NO_SHOW`, `LEFT` member와 종료 group은 current group 응답에서 제외합니다.
최초 확정 인원은 `confirmedMemberCount`, 현재 공개 참여자 수는
`currentMemberCount`로 구분합니다.

만남 포인트 정책:

- 관광공사 축제 공식 좌표는 실제 약속 장소가 아니라 후보 검색 중심점입니다.
- 축제 좌표 주변의 실제 카페·편의점·주차장·음식점 등은 Kakao Local API로
  검색하며 관광공사 `locationBasedList1`은 관광 POI와 fallback에 활용합니다.
- 문서의 `2km`는 만남 장소 후보 검색 범위이며 단말 위치 확인 반경이 아닙니다.
- 운영자가 축제별로 안전하고 찾기 쉬운 만남 장소를 여러 개 검토·등록합니다.
- 그룹 확정 시 활성 장소 중 하나를 배정합니다. MVP는 순환 배정을 사용하고,
  이후 같은 시간대 배정 그룹 수가 적은 장소를 우선하는 방식으로 확장합니다.
- 후보 수보다 동시 그룹 수가 많으면 같은 장소가 여러 그룹에 배정될 수 있으며,
  이 경우에도 각 그룹의 장소 snapshot은 확정 후 변경하지 않습니다.
- 최종 확정 장소의 ID, 장소명과 좌표는 group에 snapshot으로 저장해 모든
  참여자와 새로고침에 같은 결과를 제공합니다.
- 순환 배정은 festival row를 잠근 뒤 장소 snapshot이 있는 group 수를 활성 후보 수로
  나눈 나머지를 사용합니다. 후보는 `assignment_order, id`로 정렬하며 후보가 없으면
  그룹 확정 transaction 전체를 rollback합니다.
- 단말 위치 확인은 축제 공식 좌표가 아니라 그룹에 배정된 만남 포인트 좌표를
  기준으로 Backend가 수행합니다.
- 후속 GPS 정책은 도착 반경 `150m`, 최대 정확도 `100m`, 최대 측정 나이 `120초`,
  미래 시각 허용 `30초`이며 경계를 포함합니다. 이번 브랜치는 current-group의
  `arrivalRadiusMeters=150` 안내만 제공하고 실제 검증은 후속 브랜치에서 구현합니다.
- 신고 완료와 위치정보 약관·동의를 전제로 원본 사용자 GPS 좌표, 정확도와
  측정 시각을 도착 API로 전송합니다. Backend는 거리를 계산한 뒤 원본 좌표를
  DB, event, log와 WebSocket payload에 저장하지 않고 즉시 폐기합니다.
- 클라이언트가 계산한 거리나 `verified` 값은 전송하거나 신뢰하지 않습니다.
- 실제 만남 장소에 없는 허위 도착은 구조화된 신고 사유와 운영 검토로
  보완하며 신고만으로 즉시 자동 제재하지 않습니다.

도착 예정 시간 정책:

- 신규 선택 허용값은 `5`, `10`, `20`, `25`분입니다.
- `0`, `30`은 기존 row/event 조회 호환용으로만 유지하며 신규 요청에서는 거절합니다.
- `JOINED`에서 선택하면 `ARRIVAL_TIME_SELECTED`로 전환합니다.
- `ARRIVAL_TIME_SELECTED`는 다른 허용값으로 변경할 수 있습니다.
- 같은 값을 다시 선택하면 event와 알림을 추가하지 않는 멱등 성공입니다.
- `ARRIVED`, `CANCELLED`, `NO_SHOW`, `LEFT` member는 변경할 수 없습니다.
- `COMPLETED`, `CANCELLED` group에서는 변경할 수 없습니다.
- 현재 `도착했어요`는 `JOINED`, `ARRIVAL_TIME_SELECTED`를 `ARRIVED`로 전환하고
  `arrived_at`을 최초 한 번만 저장합니다. 후속 작업에서는 Backend가 단말 위치,
  정확도와 측정 시각을 검증한 뒤에만 이 상태 전이를 수행합니다.
- `ARRIVED` 반복 요청은 event와 알림을 추가하지 않는 멱등 성공입니다.
- deadline 정각부터 도착 완료 요청을 거절하고 Scheduler의 `NO_SHOW` 판정 대상으로 넘깁니다.

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

현재 matching 화면 구현은 아래 세부 이벤트를 화면 데이터로 직접 적용하지 않고
`MATCHING_STATE_CHANGED` 알림의 `reason`으로 전달합니다. 전달 경로는 인증된
회원별 `/user/queue/matching`이며, frontend는 알림을 받으면 current group,
active proposal, current pool, cooldown REST 상태를 다시 조회합니다.

WebSocket 알림은 유실되거나 중복될 수 있는 보조 신호입니다. PostgreSQL이 최종
상태이고 기존 polling을 fallback으로 유지합니다. transaction rollback 상태가
전달되지 않도록 실제 STOMP 전송은 상태 변경 transaction의 `AFTER_COMMIT`에
수행합니다.

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

## 시스템 이벤트 타임라인 정책

- 현재 운영 생성 코드가 있는 `MATCH_CONFIRMED`, `ARRIVAL_TIME_SELECTED`, `MEMBER_ARRIVED`만 표시합니다.
- `MATCH_CANCELLED`, `MEMBER_CANCELLED`, `SAFETY_REMINDER`는 실제 생성 기능이 구현되기 전 mock event로 표시하지 않습니다.
- 별도 미팅 시작 event가 없으므로 `startedAt`이나 첫 `MEMBER_ARRIVED`에서 “미팅이 시작됐어요” 항목을 중복 합성하지 않습니다.
- 같은 도착 예정 값과 동일 ARRIVED 멱등 요청은 새 `match_events`를 만들지 않으므로 타임라인도 증가하지 않습니다.
- 최근 50건만 제공하며 cursor pagination은 후속 범위입니다.
## 회원 본인 차단 목록 조회·해제 정책

- `GET /api/members/me/blocks`는 JWT cookie 회원이 `blocker_member_id`인 정방향
  `user_blocks`만 조회한다. 역방향 관계와 다른 회원의 관계는 노출하지 않는다.
- 응답 항목은 `blockedMemberId`, `nickname`, `profileImageUrl`, `blockedAt`으로 제한하고
  `blocked_at DESC`, 내부 `id DESC`로 결정적으로 정렬한다. 내부 ID와 reason은 응답하지 않는다.
- `DELETE /api/members/me/blocks/{blockedMemberId}`는 인증 회원과 path 대상이 정확히
  일치하는 row만 물리 삭제한다. 존재 여부와 삭제 건수에 관계없이 `204 No Content`이다.
- 해제는 상대 알림, WebSocket, match event를 만들지 않고 penalty/cooldown, 회원 점수와
  현재 group 상태를 변경하지 않는다. 감사 이력과 soft delete는 MVP 범위에서 제외한다.
- 해제는 차단 생성·proposal 생성과 동일한 정규화 member-pair advisory transaction lock
  안에서 DELETE한다. 기존 proposal 경로의 pool row lock → member-pair lock 순서를 바꾸지
  않으며 반대 순서의 신규 잠금 경로를 만들지 않는다.
- proposal transaction이 pair lock을 먼저 얻으면 종료 뒤 해제하고 기존 proposal/group은
  취소하지 않는다. 해제가 먼저 lock을 얻고 commit되면 이후 proposal 최종 검증은 차단이
  없는 상태를 관찰한다. 효과는 이후 신규 proposal 후보 검증부터 적용한다.
- 해제 뒤에도 cooldown, check-in, active pool/group과 정상 완료 제한 등 다른 제외 조건은
  유지한다. DB 직접 쓰기처럼 공통 pair lock을 우회하는 미래 경로는 이 race 보장 밖이다.
