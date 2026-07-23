# 진행 상태 기록

## [10-매칭 7차] penalty/cooldown과 proposal 기반 멱등성

상태: 운영 코드와 PostgreSQL Testcontainers 통합 테스트 완료

- 기존 V1~V11을 수정하지 않고 `V12__add_matching_penalty_cooldown_idempotency.sql` 추가
- `match_cooldowns`, `match_penalty_events`에 nullable `related_proposal_id` FK와 partial unique index 추가
- round 1 거절은 전체 terminal 집계 시각부터 30초 cooldown을 적용하고 점수는 부과하지 않음
- round 1 timeout은 처리 시각부터 2분 cooldown과 `penalty_score +1` 적용
- round 2 취소는 2분 cooldown과 `+1`, timeout은 5분 cooldown과 `+2` 적용
- 비귀책 회원은 cooldown과 penalty 대상에서 제외하고 귀책 pool은 `CANCELLED` 유지
- 만료된 `ACTIVE` cooldown을 신규 생성 transaction에서 `EXPIRED`로 lazy 전환
- response, cooldown, penalty event, 회원 점수, pool, attempt를 기존 응답 transaction에서 원자 처리
- 기존 attempt → proposal → attempt member 잠금과 pool ID 오름차순 잠금 순서 유지
- 동일 응답, Scheduler 재실행, 사용자 응답/timeout race의 중복 방지 테스트 보강
- cooldown/penalty/member update 실패 rollback과 V1~V12 migration 검증 테스트 보강
- Windows Git Bash + Docker Desktop에서 targeted PostgreSQL 통합 테스트 55건, failures 0, errors 0, skipped 0, `BUILD SUCCESSFUL`
- matching REST API, 신청 API, frontend, WebSocket, `POOL_ENTRY`, 완전 재매칭, Redis, embedding scoring은 제외

## [10-매칭 6차] 인원 미달 round 2 재확인과 최소 인원 확정

상태: 운영 코드와 PostgreSQL 통합 테스트 완료, 환경 의존 root context test를 제외한 backend 회귀 171건 완료

- 3명 또는 4명 목표의 round 1 전체 응답 종료 후 수락자 2명 이상·목표 미달·수락자 전원 `allow_minimum_two=true` 조건 판정
- 같은 attempt에 `INSUFFICIENT_MEMBERS_CONFIRMATION`, round 2 proposal을 수락자에게만 원자 생성
- round 2 생성 시 attempt를 `INSUFFICIENT_MEMBERS`로 전환하고 기존 30초 timeout 기준으로 `expires_at` 갱신
- `START_WITH_CURRENT_MEMBERS`, `CANCEL_CURRENT_MEMBERS`, `TIMEOUT` 응답과 proposal 상태 매핑 구현
- 전원 진행 동의 시 실제 인원수로 group/member 생성, pool `MATCHED`, attempt `CONFIRMED` 처리
- 취소·timeout 회원 pool `CANCELLED`, 비귀책 회원 pool `WAITING` 또는 `EXPIRED`, attempt `FAILED` 처리
- attempt row aggregate lock과 기존 attempt → proposal → attempt member 잠금 순서 유지
- round 2 중복 생성·중복 응답 방지, 응답 변경 금지, Scheduler timeout 재실행 멱등성 유지
- 동시 진행 동의, 진행/취소 race, round 2 생성 중 DB 실패 rollback PostgreSQL 통합 테스트 추가
- 기존 V1~V11 migration을 수정하지 않았고 신규 migration, penalty/cooldown, REST API, frontend, WebSocket은 제외

targeted 검증:

- `MatchProposalResponseServiceIntegrationTest` `BUILD SUCCESSFUL`
- 3→2, 4→2, 4→3 진입과 제외 조건, 최소 인원 확정, 취소·timeout, 동시성, rollback을 실제 PostgreSQL 16 + pgvector에서 검증
- `domain`, `external`, `global` 전체 171건, failures 0, errors 0, skipped 0, `BUILD SUCCESSFUL`
- 전체 172건 실행에서는 개인 `.env`의 dev SSH tunnel `127.0.0.1:15432` 미연결로 기존 `MeetOrSoloApplicationTests.contextLoads()` 1건만 환경 실패
- local PostgreSQL container는 healthy였으나 기존 volume의 초기 인증값과 개인 `.env` 값이 달라 root context test 완료를 위해 환경 정합성 확인이 필요

## [10-매칭 5차] 최초 proposal 응답과 최종 group 확정

상태: 운영 코드와 테스트 작성 완료, Docker Desktop WSL integration 비활성으로 PostgreSQL 통합·전체 회귀 테스트 실행 필요

- `INITIAL_MATCH`, round 1의 수락·거절·timeout과 `match_responses` 저장 구현
- attempt row를 aggregate lock으로 사용하고 attempt, proposal, attempt member 순서로 잠금 고정
- 동일 응답 반복 멱등성, 응답 변경 금지, `responded_at >= expires_at` timeout 경계 구현
- 거절·timeout 시 attempt 실패, 남은 proposal/member 종료, 귀책·비귀책 pool 정리 구현
- 전원 수락 시 group/member 생성, pool `MATCHED`, attempt `CONFIRMED`를 마지막 응답 transaction에서 처리
- timeout 전용 service와 조건부 Scheduler 진입점 추가, 기존 fixed delay와 batch size 재사용
- PostgreSQL trigger 기반 response·상태·group/member·pool·attempt rollback 테스트 작성
- cooldown, penalty, 인원 미달 round 2, REST API, frontend, WebSocket, POOL_ENTRY는 제외
- Java 17 compile 및 Docker 비의존 timeout/Scheduler 테스트는 `BUILD SUCCESSFUL`
- PostgreSQL Testcontainers 실행은 현재 WSL 배포에서 `docker` 명령을 찾지 못해 container 초기화 전에 중단됨

완료 판단 전 필수 재실행:

- 신규 `MatchProposalResponseServiceIntegrationTest`
- 전체 backend 회귀 테스트
- failures, errors, skipped가 모두 0인 `BUILD SUCCESSFUL` 확인

## [10-매칭 4차] Scheduler orchestration과 최초 proposal 생성

상태: 운영 코드와 PostgreSQL 통합·전체 backend 회귀 테스트 완료

- 기본 비활성화되는 설정 기반 Scheduler와 5초 실행 간격, 30초 stale/proposal timeout, batch 20 기본값 추가
- cleanup, Scheduler 전용 `FOR UPDATE SKIP LOCKED` batch claim, row lock 밖 batch 조회·조합, 그룹별 생성, 미사용 lock release의 transaction 경계 분리
- V1~V11의 `match_attempts`, `match_attempt_members`, `match_proposals` JPA mapping과 repository 추가
- 생성 직전 pool ID 오름차순 row lock과 상태/token/만료/check-in/cooldown/모든 pair 차단 관계 재검증
- 최초 attempt `WAITING_RESPONSES`, 최초 proposal `INITIAL_MATCH`/round 1/`SENT` 생성
- attempt/member/proposal과 `LOCKED -> PROPOSED` 전이를 그룹별 하나의 transaction으로 원자 처리하고 임시 lock 정보 제거
- 그룹 점수와 회원별 pair 평균 점수를 `BigDecimal` 소수점 둘째 자리로 저장
- 미사용·실패한 동일 token의 `LOCKED`를 유효 기간에 따라 즉시 `WAITING` 또는 `EXPIRED`로 release
- 고정 `Clock`과 token generator 주입이 가능한 구조 및 그룹별 실패 격리, finally release, suppressed release 오류 보존
- `@EnableScheduling`을 `enabled=true` 조건부 configuration으로 분리해 비활성 환경에서는 scheduling infrastructure도 생성하지 않음
- 실제 YAML 기본값·override·잘못된 Duration/batch 설정의 context binding 검증
- PostgreSQL test trigger로 member/proposal insert와 pool 전이 flush 실패를 유도해 그룹별 생성 전체 rollback 검증
- 외부 transaction과 내부 `REQUIRES_NEW`의 commit/rollback 독립성, 생성 실패 후 token-owned lock release 검증
- Scheduler 전용 쿼리를 첫 worker의 row lock이 유지되는 latch 구조로 검증해 `SKIP LOCKED` 동작 고정
- 신규 migration, frontend, REST API, WebSocket, Redis, embedding과 외부 scoring API는 추가하지 않음

멱등성 범위:

- 정상 중복 tick과 다중 인스턴스 실행은 PostgreSQL row lock, 상태 조건, `lock_token`, 단일 생성 transaction으로 중복 생성을 방지한다.
- ambiguous commit 이후 기존 attempt를 명시적 key로 조회해 반환하는 기능은 없다.
- 명시적 idempotency key와 V12는 완전 재매칭 정책과 함께 이월한다.

다음 단계로 이월:

- proposal 수락·거절·timeout과 penalty/cooldown
- 인원 미달 재확인, `allowMinimumTwo`, 완전 재매칭
- 그룹 확정과 match group/member 생성
- 명시적 attempt idempotency key
- REST API, frontend, WebSocket STOMP, Redis, embedding 및 외부 scoring API

## [10-매칭 3차] 매칭풀 정리, 정형 점수 및 2~4인 그룹 조합

상태: 운영 코드와 단위 테스트 완료, Docker Desktop 중지로 PostgreSQL 통합 테스트 재실행 필요

- 호출자가 전달한 `now`, `staleBefore`를 사용하는 `MatchPoolCleanupService` 추가
- `search_expires_at <= now`인 `WAITING`을 `EXPIRED`로 전환
- `locked_at <= staleBefore`인 정상 lock 정보의 stale `LOCKED`를 유효 기간에 따라 `WAITING` 또는 `EXPIRED`로 전환
- stale lock 회수 시 `locked_at`, `lock_token` 정리 및 상태 조건 기반 멱등성 보장
- `TravelStyleCode` 집합의 Jaccard 점수를 `BigDecimal`, 소수점 둘째 자리, `HALF_UP`으로 계산
- 한쪽 또는 양쪽 여행 스타일 입력이 비어 있으면 `0.00`으로 처리
- 같은 축제와 같은 `preferred_group_size` 후보끼리 정확히 2~4인 그룹 조합 생성
- 그룹 내부 모든 2인 pair 점수의 평균을 그룹 점수로 사용
- 그룹 점수, 오래된 `entered_at`, 작은 `pool_id` 순의 결정적 greedy 배정
- 최초 그룹 조합에서는 `allow_minimum_two`를 적용하지 않음
- 기존 V1~V11 migration과 frontend, Scheduler, attempt/proposal/group 영속화는 수정하지 않음

작성한 테스트:

- PostgreSQL 16 + pgvector Testcontainers 기반 pool 만료, stale lock 회수, 경계값, lock 정보 정리, 멱등성 통합 테스트
- Jaccard 동일/부분/무교집합, 빈 입력, 중복·순서 무관성 단위 테스트
- 2/3/4인 조합, 모든 pair 평균, 중복 배정 방지, greedy 우선순위, 동점 규칙, 입력 순서 결정성 단위 테스트

테스트 실행 결과:

- 임시 Temurin JDK 17에서 Jaccard scoring과 그룹 조합 단위 테스트 총 15건 `BUILD SUCCESSFUL`
- 운영 코드와 전체 test source의 Java compile 성공
- Docker Desktop daemon 중지로 신규 cleanup과 기존 후보 조회·선점 Testcontainers 통합 테스트는 container 초기화 전에 실패
- 전체 backend test는 61건 중 단위 테스트 57건이 통과하고, local PostgreSQL 연결 1건과 Testcontainers 초기화 3건이 실행 환경 때문에 실패
- Docker Desktop과 local PostgreSQL을 실행한 환경에서 targeted matching 통합 테스트와 전체 backend test 재실행이 필요함

다음 단계로 이월:

- 실제 `@Scheduled`와 stale timeout 운영 설정
- attempt/proposal/response 생성 및 상태 전이
- 인원 미달 재확인과 `allow_minimum_two` 적용
- 그룹 영속화와 확정
- embedding cosine similarity와 정형 점수 결합
- REST API, frontend, WebSocket STOMP, Redis

## [10-매칭 2차] PostgreSQL 기반 MatchPool 후보 동시 선점

상태: 운영 코드 작성 및 Windows PowerShell + Docker Desktop 실제 PostgreSQL 통합 테스트 완료

- 기존 일반 후보 조회 repository와 필터·정렬 테스트를 유지
- 같은 축제의 유효한 `WAITING` 후보를 제한 개수만큼 조회하는 잠금 query 추가
- 잠금 query에 `FOR UPDATE OF pool SKIP LOCKED` 적용
- `MatchPoolClaimService`의 짧은 `@Transactional` 안에서 잠금 조회와 `WAITING -> LOCKED` 전이 수행
- 선점 시 `locked_at`, `lock_token`, `updated_at`을 호출자가 전달한 기준 시각과 token으로 함께 기록
- `limit`, `lockToken` 입력 검증과 후보가 없을 때 빈 결과 반환 계약 추가
- test 전용 cleanup과 기존 fixture를 isolated transaction에서 commit한 뒤 worker transaction이 조회하도록 구성
- 두 thread와 독립된 두 transaction을 latch로 제어해 첫 worker의 잠금이 유지되는 동안 두 번째 worker가 다른 row를 선점하는 테스트 작성
- rollback 시 `WAITING` 상태와 null lock 정보가 유지되는 테스트 작성
- 기존 V1~V11 migration, Scheduler, stale lock 회수, scoring, 그룹 조합, proposal, frontend, Redis, WebSocket은 수정하지 않음

이번에 완료된 기능:

- 같은 축제의 유효한 `WAITING` 후보 잠금 조회
- PostgreSQL native query의 `FOR UPDATE OF pool SKIP LOCKED` 적용
- 선점 후보의 `WAITING -> LOCKED` 상태 전이
- 선점 시 `locked_at`, `lock_token`, `updated_at` 기록
- `limit` 양수 검증과 `lockToken` 필수·최대 100자 검증
- 상위 transaction rollback 시 상태와 lock 정보 원복

Windows PowerShell + Docker Desktop 실제 테스트:

- `MatchPoolClaimServiceIntegrationTest` 8건 통과
  - 제한 개수 선점, `locked_at`/`lock_token` 기록, `WAITING -> LOCKED` 전이 검증
  - `limit`, `lockToken` 입력값 검증과 후보 없음 시 빈 결과 계약 검증
  - `LOCKED`/`PROPOSED` 후보 제외와 기존 정렬·limit 유지 검증
  - 상위 transaction rollback 시 `WAITING`과 null lock 정보 유지 검증
  - latch로 제어한 두 독립 transaction이 대기 없이 서로 다른 pool을 선점해 중복 선점이 발생하지 않음을 검증
- `MatchPoolClaimServiceIntegrationTest`와 `MatchPoolRepositoryIntegrationTest` 회귀 실행 총 21건 통과
  - 후보 선점 8건과 기존 유효 `WAITING` 후보 조회·제외 조건·정렬·partial unique index 13건을 함께 검증
- 전체 backend test 총 64건 통과
  - tests 64, failures 0, errors 0, skipped 0
  - 빈 PostgreSQL 16 + pgvector Testcontainer에 Flyway V1~V11이 적용된 실제 PostgreSQL 환경에서 매칭 통합 테스트 통과

실행 명령과 완료 판단:

- `./gradlew.bat test --tests "com.survey.meetorsolo.domain.matching.service.MatchPoolClaimServiceIntegrationTest" --rerun-tasks`: 후보 잠금 조회, 상태·lock 정보 전이, 입력 검증, rollback, 두 독립 transaction의 중복 없는 동시 선점을 검증하며, 성공 시 후보 선점 기능 범위를 완료로 판단
- `./gradlew.bat test --tests "com.survey.meetorsolo.domain.matching.service.MatchPoolClaimServiceIntegrationTest" --tests "com.survey.meetorsolo.domain.matching.repository.MatchPoolRepositoryIntegrationTest" --rerun-tasks`: 신규 선점 기능과 기존 후보 조회 조건·정렬·제약조건의 회귀 없음을 함께 검증
- `./gradlew.bat test --rerun-tasks`: 전체 backend 64건을 재실행해 신규 매칭 선점 구현이 인증, 회원, 외부 연동, 시간 처리 등 기존 backend 테스트를 깨뜨리지 않았음을 검증

다음 단계로 이월:

- Scheduler의 pool/proposal 만료와 stale lock 회수
- 후보 점수 계산과 2~4인 그룹 조합
- attempt/proposal/response 상태 전이와 그룹 확정
- 임베딩 cosine similarity와 정형 점수 결합
- 실제 부하를 확인한 뒤 후보 잠금 query 인덱스 보완 여부 검토

## [10-매칭 1차] MatchPool 후보 조회 repository와 PostgreSQL 통합 테스트

상태: 최소 운영 구현과 실제 PostgreSQL 통합 검증 완료

- `MatchPool` 최소 JPA entity와 `MatchPoolRepository` 추가
- 후보 조회 기준 시각을 `OffsetDateTime now` parameter로 전달해 테스트와 실행 결과를 결정적으로 구성
- 같은 축제의 `WAITING` pool 중 유효한 체크인을 가진 후보만 조회
- 요청자 자신, 다른 축제, `WAITING`이 아닌 pool, 만료 pool, 만료 또는 비활성 체크인 제외
- 해당 시각에 active인 cooldown 회원 제외
- 요청자가 차단한 회원과 요청자를 차단한 회원을 양방향으로 제외
- 결과를 `entered_at`, `id` 오름차순으로 정렬
- `pgvector/pgvector:pg16` Testcontainers와 기존 V1~V11 Flyway migration을 그대로 사용
- Testcontainers 1.21.0과 Docker Engine 29의 최소 API 호환을 위해 test JVM에 `api.version=1.44` 기본값 적용
- `matching-engine-foundation.sql`을 test resource로 연결하고 만료/비활성 체크인과 역방향 차단 fixture 보강
- 운영 코드에는 mock profile, mock service, fixture 의존성, mock 조건 분기를 추가하지 않음
- 기존 V1~V11 migration과 frontend는 수정하지 않음

실제 검증 완료:

- `MatchPoolRepositoryIntegrationTest` 13건 통과
- 빈 PostgreSQL 16 + pgvector 컨테이너에 Flyway V1~V11 적용 및 `vector` extension 생성 확인
- 후보 포함과 각 제외 조건을 실제 PostgreSQL native query로 검증
- `entered_at`, `id` 순서의 결정적 정렬 검증
- `uq_match_pools_member_active` 이름까지 확인해 동일 회원 active pool 중복 차단 검증
- 종료 상태 pool 이후 같은 회원의 새 active pool 생성 허용 검증
- 기존 `MatchingScenarioFixtureTest` 4건 재실행 통과
- 전체 backend test 총 56건, failure 0, error 0, skipped 0

다음 단계로 이월:

- `FOR UPDATE SKIP LOCKED` 기반 후보 동시 선점
- Scheduler의 pool/proposal 만료와 stale lock 회수
- 후보 점수 계산과 2~4인 그룹 조합
- attempt/proposal/response 상태 전이와 그룹 확정
- 임베딩 cosine similarity와 정형 점수 결합
- active group/cooldown 및 proposal response의 나머지 unique constraint 통합 테스트
- Redis, WebSocket 상태 동기화, frontend 연동

## [10-매칭 기반] backend 매칭 엔진 테스트 fixture foundation

상태: fixture와 테스트 계약 작성 및 현재 실행 가능한 단위 테스트 완료

- 운영 matching engine, repository, Scheduler 구현 전에 재사용할 결정적 시나리오 fixture 추가
- 고정 KST 기준 시각과 V1~V11 상태값을 사용해 후보 포함/제외, 인원 미달 proposal 회차, Scheduler 만료 대상을 표현
- 격리된 PostgreSQL 통합 테스트 DB에서 transaction rollback을 전제로 사용할 `matching-engine-foundation.sql` 추가
- SQL seed는 `src/test/resources/fixtures`에만 두고 운영 profile, 운영 jar 초기화, Flyway migration에서 실행하지 않음
- 운영 코드에 mock service, mock profile, fixture 분기를 추가하지 않음
- 기존 V1~V11 migration과 frontend는 수정하지 않음

실제 실행 완료:

- `MatchingScenarioFixtureTest`에서 후보 포함/제외 데이터 구성 검증
- 같은 `attempt_id`에서 인원 미달 재확인이 새 `proposal_id`, `proposal_round=2`를 사용하는 fixture 계약 검증
- 만료된 `SENT` proposal만 Scheduler timeout 대상인 fixture 계약 검증
- SQL seed가 test classpath에 존재하고 V10 최초 제안/인원 미달 회차 데이터를 포함하는지 검증
- 실행 명령: `gradlew.bat test --tests com.survey.meetorsolo.domain.matching.fixture.MatchingScenarioFixtureTest`
- 위 targeted test는 총 4건 모두 통과
- 전체 backend `gradlew.bat test`도 실행했으며 총 42건 중 41건 통과, 기존 `UpdateMemberProfileRequestValidationTest`의 닉네임 최대 길이 검증 1건 실패
- 전체 suite 실패는 이번 fixture 파일이 아니라 기존 `UpdateMemberProfileRequestValidationTest.java:77`에서 발생했으며 이번 범위에서는 해당 운영/회원 코드를 수정하지 않음

matching engine 단계로 이월:

- SQL seed를 실제 `pgvector/pgvector:pg16` Testcontainers DB에 적용하고 V1~V11 호환성을 검증하는 통합 테스트
- 같은 축제, `WAITING`, 유효한 `search_expires_at` 후보 조회와 차단·cooldown·상태 제외 repository 테스트
- `SELECT FOR UPDATE SKIP LOCKED` 동시 선점 테스트
- active pool/group/cooldown 및 proposal response unique constraint 테스트
- 후보 선점부터 attempt/proposal 생성까지의 transaction 테스트
- 수락/거절/timeout, 인원 미달 재확인, 완전 재매칭, 그룹 단일 확정 engine 테스트
- 60초 pool 만료, 30초 proposal timeout, stale lock 회수와 재실행 멱등성 Scheduler 테스트
- 정형 여행 스타일 점수, 임베딩 보조 점수, 임베딩 실패 fallback 테스트

주의:

- 이 단계에서는 실제 matching engine, repository, Scheduler 운영 구현을 추가하지 않았다.
- 운영 구현 없이 실행할 수 없는 시나리오를 통과시키기 위한 mock service를 만들지 않았다.

## [10-공통 환경 보완] local/dev PostgreSQL pgvector 이미지 전환

상태: compose 및 문서 변경 완료, dev 서버 재배포와 Flyway 적용 확인 필요

- local/dev PostgreSQL 이미지를 PostgreSQL 16 호환 `pgvector/pgvector:pg16`으로 통일
- 기존 PostgreSQL data volume을 삭제하지 않고 컨테이너만 재생성하는 기준 명시
- 로컬 backend와 서버 backend가 같은 dev DB를 사용하는 경우 실제 dev PostgreSQL 컨테이너에 pgvector 이미지가 적용되어야 함을 반영
- 재기동 후 `vector.control`, `CREATE EXTENSION vector`, Flyway `V11__add_member_preference_embeddings.sql` 적용 이력을 확인하는 절차 정리
- 기존 Flyway migration과 실제 DB data는 수정하지 않음

## [10-공통 설계 보완] 매칭 제안 회차와 회원 취향 임베딩 DB 반영

상태: 문서 및 Flyway migration 작성 완료, 애플리케이션 코드와 실제 local/dev DB 적용 제외

- 기존 `V1`~`V9` migration을 수정하지 않고 `V10__add_matching_proposal_rounds.sql`, `V11__add_member_preference_embeddings.sql` 추가
- 동일 후보의 인원 미달 재확인은 같은 `attempt_id`에서 새로운 `proposal_id`, `proposal_round`로 저장
- 기존 attempt 종료 후 새로운 상대를 찾는 완전한 재매칭은 새로운 `attempt_id`를 생성
- `match_proposals`에 `proposal_type`, `proposal_round`를 추가하고 유일성을 `(attempt_id, member_id, proposal_round)`로 변경
- `match_responses(proposal_id, member_id)` 유일성은 한 질문의 중복 응답 방지 목적으로 유지
- 회원별 최신 자연어 취향과 임베딩을 저장하는 `member_preference_embeddings` 추가
- `member_travel_styles`는 정형 점수, `preference_text` 임베딩은 보조 유사도 점수로 분리
- `member_consents.consent_type`에 `AI_PROCESSING`, `OVERSEAS_TRANSFER` 추가
- PostgreSQL 비관적 행 잠금과 `lock_token`/`locked_at`의 애플리케이션 소유권 표시 역할을 구분해 문서화
- pgvector가 설치되지 않은 PostgreSQL 이미지에서는 `V11` 적용이 실패하므로 local/dev/prod DB 이미지와 확장 준비를 먼저 확인
- Java, frontend, docker-compose, 배포 설정, 실제 DB에는 변경을 적용하지 않음

## [10-C 보완] 닉네임 제한과 local access token 만료 테스트 설정

상태: 코드 작성 및 frontend build 완료, Gradle wrapper 다운로드 승인 후 backend validation 테스트 실행 필요

- 프로필 설정/수정 닉네임을 `2~12자`로 제한
- 한글, 영문 대소문자, 숫자만 허용하고 공백/특수문자는 거절
- `SignupPage`, `ProfileEditPage`에 동일한 닉네임 안내 문구와 client 선검증 추가
- backend `UpdateMemberProfileRequest` validation에 닉네임 길이와 허용 문자 제한 추가
- local profile의 access token 기본 만료 시간을 테스트용 `1분`으로 변경
- dev/prod profile의 access token 기본 만료 시간은 기존 `30분` 유지

## [10-C 보완] 프로필 이미지 업로드/조회

상태: 코드·문서 작성 및 frontend build 완료, Java 17 환경의 backend 테스트 실행 필요

- `V9__add_member_profile_image_object_key.sql`로 `members.profile_image_object_key` nullable 컬럼 추가
- 기존 `profile_image_url`은 Kakao/Naver OAuth 외부 URL로 유지하고 직접 업로드 object를 우선 표시
- OCI Object Storage S3 compatible client와 private bucket backend 중계 조회 구현
- JPEG/PNG/WEBP, MIME/file signature, 기본 5MB 제한 검증
- 새 업로드 성공 및 DB commit 후 기존 object 삭제, rollback 시 새 object 정리
- MyPage/ProfileEditPage 이미지 표시, placeholder fallback, 파일 선택·미리보기·업로드 UI 구현
- `.env.example`, `infra/env/.env.dev.example`, dev compose에 placeholder 환경변수 추가
- 실제 OCI secret과 dev 서버 값은 추가하지 않음
- local 실행 시 루트 `.env`를 optional Spring config로 읽도록 보완하고 Object Storage SDK 예외 cause를 서버 로그에 보존
- OCI가 반환한 `AWS chunked encoding not supported` 501 오류에 맞춰 S3 client의 chunked encoding을 비활성화하고 request checksum 계산을 required 요청으로 제한

## [10-C 보완] MyPage 프로필 수정

- 기존 MyPage 레이아웃과 하단 탭바를 유지하고 프로필 카드에 수정 진입 버튼 추가
- `/profile/edit` 화면을 기존 프로필 설정 화면의 입력·Chip·색상 체계로 구성
- nickname, nullable email, nullable 한 줄 소개, 성별, 연령대, 여행 스타일 수정 지원
- `V8__add_member_intro.sql`로 `members.intro` nullable 컬럼 추가
- MyPage에서 email/소개 미등록 안내 문구 표시

## [10-C 보완] 회원 프로필 표시 및 Refresh Token rotation

- 회원당 Refresh Token 1개 정책으로 변경하고 재로그인 시 기존 row의 hash와 만료시각을 갱신
- `V7__single_refresh_token_and_member_email.sql`에서 기존 중복 token row는 최신 1개만 보존하고 `UNIQUE(member_id)` 추가
- `members.email`을 nullable, non-unique 참고 정보로 추가하며 이메일 기반 조회·병합은 하지 않음
- ACTIVE 회원 재로그인 시 프로필 설정 nickname을 OAuth nickname으로 덮어쓰지 않도록 보완
- Home/MyPage의 `mockUser` 표시를 `/api/members/me` 실제 프로필 응답으로 교체
- Refresh Token 만료 설정을 분 단위 `JWT_REFRESH_TOKEN_EXPIRES_MINUTES`로 변경해 local에서 1분 만료 테스트를 지원하고 dev/prod 기본값은 14일에 해당하는 `20160`분으로 유지
- local token 만료 테스트 값을 Access/Refresh 각각 30분으로 조정하고 frontend 공통 `apiClient`가 `401 UNAUTHORIZED`를 받으면 `/login`으로 이동하도록 보완

## [10-C] Naver OAuth 로그인 추가

상태: 코드 및 테스트 작성 완료, Java 17 환경의 backend 테스트 실행 필요

- 기존 Kakao OAuth, JWT, Refresh Token, 프로필/여행 스타일 흐름을 유지하고 Naver OAuth를 같은 `domain/auth` 흐름에 연결
- `external/naver` client와 DTO 추가, connect/read timeout 및 안전한 오류 로그 적용
- provider별 HttpOnly state 쿠키와 callback 검증/즉시 삭제 적용
- `(provider, provider_user_id)` 식별을 유지하고 동일 이메일 자동 병합을 하지 않음
- 기존 migration을 수정하지 않고 `V6__allow_naver_oauth_provider.sql`로 provider CHECK에 `NAVER` 추가
- 로그인 화면에 모바일 대응 네이버 텍스트 버튼과 중복 클릭 방지 상태 추가

이 문서는 `meet-or-solo`의 현재 진행 상태와 다음 작업 순서를 기록합니다. 새 작업을 시작하기 전에 반드시 이 문서를 확인하고, 현재 단계에 맞는 작업만 수행합니다.

## 1. WBS 기준 전체 단계

현재 WBS 흐름은 다음 순서를 기준으로 합니다.

```text
개발환경 세팅
-> CI/CD 세팅
-> Front 공통 코드화
-> Backend 공통 코드화
-> 이후 풀스택 A/B 기능 분업
```

다만 실제 작업 안정성을 위해 이 저장소에서는 Backend 공통 코드화와 Frontend 공통 코드화를 먼저 마무리한 뒤, Oracle VM dev 서버/dev DB 구축 준비, nginx/docker-compose dev 배포 초안, GitHub Actions CI와 dev CD 초안을 잡고 기능 분업으로 넘어갑니다.

## 2. 현재 완료된 단계

### [0단계] 프로젝트 방향/문서화 완료

상태: 완료

- `README.md`, `AGENTS.md`, `CLAUDE.md` 작성
- `docs/00_PROJECT_OVERVIEW.md`부터 `docs/09_TEST_AND_QUALITY_STRATEGY.md`까지 문서 작성
- 문서는 한국어 중심으로 작성하고, 기술명/명령어/경로/env 이름은 영어 원문을 유지한다.
- Redis는 MVP 초기 단계에서 제외한다.
- WebSocket STOMP는 상태 동기화용이며 자유 채팅이 아니다.

### [1단계] Backend + Local PostgreSQL + Flyway 확인 완료

상태: 완료

- backend Spring Boot 실행 확인
- `GET /api/health` 확인
- `docker-compose.local.yml` 기반 local PostgreSQL 컨테이너 실행 확인
- `.env` 기반 PostgreSQL 컨테이너 환경변수 확인
- `psql`로 local PostgreSQL 접속 확인
- `select * from flyway_schema_history;` 조회 성공
- 현재 `V1__init.sql`은 DB/Flyway 연결 확인용 초기 migration이다.
- 실제 서비스 DB 테이블은 아직 만들지 않았다.

### [2단계] local/dev/prod 실행 전략 정리 완료

상태: 완료

- `local`: 개인 PC Docker PostgreSQL
- `dev`: Oracle Cloud VM 개발/시연용 서버
- `prod`: 추후 제출/운영 단계에서 분리
- 현재 VM에는 `dev`만 배포하는 방향으로 결정
- `prod`는 추후 별도 디렉터리, 별도 DB, 별도 도메인 또는 외부 DB 서비스로 분리 가능하게 설계
- 초기 서버 배포는 `SPRING_PROFILES_ACTIVE=dev`를 사용
- PostgreSQL `5432`는 외부 전체 공개하지 않는다.
- DB 직접 접속이 필요하면 SSH tunnel 방식을 우선 고려한다.
- local 실행 시 `docker compose`는 프로젝트 루트에서 `--env-file .env`와 함께 실행한다.
- Spring Boot `bootRun`은 `.env`를 자동으로 읽지 않는다.
- PowerShell과 Git Bash의 환경변수는 서로 공유되지 않는다.
- Git Bash에서 `source .env`를 했다면 같은 Git Bash 터미널에서 `./gradlew bootRun`까지 실행한다.
- PowerShell에서 실행할 경우 `application-local.yml` fallback 값으로 실행하거나 PowerShell 환경변수를 직접 설정한다.

### [3단계] Frontend PWA 기본 스캐폴딩 + `/api/health` 연동 완료

상태: 완료

- `frontend`에 React + TypeScript + Vite 기본 구조를 구성했다.
- `vite-plugin-pwa` 기반 PWA 기본 shell을 구성했다.
- `manifest`의 앱 이름은 `meet-or-solo`로 설정했다.
- 아이콘은 `public/icons/placeholder.svg` placeholder로 두었다.
- `frontend/.env.local.example`, `frontend/.env.production.example`에 `VITE_API_BASE_URL` 예시를 추가했다.
- local 개발에서는 `VITE_API_BASE_URL`을 비워두고 상대 경로 `/api/health`와 Vite proxy를 사용한다.
- Vite proxy로 `/api` 요청을 backend `localhost:8080`으로 전달한다.
- `HealthCheckPage`에서 backend `GET /api/health` 연동을 확인했다.
- 현재 PWA는 기본 shell, manifest, service worker 생성 설정, placeholder icon 수준이다.
- `frontend/dist/`는 build 결과물이므로 커밋하지 않는다.
- 현재 frontend 화면은 개발 연결 확인용이며 실제 서비스 UI가 아니다.

### [4단계] Backend 공통 코드화 완료

상태: 완료

완료 항목:

- `ApiResponse` 기반 공통 응답 포맷 추가
- `ErrorResponse` 기반 공통 에러 응답 구조 추가
- `ErrorCode`
- `BusinessException`
- `GlobalExceptionHandler`
- validation 에러 응답 공통 포맷 적용
- `/api/**` CORS 설정 추가
- local 기본 CORS origin: `http://localhost:5173`
- dev/prod CORS origin은 `CORS_ALLOWED_ORIGINS` 환경변수 기반으로 확장 가능하게 구성
- `HealthController` 응답을 공통 `ApiResponse` 포맷으로 변경

주의:

- 아직 비즈니스 기능은 구현하지 않는다.
- 실제 서비스 DB 테이블은 만들지 않는다.
- DB migration은 추가하지 않는다.
- 인증, 매칭, 축제, 체크인, 신고 기능은 구현하지 않는다.
- 응답에 stack trace, DB URL, 환경변수, 내부 예외 상세를 노출하지 않는다.
- 5단계 Frontend 공통 코드화에서 frontend `healthApi`와 `HealthCheckPage`를 새 `ApiResponse` 포맷에 맞게 수정했다.

### [5단계] Frontend 공통 코드화 완료

상태: 완료

- `ApiResponse<T>`, `ApiError`, `FieldError` 타입 추가
- fetch 기반 공통 `apiClient` 추가
- local 개발에서 Vite proxy와 `/api/...` 상대 경로 사용 기준 유지
- 추후 dev/prod에서 `VITE_API_BASE_URL`을 사용할 수 있도록 구조 유지
- 새 backend `ApiResponse` 포맷에 맞춘 `healthApi` 수정
- 새 backend `ApiResponse` 포맷에 맞춘 `HealthCheckPage` 수정
- loading/error UI는 `HealthCheckPage` 안에서 최소 상태로 유지
- React Router, 디자인 시스템, 실제 서비스 화면은 도입하지 않음

주의:

- 실제 서비스 화면은 구현하지 않는다.
- Kakao OAuth, JWT, 축제/매칭/체크인/신고 기능은 구현하지 않는다.
- backend 코드, DB migration, nginx, docker-compose, GitHub Actions, 테스트 코드는 수정하지 않는다.

### [6단계] Oracle VM dev 서버/dev DB 구축 준비 완료

상태: 완료

완료 항목:

- `/home/ubuntu/meet-or-solo` 기준 dev 서버 폴더 구조 문서화
- `backend/app.jar`, `frontend/dist`, `nginx/default.conf`, `data/postgres`, `logs`, `.env` 역할 정리
- Oracle VM 내부 PostgreSQL dev DB 기준 정리
- DB 이름 예시 `meet_or_solo_dev` 문서화
- DB user/password는 `.env` 또는 GitHub Secrets에서 주입하고 실제 값을 하드코딩하지 않는 원칙 정리
- PostgreSQL `5432` 외부 전체 공개 금지 원칙 재확인
- backend와 PostgreSQL은 같은 VM 내부 네트워크 또는 localhost 경계에서 통신하는 방향 정리
- 팀원 dev DB 직접 접근은 SSH tunnel을 우선 사용하는 방향 정리
- backend `application-dev.yml` 기준 환경변수 목록 정리
- frontend local 개발은 `npm run dev`와 Vite proxy, dev 서버 배포는 `npm run build` 결과물인 `frontend/dist`를 사용하는 기준 정리
- `frontend/dist/`는 Git에 커밋하지 않는 원칙 재확인
- nginx가 `frontend/dist`를 서빙하고 `/api`를 backend로 reverse proxy하는 방향 정리
- 실제 nginx 설정 파일은 7단계에서 작성한다고 명시
- 7단계에서 만들 파일 후보만 문서화

주의:

- 실제 Oracle VM에 접속하지 않았다.
- 실제 파일을 서버에 배포하지 않았다.
- nginx 설정 파일을 만들지 않았다.
- docker-compose dev/prod 파일을 만들지 않았다.
- GitHub Actions 파일을 만들지 않았다.
- backend/frontend 코드, DB migration, 실제 서비스 테이블, 테스트 코드는 수정하지 않았다.
- 실제 IP, 도메인, DB 계정, 비밀번호, API Key, Secret은 작성하지 않았다.

## 3. 이후 단계 순서

### [7단계] nginx + docker-compose dev 배포 초안

상태: 완료

완료 항목:

- `infra/docker/docker-compose.dev.yml` 추가
- `postgres`, `backend`, `nginx` service를 compose 내부 network로 연결
- `postgres`는 최초 `postgres:16-alpine` 기준으로 작성했으며, 이후 `V11` pgvector 요구사항에 맞춰 `pgvector/pgvector:pg16`으로 전환
- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`는 환경변수로 주입
- PostgreSQL data volume 후보를 `data/postgres`로 구성
- 팀원 dev DB 확인을 위한 SSH tunnel 고정 목적지로 PostgreSQL을 host loopback `127.0.0.1:15432`에만 publish
- `backend`는 Spring Boot jar를 `backend/app.jar`로 mount해 `java -jar`로 실행
- `backend`는 `SPRING_PROFILES_ACTIVE=dev` 기준으로 실행
- `DB_URL`은 compose 내부 service name `postgres` 기준으로 예시 구성
- `backend 8080`은 외부에 직접 publish하지 않음
- `postgres 5432`는 외부에 직접 publish하지 않고 서버 내부 `127.0.0.1:15432`에만 publish
- `nginx`는 기존 운영 nginx와 host `80` 충돌을 피하기 위해 외부 `18080` 포트로 publish
- `infra/nginx/default.dev.conf` 추가
- nginx가 `frontend/dist` 정적 파일을 서빙하고 SPA fallback을 적용하도록 구성
- `/api/` 요청을 `backend:8080`으로 reverse proxy
- `/ws/` 경로는 실제 구현 전 placeholder 주석으로만 남김
- HTTPS/Certbot/domain 설정은 추가하지 않음
- `infra/env/.env.dev.example` 추가
- 실제 서버 `.env`는 Oracle VM에서 서버 관리자가 직접 생성한다고 문서화
- backend jar와 frontend dist 산출물 배치 기준 문서화
- `.gitignore`에 실제 env, key, log, build/data 산출물 ignore 기준 보강

주의:

- 실제 Oracle VM에 접속하지 않았다.
- 실제 배포하지 않았다.
- GitHub Actions 파일을 만들지 않았다.
- prod docker-compose를 만들지 않았다.
- prod nginx 설정을 만들지 않았다.
- backend/frontend 기능 코드, DB migration, 실제 서비스 테이블, 테스트 코드는 수정하지 않았다.
- 실제 IP, 도메인, DB 계정, 비밀번호, API Key, Secret은 작성하지 않았다.

### [8-1단계] GitHub Actions CI 초안

상태: 완료

완료 항목:

- `.github/workflows/ci.yml` 추가
- `pull_request` to `dev` trigger 추가
- `pull_request` to `main` trigger 추가
- `push` to `dev` trigger 추가
- `push` to `main` trigger 추가
- `backend-build` job 추가
- backend CI에서 Java 17 설정
- backend CI에서 Gradle cache 적용
- backend CI에서 `./gradlew build -x test` 실행
- backend CI에서 `bootRun`, DB 연결, PostgreSQL 컨테이너 실행 제외
- `frontend-build` job 추가
- frontend CI에서 Node.js 20 설정
- frontend CI에서 npm cache 적용
- frontend CI에서 `npm ci`, `npm run build` 실행
- CI는 compile/build 검증만 수행하고 자동 배포/CD는 하지 않음

주의:

- 실제 Oracle VM에 접속하지 않았다.
- SSH 배포를 구성하지 않았다.
- `docker compose up`을 실행하지 않았다.
- 서버 `.env`를 생성하지 않았다.
- GitHub Secrets를 사용하지 않았다.
- backend/frontend 기능 코드, DB migration, 실제 서비스 테이블, nginx/docker-compose prod, 테스트 코드는 수정하지 않았다.
- 실제 IP, 도메인, DB 계정, 비밀번호, API Key, Secret은 작성하지 않았다.

### [8-2단계] GitHub Actions dev CD 초안

상태: 완료

완료 항목:

- `.github/workflows/deploy-dev.yml` 추가
- `push` to `dev` 자동 실행 trigger 추가
- `workflow_dispatch` 수동 재배포 trigger 유지
- backend를 Java 17로 `bootJar -x test` 빌드하는 단계 추가
- frontend를 Node.js 20으로 `npm ci`, `npm run build`하는 단계 추가
- backend jar를 `backend/app.jar` 이름으로 배포 패키지에 포함
- frontend `dist`를 배포 패키지에 포함
- `infra/docker/docker-compose.dev.yml`을 배포 패키지에 포함
- `infra/nginx/default.dev.conf`를 배포 패키지에 포함
- Flyway migration은 `backend/src/main/resources/db/migration`에서 backend jar에 포함하는 기준으로 정리
- GitHub Secrets 이름 후보 사용
- `DEV_SERVER_HOST`
- `DEV_SERVER_USER`
- `DEV_SSH_KEY`
- `DEV_DEPLOY_PATH`
- 서버 `.env`는 GitHub Actions가 만들지 않고 Oracle VM에서 서버 관리자가 직접 생성하는 기준으로 문서화
- 서버 `.env`가 없으면 workflow가 실패하도록 초안 작성
- `docker compose --env-file .env -f infra/docker/docker-compose.dev.yml up -d --force-recreate` 실행 기준으로 배포 후 컨테이너 재생성
- CD 실행 전 Oracle VM 준비 항목과 실패 시 확인 항목 문서화
- Oracle VM에서 dev compose 수동 검증 중 `eclipse-temurin:17-jre-alpine`의 ARM64 manifest 문제를 확인해 `eclipse-temurin:17-jre-jammy` 기준으로 정리
- 기존 운영 nginx가 host `80`을 사용 중인 VM에서 dev compose nginx는 host `18080`으로 검증하는 기준으로 정리
- 서버 내부 `curl http://localhost:18080/api/health` 응답 성공 확인

주의:

- 실제 Oracle VM dev compose 수동 검증은 수행했으나, 실제 Secret 값은 문서화하지 않았다.
- 실제 Secret 값을 작성하지 않았다.
- prod 배포 성공을 가정하지 않았다.
- 실제 IP, 도메인, DB 계정, 비밀번호, API Key, Secret은 작성하지 않았다.
- backend/frontend 기능 코드, DB migration, 실제 서비스 테이블, prod 설정, 테스트 코드는 수정하지 않았다.
- prod workflow를 만들지 않았다.
- prod docker-compose를 만들지 않았다.
- prod nginx 설정을 만들지 않았다.

### [8-3단계] Oracle VM dev 배포 수동 검증 완료

상태: 완료

완료 항목:

- Oracle VM에서 meet-or-solo dev 배포 수동 검증 완료
- `postgres` 컨테이너 Healthy 상태 확인
- `backend` 컨테이너 Running 상태 확인
- `nginx` 컨테이너 Started 상태 확인
- 서버 내부 `curl http://localhost:18080/api/health` 성공 확인
- 외부 브라우저 `http://<DEV_SERVER_HOST>:18080/api/health` 성공 확인
- health 응답 확인

```json
{"success":true,"data":{"status":"OK","service":"meet-or-solo-backend"},"error":null}
```

dev 서버 기준:

- dev 서버 접속 주소는 `http://<DEV_SERVER_HOST>:18080`
- health API 확인 주소는 `http://<DEV_SERVER_HOST>:18080/api/health`
- dev `CORS_ALLOWED_ORIGINS` 기준은 `http://<DEV_SERVER_HOST>:18080`
- 기존 Ubuntu nginx 또는 다른 서비스가 host `80`을 사용할 수 있으므로 현재 meet-or-solo dev는 host `80`을 사용하지 않음
- Oracle Cloud Ingress에서 `18080` 포트가 열려 있어야 함
- backend `8080`과 PostgreSQL `5432`는 외부에 직접 공개하지 않음
- PostgreSQL dev DB 직접 확인은 SSH tunnel `local 15432 -> server localhost 15432 -> postgres 5432` 기준으로 사용

주의:

- 실제 IP는 문서에 기록하지 않고 `<DEV_SERVER_HOST>` placeholder를 사용한다.
- 실제 DB 비밀번호, Secret, API Key는 작성하지 않았다.
- backend/frontend 기능 코드, DB migration, 실제 서비스 테이블, docker-compose, nginx 설정, GitHub Actions workflow는 수정하지 않았다.
- prod 배포는 아직 하지 않았다.

### [8-4단계] 협업 브랜치와 dev 자동 배포 기준 정리 완료

상태: 완료

완료 항목:

- `main`은 운영 또는 안정 버전 기준 브랜치로 둔다.
- `dev`는 개발 통합과 dev 서버 자동 배포 기준 브랜치로 둔다.
- 기능 작업은 작업자별 feature 브랜치에서 진행하고 PR로 `dev`에 병합한다.
- `dev`에 push되면 `Deploy Dev` workflow가 자동 실행된다.
- `Deploy Dev`는 수동 재배포를 위해 `workflow_dispatch`도 유지한다.
- dev 배포 시 `docker compose up -d --force-recreate`를 사용해 새 backend jar와 frontend dist가 컨테이너에 반영되도록 한다.

주의:

- prod 자동 배포는 아직 하지 않는다.
- 실제 GitHub collaborator 초대는 repository Settings에서 사용자가 직접 수행한다.
- 실제 IP, 도메인, DB 계정, 비밀번호, API Key, Secret은 작성하지 않았다.

### [9-1단계] 실제 서비스 DB 설계 검토/확정

상태: 완료

완료 항목:

- `docs/11_DATABASE_DESIGN.md` 추가
- 실제 서비스 DB 테이블 후보를 MVP 필수와 추후 분리 후보로 구분
- `members`, `festivals`, `festival_checkins`, `match_pools`, `match_attempts`, `match_proposals`, `match_groups`, `reports` 등 핵심 테이블 설계안 정리
- 각 테이블별 목적, 주요 컬럼, PK, FK, 상태값, CHECK constraint 후보, UNIQUE constraint 후보, INDEX 후보, 개인정보/보안 고려사항, MVP 필수 여부 정리
- PostgreSQL 기준으로 `VARCHAR` + `CHECK constraint` 상태값 전략 정리
- 원본 GPS 좌표를 저장하지 않는 체크인 설계 원칙 재확인
- 자유 채팅 테이블을 만들지 않는 기준 재확인
- Redis 없이 PostgreSQL `status`, `expires_at`, `locked_at`, transaction lock, partial unique index를 활용하는 방향 정리
- 다음 9-2단계 Flyway SQL 파일 분리안 정리

주의:

- 실제 DB migration을 적용하지 않았다.
- `backend/src/main/resources/db/migration/V1__init.sql`은 수정하지 않았다.
- backend/frontend 기능 코드, nginx, docker-compose, GitHub Actions workflow는 수정하지 않았다.
- 실제 Oracle VM에 접속하지 않았다.
- 실제 DB migration을 적용하지 않았다.
- 실제 IP, 도메인, DB 계정, 비밀번호, API Key, Secret은 작성하지 않았다.

### [9-2단계] 실제 서비스 DB 테이블/Flyway migration

상태: 파일 작성 완료, dev DB 적용 확인 필요

- 9-1단계에서 확정한 `docs/11_DATABASE_DESIGN.md` 기준으로 `V2` 이후 migration 작성
- `backend/src/main/resources/db/migration/V2__create_core_tables.sql` 작성
- `backend/src/main/resources/db/migration/V3__create_matching_tables.sql` 작성
- `backend/src/main/resources/db/migration/V4__create_safety_admin_recommendation_tables.sql` 작성
- Spring Boot/Flyway 기본 classpath 경로인 `classpath:db/migration` 기준으로 migration 위치 단일화
- backend jar에 migration SQL이 포함되도록 `backend/src/main/resources/db/migration`을 표준 위치로 사용
- dev 배포 시 migration SQL을 별도 디렉터리로 서버에 복사하거나 컨테이너에 mount하지 않음
- 이미 적용된 migration은 수정하지 않고 새 버전으로 추가
- local/dev DB 모두 Flyway로 동일한 schema를 적용
- 실제 dev DB 적용 여부는 재배포 후 backend 로그, `flyway_schema_history`, `information_schema.tables`로 확인

### [10단계] 풀스택 A/B 기능 분업 시작

상태: 진행 중

- A/B가 공통 환경 기준으로 기능 개발 시작
- A 예시: 관광 API, 축제 목록/상세, 추천/솔로코스, 매칭 일부
- B 예시: Kakao OAuth, JWT, 회원/프로필, 체크인, 신고/평가
- 실제 담당 범위는 WBS에 맞춰 조정

#### [10-B] Kakao 로그인 프로필 여행 스타일 저장 보완

상태: 코드 작성 완료, 실제 dev DB 적용 제외

- 기존 `V1`~`V4` migration을 수정하지 않고 `V5__create_member_travel_styles.sql` 추가
- `member_travel_styles`에 회원별 여행 스타일 code 저장
- 프로필 완료 요청의 `travelStyles`를 1~3개로 검증하고 중복·미허용 code를 거절
- 여행 스타일 code를 `RELAXED`, `ACTIVE`, `FOOD`, `PHOTO`, `CULTURE`로 고정
- 프로필 완료 트랜잭션에서 기존 스타일 삭제 후 새 스타일 저장 및 `ACTIVE` 상태 변경
- 기존 `GET /api/members/me` 응답에 여행 스타일 code와 label 포함
- 성별·연령대 AES-256-GCM 암호화 정책 유지
- frontend 프로필 설정 화면은 화면 label과 API code를 분리하고 code 배열을 전송
- nginx, docker-compose, GitHub Actions, Oracle VM, 실제 dev DB migration은 수정하거나 실행하지 않음

#### [10-공통] 날짜·시간 저장 및 한국 시간 표시 기준 정리

상태: 코드 작성 및 로컬 테스트 완료

- 기존 Flyway `TIMESTAMPTZ` 컬럼과 실제 저장 시점을 유지
- Entity `OffsetDateTime` 생성 기준을 `Asia/Seoul`로 통일
- JVM, Hibernate JDBC, Jackson의 timezone을 `Asia/Seoul`로 명시
- local/dev container에 `TZ=Asia/Seoul`, PostgreSQL client session에 `PGTZ=Asia/Seoul` 적용
- REST API는 KST offset의 ISO-8601 계약을 사용하고 frontend에서 중복 보정 없이 표시
- frontend 공통 formatter를 `yyyy-MM-dd HH:mm:ss` 형식과 null 안전 처리로 구성
- 기존 migration 수정 및 신규 migration 추가 없음
- dev Database timezone 영구 기본값은 `scripts/set-dev-db-timezone.sql`로 수동 적용
- local/dev PostgreSQL compose 실행 명령에 `-c timezone=Asia/Seoul`을 추가해 server와 신규 client session의 기본 표시 timezone을 KST로 강제

## 4. 기능 분업 전까지 남은 작업

기능 분업을 시작하기 전에 공통 개발환경, dev 배포 초안, CI/CD 초안 정리를 완료했습니다. 다음 작업은 별도 승인 후 아래 중 하나로 진행합니다.

1. 기능 분업 전 최종 점검
2. dev 서버 재배포 후 Flyway V1~V4 인식 및 dev DB 적용 확인
3. [10단계] 풀스택 A/B 기능 분업 시작

## 5. 현재 아직 하지 않은 것

- dev DB에서 V1~V4 Flyway migration 적용 확인
- 실제 서비스 React 화면 구현
- Kakao OAuth 로그인
- JWT 인증/인가
- 축제 목록/상세 기능
- 체크인 기능
- 매칭 알고리즘
- WebSocket STOMP
- `MatchRoomPage`
- 신고/제재 기능
- 테스트 코드
- prod nginx 설정
- prod docker-compose 배포 구성
- prod 배포

## 6. 현재까지 생성/수정된 주요 파일

- `.env.example`
- `docker-compose.local.yml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-local.yml`
- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/resources/application-prod.yml`
- `backend/src/main/java/.../global/health/HealthController.java`
- `backend/src/main/java/.../global/response/ApiResponse.java`
- `backend/src/main/java/.../global/error/ErrorCode.java`
- `backend/src/main/java/.../global/error/ErrorResponse.java`
- `backend/src/main/java/.../global/exception/BusinessException.java`
- `backend/src/main/java/.../global/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/.../global/config/CorsConfig.java`
- `backend/src/main/resources/db/migration/V1__init.sql`
- `backend/src/main/resources/db/migration/V2__create_core_tables.sql`
- `backend/src/main/resources/db/migration/V3__create_matching_tables.sql`
- `backend/src/main/resources/db/migration/V4__create_safety_admin_recommendation_tables.sql`
- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/vite.config.ts`
- `frontend/index.html`
- `frontend/src/App.tsx`
- `frontend/src/main.tsx`
- `frontend/src/vite-env.d.ts`
- `frontend/src/api/types.ts`
- `frontend/src/api/apiClient.ts`
- `frontend/src/api/healthApi.ts`
- `frontend/src/pages/HealthCheckPage.tsx`
- `frontend/src/styles/global.css`
- `frontend/public/icons/placeholder.svg`
- `frontend/.env.local.example`
- `frontend/.env.production.example`
- `infra/docker/docker-compose.dev.yml`
- `infra/nginx/default.dev.conf`
- `infra/env/.env.dev.example`
- `.github/workflows/ci.yml`
- `.github/workflows/deploy-dev.yml`
- `README.md`
- `AGENTS.md`
- `CLAUDE.md`
- `docs/*.md`
- `docs/11_DATABASE_DESIGN.md`

## 7. 작업 규칙

- 새 작업을 시작하기 전 `docs/10_PROGRESS_LOG.md`를 먼저 확인한다.
- 현재 완료 단계와 다음 작업 단계를 확인한 뒤, 현재 단계에 맞는 작업만 수행한다.
- 기능 구현 전 작업 범위를 먼저 제안하고 사용자 승인을 받는다.
- 파일 생성/수정 전에는 변경 계획을 먼저 제안한다.
- 이미 적용된 migration 파일은 수정하지 않는다.
- `V1__init.sql`은 불필요하게 수정하지 않는다.
- 실제 비밀번호, API Key, Secret, 서버 IP, 도메인은 하드코딩하지 않는다.
- 사용자가 문서만 요청했다면 backend, frontend, DB migration, nginx, docker-compose, GitHub Actions, test 파일을 수정하지 않는다.
