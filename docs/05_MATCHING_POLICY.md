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
- 태그 교집합
- 희망 시간 유사성
- 체크인 시각 근접성
- 매너온도
- 관광공사 데이터 기반 축제/동선 태그

첫 구현에서는 정교한 scoring보다 상태 정확성과 중복 방지가 중요합니다.

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
11. 목표 인원에는 미달하지만 2명 이상 수락했고 수락자가 모두 2명 진행을 허용하면 인원 미달 팝업을 보낸다.
12. 현재 인원으로 시작을 선택하면 매칭을 확정한다.
13. 조건을 만족하지 못하면 실패 처리하고 재대기, cooldown, penalty를 적용한다.
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

## penalty/cooldown

penalty 또는 cooldown 적용 대상:

- 반복 거절
- timeout/미응답
- 매칭 확정 후 취소
- No-show
- 관리자에 의해 유효하다고 판단된 신고

MVP는 단순 cooldown window로 시작하고, 이후 매너온도 scoring으로 확장할 수 있습니다.

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
