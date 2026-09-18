# 매칭 엔진 후속 테스트 계약

이 문서는 운영 matching engine, repository, Scheduler 구현 후
`matching-engine-foundation.sql`과 `MatchingScenarioFixture`를 이용해 추가할 테스트 목록입니다.

## Repository 통합 테스트

- 같은 `festival_id`, `WAITING`, `search_expires_at > now`인 pool만 후보로 조회한다.
- 차단 관계를 양방향으로 검사한다.
- active cooldown 회원과 이미 `PROPOSED`인 회원을 제외한다.
- `SELECT FOR UPDATE SKIP LOCKED`를 사용한 두 transaction이 같은 pool을 선점하지 않는다.
- active pool, active group, active cooldown partial unique index가 중복을 차단한다.

## Engine 상태 전이 테스트

- 후보 선점과 attempt/proposal 생성이 하나의 transaction으로 처리된다.
- 최초 제안은 `INITIAL_MATCH`, `proposal_round=1`이다.
- 같은 proposal의 중복 응답은 `(proposal_id, member_id)` 제약으로 차단된다.
- 목표 인원 전원 수락 시 group이 한 번만 확정된다.
- 목표 미달이면서 2명 이상 수락하고 전원이 `allow_minimum_two=true`이면 같은 attempt에서 round 2를 만든다.
- 인원 미달 재확인은 새로운 proposal ID를 사용한다.
- 완전 재매칭은 새로운 attempt ID를 사용한다.

## Scheduler 테스트

- `search_expires_at`이 지난 `WAITING` pool을 `EXPIRED`로 전이한다.
- `expires_at`이 지난 `SENT` proposal만 `TIMEOUT`으로 전이한다.
- 이미 응답한 proposal을 timeout으로 덮어쓰지 않는다.
- 만료 처리 재실행은 동일 event, penalty, cooldown을 중복 생성하지 않는다.
- stale `locked_at`과 `lock_token`을 회수하되 유효한 transaction lock을 대체하지 않는다.

## pgvector와 scoring 테스트

- V1~V11이 빈 `pgvector/pgvector:pg16` PostgreSQL에 적용된다.
- `preference_text` 또는 embedding이 없거나 `FAILED`여도 정형 여행 스타일 점수로 진행한다.
- embedding이 `COMPLETED`이면 cosine similarity를 보조 점수로 결합한다.
- 임베딩 계산은 후보 row lock transaction 밖에서 수행한다.
