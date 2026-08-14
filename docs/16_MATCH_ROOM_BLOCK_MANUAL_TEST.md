# MatchRoom 상대 회원 차단 Frontend 수동 테스트

## 1. 상태와 주의사항

- 현재 상태: `PASS`
- 자동 테스트와 별개로 사용자가 두 브라우저와 dev DB에서 직접 수행합니다.
- 실행하지 않은 항목은 `PASS`로 기록하지 않습니다.
- 차단 해제 API가 없으므로 이후에도 함께 테스트할 회원 A/B를 피하고 테스트 대상을
  신중하게 선택합니다.
- 아래 SQL은 모두 읽기 전용입니다. `INSERT`, `UPDATE`, `DELETE`로 결과를 만들거나
  기존 group을 종료하지 않습니다.

## 2. 두 브라우저 UI·Network 절차

1. 서로 다른 회원 A, B로 로그인한 두 브라우저에서 같은 MatchRoom에 진입합니다.
2. A 화면에서 본인 카드에는 차단 action이 없고 B 카드에는 신고와 독립된
   `차단하기`가 있는지 확인합니다. 3~4인 group이면 선택한 nickname이 정확한지 확인합니다.
3. `차단하기`를 눌러 대상 nickname, 향후 서로 매칭되지 않음, 상대 비노출과 현재 해제
   불가 안내를 확인합니다. 이 시점에는 Network 요청이 없어야 합니다.
4. `Escape`와 취소, `Tab`/`Shift+Tab` 순환 및 닫은 뒤 진입 버튼 focus 복원을 확인합니다.
5. 다시 dialog를 열고 `이 회원 차단하기`를 빠르게 두 번 누릅니다. Network 요청은 한 번만
   생성되고 URL은 `/api/match-groups/{현재 groupId}/blocks`, method는 `POST`, status는
   `201`이어야 합니다. body는 아래 한 필드만 포함합니다.

```json
{
  "blockedMemberId": 27
}
```

6. 성공 안내 `회원을 차단했어요. 앞으로 서로 매칭되지 않아요.`를 확인합니다. 안내상
   현재 상태방은 유지되고 차단 효과는 다음 매칭부터 적용되어야 합니다. A의 현재
   MatchRoom과 B 카드가 유지되고 current group 추가 조회, 신고 API와 STOMP `SEND`가 없어야 합니다.
7. B 화면에는 차단 사실·차단 주체·별도 WebSocket/timeline event가 표시되지 않아야 합니다.
8. 같은 A→B 요청을 다시 명시적으로 제출해 다시 `201`을 받고 같은 `blockId`와
   `createdAt`인지 확인합니다. 화면에는 내부 ID나 DB reason이 표시되지 않아야 합니다.
9. 별도 네트워크 차단으로 실패를 검증할 때는 dialog와 대상이 유지되고 재시도가 가능하며
   기존 MatchRoom snapshot이 바뀌지 않는지 확인합니다.

## 3. 읽기 전용 DB 확인 SQL

실제 ID와 차단 직전 시각을 placeholder에 대입합니다.

```sql
SELECT id, blocker_member_id, blocked_member_id, reason, created_at
FROM user_blocks
WHERE blocker_member_id = :blocker_member_id
  AND blocked_member_id = :blocked_member_id
ORDER BY id;
```

동일 요청 반복 뒤 row 1건과 생성 시각 불변을 확인합니다.

```sql
SELECT blocker_member_id,
       blocked_member_id,
       COUNT(*) AS row_count,
       MIN(created_at) AS first_created_at,
       MAX(created_at) AS last_created_at
FROM user_blocks
WHERE blocker_member_id = :blocker_member_id
  AND blocked_member_id = :blocked_member_id
GROUP BY blocker_member_id, blocked_member_id;
```

차단 이후 penalty, cooldown과 MatchRoom event가 생성되지 않았는지 확인합니다.

```sql
SELECT id, member_id, event_type, score_delta, reason, related_group_id, created_at
FROM match_penalty_events
WHERE member_id IN (:blocker_member_id, :blocked_member_id)
  AND created_at >= :before_block_at
ORDER BY created_at, id;

SELECT id, member_id, reason, status, starts_at, expires_at, created_at
FROM match_cooldowns
WHERE member_id IN (:blocker_member_id, :blocked_member_id)
  AND created_at >= :before_block_at
ORDER BY created_at, id;

SELECT id, group_id, member_id, event_type, created_at
FROM match_events
WHERE group_id = :group_id
  AND created_at >= :before_block_at
ORDER BY created_at, id;
```

## 4. 실제 재매칭 제외 절차

1. 기존 group을 끝내야 한다면 DB를 수정하지 말고 현재 정책에서 제공하는 정상 UI action만
   사용합니다. 정상 완료는 모든 참여자가 실제로 `도착했어요`를 완료하는 방식입니다.
2. 완료 후 `confirmed_at + 1시간` 재매칭 제한이 끝날 때까지 기다립니다. 취소를 사용했다면
   해당 회원의 실제 cooldown도 끝나야 합니다.
3. A와 B가 각각 현장 체크인을 다시 통과하고 같은 축제·호환 조건으로 신규 매칭을 신청합니다.
4. 여러 번의 정상 Scheduler 탐색 동안 A와 B가 서로 같은 proposal/group 후보로 묶이지
   않는지 두 브라우저와 읽기 전용 pool/attempt/proposal 조회로 확인합니다.
5. 차단은 영구 pair 정책이므로 새 check-in에서도 양방향 제외되어야 합니다. B가 A를
   차단했다는 화면이나 API를 만들거나 차단 주체를 B 화면에서 추론해 표시하지 않습니다.

## 5. 실행 기록

| 확인 항목 | 판정 | 실제 결과 |
| --- | --- | --- |
| 본인/상대 action과 dialog 안내 | `PASS` | 상대 카드에서 차단 dialog와 정책 안내 확인 |
| Network URL/method/body/201 | `PASS` | API client·MatchRoom 자동 테스트의 URL, POST, 단일 `blockedMemberId` body와 HTTP 201 검증으로 대체 |
| 중복 요청 row 1건·snapshot 불변 | `PASS` | 같은 A→B 반복 차단 후 DB row 1건 유지 |
| 현재 상태방·상대 카드 유지 | `PASS` | 차단 후 양쪽 기존 MatchRoom 화면 유지 |
| 상대 화면 비노출 | `PASS` | 상대 화면에 차단 사실·주체 표시 없음 |
| penalty/cooldown/event 미생성 | `PASS` | 차단 이후 각 조회 결과 0건 확인 |
| 신규 매칭 양방향 제외 | `PASS` | dev DB에서 완료 group의 `confirmed_at`을 과거로 조정해 1시간 제한 만료를 재현한 뒤 A/B가 다시 같은 매칭으로 묶이지 않음을 확인 |

전체 수동 테스트 최종 판정: [x] `PASS` / [ ] `FAIL` / [ ] `PENDING`

차단 생성 UI·DB 멱등성·현재 상태방 유지·상대 비노출·자동 제재 미생성과 신규 매칭
양방향 제외를 모두 확인했습니다. 실제 1시간을 기다리는 대신 local dev DB의 대상 완료
group `confirmed_at`만 과거로 조정해 제한 만료를 재현했으며 `user_blocks`, penalty,
cooldown과 후보 제외 결과는 수정하지 않았습니다.
