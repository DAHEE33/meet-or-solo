# 마이페이지 차단 회원 관리·해제 수동 테스트

## 상태와 원칙

- 현재 상태: `PENDING`
- 실제 두 브라우저와 dev DB에서 실행하기 전에는 어떤 항목도 `PASS`로 기록하지 않습니다.
- 아래 SQL은 모두 읽기 전용입니다. 차단 관계나 매칭 상태를 만들기 위한 DB 변경 SQL은
  제공하거나 실행하지 않습니다.

## 수동 절차

1. 정상 UI로 회원 A가 B를 차단한 상태를 준비합니다.
2. A의 마이페이지 `차단 회원 관리`에 B의 nickname, 공개 profile image와 차단 시각만
   표시되는지 확인합니다.
3. B 화면과 API에서는 차단 관계, 차단 주체와 역방향 관계를 알 수 없는지 확인합니다.
4. A가 B의 해제를 선택하고 향후 재매칭 가능, 현재 MatchRoom 불변과 상대 알림 부재 안내를
   확인한 뒤 최종 제출합니다.
5. 성공 뒤 목록에서 B만 제거되고 다른 차단 회원은 유지되는지 확인합니다.
6. 아래 조회로 A→B `user_blocks` row가 0건인지 확인합니다.
7. 같은 DELETE를 반복해 body 없는 HTTP 204 멱등 성공인지 확인합니다.
8. 해제 전후 penalty/cooldown/event가 생성되지 않고 회원 점수가 불변인지 확인합니다.
9. 양쪽의 현재 MatchRoom과 확정 group/member가 변경되지 않는지 확인합니다.
10. 다른 제한이 만료되고 양쪽이 유효 체크인을 통과한 뒤 A/B가 신규 매칭 후보가 될 수
    있는지 확인합니다. 상대의 역방향 차단이 남아 있으면 후보가 되지 않는 것이 정상입니다.

## 읽기 전용 SQL

```sql
SELECT id, blocker_member_id, blocked_member_id, reason, created_at
FROM user_blocks
WHERE blocker_member_id = :member_a_id
  AND blocked_member_id = :member_b_id;

SELECT id, penalty_score, manner_temperature
FROM members
WHERE id IN (:member_a_id, :member_b_id)
ORDER BY id;

SELECT id, member_id, event_type, score_delta, related_group_id, created_at
FROM match_penalty_events
WHERE member_id IN (:member_a_id, :member_b_id)
  AND created_at >= :before_unblock_at
ORDER BY created_at, id;

SELECT id, member_id, reason, status, starts_at, expires_at, created_at
FROM match_cooldowns
WHERE member_id IN (:member_a_id, :member_b_id)
  AND created_at >= :before_unblock_at
ORDER BY created_at, id;

SELECT id, group_id, member_id, event_type, created_at
FROM match_events
WHERE group_id = :current_group_id
  AND created_at >= :before_unblock_at
ORDER BY created_at, id;

SELECT g.id, g.status, gm.member_id, gm.status AS member_status
FROM match_groups g
JOIN match_group_members gm ON gm.group_id = g.id
WHERE g.id = :current_group_id
ORDER BY gm.member_id;
```

## 실행 기록

| 확인 항목 | 판정 | 실제 결과 |
| --- | --- | --- |
| A 정방향 목록과 공개 필드 | `PENDING` | 미실행 |
| B 화면 역방향 관계 비노출 | `PENDING` | 미실행 |
| B만 해제·반복 DELETE 204 | `PENDING` | 미실행 |
| `user_blocks` row 0건 | `PENDING` | 미실행 |
| penalty/cooldown/event·점수 불변 | `PENDING` | 미실행 |
| 현재 MatchRoom/group 불변 | `PENDING` | 미실행 |
| 제한 만료·유효 체크인 후 신규 후보 복귀 | `PENDING` | 미실행 |
