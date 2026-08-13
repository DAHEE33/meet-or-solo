# MatchRoom 상대 회원 차단 Frontend 수동 테스트

## 1. 상태와 범위

- 현재 상태: `PENDING`
- Backend 1차 완료 뒤 후속 Frontend 차단 확인 UI를 연결할 때 실행한다.
- 실행하지 않은 항목은 `PASS`로 기록하지 않는다.

## 2. 후속 UI 연결 지점

- `MatchRoomPage`의 본인을 제외한 상대 카드 action에서 현재 `groupId`와 상대
  `memberId`를 사용한다.
- 확인 dialog에서 상대 nickname과 향후 서로 매칭되지 않는다는 효과를 안내하되,
  신고·제재나 상대 알림이 발생한다고 표현하지 않는다.
- 확인 후 `POST /api/match-groups/{groupId}/blocks`에 아래 body만 전송한다.

```json
{
  "blockedMemberId": 27
}
```

## 3. 수동 검증 항목

1. 본인 카드에는 차단 action이 없고 상대 카드에만 표시되는지 확인한다.
2. 대상 확인 전에는 요청하지 않고, 확인 후 한 번만 요청하는지 확인한다.
3. 빠른 중복 클릭과 재시도에서도 `201`과 같은 `blockId`/`createdAt`을 받는지 확인한다.
4. payload에 blocker ID, 자유 reason, JWT/cookie 값이 없는지 확인한다.
5. 성공 응답과 화면에 blocker identity, 내부 reason, 상대 개인정보가 없는지 확인한다.
6. 상대 화면에 차단 사실이나 WebSocket/MatchRoom event가 표시되지 않는지 확인한다.
7. 실패 시 dialog 대상이 유지되고 재시도할 수 있는지 확인한다.
8. 차단 성공이 신고 성공, penalty/cooldown 또는 점수 변경으로 표현되지 않는지 확인한다.
9. 차단 후 양쪽 회원이 새 매칭을 신청했을 때 서로 추천되지 않는지 확인한다.

전체 수동 테스트 최종 판정: [ ] `PASS` / [ ] `FAIL` / [x] `PENDING`
