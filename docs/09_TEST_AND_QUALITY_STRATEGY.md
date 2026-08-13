# 테스트/품질 전략

## 1. 테스트 전략 개요

이 프로젝트는 단순 CRUD보다 매칭 알고리즘, 동시성 처리, 인증/인가, 개인정보/위치정보 보호가 중요합니다.

전체 coverage 숫자만 높이는 것보다 장애 시 영향이 큰 핵심 로직을 우선 테스트합니다. 특히 사용자가 직접 경험하는 매칭 결과, 중복 매칭 방지, 토큰 처리, 위치정보 보호, 신고/제재 흐름은 우선순위가 높습니다.

포트폴리오에서는 "테스트를 많이 작성했다"보다 "왜 이 로직을 테스트했는지"를 설명할 수 있어야 합니다.

중점 설명 포인트:

- 매칭 알고리즘은 사용자 경험과 직접 연결된다.
- 동시성 처리는 중복 매칭과 잘못된 그룹 확정을 막는다.
- 인증/인가는 개인정보와 관리자 권한을 보호한다.
- 위치정보는 저장 최소화 원칙을 지켜야 한다.
- 신고/제재 로직은 사용자 안전과 운영 신뢰도에 영향을 준다.

## 2. 백엔드 단위 테스트

사용 도구:

- JUnit5
- Mockito
- AssertJ

대상:

- 매칭 점수 계산
- 정형 여행 스타일 점수와 임베딩 유사도 점수 결합
- `preference_text` 미입력 또는 임베딩 실패 시 정형 점수 fallback
- 희망 인원 `2/3/4` 판단
- 2명 진행 허용 여부
- 인원 미달 팝업 조건
- 최초 제안과 인원 미달 재확인 proposal 회차 판단
- 패널티/쿨타임 계산
- 귀책 proposal 기반 cooldown/penalty 멱등성
- 차단 사용자 제외 판단
- 이미 매칭 중인 사용자 제외 판단

단위 테스트는 DB, WebSocket, 외부 API 없이 순수한 domain/service 로직을 빠르게 검증합니다.

예시 관점:

- 희망 인원이 4명이고 2명 진행을 허용하지 않으면 최소 인원 매칭으로 확정되지 않는다.
- 3명 희망 사용자 3명이 모두 수락하면 인원 미달 팝업 없이 확정된다.
- 차단 관계가 양방향 중 하나라도 있으면 후보에서 제외된다.
- cooldown 중인 사용자는 매칭풀 후보가 될 수 없다.

## 3. 백엔드 슬라이스 테스트

사용 도구:

- `@WebMvcTest`
- `@DataJpaTest`

대상:

- Controller 요청/응답 검증
- validation 검증
- Repository/JPA 쿼리 검증
- `match_pool`, `match_proposal` 상태 조회 검증

`@WebMvcTest`는 Controller 계층의 HTTP contract를 검증합니다.

검증 예시:

- 필수 요청값이 없으면 `400 Bad Request`를 반환한다.
- 잘못된 희망 인원 값은 validation error가 된다.
- 인증이 필요한 endpoint는 인증 없이 접근할 수 없다.

`@DataJpaTest`는 Repository와 JPA query를 검증합니다.

검증 예시:

- 특정 축제의 `WAITING` 상태 pool만 조회한다.
- 만료된 proposal은 active proposal 조회에서 제외된다.
- 이미 매칭 중인 사용자는 후보 조회에서 제외된다.

## 4. 백엔드 통합 테스트

사용 도구:

- `@SpringBootTest`
- Testcontainers
- PostgreSQL Container
- Flyway

대상:

- 실제 PostgreSQL 환경에서 Flyway migration 적용 확인
- 매칭 제안 생성부터 응답 저장까지 흐름 검증
- unique constraint 기반 중복 응답 방지 검증
- 동일 attempt에서 회원별 proposal round 생성 검증
- `SELECT FOR UPDATE SKIP LOCKED` 기반 후보 선점 동시성 검증
- pgvector extension과 `vector(1536)` migration 적용 검증
- Scheduler 기반 만료 proposal `TIMEOUT` 처리 검증
- Refresh Token 저장/재발급/폐기 흐름 검증

통합 테스트는 실제 PostgreSQL 동작을 확인하는 데 중요합니다. H2 같은 in-memory DB는 PostgreSQL lock, constraint, transaction 동작을 완전히 대체하지 못할 수 있습니다.

우선 검증해야 할 통합 시나리오:

- Flyway migration이 빈 PostgreSQL Container에 정상 적용된다.
- 동일 proposal에 대해 두 번 수락 요청이 들어오면 unique constraint로 중복 응답을 막는다.
- 동일 attempt와 회원에 대해 round가 같은 proposal은 중복 생성되지 않는다.
- 동일 attempt와 회원이라도 다음 round의 인원 미달 재확인 proposal은 생성할 수 있다.
- 동일 귀책 proposal의 응답 재전송과 Scheduler timeout 재실행에도 cooldown과 penalty event는 각각 한 건만 생성된다.
- response, cooldown, penalty event, 회원 점수, pool, attempt 중 하나가 실패하면 같은 transaction이 전체 rollback된다.
- 사용자 응답과 timeout race에서도 proposal별 cooldown과 penalty event가 중복되지 않는다.
- 인원 미달 재확인은 같은 `attempt_id`와 새로운 `proposal_id`를 사용한다.
- 기존 attempt 종료 후 새로운 상대를 탐색하는 재매칭은 새로운 `attempt_id`를 사용한다.
- 동시에 여러 Scheduler worker가 후보를 조회해도 `FOR UPDATE SKIP LOCKED`로 같은 사용자를 중복 선점하지 않는다.
- 잠금 구간에서는 후보 상태 재검증과 선점만 수행하고 임베딩 계산은 수행하지 않는다.
- 임베딩 생성 실패 또는 미입력 시에도 정형 여행 스타일 기반 매칭은 정상 동작한다.
- `expires_at`이 지난 proposal은 Scheduler에 의해 `TIMEOUT`으로 전이된다.
- Refresh Token은 저장, 재발급, 폐기가 일관되게 처리된다.

## 5. 프론트엔드 테스트

사용 도구:

- Vitest 또는 Jest
- React Testing Library
- MSW(Mock Service Worker)

대상:

- 페이지 렌더링
- `MatchProposalModal` 수락/거절 버튼 동작
- `InsufficientMembersModal` 현재 인원으로 시작/취소 동작
- `MatchRoomPage` 시스템 이벤트 타임라인 표시
- API error/loading 상태 표시
- WebSocket 이벤트 수신 시 화면 상태 변경은 mock 기반으로 검증
- WebSocket 연결 성공·재접속 시 REST 상태 복원과 polling fallback 유지 검증
- 읽기 전용 `MatchRoomPage`의 최초 mount current group 복원, null redirect, festival/member 표시 검증
- 상태방 WebSocket 알림 payload를 화면 상태로 사용하지 않고 REST refresh만 유도하는지 검증
- 상태방 unmount 시 timer, WebSocket과 `AbortController` 정리 검증
- 도착 예정 시간 허용값 validation, 상태 전이, 같은 값 멱등성과 다른 값 변경 event 검증
- group row → group member row 잠금 순서와 동일 회원 동시 요청 직렬화 검증
- member update/event insert 실패 rollback과 rollback 알림 부재 검증
- frontend 선택 panel, 중복 제출 방지, 성공 snapshot 반영과 실패 snapshot 보존 검증
- 신규 선택값 `5/10/20/25`, 과거 응답 `0/30`, 25분 절대 마감 경계를 검증
- 정상 REST snapshot 전후 비교로만 상대 도착 시간 snackbar가 생성되고 최초
  snapshot, 본인 변경, 동일값, 실패 refresh에는 생성되지 않는지 검증
- WebSocket refresh와 polling fallback의 상대 변경 감지, 자동 제거와 timer
  cleanup을 fake timer 또는 제어 가능한 scheduler로 검증
- 도착 완료의 row lock, ARRIVED 멱등성, 최초 IN_PROGRESS 전환과 AFTER_COMMIT 알림 검증
- 축제 좌표 기반 Kakao Local 후보 검색의 빈 결과, 중복, 거리 정렬과 fallback 검증
- 축제별 복수 장소의 순환 배정과 후보 소진 후 재사용, 비활성 장소 제외 검증
- 같은 축제 동시 확정의 festival row lock과 축제별 독립 순환을 PostgreSQL에서 검증
- pool 진입 뒤 후보가 모두 비활성화되면 response/group/member/pool/attempt/event 전체 rollback 검증
- 확정 만남 포인트 snapshot이 참여자, REST 복원과 재조회에서 동일한지 검증
- Backend 거리 판정의 반경 내부·경계·외부, 잘못된 좌표, 낮은 정확도와 오래된 측정값 검증
- 도착 API가 원본 좌표·정확도·측정 시각을 받고 계산 거리와 `verified`는 받지 않는지 검증
- 원본 GPS 좌표가 DB, event payload, log와 WebSocket payload에 남지 않는지 검증
- 허위 도착 신고 사유, 중복 신고 방지와 신고만으로 자동 제재되지 않는지 검증
- 확정 후 취소의 3분 경계, KST 당일 CANCEL 횟수와 10/30/60분 cooldown 검증
- 30분 deadline 경계의 NO_SHOW, KST 당일 30/60분 cooldown과 Scheduler 재실행 멱등성 검증
- `allow_minimum_two` snapshot 기반 3명 이상/동의한 2명 유지와 비귀책 `LEFT` 검증
- 도착·취소·NO_SHOW 경쟁에서 group row → group member ID 순 잠금과 단일 terminal 상태 검증
- group/member/event/penalty/cooldown 실패 전체 rollback과 AFTER_COMMIT 이전 알림 부재 검증

프론트엔드 테스트는 사용자에게 보이는 상태 전환을 검증합니다.

검증 예시:

- 매칭 제안 API 응답이 오면 `MatchProposalModal`이 표시된다.
- 사용자가 수락을 누르면 대기 모달로 전환된다.
- 인원 미달 이벤트를 받으면 `InsufficientMembersModal`이 표시된다.
- `MEMBER_ARRIVED` 이벤트를 mock으로 전달하면 `MatchRoomPage` 타임라인에 도착 이벤트가 표시된다.
- API error 상태에서는 재시도 또는 안내 UI가 표시된다.
- 잘못된 WebSocket payload는 무시하고 정상 상태 알림만 REST refresh를 유도한다.

Backend WebSocket 테스트는 handshake cookie 인증, 본인 user destination 구독,
client `SEND` 거절과 transaction `AFTER_COMMIT` 이후 알림 전달을 우선 검증합니다.
rollback된 transaction의 알림이 전달되지 않는지도 함께 확인합니다.

## 6. 기능 테스트 / 시나리오 테스트

주요 사용자 흐름:

- 로그인 후 축제 피드 진입
- 축제 상세 확인
- 체크인 성공
- 매칭 조건 설정
- 매칭 대기
- 매칭 제안 수락
- 인원 미달 팝업 처리
- `MatchRoomPage` 진입
- 만남 포인트 지도와 장소명 확인
- 도착 시간 선택
- 단말 위치 권한 허용 후 도착했어요
- 평가/신고

MVP 초기에는 백엔드 통합 테스트와 프론트 mock 테스트로 대체합니다. 추후 Playwright 기반 E2E 테스트 도입을 검토합니다.

시나리오 테스트는 문서와 QA checklist로도 관리할 수 있습니다.

## 7. E2E 테스트 전략

Playwright는 추후 도입 후보로 둡니다.

공모전 MVP 초반에는 필수로 넣지 않습니다. 초기에는 기능 구현과 핵심 통합 테스트가 우선입니다.

최종 제출 전에는 핵심 시연 플로우만 E2E로 자동화할 수 있습니다.

우선 자동화 후보:

- 축제 피드 진입
- 축제 상세 확인
- 체크인 성공 mock
- 매칭 제안 수락 mock
- `MatchRoomPage` 진입
- 만남 포인트 표시 mock
- 위치 권한·단말 위치 확인 mock
- 신고 화면 진입

E2E는 유지보수 비용이 높으므로 전체 기능을 무리하게 자동화하지 않습니다.

## 8. CI/CD 품질 전략

GitHub Actions에서 push/PR마다 다음을 실행하는 방향으로 설계합니다.

- backend test 실행
- frontend test 실행
- 테스트 실패 시 deploy 방지
- JaCoCo로 backend coverage report 생성
- README에 CI badge와 coverage badge 추가 가능하도록 설계

coverage 숫자는 참고 지표입니다. 핵심 위험 로직이 테스트되었는지가 더 중요합니다.

현재 8-1단계 CI 초안은 테스트 자동화 전 단계의 build 검증입니다.

8-1단계 CI 범위:

- backend `./gradlew build -x test`
- frontend `npm ci`
- frontend `npm run build`

8-1단계 CI에서는 `bootRun`, DB 연결, PostgreSQL 컨테이너 실행, Oracle VM 배포를 하지 않습니다. backend/frontend 테스트 자동화와 배포 차단 기준은 테스트 코드와 CD 초안이 준비되는 후속 단계에서 확장합니다.

품질 기준 예시:

- 매칭 domain/service 테스트가 존재한다.
- 동시성 통합 테스트가 존재한다.
- 인증/토큰 흐름 테스트가 존재한다.
- 프론트 주요 modal 상태 전환 테스트가 존재한다.
- CI에서 테스트 실패 시 배포가 중단된다.

## 9. 포트폴리오 어필 포인트

포트폴리오에서는 다음을 강조할 수 있습니다.

- 매칭 알고리즘은 사용자 경험과 직접 연결되므로 단위 테스트로 검증한다.
- 동시성 처리는 중복 매칭을 막기 위해 Testcontainers 기반 통합 테스트로 검증한다.
- PostgreSQL lock과 unique constraint를 실제 DB에서 검증한다.
- GitHub Actions로 테스트 자동화를 구성해 실무형 개발 흐름을 보여준다.
- JaCoCo 리포트와 테스트 코드 일부를 README 또는 포트폴리오에 캡처로 정리한다.

좋은 설명 예시:

```text
전체 coverage 숫자보다 장애 영향도가 높은 매칭 확정, 중복 응답, 후보 선점 로직을 우선 테스트했습니다.
특히 SELECT FOR UPDATE SKIP LOCKED와 unique constraint는 실제 PostgreSQL Container에서 검증했습니다.
```

## 10. 1단계에서 하지 않는 것

현재 문서는 전략 문서입니다. 1단계 문서 작업에서는 실제 테스트 코드를 작성하지 않습니다.

1단계에서 하지 않는 작업:

- backend 테스트 코드 작성
- frontend 테스트 코드 작성
- Testcontainers 설정 추가
- Vitest/Jest 설정 추가
- Playwright 설정 추가
- GitHub Actions 테스트 workflow 실제 작성

위 항목은 개발환경 세팅 또는 비즈니스 기능 구현 단계에서 별도 승인 후 진행합니다.

## 시스템 이벤트 타임라인 검증 기준

- Controller/service에서 인증, current group 인가, DTO 변환과 raw payload 비노출을 검증합니다.
- `pgvector/pgvector:pg16` Testcontainers에서 다른 group 비노출, actor 공개 경계, 시간/ID 정렬, 최신 50건과 malformed payload 제외를 검증합니다.
- group 확정 transaction의 `MATCH_CONFIRMED` 저장과 event insert 실패 전체 rollback을 회귀 검증합니다.
- arrival-time/arrival 멱등 및 rollback 뒤 event 조회 결과가 증가하지 않는지 검증합니다.
- Frontend는 group/events 부분 실패, WebSocket 연결·재연결·알림, polling, 늦은 응답 차단과 자유 입력/전송 UI 부재를 검증합니다.
- 취소 화면은 세 구조화 사유만 표시하고 자유 입력 부재, 중복 제출 방지, 성공 전 snapshot 불변과 유지/종료 결과 이동을 검증합니다.

## 거절 상대 재추천 제외 검증 기준

- 2인·3인 proposal의 명시적 round 1 `REJECTED` pair 생성 범위와 반복 요청 멱등성을 PostgreSQL 통합 테스트로 검증한다.
- `TIMEOUT`, round 2 취소와 시스템 실패에서 exclusion이 생성되지 않는지 기존 proposal response 회귀와 함께 검증한다.
- requester 정방향·역방향, Scheduler batch 조합과 proposal 생성 직전 최종 재검증을 각각 focused 테스트로 검증한다.
- 동일 check-in에서 exclusion 유지, 새 active check-in에서 과거 exclusion 미적용을 실제 `festival_checkins` unique·FK 제약과 함께 검증한다.
- advisory lock은 pair 정규화·결정적 정렬 단위 테스트와 exclusion commit/proposal 생성 PostgreSQL race 테스트로 검증한다.
- 기존 `user_blocks`, cooldown, pool claim/release, response/timeout 동시성 테스트는 재작성하지 않고 전체 matching 회귀로 확인한다.

## MatchRoom 구조화 신고 검증 기준

- mock만으로 끝내지 않고 `pgvector/pgvector:pg16` Testcontainers와 Flyway V1~V18이
  적용된 실제 PostgreSQL에서 Controller부터 transaction과 UNIQUE까지 검증한다.
- JWT 회원 ID만 reporter로 저장되는지, 본인 신고와 양측 비참여 및 임의 group ID
  IDOR이 거절되는지 확인한다.
- DB CHECK와 같은 여섯 reason code만 허용하고 응답에서 reporter와
  `detail_encrypted`가 제외되는지 확인한다.
- 반복 및 동시 동일 요청에서 row가 한 건이고 기존 report status와 생성 시각이
  초기화되지 않는지 검증한다.
- 고정 `Clock`으로 종료 30일 직전, 정확한 경계와 초과를 검증하며 PostgreSQL 시간
  정밀도를 고려해 경계 밖 비교는 안정적인 1초 차이를 사용한다.
- insert 실패 rollback과 신고 전후 penalty event, cooldown, `penalty_score`,
  `manner_temperature`, match event 불변을 확인한다.
- focused 신고 테스트 뒤 matching 전체와 backend 전체 회귀를 순서대로 실행한다.
- Frontend API 단위 테스트는 URL, POST method, cookie credentials, `groupId`,
  `reportedMemberId`, `reasonCode`와 `reporterMemberId` 부재를 검증한다. 신규와 멱등
  응답의 HTTP 201은 모두 같은 성공으로 처리한다.
- MatchRoom UI 테스트는 본인 action 부재, 여러 상대별 정확한 action, 여섯 한국어
  사유, 미선택 차단, 최종 확인, SAFETY 안내와 자유 입력·채팅 부재를 검증한다.
- 신고 상태 테스트는 제출 중 비활성화, 빠른 이중 호출 1회, 실패 후 snapshot을
  건드리지 않는 재시도, 취소 초기화와 늦은 응답의 새 대상 상태 비덮어쓰기를 검증한다.
- 신고 성공 뒤 차단 API, current group 재조회와 WebSocket 발행이 없음을 구현 경계와
  MatchRoom 기존 회귀 테스트로 확인한다.

## MatchRoom 상대 회원 차단 Backend 검증

- `MatchBlockIntegrationTest`는 실제 PostgreSQL에서 정상 차단, 본인 차단, 양쪽 참여
  권한과 IDOR, 진행/종료 상태와 30일 경계, terminal timestamp 누락을 검증한다.
- UNIQUE와 `ON CONFLICT DO NOTHING`의 반복·동시 요청 멱등성, 다른 group에서 같은 pair
  반복, 기존 `created_at`/reason 불변과 실패 rollback을 검증한다.
- 차단 API 생성 후 requester 양방향 후보 조회와 Scheduler batch pair 제외를 검증하고,
  기존 `MatchProposalCreationServiceIntegrationTest`에서 proposal 직전 차단 재검증과
  block commit race를 member-pair advisory lock으로 검증한다.
- penalty/cooldown/회원 점수와 MatchRoom event 불변, 최소 응답 계약과 내부 정보 비노출을
  함께 검증한다. 실행 순서는 focused 차단 → matching 전체 → backend 전체다.

## MatchRoom 상대 회원 차단 Frontend 검증

- API client는 current group ID가 포함된 URL, POST와 cookie credentials, body의
  `blockedMemberId` 단일 필드 및 `blockerMemberId` 부재를 검증한다. 신규·멱등 201을
  모두 정상 성공으로 처리한다.
- 차단 session은 동기 이중 제출 1회, submitting 상태, 실패 후 대상 유지와 재시도,
  취소·대상 변경·unmount abort 및 request identity가 다른 늦은 응답 무시를 검증한다.
- MatchRoom UI는 본인 action 부재, 3~4인 상대별 정확한 ID, 신고와 독립된 action,
  대상 nickname과 양방향 제외·비노출·해제 불가 안내, 제출 중 action 차단을 검증한다.
- 성공·실패 모두 기존 MatchRoom snapshot을 임의 변경하지 않으며 성공 후 상대 카드 유지,
  신고 API·current group 재조회·WebSocket SEND 부재를 기존 경계와 전체 회귀로 확인한다.
- 접근 가능한 dialog title/description, `Escape`, focus 복원과 `Tab` 순환을 구현 경계로
  확인하고 자유 입력·상대 추론·채팅 UI가 추가되지 않았는지 회귀한다.
