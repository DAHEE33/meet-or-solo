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

프론트엔드 테스트는 사용자에게 보이는 상태 전환을 검증합니다.

검증 예시:

- 매칭 제안 API 응답이 오면 `MatchProposalModal`이 표시된다.
- 사용자가 수락을 누르면 대기 모달로 전환된다.
- 인원 미달 이벤트를 받으면 `InsufficientMembersModal`이 표시된다.
- `MEMBER_ARRIVED` 이벤트를 mock으로 전달하면 `MatchRoomPage` 타임라인에 도착 이벤트가 표시된다.
- API error 상태에서는 재시도 또는 안내 UI가 표시된다.

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
- 도착 시간 선택
- 도착했어요
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
- 도착했어요 버튼 클릭
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
