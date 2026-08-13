# 매칭 엔진 구현 기록

## 40. 축제별 만남 장소와 그룹 snapshot

`V15`는 `festival_meeting_points`와 `match_groups.meeting_place_address`를 추가합니다.
운영자가 등록한 장소는 기본 `INACTIVE`이고 관리자 API로 검증 후 `ACTIVE`로 전환합니다.
신규 pool은 활성 후보가 한 곳 이상인 축제에만 진입할 수 있습니다.

```text
attempt FOR UPDATE
→ proposal FOR UPDATE
→ attempt member FOR UPDATE
→ accepted pools FOR UPDATE (id 오름차순)
→ festival FOR UPDATE
→ ACTIVE meeting points (assignment_order, id)
→ snapshot group count
→ group/member/event 저장
→ pool MATCHED
→ attempt CONFIRMED
```

index는 `assignedSnapshotGroupCount % activeCandidateCount`입니다. 같은 festival은
festival row lock으로 직렬화되고 다른 festival은 독립적입니다. 후보가 없으면 마지막
응답을 포함해 rollback하며 후보 수정·비활성화 후에도 기존 snapshot은 변하지 않습니다.

current-group의 nullable `meetingPoint`는 group snapshot을 사용합니다. 후보 검색 반경만
festival 값이고 도착 안내 반경은 `150`입니다. REST가 최종 원천이며 WebSocket은 재조회
trigger 역할만 유지합니다. MatchRoom은 읽기 전용 Kakao Maps 단일 핀을 표시하고 SDK
실패 시에도 장소명·주소를 유지합니다. SDK loader는 module-level Promise로 동시 삽입을
막고 실패한 script와 상태를 정리해 다음 mount에서 재시도합니다. unmount 이후에는
component 상태와 지도 DOM을 갱신하지 않습니다.

최초 meeting-point focused Backend 27건은 25건이 성공했고, 2건은 운영 코드가 아닌
`FestivalMeetingPointAdminServiceTest`의 nested Mockito stubbing 오류로 실패했습니다.
member mock을 지역 변수로 분리한 뒤 focused unit/Controller 11건과 test source compile이
성공했습니다. PostgreSQL Testcontainers repository 3건과 confirm transaction 46건,
matching 전체 266건, Backend 전체 322건도 failure·error·skip 없이 통과했습니다.

`package-lock.json` 기준 `npm ci`로 Frontend 의존성을 복원한 뒤 전체 Vitest 11 files
119건, TypeScript 검사와 production/PWA build가 성공했습니다. WSL npm의
`Exit handler never called` 오류 때문에 동일 명령을 Windows npm에서 완료했으며
package manager와 lockfile 의미 내용은 변경하지 않았습니다.
이번 작업 파일 대상 `git diff --check`는 통과했습니다. 저장소 전체 검사는 기존
working tree의 광범위한 CRLF 변경을 trailing whitespace로 판정해 실패했으며,
기존 사용자 파일의 줄바꿈은 일괄 변경하지 않았습니다.

### 40.1 dev DB·두 브라우저 수동 검증

2026-08-09 local dev DB에서 festival `144`, member `2`, `27`과 유효한
`ACTIVE` check-in을 사용해 수동 검증했습니다. 첫 번째 확정 group `21`에는
`assignment_order`가 앞선 `dev-meeting-point-1`이 snapshot으로 저장되었고,
group 취소 후 같은 조건으로 확정한 두 번째 group `22`에는
`dev-meeting-point-2`가 저장되었습니다. 이전 group `21`의 장소 snapshot은
취소 후에도 변경되지 않아 후보 원본과 확정 group snapshot의 분리를 함께
확인했습니다.

첫 번째 group의 두 브라우저에는 동일한 장소명·주소와
`arrivalRadiusMeters=150` 안내가 표시되었습니다. Kakao SDK를 불러오지 못한
환경에서도 장소명·주소 fallback이 유지되었습니다. 실제 Kakao JavaScript Key와
허용 도메인을 사용한 지도 핀 표시는 이번 local 검증에서 수행하지 않았으며 별도
환경 검증 항목으로 유지합니다.

## 1. 문서 목적과 범위

이 문서는 `meet-or-solo` backend에 실제로 구현된 매칭 엔진을 코드 중심으로 설명합니다. 매칭 정책을 새로 정의하거나 DB 설계를 반복하기보다, 정책과 schema가 Spring service, PostgreSQL transaction, JUnit 테스트로 어떻게 연결되는지 정리합니다.

문서의 주요 독자는 다음과 같습니다.

- 현재 매칭 코드를 인수인계받는 개발자
- proposal 응답, REST API, frontend를 이어서 구현할 개발자
- Spring transaction과 PostgreSQL row lock을 처음 접하는 참여자
- 구현 경험을 기술 블로그로 정리하려는 참여자

현재 구현 기준은 `feature/wbs-10-matching-frontend-integration` 작업 트리이며, 기준 HEAD는 `fb50c98d62c92ea51073ad195faec3ef33a9b238`입니다. 이 HEAD 이후 working tree의 확정 group 조회 frontend REST 연동 코드와 테스트까지 포함해 설명합니다.

관련 문서의 역할은 다음과 같이 구분합니다.

| 문서 | 역할 |
| --- | --- |
| [아키텍처](01_ARCHITECTURE.md) | 시스템 전체 구성과 도메인 경계 |
| [매칭 정책](05_MATCHING_POLICY.md) | 최종 매칭 정책과 상태 계약 |
| [테스트/품질 전략](09_TEST_AND_QUALITY_STRATEGY.md) | 프로젝트 전체 테스트 원칙 |
| [진행 기록](10_PROGRESS_LOG.md) | 시간순 구현 이력 |
| [DB 설계](11_DATABASE_DESIGN.md) | 테이블, 컬럼, 상태값, 제약조건 |
| 이 문서 | 실제 클래스 협력, transaction, 동시성, 테스트와 기술적 의사결정 |

이 문서에서 사용하는 구현 상태는 다음과 같습니다.

| 표시 | 의미 |
| --- | --- |
| 구현 완료 | 운영 코드와 대응 테스트가 존재함 |
| 일부 구현 | 기반 동작은 있지만 사용자 흐름 전체가 완성되지 않음 |
| schema 또는 문서 계약만 존재 | DB나 정책은 있지만 운영 코드가 없음 |
| 미구현 | 현재 저장소에 해당 운영 기능이 없음 |
| 후속 계획 | 방향만 합의됐고 아직 구현하지 않음 |

## 2. 현재 구현 수준 요약

한 문장으로 요약하면, 현재 backend는 유효한 대기 후보를 PostgreSQL에서 중복 없이 선점하고, 정형 여행 스타일로 2~4인 그룹을 조합한 뒤 최초 attempt와 proposal을 원자적으로 생성할 수 있습니다.

현재 작업 트리에는 matching 최소 REST API, 최초 proposal 수락·거절·timeout, 인원 미달 round 2와 최종 group 확정뿐 아니라 pool 신청 commit 이후 즉시 실행되는 application-level `POOL_ENTRY` trigger 운영 코드와 테스트도 포함되어 있습니다.

### 구현 완료

- `WAITING` 후보 조회와 안전 조건 필터
- requester 중심 claim과 Scheduler 전용 batch claim
- `FOR UPDATE SKIP LOCKED` 기반 다중 worker 선점
- 만료 `WAITING` 및 stale `LOCKED` 정리
- `TravelStyleCode` Jaccard scoring
- 같은 축제·같은 `preferredGroupSize`의 2~4인 그룹 조합
- 모든 pair 평균 group score와 결정적 greedy 배정
- 생성 직전 pool row lock과 안전 조건 재검증
- `match_attempts`, `match_attempt_members`, 최초 `match_proposals` 생성
- 그룹별 `REQUIRES_NEW` rollback 격리
- 성공 pool의 `LOCKED -> PROPOSED` 전환
- 미사용·실패 lock release
- 기본 비활성 Scheduler와 조건부 scheduling infrastructure
- `INITIAL_MATCH`, round 1의 수락·거절·timeout 상태 전이
- attempt 선잠금 기반 동시 응답 직렬화와 동일 응답 멱등성
- 실패 attempt의 proposal/member/pool 정리
- 전원 수락 시 group/member 생성과 pool `MATCHED`, attempt `CONFIRMED`
- 별도 timeout service와 조건부 Scheduler 진입점
- round 1 전체 terminal 이후 3명/4명 목표의 2명 이상 수락자에 대한 인원 미달 조건 판정
- 같은 attempt의 round 2 proposal 생성과 `START_WITH_CURRENT_MEMBERS`/`CANCEL_CURRENT_MEMBERS`/`TIMEOUT` 처리
- round 2 전원 진행 동의 시 실제 인원 group 확정과 취소·timeout 시 귀책/비귀책 pool 분리
- matching pool 신청과 현재 pool/proposal/restriction 조회 REST API
- 현재 확정 group과 참여자 공개 정보 조회 REST API
- pool 신청 transaction commit 이후 동기 application event 기반 즉시 matching orchestration
- requester pool 중심 동일 축제 claim과 trigger attempt `created_by=POOL_ENTRY`

### 일부 구현 또는 기반만 존재

| 기능 | 현재 수준 |
| --- | --- |
| 매칭 신청 | REST API, 신청 검증, pool 저장과 AFTER_COMMIT trigger 구현 |
| attempt lifecycle | `WAITING_RESPONSES -> INSUFFICIENT_MEMBERS -> FAILED/CONFIRMED`와 기존 전원 수락 확정 구현 |
| proposal lifecycle | `INITIAL_MATCH` round 1과 `INSUFFICIENT_MEMBERS_CONFIRMATION` round 2의 응답·timeout 구현 |
| cooldown | active cooldown 제외 조회와 귀책 응답 기반 생성 구현 |
| penalty | timeout·round 2 취소 점수와 append-only event 구현 |
| embedding | V11 schema만 존재. matching score에는 사용하지 않음 |
| response/group | 최초·인원 미달 응답 저장, 목표/최소 인원 group 확정, proposal action과 current group REST API 구현 |

### 미구현 또는 후속 계획

- frontend, WebSocket STOMP, Redis
- embedding 또는 외부 API scoring

현재 화면에서 동작을 확인할 수 없는 이유는 frontend 연결이 없기 때문입니다. backend는 pool 신청 시 `POOL_ENTRY` trigger로 즉시 탐색하며 Scheduler는 fallback과 시간 기반 정리를 담당합니다.

## 3. 전체 처리 흐름

Scheduler tick의 실제 흐름은 다음과 같습니다.

```mermaid
flowchart TD
    A["MatchingScheduler.run"] --> B["MatchingOrchestrationService.runTick"]
    B --> C["cleanup: 만료와 stale lock 정리"]
    C --> D["claim: WAITING 후보를 LOCKED로 선점"]
    D --> E{"claimedCount == 0"}
    E -- "yes" --> Z["finally release 후 종료"]
    E -- "no" --> F["batch read: pool, travel style, block"]
    F --> G["Jaccard scoring"]
    G --> H["2~4인 deterministic greedy grouping"]
    H --> I["그룹별 최종 row lock과 재검증"]
    I --> J["attempt, members, proposals 생성"]
    J --> K["LOCKED -> PROPOSED"]
    K --> L{"다음 그룹"}
    L -- "있음" --> I
    L -- "없음" --> Z
    I -- "실패" --> M["해당 그룹 rollback 및 로그"]
    M --> L
    Z --> N["같은 token의 남은 LOCKED release"]
```

중요한 점은 이 흐름 전체가 하나의 transaction이 아니라는 것입니다. DB row lock이 필요한 단계만 짧은 transaction으로 나누고, scoring과 조합은 row lock 밖에서 실행합니다.

관련 코드:

- [MatchingScheduler](../backend/src/main/java/com/survey/meetorsolo/domain/matching/scheduler/MatchingScheduler.java)
- [MatchingOrchestrationService](../backend/src/main/java/com/survey/meetorsolo/domain/matching/service/MatchingOrchestrationService.java)

## 4. 주요 클래스와 책임

### 설정과 Scheduler

| 클래스 | 입력 | 출력·효과 | Transaction |
| --- | --- | --- | --- |
| [MatchingConfiguration](../backend/src/main/java/com/survey/meetorsolo/domain/matching/config/MatchingConfiguration.java) | Spring context | `Clock`, scorer, composer bean | 없음 |
| [MatchingSchedulerProperties](../backend/src/main/java/com/survey/meetorsolo/domain/matching/config/MatchingSchedulerProperties.java) | `app.matching.scheduler.*` | 검증된 설정값 | 없음 |
| [MatchingSchedulingConfiguration](../backend/src/main/java/com/survey/meetorsolo/domain/matching/config/MatchingSchedulingConfiguration.java) | `enabled` | 조건부 scheduling infrastructure | 없음 |
| [MatchingScheduler](../backend/src/main/java/com/survey/meetorsolo/domain/matching/scheduler/MatchingScheduler.java) | scheduled tick | orchestration 호출 | 없음 |

### Service와 알고리즘

| 클래스 | 핵심 책임 | Transaction |
| --- | --- | --- |
| [MatchingOrchestrationService](../backend/src/main/java/com/survey/meetorsolo/domain/matching/service/MatchingOrchestrationService.java) | 단계 순서, 그룹 실패 격리, finally release | 없음 |
| [MatchPoolCleanupService](../backend/src/main/java/com/survey/meetorsolo/domain/matching/service/MatchPoolCleanupService.java) | 만료·stale 상태 정리 | `@Transactional` |
| [MatchPoolClaimService](../backend/src/main/java/com/survey/meetorsolo/domain/matching/service/MatchPoolClaimService.java) | requester 중심 후보 선점 | `@Transactional` |
| [SchedulerMatchPoolClaimService](../backend/src/main/java/com/survey/meetorsolo/domain/matching/service/SchedulerMatchPoolClaimService.java) | Scheduler batch 선점 | `@Transactional` |
| [MatchingBatchReader](../backend/src/main/java/com/survey/meetorsolo/domain/matching/service/MatchingBatchReader.java) | pool/style/block batch 조회 | read-only |
| [TravelStyleScorer](../backend/src/main/java/com/survey/meetorsolo/domain/matching/scoring/TravelStyleScorer.java) | Jaccard pair score | 없음 |
| [MatchGroupComposer](../backend/src/main/java/com/survey/meetorsolo/domain/matching/group/MatchGroupComposer.java) | 조합 생성·정렬·greedy 선택 | 없음 |
| [MatchProposalCreationService](../backend/src/main/java/com/survey/meetorsolo/domain/matching/service/MatchProposalCreationService.java) | 최종 검증·원자 생성 | `REQUIRES_NEW` |
| [MatchPoolReleaseService](../backend/src/main/java/com/survey/meetorsolo/domain/matching/service/MatchPoolReleaseService.java) | 같은 token의 남은 lock 반환 | `@Transactional` |

### Entity와 repository

| 코드 | 테이블·책임 |
| --- | --- |
| [MatchPool](../backend/src/main/java/com/survey/meetorsolo/domain/matching/entity/MatchPool.java) | `match_pools`, `lock()`, `propose()` |
| [MatchAttempt](../backend/src/main/java/com/survey/meetorsolo/domain/matching/entity/MatchAttempt.java) | `match_attempts`, 최초 attempt 생성 |
| [MatchAttemptMember](../backend/src/main/java/com/survey/meetorsolo/domain/matching/entity/MatchAttemptMember.java) | `match_attempt_members`, 후보별 score와 상태 |
| [MatchProposal](../backend/src/main/java/com/survey/meetorsolo/domain/matching/entity/MatchProposal.java) | `match_proposals`, 최초 proposal 생성 |
| [MatchResponse](../backend/src/main/java/com/survey/meetorsolo/domain/matching/entity/MatchResponse.java) | `match_responses`, 최초 proposal 응답 이력 |
| [MatchGroup](../backend/src/main/java/com/survey/meetorsolo/domain/matching/entity/MatchGroup.java) | 전원 수락 시 최종 group |
| [MatchGroupMember](../backend/src/main/java/com/survey/meetorsolo/domain/matching/entity/MatchGroupMember.java) | 확정 group 참여 회원 |
| [MatchPoolRepository](../backend/src/main/java/com/survey/meetorsolo/domain/matching/repository/MatchPoolRepository.java) | 후보 SQL, row lock, cleanup, release |
| [MatchAttemptRepository](../backend/src/main/java/com/survey/meetorsolo/domain/matching/repository/MatchAttemptRepository.java) | attempt 저장 |
| [MatchAttemptMemberRepository](../backend/src/main/java/com/survey/meetorsolo/domain/matching/repository/MatchAttemptMemberRepository.java) | attempt member 저장 |
| [MatchProposalRepository](../backend/src/main/java/com/survey/meetorsolo/domain/matching/repository/MatchProposalRepository.java) | proposal 저장 |
| [MatchResponseRepository](../backend/src/main/java/com/survey/meetorsolo/domain/matching/repository/MatchResponseRepository.java) | proposal/member별 단일 응답 조회·저장 |
| [MatchGroupRepository](../backend/src/main/java/com/survey/meetorsolo/domain/matching/repository/MatchGroupRepository.java) | attempt별 최종 group 저장 |
| [MatchGroupMemberRepository](../backend/src/main/java/com/survey/meetorsolo/domain/matching/repository/MatchGroupMemberRepository.java) | active 참여자 존재 확인과 공개 프로필 일괄 조회 |

## 5. MatchPool 상태 전이

V3는 여러 상태를 허용하지만 현재 운영 코드가 실제로 만드는 전이는 일부입니다. 전체 상태 계약은 [매칭 정책](05_MATCHING_POLICY.md)과 [DB 설계](11_DATABASE_DESIGN.md)를 참고합니다.

| 시작 상태 | 종료 상태 | 조건 | 구현 주체 |
| --- | --- | --- | --- |
| `WAITING` | `LOCKED` | 유효 후보 선점 성공 | claim service |
| `WAITING` | `EXPIRED` | `search_expires_at <= now` | cleanup |
| `LOCKED` | `WAITING` | 유효 stale lock 또는 미사용 lock | cleanup/release |
| `LOCKED` | `EXPIRED` | stale 또는 release 시 이미 만료 | cleanup/release |
| `LOCKED` | `PROPOSED` | attempt/proposal 원자 생성 성공 | creation service |

```mermaid
stateDiagram-v2
    [*] --> WAITING
    WAITING --> LOCKED: "claim"
    WAITING --> EXPIRED: "search_expires_at <= now"
    LOCKED --> WAITING: "stale recovery or unused release"
    LOCKED --> EXPIRED: "expired recovery or release"
    LOCKED --> PROPOSED: "initial proposal created"
    PROPOSED --> MATCHED: "전원 수락 group 확정"
    PROPOSED --> CANCELLED: "거절 또는 timeout 귀책"
    PROPOSED --> WAITING: "실패 attempt의 비귀책 / 검색 유효"
    PROPOSED --> EXPIRED: "실패 attempt의 비귀책 / 검색 만료"
    WAITING --> CANCELLED: "schema only / 미구현"
    WAITING --> COOLDOWN: "schema only / 미구현"
```

`MATCHED`, `CANCELLED`는 최초 proposal 응답 결과에서 사용합니다. `COOLDOWN` 생성은 정책 미확정으로 아직 사용하지 않습니다.

## 6. 후보 조회와 제외 조건

후보 조회에는 두 경로가 있습니다.

| 경로 | 사용 목적 | 특징 |
| --- | --- | --- |
| requester 중심 | 향후 사용자 진입 흐름의 기반 | 특정 축제, requester 자신과 양방향 block 제외 |
| Scheduler batch | 정기 matching tick | requester 없이 전역 batch 선점, block은 batch 조합 단계와 최종 생성 단계에서 확인 |

Scheduler claim 대상은 다음 조건을 모두 만족해야 합니다.

- pool `status = 'WAITING'`
- `search_expires_at > now`
- check-in의 member와 festival이 pool과 일치
- check-in `status = 'ACTIVE'`
- check-in `expires_at > now`
- 해당 시각에 active cooldown이 없음

정렬은 `entered_at ASC, id ASC`이고 기본 batch 상한은 20입니다. 이 정렬은 오래 기다린 후보를 먼저 고려하고 동일 시각에는 작은 pool ID로 결과를 결정적으로 만듭니다.

현재 후보 SQL은 active `match_group_members`를 직접 조회해 제외하지 않습니다. group 생성은 구현됐지만 이후 group lifecycle과 새 pool 진입 경로가 아직 없으므로 후보 제외 조건은 해당 단계에서 다시 확인해야 합니다.

## 7. PostgreSQL 동시성 설계

### DB row lock과 lock_token의 차이

DB row lock은 PostgreSQL이 transaction 동안 실제 row 접근을 조정하는 물리적 잠금입니다. 같은 row를 변경하려는 다른 transaction은 기다리거나, `SKIP LOCKED`를 사용했다면 그 row를 건너뜁니다. transaction이 commit 또는 rollback되면 DB row lock은 사라집니다.

`lock_token`은 DB 컬럼에 저장되는 논리적 소유권 표시입니다. transaction이 끝난 뒤에도 “어느 matching tick이 이 pool을 선점했는가”를 추적할 수 있습니다. 다른 worker의 token을 가진 row를 최종 생성하거나 release하지 않도록 검사하는 데 사용합니다.

| 구분 | DB row lock | `lock_token` |
| --- | --- | --- |
| 관리 주체 | PostgreSQL | application과 DB row |
| 수명 | transaction 종료까지 | 명시적으로 제거할 때까지 |
| 목적 | 동시에 같은 row 변경 방지 | transaction 사이의 논리적 소유권 추적 |
| stale 가능성 | transaction 종료 시 해제 | worker 장애 시 남을 수 있음 |
| 복구 | DB가 자동 해제 | stale cleanup 필요 |

`lock_token`은 row lock을 대체하지 않습니다. 최종 생성 transaction은 token만 비교하지 않고 pool row를 다시 잠급니다.

### FOR UPDATE SKIP LOCKED

Scheduler claim의 핵심은 다음 형태입니다.

```sql
SELECT pool.*
FROM match_pools pool
WHERE pool.status = 'WAITING'
ORDER BY pool.entered_at, pool.id
LIMIT :limit
FOR UPDATE OF pool SKIP LOCKED;
```

`FOR UPDATE`는 선택된 pool row를 transaction 동안 잠급니다. `SKIP LOCKED`는 다른 worker가 이미 잠근 row를 기다리지 않고 건너뛰게 합니다. 따라서 여러 backend 인스턴스가 동시에 tick을 실행해도 서로 다른 후보 묶음을 처리할 수 있습니다.

claim transaction 안에서는 선점과 `WAITING -> LOCKED`, `locked_at`, `lock_token` 기록만 수행하고 즉시 끝냅니다. scoring까지 잠금 안에서 수행하면 다른 worker가 후보를 오래 기다리게 되고 처리량이 감소하기 때문입니다.

최종 생성 시에는 pool ID를 오름차순으로 정렬해 다시 `FOR UPDATE`합니다. 여러 transaction이 여러 row를 서로 다른 순서로 잡을 때 생길 수 있는 deadlock 가능성을 줄이기 위한 순서 규칙입니다.

JVM 전역 lock이나 장시간 PostgreSQL advisory lock은 사용하지 않습니다. 최종 안전성은 각 pool row의 DB lock, 상태와 token 검증에 둡니다.

## 8. 만료와 stale lock

cleanup은 외부에서 주입된 `now`, `staleBefore`로 실행되어 경계가 결정적입니다.

| 대상 | 조건 | 결과 |
| --- | --- | --- |
| 만료 WAITING | `search_expires_at <= now` | `EXPIRED` |
| 유효 stale LOCKED | `locked_at <= staleBefore` 및 검색 유효 | `WAITING` |
| 만료 stale LOCKED | `locked_at <= staleBefore` 및 검색 만료 | `EXPIRED` |
| 최신 LOCKED | `locked_at > staleBefore` | 보존 |
| 불완전 LOCKED | `locked_at` 또는 `lock_token`이 null | 자동 복구하지 않음 |

stale lock은 worker가 선점 후 종료되어 논리적 token이 남았을 때 발생할 수 있습니다. cleanup은 이를 다음 tick에서 다시 처리할 수 있는 상태로 복구합니다.

모든 update가 현재 상태와 경계 조건을 포함하므로 같은 cleanup을 반복해도 두 번째 실행에는 추가 변경이 없습니다. 이것이 cleanup의 멱등성입니다.

기본 stale timeout은 30초이고 fixed delay는 5초입니다. 운영 환경에서는 환경변수로 조정할 수 있습니다.

## 9. TravelStyleCode scoring

현재 scoring은 V5의 `member_travel_styles.style_code`를 사용합니다.

```text
Jaccard score = |A ∩ B| / |A ∪ B| × 100
```

예를 들어 다음 두 회원이 있다고 가정합니다.

```text
A = {PHOTO, FOOD}
B = {PHOTO, ACTIVE}
```

교집합은 1개, 합집합은 3개이므로 점수는 `33.33`입니다.

- 범위: `0.00`~`100.00`
- 한쪽 또는 양쪽 집합이 비면 `0.00`
- 입력 순서와 중복은 영향 없음
- `BigDecimal` 사용
- 소수점 둘째 자리, `RoundingMode.HALF_UP`

double을 거치지 않기 때문에 부동소수점 오차에 의존하지 않습니다. V11에는 embedding schema가 있지만 현재 `TravelStyleScorer`는 이를 읽지 않습니다. 따라서 embedding이나 외부 API가 없어도 현재 matching은 동작합니다.

관련 코드: [TravelStyleScorer](../backend/src/main/java/com/survey/meetorsolo/domain/matching/scoring/TravelStyleScorer.java)

## 10. 2~4인 그룹 조합

`MatchGroupComposer`는 후보를 `(festivalId, preferredGroupSize)`로 나눈 뒤 정확한 희망 인원 크기의 모든 조합을 생성합니다.

그룹 점수는 그룹 내부 모든 2인 pair 점수의 평균입니다.

```text
3인 그룹 A, B, C
groupScore = (score(A,B) + score(A,C) + score(B,C)) / 3
```

각 회원의 `member_score`는 해당 회원과 나머지 회원 사이 pair 점수 평균입니다.

조합 정렬 순서:

1. group score 내림차순
2. 오래된 `enteredAt` 우선
3. 작은 `poolId` 우선

정렬된 조합을 앞에서부터 선택하면서 이미 배정된 member ID 또는 pool ID와 겹치는 조합을 건너뜁니다. 입력 collection 순서와 무관하게 같은 결과를 내는 결정적 greedy 방식입니다.

greedy는 현재 가장 좋은 조합부터 선택하지만 모든 그룹 점수의 총합이 최대가 되는 전역 최적해를 보장하지는 않습니다. 또한 후보가 많아질수록 `n choose k` 조합 수가 빠르게 증가합니다. 기본 batch 20은 이 비용을 제한하는 역할도 합니다.

`allowMinimumTwo`는 최초 조합에 사용하지 않습니다. round 1 전체 응답이 종료된 뒤 3명 또는 4명 목표에서 2명 이상이 수락했고 목표보다 적으며 수락자 전원이 허용한 경우에만 같은 attempt의 round 2를 생성합니다.

관련 코드:

- [MatchGroupComposer](../backend/src/main/java/com/survey/meetorsolo/domain/matching/group/MatchGroupComposer.java)
- [MatchingCandidate](../backend/src/main/java/com/survey/meetorsolo/domain/matching/group/MatchingCandidate.java)

## 11. Scheduler 실행 구조

기본 설정은 다음과 같습니다.

| 설정 | 기본값 | 환경변수 |
| --- | ---: | --- |
| enabled | `false` | `MATCHING_SCHEDULER_ENABLED` |
| fixed delay | 5초 | `MATCHING_SCHEDULER_FIXED_DELAY` |
| stale timeout | 30초 | `MATCHING_STALE_TIMEOUT` |
| proposal timeout | 30초 | `MATCHING_PROPOSAL_TIMEOUT` |
| batch size | 20 | `MATCHING_SCHEDULER_BATCH_SIZE` |

`MatchingScheduler`와 `MatchingSchedulingConfiguration`은 enabled가 명시적으로 true일 때만 생성됩니다. enabled가 없거나 false이면 scheduling infrastructure도 활성화하지 않습니다. local/test/dev/prod 어디에서도 환경변수 없이 자동 matching이 시작되지 않습니다.

`MatchingScheduler.run()`은 business logic을 포함하지 않고 `MatchingOrchestrationService.runTick()`만 호출합니다.

한 tick에서는 주입된 `Clock`을 한 번 읽어 같은 `now`를 cleanup, claim, 생성, release에 사용합니다. token은 운영에서 UUID로 생성합니다. 테스트는 고정 `Clock`과 token generator를 주입하여 시간 경계와 소유권을 결정적으로 검증합니다. 운영 코드에는 test profile 분기가 없습니다.

관련 설정: [application.yml](../backend/src/main/resources/application.yml)

## 12. 최종 재검증

claim과 최종 생성 사이에는 scoring과 grouping 시간이 있습니다. 그 사이 후보 상태나 안전 조건이 바뀔 수 있으므로 최초 조회 결과만 신뢰하지 않습니다.

최종 생성 transaction은 다음 조건을 다시 검사합니다.

| 범주 | 검증 |
| --- | --- |
| 조회 완전성 | 요청 pool 수와 잠금 조회 수 동일 |
| pool | 모두 `LOCKED`, `locked_at` 존재 |
| 소유권 | 모두 현재 실행의 동일 `lock_token` |
| 시간 | `search_expires_at > now` |
| snapshot | member, festival, 희망 인원이 조합 snapshot과 동일 |
| check-in | member·festival 일치, `ACTIVE`, `expires_at > now` |
| cooldown | 현재 active cooldown 없음 |
| block | 그룹 내부 어느 방향의 block도 없음 |
| 그룹 | 같은 festival, 같은 희망 인원, 정확히 2~4명 |
| 중복 | pool ID와 member ID 모두 고유 |

TOCTOU는 “검사한 시점(time of check)”과 “사용한 시점(time of use)” 사이에 상태가 바뀌는 문제입니다. 이 구현은 최종 사용 직전에 pool row를 잠그고 다시 검사해 TOCTOU 범위를 줄입니다.

다만 pool row만 잠그며 block/cooldown 테이블 전체를 직렬화하지는 않습니다. 최종 검사 직후 다른 transaction이 block이나 cooldown을 새로 만드는 극단적 race는 후속 보안·동시성 설계 대상입니다.

관련 코드: [MatchProposalCreationService](../backend/src/main/java/com/survey/meetorsolo/domain/matching/service/MatchProposalCreationService.java)

## 13. Attempt와 Proposal 생성

최종 검증을 통과한 한 그룹은 하나의 transaction에서 다음 row를 생성합니다.

| 테이블 | 현재 생성값 |
| --- | --- |
| `match_attempts` | `status=WAITING_RESPONSES`, `created_by=SCHEDULER`, group score |
| `match_attempt_members` | 회원별 row, `status=PROPOSED`, member score |
| `match_proposals` | `proposal_type=INITIAL_MATCH`, `proposal_round=1`, `status=SENT` |

`started_at`, proposal `sent_at`, entity의 `created_at`, `updated_at`은 같은 tick의 `now`를 사용합니다. attempt와 proposal의 `expires_at`은 모두 `now + proposalTimeout`입니다.

모든 저장이 성공하면 각 pool은 `LOCKED -> PROPOSED`로 전환되고 임시 소유권 정보인 `locked_at`, `lock_token`은 null로 제거됩니다. 이후의 업무 관계는 `match_attempt_members.pool_id`가 보존합니다.

컬럼과 제약조건의 전체 목록은 [DB 설계](11_DATABASE_DESIGN.md)와 다음 migration을 참고합니다.

- [V3 matching tables](../backend/src/main/resources/db/migration/V3__create_matching_tables.sql)
- [V10 proposal rounds](../backend/src/main/resources/db/migration/V10__add_matching_proposal_rounds.sql)

## 14. Transaction 경계

| 단계 | 경계 | 이 경계를 사용한 이유 |
| --- | --- | --- |
| cleanup | 짧은 `@Transactional` | 조건부 bulk update 원자성 |
| pool-entry claim | 짧은 `REQUIRES_NEW` | AFTER_COMMIT 문맥과 분리하고 LOCKED/token을 proposal 생성 전에 commit |
| Scheduler claim | 짧은 `@Transactional` | row lock을 오래 유지하지 않고 LOCKED 상태만 기록 |
| batch read | read-only `REQUIRES_NEW` | commit된 token 후보를 새 persistence context에서 batch 조회 |
| scoring/grouping | transaction 없음 | CPU 계산 중 DB row lock을 유지하지 않음 |
| create | 그룹별 `REQUIRES_NEW` | 그룹 단위 원자성과 실패 격리 |
| release | `REQUIRES_NEW` | 같은 token의 남은 LOCKED를 호출 transaction과 무관하게 일괄 반환 |

`REQUIRES_NEW`는 호출한 쪽에 transaction이 있더라도 새 transaction을 시작합니다. `MatchProposalCreationService`가 orchestration과 별도 Spring bean이므로 Spring proxy를 통해 이 설정이 적용됩니다. 한 그룹의 insert가 실패해도 해당 그룹만 rollback되고 다른 그룹 생성은 계속됩니다.

orchestration 전체에 transaction을 적용하면 cleanup부터 scoring까지 하나의 긴 transaction이 되고 row lock 유지 시간과 DB connection 점유가 증가합니다. 또한 한 그룹의 실패가 이미 성공한 다른 그룹까지 rollback할 수 있습니다. 그래서 orchestration은 순서만 조정하고 transaction은 각 단계 service가 소유합니다.

```mermaid
sequenceDiagram
    participant S as MatchingScheduler
    participant O as MatchingOrchestrationService
    participant C as CleanupService
    participant Q as ClaimService
    participant R as BatchReader
    participant G as Scorer/Composer
    participant P as ProposalCreationService
    participant L as ReleaseService
    participant DB as PostgreSQL

    S->>O: runTick()
    O->>C: cleanup(now, staleBefore)
    C->>DB: short transaction
    O->>Q: claim(now, batchSize, token)
    Q->>DB: FOR UPDATE SKIP LOCKED / 독립 commit
    O->>R: read(token)
    R->>DB: REQUIRES_NEW read-only batch read
    O->>G: score and compose
    loop each group
        O->>P: createInitial(group, token, now)
        P->>DB: REQUIRES_NEW / final lock and insert
    end
    O->>L: finally release(token, now)
    L->>DB: REQUIRES_NEW / short transaction
```

## 15. 실패 및 복구

### 그룹 생성 실패

attempt, members, proposals, pool 전이는 같은 `REQUIRES_NEW` transaction입니다. member insert, 일부 proposal insert 또는 pool update/flush가 실패하면 모두 rollback되고 pool은 기존 `LOCKED` 상태와 token을 유지합니다.

orchestration은 그룹별 예외를 WARN 로그로 남기고 다음 그룹을 계속 처리합니다.

### finally release

성공한 pool은 이미 `PROPOSED`이고 token이 제거되었으므로 release 대상이 아닙니다. 실패하거나 그룹에 사용되지 않아 여전히 같은 token을 가진 `LOCKED`만 다음과 같이 처리합니다.

- 검색 유효: `WAITING`
- 검색 만료: `EXPIRED`
- `locked_at`, `lock_token`: null

release는 `finally`에서 실행하므로 batch read나 grouping에서 예외가 발생해도 시도됩니다. release도 실패하면 로그를 남깁니다. 원래 예외가 있으면 release 예외를 suppressed exception으로 추가해 원래 원인을 숨기지 않습니다.

### worker 장애와 stale recovery

프로세스가 종료되어 finally가 실행되지 않으면 token이 있는 `LOCKED`가 남을 수 있습니다. 이후 cleanup이 `locked_at <= staleBefore`인 row를 유효 기간에 따라 `WAITING` 또는 `EXPIRED`로 회수합니다.

## 16. JUnit 및 Testcontainers 테스트

2026-07-22까지의 기존 검증 결과와 2026-07-23 penalty/cooldown 작업의 실행 결과는 다음과 같습니다.

- 사용자가 직접 실행한 `MatchProposalResponseServiceIntegrationTest`: 36건
- failures: 0
- errors: 0
- skipped: 0
- 실행 결과: `BUILD SUCCESSFUL`
- 별도 실행한 `domain`, `external`, `global` backend 회귀: 171건, failures/errors/skipped 0, `BUILD SUCCESSFUL`
- 전체 172건에서는 개인 `.env`의 dev SSH tunnel과 local PostgreSQL 인증값 불일치로 기존 root `contextLoads()` 1건만 환경 실패
- penalty/cooldown 정책과 기존 DB 비의존 matching 회귀 21건은 `BUILD SUCCESSFUL`
- 2026-07-23 사용자가 Windows Git Bash + Docker Desktop에서 실행한 V1~V12와 penalty/cooldown PostgreSQL targeted 통합 테스트: 55건
- targeted 통합 테스트 결과: failures 0, errors 0, skipped 0, `BUILD SUCCESSFUL`

사용자가 직접 실행한 명령은 다음과 같습니다.

```bash
cd backend
./gradlew.bat test \
  --tests "com.survey.meetorsolo.domain.matching.service.MatchProposalResponseServiceIntegrationTest" \
  --tests "com.survey.meetorsolo.domain.matching.repository.MatchPoolRepositoryIntegrationTest" \
  --rerun-tasks
```

이 targeted 테스트는 mock backend나 화면 fixture가 아니라 실제 운영 matching entity/repository/service와 PostgreSQL 16 + pgvector Testcontainer를 사용합니다.

### 테스트 클래스별 계약

| 테스트 클래스 | 건수 | 구분 | 핵심 검증 |
| --- | ---: | --- | --- |
| `MatchingSchedulerPropertiesTest` | 6 | context | YAML binding과 잘못된 설정 거부 |
| `MatchingScenarioFixtureTest` | 4 | fixture | 후보·round·timeout test 계약 |
| `MatchGroupComposerTest` | 9 | 단위 | 2~4인, pair 평균, greedy, 결정성 |
| `MatchPoolRepositoryIntegrationTest` | 13 | PostgreSQL | V1~V12, pgvector, 후보 조건, unique |
| `MatchingSchedulerTest` | 4 | context/단위 | 조건부 Scheduler와 위임 |
| `TravelStyleScorerTest` | 6 | 단위 | Jaccard 전체 계약 |
| `MatchPoolClaimServiceIntegrationTest` | 8 | PostgreSQL | requester claim, rollback, latch 동시성 |
| `MatchPoolCleanupServiceIntegrationTest` | 6 | PostgreSQL | 만료, stale, 경계, 멱등성, rollback |
| `MatchPoolReleaseServiceIntegrationTest` | 3 | PostgreSQL | token 조건, PROPOSED 보존, rollback |
| `MatchProposalCreationServiceIntegrationTest` | 28 | PostgreSQL | 최종 검증, 생성, rollback, 재실행 |
| `MatchingOrchestrationServiceIntegrationTest` | 1 | PostgreSQL | 생성 실패 후 실제 release |
| `MatchingOrchestrationServiceTest` | 3 | 단위 | 처리 순서, 실패 격리, suppressed 예외 |
| `SchedulerMatchPoolClaimServiceIntegrationTest` | 4 | PostgreSQL | batch filter, rollback, latch `SKIP LOCKED` |
| `MatchingPenaltyPolicyTest` | 4 | 단위 | round 1/2 cooldown 기간과 penalty 점수 |
| `MatchProposalResponseServiceIntegrationTest` | 42 | PostgreSQL | round 1/2 응답, penalty/cooldown, V12 unique, pool 정책, 멱등성, 동시성, rollback |

통합 테스트는 `pgvector/pgvector:pg16` Testcontainer에 Flyway V1~V12를 적용합니다. 운영 DB나 local/dev DB에 fixture를 넣지 않습니다.

### PostgreSQL trigger를 테스트에서 사용한 이유

rollback 테스트는 정상 입력만으로 발생하기 어려운 “중간 insert 성공 후 다음 SQL 실패”를 검증해야 합니다. 테스트는 DB에 임시 trigger를 설치해 member insert, 일부 proposal insert, pool update를 실패시키고 테스트 후 제거합니다. 이를 통해 운영 코드에 mock 분기나 test profile 조건을 넣지 않고 실제 transaction flush/rollback을 확인합니다.

### BUILD SUCCESSFUL이 보장하는 것

- 현재 테스트가 다루는 후보 필터와 상태 경계
- PostgreSQL row lock과 latch 기반 동시 선점
- scoring과 그룹 조합의 결정성
- 최종 재검증 거부 조건
- 그룹별 생성 원자성과 rollback
- 조건부 Scheduler configuration
- 현재 backend 기존 기능과의 회귀 없음

### 보장하지 않는 것

- 아직 작성되지 않은 frontend matching 화면 동작
- 운영 부하에서의 처리량과 지연 시간
- 프로세스·네트워크 장애의 모든 조합
- ambiguous commit 후 기존 attempt 탐색
- 최종 검사 직후 block/cooldown 동시 생성 race
- 실제 dev/prod에서 Scheduler가 활성화된 배포 상태

## 17. 현재 화면에서 테스트할 수 없는 이유

현재 matching engine은 REST API로 호출할 수 있지만 frontend matching 화면이 아직 연결되지 않았습니다.

- 매칭 신청, proposal 조회·응답, current group 조회 REST API 구현
- frontend 연결 없음
- WebSocket 상태 동기화 없음
- Scheduler 기본 `enabled=false`

따라서 브라우저에서 버튼을 눌러 전체 흐름을 확인할 수 없습니다. 현재는 JUnit과 PostgreSQL 통합 테스트 또는 DB row 조회로 다음 결과를 확인할 수 있습니다.

현재 검증은 frontend mock이 backend API로 요청을 보내는 방식이 아닙니다. 테스트가 fixture 데이터를 격리된 PostgreSQL 16 + pgvector Testcontainer에 준비한 뒤 실제 운영 `entity`, `repository`, `service`, transaction을 직접 호출합니다. Mockito는 Scheduler 진입점처럼 외부 협력 호출만 확인하는 일부 단위 테스트에서 사용하며, 인원 미달 round 2의 상태 전이와 동시성 검증은 실제 PostgreSQL 통합 테스트입니다.

- pool 상태와 token 변화
- attempt/member/proposal row
- round 1 전체 terminal 집계와 round 2 proposal 생성
- `START_WITH_CURRENT_MEMBERS`, `CANCEL_CURRENT_MEMBERS`, `TIMEOUT` 응답
- 최소 인원 group/member 확정과 귀책·비귀책 pool 처리
- score와 만료 시각
- transaction rollback 후 중간 row 부재
- worker별 중복 없는 claim

화면 테스트가 가능해지려면 다음 연결이 추가되어야 합니다.

```text
frontend 버튼
→ matching REST API/controller
→ 현재 구현된 MatchProposalResponseService
→ PostgreSQL 상태 변경
→ API 응답 또는 WebSocket 상태 이벤트
→ frontend modal/page 갱신
```

frontend가 아직 연결되지 않아 사용자가 proposal과 인원 미달 재확인을 화면에서 볼 수 없습니다. WebSocket은 최초 화면 테스트의 필수 조건이 아니며 REST polling과 `MATCHED` 이후 current group 조회로 먼저 연결한 뒤 상태 동기화 단계에서 추가할 수 있습니다.

## 18. 현재 보장 범위와 한계

### 상태 기반 멱등성

정상적인 중복 tick과 다중 worker 실행은 다음 조건으로 같은 pool의 중복 생성을 막습니다.

```text
SKIP LOCKED claim
+ final FOR UPDATE
+ status == LOCKED
+ lock_token ownership
+ atomic LOCKED -> PROPOSED
```

재실행이 성공한 pool을 다시 처리하려 하면 이미 `PROPOSED`이므로 생성 조건을 통과하지 못합니다.

### 명시적 idempotency key 부재

`match_attempts`에는 요청 단위 idempotency key가 없습니다. DB commit은 성공했지만 application이 응답을 받지 못한 ambiguous commit 상황에서 기존 attempt를 명시적 key로 찾아 반환하는 기능은 없습니다. V12는 penalty/cooldown의 원인 proposal 멱등성만 추가하므로, attempt 생성 idempotency는 완전 재매칭 정책과 후속 migration을 함께 검토해야 합니다.

### 그 밖의 한계

| 한계 | 영향 |
| --- | --- |
| block/cooldown 동시 생성 race | 최종 검사 직후 새 안전 상태 변경을 완전히 직렬화하지 못함 |
| 축제별 batch 공정성 없음 | 오래된 후보가 많은 축제가 전역 batch를 우선 점유할 수 있음 |
| 모든 조합 생성 | batch가 커질수록 조합 수 증가 |
| greedy | 결정적이지만 전역 최적 조합은 아님 |
| active group 직접 검사 없음 | group lifecycle 구현 시 후보 제외 조건 재검토 필요 |

block/cooldown race를 해결하려면 isolation level, advisory lock, 회원 단위 직렬화 또는 schema 변경의 처리량·deadlock·운영 복잡도를 함께 비교해야 합니다.

## 19. 아직 구현하지 않은 기능

| 기능 | 현재 상태 |
| --- | --- |
| proposal 수락·거절 | service와 REST API 구현 |
| proposal timeout 상태 처리 | 최초·인원 미달 service와 Scheduler 구현 및 PostgreSQL targeted 검증 완료 |
| `match_responses` 생성 | 최초 proposal round 1과 인원 미달 round 2 구현 |
| penalty/cooldown 생성 | proposal 기반 멱등성과 응답 transaction 원자 처리 구현 |
| 인원 미달 재확인 | round 1 전체 terminal 집계, round 2 응답·timeout과 최소 인원 확정 구현 |
| `allowMinimumTwo` 적용 | 최초 조합에는 미사용하고 인원 미달 round 2 진입 조건에 적용 |
| 최종 group/member 생성 | 목표 인원 전원 수락과 최소 인원 전원 진행 경로 구현 |
| matching REST API | 신청·조회·proposal action·restriction 최소 API 구현 |
| current group REST API | 인증 회원의 `CONFIRMED`/`IN_PROGRESS` group과 참여자 공개 필드 조회 구현 |
| 매칭 신청 API | 유효 체크인과 신청 제한 검증, AFTER_COMMIT trigger 구현 |
| `POOL_ENTRY` 실행 | application event 기반 동기 AFTER_COMMIT 즉시 orchestration 구현 |
| frontend | 미구현 |
| WebSocket STOMP | 설계 방향만 존재 |
| Redis | MVP 제외 |
| embedding scoring | V11 schema만 존재 |
| 외부 API scoring | 미구현 |

## 20. 다음 개발 순서

권장 후속 순서는 다음과 같습니다.

1. 개인 `.env`와 local PostgreSQL 인증값을 맞춘 뒤 root `contextLoads()`를 포함한 전체 회귀 테스트 완료
2. frontend proposal UI, 인원 미달 modal과 `MATCHED` 이후 current group 조회 연결
3. WebSocket STOMP 상태 동기화

현재 브랜치에서 먼저 할 일은 다음과 같습니다.

1. 변경 diff와 테스트 결과를 최종 확인한다.
2. 개인 `.env`와 local PostgreSQL 연결값을 맞춰 `contextLoads()`를 포함한 전체 172건을 확인한다.
3. 이 브랜치를 PR로 `dev`에 병합한다.

화면에서 직접 확인하는 것이 다음 목표라면 별도 승인 후 아래 순서로 진행합니다.

1. 매칭풀 신청·취소 API와 현재 proposal 조회 API
2. 최초 proposal 및 인원 미달 round 2 응답 API
3. frontend `MatchProposalModal`, `MatchResponseWaitingModal`, `InsufficientMembersModal`
4. API 기반 수동 시나리오 테스트
5. WebSocket STOMP 상태 동기화

penalty/cooldown은 중요한 backend 정책이지만 화면 연결의 기술적 선행 조건은 아닙니다. 다만 현재 WBS 순서를 유지하려면 세부 정책을 먼저 확정한 뒤 REST API 작업으로 넘어갑니다.

`POOL_ENTRY`는 다음 application 흐름으로 구현했습니다.

```text
pool 생성 transaction commit
→ application event
→ 즉시 matching orchestration 시도
→ 실패 시 WAITING 유지
→ Scheduler fallback
```

PostgreSQL DB trigger는 사용하지 않습니다. `match_attempts.created_by`는 trigger 경로에서 `POOL_ENTRY`, 기존 fallback Scheduler 경로에서 `SCHEDULER`를 저장합니다.

## 21. 기술 블로그 소재

이 구현을 바탕으로 다음 주제를 독립적인 글로 확장할 수 있습니다.

1. PostgreSQL `FOR UPDATE SKIP LOCKED`로 다중 worker 작업 큐 만들기
2. DB row lock과 application `lock_token`을 함께 사용한 이유
3. scoring을 transaction 밖으로 분리해 잠금 시간을 줄이는 방법
4. TOCTOU를 줄이기 위한 최종 재검증 설계
5. Spring `REQUIRES_NEW`로 그룹별 실패를 격리한 과정
6. Testcontainers와 PostgreSQL trigger로 rollback을 검증하는 방법
7. Jaccard와 `BigDecimal`로 설명 가능한 취향 점수 만들기
8. 입력 순서에 흔들리지 않는 결정적 greedy 그룹 조합
9. local/test에서 안전한 조건부 Scheduler 구성
10. 상태 기반 멱등성과 명시적 idempotency key의 차이

## 부록 A. 실제 파일 인덱스

### 운영 코드

- 설정: [`domain/matching/config`](../backend/src/main/java/com/survey/meetorsolo/domain/matching/config/)
- entity: [`domain/matching/entity`](../backend/src/main/java/com/survey/meetorsolo/domain/matching/entity/)
- group: [`domain/matching/group`](../backend/src/main/java/com/survey/meetorsolo/domain/matching/group/)
- repository: [`domain/matching/repository`](../backend/src/main/java/com/survey/meetorsolo/domain/matching/repository/)
- Scheduler: [`domain/matching/scheduler`](../backend/src/main/java/com/survey/meetorsolo/domain/matching/scheduler/)
- scoring: [`domain/matching/scoring`](../backend/src/main/java/com/survey/meetorsolo/domain/matching/scoring/)
- service: [`domain/matching/service`](../backend/src/main/java/com/survey/meetorsolo/domain/matching/service/)

### 테스트와 fixture

- matching 테스트: [`backend/src/test/.../matching`](../backend/src/test/java/com/survey/meetorsolo/domain/matching/)
- SQL fixture: [`backend/src/test/resources/fixtures`](../backend/src/test/resources/fixtures/)
- fixture 계약: [MATCHING_ENGINE_TEST_CONTRACT.md](../backend/src/test/resources/fixtures/MATCHING_ENGINE_TEST_CONTRACT.md)

### Migration과 설정

- [V3 matching tables](../backend/src/main/resources/db/migration/V3__create_matching_tables.sql)
- [V5 travel styles](../backend/src/main/resources/db/migration/V5__create_member_travel_styles.sql)
- [V10 proposal rounds](../backend/src/main/resources/db/migration/V10__add_matching_proposal_rounds.sql)
- [V11 preference embeddings](../backend/src/main/resources/db/migration/V11__add_member_preference_embeddings.sql)
- [V12 penalty/cooldown idempotency](../backend/src/main/resources/db/migration/V12__add_matching_penalty_cooldown_idempotency.sql)
- [application.yml](../backend/src/main/resources/application.yml)

## 부록 B. 용어와 상태 구분

| 용어 | 의미 |
| --- | --- |
| candidate | 현재 batch에서 그룹 조합 대상으로 읽은 pool snapshot |
| claim | `WAITING` pool을 짧게 잠그고 `LOCKED`로 선점하는 단계 |
| row lock | PostgreSQL transaction 동안 유지되는 물리적 잠금 |
| `lock_token` | transaction 사이에서 선점 worker를 표시하는 논리적 소유권 |
| stale lock | worker 장애 등으로 `LOCKED`와 token이 오래 남은 상태 |
| attempt | 한 후보 그룹의 매칭 시도 |
| attempt member | attempt에 포함된 회원과 원본 pool의 연결 |
| proposal | 각 회원에게 보낸 응답 요청 |
| group | 목표 인원 전원 수락 또는 인원 미달 round 2 전원 진행 동의로 최종 확정된 그룹 |
| deterministic | 같은 논리 입력이면 입력 순서와 무관하게 같은 결과를 내는 성질 |
| idempotency | 같은 작업을 반복해도 중복 부작용이 생기지 않는 성질 |
| TOCTOU | 검사 시점과 사용 시점 사이 상태 변경 문제 |

## 22. Matching 최소 REST API

### 22.1 구현 범위

Postman/curl에서 현재 matching engine을 직접 호출할 수 있도록 다음 endpoint를 추가했습니다.

| Method | Endpoint | 목적 |
| --- | --- | --- |
| `POST` | `/api/matching/pools` | 유효한 본인 체크인으로 60초 `WAITING` pool 생성 |
| `GET` | `/api/matching/pools/me/current` | 본인의 최신 pool과 상태 조회 |
| `GET` | `/api/matching/proposals/me/active` | 아직 만료되지 않은 본인의 최신 `SENT` proposal 조회 |
| `POST` | `/api/matching/proposals/{proposalId}/responses` | 최초 또는 인원 미달 proposal 응답 |
| `GET` | `/api/matching/me/restrictions` | 현재 cooldown과 누적 penalty score 조회 |
| `GET` | `/api/matching/groups/me/current` | 본인의 현재 확정 group과 참여자 목록 조회 |

Swagger/OpenAPI, frontend, WebSocket은 이 단계에 추가하지 않았습니다. proposal 생성 알림도 아직 없으므로 client는 active proposal API를 polling해야 합니다.

### 22.2 인증

기존 `MemberProfileController`와 같은 인증 계약을 사용합니다.

```text
access_token HttpOnly cookie
-> JwtProvider.getMemberIdFromAccessToken()
-> memberId
-> matching application service
```

요청 body, path, query parameter에서는 `memberId`를 받지 않습니다. cookie 누락, 빈 값, 서명 오류, access token 만료는 `401 UNAUTHORIZED`입니다.

현재 `SecurityConfig`에는 JWT authentication filter가 없고 Controller가 cookie를 검증합니다. 따라서 신규 endpoint의 모든 진입점은 같은 `memberId(accessToken)` 검증을 거칩니다.

### 22.3 pool 신청 계약

요청:

```http
POST /api/matching/pools
Cookie: access_token=<ACCESS_TOKEN>
Content-Type: application/json
```

```json
{
  "festivalId": 1,
  "preferredGroupSize": 2,
  "allowMinimumTwo": false,
  "tags": []
}
```

- `preferredGroupSize`: `2`~`4`
- `tags`: 현재는 반드시 빈 배열
- `searchExpiresAt`: 서버 기준 `enteredAt + 60초`
- 성공 status: `201 Created`

`TravelStyleCode`라는 공식 회원 여행 스타일 코드는 존재하지만 pool의 `tags`와 같은 계약이라는 정책은 없습니다. 또한 현재 scoring은 `member_travel_styles`를 직접 읽으며 `match_pools.tags`를 사용하지 않습니다. 임의 문자열이나 잘못된 공식 코드 재사용을 피하기 위해 이번 최소 API는 `tags` 필드를 빈 배열로만 허용하고 DB에도 `[]`를 저장합니다.

신청 service는 회원 row를 먼저 `FOR UPDATE`로 잠근 뒤 다음을 확인합니다.

- 회원 존재 및 `ACTIVE`
- 현재 active cooldown 없음
- 기존 `WAITING`, `LOCKED`, `PROPOSED` pool 없음
- 기존 active group member 상태 없음
- 요청 축제가 `ACTIVE`
- 로그인 회원과 요청 축제에 속한 `ACTIVE`, 미만료 체크인 존재

동일 회원의 동시 요청은 회원 row lock으로 직렬화하고, `uq_match_pools_member_active` partial unique index를 마지막 방어선으로 유지합니다.

성공 응답 예:

```json
{
  "success": true,
  "data": {
    "poolId": 101,
    "festivalId": 1,
    "preferredGroupSize": 2,
    "allowMinimumTwo": false,
    "tags": [],
    "status": "WAITING",
    "enteredAt": "2026-07-23T15:00:00+09:00",
    "searchExpiresAt": "2026-07-23T15:01:00+09:00"
  },
  "error": null
}
```

### 22.4 조회 계약

최신 pool:

```http
GET /api/matching/pools/me/current
Cookie: access_token=<ACCESS_TOKEN>
```

pool이 한 번도 없으면 `200 OK`와 다음 응답을 반환합니다.

```json
{"success":true,"data":null,"error":null}
```

active proposal:

```http
GET /api/matching/proposals/me/active
Cookie: access_token=<ACCESS_TOKEN>
```

조회 조건은 본인, `status=SENT`, `expires_at > now`이며 높은 round와 최신 ID를 우선합니다. active proposal이 없으면 동일하게 `200 OK`, `data:null`입니다.

```json
{
  "success": true,
  "data": {
    "proposalId": 201,
    "attemptId": 301,
    "proposalType": "INITIAL_MATCH",
    "proposalRound": 1,
    "status": "SENT",
    "targetGroupSize": 2,
    "attemptStatus": "WAITING_RESPONSES",
    "expiresAt": "2026-07-23T15:00:30+09:00"
  },
  "error": null
}
```

restriction:

```http
GET /api/matching/me/restrictions
Cookie: access_token=<ACCESS_TOKEN>
```

```json
{
  "success": true,
  "data": {
    "penaltyScore": 1,
    "cooldown": {
      "active": true,
      "reason": "TIMEOUT",
      "startsAt": "2026-07-23T15:00:30+09:00",
      "expiresAt": "2026-07-23T15:02:30+09:00",
      "remainingSeconds": 85
    }
  },
  "error": null
}
```

### 22.5 proposal action 계약

```http
POST /api/matching/proposals/{proposalId}/responses
Cookie: access_token=<ACCESS_TOKEN>
Content-Type: application/json
```

외부 action은 다음 세 값만 허용합니다.

```text
ACCEPT
REJECT
CANCEL_CURRENT_MEMBERS
```

내부 기존 service와의 매핑:

| proposal type | 외부 action | 기존 service 입력 |
| --- | --- | --- |
| `INITIAL_MATCH` | `ACCEPT` | `ACCEPTED` |
| `INITIAL_MATCH` | `REJECT` | `REJECTED` |
| `INSUFFICIENT_MEMBERS_CONFIRMATION` | `ACCEPT` | `START_WITH_CURRENT_MEMBERS` |
| `INSUFFICIENT_MEMBERS_CONFIRMATION` | `CANCEL_CURRENT_MEMBERS` | `CANCEL_CURRENT_MEMBERS` |

round 1의 `CANCEL_CURRENT_MEMBERS`, round 2의 `REJECT`는 `400 MATCHING_INVALID_REQUEST`입니다. 변환은 Controller가 아니라 `MatchProposalActionService`가 담당하고, 실제 상태 변경은 기존 `MatchProposalResponseService` transaction이 처리합니다.

동일 action 재전송은 기존 응답을 반환합니다. 최초 응답 후 다른 action으로 변경하면 `409 MATCHING_CONFLICT`입니다.

다른 회원의 proposal ID로 요청하면 proposal 존재 여부와 소유자를 노출하지 않고 `404 MATCHING_RESOURCE_NOT_FOUND`를 반환합니다.

### 22.6 오류 코드

| HTTP | code | 주요 조건 |
| ---: | --- | --- |
| `400` | `MATCHING_INVALID_REQUEST` | 비활성 회원, 유효 체크인 없음, proposal 유형/action 불일치 |
| `400` | `VALIDATION_ERROR` | 희망 인원 범위, non-empty tags, 필수 필드 오류 |
| `400` | `INVALID_INPUT_VALUE` | 알 수 없는 enum, 읽을 수 없는 JSON |
| `401` | `UNAUTHORIZED` | cookie 누락, 잘못된 JWT, 만료 JWT |
| `404` | `MATCHING_RESOURCE_NOT_FOUND` | 회원 또는 본인 소유 proposal 없음 |
| `409` | `MATCHING_CONFLICT` | active cooldown/pool/group, 종료된 proposal, 기존 응답 변경 |

내부 stack trace, 다른 회원 ID, proposal 소유자 정보는 오류 응답에 포함하지 않습니다.

## 23. 자동 테스트

### 23.1 실행 명령

Windows Git Bash + Docker Desktop 기준:

```bash
cd backend

./gradlew.bat test \
  --tests "com.survey.meetorsolo.domain.matching.controller.MatchingRestApiIntegrationTest" \
  --tests "com.survey.meetorsolo.domain.matching.service.MatchPoolEntryServiceIntegrationTest" \
  --rerun-tasks
```

기존 matching 회귀 포함:

```bash
cd backend

./gradlew.bat test \
  --tests "com.survey.meetorsolo.domain.matching.*" \
  --rerun-tasks
```

### 23.2 현재 실행 결과

2026-07-23 실행 환경별 결과:

- WSL 작업 환경에서는 `docker` 명령과 `/var/run/docker.sock`을 찾지 못해 Testcontainers initialization 단계에서 `Could not find a valid Docker environment`로 중단됐습니다. 이 결과는 application assertion이나 PostgreSQL SQL 실패가 아닙니다.
- Windows Git Bash + Docker Desktop에서 REST API와 pool 신청 통합 테스트 명령을 `--rerun-tasks`로 실행한 결과 39초에 `BUILD SUCCESSFUL`이었습니다.
- 같은 Windows 환경에서 `com.survey.meetorsolo.domain.matching.*` 전체 회귀 명령을 `--rerun-tasks`로 실행한 결과 1분 24초에 `BUILD SUCCESSFUL`이었습니다.
- PostgreSQL Testcontainers가 정상 실행됐고 빈 PostgreSQL 환경에 Flyway V1~V12를 적용한 상태에서 matching 전체 테스트가 실패 없이 완료됐습니다.

최초 전체 실행에서 발견된 두 테스트 실패는 운영 코드가 아니라 테스트 구성 문제였습니다.

- round 2 REST fixture에 attempt `9130001`의 `match_attempt_members`가 없어 첫 `ACCEPT`가 `409`였습니다. 테스트 클래스 내부에 두 수락 회원의 attempt member와 마지막 동의 조건을 준비해 첫 응답 후 attempt가 `CONFIRMED`된 상태에서도 동일 `ACCEPT` 재전송이 기존 `START_WITH_CURRENT_MEMBERS`를 반환하는지 검증했습니다.
- pool 생성 응답의 `+09:00`과 PostgreSQL 조회 응답의 `Z`가 동일 절대 시각인데도 `OffsetDateTime` record 전체 equality로 비교해 실패했습니다. 일반 필드는 정확히 비교하고 시간은 `toInstant()`로 비교하며 `Duration.between(enteredAt, searchExpiresAt)`이 정확히 60초인지 검증하도록 수정했습니다.
- 운영 matching 코드, transaction, 동시성, 멱등성 정책은 변경하지 않았습니다.

최종 상태는 PostgreSQL REST/pool 통합 테스트와 matching 전체 회귀 테스트 완료입니다. WSL에서 직접 재실행하려면 Docker Desktop의 `Settings > Resources > WSL Integration`에서 현재 배포판을 활성화해야 합니다.

PostgreSQL 통합 테스트가 검증하도록 작성된 항목:

- 유효 체크인 pool 생성과 60초 만료
- 유효/미만료 체크인 거절
- cooldown 신청 거절
- 회원 row lock 기반 동시 신청 한 건 성공
- 현재 pool과 cooldown/penalty 조회
- JWT 회원의 active round 2 proposal 조회
- 다른 회원 proposal `404`
- round 2 action 매핑과 동일 action 멱등성
- 기존 `MatchProposalResponseServiceIntegrationTest`의 응답/penalty/rollback 회귀

## 24. Postman/curl 직접 검증 절차

아래 명령의 `<...>`만 로컬 값으로 교체합니다. 실제 비밀번호와 token은 문서나 Git에 저장하지 않습니다.

### 24.1 PostgreSQL 실행

프로젝트 루트의 `.env`를 준비한 뒤:

```bash
cd /c/dev/meet-or-solo

docker compose \
  --env-file .env \
  -f docker-compose.local.yml \
  up -d postgres

docker compose \
  --env-file .env \
  -f docker-compose.local.yml \
  ps
```

PostgreSQL timezone과 Flyway 확인:

```bash
set -a
source .env
set +a

docker compose \
  --env-file .env \
  -f docker-compose.local.yml \
  exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "show timezone;" \
  -c "select version, description, success from flyway_schema_history order by installed_rank;"
```

Flyway가 아직 적용되지 않았다면 backend를 한 번 실행한 뒤 다시 확인합니다.

### 24.2 backend 실행

OAuth와 profile 암호화에 필요한 실제 값은 개인 `.env`에만 둡니다.

```text
JWT_SECRET=<LOCAL_JWT_SECRET>
PROFILE_ENCRYPTION_KEY=<LOCAL_BASE64_AES_256_KEY>
KAKAO_CLIENT_ID=<KAKAO_CLIENT_ID>
KAKAO_CLIENT_SECRET=<KAKAO_CLIENT_SECRET>
KAKAO_REDIRECT_URI=http://localhost:8080/api/auth/kakao/callback
FRONTEND_BASE_URL=http://localhost:5173
AUTH_COOKIE_SECURE=false
MATCHING_SCHEDULER_ENABLED=true
MATCHING_SCHEDULER_FIXED_DELAY=5s
MATCHING_PROPOSAL_TIMEOUT=30s
```

Git Bash:

```bash
cd /c/dev/meet-or-solo/backend

export MATCHING_SCHEDULER_ENABLED=true
export MATCHING_SCHEDULER_FIXED_DELAY=5s
export MATCHING_PROPOSAL_TIMEOUT=30s

./gradlew.bat bootRun
```

backend가 실행된 별도 terminal에서:

```bash
curl -i http://localhost:8080/api/health
```

예상 status는 `200`, 핵심 JSON은 `"status":"OK"`입니다.

### 24.3 OAuth 회원과 cookie 준비

회원 A와 B는 서로 다른 Kakao/Naver 계정이어야 합니다. 각 browser profile 또는 시크릿 창에서 다음 주소를 엽니다.

```text
http://localhost:8080/api/auth/kakao/login
```

로그인 후 Chrome DevTools에서 다음 순서로 확인합니다.

```text
Application
-> Storage
-> Cookies
-> http://localhost:8080
-> access_token
```

`HttpOnly`이므로 frontend JavaScript에서는 읽을 수 없지만 DevTools와 HTTP client cookie jar에서는 확인할 수 있습니다. 실제 token을 채팅, 문서, Git에 붙여 넣지 않습니다.

curl용 Netscape cookie file:

```bash
COOKIE_DIR="$(mktemp -d)"

printf 'localhost\tFALSE\t/\tFALSE\t0\taccess_token\t%s\n' \
  '<MEMBER_A_ACCESS_TOKEN>' > "$COOKIE_DIR/member-a.cookies"

printf 'localhost\tFALSE\t/\tFALSE\t0\taccess_token\t%s\n' \
  '<MEMBER_B_ACCESS_TOKEN>' > "$COOKIE_DIR/member-b.cookies"

chmod 600 "$COOKIE_DIR/member-a.cookies" "$COOKIE_DIR/member-b.cookies"
```

신규 OAuth 회원이 `PROFILE_REQUIRED`이면 각 cookie file을 갱신하면서 프로필을 완료합니다.

```bash
curl -i -sS \
  -b "$COOKIE_DIR/member-a.cookies" \
  -c "$COOKIE_DIR/member-a.cookies" \
  -X PUT \
  -H 'Content-Type: application/json' \
  -d '{"nickname":"테스트A","email":null,"intro":"매칭 API 검증","gender":"FEMALE","ageRange":"20S","travelStyles":["PHOTO"]}' \
  http://localhost:8080/api/members/me/profile

curl -i -sS \
  -b "$COOKIE_DIR/member-b.cookies" \
  -c "$COOKIE_DIR/member-b.cookies" \
  -X PUT \
  -H 'Content-Type: application/json' \
  -d '{"nickname":"테스트B","email":null,"intro":"매칭 API 검증","gender":"MALE","ageRange":"20S","travelStyles":["PHOTO"]}' \
  http://localhost:8080/api/members/me/profile
```

예상 status는 `200`, 핵심 값은 `"status":"ACTIVE"`입니다. 응답의 새 `Set-Cookie`를 `-c`가 같은 cookie file에 반영합니다.

회원 ID 확인:

```bash
curl -sS -b "$COOKIE_DIR/member-a.cookies" \
  http://localhost:8080/api/members/me

curl -sS -b "$COOKIE_DIR/member-b.cookies" \
  http://localhost:8080/api/members/me
```

응답의 `data.memberId`를 각각 `<MEMBER_A_ID>`, `<MEMBER_B_ID>`로 사용합니다.

Postman에서는 `localhost` cookie jar에 이름 `access_token`, Path `/`로 회원별 token을 저장합니다. 두 회원을 동시에 다룰 때는 별도 Postman environment 또는 별도 workspace/cookie 백업을 사용합니다.

### 24.4 테스트 festival과 체크인 준비 SQL

프로젝트 루트에서 회원 ID를 교체해 실행합니다.

```bash
cd /c/dev/meet-or-solo
set -a
source .env
set +a

docker compose \
  --env-file .env \
  -f docker-compose.local.yml \
  exec -T postgres \
  psql -v ON_ERROR_STOP=1 \
    -v member_a_id='<MEMBER_A_ID>' \
    -v member_b_id='<MEMBER_B_ID>' \
    -U "$POSTGRES_USER" \
    -d "$POSTGRES_DB" <<'SQL'
BEGIN;

INSERT INTO festivals(
    content_id, content_type_id, title, address,
    checkin_radius_meters, meeting_radius_meters,
    status, created_at, updated_at
) VALUES (
    'matching-rest-manual-festival',
    '15',
    '매칭 REST 수동 검증 축제',
    '테스트 주소',
    500,
    2000,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (content_id) DO UPDATE
SET status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP;

UPDATE festival_checkins
SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
WHERE member_id IN (:member_a_id, :member_b_id)
  AND festival_id = (
      SELECT id FROM festivals
      WHERE content_id = 'matching-rest-manual-festival'
  )
  AND status = 'ACTIVE';

INSERT INTO festival_checkins(
    member_id, festival_id, distance_meters, status,
    checked_in_at, expires_at, created_at, updated_at
)
SELECT
    member_id,
    (SELECT id FROM festivals WHERE content_id = 'matching-rest-manual-festival'),
    100,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP + INTERVAL '1 hour',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (VALUES (:member_a_id), (:member_b_id)) AS test_members(member_id);

COMMIT;

SELECT id, content_id, title
FROM festivals
WHERE content_id = 'matching-rest-manual-festival';

SELECT id, member_id, festival_id, status, expires_at
FROM festival_checkins
WHERE member_id IN (:member_a_id, :member_b_id)
ORDER BY member_id, id;
SQL
```

마지막 festival 조회의 `id`를 `<TEST_FESTIVAL_ID>`로 사용합니다.

### 24.5 WAITING → PROPOSED → MATCHED

두 신청을 60초 안에 연속 실행합니다.

```bash
FESTIVAL_ID='<TEST_FESTIVAL_ID>'

curl -i -sS \
  -b "$COOKIE_DIR/member-a.cookies" \
  -H 'Content-Type: application/json' \
  -d "{\"festivalId\":$FESTIVAL_ID,\"preferredGroupSize\":2,\"allowMinimumTwo\":false,\"tags\":[]}" \
  http://localhost:8080/api/matching/pools

curl -i -sS \
  -b "$COOKIE_DIR/member-b.cookies" \
  -H 'Content-Type: application/json' \
  -d "{\"festivalId\":$FESTIVAL_ID,\"preferredGroupSize\":2,\"allowMinimumTwo\":false,\"tags\":[]}" \
  http://localhost:8080/api/matching/pools
```

예상 status는 각각 `201`, 핵심 상태는 `"status":"WAITING"`입니다.

현재 pool:

```bash
curl -sS -b "$COOKIE_DIR/member-a.cookies" \
  http://localhost:8080/api/matching/pools/me/current

curl -sS -b "$COOKIE_DIR/member-b.cookies" \
  http://localhost:8080/api/matching/pools/me/current
```

Scheduler fixed delay 5초를 기다린 뒤 proposal을 조회합니다.

```bash
sleep 6

curl -sS -b "$COOKIE_DIR/member-a.cookies" \
  http://localhost:8080/api/matching/proposals/me/active

curl -sS -b "$COOKIE_DIR/member-b.cookies" \
  http://localhost:8080/api/matching/proposals/me/active
```

예상 status는 `200`, 핵심 값은 `"proposalType":"INITIAL_MATCH"`, `"proposalRound":1`, `"status":"SENT"`입니다. `data:null`이면 60초 pool 만료, Scheduler 비활성, 희망 인원 불일치를 확인합니다.

`jq`가 설치된 경우 proposal ID를 변수에 저장합니다.

```bash
PROPOSAL_A_ID="$(
  curl -sS -b "$COOKIE_DIR/member-a.cookies" \
    http://localhost:8080/api/matching/proposals/me/active |
  jq -r '.data.proposalId'
)"

PROPOSAL_B_ID="$(
  curl -sS -b "$COOKIE_DIR/member-b.cookies" \
    http://localhost:8080/api/matching/proposals/me/active |
  jq -r '.data.proposalId'
)"
```

30초 안에 두 회원 모두 수락합니다.

```bash
curl -i -sS \
  -b "$COOKIE_DIR/member-a.cookies" \
  -H 'Content-Type: application/json' \
  -d '{"action":"ACCEPT"}' \
  "http://localhost:8080/api/matching/proposals/$PROPOSAL_A_ID/responses"

curl -i -sS \
  -b "$COOKIE_DIR/member-b.cookies" \
  -H 'Content-Type: application/json' \
  -d '{"action":"ACCEPT"}' \
  "http://localhost:8080/api/matching/proposals/$PROPOSAL_B_ID/responses"
```

예상 status는 `200`입니다. 마지막 응답의 핵심 값은 `"recordedResponse":"ACCEPTED"`, `"attemptStatus":"CONFIRMED"`입니다.

```bash
curl -sS -b "$COOKIE_DIR/member-a.cookies" \
  http://localhost:8080/api/matching/pools/me/current
```

핵심 pool 상태는 `"MATCHED"`입니다.

### 24.6 REJECT → cooldown

이 절차의 각 시나리오는 독립적으로 실행합니다. 앞의 `MATCHED` 시나리오를 완료했다면 24.9 정리 SQL을 먼저 실행하고 24.4의 festival/check-in 준비부터 다시 시작하거나, 별도 회원 C/D를 준비합니다. 새 체크인과 pool을 준비한 두 회원으로 동일하게 proposal을 만든 뒤 한 회원이 거절합니다.

```bash
curl -i -sS \
  -b "$COOKIE_DIR/member-a.cookies" \
  -H 'Content-Type: application/json' \
  -d '{"action":"REJECT"}' \
  "http://localhost:8080/api/matching/proposals/$PROPOSAL_A_ID/responses"
```

round 1은 전체 terminal 집계 후 실패하므로 상대 회원도 `ACCEPT` 또는 `REJECT`로 응답해야 합니다. 이후:

```bash
curl -sS -b "$COOKIE_DIR/member-a.cookies" \
  http://localhost:8080/api/matching/me/restrictions
```

예상 핵심 값:

```json
{
  "penaltyScore": 0,
  "cooldown": {
    "active": true,
    "reason": "REJECT"
  }
}
```

round 1 거절 cooldown은 전체 terminal 집계 시각부터 30초이며 penalty score는 증가하지 않습니다.

### 24.7 TIMEOUT → penalty/cooldown

이 시나리오도 24.9 정리 후 다시 준비하거나 별도 회원을 사용합니다. 새 proposal을 받은 뒤 아무 응답도 보내지 않고 Scheduler timeout을 기다립니다.

```bash
sleep 35

curl -sS -b "$COOKIE_DIR/member-a.cookies" \
  http://localhost:8080/api/matching/me/restrictions
```

round 1 timeout의 예상 핵심 값:

```json
{
  "penaltyScore": 1,
  "cooldown": {
    "active": true,
    "reason": "TIMEOUT"
  }
}
```

기존 score가 이미 있었다면 `penaltyScore`는 기존 값에서 `+1`입니다. cooldown은 timeout 처리 시각부터 2분입니다.

### 24.8 오류 확인

cookie 없음:

```bash
curl -i http://localhost:8080/api/matching/pools/me/current
```

예상: `401`, `UNAUTHORIZED`.

non-empty tags:

```bash
curl -i -sS \
  -b "$COOKIE_DIR/member-a.cookies" \
  -H 'Content-Type: application/json' \
  -d "{\"festivalId\":$FESTIVAL_ID,\"preferredGroupSize\":2,\"allowMinimumTwo\":false,\"tags\":[\"PHOTO\"]}" \
  http://localhost:8080/api/matching/pools
```

예상: `400`, `VALIDATION_ERROR`.

round 1에서 잘못된 action:

```bash
curl -i -sS \
  -b "$COOKIE_DIR/member-a.cookies" \
  -H 'Content-Type: application/json' \
  -d '{"action":"CANCEL_CURRENT_MEMBERS"}' \
  "http://localhost:8080/api/matching/proposals/$PROPOSAL_A_ID/responses"
```

예상: `400`, `MATCHING_INVALID_REQUEST`.

### 24.9 테스트 데이터 정리

아래 SQL은 테스트 festival에 연결된 matching 데이터만 FK 역순으로 제거하고 OAuth 회원은 삭제하지 않습니다.

```bash
cd /c/dev/meet-or-solo
set -a
source .env
set +a

docker compose \
  --env-file .env \
  -f docker-compose.local.yml \
  exec -T postgres \
  psql -v ON_ERROR_STOP=1 \
    -U "$POSTGRES_USER" \
    -d "$POSTGRES_DB" <<'SQL'
BEGIN;

CREATE TEMP TABLE cleanup_attempts AS
SELECT id FROM match_attempts
WHERE festival_id = (
    SELECT id FROM festivals
    WHERE content_id = 'matching-rest-manual-festival'
);

CREATE TEMP TABLE cleanup_groups AS
SELECT id FROM match_groups
WHERE attempt_id IN (SELECT id FROM cleanup_attempts);

DELETE FROM match_penalty_events
WHERE related_attempt_id IN (SELECT id FROM cleanup_attempts)
   OR related_group_id IN (SELECT id FROM cleanup_groups);

DELETE FROM match_cooldowns
WHERE related_proposal_id IN (
    SELECT id FROM match_proposals
    WHERE attempt_id IN (SELECT id FROM cleanup_attempts)
);

DELETE FROM match_events
WHERE attempt_id IN (SELECT id FROM cleanup_attempts)
   OR group_id IN (SELECT id FROM cleanup_groups);

DELETE FROM match_responses
WHERE attempt_id IN (SELECT id FROM cleanup_attempts);

DELETE FROM match_group_members
WHERE group_id IN (SELECT id FROM cleanup_groups);

DELETE FROM match_groups
WHERE id IN (SELECT id FROM cleanup_groups);

DELETE FROM match_proposals
WHERE attempt_id IN (SELECT id FROM cleanup_attempts);

DELETE FROM match_attempt_members
WHERE attempt_id IN (SELECT id FROM cleanup_attempts);

DELETE FROM match_attempts
WHERE id IN (SELECT id FROM cleanup_attempts);

DELETE FROM match_pools
WHERE festival_id = (
    SELECT id FROM festivals
    WHERE content_id = 'matching-rest-manual-festival'
);

DELETE FROM festival_checkins
WHERE festival_id = (
    SELECT id FROM festivals
    WHERE content_id = 'matching-rest-manual-festival'
);

DELETE FROM festivals
WHERE content_id = 'matching-rest-manual-festival';

COMMIT;
SQL

rm -f "$COOKIE_DIR/member-a.cookies" "$COOKIE_DIR/member-b.cookies"
rmdir "$COOKIE_DIR"
```

## 25. 현재 한계

- frontend matching 화면이 없어 JSON으로만 확인합니다.
- WebSocket이 없어 proposal과 상태 변경을 polling해야 합니다.
- Scheduler는 기본 `false`이지만 pool 신청의 신규 matching 탐색은 AFTER_COMMIT trigger로 즉시 실행됩니다. Scheduler fallback과 시간 기반 처리를 검증하려면 명시적으로 활성화해야 합니다.
- check-in REST API가 없어 수동 검증용 `festival_checkins`는 SQL로 준비합니다.
- Swagger/OpenAPI는 이번 범위에 없습니다.
- 자유 채팅은 구현하지 않았습니다.

## 26. Pool 신청 AFTER_COMMIT 매칭 실행 trigger

### 26.1 구현 배경과 변경 전 구조

기존 `MatchingScheduler`는 기본 5초 fixed delay로 `MatchingOrchestrationService.runTick()`을 호출했습니다. 이 한 경로가 만료 `WAITING` 정리, stale `LOCKED` 복구, 전체 batch claim, 신규 매칭 탐색, proposal 생성과 미사용 lock release를 수행했습니다. 따라서 `POST /api/matching/pools`가 `WAITING` pool을 commit해도 신규 탐색은 다음 Scheduler tick까지 기다렸고 Scheduler가 비활성화된 환경에서는 시작되지 않았습니다.

기존 문서에는 다음 application 흐름이 후속 계약으로 기록되어 있었습니다.

```text
pool 생성 transaction commit
→ application event
→ 즉시 matching orchestration 시도
→ 실패 시 WAITING 유지
→ Scheduler fallback
```

이번 구현은 이 계약을 Spring application event로 연결했습니다. DB Trigger, queue table, 외부 메시지 브로커, Redis, Kafka와 `@Async` executor는 사용하지 않았습니다.

### 26.2 최종 event 구조와 처리 흐름

```text
MatchingController.enterPool
→ MatchPoolEntryService.enter
→ 회원 row lock과 기존 신청 조건 검증
→ matching repository의 유효 checkinId 조회
→ WAITING pool saveAndFlush
→ MatchingPoolEnteredEvent(poolId, memberId, festivalId) publish
→ 신청 transaction commit
→ MatchingPoolEnteredEventHandler AFTER_COMMIT
→ PoolEntryMatchingOrchestrationService.run
→ requester 중심 동일 festival claim
→ WAITING → LOCKED
→ 기존 MatchingBatchReader / MatchGroupComposer
→ requester 포함 group 선택
→ 기존 MatchProposalCreationService REQUIRES_NEW
→ attempt/member/proposal 생성
→ LOCKED → PROPOSED
→ 미사용 token lock release
```

event는 `MatchPoolEntryService`가 저장된 pool의 실제 ID를 얻은 뒤 publish합니다. handler는 다음과 같이 동기 commit listener로 등록했습니다.

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
```

원본 transaction이 rollback되면 handler는 실행되지 않습니다. 별도 thread와 in-memory queue 유실 문제를 만들지 않고 기존 단계별 transaction 경계를 그대로 사용하기 위해 동기 listener를 선택했습니다. handler는 orchestration 예외를 내부에서 catch하고 `poolId`, `memberId`, `festivalId`를 구조화된 로그 인자로 기록합니다. 따라서 pool 신청 commit 이후 trigger가 실패해도 이미 성공한 신청 응답을 실패로 되돌리지 않습니다.

`AFTER_COMMIT`은 원본 commit 완료를 뜻하지만 transaction synchronization 정리까지 끝났다는 뜻은 아닙니다. 이 시점에는 원본 EntityManager/JDBC resource가 현재 thread에 남아 있을 수 있으므로 후속 service의 기본 `REQUIRED`만으로는 실제 새 transaction을 보장할 수 없습니다. 따라서 pool-entry claim, token batch read, proposal 생성, 미사용 lock release는 각각 별도 Spring bean의 `REQUIRES_NEW` 경계로 실행합니다.

```text
pool 생성 transaction commit
→ claim REQUIRES_NEW: WAITING → LOCKED/token commit
→ batch read REQUIRES_NEW(readOnly): commit된 token 후보 조회
→ proposal create REQUIRES_NEW: attempt/member/proposal + PROPOSED commit
→ release REQUIRES_NEW: 남은 LOCKED를 WAITING/EXPIRED로 commit
```

orchestration 전체에는 transaction을 적용하지 않습니다. 그래야 claim의 logical lock이 proposal 생성 transaction에서 보이고, scoring/grouping 중 row lock이나 DB connection을 장시간 점유하지 않으며, proposal 실패 후 release도 독립적으로 복구할 수 있습니다.

### 26.3 requester pool 중심 claim

`PoolEntryMatchPoolClaimService`와 `MatchPoolRepository.findPoolEntryClaimablePoolsForUpdate`를 trigger 전용 경로로 추가했습니다. 기존 `SchedulerMatchPoolClaimService`와 전역 batch query는 변경하지 않았습니다.

trigger claim 조건은 다음과 같습니다.

- event의 `poolId`, `memberId`, `festivalId`가 requester row와 모두 일치
- requester가 `WAITING`이고 검색 시간이 남아 있음
- requester와 같은 festival, 같은 `preferred_group_size`
- 후보도 `WAITING`이고 검색 시간이 남아 있음
- pool과 check-in의 회원·축제 일치
- check-in `ACTIVE`, 미만료
- active cooldown 제외
- requester와 후보 사이 양방향 block 제외
- requester를 정렬 최우선으로 두고 기존 `entered_at`, `pool_id` 순서 유지
- `FOR UPDATE OF pool SKIP LOCKED`

조회 결과에 requester pool이 없으면 아무 row도 `LOCKED`로 전환하지 않습니다. requester가 포함된 경우에만 같은 lock token과 `lockedAt`으로 claim합니다. batch 조합 결과에서도 requester pool을 포함한 group 하나만 proposal 생성 대상으로 선택합니다. 후보 부족 시 생성 없이 finally release가 실행되어 검색 시간이 유효한 pool은 `WAITING`으로 돌아갑니다.

### 26.4 trigger와 Scheduler 책임 분리

| 실행 경로 | 책임 | attempt `created_by` |
| --- | --- | --- |
| pool-entry trigger | 신규 신청 직후 requester 중심 같은 축제 즉시 탐색 | `POOL_ENTRY` |
| `MatchingScheduler` | 누락·실패 후 남은 `WAITING` fallback, 전체 batch matching, `WAITING` 만료, stale `LOCKED` 복구, 미사용 lock release | `SCHEDULER` |
| `MatchProposalTimeoutScheduler` | proposal과 attempt timeout 처리 | 해당 없음 |

기존 5초 실행 주기와 Scheduler 활성화 설정은 변경하지 않았습니다. `MatchAttempt.initial(...)`은 기존 overload에서 계속 `SCHEDULER`를 기본값으로 사용하고, trigger용 proposal 생성 overload만 `POOL_ENTRY`를 명시합니다. V3 constraint가 두 값을 이미 허용하므로 migration은 추가하지 않았습니다.

### 26.5 동시성과 중복 실행 방지

- 동일 회원의 빠른 중복 신청은 회원 row `FOR UPDATE`와 active pool partial unique index로 방어합니다.
- 동일 event 재전달은 requester의 `WAITING` 조건으로 방어합니다. 최초 실행이 `LOCKED` 또는 `PROPOSED`로 전환하면 다음 실행은 claim하지 못합니다.
- 두 pool-entry trigger 또는 trigger와 Scheduler가 동시에 실행되면 `FOR UPDATE SKIP LOCKED`가 이미 선점된 pool을 건너뜁니다.
- proposal 생성 전 기존 코드가 pool ID 오름차순으로 다시 잠그고 `LOCKED`, `lockToken`, 만료, check-in, cooldown, 모든 pair block을 재검증합니다.
- proposal 생성은 기존 그룹별 `REQUIRES_NEW` transaction을 유지합니다. 일부 insert 또는 pool flush가 실패하면 attempt/member/proposal/pool 전이가 함께 rollback됩니다.
- orchestration의 finally release는 같은 token으로 남은 `LOCKED`만 `WAITING` 또는 `EXPIRED`로 전환합니다.
- process 중단으로 release하지 못한 `LOCKED`는 기존 Scheduler stale cleanup이 복구합니다.

event ID나 별도 deduplication table은 추가하지 않았으며 PostgreSQL row lock, pool 상태와 token을 이용한 기존 상태 기반 멱등성을 유지합니다.

### 26.6 체크인 기능과의 경계

체크인 기능은 다른 담당 범위입니다. 이번 작업은 `domain/checkin/**`, 체크인 Controller/Service/Entity/Repository, `festival_checkins` migration, GPS 정책과 체크인 API 계약을 수정하지 않았습니다.

신청 단계는 기존 `MatchPoolRepository.findValidCheckinId(memberId, festivalId, now)`를 사용합니다. trigger claim과 proposal 생성 직전 검증도 기존 `festival_checkins` SQL 조건을 유지합니다. 자동 테스트는 실제 PostgreSQL의 `festival_checkins` fixture를 사용해 유효·비활성 check-in을 검증했습니다.

### 26.7 변경 파일

신규 운영 파일:

- `domain/matching/event/MatchingPoolEnteredEvent.java`
- `domain/matching/event/MatchingPoolEnteredEventHandler.java`
- `domain/matching/service/PoolEntryMatchPoolClaimService.java`
- `domain/matching/service/PoolEntryMatchingOrchestrationService.java`

수정 운영 파일:

- `MatchPoolEntryService.java`: pool 저장 후 event publish
- `MatchPoolRepository.java`: trigger 전용 requester 중심 claim query
- `MatchAttempt.java`: `POOL_ENTRY` 생성 주체 지원
- `MatchProposalCreationService.java`: 생성 주체를 명시하는 overload

신규 테스트 파일:

- `MatchingPoolEnteredEventHandlerTest.java`
- `MatchingPoolEnteredEventIntegrationTest.java`
- `PoolEntryMatchingOrchestrationServiceTest.java`
- `PoolEntryMatchingOrchestrationServiceIntegrationTest.java`

### 26.8 실제 테스트 명령과 결과

신규 focused 테스트:

```bash
cd backend
PROFILE_ENCRYPTION_KEY=<TEST_BASE64_KEY> ./gradlew.bat test \
  --tests "com.survey.meetorsolo.domain.matching.event.*" \
  --tests "com.survey.meetorsolo.domain.matching.service.PoolEntryMatchingOrchestrationServiceTest" \
  --tests "com.survey.meetorsolo.domain.matching.service.PoolEntryMatchingOrchestrationServiceIntegrationTest" \
  --rerun-tasks
```

- 최종 15건 통과
- `BUILD SUCCESSFUL` 25초
- commit 전 handler 미실행, commit 이후 event payload 전달, rollback 시 미실행 검증
- listener 실패 후 commit된 `WAITING` pool 유지 검증
- requester 포함, 같은 축제 제한, 후보 부족, `POOL_ENTRY`, 중복 event 검증
- 두 trigger 동시 실행과 trigger/Scheduler 동시 실행의 attempt 단일 생성 검증
- 비활성 check-in 제외와 proposal 생성 DB 실패 rollback/release 검증
- Scheduler attempt `created_by=SCHEDULER` 회귀 검증

matching 전체:

```bash
cd backend
PROFILE_ENCRYPTION_KEY=<TEST_BASE64_KEY> ./gradlew.bat test \
  --tests "com.survey.meetorsolo.domain.matching.*" \
  --rerun-tasks
```

- 최종 코드 기준 179건 통과
- `BUILD SUCCESSFUL` 1분 20초

backend 전체:

```bash
cd backend
PROFILE_ENCRYPTION_KEY=<TEST_BASE64_KEY> ./gradlew.bat test --rerun-tasks
```

- 최초 실행은 local PostgreSQL `localhost:5432` 부재로 기존 `MeetOrSoloApplicationTests.contextLoads()` 1건 실패, 나머지 215건 통과
- 고유 이름의 임시 `pgvector/pgvector:pg16` local PostgreSQL을 실행한 뒤 재실행
- 최종 코드 기준 218건 통과
- `BUILD SUCCESSFUL` 1분 49초
- 임시 컨테이너는 검증 후 중지했고 `--rm` 설정으로 제거

backend build:

```bash
cd backend
PROFILE_ENCRYPTION_KEY=<TEST_BASE64_KEY> ./gradlew.bat build
```

- 최종 재실행 `BUILD SUCCESSFUL` 9초
- `bootJar`, `jar`, `assemble`, `check`, `build` 완료

### 26.9 남은 제한사항

- frontend matching 화면과 WebSocket 상태 동기화는 아직 없습니다.
- 체크인 API는 다른 담당 범위이며 수동 검증에서는 기존 `festival_checkins` fixture가 필요합니다.
- application event는 process 내부 신호이므로 commit 직후 process가 종료되는 극단적인 경우 event 자체를 영속 재처리하지 못합니다. 남은 `WAITING` pool은 기존 Scheduler fallback이 보정합니다.
- Scheduler가 기본 비활성인 환경에서 commit 직후 process 장애까지 발생하면 자동 fallback도 실행되지 않습니다. 운영 환경에서는 시간 기반 timeout·복구를 위해 Scheduler 활성화가 필요합니다.
- block/cooldown 최종 검증 직후 다른 transaction이 안전 상태를 변경하는 기존 race 한계는 이번 범위에서 바꾸지 않았습니다.

### 26.10 AFTER_COMMIT transaction 버그 수정 검증

기존 구현은 proposal 생성만 `REQUIRES_NEW`였고 pool-entry claim, batch read, release는 기본 `REQUIRED`였습니다. 실제 dev 로그에서 release modifying query의 JPA flush가 `TransactionRequiredException: no transaction is in progress`로 실패했고, claim도 proposal 생성 전에 별도 commit되지 않아 두 pool이 `WAITING`에 머물며 attempt/proposal이 생성되지 않았습니다.

수정 후 다음 자동 검증은 실제 dev DB가 아니라 모두 `pgvector/pgvector:pg16` PostgreSQL Testcontainers에서 실행했습니다.

- focused event/orchestration/release 20건: failures 0, errors 0, `BUILD SUCCESSFUL` 59초
- matching 전체 192건: failures 0, errors 0, `BUILD SUCCESSFUL` 1분 47초
- backend 전체 231건: failures 0, errors 0, `BUILD SUCCESSFUL` 1분 56초
- backend build: `BUILD SUCCESSFUL` 4초

실제 `MatchPoolEntryService.enter()`를 외부 `TransactionTemplate`에서 호출해 commit한 통합 테스트는 첫 회원 신청 후 `WAITING`, attempt/proposal 0건, lock 정보 없음과 두 번째 회원 신청 commit 직후 양쪽 `PROPOSED`, `POOL_ENTRY` attempt 1건, 회원별 proposal 1건을 검증합니다. 별도 transaction 검증은 claim의 `LOCKED/lockToken`과 release 결과가 외부 transaction rollback과 무관하게 commit되는지 확인합니다.

기존 proposal insert 실패 test trigger 검증은 attempt/member/proposal/pool 전이의 부분 저장이 없고 동일 token의 pool이 `WAITING`으로 release되며 stale lock이 남지 않는 것을 유지합니다. 두 pool-entry trigger 동시 실행과 trigger/Scheduler 동시 실행에서도 attempt/member/proposal 단일 생성을 유지합니다.

dev DB 수동 재검증은 회원 `2`, `27`과 축제 `144`를 사용했습니다. 두 회원 모두 유효한 `ACTIVE` 체크인과 희망 인원 2명 조건이었고, 서로 다른 일반/시크릿 브라우저에서 확인했습니다.

- 첫 회원 신청 후 `WAITING`
- 두 번째 회원 신청 후 양쪽 proposal
- 양쪽 수락 후 동일한 2인 `MATCHED` 화면
- 참여자 `테스트`, `dev카테` 표시
- 확정 화면 캡처 확인
- `TransactionRequiredException` 재발 없음

이 문제는 WebSocket 전달이나 frontend polling 문제가 아니라 Spring `AFTER_COMMIT` 이후 backend DB transaction 경계 문제입니다.

## 27. 확정 group 조회와 frontend 최종 결과 계약

### 27.1 책임 분리

| 구성 요소 | 책임 |
| --- | --- |
| `MatchingController` | `access_token` cookie에서 인증 회원 ID 식별, query service 호출, `ApiResponse` 반환 |
| `MatchGroupQueryService` | active group 단일성, 참여자 수와 본인 포함 여부 검증, response DTO 구성 |
| `MatchGroupRepository` | `match_group_members.member_id`를 기준으로 active group 조회 |
| `MatchGroupMemberRepository` | 같은 group의 active 참여자와 `members` 공개 필드를 한 번의 join query로 조회 |
| `MatchGroupResponse` | group 결과 계약 |
| `MatchGroupMemberResponse` | 참여자 최소 공개 계약 |

기존 `MatchingQueryService`는 pool, proposal, restriction 조회를 담당합니다. 최종 group과 참여자 aggregate 조회는 별도 `MatchGroupQueryService`로 분리해 기존 책임을 넓히지 않았습니다. Entity는 Controller 응답으로 직접 반환하지 않습니다.

### 27.2 `MATCHED` 이후 조회 흐름

```text
proposal action 응답의 attemptStatus == CONFIRMED
또는 pool status == MATCHED
→ frontend가 GET /api/matching/groups/me/current 호출
→ access_token에서 memberId 식별
→ 회원이 참여한 CONFIRMED/IN_PROGRESS group 조회
→ group 참여자와 공개 프로필을 일괄 조회
→ 최종 group DTO 수신
→ MatchingResultPage 또는 후속 MatchRoomPage 상태 구성
```

proposal 응답에 `groupId`를 추가하지 않습니다. frontend는 `MATCHED` 또는 `CONFIRMED` 상태를 확인한 뒤 독립적인 current group API를 기준 계약으로 사용합니다.

### 27.3 HTTP 계약

```http
GET /api/matching/groups/me/current
Cookie: access_token=<ACCESS_TOKEN>
```

`memberId`나 `groupId`를 path, query parameter, request body로 받지 않습니다. 다른 회원이나 임의 group을 직접 지정하는 조회 API도 제공하지 않습니다.

성공 응답 예:

```json
{
  "success": true,
  "data": {
    "groupId": 10,
    "festivalId": 1,
    "status": "CONFIRMED",
    "confirmedMemberCount": 2,
    "confirmedAt": "2026-07-27T12:30:00+09:00",
    "members": [
      {
        "memberId": 1,
        "nickname": "member-a",
        "profileImageUrl": null
      },
      {
        "memberId": 2,
        "nickname": "member-b",
        "profileImageUrl": "https://example.com/member-b.png"
      }
    ]
  },
  "error": null
}
```

현재 group이 없으면 이력 부재를 오류로 보지 않고 다음처럼 반환합니다.

```json
{"success":true,"data":null,"error":null}
```

HTTP status는 `200 OK`입니다. 인증 cookie가 없거나 유효하지 않으면 기존 정책대로 `401 UNAUTHORIZED`입니다.

### 27.4 active group과 참여자 판정

group 상태:

```text
CONFIRMED
IN_PROGRESS
```

group member 상태:

```text
JOINED
ARRIVAL_TIME_SELECTED
ARRIVED
```

`COMPLETED`, `CANCELLED` group과 `CANCELLED`, `NO_SHOW`, `LEFT` member 이력은 current 결과에서 제외합니다. 현재 schema의 `uq_match_group_members_member_active` partial unique index가 한 회원의 active group member 중복을 차단합니다. Service도 조회 결과가 두 건 이상이면 조용히 하나를 선택하지 않고 `409 MATCHING_CONFLICT`로 처리합니다.

참여자는 `match_group_members.id ASC`로 정렬합니다. 이 순서는 입력이나 JPA collection 순서와 무관하게 결정적입니다. `confirmedMemberCount`는 실제 응답 `members.size()`로 구성하되, DB의 `match_groups.confirmed_member_count`와 다르거나 로그인 회원이 목록에 없으면 데이터 정합성 오류로 응답을 중단합니다.

### 27.5 참여자 공개 범위

공개 필드는 다음 세 가지뿐입니다.

- `memberId`
- `nickname`
- `profileImageUrl`

`profileImageUrl`은 `members.profile_image_url` 값이 없거나 blank이면 `null`입니다. private Object Storage의 `profile_image_object_key`는 이번 API에서 노출하지 않으며, 다른 참여자의 private object를 중계하는 별도 권한 API도 이번 범위에 추가하지 않았습니다.

이메일, OAuth 식별자, 전화번호, 성별, 연령대, 여행 스타일, 자기소개, 체크인 위치, GPS, penalty, cooldown은 반환하지 않습니다.

### 27.6 조회 성능과 정합성

active group 조회 1회와 참여자/profile join 조회 1회를 사용합니다. 참여자별 `Member` lazy loading을 반복하지 않아 N+1 query가 발생하지 않습니다.

이번 조회는 기존 `match_groups.attempt_id` unique constraint와 group member unique/partial unique index를 그대로 사용합니다. 신규 migration은 추가하지 않았습니다.

### 27.7 테스트와 build 결과

실행 환경은 Windows Java 17, Docker Desktop, `pgvector/pgvector:pg16` Testcontainers입니다.

focused 단위/Controller:

```bash
gradlew.bat test \
  --tests com.survey.meetorsolo.domain.matching.controller.MatchingControllerTest \
  --tests com.survey.meetorsolo.domain.matching.service.MatchGroupQueryServiceTest
```

- 16건 통과, `BUILD SUCCESSFUL` 17초

PostgreSQL focused:

```bash
gradlew.bat test \
  --tests com.survey.meetorsolo.domain.matching.controller.MatchingRestApiIntegrationTest \
  --tests com.survey.meetorsolo.domain.matching.service.MatchProposalResponseServiceIntegrationTest \
  --rerun-tasks
```

- 49건 통과, `BUILD SUCCESSFUL` 51초
- 목표 인원 확정과 round 2 최소 인원 확정 후 참여자별 동일 group 조회
- 동일 members 목록, 결정적 정렬, profile null/URL 매핑
- 비참여자와 `COMPLETED`/`CANCELLED` group의 `data:null`
- 중복 proposal 응답 후 단일 group과 `attempt_id` unique 결과
- 마지막 두 회원 동시 수락 후 동일 group
- ACCEPT/timeout race에서 최종 `CONFIRMED`인 경우에만 group 노출

matching 전체:

```bash
PROFILE_ENCRYPTION_KEY=<TEST_BASE64_KEY> gradlew.bat test \
  --tests "com.survey.meetorsolo.domain.matching.*" \
  --rerun-tasks
```

- 190건 통과, `BUILD SUCCESSFUL` 1분 33초

backend 전체:

```bash
PROFILE_ENCRYPTION_KEY=<TEST_BASE64_KEY> gradlew.bat test --rerun-tasks
```

- 개인 `.env`의 dev SSH tunnel이 없는 첫 실행은 기존 `contextLoads()` 1건만 실패하고 나머지 228건 통과
- 격리된 일회성 PostgreSQL을 사용한 최종 실행 229건 통과
- 최종 `BUILD SUCCESSFUL` 2분 8초

backend build:

```bash
PROFILE_ENCRYPTION_KEY=<TEST_BASE64_KEY> gradlew.bat build
```

- `bootJar`, `jar`, `assemble`, `check`, `build` 완료
- `BUILD SUCCESSFUL` 9초

### 27.8 제외 범위와 남은 제한사항

- `domain/checkin/**`, `festival_checkins` schema와 GPS 정책은 수정하지 않음
- pool-entry trigger, `MatchingPoolEnteredEvent`, handler와 orchestration은 수정하지 않음
- `MatchingScheduler`, `MatchProposalTimeoutScheduler`와 기존 동시성/timeout 책임은 수정하지 않음
- proposal response 상태 전이 transaction, penalty/cooldown 정책은 수정하지 않음
- frontend 코드는 수정하지 않음
- meeting point, 도착 상태 변경 API와 WebSocket STOMP는 구현하지 않음
- 다른 참여자의 private Object Storage 업로드 이미지를 제공하는 권한 기반 image endpoint는 후속 범위

## 28. frontend matching REST 연동

### 28.1 API와 nullable 응답 계약

`frontend/src/api/matching.ts`는 다음 API를 한 곳에서 호출합니다.

- `POST /api/matching/pools`
- `GET /api/matching/pools/me/current`
- `GET /api/matching/proposals/me/active`
- `POST /api/matching/proposals/{proposalId}/responses`
- `GET /api/matching/me/restrictions`
- `GET /api/matching/groups/me/current`

current pool, active proposal, current group의 `200 OK`, `data:null`은 오류가 아니라 현재 데이터가 없는 정상 결과입니다. 공통 client의 기존 non-null 호출 계약은 유지하고, 조회 API만 `apiClientNullable`을 사용합니다. 모든 요청은 `credentials: 'include'`를 유지하며, `ApiClientError`는 HTTP status와 backend `error.code`, `message`, `fields`를 보존합니다. `401` login redirect도 기존 정책을 유지합니다.

proposal action은 `ACCEPT`, `REJECT`, `CANCEL_CURRENT_MEMBERS` 세 값만 선언합니다. round 2의 “현재 인원으로 시작”은 `ACCEPT`, 취소는 `CANCEL_CURRENT_MEMBERS`를 전송합니다.

### 28.2 새로고침 상태 복원 규칙

단일 `/matching` route는 네 조회 결과로 화면 상태를 파생합니다.

1. current group이 있으면 `MATCHED`
2. active proposal이 있으면 `INITIAL_MATCH`와 `INSUFFICIENT_MEMBERS_CONFIRMATION`을 round 1/2 화면으로 구분
3. current pool이 `WAITING` 또는 `LOCKED`이면 같은 탐색 화면 상태
4. pool이 `PROPOSED` 또는 `MATCHED`지만 proposal/group이 아직 없으면 `RESPONSE_PENDING`
5. pool이 `CANCELLED` 또는 `EXPIRED`이면 terminal 화면
6. active 상태가 없고 restriction의 cooldown이 active이면 `COOLDOWN`
7. 모두 없으면 `IDLE`

이 규칙은 client timer로 서버 상태를 확정하지 않습니다. `useCountdown`은 서버의 `searchExpiresAt`, proposal `expiresAt`, cooldown `expiresAt` 표시만 담당하며 0이 되면 서버를 즉시 다시 조회합니다. `MATCHED` 화면은 current group의 `confirmedMemberCount`, `confirmedAt`, `members`를 사용하고 참여자에서는 `memberId`, `nickname`, `profileImageUrl`만 소비합니다.

### 28.3 polling과 화면 보존

`WAITING`, `LOCKED`, proposal, `RESPONSE_PENDING`은 2초, active cooldown은 5초 간격으로 polling합니다. 연속 network 오류는 2초부터 최대 30초까지 backoff하며 `ErrorCard`를 표시합니다. 동일 조회는 하나의 in-flight Promise로 합치고, mutation 중복 제출을 막습니다.

document가 hidden이면 polling timer를 해제합니다. visible 복귀 시 즉시 조회하며, unmount 시 query/mutation `AbortController`와 timer/listener를 정리합니다. `MATCHED`, cooldown 없는 `CANCELLED`/`EXPIRED`, `IDLE`은 polling하지 않습니다.

기존 `IdleForm`, `SearchingCard`, `ProposalCard`, `ResponsePendingCard`, `ConfirmedCard`, `CancelledCard`, `ErrorCard`와 coral/teal/ink/sand 색상, radius, shadow, 타이포그래피, 모바일 max-width 레이아웃은 유지했습니다. demo chip, client 상태 생성 timer, mock participant/session은 제거했습니다.

### 28.4 festival/check-in 경계

매칭 신청의 `festivalId`는 다음 순서로만 결정합니다.

1. `location.state.festivalId`
2. 개발 build에서만 `VITE_DEV_FESTIVAL_ID`
3. 둘 다 없으면 신청 버튼 비활성화와 체크인 필요 안내

운영 코드에 `festivalId=1`을 두지 않았습니다. 실제 festival 상세/체크인 화면이 `location.state`를 전달하는 연결과 check-in API는 후속 담당 범위입니다. 이번 작업에서 backend, `domain/checkin/**`, meeting point, WebSocket STOMP는 수정하거나 구현하지 않았습니다.

### 28.5 frontend 검증 결과

```bash
npx tsc --noEmit
```

- 성공

```bash
npx vitest run \
  src/api/apiClient.test.ts \
  src/api/matching.test.ts \
  src/hooks/useMatchingSession.test.ts \
  src/pages/MatchingConditionPage.test.ts
```

- 4 files, 25 tests 통과
- nullable 조회, error 보존, cookie/401/AbortError, proposal action, 상태 우선순위, polling 간격/backoff, festivalId 결정 검증

```bash
npm test
```

- 전체 37건 중 36건 통과
- 기존 `src/utils/nickname.test.ts`의 “열두글자를넘는닉네임” fixture가 실제로 10자여서 길이 오류를 기대한 1건 실패
- 이번 matching integration 테스트 25건은 모두 통과

```bash
npm run build
```

- Windows 설치 의존성 환경에서 성공, 1,616 modules transformed
- WSL에서는 기존 `node_modules`에 Linux Rollup optional binary가 없어 Vitest/Vite 시작 전에 실패하며 소스 또는 TypeScript 오류는 아님

신규 package 의존성이나 `package-lock.json` semantic 변경은 없습니다.

## 29. WebSocket STOMP 매칭 상태 변경 알림

### 29.1 상태 책임

```text
PostgreSQL = 최종 상태
REST = 최초 진입, 새로고침, 재접속 후 상태 복원
WebSocket = 상태 변경 즉시 알림
```

STOMP payload는 화면에 적용할 proposal, pool, group 전체 데이터를 포함하지
않습니다. `MATCHING_STATE_CHANGED`, 변경 이유와 발생 시각만 전달하며 frontend는
수신 직후 기존 matching REST 조회 네 개를 다시 실행합니다. 이벤트 유실, 중복,
순서 역전이 있어도 REST 결과가 화면의 최종 상태입니다.

### 29.2 인증과 destination

- handshake endpoint: `/ws`
- 인증: `access_token` HttpOnly cookie
- server destination: `convertAndSendToUser(memberId, "/queue/matching", payload)`
- client subscription: `/user/queue/matching`
- client STOMP `SEND`: 허용하지 않음

handshake interceptor가 JWT를 검증하고 회원 ID 기반 Principal을 만듭니다. client는
member ID를 header나 destination에 넣지 않으며 다른 회원의 queue를 지정할 수
없습니다.

### 29.3 transaction 경계

proposal 생성과 응답 service는 같은 DB transaction 안에서
`MatchingStateChangedEvent`를 발행합니다. `MatchingStateChangedEventHandler`는
`AFTER_COMMIT`에서만 실제 STOMP 메시지를 전송합니다. rollback된 DB 상태는
알림으로 전달되지 않습니다.

알림 reason:

- `MATCH_PROPOSED`
- `MATCH_ACCEPTED`
- `MATCH_REJECTED`
- `MATCH_TIMEOUT`
- `MATCH_INSUFFICIENT_MEMBERS`
- `MATCH_CONFIRMED`

같은 attempt의 참여 회원에게 알림을 보내며 각 client는 본인 REST 상태를 다시
조회합니다. 동일 응답 멱등성 경로는 새 DB 변경이 없으므로 중복 알림을 발행하지
않습니다.

### 29.4 frontend와 proxy

frontend는 `@stomp/stompjs` native WebSocket client를 사용해 현재 origin의 `/ws`에
연결합니다. local에서는 `ws://localhost:5173/ws`를 Vite가 backend
`http://localhost:8080`으로 `ws: true` proxy합니다. dev에서는 nginx가 `/ws`
Upgrade 요청을 backend로 전달합니다. HTTPS 운영 origin에서는 자동으로 `wss`를
사용합니다.

연결 성공과 재접속 성공 시 즉시 REST refresh를 수행하고 상태 알림 수신 시에도
같은 refresh를 호출합니다. 기존 in-flight Promise 병합으로 동시 알림의 중복
조회를 줄입니다. WebSocket 연결 실패는 화면의 REST 오류로 취급하지 않으며 기존
active 2초, cooldown 5초 polling과 network backoff를 fallback으로 유지합니다.

### 29.5 제외 범위

- Redis와 외부 message broker
- SockJS fallback
- 자유 채팅과 client message endpoint
- WebSocket payload를 최종 상태로 사용하는 client cache
- 신규 Flyway migration

### 29.6 검증 결과

- backend WebSocket 인증, 허용 구독, client `SEND` 거절과 회원별 알림 focused 6건 통과
- `pgvector/pgvector:pg16` Testcontainers matching 전체 193건, failures/errors/skipped 0
- root context test도 `pgvector/pgvector:pg16` Testcontainer로 격리한 backend 전체 237건, failures/errors/skipped 0
- backend build 성공
- frontend TypeScript 검사 성공
- matching WebSocket을 포함한 frontend 전체 39건 통과
- frontend production build 성공, 1,619 modules transformed

### 29.7 두 브라우저 dev 수동 검증 결과

- 양쪽 `/ws` 연결 및 `/user/queue/matching` 구독 성공
- 양쪽 pool 진입 후 proposal 화면 전환 성공
- A 수락 후 A는 `RESPONSE_PENDING`, B는 proposal 유지
- B 수락 후 양쪽 `MATCHED` 화면 전환 성공
- 양쪽에서 동일한 확정 group 확인
- 새로고침 후 `MATCHED` 상태 복원 성공
- WebSocket 재연결 후 REST 상태 복원 성공

### 29.8 별도 Frontend 후속 작업

- terminal pool 상태에서 `다시 시도`가 신규 신청 화면으로 돌아가지 않는 문제가 남아 있다.
- 이 문제는 완료된 기능이 아니며, 이번 WebSocket STOMP 작업에서는 수정하지 않고 별도 Frontend 후속 작업으로 관리한다.
- WebSocket STOMP는 자유 채팅이 아닌 매칭 상태 동기화 전용이다.
- Redis, Flyway, 자유 채팅 관련 변경은 이번 구현 범위에서 제외했다.

## 30. terminal pool 재신청 화면 전환

### 30.1 서버 상태와 로컬 retry form 책임 분리

`GET /api/matching/pools/me/current`는 이력 보존을 위해 본인의 가장 최신 pool을
반환합니다. 최신 pool이 `CANCELLED` 또는 `EXPIRED`이면 단순 REST refresh만으로는
신청 전 화면인 `IDLE`이 되지 않습니다.

frontend는 이 terminal 서버 상태를 `IDLE`로 변환하지 않습니다. PostgreSQL과 REST
응답은 계속 최종 상태이며, 사용자가 `다시 신청하기`를 선택한 경우에만 같은
terminal pool 위에 일시적인 로컬 retry form 모드를 표시합니다.

```text
server state: CANCELLED 또는 EXPIRED
+ retrySourcePoolId가 같은 terminal pool ID
→ 신규 신청 조건 form 표시
```

이 모드는 browser memory에만 존재합니다. 새로고침이나 새 mount에서는 사라지며,
기존 REST 복원 우선순위에 따라 최신 terminal 카드 또는 더 우선하는 서버 화면을
다시 표시합니다.

### 30.2 `retrySourcePoolId` 상태 전환

retry form 진입은 다음 조건을 모두 만족할 때만 허용합니다.

- 현재 UI 상태가 `CANCELLED` 또는 `EXPIRED`
- current pool이 존재함
- active cooldown이 아님
- mutation 제출 중이 아님

진입 시 현재 terminal pool의 ID를 `retrySourcePoolId`로 보관합니다. REST refresh,
WebSocket 연결·재연결 또는 상태 변경 알림에 따른 refresh가 같은 terminal pool을
반환하면 retry form을 유지합니다.

다음 상태가 확인되면 retry 모드를 즉시 해제하고 서버 상태를 우선합니다.

- current group 존재로 `MATCHED`
- active proposal 존재
- 새 pool의 `WAITING` 또는 `LOCKED`
- pool `PROPOSED`/`MATCHED`에 따른 `RESPONSE_PENDING`
- `retrySourcePoolId`와 다른 최신 pool
- active cooldown

따라서 다른 browser tab 또는 다른 요청이 새 pool을 만든 경우에도 오래된 terminal
pool의 로컬 form이 최신 서버 상태를 가리지 않습니다. WebSocket payload를 화면
상태로 직접 적용하지 않고 기존 REST refresh를 사용하는 계약과 polling 주기,
network backoff fallback은 변경하지 않았습니다.

### 30.3 재신청 조건과 `festivalId`

retry form은 기존 신청 전 form을 재사용해 희망 인원 `2`/`3`/`4`와
`allowMinimumTwo`를 다시 선택할 수 있게 합니다.

재신청 `festivalId`는 다음 순서로 결정합니다.

1. `location.state.festivalId`
2. retry 대상 terminal pool의 `festivalId`
3. 개발 build의 `VITE_DEV_FESTIVAL_ID`

사용자가 `자동 매칭 신청`을 누르면 기존 `POST /api/matching/pools`를 호출합니다.
성공 응답의 새 pool은 즉시 `WAITING` 또는 `LOCKED`로 반영하고 retry 모드를
해제한 뒤 REST refresh로 최종 상태를 다시 확인합니다. POST 실패 시에는 terminal
서버 상태, retry form과 사용자가 선택한 조건을 유지합니다. active cooldown은
retry form 진입뿐 아니라 POST 제출 경계에서도 다시 차단합니다.

### 30.4 이력 보존과 제외 범위

재신청은 기존 terminal pool을 되살리거나 상태를 덮어쓰지 않고 신규 pool row를
생성하는 기존 backend 계약을 사용합니다. 따라서 기존 pool, attempt, proposal,
response와 group 이력은 그대로 보존되고 current pool 조회는 더 큰 ID의 신규
pool을 반환합니다.

이번 수정에서 다음 항목은 변경하지 않았습니다.

- backend REST API와 matching service
- PostgreSQL schema와 Flyway migration
- Redis
- WebSocket STOMP 인증, destination과 알림 payload
- polling 주기와 fallback
- 자유 채팅
- frontend package 의존성

### 30.5 자동 검증

- frontend focused 5 files, 47 tests 통과
- frontend 전체 8 files, 59 tests 통과
- `npx tsc --noEmit` 성공
- frontend production build 성공, 1,619 modules transformed
- PWA service worker 생성 성공
- `CANCELLED`/`EXPIRED` form 전환, 동일 terminal refresh 유지, 다른 pool·proposal·group 우선 검증
- cooldown 진입·제출 차단, terminal pool `festivalId` 재사용과 옵션 전달 검증
- POST 성공 후 `WAITING`/`LOCKED` 즉시 반영과 POST 실패 후 form·선택값 보존 검증
- `MATCHED` 재신청 UI 미표시, 새 mount REST terminal 복원 검증
- 기존 WebSocket refresh, `401`, network error와 `AbortError` 회귀 검증

### 30.6 두 브라우저 dev 화면 수동 검증

dev DB의 festival `144`, member `2`, `27`과 유효한 `ACTIVE` check-in을 사용했습니다.
기존 matching 이력은 삭제하지 않고 유지했습니다.

- `CANCELLED` terminal 화면 표시
- `다시 신청하기` 클릭 후 신청 form 전환
- 희망 인원과 최소 2명 진행 옵션 변경
- DevTools fetch 없이 `자동 매칭 신청`으로 신규 pool 생성
- 신청 직후 `WAITING` 화면 전환
- 새로고침 후 새 pool 상태 복원
- 두 브라우저 신청 후 proposal 전환
- A 수락 후 A는 `RESPONSE_PENDING`, B는 proposal 유지
- B 수락 후 양쪽 `MATCHED` 전환
- `MATCHED`에서 재신청 UI 미표시
- retry form 상태에서 새로고침 시 서버 terminal 카드 복원

## 31. 읽기 전용 MatchRoomPage

### 31.1 current group 응답 확장 계약

기존 endpoint와 인증 방식은 변경하지 않습니다.

```text
GET /api/matching/groups/me/current
Cookie: access_token=<HttpOnly JWT>
```

`memberId`, `groupId`를 path, query, body로 받지 않으며 로그인 회원이 참여한
current group만 조회합니다. current group이 없으면 `200 OK`,
`{"success":true,"data":null,"error":null}`을 반환합니다.

응답 예:

```json
{
  "success": true,
  "data": {
    "groupId": 10,
    "festivalId": 1,
    "status": "CONFIRMED",
    "confirmedMemberCount": 2,
    "confirmedAt": "2026-07-27T12:30:00+09:00",
    "festival": {
      "festivalId": 1,
      "title": "강원 여름 축제",
      "address": "강원특별자치도",
      "eventStartDate": "2026-07-27",
      "eventEndDate": "2026-07-29"
    },
    "members": [
      {
        "memberId": 1,
        "nickname": "member-a",
        "profileImageUrl": null,
        "status": "JOINED"
      }
    ]
  },
  "error": null
}
```

기존 `groupId`, `festivalId`, `status`, `confirmedMemberCount`, `confirmedAt`,
`members`는 유지했습니다. `festival` summary와 member `status`만
추가했습니다. 날짜가 없는 축제는 `eventStartDate`, `eventEndDate`가
`null`일 수 있고 주소도 nullable입니다.

### 31.2 active 범위와 공개 정보

active group:

```text
CONFIRMED
IN_PROGRESS
```

active member:

```text
JOINED
ARRIVAL_TIME_SELECTED
ARRIVED
```

종료 group과 inactive member는 제외합니다. active group이 여러 건이거나 저장된
`confirmed_member_count`와 응답 참여자 수가 다르거나 로그인 회원이 참여자에
없으면 `409 MATCHING_CONFLICT`입니다. 참여자는
`match_group_members.id ASC`로 결정적 정렬합니다.

공개 범위:

- festival: ID, 제목, 주소, 행사 시작일, 행사 종료일
- member: ID, nickname, 공개 profile image URL, 참여 상태

이메일, OAuth 식별자, GPS, 성별, 연령대, 여행 스타일, 자기소개,
penalty/cooldown, private Object Storage key와 도착 기능의 세부 시각은
반환하지 않습니다.

active group과 festival은 하나의 native projection query로 조회하고, active
member와 공개 profile은 하나의 projection query로 조회합니다. 참여자 수와
관계없이 총 2개 query 구조이며 member entity lazy loading을 사용하지 않습니다.
기존 `festivals`와 matching schema만 사용했고 신규 Flyway migration은 없습니다.

### 31.3 route와 사용자 동선

frontend route는 `/match-room`이며 URL에 `groupId`가 없습니다.

```text
/matching MATCHED 카드
  -> 사용자가 "상태방 들어가기" 클릭
  -> /match-room
  -> current group REST 복원
```

`MATCH_CONFIRMED` 직후 자동 이동하지 않습니다. 사용자가 확정 결과를 확인한 뒤
버튼으로 이동합니다. `/match-room` 새로고침과 직접 URL 접근도 같은 REST
복원 경로를 사용합니다. current group이 `null`이면 `/matching`으로 replace
이동합니다.

읽기 전용 화면은 확정 안내, 확정 시각·인원, group 상태 문구, 축제 summary와
멤버 공개 정보만 표시합니다. `CONFIRMED`는 `만남 준비 중`,
`IN_PROGRESS`는 `미팅 진행 중`으로 표시합니다. loading과 API 오류를 별도로
표시하고 오류에는 `다시 시도` 버튼을 제공합니다.

### 31.4 WebSocket REST refresh와 polling

MatchRoomPage는 pool/proposal/restriction까지 조회하는 `useMatchingSession`을
재사용하지 않고 current group 전용 `useMatchRoom`을 사용합니다.

```text
PostgreSQL = 최종 상태
current group REST = 화면 복원
/user/queue/matching = refresh trigger
5초 polling = WebSocket 미연결·장애 fallback
```

최초 mount, `/ws` 연결 성공, 재연결 성공과 정상 상태 변경 알림 수신 시 current
group REST를 다시 조회합니다. STOMP payload는 최종 화면 데이터로 사용하지
않습니다. 연결 성공 시 fallback timer를 해제하고 연결 종료·오류 시 polling을
재개합니다. unmount에서는 timer, STOMP subscription/client와 진행 중
`AbortController`를 정리합니다.

신규 `/topic/match-groups/{groupId}` 구독, client `SEND`, Redis, 외부 broker,
SockJS와 자유 채팅은 추가하지 않았습니다.

### 31.5 인증·인가 경계

- REST와 WebSocket 모두 기존 `access_token` HttpOnly cookie를 사용합니다.
- client가 회원 ID 또는 group ID를 지정하지 않습니다.
- 다른 group 직접 조회 endpoint와 frontend route를 제공하지 않습니다.
- 공개 summary 밖의 개인정보와 위치정보를 응답하지 않습니다.
- 잘못된 WebSocket payload는 기존 parser에서 무시하며 REST refresh를 유도하지 않습니다.

### 31.6 자동 검증

frontend focused:

```bash
npx tsc --noEmit
npx vitest run \
  src/api/matching.test.ts \
  src/api/matchingWebSocket.test.ts \
  src/hooks/useMatchRoom.test.ts \
  src/pages/MatchRoomPage.test.ts \
  src/pages/MatchingConditionPage.test.ts
```

- 5 files, 31 tests 통과
- loading, group/festival/member 렌더링과 두 group 상태 문구 검증
- null redirect 판정, API 오류와 재시도 검증
- 최초 REST 복원, 연결·재연결, 정상 알림 refresh 검증
- 잘못된 payload 무시 회귀, 장애 polling과 stop cleanup 검증
- 상태방 버튼 이동 handler와 자동 이동 부재 검증
- 자유 text input/textarea 부재 검증

frontend 전체:

```bash
npm test
npx tsc --noEmit
npm run build
```

- 전체 10 files, 70 tests 통과
- TypeScript 검사 성공
- production build 성공, 1,621 modules transformed
- PWA service worker 생성 성공

backend에는 Controller/service/PostgreSQL 통합 테스트의 응답 기대값과 정합성
검증을 보강했습니다. 다만 현재 WSL에는 Linux Java가 없고 Windows Java 실행도
WSL interop의 `UtilBindVsockAnyPort` 오류로 실패해 아래 명령은 실행하지
못했습니다.

```text
focused unit/controller tests: 미실행
focused PostgreSQL integration tests: 미실행
matching 전체 tests: 미실행
backend 전체 tests: 미실행
backend build: 미실행
```

PostgreSQL 통합 테스트 구성은 기존 `pgvector/pgvector:pg16` Testcontainers를
그대로 사용하며 H2로 대체하지 않았습니다.

### 31.7 두 브라우저 수동 검증 절차

이 작업 환경에서는 dev runtime과 두 로그인 session을 준비하지 않아 수동
검증을 실행하지 않았습니다. 다음 순서로 확인합니다.

1. 서로 다른 두 회원을 같은 축제·같은 조건으로 신청해 동일 group을 확정한다.
2. 양쪽 `/matching`에서 `MATCHED` 카드가 유지되고 자동 이동하지 않는지 확인한다.
3. 양쪽에서 `상태방 들어가기`를 눌러 `/match-room`으로 이동한다.
4. 양쪽의 group ID, 축제 summary, 확정 인원과 정렬된 멤버가 같은지 확인한다.
5. 한쪽을 새로고침해 current group REST로 같은 화면이 복원되는지 확인한다.
6. 다른 쪽에서 `/match-room`을 직접 입력해 같은 화면이 복원되는지 확인한다.
7. DevTools Network offline/online 또는 WebSocket 연결 종료 후 재연결하여 REST가 다시 조회되는지 확인한다.
8. active group이 없는 회원으로 `/match-room`에 접근해 `/matching`으로 replace 이동하는지 확인한다.
9. `/match-room/{groupId}`와 `/api/matching/groups/{groupId}` 같은 임의 조회 경로가 없는지 확인한다.
10. text input, 메시지 목록, client STOMP `SEND`, group topic 구독이 없는지 확인한다.

### 31.8 제외 범위와 남은 제한사항

- 자유 채팅, 자유 text input과 메시지 목록
- meeting point와 지도
- 도착 시간 선택, 도착 완료와 도착 상세 시각
- 취소, 신고, 긴급 도움, 안전 리마인드, 평가와 사진 공유
- group topic과 client STOMP `SEND`
- Redis, 외부 message broker와 SockJS
- matching engine 상태 전이와 terminal retry 계약 변경
- Flyway migration 추가 또는 기존 migration 수정
- backend 자동 검증과 두 브라우저 dev 수동 검증은 Java/runtime 가능한 환경에서 후속 실행 필요

## 32. MatchRoomPage 도착 예정 시간 선택

### 32.1 REST 계약

```text
PUT /api/matching/groups/me/current/arrival-time
Cookie: access_token=<HttpOnly JWT>
Content-Type: application/json
```

request:

```json
{
  "arrivalMinutes": 10
}
```

허용값은 기존 DB CHECK와 같은 `0`, `5`, `10`, `20`, `30`입니다.
`arrivalMinutes` 누락과 그 밖의 값은 validation 오류입니다. request path,
query, body에 `memberId`, `groupId`를 받지 않고 로그인 회원의 current active
group과 group member를 서버에서 찾습니다.

성공 응답은 갱신된 `MatchGroupResponse` 전체 snapshot입니다. member 공개
필드에는 다음 두 항목이 추가됩니다.

- `arrivalMinutes`: 선택 전에는 `null`, 선택 후 `0|5|10|20|30`
- `arrivalTimeSelectedAt`: 선택 전에는 `null`, 선택·변경 commit 시각

같은 active group 참여자에게만 기존 current group 조회를 통해 공개하며 GPS,
원본 위치, 이메일, OAuth 식별자와 Secret은 포함하지 않습니다.

### 32.2 상태 전이와 오류 경계

```text
JOINED
  -> ARRIVAL_TIME_SELECTED

ARRIVAL_TIME_SELECTED
  -> ARRIVAL_TIME_SELECTED (다른 허용값으로 변경)
```

아래 상태에서는 변경을 거절하고 내부 group/member 존재 여부를 구분해
노출하지 않는 `MATCHING_CONFLICT`를 사용합니다.

- member `ARRIVED`, `CANCELLED`, `NO_SHOW`, `LEFT`
- group `COMPLETED`, `CANCELLED`
- active group 부재 또는 다중 active group
- 로그인 회원의 group member 부재

`ARRIVED`의 도착 완료 상태는 읽기 전용 표시만 하며 이번 API가 `arrived_at`을
변경하지 않습니다.

### 32.3 transaction과 lock 순서

도착 예정 시간 변경은 하나의 Spring transaction입니다.

```text
1. 로그인 회원 기준 active match_groups row 조회 및 FOR UPDATE
2. group_id + 로그인 member_id 기준 match_group_members row FOR UPDATE
3. 잠금 획득 후 group/member 상태 재검증
4. 멱등 여부 판정
5. member 상태, arrival_minutes, arrival_time_selected_at 갱신 및 flush
6. match_events 저장 및 flush
7. active group member ID 조회
8. MatchingStateChangedEvent publish
9. current group snapshot 조회
10. transaction commit
11. AFTER_COMMIT STOMP fan-out
```

잠금 순서는 `group row -> group member row`입니다. 향후 도착 완료 기능도 같은
순서를 사용해야 arrival 선택과 도착 완료 경쟁에서 교착 가능성을 줄이고,
member 상태를 잠금 후 다시 검증할 수 있습니다. matching engine의 attempt,
proposal, pool transaction과 terminal retry transaction은 변경하지 않았습니다.

### 32.4 멱등성과 동시성

member가 이미 `ARRIVAL_TIME_SELECTED`이고 저장된 `arrival_minutes`가 request와
같으면 갱신된 current group snapshot을 반환하되 다음 작업은 수행하지 않습니다.

- member update와 선택 시각 갱신
- `match_events` insert
- `MatchingStateChangedEvent` publish
- WebSocket 알림

다른 값은 member row lock 아래에서 한 번 변경하고 event 한 건을 추가합니다.
동일 회원의 동시 요청은 같은 group/member row lock으로 직렬화되며 commit 순서의
최종 `arrival_minutes`와 event ID 순서의 마지막 payload가 일치합니다.

member update 또는 `match_events` insert가 실패하면 transaction 전체가
rollback됩니다. `@TransactionalEventListener(AFTER_COMMIT)` handler는 commit된
transaction에만 반응하므로 rollback 시 STOMP 알림도 없습니다.

### 32.5 match_events

기존 허용 event type을 사용합니다.

```text
event_type = ARRIVAL_TIME_SELECTED
group_id = current group ID
attempt_id = current group attempt ID
member_id = 로그인 회원 ID
```

payload:

```json
{
  "arrivalMinutes": 10
}
```

payload에는 token, GPS, 원본 위치, 이메일, OAuth 식별자와 Secret을 저장하지
않습니다. 기존 V3 schema의 JSONB와 event type CHECK를 그대로 사용하며 Flyway
migration을 추가하거나 수정하지 않았습니다.

### 32.6 AFTER_COMMIT WebSocket fan-out

실제 값이 변경된 transaction만 active group 참여 회원 ID 목록으로
`MatchingStateChangedEvent`를 발행합니다.

```text
reason = ARRIVAL_TIME_SELECTED
destination = /user/queue/matching
```

기존 `MatchingStateChangedEventHandler`가 commit 후 회원별
`/queue/matching`으로 알립니다. 다른 group 회원은 ID 목록에 포함하지 않습니다.
동일 값 멱등 요청과 rollback transaction에는 application event를 발행하지
않거나 AFTER_COMMIT 단계가 없으므로 추가 알림이 없습니다.

신규 group topic, client STOMP `SEND`, Redis, 외부 broker와 SockJS는 없습니다.
알림 payload는 최종 화면 데이터가 아니라 current group REST refresh
trigger입니다.

### 32.7 frontend 복원과 사용자 동선

MatchRoomPage의 `몇 분 후 도착하나요?` 제한형 panel에서 다음 선택지를
제공합니다.

- `지금 도착` → `0`
- `5분` → `5`
- `10분` → `10`
- `20분` → `20`
- `30분` → `30`

mutation 중에는 추가 선택을 막고 동일 in-flight Promise를 재사용합니다. 성공
응답 snapshot을 즉시 현재 화면에 반영하고, 실패하면 기존 group snapshot을
유지한 채 오류 안내를 표시해 재선택할 수 있게 합니다.

member 행 표시:

```text
JOINED                         -> 도착 시간 미정
ARRIVAL_TIME_SELECTED + 0      -> 곧 도착 예정
ARRIVAL_TIME_SELECTED + N      -> N분 후 도착 예정
ARRIVED                        -> 도착 완료
```

다른 browser의 변경은 기존 `/user/queue/matching` 알림 수신 후 current group
REST refresh로 반영합니다. WebSocket 장애 중에는 기존 5초 polling fallback을
유지합니다. 최초 mount, 직접 URL, 새로고침과 재연결 복원 계약도 변경하지
않았습니다.

자유 text input, 메시지 목록, 채팅 형태 UX와 `도착했어요` 버튼은 없습니다.

### 32.8 자동 테스트

frontend focused:

```bash
npx tsc --noEmit
npx vitest run \
  src/api/matching.test.ts \
  src/api/matchingWebSocket.test.ts \
  src/hooks/useMatchRoom.test.ts \
  src/pages/MatchRoomPage.test.ts \
  src/pages/MatchingConditionPage.test.ts
```

- 5 files, 43 tests 통과
- 0/5/10/20/30 API contract와 선택 panel 검증
- mutation 중복 제출 병합, 성공 snapshot 반영, 실패 snapshot 보존과 재시도 검증
- member 도착 상태 문구, WebSocket refresh, polling fallback과 cleanup 회귀
- 직접 URL/null redirect 판정, `/matching` MATCHED와 terminal retry 회귀
- 자유 text input과 도착 완료 버튼 부재 검증

frontend 전체:

```bash
npm test
npx tsc --noEmit
npm run build
```

- 전체 10 files, 81 tests 통과
- TypeScript 검사 성공
- production build 성공, 1,621 modules transformed
- PWA service worker 생성 성공

backend에는 다음 테스트를 작성했습니다.

- Controller 인증, 누락/허용 외 validation, 모든 허용값과 snapshot contract
- service lock 순서, 멱등, ARRIVED와 리소스 은닉
- `pgvector/pgvector:pg16` Testcontainers의 상태 전이, 변경, 멱등 event,
  concurrent serialization, member/event failure rollback
- 실제 commit 후 active member fan-out, 멱등/rollback 알림 부재
- 기존 WebSocket handler의 `ARRIVAL_TIME_SELECTED` reason

하지만 현재 WSL에는 Linux Java와 Docker가 없어서 backend focused,
PostgreSQL integration, WebSocket focused, matching 전체, backend 전체와
build를 실행하지 못했습니다. 직전 31절의 MatchRoomPage backend 테스트도 같은
환경 제약으로 여전히 미검증입니다. H2로 대체하지 않았습니다.

### 32.9 두 브라우저 수동 검증

현재 작업 환경에는 dev runtime과 두 로그인 session이 없어 실행하지
못했습니다. 다음 절차를 후속 실행합니다.

1. A/B 두 회원을 같은 group으로 확정하고 양쪽 MatchRoomPage에 진입한다.
2. A가 10분을 선택하고 A 화면에 즉시 `10분 후 도착 예정`이 표시되는지 확인한다.
3. B가 `/user/queue/matching` 알림 후 REST refresh로 A의 10분을 표시하는지 확인한다.
4. A가 5분으로 변경하고 양쪽에서 최신 5분을 표시하는지 확인한다.
5. A가 같은 5분을 반복 선택한 뒤 `match_events`와 알림이 증가하지 않는지 확인한다.
6. B가 `지금 도착`을 선택하고 양쪽에 `곧 도착 예정`이 표시되는지 확인한다.
7. 양쪽 새로고침과 `/match-room` 직접 접근 후 상태가 복원되는지 확인한다.
8. WebSocket 연결을 끊고 한쪽 값을 변경해 5초 polling fallback으로 복원되는지 확인한다.
9. request와 URL에 임의 `memberId`, `groupId` 조작 경로가 없는지 확인한다.
10. 채팅, group topic, client `SEND`, 도착 완료 기능이 없는지 확인한다.

### 32.10 제외 범위와 제한사항

- 도착 완료와 `arrived_at` 변경
- 자유 채팅, 자유 text input과 메시지 목록
- meeting point, 지도와 Kakao Maps
- 취소, 신고, 긴급 도움, 평가, 사진 공유와 no-show penalty
- group topic, client STOMP `SEND`
- Redis, 외부 broker와 SockJS
- matching engine와 terminal retry transaction 변경
- Flyway migration 추가 또는 기존 migration 수정
- backend 자동 검증과 두 브라우저 dev 수동 검증은 Java/Docker/dev runtime 가능한 환경에서 후속 실행 필요

## 33. MatchRoomPage 도착 완료

### 33.1 Windows 선행 검증과 REST 계약

Windows PowerShell, Azul Java 17.0.15, Docker Desktop과 기존
`pgvector/pgvector:pg16` Testcontainers로 직전 backend 미검증을 해소했습니다.
timestamp native projection, 미정의 route 500 변환, Mockito fixture와 Windows
SQL fixture encoding 결함을 수정한 뒤 gate를 통과했습니다.

```text
PUT /api/matching/groups/me/current/arrival
Cookie: access_token=<HttpOnly JWT>
body 없음
```

회원/group 식별자를 입력받지 않고 인증 회원의 current active group member만
변경합니다. 응답에는 `startedAt`, `currentMemberId`, member `arrivedAt`을
추가한 전체 snapshot을 반환합니다.

### 33.2 상태 전이, transaction과 lock

`JOINED`와 `ARRIVAL_TIME_SELECTED`는 `ARRIVED`로 전환하며 기존 도착 예정 분과
선택 시각은 유지합니다. 첫 도착이면 같은 transaction에서
`CONFIRMED -> IN_PROGRESS`와 `started_at`을 한 번만 처리합니다.

```text
group row FOR UPDATE
-> group 전체 member row ID 오름차순 FOR UPDATE
-> 잠금 후 상태 재검증
-> member/group 변경
-> MEMBER_ARRIVED 저장과 application event 발행
-> commit
-> AFTER_COMMIT STOMP fan-out
```

`ARRIVED -> ARRIVED` 반복은 기존 snapshot을 반환하고 `arrivedAt`, `startedAt`,
event와 알림을 변경하지 않습니다. 마지막 유효 회원 도착에서는 group을
`COMPLETED`로 전환하고 최초 `completed_at`, 유효 member `COMPLETED`, 단일
`MATCH_COMPLETED`를 함께 기록합니다. 완료 후 반복 도착은 최근 완료 snapshot을
반환하되 새 active group이 있으면 새 group을 우선합니다.

### 33.3 WebSocket, frontend와 검증

실제 변경 commit 뒤 active member 전원의 기존 `/user/queue/matching`에
`MEMBER_ARRIVED` reason을 전송합니다. Frontend는 payload를 직접 적용하지 않고
current group REST로 복원하며 WebSocket 장애 시 기존 polling을 유지합니다.

MatchRoomPage는 본인이 JOINED 또는 ARRIVAL_TIME_SELECTED일 때 확인 panel을
표시합니다. 성공 snapshot 전에는 optimistic하게 ARRIVED로 표시하지 않고,
실패하면 기존 snapshot을 보존합니다. `arrivedAt`은 기존 KST formatter를
사용합니다.

자동 검증은 focused, PostgreSQL arrival/arrival-time, WebSocket, matching 전체
206건, backend build, frontend focused 42건·전체 83건, TypeScript와 production
build가 통과했고 backend 전체 260건과 최종 build도 성공했습니다. 두 브라우저 dev 검증은 준비된 로그인 session이 없어
미실행이며 후속으로 예정 시간 전파, 첫 도착 IN_PROGRESS/startedAt, 반복 event
불변, 두 번째 도착 후 COMPLETED 미전환과 polling 복원을 확인해야 합니다.

### 33.4 제외 범위

- 자유 채팅, meeting point와 지도
- COMPLETED 전환
- 취소, 신고, 긴급 도움, 평가, 사진 공유와 no-show penalty
- group topic, client STOMP SEND, Redis와 외부 broker

## 34. 도착 완료 동시성·rollback 검증

### 34.1 실제 PostgreSQL 동시 도착

Windows PowerShell, Azul Java 17.0.15, Docker Desktop과 기존
`pgvector/pgvector:pg16` Testcontainers를 사용했습니다. sleep 대신
`CountDownLatch`로 두 thread를 동시에 시작하고 각 `Future`를 10초 timeout으로
회수하여 deadlock과 lock timeout도 테스트 실패로 처리했습니다. 각 service
호출은 Spring proxy를 통해 별도 transaction으로 실행됩니다.

서로 다른 A/B가 동시에 도착해도 두 요청이 완료되고 최종 snapshot에서 양쪽이
ARRIVED입니다. 회원별 `MEMBER_ARRIVED`는 정확히 1건이고 group은 IN_PROGRESS,
startedAt은 최초 값으로 유지되며 confirmedAt과 confirmedMemberCount는 바뀌지
않습니다. A/B가 다시 조회한 current group의 status, startedAt, confirmedAt과
정렬된 member snapshot은 동일합니다. active member count는 확정 인원과 같고
group은 COMPLETED로 전환되지 않습니다.

이는 운영 repository의 고정 lock 순서를 실제 DB에서 검증합니다.

```text
group row FOR UPDATE
-> 로그인 회원 group member row FOR UPDATE
```

### 34.2 동일 회원 동시 멱등 요청

같은 회원의 두 동시 요청도 모두 성공합니다. 첫 transaction만 ARRIVED,
arrivedAt, IN_PROGRESS, startedAt과 event를 만들고 두 번째 transaction은
group/member lock 뒤 ARRIVED를 확인해 snapshot만 반환합니다. 두 응답과 최종
snapshot의 arrivedAt/startedAt이 같고 `MEMBER_ARRIVED`와 active 회원별 fan-out은
각각 한 번뿐임을 검증했습니다.

### 34.3 failure rollback

운영 migration을 수정하지 않고 테스트 안에서 다음 PostgreSQL trigger를
생성하고 `@AfterEach`에서 trigger/function을 제거합니다.

- `match_group_members BEFORE UPDATE`: member 상태와 arrivedAt, group 상태와 startedAt, event 전체 rollback
- `match_groups BEFORE UPDATE`: 먼저 flush된 member 변경까지 rollback, group CONFIRMED/startedAt null 유지
- `match_events BEFORE INSERT`: member/group 변경 rollback, event 없음

각 실패 뒤 current group REST용 query snapshot도 변경 전 CONFIRMED/JOINED로
복원되며 `SimpMessagingTemplate` 호출이 없음을 확인했습니다. application
event와 실제 STOMP 사이의 `AFTER_COMMIT` 경계 때문에 rollback 상태는 전송되지
않습니다. 이미 IN_PROGRESS인 group은 group update 실패 trigger가 있어도
member 도착이 성공하므로 startedAt을 다시 쓰는 불필요한 update가 없음을
검증했습니다.

### 34.4 WebSocket fan-out

실제 MEMBER_ARRIVED commit마다 active group 두 회원 모두에게
`/queue/matching`, reason `MEMBER_ARRIVED`가 전달되고 fixture의 다른 회원에게는
전달되지 않습니다. 서로 다른 두 회원의 실제 변경은 수신자별 2회, 동일 회원
동시 멱등 요청은 수신자별 1회입니다. rollback은 0회이며 기존
ARRIVAL_TIME_SELECTED fan-out도 focused 회귀에 포함했습니다.

신규 group topic, client SEND, Redis, 외부 broker와 SockJS는 없습니다.

### 34.5 실행 명령과 결과

```powershell
.\gradlew.bat test --tests "com.survey.meetorsolo.domain.matching.service.MatchArrivalTimeServiceIntegrationTest" --rerun-tasks
.\gradlew.bat test --tests "com.survey.meetorsolo.domain.matching.*" --rerun-tasks
.\gradlew.bat test --rerun-tasks
.\gradlew.bat build

npm test -- --run
npx tsc --noEmit
npm run build
```

- arrival PostgreSQL integration: 13건 성공
- matching 전체: 212건 성공
- backend 전체: 266건 성공
- backend build 성공
- frontend 전체: 10 files, 83건 성공
- TypeScript와 production build 성공

### 34.6 두 브라우저 검증과 남은 범위

기존 수동 검증 이력의 festival `144`, member `2`, `27`은 확인했지만 현재
Windows에서 8080/5173 dev runtime이 실행 중이지 않고 준비된 두 로그인 session을
식별·제어할 수 없어 수동 검증은 실행하지 않았습니다. 기존 DB를 삭제하거나
보정하지 않았습니다.

후속 수동 검증은 동일 group/축제/member 확인, A 예정 시간의 B 전파, A 도착의
양쪽 ARRIVED·IN_PROGRESS·startedAt 복원, A 반복 요청 event/알림 불변, B 도착
후에도 COMPLETED 미전환, 새로고침, WebSocket 차단 polling, 재연결 REST 복원,
임의 ID route와 채팅/group topic/client SEND 부재를 확인합니다.

운영 코드 결함은 발견되지 않았습니다. meeting point, COMPLETED, 취소·신고·평가,
채팅과 외부 broker는 계속 제외합니다.

## 35. MatchRoomPage 시스템 이벤트 타임라인

### 35.1 실제 event 생성 조사와 REST 계약

조사 전 운영 코드는 `ARRIVAL_TIME_SELECTED`를 `{"arrivalMinutes": N}`,
`MEMBER_ARRIVED`를 빈 object payload로 저장했습니다. `MATCH_CONFIRMED`는 DB CHECK와
WebSocket reason에만 존재하고 audit row를 저장하지 않아, group/member 확정과
같은 transaction에서 actor 없는 빈 object event를 저장하도록 보강했습니다.
`MATCH_CANCELLED`, `MEMBER_CANCELLED`, `SAFETY_REMINDER`는 생성 기능이 없어 이번
응답과 화면에서 제외합니다.

```text
GET /api/matching/groups/me/current/events
Cookie: access_token=<HttpOnly JWT>
path/query/body 식별자 없음
```

current active group이 없으면 `200 data:null`, 있으면 다음 안전한 DTO를
반환합니다.

```json
{
  "events": [
    {
      "eventId": 101,
      "type": "ARRIVAL_TIME_SELECTED",
      "occurredAt": "2026-07-30T09:01:00+09:00",
      "actor": {"memberId": 1, "nickname": "민수"},
      "arrivalMinutes": 10
    }
  ]
}
```

raw payload, GPS, 이메일, OAuth 식별자, token, penalty/cooldown과 Secret은
포함하지 않습니다.

### 35.2 인가, actor와 N+1 경계

기존 `MatchGroupQueryService.currentGroup(memberId)` 계약으로 active group 부재,
다중 active group, 확정 인원 불일치와 로그인 member 누락을 동일하게 검증한 뒤
확정된 group ID로 event를 조회합니다. event query는
`match_group_members`와 `members`를 한 번에 left join합니다.

event `member_id`가 같은 group의 `JOINED`, `ARRIVAL_TIME_SELECTED`, `ARRIVED`
member일 때만 ID/nickname을 공개합니다. system event와 unrelated/inactive
member는 actor `null`입니다. event별 member 추가 조회는 없어 N+1이 발생하지
않습니다.

### 35.3 payload, 정렬과 조회 제한

기존 `idx_match_events_group_created_at(group_id, created_at)`을 사용하며 migration은
추가하지 않았습니다.

```text
DB 선택: created_at DESC, id DESC LIMIT 50
응답 순서: created_at ASC, id ASC
```

`ARRIVAL_TIME_SELECTED`만 `arrivalMinutes`를 파싱하고 `0`, `5`, `10`, `20`,
`30`인지 검증합니다. JSON 구조가 잘못됐거나 허용값이 아니면 원문을 노출하거나
전체 API를 실패시키지 않고 해당 event만 제외합니다. 다른 지원 type은 payload를
파싱하지 않습니다.

별도 미팅 시작 event가 없으므로 group `startedAt` 또는 첫 `MEMBER_ARRIVED`에서
“미팅이 시작됐어요” event를 합성하지 않습니다.

### 35.4 frontend REST 복원

최초 mount, 직접 URL, 새로고침, WebSocket 연결·재연결, 정상 상태 알림과 장애
polling에서 current group과 events REST를 함께 조회합니다. 동일 in-flight
refresh는 병합하고, 연결/알림 trigger가 진행 중 요청과 겹치면 완료 뒤 한 번 더
refresh합니다. mutation generation을 사용해 mutation 뒤 늦게 끝난 과거 group
응답이 최신 snapshot을 덮지 못하게 합니다.

도착 예정/도착 완료 성공 snapshot은 즉시 반영하되 timeline은 optimistic하게
append하지 않고 commit 후 events REST 결과로 교체합니다. events만 실패하면
group 화면과 기존 timeline을 유지하고 상태 기록 영역에 별도 오류/재시도를
표시합니다.

타임라인은 KST 시각, actor nickname 또는 본인의 “내가” 표현과 다음 문구를
사용합니다.

- `MATCH_CONFIRMED`: 매칭이 확정됐어요.
- `ARRIVAL_TIME_SELECTED + 0`: 곧 도착할 예정이에요.
- `ARRIVAL_TIME_SELECTED + N`: N분 후 도착할 예정이에요.
- `MEMBER_ARRIVED`: 도착했어요.

자유 text input, 전송 버튼, 메시지 작성, group topic과 client SEND는 없습니다.

### 35.5 멱등성, rollback과 자동 검증

동일 arrivalMinutes와 동일 ARRIVED 요청은 기존 transaction 계약대로 새 event를
만들지 않아 timeline 항목도 증가하지 않습니다. arrival member/group/event
rollback 뒤 events API에는 변경 event가 노출되지 않습니다.
`MATCH_CONFIRMED` event insert 실패도 proposal response, attempt, group/member,
pool 확정 전체를 rollback하도록 PostgreSQL trigger 회귀에 포함했습니다.

Windows PowerShell, Azul Java 17.0.15, Docker Desktop과
`pgvector/pgvector:pg16`으로 실행했습니다.

```powershell
.\gradlew.bat test --tests "com.survey.meetorsolo.domain.matching.controller.MatchingControllerTest" --tests "com.survey.meetorsolo.domain.matching.service.MatchGroupEventQueryServiceTest" --tests "com.survey.meetorsolo.domain.matching.service.MatchArrivalTimeServiceIntegrationTest" --tests "com.survey.meetorsolo.domain.matching.service.MatchProposalResponseServiceIntegrationTest" --rerun-tasks
.\gradlew.bat test --tests "com.survey.meetorsolo.domain.matching.*" --rerun-tasks
.\gradlew.bat test --rerun-tasks
.\gradlew.bat build
npx vitest run src/api/matching.test.ts src/hooks/useMatchRoom.test.ts src/pages/MatchRoomPage.test.ts
npm test -- --run
npx tsc --noEmit
npm run build
```

- backend focused: 84건 성공
- matching 전체: 234건 성공
- backend 전체: 278건 성공
- backend build 성공
- frontend focused: 3 files, 39건 성공
- frontend 전체: 10 files, 94건 성공
- TypeScript, production build와 PWA service worker 생성 성공

### 35.6 수동 검증과 제한사항

Windows의 8080/5173 dev runtime이 실행 중이지 않았고 기존 Chrome process에서
안전하게 식별 가능한 두 로그인 session을 확인·제어할 수 없어 두 브라우저
검증은 실행하지 않았습니다. DB를 삭제하거나 fixture를 보정하지 않았습니다.

후속 수동 검증은 A/B 동일 group의 MATCH_CONFIRMED, A 예정 시간 변경과 멱등,
A/B 도착 event, 양쪽 순서 일치, 새로고침, WebSocket 차단 polling과 재연결 REST
복원, 다른 group event 접근 차단 및 자유 입력/전송 UI 부재를 확인합니다.
cursor pagination, 취소·안전 event, meeting point, COMPLETED와 자유 채팅은
계속 후속 범위입니다.

## 36. 요구사항 원문 재확인과 30분 도착 마감 후속 설계

### 36.1 요구사항 분석서에서 재확인한 거리·시간 정책

2026-07-30 수동 화면 검증 중 도착 예정 시간을 반복 변경할 수 있는 현재 UX를
검토했고, 프로젝트 외부 기획 문서인 `관광데이터_기획_요구사항분석서_v1.docx.pdf`
의 서비스 플로우와 기능 요구사항을 다시 확인했습니다.

거리 기준은 용도별로 다음과 같이 구분합니다.

| 기준 | 거리 | 용도 |
| --- | ---: | --- |
| 축제 탐색 | 반경 2km | 현재 위치 주변의 진행 중 강원 축제 탐색 |
| GPS 체크인 | 반경 500m | 선택한 축제 현장 체크인 검증 |
| 만남 장소 후보 | 2km 이내 | 축제 공식 좌표를 중심으로 실제 POI 검색 |
| 단말 위치 확인 | 결정 필요 | Backend에서 그룹별 확정 만남 포인트 기준 반경 확인 |
| 주변 자원·후속 추천 | 반경 3km | 음식점·카페·관광지 밀도와 상황형 동선 추천 |

따라서 `2km`는 단말 위치 확인 반경이 아니라 만남 장소 후보 검색 범위이고,
`3km`는 관광 자원과 후속 추천 조회 범위입니다. 관광공사 축제 공식 좌표는
Kakao Local API의 키워드·카테고리 검색 중심점으로 사용합니다. 최종 만남
포인트는 실제 장소 ID, 장소명, 주소와 좌표를 가진 POI로 확정해 group에
snapshot으로 저장합니다. 운영자가 축제별 검증 장소를 여러 개 사전 등록하고,
그룹 확정 시 MVP 순환 방식으로 1곳을 고정 배정합니다. 후보보다 동시 그룹이
많으면 같은 장소를 다시 사용할 수 있고 향후 시간대별 혼잡도 기반 배정으로
확장합니다. 신고와 약관·동의를 전제로 사용자 GPS 좌표, 정확도와 측정 시각을
Backend에 보내 일회성 거리 판정을 수행하며 원본 좌표는 저장하지 않습니다.
단말 위치 확인 반경은 구현 전에 결정하고 허위 도착은 신고와 운영 검토로
보완합니다.

요구사항의 “2km 이내, 30분 내외 도착”과 `INTER-03`의 도착 인증·노쇼 자동
감지·취소 시점별 패널티를 함께 해석하면, 30분은 매 선택마다 새로 시작하는
상대 시간이 아니라 그룹에 고정되는 전체 도착 마감입니다.

### 36.2 현재 구현과 확인된 정책 차이

현재 구현은 다음 값을 저장합니다.

```text
arrival_minutes
arrival_time_selected_at
```

같은 `arrivalMinutes` 반복 요청은 멱등 처리하여 선택 시각, event와 WebSocket
알림을 갱신하지 않습니다. 그러나 다른 값을 선택하면
`arrival_time_selected_at`이 새로 설정됩니다. 별도의 절대 마감 검증이 없으므로
아래와 같은 연장이 가능합니다.

```text
14:00 30분 선택
14:20 10분 또는 30분으로 변경
→ 그룹 확정 후 30분을 넘긴 예정 시각 생성 가능
```

이는 재확인한 요구사항과 맞지 않습니다. 화면에서도 본인이 `ARRIVED`가 된 뒤
`도착했어요` 버튼은 사라지지만 “몇 분 후 도착하나요?” 영역은 상태와 무관하게
남아 있습니다. backend는 `ARRIVED` 회원의 변경을 거절하더라도 frontend에서
실행 불가능한 action을 계속 노출하는 UX 결함입니다.

현재 구현과 자동 테스트가 보장하는 arrival-time 멱등성·transaction 원자성은
유효하지만, “그룹 전체 30분 절대 마감”까지 구현·검증한 것으로 보지 않습니다.
이 항목을 보완하기 전에는 두 브라우저 화면 검증과 현재 MatchRoom 브랜치의
최종 마감이 남아 있는 상태로 기록합니다.

### 36.3 확정할 30분 절대 마감 계약

meeting point 기능이 아직 없는 현재 단계에서는 다음 값을 파생 기준으로
사용합니다.

```text
arrival_deadline_at = match_groups.confirmed_at + 30 minutes
```

후속 meeting point 정책에서 만남 포인트 안내 시점이 그룹 확정과 분리되면,
마감 기준을 `meeting_point_confirmed_at + 30 minutes`로 변경할지 별도 검토합니다.
그 전까지 선택 변경으로 `arrival_deadline_at`을 갱신하거나 연장하지 않습니다.

허용 예정 시각은 다음 조건을 만족해야 합니다.

```text
estimated_arrival_at
  = arrival_time_selected_at + arrival_minutes

estimated_arrival_at <= arrival_deadline_at
```

예를 들어 14:00에 확정된 그룹은 14:30이 최종 마감입니다.

| 현재 시각 | 남은 전체 시간 | 허용 가능한 선택 |
| --- | ---: | --- |
| 14:00 | 30분 | 즉시, 5분, 10분, 20분, 30분 |
| 14:10 | 20분 | 즉시, 5분, 10분, 20분 |
| 14:25 | 5분 | 즉시, 5분 |
| 14:30 이후 | 0분 | 선택 불가 |

도착 완료 전에는 남은 전체 시간 안에서 다른 값으로 변경할 수 있습니다. 같은
값의 반복 선택은 기존 멱등 계약을 유지하며 선택 기준 시각과 마감을 갱신하지
않습니다. 다른 값으로 변경하더라도 새 예정 시각이 고정 마감을 넘을 수 없습니다.

선택한 개별 예정 시각이 지났다고 즉시 `NO_SHOW`로 전환하지 않습니다. 전체
30분 마감 전에는 “예정 시간이 지났어요”를 표시하고 남은 범위 안에서 다시
알릴 수 있습니다. 전체 마감 이후에만 시간 선택을 차단하고 노쇼 판정 대상으로
넘깁니다.

`ARRIVED` 회원은 도착 예정 시간과 도착 완료 action을 더 이상 실행할 수 없습니다.
frontend는 두 action을 모두 숨기고 도착 완료 시각만 표시합니다.

### 36.4 현재 브랜치 마감 범위

현재 `feature/wbs-10-b-match-room-state`에서는 노쇼와 패널티 전체를 함께
구현하지 않고 아래 경계까지만 보완합니다.

- `arrivalDeadlineAt`을 current group 응답에 제공
- `confirmedAt + 30분`의 절대 마감 계산
- backend에서 남은 시간보다 긴 도착 예정 선택 거절
- 마감 이후 도착 예정 선택 거절
- 같은 값 반복 요청의 기존 멱등성과 마감 불변 유지
- frontend에서 남은 전체 시간과 실제 예상 도착 시각 표시
- 남은 시간보다 긴 선택지 비활성화 또는 미표시
- 개별 예정 시각 경과 안내
- 마감 이후 선택 UI 차단
- 본인 `ARRIVED` 이후 도착 예정 및 도착 완료 action 제거
- 경계 시각, 동시 요청, REST 복원과 WebSocket refresh 회귀 테스트
- 회원 `2`, `27`을 사용하는 두 브라우저 수동 검증

기존 `confirmed_at`으로 파생할 수 있으므로 이 범위에서는 신규 migration을
추가하지 않습니다. 기존 Flyway migration도 수정하지 않습니다.

### 36.5 후속 브랜치 분리

현재 브랜치를 위 마감 범위와 수동 검증까지 완료한 뒤 PR로 종료하고, 이후 작업은
다음처럼 분리합니다.

```text
feature/wbs-10-b-match-room-state
  현재 MatchRoom 상태방 + 30분 절대 마감 보완 + 수동 검증

feature/wbs-10-b-match-room-no-show
  마감 Scheduler + NO_SHOW + 못 갈 것 같아요 + 취소/노쇼 패널티

feature/wbs-10-b-meeting-point
  축제 좌표 기반 Kakao Local 후보 검색 + 만남 포인트 snapshot
  + Kakao Maps 핀 + Backend 단말 위치 확인 + 허위 도착 신고 연결

feature/wbs-10-b-match-room-completion
  그룹 완료 조건과 COMPLETED 전환
```

`match-room-no-show`에서는 최소한 다음 정책을 구현 전에 확정합니다.

- 30분 마감 Scheduler의 조회·잠금·batch·재실행 멱등성
- 미도착 active member의 `NO_SHOW` 전환
- 노쇼 매너온도 `-5`와 cooldown/penalty event 연결
- 마감 전 “못 갈 것 같아요”의 구조화된 취소 사유
- 취소 시점별 패널티
- 비귀책 회원과 group 유지·취소 기준
- `MEMBER_CANCELLED`, `MATCH_CANCELLED` 및 노쇼 알림 reason
- match event 타임라인 공개 문구
- transaction rollback과 AFTER_COMMIT WebSocket

자유 채팅, 자유 text input, group topic, client SEND, Redis와 외부 broker는 이
후속 작업에도 포함하지 않습니다.

### 36.6 실제 구현 결과

현재 브랜치에서 30분 절대 마감을 다음 공통 계산으로 구현했습니다.

```text
arrivalDeadlineAt = confirmedAt + 30 minutes
```

`MatchArrivalDeadlinePolicy`가 계산을 한 곳에서 제공하고 current group DTO와
도착 예정 시간 service가 같은 계산을 사용합니다. DB에는 deadline 컬럼을
추가하지 않고 기존 `match_groups.confirmed_at`에서 매번 파생하므로 선택 변경,
재시도와 동시 요청으로 마감이 연장되지 않습니다. 기존 Flyway migration과
schema는 변경하지 않았습니다.

`MatchArrivalTimeService`의 transaction 검증 순서는 다음과 같습니다.

```text
1. 인증 회원의 current active group row 잠금
2. 같은 group의 active member row 잠금
3. group CONFIRMED/IN_PROGRESS와 member JOINED/ARRIVAL_TIME_SELECTED 재검증
4. now < arrivalDeadlineAt 검증
5. 같은 arrivalMinutes이면 기존 snapshot 멱등 반환
6. 실제 값 변경이면 now + arrivalMinutes <= arrivalDeadlineAt 검증
7. member update, match event insert와 AFTER_COMMIT 알림 event 발행
```

같은 값 반복은 기존 `arrival_time_selected_at`을 기준으로 한 예정 시각을
유지해야 하므로 5단계에서 새 예정 시각을 다시 계산하지 않습니다. 다만 전체
deadline 시각부터는 같은 값 요청도 거절합니다. 실제 값 변경은 예상 도착
시각이 deadline과 같을 때까지 허용하고 이를 넘으면
`MATCHING_ARRIVAL_DEADLINE_EXCEEDED` 409로 거절합니다. 오류 응답은 내부
group/member 존재 여부를 구분해 노출하지 않습니다.

current group 응답은 다음 시간 계약을 함께 제공합니다.

```text
confirmedAt
arrivalDeadlineAt
startedAt
members[].arrivalTimeSelectedAt
members[].arrivedAt
```

Frontend는 `arrivalDeadlineAt`과 REST snapshot만 기준으로 최종 마감 시각,
전체 남은 시간, 본인의 실제 예상 도착 시각과 예상 도착까지 남은 시간을
표시합니다. 1초 timer는 표시만 갱신하며 server 상태, `NO_SHOW`, event를
확정하거나 생성하지 않습니다.

- 남은 시간보다 긴 `0/5/10/20/30` 선택지는 비활성화합니다.
- 개별 예정 시각이 지났지만 전체 deadline 전이면 `예정 시간이 지났어요`를
  표시하고 남은 범위에서 다시 선택할 수 있습니다.
- 전체 deadline부터 시간 선택 UI를 차단합니다.
- `JOINED`, `ARRIVAL_TIME_SELECTED`는 기존 도착 완료 action을 유지합니다.
- `ARRIVED`는 시간 선택과 도착 완료 action을 모두 숨기고 도착 완료 시각만
  표시합니다.
- WebSocket 연결·재연결·알림과 polling은 기존처럼 current group/events REST
  refresh trigger로만 사용합니다.

### 36.7 자동 검증 결과

현재 실행 환경에는 Linux Java가 없어 임시 Linux Java 17을 `/tmp`에 준비해
Gradle compile과 비컨테이너 테스트를 실행했습니다. 운영 코드와 전체 테스트
소스 compile은 성공했습니다.

Backend 결과:

```text
focused 단위/Controller
- MatchArrivalTimeServiceTest
- MatchGroupQueryServiceTest
- MatchingControllerTest
결과: 36건 통과

matching 전체
결과: 102건 실행, 일반 테스트 88건 통과
제약: Testcontainers 14개 class Docker initialization 실패

backend 전체
결과: 146건 실행, 일반 테스트 131건 통과
제약: Testcontainers 15개 class Docker initialization 실패

backend build
./gradlew build -x test
결과: BUILD SUCCESSFUL
```

추가한 Backend 테스트 소스는 다음 계약을 포함합니다.

- `confirmedAt + 30분` 계산과 current group `arrivalDeadlineAt`
- 확정 직후 `0/5/10/20/30`, 30분 선택의 예상 시각과 deadline 동일 경계
- 남은 시간보다 긴 값 거절
- deadline 직전 즉시 선택과 deadline 시각부터 거절
- deadline 전 같은 값 멱등 요청의 기존 선택 시각 유지
- 같은 값과 다른 값 변경의 deadline 불변
- 기존 PostgreSQL 동시 요청, rollback, event와 AFTER_COMMIT 알림 회귀

`pgvector/pgvector:pg16` Testcontainers 통합 테스트는 compile됐지만 현재
WSL에 `docker` command와 Docker Desktop WSL integration이 없어 container
탐지 단계에서 중단됐습니다. 이는 assertion 실패가 아니며, Docker가 연결된
Windows PowerShell 또는 WSL 환경에서 재실행해야 최종 통과로 기록할 수 있습니다.

Frontend 결과:

```text
focused: 3 files, 45건 통과
전체: 10 files, 99건 통과
npx tsc --noEmit: 성공
npm run build: 성공
Vite: 1,621 modules transformed
PWA generateSW: 6 entries precache, sw.js/workbox 생성
```

Frontend 테스트는 남은 시간별 선택지 활성·비활성, deadline과 두 countdown,
예상 도착 시각, 개별 예정 시각 경과, 전체 마감 차단, `ARRIVED` action 제거와
도착 완료 시각, 기존 WebSocket/polling/새로고침/timeline 복원을 검증합니다.

repository 전체 `git diff --check`는 이번 작업 전부터 작업 트리의 광범위한
CRLF 변경을 각 줄의 trailing whitespace로 판정해 실패했습니다. 이번 범위 밖
기존 변경을 일괄 변환하거나 덮어쓰지 않았습니다.

### 36.8 수동 검증 상태와 남은 gate

현재 세션에서는 `http://localhost:8080`, `http://localhost:5173`이 모두
실행 중이지 않았습니다. `powershell.exe`와 Windows Java executable 호출도
WSL `UtilBindVsockAnyPort` 오류로 시작되지 않아 dev DB의 festival `144`,
member `2`, `27`을 사용하는 일반/시크릿 브라우저 검증은 실행하지 못했습니다.

현재 브랜치를 완전히 마감하려면 Docker Desktop과 두 로그인 session을 사용할 수
있는 Windows PowerShell에서 다음 gate를 다시 통과해야 합니다.

1. 신규 `MatchArrivalTimeServiceIntegrationTest`
2. matching 전체
3. backend 전체
4. 양쪽 동일 group과 deadline 확인
5. 남은 시간보다 긴 선택 차단과 선택 변경 deadline 불변
6. 같은 값 반복 timeline 불변
7. A 도착 후 A의 두 action 제거와 B 화면 반영
8. 새로고침, WebSocket 재연결과 polling fallback

이 구현에는 `NO_SHOW`, Scheduler, 취소·패널티, meeting point와 지도,
`COMPLETED`, 자유 채팅, group topic, client `SEND`, Redis를 포함하지 않았습니다.

## 37. 도착 예정 선택지와 상대 변경 snackbar

### 37.1 신규 요청과 기존 데이터 호환

신규 PUT 요청과 화면 선택값은 `5`, `10`, `20`, `25`로 제한했습니다.
실제 도착은 별도 `도착했어요` action만 사용하므로 `0`, 전체 마감 안에서
사실상 선택하기 어려운 `30`은 신규 UI와 API에서 거절합니다.

기존 row/event 호환을 위해 조회 타입과 event parser는
`0`, `5`, `10`, `20`, `25`, `30`을 지원합니다. 과거 `0`은
`곧 도착 예정`, 과거 `30`과 신규 `25`는 기존 N분 문구로 표시합니다.

기존 migration은 수정하지 않고 다음 migration을 추가했습니다.

```text
V13__allow_25_arrival_minutes.sql
```

`V13`은 실제 기존 이름인 `chk_match_group_members_arrival_minutes`를 제거한 뒤
`NULL 또는 0,5,10,20,25,30` 정의로 다시 생성합니다. 기존 데이터를 UPDATE하거나
일괄 변환하지 않습니다.

### 37.2 절대 마감과 transaction 회귀

기존 계약은 그대로 유지합니다.

```text
arrivalDeadlineAt = confirmedAt + 30분
now < arrivalDeadlineAt
now + arrivalMinutes <= arrivalDeadlineAt
```

`25분`도 예상 도착 시각이 deadline과 같으면 허용하고 1ns라도 넘으면
`MATCHING_ARRIVAL_DEADLINE_EXCEEDED`로 거절합니다. 선택 변경은 deadline을
갱신하지 않습니다. 같은 신규 허용값 반복의 멱등 성공, group/member lock,
member/event 단일 transaction, rollback과 AFTER_COMMIT fan-out도 변경하지
않았습니다. `ARRIVED` 이후 두 action 제거 계약도 유지합니다.

### 37.3 상대 변경 snackbar 판정

WebSocket payload는 계속 REST refresh trigger로만 사용합니다. 정상 current
group REST refresh 전후 snapshot에서 current member가 아닌 회원의
`arrivalMinutes` 또는 `arrivalTimeSelectedAt`이 실제 달라졌을 때만 다음 문구를
표시합니다.

```text
{nickname}님이 도착 시간을 변경하였어요.
```

최초 snapshot은 비교 기준만 만들고 알림을 생성하지 않습니다. 본인 mutation
성공 snapshot은 먼저 현재 상태에 반영하므로 후속 refresh에서 본인 snackbar로
오인하지 않습니다. 동일 snapshot, 같은 값 멱등 요청, rollback/실패 refresh와
잘못된 WebSocket payload에도 표시하지 않습니다. WebSocket 장애 polling도 같은
비교 함수를 사용합니다.

snackbar는 모바일 하단 navigation을 가리지 않는 `bottom-24`에 두고
`role="status"`, `aria-live="polite"`를 사용합니다. 3초 뒤 자동 제거하며
연속 변경은 이전 timer를 취소하고 최신 문구와 timer로 교체합니다. unmount에서
polling, snackbar timer, WebSocket과 AbortController를 함께 정리합니다.

### 37.4 자동 검증 결과와 제한

```text
Backend focused: 41건 통과
PostgreSQL focused: 2개 class Docker 탐지 단계 initialization 실패
matching 전체: 104건 중 일반 90건 통과, Testcontainers 14개 class 실패
backend 전체: 148건 중 일반 133건 통과, Testcontainers 15개 class 실패
backend build -x test: 성공

Frontend focused: 3 files, 52건 통과
Frontend 전체: 10 files, 106건 통과
npx tsc --noEmit: 성공
production/PWA build: 성공, 1,621 modules와 precache 6 entries
```

Backend Testcontainers는 기존 `pgvector/pgvector:pg16` 구성을 유지했고 H2로
대체하지 않았습니다. 현재 WSL에서 Docker client를 탐지하지 못해 V13 적용,
PostgreSQL 동시성·rollback·AFTER_COMMIT 회귀는 assertion에 진입하지 못했습니다.
Docker Desktop integration이 가능한 환경에서 focused, matching 전체와 backend
전체를 다시 실행해야 합니다.

repository 전체 `git diff --check`는 작업 시작 전부터 존재한 광범위한 CRLF
working tree 변경을 trailing whitespace로 판정해 실패했습니다. 기존 변경을
정규화하거나 되돌리지 않았습니다. 시작 시 이미 수정 상태였던 `V1`~`V12`
migration은 건드리지 않고 신규 `V13`만 추가했습니다.

`NO_SHOW`, 마감 Scheduler, 취소·패널티, meeting point, Kakao Maps,
`COMPLETED`, 자유 채팅, group topic, client STOMP `SEND`, Redis와 외부 broker는
구현하지 않았습니다.

## 38. 확정 후 자발적 취소와 30분 마감 NO_SHOW

### 38.1 V14와 snapshot

기존 migration은 수정하지 않고
`V14__add_match_room_cancellation_no_show.sql`을 추가했습니다.
`match_group_members.allow_minimum_two`는 신규 group 확정 시 pool 값으로
snapshot하며 기존 row는 group attempt, attempt member와 pool 관계로
backfill합니다. 매핑하지 못한 row가 하나라도 있으면 migration을 실패시킵니다.

V14는 `no_show_at`, 구조화된 member/group 취소 사유, `MEMBER_NO_SHOW`,
cooldown의 `related_group_id`와 `(group, member, cause)` unique index를
추가합니다.

### 38.2 transaction과 상태 전이

취소, 도착과 NO_SHOW는 다음 잠금 순서를 공유합니다.

```text
match_groups row
→ match_group_members row ID 오름차순
→ member/cooldown 관련 row
```

자발적 취소는 확정 후 3분 이내에는 penalty event와 cooldown을 만들지 않습니다.
그 이후 deadline 전에는 `CANCEL +1` event를 만들고 KST 당일 횟수에 따라
10/30/60분 cooldown을 적용합니다. NO_SHOW는 deadline 정각부터 `+3`,
KST 당일 30/60분 cooldown을 적용합니다. 기존 active cooldown보다 새 만료가
짧으면 기존 만료를 보존합니다. `manner_temperature`는 변경하지 않습니다.

현재 유효 인원이 3명 이상이거나 정확히 2명이고 두 snapshot이 모두 true이면
group을 유지합니다. 그 외에는 group을 `CANCELLED`, 남은 비귀책 회원을
`LEFT`로 전환하며 `MATCH_CANCELLED`를 한 번 저장합니다.
`confirmedMemberCount`는 최초 확정 인원이고 `currentMemberCount`는 공개 active
member 수입니다.

### 38.3 Scheduler와 알림

NO_SHOW Scheduler는 `MATCHING_NO_SHOW_SCHEDULER_ENABLED=true`일 때만 실행하며
기본 fixed delay 5초와 batch 20을 사용합니다. 한 tick에서 `Clock`을 한 번
읽고 만료 group을 제한 조회한 뒤 group별 `REQUIRES_NEW` transaction과
`FOR UPDATE SKIP LOCKED`로 실패를 격리합니다.

`MEMBER_CANCELLED`, `MEMBER_NO_SHOW`, `MATCH_CANCELLED`는 DB transaction에서
event와 application event를 만들고 실제 STOMP 전송은 기존
`AFTER_COMMIT` listener에서만 수행합니다. Frontend는 WebSocket payload를
상태로 사용하지 않고 REST refresh trigger로만 사용합니다.

### 38.4 검증 결과와 제한

```text
Backend focused: 47건 성공
Frontend focused: 3 files, 56건 성공
Frontend 전체: 10 files, 110건 성공
npx tsc --noEmit: 성공
Backend build -x test와 production/PWA build: 성공
PostgreSQL focused: Docker client 탐지 실패로 initialization 실패
matching 전체: 114건 중 일반 98건 통과, Testcontainers 14건 initialization 실패,
  scheduling 조건 2건은 수정 후 focused 재실행 성공
```

PostgreSQL 테스트는 Flyway V14와 assertion 실행 전에 중단됐으므로 실제
migration, 동시성, rollback 통합 검증 성공으로 기록하지 않습니다. 실행 중인
local backend/frontend와 두 로그인 session도 없어 회원 `2`, `27`, festival
`144`의 두 브라우저 수동 검증은 실행하지 않았습니다.
V14 취소와 NO_SHOW 재실행 멱등성 PostgreSQL 통합 테스트 소스는 추가하고
compile했지만 같은 Docker 제약으로 assertion에는 진입하지 못했습니다.

### 38.5 Windows Testcontainers 경계 실패와 Scheduler 격리 보완

Windows 실행에서 관련 통합 테스트 36건 중 다음 3건이 실패했습니다.

```text
MatchingRestApiIntegrationTest
- 저장된 확정 인원과 active member 수 불일치 assertion

MatchArrivalTimeServiceIntegrationTest
- 25분 예상 도착 == deadline 경계
- 남은 5분과 deadline 정각 경계
```

첫 REST 실패는 과거의 `confirmedMemberCount == active member count` 계약이
남아 있던 테스트 문제였습니다. 현재는 최초 확정 3명에서 active 2명이 남는
상태를 정상으로 검증하고 `confirmedMemberCount=3`,
`currentMemberCount=2`, 공개 member 2명을 확인합니다. active member가 2명
미만이거나 최초 확정 인원보다 많은 경우는 계속 `MATCHING_CONFLICT`입니다.

시간 경계 실패는 테스트 fixed Clock이 `NOW + 10초`인데 DB `confirmed_at`
fixture는 `NOW`를 기준으로 만들었던 10초 오차가 원인이었습니다.
`TEST_NOW = (NOW + 10초).truncatedTo(ChronoUnit.MICROS)`로 DB와 Clock 기준을
통일했습니다. `minusNanos(1)`은 제거하고 deadline 초과는 PostgreSQL에서
안정적인 1초 차이로 검증합니다. 운영 서비스의 `예상 도착 <= deadline`,
`now < deadline` 계약은 변경하지 않았습니다.

일반 matching Spring 통합 테스트는 사용자 환경변수와 무관하게 아래 값을
test property로 고정합니다.

```text
app.matching.scheduler.enabled=false
app.matching.no-show-scheduler.enabled=false
```

따라서 테스트 context와 Testcontainers 종료 뒤 Scheduler가 DB에 재접근하지
않습니다. Scheduler 자체의 조건을 검증하는 전용 테스트에는 이 설정을
적용하지 않았습니다.

수정 후 test source compile과 비컨테이너 정책 회귀 21건은 성공했습니다.
현재 WSL에는 Docker command가 없고 Windows interop은
`UtilBindVsockAnyPort` 오류로 실행되지 않아 다음 Windows 재검증은 남아
있습니다.

```text
1. MatchingRestApiIntegrationTest
2. MatchArrivalTimeServiceIntegrationTest
3. matching 전체
```

### 38.6 dev DB·두 브라우저 수동 검증과 Frontend 보완

2026-08-04에 festival `144`, member `1`, `2`, `27`을 사용해
`14_MATCH_ROOM_NO_SHOW_MANUAL_TEST.md`의 취소·NO_SHOW·인원 감소 시나리오를
dev DB와 일반/시크릿 브라우저에서 검증했습니다.

확인한 결과는 다음과 같습니다.

- deadline 전 상태 유지, deadline 정각부터 도착 API 거절과 Scheduler의
  `JOINED`/`ARRIVAL_TIME_SELECTED -> NO_SHOW` 전환
- `no_show_at`, 회원별 `MEMBER_NO_SHOW`, penalty event, cooldown과
  `related_group_id` 저장
- NO_SHOW `penalty_score +3`, KST 당일 첫 30분·두 번째 이상 60분 cooldown,
  `manner_temperature` 불변
- Scheduler 반복 tick 이후 member event, penalty event와 cooldown 각 1건 유지
- `ARRIVED`, `CANCELLED`, `NO_SHOW`, `LEFT` 재처리 제외와 비귀책 회원 무패널티
- 2명 group의 귀책 이탈 시 group 종료와 비귀책 회원 `LEFT`
- 최초 확정 3명에서 잔여 2명의 `allow_minimum_two`가 모두 true이면 group 유지,
  false가 포함되면 group 종료
- 한 회원이 `ARRIVED`인 상태에서 상대가 확정 3분 이후 취소하면 취소 회원에게만
  `CANCEL +1`과 첫 10분 cooldown 적용
- 동일 취소 요청 재전송과 Scheduler 재실행에도 event, penalty와 cooldown 멱등성 유지
- 기존 2시간 `REPORT` active cooldown보다 새 NO_SHOW 만료가 짧을 때 기존
  만료 시각 보존. 기존 row는 `EXPIRED`, 새 `NO_SHOW` row는 `ACTIVE`이며
  `expires_at`이 동일함
- 취소·NO_SHOW 종료 snapshot이 양쪽 화면과 새로고침에서 복원됨

수동 검증 중 deadline 이후에도 `도착했어요` action이 남는 문제를 발견해,
deadline부터 action을 숨기고 Scheduler 처리 대기 안내를 표시하도록
`MatchRoomPage`를 수정했습니다. 또한 종료 안내가 Router history state에 남아
새 매칭과 새로고침에도 반복 표시되는 문제를 수정했습니다. 종료 안내는 최초
`/matching` 진입에서만 local 상태로 표시하고 history state에서 즉시 소비하며,
재신청과 새 pool 신청에서도 제거합니다.

관련 frontend focused 테스트는 2 files, 43건이 통과했고
`tsc --noEmit`도 성공했습니다. 두 문제는 브라우저 재검증까지 완료해
`ISSUE-MR-006`, `ISSUE-MR-007`을 `FIXED`로 판정했습니다. 상세 SQL과 개별
체크 결과는 `14_MATCH_ROOM_NO_SHOW_MANUAL_TEST.md`를 기준으로 합니다.

이 범위에서 확정 후 취소·NO_SHOW 기능과 수동 검증은 완료했습니다. 다음 기능
브랜치는 `feature/wbs-10-b-meeting-point`이며 축제별 복수 만남 장소 등록,
그룹별 고정 배정, 2km 후보 검색, Kakao Maps 핀과 Backend GPS 도착 판정을
구현합니다. 이후
`feature/wbs-10-b-match-room-completion`에서 group 완료 조건과
`IN_PROGRESS -> COMPLETED` 전환을 구현했습니다.

## 41. 명시적 거절 상대의 check-in pair 재추천 제외

### 41.1 정책과 데이터 경계

기획서 8.3 `MATCH-08`의 거절 상대 자동 제외만 반영하고 최대 5회 제한은 적용하지 않습니다. round 1 `INITIAL_MATCH`의 명시적 `REJECTED`만 거절 회원과 같은 proposal의 다른 회원 pair를 생성합니다. `TIMEOUT`, round 2, 인원 미달·시스템 실패, 정상 완료와 MatchRoom 취소는 생성 원인이 아닙니다.

`V18`의 `match_opponent_exclusions`는 member ID 오름차순과 그 member가 실제 사용한 check-in ID 대응을 함께 보존합니다. 따라서 A-B/B-A는 같은 pair이고 어느 한쪽이 새 check-in을 만들면 과거 row는 후보 조회에 일치하지 않습니다. `user_blocks`는 영구 안전 차단, exclusion은 check-in 범위 임시 재추천 제외로 책임을 분리합니다.

### 41.2 transaction과 advisory lock

REJECT 응답은 기존 `attempt → proposal → attempt member → pool ID 오름차순` 잠금 뒤 response와 exclusion을 같은 transaction에 저장합니다. attempt member의 `pool_id`, pool의 `member_id/checkin_id`와 실제 check-in의 `member_id/festival_id`를 검증해 임의 조합 저장을 막습니다. 반복 REJECT는 기존 response 멱등 경로를 반환하며 DB unique와 `ON CONFLICT DO NOTHING`도 중복 row를 막습니다.

proposal 생성은 기존 pool row 잠금과 status/token/check-in/cooldown/block 재검증 뒤 모든 check-in pair advisory lock을 획득하고 exclusion을 다시 조회합니다. REJECT 생성 경로도 같은 lock을 사용합니다.

```text
pair 정규화
→ (lowerMemberId, higherMemberId, lowerCheckinId, higherCheckinId) 정렬
→ SHA-256(lowerCheckinId:higherCheckinId)의 앞 64bit
→ pg_advisory_xact_lock(firstInt, secondInt)
→ exclusion 재조회 또는 insert
```

두 32-bit key는 합쳐서 64-bit hash 공간을 사용하므로 충돌 가능성은 있으나 매우 낮습니다. hash 충돌이 발생해도 정합성 오류가 아니라 서로 다른 pair가 잠시 불필요하게 직렬화됩니다. 모든 경로가 동일 pair 정렬 순서로 lock을 획득하고 advisory lock은 기존 row lock 뒤에만 잡으므로 신규 역순 lock 경로를 만들지 않습니다.

### 41.3 후보 조회와 최종 방어

- pool-entry requester SQL과 legacy requester SQL은 현재 requester/candidate pool의 check-in pair `NOT EXISTS`를 적용한다. requester 자신은 예외로 계속 포함한다.
- Scheduler는 pool을 batch 선점한 뒤 `MatchingBatchReader`가 `user_blocks`와 exclusion pair를 별도 집합으로 읽는다.
- `MatchGroupComposer` compatibility는 block과 exclusion을 모두 통과한 pair만 허용한다.
- `MatchProposalCreationService`의 `REQUIRES_NEW` 최종 검증이 race 중 commit된 exclusion을 확인하면 attempt/proposal 생성 전 rollback한다.

### 41.4 보존과 공개 범위

현재 적용 여부는 현재 두 check-in ID 조합 일치로만 판단하며 과거 row 즉시 삭제 Scheduler는 추가하지 않습니다. 과거 row는 감사와 문제 분석을 위해 일정 기간 보존할 수 있고 실제 삭제 기간은 match event·개인정보 보존 정책과 함께 후속 확정합니다. FK는 `ON DELETE RESTRICT`이므로 회원/check-in 삭제 전 관련 보존 또는 익명화 정책이 필요합니다.

exclusion pair, rejector와 source proposal은 REST/WebSocket/Frontend DTO와 log에 출력하지 않습니다.

### 41.5 자동 검증

- pair 정규화, 동일 방향 advisory key와 결정적 lock 순서 단위 테스트
- 2인/3인 REJECT 생성 범위, 반복 REJECT, TIMEOUT과 round 2 비생성 PostgreSQL response 테스트
- requester 양방향 제외, requester 자기 포함과 새 check-in 미적용 테스트
- Scheduler batch exclusion 조합 배제 테스트
- proposal 생성 직전 exclusion 최종 재검증과 exclusion commit race 테스트
- 기존 block/cooldown/pool claim·release/proposal response·timeout matching 전체 회귀

최종 자동 검증은 matching 전체 288건과 backend `clean build` 전체 347건이 성공했습니다. 전체 build 종료 시 이미 정리된 Testcontainers PostgreSQL 연결을 background Scheduler/Hikari 종료 thread가 확인한 connection warning이 있었지만 테스트 실패는 없었습니다.

Frontend 계약은 변경하지 않았으므로 Frontend 테스트와 build는 실행 대상에서 제외했습니다.

### 41.6 local DB 최소 수동 검증

2026-08-12 local DB에서 다음 항목을 확인했습니다.

- `V18__add_match_opponent_exclusions.sql` 적용: `PASS`
- `match_opponent_exclusions` 테이블 생성: `PASS`
- A-B round 1 명시적 거절 후 exclusion 1건 생성: `PASS`
- 동일 check-in pair 재추천 방지: `PASS`
- `TIMEOUT` exclusion 미생성: PostgreSQL 자동 통합 테스트로 대체, `PASS`

따라서 명시적 거절 상대의 check-in pair 재추천 제외 범위는 구현, 자동 회귀,
local DB migration과 최소 수동 검증까지 완료했습니다. 최종 상태는 `완료`입니다.

## 42. 완료 수동 검증 후 확인된 후속 보완

group `24` 수동 검증에서 두 회원의 `MEMBER_ARRIVED`와 group당 단일
`MATCH_COMPLETED`, group/member `COMPLETED` 전환은 정상임을 확인했습니다.
그러나 `/matching`은 완료 안내를 한 번 표시하면서도 남아 있는 `MATCHED` pool을
일반 terminal 상태로 해석해 `매칭이 취소됐어요`와 `다시 신청하기` card를 함께
표시했습니다. Backend 완료 transaction과 별개인 Frontend 완료 결과 표현
문제로 판정합니다.

구현한 보완 계약은 다음과 같습니다.

- 체크인과 확정 매칭 유효시간을 2시간에서 각각 1시간으로 조정
- 재매칭 제한 종료 시각은 `completed_at`이 아니라 `confirmed_at + 1시간`
- 정상 완료 group에서 restriction을 파생하고 신규 pool 신청도 서버에서 거절
- 완료 전용 card와 countdown을 제공하고 제한 중 신청 action 비활성화
- 제한 종료 뒤 체크인이 만료됐으면 재체크인 동선 제공
- 최대 3회 제한, 후기 작성, 최근 완료 상세 API와 GPS 판정은 제외

Backend는 완료 group/member 관계에서 `confirmed_at + 1시간` 제한을 파생하고
restriction의 `completionLock`과 pool 신청 오류 `MATCHING_COMPLETION_LOCKED`에
적용했습니다. active pool/group 검증은 완료 제한보다 먼저 적용되며 정상 완료
penalty/cooldown row는 만들지 않습니다. check-in은 `V17`과 matching SQL에서
1시간 상한을 강제합니다.

Frontend는 `MatchingUiStatus.COMPLETED`와 완료 전용 card를 추가했습니다. current
group이 `null`이고 최신 pool이 `MATCHED`여도 완료 이력이 현재 matching lifecycle에
해당하면 완료로 복원하며, 종료 시각/countdown과 제한 중 비활성 action을
표시합니다. 제한 종료 뒤에는 retry form을 열고 만료 check-in은 기존 API 오류의
체크인 동선으로 연결합니다.

Backend focused unit/controller, PostgreSQL Testcontainers 완료 제한 통합,
matching 전체와 전체 `clean build` 336건을 성공했습니다. Frontend focused
Vitest 63건, 전체 128건, TypeScript와 production/PWA build도 성공했습니다.

### 42.1 브라우저·DB 수동 재검증 결과

두 브라우저에서 마지막 회원 도착 후 `/matching` 이동, 완료 전용 card,
`confirmed_at + 1시간` 종료 시각과 countdown, 제한 중 비활성 action을
확인했습니다. 기존 `매칭이 취소됐어요` 문구는 더 이상 표시되지 않아
`ISSUE-MR-008`을 `CLOSED`로 판정했습니다.

DB에서는 다음을 모두 확인했습니다.

- group과 두 유효 member `COMPLETED`, `completed_at`과 `arrived_at` 저장
- 회원별 `MEMBER_ARRIVED` 각 1건과 group당 `MATCH_COMPLETED` 1건
- 정상 완료 관련 penalty event와 cooldown 0건
- 완료 회원의 active pool/group 점유 해제
- 완료 group의 `confirmed_at + 1시간` 기준 completion lock과 화면 countdown 일치
- check-in 저장 만료시각과 정책상 1시간 유효 상한 적용

따라서 MatchRoom 전원 도착 완료와 1시간 재매칭 제한 범위는 구현, 자동 테스트,
브라우저·DB 수동 검증까지 완료했습니다.

다만 새로고침 직후 서버 snapshot을 받기 전에 자동 매칭 신청 form이 잠깐
노출되고 완료 card로 바뀌는 전환을 확인했습니다. 이는 완료 transaction이나
restriction 정합성 문제가 아니라 Frontend가 초기 `unknown`을 `IDLE`로 먼저
렌더링하는 hydration UX 문제입니다. 별도
`feature/wbs-10-frontend-async-ux-stabilization` 브랜치에서 전체 화면의 최초
loading, 재조회 상태 유지와 layout shift를 함께 보완합니다.

## 43. MatchRoom 상대 회원 구조화 신고 Backend 1차

### 43.1 API와 권한 계약

```http
POST /api/match-groups/{groupId}/reports
Cookie: access_token=<ACCESS_TOKEN>
Content-Type: application/json
```

```json
{
  "reportedMemberId": 27,
  "reasonCode": "RUDE"
}
```

Controller는 reporter ID를 받지 않고 JWT cookie에서만 계산합니다. Service는 group
row를 `FOR SHARE`로 잠근 뒤 신고자와 피신고자가 모두 같은 group의
`match_group_members` 이력을 갖는지 확인합니다. group 또는 어느 참여 관계가 없든
동일한 `REPORT_RESOURCE_NOT_FOUND`를 반환해 IDOR과 ID 탐색을 방어합니다.

진행 중인 `CONFIRMED`, `IN_PROGRESS`는 신고할 수 있습니다. 종료 group은
`COMPLETED.completed_at`, `CANCELLED.cancelled_at`부터 30일 이내이며 정확히 30일도
허용합니다. terminal timestamp 누락은 `updated_at` 같은 임의 시각으로 대체하지
않고 `REPORT_CONFLICT`로 거절합니다.

### 43.2 응답과 멱등성

신규 생성과 동일 멱등 재요청은 모두 `201 Created`로 같은 resource 계약을 반환합니다.

```json
{
  "reportId": 1,
  "groupId": 10,
  "reportedMemberId": 27,
  "reasonCode": "RUDE",
  "status": "SUBMITTED",
  "createdAt": "2026-08-12T06:42:00+09:00"
}
```

V4의 `(reporter_member_id, reported_member_id, group_id, reason_code)` UNIQUE와
`INSERT ... ON CONFLICT DO NOTHING`을 함께 사용합니다. conflict 시 기존 row를 다시
조회하므로 동시 요청도 한 건으로 수렴하고, 이미 `REVIEWING` 등으로 바뀐 status와
최초 `created_at`을 초기화하지 않습니다.

응답은 reporter ID, 회원 프로필과 `detail_encrypted`를 포함하지 않습니다. 신고
transaction은 report 외의 penalty/cooldown/member/match event를 변경하지 않으며
WebSocket과 application event를 발행하지 않습니다.

### 43.3 자동 검증 결과

- 신고 focused PostgreSQL Testcontainers 13건 성공
- matching 전체 288건 성공
- backend 전체 360건 성공
- failure, error, skip 모두 0건

최초 focused 실행의 30일 초과 1건은 나노초 차이가 PostgreSQL `TIMESTAMPTZ`
정밀도에서 경계로 정규화된 테스트 데이터 문제였습니다. 운영 비교는 유지하고
경계 밖 fixture를 1초 차이로 수정한 뒤 재실행했습니다. backend 전체 종료 중 이전
context의 닫힌 Testcontainers 연결을 Scheduler/Hikari가 확인한 기존 경고가 있었지만
Gradle은 `BUILD SUCCESSFUL`로 종료했습니다.

1차 범위에는 Frontend 신고 UI, 차단, 관리자 처리, 자동 penalty/cooldown,
`manner_temperature`, 자유 입력과 자유 채팅을 포함하지 않습니다. 후속 MatchRoom UI는
current group의 상대 `memberId`와 이 API를 연결하고, 차단은 신고 성공과 독립된 명시적
사용자 선택 API로 연결합니다.

## 44. MatchRoom 상대 회원 구조화 신고 Frontend

### 44.1 UI와 API 경계

MatchRoom의 current group snapshot에서 본인을 제외한 각 member 카드에만 신고
action을 표시합니다. dialog는 여섯 사유 중 하나를 고른 뒤 대상 nickname과 한국어
사유를 다시 확인해야 제출할 수 있습니다. 자유 입력, 첨부, 채팅과 차단 action은
포함하지 않습니다.

API client는 current snapshot의 `groupId`와 선택 카드의 `memberId`를 사용해
`POST /api/match-groups/{groupId}/reports`를 호출합니다. body는
`reportedMemberId`, `reasonCode`만 포함하며 reporter 정보는 cookie 인증을 사용하는
Backend에 맡깁니다. 공통 `apiClient`의 `credentials: include`, `ApiResponse` 및
오류 parsing을 그대로 사용하고 HTTP 201 신규·멱등 응답을 모두 정상 처리합니다.

### 44.2 상태와 비동기 방어

신고 상태는 MatchRoom의 REST/WebSocket snapshot 상태와 분리합니다. 선택 대상·사유,
사유/확인 단계, submitting, 성공·오류 feedback만 신고 session이 소유합니다.
submit 함수의 동기 in-flight guard가 같은 promise를 반환해 빠른 이중 클릭도 API 한
번으로 수렴합니다. dialog 취소, 다른 상대 선택과 unmount는 요청을 abort하고 request
identity를 갱신하므로 늦은 성공·실패가 새 dialog를 덮어쓰지 않습니다.

성공 시 신고 dialog만 닫고 완료 안내를 표시하며 current group을 재조회하거나
WebSocket event를 보내지 않습니다. 실패 시 대상·사유·확인 단계와 기존 group
snapshot을 유지해 재시도합니다. 신고만으로 penalty/cooldown, `penalty_score`,
`manner_temperature` 또는 차단 상태가 바뀐다고 안내하지 않습니다.

### 44.3 자동·수동 검증

- focused Frontend Vitest: 3 files, 52 tests 성공
- TypeScript `npx tsc --noEmit`: 성공
- Frontend 전체 Vitest: 12 files, 137 tests 성공
- TypeScript/Vite production build 및 PWA `generateSW`: 성공
- 두 브라우저·dev DB 수동 검증: 실행 전 `PENDING`

수동 절차와 읽기 전용 DB SQL은 `docs/15_MATCH_ROOM_REPORT_MANUAL_TEST.md`에
분리했습니다. 실제 수행 전에는 PASS로 기록하지 않습니다.
