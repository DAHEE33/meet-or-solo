# Proposal 조기 종료와 타이머 동기화

## 1. 목적과 상태

- 상태: `COMPLETED`
- 다음 작업 브랜치: `fix/wbs-10-b-proposal-termination-timer-sync`
- 차단·신고·차단 해제 기능을 완료한 뒤 별도 결함 수정 단위로 진행합니다.
- Backend/Frontend 구현과 자동 검증, 두 브라우저 핵심 수동 검증을 완료했습니다.

## 2. 2026-08-14 재현 결과

두 브라우저의 최초 매칭 proposal에서 한 회원이 거절했지만 다른 화면은 응답 대기를
계속했습니다. proposal 만료 뒤 양쪽 모두 종료 화면으로 이동했으며 재신청 가능 시간이
한쪽은 약 30초, 다른 쪽은 약 2분으로 표시됐습니다.

현재 코드 기준 원인은 다음과 같습니다.

1. 최초 proposal의 `REJECTED`는 해당 회원 proposal과 attempt member만 terminal로 만듭니다.
2. `MatchProposalResponseService.completeInitialRoundIfReady()`는 모든 attempt member가
   `PROPOSED`를 벗어날 때까지 전체 attempt 종료를 보류합니다.
3. 남은 회원이 응답하지 않으면 Scheduler가 `TIMEOUT`으로 처리합니다.
4. 거절 회원은 `REJECT` 30초 cooldown, timeout 회원은 `TIMEOUT` 2분 cooldown을 받습니다.
5. Frontend는 각 cooldown의 실제 `expiresAt`을 표시하므로 큰 시간 차이는 렌더링 오차가
   아니라 서로 다른 귀책 처리 결과입니다.

이 동작은 이미 성사 불가능한 2인 proposal에서 비거절 회원에게 timeout 귀책을 부여하므로
수정 대상입니다.

## 3. 목표 정책

### 3.1 2인 최초 proposal

한 명이 `REJECTED`를 제출하면 다음 transaction에서 즉시 종료합니다.

```text
REJECTED 저장
→ attempt 성사 불가능 판정
→ 미응답 상대 비귀책 종료
→ 거절자만 REJECT 30초 cooldown
→ 상대 penalty/cooldown 없음
→ pool/attempt/proposal 상태 원자적 정리
→ commit 뒤 양쪽 WebSocket 알림
```

상대에게 누가 거절했는지는 노출하지 않습니다.

### 3.2 3~4인 최초 proposal

응답이 하나 들어올 때마다 다음 가능성을 다시 계산합니다.

- 목표 인원 전원 수락 가능성이 남아 있으면 계속 대기합니다.
- 목표 인원은 불가능하지만 최소 2인 재확인 조건이 가능하면 필요한 응답까지 대기합니다.
- 목표 인원 확정과 최소 2인 진행이 모두 불가능해진 순간 즉시 종료합니다.
- 조기 종료 시 아직 응답하지 않은 회원은 비귀책이며 timeout penalty/cooldown을 받지 않습니다.
- 이미 제출된 각 회원의 명시적 응답에 해당하는 정책만 적용합니다.

### 3.3 MatchRoom 취소와 구분

이 작업은 확정 전 proposal 응답 처리입니다. 확정 후 MatchRoom의 `못 갈 것 같아요`는 기존
정책을 유지합니다.

- 확정 후 정확히 3분까지 무패널티 취소
- 3분 이후부터 도착 마감 전까지 취소 횟수별 penalty/cooldown
- 2인 group에서 한 명 취소 시 group `CANCELLED`, 상대는 비귀책 `LEFT`

## 4. 타이머 동기화 목표

현재 Frontend의 countdown은 브라우저 `Date.now()`에 직접 의존합니다. 다음 계약을
검토하고 구현합니다.

- matching snapshot 또는 공통 API 응답에서 `serverNow`를 제공합니다.
- Frontend는 `serverNow - clientNow` offset을 계산합니다.
- countdown 기준 시각은 `Date.now() + offset`으로 통일합니다.
- REST refresh마다 offset을 재보정하되 화면 시간이 뒤로 크게 튀지 않게 정책을 정합니다.
- WebSocket payload로 상태나 시간을 확정하지 않고 기존처럼 REST refresh만 유도합니다.
- 탭이 다시 visible이 되면 즉시 REST refresh합니다.
- 0초 도달 시 Frontend가 timeout을 만들지 않고 Backend 상태를 다시 조회합니다.
- 같은 attempt·round의 모든 회원 proposal이 동일한 논리 deadline을 갖는지 Backend에서
  검증합니다.

같은 기기에서 발생하는 1초 안팎 렌더링 차이와 서로 다른 cooldown 정책은 구분해서
표현합니다. 조기 종료 후 비귀책 회원에게 cooldown이 없어지면 종료 화면에서도 그 차이가
명확히 설명되어야 합니다.

## 5. 종료 화면 문구

- 직접 거절: `매칭 제안을 거절했어요.`
- 비귀책 종료: `이번 매칭을 진행할 수 없어 종료됐어요.`
- 본인 timeout: `응답 시간이 지나 매칭이 종료됐어요.`
- 시스템 종료: `이번 매칭을 진행할 수 없어요.`

거절 회원 identity와 다른 회원의 상세 응답은 공개하지 않습니다.

## 6. Backend 구현·테스트 범위

- attempt row 선잠금과 기존 proposal/attempt member/pool 잠금 순서를 유지합니다.
- 응답과 timeout, 두 명의 동시 응답 및 조기 종료 race를 실제 PostgreSQL transaction으로
  검증합니다.
- 조기 종료 transaction에서 response, proposal, attempt member, pool, attempt,
  cooldown/penalty와 event가 함께 commit되거나 rollback되어야 합니다.
- commit 전 WebSocket 전송은 금지하고 `AFTER_COMMIT`만 사용합니다.
- 2인 거절 즉시 종료와 비귀책 상대의 cooldown 0건을 검증합니다.
- 3~4인의 계속 대기, 최소 인원 재확인 가능, 즉시 불가능 경계를 각각 검증합니다.
- 거절과 마지막 수락, 거절과 Scheduler timeout 경합에서 단일 terminal 결과를 검증합니다.
- 동일 응답 재전송 멱등성과 기존 거절 상대 exclusion을 회귀 검증합니다.
- matching 전체와 Backend 전체 테스트를 실행합니다.

## 7. Frontend 구현·테스트 범위

- 서버 시각 offset 계산과 deadline countdown을 순수 함수로 분리합니다.
- 서로 다른 client clock 편차에서도 같은 deadline이 동일한 남은 시간을 표시하는지 검증합니다.
- REST 갱신, WebSocket 재연결, polling과 visibility 복원을 검증합니다.
- 조기 종료 알림 수신 뒤 응답 대기 화면이 즉시 terminal 화면으로 바뀌는지 검증합니다.
- 본인 귀책과 비귀책 종료 문구, cooldown 표시 유무를 검증합니다.
- 기존 MatchRoom, 신고·차단·차단 관리 화면을 회귀 검증합니다.
- Frontend 전체 Vitest, `npx tsc --noEmit`과 production/PWA build를 실행합니다.

## 8. 수동 검증

1. 두 회원으로 동일 2인 proposal을 받습니다.
2. 양쪽 deadline과 countdown 차이를 기록합니다.
3. 한쪽이 거절하면 다른 쪽도 polling timeout을 기다리지 않고 종료되는지 확인합니다.
4. 거절자만 30초 cooldown, 상대는 cooldown 없음인지 DB와 화면에서 확인합니다.
5. 상대 화면에 거절자 identity가 노출되지 않는지 확인합니다.
6. 두 브라우저의 시스템 시각을 의도적으로 다르게 하거나 clock offset을 테스트 주입해도
   서버 deadline 기준 표시가 일치하는지 확인합니다.
7. WebSocket 차단 상태에서는 polling으로 동일 terminal 결과가 복원되는지 확인합니다.
8. 새로고침과 탭 비활성화·복귀 뒤 countdown과 상태가 정상 복원되는지 확인합니다.

## 9. 완료 조건

- 불가능이 확정된 proposal이 즉시 종료됩니다.
- 미응답 비귀책 회원에게 timeout penalty/cooldown이 생성되지 않습니다.
- 응답·timeout race에서도 terminal 결과와 부수 row가 중복되지 않습니다.
- 브라우저 로컬 시각 차이를 서버 시각 offset으로 보정합니다.
- 종료 사유와 cooldown이 사용자 귀책 여부에 맞게 표시됩니다.
- Backend·Frontend 전체 자동 테스트와 두 브라우저 수동 검증이 통과합니다.

## 10. 구현 결과와 검증 기록

- `MatchProposalResponseService`가 attempt 선잠금과 기존 proposal → attempt member → 정렬된
  pool 잠금 순서를 유지한 채 응답마다 목표/최소 인원 가능성을 계산합니다.
- 조기 종료 미응답자는 proposal `EXPIRED`, attempt member `EXCLUDED`이며 response와
  penalty/cooldown이 없습니다. 명시적 거절자만 기존 exclusion과 `REJECT` cooldown을
  응답 transaction에서 생성합니다.
- 같은 attempt/round proposal의 `expiresAt` 불일치는 응답 처리에서 거부하며 최초·round 2
  생성은 각각 공유 deadline 한 개를 사용합니다.
- `GET /api/matching/pools/me/current`의 `terminationReason`과
  `GET /api/matching/me/restrictions`의 `serverNow`를 추가했습니다. 상대 identity, 상대 응답,
  상대 penalty/cooldown은 반환하지 않습니다.
- Frontend는 REST 왕복 중간 client 시각을 사용해 offset을 재보정하고 모든 matching
  countdown을 보정 시각으로 계산합니다. 동일 deadline은 남은 시간이 증가하지 않게 하며
  attempt round/deadline이 바뀌면 새 deadline을 즉시 반영합니다.
- 2026-08-14 Frontend 전체 Vitest `18 files / 170 tests`, `npx tsc --noEmit`, production/PWA
  build는 `PASS`입니다.
- 최종 검토에서 Controller의 `serverNow` JSON 직렬화 기대값과 PostgreSQL 통합 테스트의
  `allowMinimumTwo` fixture 전제가 실제 설정과 다른 두 곳을 수정했습니다. 운영 코드는 추가로
  변경하지 않았습니다.
- WSL `/mnt/c`의 `9p`/DrvFS bind mount와 98% 사용 중인 Windows C:에서 Gradle output
  repository metadata 쓰기가 `Input/output error`를 낸 것으로 진단했습니다. 저장소 파일을
  삭제·초기화하지 않고 Backend source를 `/tmp`의 새 Linux filesystem 작업 디렉터리로
  `rsync`해 Docker Gradle JDK 17에서 검증했습니다.
- 2026-08-14 matching focused는 `37 suites / 294 tests`, Backend 전체는
  `59 suites / 386 tests`이며 failures/errors/skipped 0건으로 `PASS`입니다. PostgreSQL은
  Testcontainers의 disposable instance만 사용했습니다.
- 이번 최종 검증에서는 Frontend 코드를 추가로 변경하지 않아 Frontend 명령을 재실행하지
  않았고 위 기존 성공 결과를 유지합니다.
- `docs/05_MATCHING_POLICY.md`의 기존 round 1 전체 terminal 집계와 cooldown 시작 설명을
  최신 조기 종료 정책 및 실제 구현에 맞게 정리했습니다.
- 2026-08-15 두 브라우저에서 2인 proposal 거절 직후 양쪽 terminal 화면 전환, 거절자의
  30초 cooldown, 비귀책 상대의 cooldown 미표시와 상대 identity 비노출을 확인했습니다.
- dev DB 읽기 전용 조회로 거절자는 proposal/attempt member `REJECTED`, response 1건과
  `REJECT` 30초 cooldown 1건이고, 비귀책 상대는 proposal `EXPIRED`, attempt member
  `EXCLUDED`, response·penalty·cooldown 0건임을 확인했습니다. attempt는 `FAILED`이며
  비귀책 pool은 검색 만료 경계에 맞는 상태로 정리됐습니다.
- 새로고침과 탭 비활성화·복귀 뒤 terminal 상태와 countdown 복원을 확인했습니다.
  WebSocket 강제 차단 polling 복원과 client clock 강제 편차 주입은 수동 실행하지 않고
  통과한 Frontend 자동 테스트로 대체했습니다.
