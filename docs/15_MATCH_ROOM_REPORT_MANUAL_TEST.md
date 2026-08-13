# MatchRoom 구조화 신고 수동 테스트

## 1. 상태와 범위

- 현재 상태: `PARTIAL_PASS`
- 2026-08-13 핵심 UI·멱등 저장 검증은 `PASS`입니다. penalty/cooldown·회원 점수
  불변과 실패 복구·Network 상세 항목은 확인 전이므로 최종 판정은 보류합니다.
- 자동 테스트와 별개로 사용자가 두 브라우저에서 직접 수행하는 절차입니다.
- 조회 SQL만 사용하며 데이터를 삭제하거나 수정하지 않습니다.
- 차단, 관리자 처리, 자동 penalty/cooldown과 manner temperature 변경은 기대 동작이
  아닙니다.

## 2. 두 브라우저 절차

1. 서로 다른 회원 A, B로 로그인한 두 브라우저에서 같은 group을 확정하고
   `/match-room`에 진입합니다.
2. A 화면에서 본인 카드에는 `신고하기`가 없고 B 카드에만 있는지 확인합니다.
   3~4인 group이면 각 상대 카드 action이 올바른 nickname을 대상으로 여는지 확인합니다.
3. B의 `신고하기`를 누르고 여섯 한국어 사유, 자유 입력 부재와 운영 검토 안내를
   확인합니다. `안전 문제` 선택 시 112 등 긴급 기관 안내를 확인합니다.
4. 사유 없이 `다음`이 비활성인지 확인한 뒤 사유를 선택합니다. 최종 확인 화면에서
   B의 nickname과 선택한 한국어 사유가 일치하는지 확인합니다.
5. 브라우저 개발자 도구 Network 기록을 보존한 채 `신고 접수하기`를 빠르게 두 번
   눌러 요청이 한 번만 생성되는지 확인합니다.
6. Network에서 URL이 `/api/match-groups/{현재 groupId}/reports`, method가 `POST`,
   status가 `201`인지 확인합니다. Request Payload는 아래 두 필드만 있어야 합니다.

```json
{
  "reportedMemberId": 27,
  "reasonCode": "RUDE"
}
```

`reporterMemberId`, JWT와 cookie 값은 payload·화면·console에 출력되면 안 됩니다.

7. A 화면에 접수 완료 안내가 표시되고 dialog가 닫히는지 확인합니다. 같은 요청을
   다시 제출해도 HTTP 201과 정상 완료로 처리되는지 확인합니다.
8. Offline 또는 Network request blocking으로 실패를 유도할 경우 dialog와 대상·사유가
   유지되고 재시도할 수 있는지 확인합니다. 이때 MatchRoom 멤버/도착/timeline
   snapshot이 바뀌지 않아야 합니다.
9. B 화면에는 신고 사실, 신고자, 신고 사유 또는 별도 WebSocket 알림이 표시되지
   않는지 확인합니다. 두 화면의 기존 도착·취소·완료·timeline 및 WebSocket/polling
   복원이 계속 동작하는지 확인합니다.
10. 신고 성공 뒤 차단됐다는 안내나 차단 action 자동 호출이 없는지 확인합니다.

## 3. 읽기 전용 DB 확인 SQL

아래 placeholder를 실제 확인 대상 ID로 바꿔 실행합니다.

```sql
SELECT id,
       reporter_member_id,
       reported_member_id,
       group_id,
       reason_code,
       status,
       created_at,
       updated_at
FROM reports
WHERE group_id = :group_id
  AND reporter_member_id = :reporter_member_id
  AND reported_member_id = :reported_member_id
ORDER BY created_at DESC, id DESC;
```

동일 신고의 중복 row가 없는지 확인합니다.

```sql
SELECT reporter_member_id,
       reported_member_id,
       group_id,
       reason_code,
       COUNT(*) AS row_count
FROM reports
WHERE group_id = :group_id
  AND reporter_member_id = :reporter_member_id
  AND reported_member_id = :reported_member_id
GROUP BY reporter_member_id, reported_member_id, group_id, reason_code
ORDER BY reason_code;
```

신고 전후 결과를 별도로 저장해 비교합니다. 신고 자체로 아래 값이나 row가 추가되면
안 됩니다.

```sql
SELECT id, penalty_score, manner_temperature
FROM members
WHERE id IN (:reporter_member_id, :reported_member_id)
ORDER BY id;

SELECT id, member_id, event_type, score_delta, reason, related_group_id, created_at
FROM match_penalty_events
WHERE member_id IN (:reporter_member_id, :reported_member_id)
  AND created_at >= :before_report_at
ORDER BY created_at, id;

SELECT id, member_id, reason, status, starts_at, expires_at, created_at
FROM match_cooldowns
WHERE member_id IN (:reporter_member_id, :reported_member_id)
  AND created_at >= :before_report_at
ORDER BY created_at, id;
```

신고가 MatchRoom timeline event를 만들지 않는지도 확인합니다.

```sql
SELECT id, group_id, member_id, event_type, created_at
FROM match_events
WHERE group_id = :group_id
  AND created_at >= :before_report_at
ORDER BY created_at, id;
```

## 4. 완료 기록

실제 실행 전에는 `PENDING`을 유지합니다. 실행 시 날짜, 환경, group/member ID,
Network 결과와 각 SQL 판정을 기록하고 확인하지 않은 항목을 PASS로 표시하지 않습니다.

### 2026-08-13 수동 검증

| 확인 항목 | 판정 | 실제 결과 |
| --- | --- | --- |
| 상대 카드 신고 action | `PASS` | 상대 카드에서 신고 dialog 진입 |
| 동일 상대·동일 사유 반복 제출 | `PASS` | 반복 제출 성공 |
| 동일 사유 DB 멱등성 | `PASS` | 동일 group/reporter/reported/reason row 1건 확인 |
| 다른 사유 별도 저장 | `PASS` | `SAFETY`가 별도 row로 저장됨 |
| 안전 문제 긴급 안내 | `PASS` | 112 안내 표시 확인 |
| 상대 화면 비노출 | `PASS` | 신고 사실·신고자 관련 표시 없음 |
| 새로고침 후 MatchRoom 유지 | `PASS` | 신고 내역을 노출하지 않고 기존 화면 유지 |
| penalty/cooldown 불변 | `PENDING` | 아래 읽기 전용 SQL 확인 필요 |
| `penalty_score`, `manner_temperature` 불변 | `PENDING` | 신고 전 값과 비교 필요 |
| 신고 관련 MatchRoom event 미생성 | `PENDING` | 아래 읽기 전용 SQL 확인 필요 |
| Network method/status/payload | `PENDING` | `POST`, HTTP 201과 payload 확인 필요 |
| 실패 후 dialog 유지·재시도 | `PENDING` | Offline 검증 필요 |

핵심 UI와 DB 멱등 저장 판정: **`PASS`**

전체 수동 테스트 최종 판정: [ ] `PASS` / [ ] `FAIL` / [x] `PENDING`
