# 진행 상태 기록

## [10-B AI 임베딩] 취향 임베딩 도입

상태: 1~4단계 Backend·Frontend 구현 완료, 자동 테스트 통과 (2026-08-26 기준)

### 1. 진행 순서 변경

기존 계획의 `MATCH-09` 솔로 코스 연결보다 AI 임베딩을 먼저 진행합니다. 솔로 코스는
관광공사 OpenAPI 연동이 선행되어야 하는데 해당 연동이 아직 착수되지 않았고, AI 임베딩은
선행 의존성이 없기 때문입니다. 문서 하단 `4. 이후 순서`에도 반영했습니다.

### 2. 단계 계획

| 단계 | 범위 | 브랜치 |
| --- | --- | --- |
| 1 | Backend — OpenAI 연동, 임베딩 생성·갱신·삭제 서비스, fallback | `feature/wbs-10-b-embedding-api` |
| 2 | Backend — `EmbeddingScorer` + Jaccard 결합, 매칭 scoring 반영 | `feature/wbs-10-b-embedding-scoring` |
| 3 | Frontend — 취향 입력 공통 컴포넌트(가이드 2문항 + 자유 입력) | `feature/wbs-10-b-preference-input-ui` |
| 4 | Frontend — 회원가입 AI 동의 + 가입 흐름에 취향 입력 연결 | `feature/wbs-10-b-consent-ai-signup` |
| 5 | Frontend — 매칭 신청 전 미입력 체크 + 프로필 설정에서 수정 | `feature/wbs-10-b-preference-guard` |

1·2·3단계는 브랜치를 분리하지 않고 `feature/wbs-10-b-embedding-backend-and-preference-ui`
하나로 묶어 진행했습니다.
4단계부터 다시 계획대로 분리합니다.

### 3. 확정 사항

- `member_preference_embeddings`는 회원당 1건이며 `preference_text`, 벡터,
  `embedding_status`, 모델명을 보관합니다. pgvector 기반이므로 local compose와
  Testcontainers 모두 `pgvector/pgvector:pg16` 이미지를 사용합니다.
- 임베딩 모델 기본값은 `text-embedding-3-small`이며 `OPENAI_EMBEDDING_MODEL`로 교체할 수
  있습니다. API Key가 비어 있으면 기동은 성공하지만 임베딩이 `FAILED`로 저장됩니다.
- 외부 호출 실패는 예외를 전파하지 않고 `embedding_status = FAILED`로 저장한 뒤 매칭에서
  임베딩 미보유로 간주합니다. 임베딩 실패가 프로필 저장이나 매칭 자체를 막지 않습니다.
- pair 점수 계산은 `PairCompatibilityScorer` 한 곳으로 모았습니다. 그룹 선정
  (`MatchGroupComposer`)과 proposal에 저장되는 회원 점수
  (`MatchProposalCreationService.memberScore()`)가 같은 계산식을 사용하므로 "선정 근거와
  저장 점수가 다른" 상태가 재발하지 않습니다.
- 양쪽 모두 임베딩을 보유한 경우에만 `Jaccard 0.70 + cosine 0.30`으로 가중 합산합니다.
  한쪽이라도 미보유·미완료면 Jaccard 점수만 사용합니다. 이 fallback은 `cosine = jaccard`로
  간주하는 것과 수학적으로 같으므로, 임베딩 보유자와 미보유자가 같은 후보 pool에 섞여도
  점수 스케일이 왜곡되지 않습니다.
- 0.70 / 0.30 가중치 근거: 태그는 5종 중 1~3개만 고르므로 Jaccard가 취할 수 있는 값이
  `0, 20, 25, 33.33, 50, 66.67, 100` 7가지뿐이고 동점이 자주 발생합니다. 반면 코사인 유사도는
  촘촘하지만 무관한 텍스트도 값이 크게 내려가지 않습니다. 이 배분에서는 태그가 크게 갈리면
  (0 vs 100, 가중 차이 70점) 임베딩 최대 기여 30점으로 순위를 뒤집을 수 없고, 태그가 같은
  버킷이면 임베딩이 순위를 결정합니다. 즉 태그를 주 신호, 임베딩을 동점 판별자로 두는 배분입니다.
- 위 가중치는 실사용 데이터로 조정할 값이므로 하드코딩하지 않고
  `app.matching.scoring.jaccard-weight` / `embedding-weight`로 주입합니다. 환경변수는
  `MATCHING_SCORING_JACCARD_WEIGHT` / `MATCHING_SCORING_EMBEDDING_WEIGHT`이며, 두 값의 합이
  1이 아니면 기동 시점에 실패합니다.
- 취향 입력은 `PreferenceInputSection` 공통 컴포넌트로 가이드 2문항(하고 싶은 것, 편한 사람)과
  자유 입력을 함께 받습니다. 서버 `preference_text`는 단일 컬럼이므로 `buildPreferenceText()`가
  라벨 접두어를 붙여 한 문자열로 직렬화하고 `parsePreferenceText()`가 되돌립니다. 라벨을 찾지
  못하면 전체를 자유 입력으로 두어 기존 저장 값도 내용을 잃지 않습니다.
- 외부 API 전송 동의는 `member_consents`의 `AI_PROCESSING`으로 관리합니다.
  `V11__add_member_preference_embeddings.sql`에서 `chk_member_consents_type` 제약에
  값을 추가했습니다.

### 4. 알려진 제약과 남은 작업

- (4단계에서 해소) 동의를 기록하는 API와 화면이 없어 1~3단계 수동 검증은 `member_consents`에
  직접 INSERT한 계정으로 수행했습니다. 4단계에서 동의 API와 화면을 추가해 실사용 경로를
  열었습니다.
- 저장되는 점수는 총점 하나뿐이라 사후 분석이 어렵습니다. `jaccard`, `cosine`, 임베딩 사용
  여부를 함께 남기면 "임베딩이 실제로 매칭 품질을 높였는가"를 데이터로 판단할 수 있습니다.
  컬럼 추가가 필요하므로 후기·평점 연계 단계에서 함께 검토합니다.
- `MemberPreferenceEmbeddingService.createOrUpdate()`가 `@Transactional` 안에서 OpenAI를
  호출합니다. read timeout 10초 동안 DB 커넥션을 점유하므로 외부 호출을 트랜잭션 밖으로
  분리하거나 비동기화하는 방안을 후속으로 검토합니다.
- `FAILED` 상태 재시도 경로가 없습니다. 현재는 사용자가 같은 취향 글을 다시 저장하는 것이
  유일한 복구 수단입니다.
- (4단계에서 해소) 동의 철회 시 임베딩 삭제 연동과 개인정보 고지 문구를 추가했습니다.

### 4-1. 4단계 — 동의 API와 회원가입 취향 입력 연결

브랜치 `feature/wbs-10-b-consent-ai-signup`에서 진행했습니다.

**국외 이전 동의 판단**

OpenAI는 미국 소재 사업자이고 `preference_text`는 자유 서술형 개인정보이므로 `AI_PROCESSING`과
별개로 `OVERSEAS_TRANSFER` 동의를 받기로 확정했습니다. `docs/06_SECURITY_POLICY.md`가 이미 두
동의를 합치지 않기로 정해 두었고, 국외 이전 고지에 필요한 항목(이전받는 자, 국가, 항목, 시점과
방법, 목적, 보유 기간, 거부권)이 하나의 체크박스에 담기지 않기 때문입니다. 임베딩 저장은 두
동의를 모두 보유한 경우에만 허용하고, 하나라도 없으면 기존 `AI_CONSENT_REQUIRED`로 거절합니다.
실제 법적 요건과 최종 문구는 출시 전 별도 검토 대상으로 남깁니다.

**동의 API**

- `MemberConsentType` enum에 6개 유형과 고지 문구 버전(`currentVersion`)을 두었습니다. 현재
  API로 다루는 유형은 `TERMS`, `PRIVACY`, `AI_PROCESSING`, `OVERSEAS_TRANSFER` 4개이고,
  `LOCATION`·`MARKETING`은 화면이 없어 400으로 거절합니다.
- `GET /api/members/me/consents`는 취향 분석에 필요한 2개 유형을 항상 채워서 반환합니다.
  기록이 없어도 항목을 빼지 않고 `agreed = false`로 내려보냅니다. "아직 없으면 200 + null data"
  규약은 단일 리소스 조회에 적용하는 규약인데, 동의 상태는 조회 대상 유형이 고정되어 있고
  화면이 "어떤 동의가 비어 있는가"를 알아야 체크박스를 그릴 수 있어 다르게 판단했습니다.
- `POST /api/members/me/consents`로 동의를 기록하고
  `DELETE /api/members/me/consents/{consentType}`로 철회합니다. 고지 문구 버전은 클라이언트가
  아니라 서버가 정합니다.
- 쓰기는 `MemberConsentCommandRepository`의 `INSERT ... ON CONFLICT DO UPDATE` 한 문장으로
  처리합니다. `uq_member_consents_member_type_version` 때문에 "철회 후 재동의"는 새 row가 아니라
  기존 row 갱신이고, 조회 후 분기하면 중복 제출 시 UNIQUE 위반이 나기 때문입니다.
- 철회는 `agreed`를 `FALSE`로 바꾸지 않고 `revoked_at`만 기록합니다. 기존
  `MemberConsentQueryRepository.hasAgreedConsent()`가 이미 `agreed = TRUE AND revoked_at IS NULL`을
  보고 있어 그대로 맞물립니다.
- migration은 추가하지 않았습니다. V2와 V11로 충분합니다.

**철회 시 삭제 정책 (진행 로그가 요구하던 항목)**

`AI_PROCESSING` 또는 `OVERSEAS_TRANSFER` 중 하나라도 철회하면 같은 transaction에서
`member_preference_embeddings` row를 삭제합니다. 두 동의가 모두 있어야 전송이 허용되므로 하나만
철회해도 보관 근거가 사라지고, 원문과 벡터가 같은 row라 한 번의 삭제로 함께 지워집니다.
확정된 정책은 `docs/06_SECURITY_POLICY.md`에 반영했습니다.

**가입 시 약관 동의 (LoginPage "간주" 문구 처리)**

`LoginPage`의 "계속 진행하면 이용약관 및 개인정보처리방침에 동의하는 것으로 간주됩니다" 문구는
묵시적 동의인 데다 DB에 아무 기록을 남기지 않아 사후 증명이 불가능했습니다. OAuth 리다이렉트
전에 동의를 받으면 흐름이 복잡해지므로 동의 시점을 회원가입(프로필 설정 완료)으로 옮겼습니다.

- `SignupPage`에 `TERMS`·`PRIVACY` 필수 체크박스를 추가하고, 프로필 저장 직전에 동의를
  기록합니다.
- 서버 `MemberProfileService.completeProfile()`은 최초 가입 완료(`PROFILE_REQUIRED`) 시점에만
  두 동의를 확인하고 없으면 `SIGNUP_CONSENT_REQUIRED`(400)로 거절합니다. 기존 `ACTIVE` 회원의
  프로필 수정에는 적용하지 않습니다. 동의 기록 구조가 생기기 전에 가입한 회원까지 소급해 막으면
  프로필 수정 자체가 불가능해지기 때문입니다.
- `LoginPage` 문구는 "로그인 후 프로필 설정 단계에서 ... 동의하게 됩니다"로 바꿨습니다.

**Frontend**

- `api/memberConsents.ts`와 고지 문구 상수 `components/consent/consentNotice.ts`, 공통 컴포넌트
  `components/consent/AiConsentSection.tsx`를 추가했습니다. AI 처리와 국외 이전을 별도 체크박스로
  받고, "자세히"를 펼치면 국외 이전 고지 항목을 표시합니다.
- `SignupPage`에 기존 `PreferenceInputSection`을 그대로 재사용해 취향 입력을 붙였습니다. 취향은
  선택 입력이며 동의 두 가지를 체크해야 입력란이 활성화됩니다. 취향 저장에 실패해도 가입은 이미
  완료된 상태이므로 되돌리지 않고 "취향 없이 시작하기"로 진행할 수 있게 했습니다. 임베딩 실패가
  가입을 막지 않는다는 1~3단계 원칙과 같습니다.
- `ProfileEditPage`는 진입 시 동의 상태를 함께 조회합니다. 미동의면 동의 섹션을 먼저 보여주고
  버튼을 "동의하고 저장"으로 바꿔 동의와 저장을 한 번에 처리합니다. 저장 중
  `AI_CONSENT_REQUIRED`가 오면(다른 기기에서 철회한 경우) 에러 문구 대신 동의 입력을 다시
  노출합니다. 동의 철회 버튼도 추가했고, 철회하면 저장한 취향도 삭제된다는 확인을 받습니다.
- 화면 문구에는 "임베딩" 같은 개발 용어를 쓰지 않았습니다. 문구에 개발 용어가 섞이는 것을
  테스트로 막습니다(`consentNotice.test.ts`).

**2026-08-26 브라우저·dev DB 수동 검증**

로컬 backend/frontend와 SSH tunnel로 연결한 dev DB(`meet_or_solo_dev`)에서 확인했습니다.

확인한 항목:

- 로그인 화면의 "동의하는 것으로 간주됩니다" 문구가 가입 단계 안내로 바뀐 것
- 약관·개인정보 동의 없이 "프로필 설정 완료"를 누르면 진행되지 않는 것
- AI 처리와 국외 이전 체크박스가 분리되어 있고, 하나만 체크하면 취향 입력이 잠긴 채로 남는 것
- "자세히"를 펼치면 국외 이전 고지 항목이 표시되는 것
- 가입 완료 후 `member_consents`에 4개 유형이 기록되고
  `member_preference_embeddings`에 `embedding_status = COMPLETED`,
  `embedding_model = text-embedding-3-small`, `vector_dims = 1536`이 저장되는 것
- 동의 철회 시 `member_preference_embeddings` row가 삭제되고 `member_consents.revoked_at`이
  기록되는 것 (진행 로그가 요구하던 삭제 정책)
- 철회 상태에서 화면을 우회해 `POST /api/members/me/preference-embedding`을 직접 호출하면
  `403 AI_CONSENT_REQUIRED`로 거절되는 것
- 재동의 후 같은 endpoint 호출이 성공하고 `COMPLETED`로 저장되는 것

수행하지 않은 항목: 취향을 비운 채 가입, 기존 `ACTIVE` 회원의 프로필 수정 회귀 확인,
국외 이전 동의만 단독 철회. 자동 테스트로 각각 대응되는 케이스가 있으나 수동 `PASS`로
판정하지 않습니다.

검증 중 회원가입 완료가 느리게 느껴지는 현상을 확인했습니다. 동의 4건과 프로필, 임베딩까지
HTTP 요청이 순차로 나가고 마지막 요청이 transaction 안에서 OpenAI를 호출하기 때문이며(read
timeout 10초), dev DB가 SSH tunnel 너머에 있어 왕복 지연이 더해집니다. 기존 구조에서 비롯된
현상이라 이번 범위에서 바꾸지 않고 로드맵 4.7로 이관했습니다.

**남은 제약**

- 동의 여부 조회가 `version`을 보지 않으므로 고지 문구를 개정해 `currentVersion`을 올려도 기존
  동의자에게 재동의가 강제되지 않습니다. 지금 강제하면 기존 동의자가 전부 취향을 잃습니다.
- 동의 기록 구조 이전에 가입한 `ACTIVE` 회원의 `TERMS`·`PRIVACY` 소급 동의 수집 경로가 없습니다.
- `GET /api/members/me/consents`는 AI 관련 2개만 반환합니다. `TERMS`·`PRIVACY`는 기록만 하고
  조회로 노출하지 않습니다.

위 제약과 임베딩 재시도·외부 호출 분리·점수 분해 저장은
`docs/19_ADMIN_MEMBER_SAFETY_ROADMAP.md`의 `4.7 동의·개인정보 후속`으로 모았습니다. 수동 검증
중 확인한 로그아웃 미구현(화면 버튼이 cookie와 refresh token, WebSocket session을 정리하지
않음)은 같은 문서 `4.6 로그아웃`으로 추가했고 `docs/06_SECURITY_POLICY.md`에도 미구현임을
명시했습니다. 두 항목 모두 이번 단계에서는 문서화만 하고 구현하지 않았습니다.

### 5. 검증 결과

- (4단계) Backend 전체 493 tests가 failures/errors/skipped 0건으로 통과했습니다. Docker Desktop을
  실행한 상태로 검증해 Testcontainers 통합 테스트와 전체 Spring context 기동 검증
  (`MeetOrSoloApplicationTests`)이 실제로 수행됐습니다. 신규 `MemberConsentServiceTest` 10건,
  `MemberConsentControllerTest` 9건, `MemberConsentRepositoryIntegrationTest` 6건을 포함합니다.
  JDK 17로 실행합니다. 기본 `JAVA_HOME`이 JDK 8이면 실패합니다.
- (4단계) Frontend Vitest 28 files / 230 tests 통과, `npx tsc --noEmit` 통과,
  production/PWA build 성공.
- (1~3단계) Backend 비-컨테이너 테스트 221건 전체 통과 (`PairCompatibilityScorerTest` 9건 신규 포함).
- Testcontainers 22건은 Docker 미실행으로 초기화 실패했습니다. 기존 환경 제약이며 이번 변경과
  무관하지만, `MemberPreferenceEmbeddingRepositoryIntegrationTest`와 전체 Spring context 기동
  검증(`MeetOrSoloApplicationTests`)이 미확인 상태로 남습니다. Docker 실행 후 재확인이 필요합니다.
- Frontend Vitest 25 files / 207 tests 통과 (`preferenceText.test.ts` 18건 신규 포함),
  `npx tsc --noEmit` 통과.
- `MatchingControllerTest`가 `MatchPoolCancellationService` mock 누락으로 24건 실패하던 문제를
  함께 고쳤습니다. 매칭 취소 기능(PR #37)에서 controller 의존성이 늘었는데 `@MockitoBean`이
  추가되지 않아 발생한 기존 문제이며 이번 임베딩 작업과는 무관합니다.


### 6. 부수 정리

- 루트 `.env.example`을 실제 설정 기준으로 재정리했습니다. `OPENAI_*` 5개, 매칭 스케줄러,
  관리자 제재 스케줄러 항목을 추가하고, 어디에서도 읽지 않던 `LOCAL_DB_URL`, `DEV_DB_*`,
  `PROD_DB_*`와 Frontend 전용 `VITE_KAKAO_MAPS_APP_KEY`를 제거했습니다. dev·prod profile
  전용 `DB_URL` 계열은 주석 블록으로 남겼습니다. 매칭 점수 가중치 환경변수 2개도 추가했습니다.
- Spring Boot local profile은 `application-local.yml`의 optional config import로 루트
  `.env`를 읽으므로 `bootRun` 시 별도 환경변수 주입이 필요하지 않습니다.

## [10-관리자 안전 3차] 관리자 정지 조기 해제(UNSUSPEND)

상태: 구현·자동 검증 및 브라우저 수동 검증 완료

- 관리자 회원 제재 2차(PR #35) 수동 검증에서 `SUSPENDED` 상태 회원을 즉시 해제하는 action이
  없어, 테스트 계정도 `BAN → UNBAN`을 거쳐야 원래 상태로 복구되는 운영 UX 누락을 확인했다.
- `AdminMemberActionType`에 `UNSUSPEND` 값을 추가하고 `Member.unsuspend()`를 구현했다.
  `SUSPENDED` 상태에서만 호출 가능하며 기존 `restorePreviousStatus()`를 재사용해
  `statusBeforeSanction`(ACTIVE 또는 PROFILE_REQUIRED)으로 복원한다.
- `AdminMemberService.apply()` switch에 `UNSUSPEND` case를 추가했다. 기존 `validateRequest()`의
  `!= SUSPEND` 조건이 `suspensionDuration` 조합을 자동 거절하고, `lockReport()`에서 UNBAN과 함께
  해제 조치의 신고 연결을 거절한다. access revocation과 active matching 검증은 해제이므로 미적용.
- V4의 `chk_admin_actions_type` CHECK 제약에 `UNSUSPEND`가 없어 INSERT 시 500 에러가 발생하는
  문제를 확인했다. 기존 V1~V19를 수정하지 않고 `V20__allow_unsuspend_action_type.sql`로 CHECK를
  재생성해 해결했다.
- `AdminMemberRepository.findActions()` SQL IN 절에 `'UNSUSPEND'`를 추가해 제재 이력에 정지 해제
  기록이 표시되도록 했다.
- Frontend `AdminMemberActionType`에 `'UNSUSPEND'`를 추가하고 `actionLabel`에 `정지 해제`를
  등록했다. 회원 상세 dialog에서 `SUSPENDED` 상태일 때 teal 색 "정지 해제" 버튼을 표시한다.
- UNSUSPEND action dialog의 사유 select를 `ADMIN_CORRECTION`과 `OTHER` 두 가지로 필터링했다.
  기본 사유는 `ADMIN_CORRECTION`이다. 기존 WARNING, SUSPEND, BAN, UNBAN의 사유 목록은
  변경하지 않았다.
- 기존 WARNING, SUSPEND, BAN, UNBAN 동작, Flyway migration V1~V19, 기존 테스트는
  변경하지 않았다.
- Backend 비-컨테이너 187건 전체 통과. Testcontainers 21건은 Docker 미설치로 초기화 실패
  (기존 환경 제약, 이번 변경과 무관).
- Frontend Vitest 24 files/189 tests, `npx tsc --noEmit`, production/PWA build 성공.
- 2026-08-18 브라우저 수동 검증에서 SUSPENDED 회원 상세의 "정지 해제" 버튼 표시,
  정지 해제 실행 후 ACTIVE 복원, 제재 이력의 `정지 해제` 기록, 비-SUSPENDED 회원에서
  버튼 미표시를 확인했다.

## [10-관리자 안전 2차] 관리자 회원 조회·제재

상태: 구현·자동 검증 완료, 브라우저·dev DB 수동 검증 일부 수행

- `V19__add_admin_member_sanctions.sql`로 `BANNED`, 정지 시작·종료 시각, 제재 전 상태,
  `admin_actions.reason_code`, `idempotency_key`와 관련 제약조건·인덱스를 추가했다. 기존
  migration은 수정하지 않았다.
- 관리자 회원 목록·닉네임 검색·상태/역할 filter·cursor pagination, 회원 상세와 신고·제재
  이력, `WARNING`, `SUSPEND`, `BAN`, `UNBAN`을 구현했다.
- 필수 `Idempotency-Key`, member → optional report 고정 row lock, 신고 `ACTION_TAKEN`과
  감사 로그의 원자 저장, active pool/proposal/group 회원의 `SUSPEND`·`BAN` 409 거절을
  적용했다.
- 정지 만료 lazy 복구와 Scheduler, 로그인·refresh·기존 access token 요청 제한, refresh
  token 폐기와 transaction commit 후 WebSocket session 종료를 구현했다.
- `/admin/members`와 관리자 진입 버튼, 실패 전 snapshot 유지, 중복 제출 방지와 성공한
  대상 회원만 갱신하는 UI를 추가했다.
- Backend 전체 426 tests와 관리자 제재 PostgreSQL Testcontainers 통합 시나리오 7건,
  Frontend 전체 24 files/189 tests 및 production build가 성공했다.
- 2026-08-17 사용자 브라우저 수동 검증에서는 관리자 회원 화면의 기본 조회·검색·filter·상세,
  제재와 복구 흐름 중 사용자가 실제 확인한 범위까지만 검증했다. WebSocket session 종료,
  active matching 충돌, dialog 접근성과 자동 테스트 대체 항목은 수동 `PASS`로 판정하지 않는다.
- 수동 검증 중 관리자 `SUSPENDED` 상태를 즉시 해제하는 action과 UI가 없어, 테스트 계정도
  `BAN -> UNBAN`을 거쳐야 원래 `ACTIVE` 또는 `PROFILE_REQUIRED`로 복구되는 운영 UX 누락을
  확인했다. 후속 작업에서 `UNSUSPEND` 또는 동등한 조기 해제 action, 감사 유형, API와 확인
  dialog를 별도로 설계한다.
- commit, push, PR과 전체 수동 검증 완료 판정은 아직 수행하지 않았다.

## [10-관리자 안전 1차] 관리자 신고 검토

상태: 구현·자동 검증 및 dev DB·브라우저 수동 검증 완료

- JWT HttpOnly `access_token` cookie에서 member ID만 추출하고 `members.role`을 매 요청
  재조회하는 `GET /api/admin/me`와 관리자 신고 목록·상세·상태 변경 API를 추가했다.
- 목록은 `created_at DESC, id DESC` keyset 정렬, filter fingerprint를 HMAC 서명한 opaque
  cursor, `size + 1` 조회를 사용한다. 상태·사유·기간 filter와 잘못된 cursor·날짜 범위를
  공통 400 응답으로 처리하며 migration과 성능 index는 추가하지 않았다.
- 상태 변경은 report row를 `SELECT FOR UPDATE`로 먼저 잠근 뒤 transaction 안에서 현재
  상태와 피신고자를 재검증한다. 같은 목표 상태는 timestamp와 감사 로그를 변경하지 않고
  기존 snapshot을 반환하며, terminal 경합은 최종 상태 하나와 감사 로그 하나만 남긴다.
- `RESOLVED`와 `REJECTED`에만 각각 `REPORT_RESOLVE`, `REPORT_REJECT` 감사 로그를 같은
  transaction으로 저장한다. `REVIEWING` 감사 로그, 제재·penalty·cooldown·회원 점수·상태,
  매칭 상태와 WebSocket/application notification event는 생성하거나 변경하지 않는다.
- `/admin`과 `/admin/reports`에 server-driven ADMIN route guard를 적용했다. 신고 관리 화면은
  filter, cursor 이전·다음, loading·빈 목록·오류·재시도, 상세·확인 dialog, 동기 in-flight
  guard, abort/request identity와 dialog keyboard/focus 접근성을 제공한다. 기존 관리자 관광·통계
  mock은 신고 관리 진입 링크 외에 변경하지 않았다.
- Backend 관리자 신고·인가 및 cursor 보안 focused 5 suites/30 tests, safety 전체
  7 suites/58 tests, Backend 전체 64 suites/416 tests가 failures/errors/skipped 0건으로
  통과했고 build가 성공했다.
  전체 검증은 DrvFS 산출물을 삭제하지 않고 `/tmp` Linux filesystem과 PostgreSQL
  Testcontainers에서 테스트 전용 profile 암호화 키를 환경변수로 제공해 실행했다.
- Frontend focused 4 files/14 tests, 전체 Vitest 22 files/184 tests, `npx tsc --noEmit`,
  production/PWA `generateSW` build가 성공했다.
- dev 수동 검증 전 보안 점검에서 cursor HMAC이 JWT Secret을 재사용하는 문제를 발견해
  `ADMIN_REPORT_CURSOR_HMAC_SECRET` 전용 키로 분리했다. UTF-8 기준 32바이트 이상을 요구하고
  누락·blank·짧은 값은 시작 단계에서 거절하며 기본값과 JWT fallback은 두지 않는다.
  실제 Secret은 repository에 저장하지 않고 dev/prod에서 별도 주입해야 하며, 키 회전 시 기존
  cursor는 무효화될 수 있다. DB migration은 추가하지 않았다.
- 2026-08-16 로컬 Backend/Frontend와 dev DB를 연결한 브라우저 수동 검증에서 ADMIN의
  `/admin/reports` 접근과 목록·filter·상세·상태 변경, 일반 USER `403`, 미인증 로그인 이동을
  확인했다. `REVIEWING`, `RESOLVED`, `REJECTED` 전이와 동일 처리 멱등 재요청, terminal 상태
  충돌을 확인했고 dev DB의 `reports`와 `admin_actions` 상태·감사 로그 단일성이 일치했다.
  penalty·cooldown·회원 상태와 매칭 관련 부수 상태는 변경되지 않았고, 피신고자 화면에는 신고
  상태·처리 결과·신고자와 관리자 identity가 노출되지 않았다.
- 관리자는 일반 회원 화면도 사용할 수 있으며 `/admin`을 직접 열어 관리자 기능에 진입한다.
  일반 화면에서 ADMIN에게만 보이는 관리자 진입 버튼과 관리자 화면 상세 UX 보완은 이번 1차의
  완료 조건에서 제외하고 후속 관리자 UX 작업으로 이관한다.
- 다음 Fullstack B 작업은 `dev` 병합 후
  `feature/wbs-10-b-admin-member-sanctions`에서 진행하는 관리자 회원 조회·제재 2차다.
  구현 전 정책과 조사 범위는 `docs/20_ADMIN_MEMBER_SANCTIONS_HANDOFF.md`로 인계한다.

## [10-안전 6차] 차단 해제 동시성·마이페이지 관리 UI

상태: 구현·자동 검증 및 두 브라우저·dev DB 수동 검증 완료

- 해제 DELETE에 차단 생성·proposal 생성과 같은 정규화 member-pair advisory transaction
  lock을 적용했다. 기존 pool row lock → pair lock 순서는 유지한다.
- 동시 DELETE 최종 row 0건, 해제 전후 requester 양방향·Scheduler batch 후보 복귀,
  proposal 직전 및 해제 선행 race와 기존 차단 생성 race를 PostgreSQL 통합 테스트로 보강했다.
- 마이페이지에 `/mypage/blocks` 진입, 목록 loading/빈 목록/오류 재시도, 최종 확인 dialog와
  204 성공 뒤 대상 항목만 제거하는 UI를 추가했다.
- Frontend는 in-flight guard, abort/request identity, 실패 전 optimistic removal 금지,
  dialog focus/Escape/Tab 순환과 live region을 적용했다. 현재 MatchRoom 재조회와 WebSocket
  SEND는 추가하지 않았다.
- migration과 Backend API 계약은 변경하지 않았다. 2026-08-14 두 브라우저·dev DB에서
  정방향 목록, 역방향 비노출, 해제 후 `user_blocks` 0건, 부수 상태 불변과 신규 매칭
  후보 복귀를 확인해 `docs/17_MEMBER_BLOCK_MANAGEMENT_MANUAL_TEST.md`를 `PASS`로 마감했다.
- Backend focused/safety/matching/전체 테스트와 build가 성공했다. 최종 전체 결과는
  384 tests, failures/errors/skipped 0건이다.
- Frontend focused 56건, 전체 Vitest 17 files/160 tests, `npx tsc --noEmit`과 PWA
  production build가 성공했다.

## [10-매칭 후속] Proposal 조기 종료·서버 시각 타이머 동기화

상태: 구현·Backend/Frontend 자동 검증 및 두 브라우저 핵심 수동 검증 완료

- 최초 proposal에서 한 회원이 `REJECTED`를 제출해도 다른 회원이 응답하거나 만료될 때까지
  attempt가 끝나지 않아, 이미 성사 불가능한 2인 proposal의 상대가 `TIMEOUT` 2분 cooldown을
  받는 현상을 확인했다.
- 같은 종료 화면에서 거절 회원은 약 30초, timeout 회원은 약 2분의 서로 다른 cooldown을
  표시한다. 이는 단순 countdown 오차가 아니라 서로 다른 귀책 정책 결과이나 사용자에게
  원인이 설명되지 않아 타이머 불일치처럼 보인다.
- Frontend countdown은 `expiresAt - Date.now()`로 계산하므로 서로 다른 기기의 로컬 시각
  편차와 REST 수신 시점 차이를 보정하지 않는다. 동일 proposal의 deadline 일치와 서버 시각
  offset 기반 countdown을 함께 검증해야 한다.
- 2인 거절은 같은 transaction에서 attempt를 즉시 실패시키고 미응답 proposal/member를
  `EXPIRED`/`EXCLUDED`로 비귀책 종료한다. 거절자에게만 기존 exclusion과 30초 cooldown을
  적용하며 비귀책 pool은 검색 시각 경계에 따라 `WAITING` 또는 `EXPIRED`로 복귀한다.
- 3~4인은 목표 인원 가능성, 최소 2인 가능성과 확정 여부를 응답마다 계산한다. 목표가
  불가능해진 뒤 최소 2명 수락과 전원 허용이 확정되면 미응답자를 비귀책 종료하고 round 2로
  즉시 전환한다. 수락자 중 `allowMinimumTwo=false`가 있거나 두 가능성이 모두 사라지면 즉시
  실패한다.
- 최신 pool 응답에 개인정보 없는 회원별 `terminationReason` 네 값을 추가했고 restriction에
  동일 Backend `Clock`의 `serverNow`를 추가했다. migration은 추가하지 않았다.
- Frontend는 `serverClock.ts`에서 offset·보정 현재 시각·남은 초·동일 deadline의 역방향 점프
  억제를 계산한다. round/deadline key가 바뀌면 실제 연장을 반영하며 0초에는 REST refresh만
  수행한다. WebSocket은 계속 REST refresh trigger로만 사용한다.
- 최종 검토에서 Controller의 `serverNow` JSON 직렬화 기대값과 PostgreSQL 통합 테스트의
  `allowMinimumTwo` fixture 전제가 실제 설정과 다른 두 곳을 바로잡았다. 운영 코드는 추가로
  변경하지 않았다.
- WSL `/mnt/c`는 `9p`/DrvFS bind mount이고 Windows C: 여유 공간이 약 6.3GB(98% 사용)인
  상태였다. Gradle output repository의 고빈도 metadata 쓰기에서 발생한 `Input/output error`는
  권한이나 구현 결함이 아니라 이 조합의 파일시스템 오류로 진단했다.
- 저장소의 `build`/`.gradle`을 삭제하거나 초기화하지 않고 Backend source를 `/tmp`의 새 Linux
  filesystem 작업 디렉터리로 `rsync`한 뒤 Docker Gradle JDK 17과 disposable PostgreSQL
  Testcontainers로 검증했다. matching focused는 37 suites/294 tests, Backend 전체는
  59 suites/386 tests이며 failures/errors/skipped 0건으로 통과했다.
- Frontend 전체 Vitest 18 files/170 tests, `npx tsc --noEmit`, production/PWA build의 기존
  성공 결과를 유지한다. 이번 최종 검증에서는 Frontend 코드를 변경하지 않아 재실행하지 않았다.
- `docs/05_MATCHING_POLICY.md`의 기존 round 1 전체 terminal 집계와 cooldown 시작 설명을
  최신 조기 종료 정책 및 실제 구현과 일치하도록 정리했다.
- 2026-08-15 두 브라우저에서 2인 proposal 거절 직후 양쪽 terminal 전환, 거절자의 30초
  cooldown, 비귀책 상대의 cooldown 미표시와 상대 identity 비노출을 확인했다. dev DB
  읽기 전용 조회에서도 거절자만 `REJECTED` response와 cooldown 1건이 있고 비귀책 상대는
  proposal/member `EXPIRED`/`EXCLUDED`, response·penalty·cooldown 0건임을 확인했다.
- 새로고침과 탭 비활성화·복귀 뒤 상태·countdown 복원을 확인했다. WebSocket 강제 차단과
  client clock 강제 편차 수동 주입은 실행하지 않고 통과한 Frontend 자동 테스트로 대체했다.

## [10-안전 4차] MatchRoom 상대 회원 차단 Frontend

상태: Frontend 구현·자동 검증 및 차단 생성부터 신규 매칭 양방향 제외까지 수동 검증 완료

- 본인을 제외한 상대 카드에 신고와 독립된 차단 action과 대상 nickname을 포함한 최종
  확인 dialog를 추가했다. 향후 양방향 매칭 제외, 상대 비노출, 현재 해제 불가를 안내한다.
- current group의 `groupId`와 카드의 `memberId`를 사용하며 request body에는
  `blockedMemberId`만 포함한다. blocker identity, 자유 reason과 내부 `blockId`는 표시하지 않는다.
- 차단 상태는 신고와 MatchRoom snapshot에서 분리했다. 동기 in-flight guard,
  `AbortController`, request identity로 이중 클릭과 취소·대상 변경·unmount 뒤 늦은 응답을 방어한다.
- 실패는 대상/dialog와 기존 snapshot을 유지해 재시도한다. 성공은 완료 안내만 표시하며
  group 종료, 상대 카드 제거, 신고 호출, REST 재조회와 WebSocket SEND를 실행하지 않는다.
- API focused 20건, 차단 hook focused 4건, MatchRoomPage focused 36건이 성공했다.
  Frontend 전체 Vitest 13 files 149건, `npx tsc --noEmit`, production build와 PWA
  `generateSW`도 성공했다.
- 2026-08-14 두 브라우저와 dev DB에서 차단 생성, 동일 요청 row 1건 유지, 현재
  MatchRoom과 상대 카드 유지, 상대 비노출 및 penalty/cooldown/event 0건을 확인했다.
- 신고·차단은 현재 상태방을 종료하거나 참여자를 퇴장시키지 않는다. 신고는 운영 검토,
  차단은 이후 신규 매칭의 양방향 후보 제외로 처리하며 기존 도착·취소·완료 흐름을 유지한다.
- local dev DB에서 대상 완료 group의 `confirmed_at`을 과거로 조정해 1시간 재매칭 제한
  만료를 재현한 뒤 A/B가 다시 같은 매칭으로 묶이지 않음을 확인했다. `user_blocks`나
  후보 제외 결과는 수정하지 않았으며 `docs/16_MATCH_ROOM_BLOCK_MANUAL_TEST.md`의 최종
  수동 판정을 `PASS`로 마감했다.

## [10-안전 3차] MatchRoom 상대 회원 차단 Backend 1차

상태: Backend 구현 및 PostgreSQL 통합·전체 자동 회귀 완료, Frontend 연결 완료

- `POST /api/match-groups/{groupId}/blocks`를 추가한다. request는 `blockedMemberId`만
  계약으로 사용하고 blocker는 JWT cookie의 인증 회원으로 결정한다.
- 본인 차단을 금지하고 양쪽의 실제 group 참여 이력을 확인한다. group/참여 불일치는
  같은 404로 통합한다.
- `CONFIRMED`, `IN_PROGRESS`와 terminal 시각 기준 종료 후 정확히 30일까지 허용한다.
  terminal timestamp 누락과 기간 초과는 fallback 없이 거절한다.
- `user_blocks` UNIQUE와 `INSERT ... ON CONFLICT DO NOTHING`을 최종 방어선으로 사용하며,
  반복·동시·다른 group 요청에도 기존 row snapshot과 `201 Created`를 반환한다.
- 기존 후보 조회, Scheduler batch 조합, proposal 생성 직전 양방향 차단 제외는 재작성하지
  않고 회귀 테스트로 연결한다.
- 차단 생성과 proposal 생성은 정렬된 member pair advisory transaction lock을 공유한다.
  proposal은 기존 pool row lock 후 member-pair lock을 얻고 block을 재조회하며, 이후 기존
  check-in-pair exclusion lock을 얻는다. 따라서 차단 transaction이 먼저 lock/commit하면
  proposal이 차단을 관찰하고, proposal이 먼저 lock을 얻으면 그 proposal transaction이
  끝난 뒤 차단이 생성된다.
- Frontend 차단 UI, 차단 해제/관리, 관리자 기능, 신고 후 자동 차단, 상대 알림과 자유
  사유는 제외한다. Frontend 수동 검증은 `docs/16_MATCH_ROOM_BLOCK_MANUAL_TEST.md`에
  `PENDING`으로 정리한다.
- 최초 `MatchBlockIntegrationTest` 11건 중 다른 group 멱등 테스트 1건은 두 group이
  같은 fixture `attempt_id=9130001`을 사용해 V3 `uq_match_groups_attempt`와 충돌했다.
  production 경로가 실행되기 전 fixture insert에서 실패한 것으로 확인했다.
- 해당 테스트는 고정된 두 번째 attempt를 먼저 만들고 첫 group 참여를 종료한 다음
  두 번째 active group을 생성하도록 수정했다. 이로써 V3/V16의 한 attempt당 group 1개와
  회원당 active group 1개 제약을 모두 지키며 다른 group 반복 계약을 검증한다.
- `MatchBlockIntegrationTest` 11건과 `MatchProposalCreationServiceIntegrationTest`가
  성공했다. backend 전체 테스트 372건도 failure 0, error 0, skipped 0으로 성공했다.
  전체 종료 중 이전 context의 닫힌 Testcontainers 연결을 Scheduler/Hikari가 확인한
  경고가 있었지만 Gradle 결과에는 영향을 주지 않았다.

## [10-안전 2차] MatchRoom 상대 회원 구조화 신고 Frontend

상태: Frontend 구현 및 자동 검증 완료, 두 브라우저·dev DB 수동 검증 PENDING

- 상대 회원 카드에만 신고 action을 제공하고 여섯 한국어 구조화 사유, 대상·사유
  최종 확인, 운영 검토 및 SAFETY 긴급 연락 안내를 추가했다.
- current group snapshot의 `groupId`와 카드의 `memberId`를 사용해
  `POST /api/match-groups/{groupId}/reports`를 호출하며 reporter ID와 자유 입력은
  request에 포함하지 않는다.
- 동기 in-flight guard, `AbortController`와 request identity로 빠른 이중 클릭,
  dialog 취소 및 다른 상대 선택 뒤 도착한 늦은 응답을 방어한다.
- 성공은 dialog를 닫고 접수 안내만 표시한다. 실패는 기존 snapshot과 dialog를
  유지해 재시도하며 차단, 자동 제재, current group 재조회와 WebSocket event를
  실행하지 않는다.
- focused Vitest 3 files 52건, Frontend 전체 Vitest 12 files 137건과
  `npx tsc --noEmit`을 성공했다.
- `npm run build`의 TypeScript build, Vite production bundle과 PWA `generateSW`
  산출물 생성을 성공했다.
- 실제 두 브라우저 및 dev DB 수동 검증은 실행하지 않았으며
  `docs/15_MATCH_ROOM_REPORT_MANUAL_TEST.md` 기준 `PENDING`이다.

## [10-안전 1차] MatchRoom 상대 회원 구조화 신고 Backend

상태: Backend 구현 및 PostgreSQL 통합·전체 자동 회귀 완료, Frontend·관리자 처리 제외

- `POST /api/match-groups/{groupId}/reports`를 추가하고 reporter는 request가 아니라
  HttpOnly `access_token`의 인증 회원 ID만 사용한다.
- request는 `reportedMemberId`, `reasonCode`만 계약으로 사용하며 V4 CHECK와 같은
  `RUDE`, `SEXUAL_HARASSMENT`, `NO_SHOW`, `SCAM`, `SAFETY`, `OTHER`만 허용한다.
- group row `FOR SHARE` 뒤 신고자와 피신고자의 전체 참여 이력을 확인해 본인 신고,
  비참여 회원과 임의 group ID IDOR을 거절한다. 참여·존재 불일치는 동일 404로 숨긴다.
- 진행 중 `CONFIRMED`·`IN_PROGRESS`는 허용하고, `COMPLETED.completed_at` 또는
  `CANCELLED.cancelled_at`부터 30일 이내와 정확한 경계를 허용한다. terminal 시각
  누락은 임의 fallback 없이 conflict로 거절한다.
- 신규·멱등 재요청 모두 `201 Created`와 같은 report resource snapshot을 반환한다.
  V4 UNIQUE와 `INSERT ... ON CONFLICT DO NOTHING`으로 반복·동시 요청을 한 건으로
  수렴시키고 기존 status와 생성 시각을 초기화하지 않는다.
- 응답은 report ID, group ID, 피신고자 ID, 사유, 상태와 생성 시각만 포함하며
  reporter, `detail_encrypted`와 회원 개인정보를 노출하지 않는다.
- 신고 접수는 penalty/cooldown, 회원 점수·매너온도와 match event를 변경하지 않고
  WebSocket/application event를 발행하지 않는다.
- 기존 V4 schema와 terminal timestamp로 계약을 충족해 신규 migration은 추가하지 않았다.
- 최초 focused 13건 중 30일 초과 테스트 1건은 `minusNanos(1)`이 PostgreSQL
  `TIMESTAMPTZ` 정밀도에서 경계로 정규화되어 실패했다. 경계 밖 값을 1초 차이로
  고친 뒤 focused 13건이 모두 성공했다.
- matching 전체 288건과 backend 전체 360건이 failure·error·skip 없이 성공했다.
  전체 종료 중 이전 context의 닫힌 Testcontainers 연결을 Scheduler/Hikari가 확인한
  경고가 있었지만 Gradle 결과에는 영향을 주지 않았다.
- 차단 API/UI, 관리자 신고 처리 API/UI, 자동 제재, manner temperature 변경,
  자유 입력, Frontend 신고 UI와 자유 채팅은 제외했다.

## [10-매칭 25차] 명시적 거절 상대의 check-in pair 재추천 제외

상태: 구현·자동 회귀, local DB V18 적용과 최소 수동 검증 완료

- 기획서 `MATCH-08` 중 명시적 거절 상대 자동 제외만 구현하고 재매칭 최대 5회 제한은 적용하지 않았다.
- 기존 V1~V17을 수정하지 않고 `V18__add_match_opponent_exclusions.sql`을 추가했다.
- round 1 `INITIAL_MATCH`의 명시적 `REJECTED`만 거절 회원과 나머지 proposal 회원 사이 exclusion을 생성한다. 3인 A 거절은 A-B/A-C만 생성하고 B-C는 생성하지 않는다.
- `TIMEOUT`은 proposal 종료 처리상 자동 거절에 준하지만 명시적 `REJECTED`가 아니다. 따라서 기존 penalty/cooldown만 적용하고 exclusion은 생성하지 않는다. round 2 취소, 인원 미달 자체, 시스템 오류, 정상 완료와 MatchRoom 취소도 exclusion 원인이 아니다.
- member ID 정렬과 원래 check-in 대응을 함께 보존하는 `MatchOpponentPair`를 사용하고 동일 check-in pair 및 source proposal/member pair unique 제약과 `ON CONFLICT DO NOTHING`으로 멱등성을 보강했다.
- response와 exclusion insert는 기존 attempt → proposal → attempt member → 정렬된 pool 잠금 뒤 같은 transaction에서 commit한다. pool/check-in/member 소유 관계도 저장 전에 검증한다.
- requester/legacy 후보 SQL, Scheduler batch 조합과 proposal 생성 직전 `REQUIRES_NEW` 재검증에 동일 exclusion 정책을 적용했다.
- exclusion 생성과 최종 proposal 검증은 정렬된 check-in pair별 `pg_advisory_xact_lock(int,int)`을 공유한다. SHA-256의 앞 64비트를 두 key로 사용하며 lock 획득 뒤 exclusion을 다시 조회한다.
- focused 비컨테이너 4개 class, response PostgreSQL integration, requester/Scheduler/final race PostgreSQL integration과 matching 전체 288건이 성공했다.
- backend 전체 `clean build` 347건이 성공했다. 종료 중 이미 중지된 Testcontainers 연결을 Scheduler/Hikari 종료 thread가 확인한 warning은 있었지만 test와 build 결과에는 영향을 주지 않았다.
- 2026-08-12 local DB에 V18이 성공 적용되었고 `match_opponent_exclusions` 테이블 생성을 확인했다.
- A-B round 1 명시적 거절로 exclusion 1건이 생성되고, 같은 check-in pair가 다시 추천되지 않는 것을 최소 수동 테스트로 확인했다.
- `TIMEOUT` exclusion 미생성은 PostgreSQL 자동 통합 테스트로 대체했으며 통과했다.
- 같은 check-in pair의 재추천 제외 수동 검증 중 두 브라우저의 `/matching` 화면이
  약 1분 동안 `주변 여행자를 찾고 있어요`와 `함께할 분을 확정하고 있어요` 사이를
  반복 전환하는 현상을 확인했다. exclusion DB 정합성과 재추천 방지는 정상이며,
  Scheduler의 짧은 `LOCKED` snapshot과 polling 화면 전환을 함께 조사할 Frontend
  비동기 UX 후속 이슈 `ISSUE-MR-010`으로 분리했다.
- exclusion 적용 여부는 현재 두 pool의 check-in ID 조합 일치로 판단한다. 새 check-in에서는 과거 row가 적용되지 않으며 즉시 삭제 Scheduler는 추가하지 않았다.
- 과거 row의 실제 삭제 기간은 match event·개인정보 보존 정책과 함께 후속 확정한다. REST/WebSocket/Frontend와 log에는 pair, 거절자, source proposal 정보를 노출하지 않는다.
- AI 임베딩, 신고·안전, Frontend UX 안정화와 재매칭 횟수 제한은 제외했다.

## [10-매칭 24차] 축제별 만남 장소 관리·순환 배정·MatchRoom 지도

상태: 구현, Backend·Frontend 자동 회귀 및 dev DB·두 브라우저 수동 검증 완료

- `V15`에서 축제별 복수 장소, 상태·좌표·배정 순서·Kakao 장소 ID 제약, 활성 후보 index와 nullable group 주소 snapshot을 추가했다.
- 관리 API는 DB의 `ADMIN` role만 등록·수정·활성/비활성·목록 조회를 허용한다. Admin UI는 현재 mock dashboard 범위를 과도하게 확장하므로 제외했다.
- 신규 pool은 해당 축제의 `ACTIVE` 장소가 없으면 `MATCHING_MEETING_POINT_NOT_READY`로 차단한다.
- confirm transaction은 기존 lock 뒤 festival row를 `FOR UPDATE`로 잠그고 `assignment_order, id` 후보를 `assignedGroupCount % candidateCount`로 선택한다. 후보가 없으면 전체 rollback한다.
- current-group은 snapshot 기반 nullable `meetingPoint`, 후보 검색 반경과 안내 전용 `arrivalRadiusMeters=150`을 반환한다.
- MatchRoom은 도착 action 위에 장소 카드와 Kakao Maps 단일 핀을 표시하며 SDK 실패 시 장소명·주소를 유지한다. SDK loader는 동시 호출 Promise를 공유하고 실패한 script를 제거해 재진입 시 재시도한다. 정책과 충돌하던 mock route/page/data는 제거했다.
- 최초 focused Backend 27건은 25건 성공, `FestivalMeetingPointAdminServiceTest`의 nested Mockito stubbing 오류 2건 실패였다. 운영 코드는 변경하지 않고 member mock을 지역 변수로 분리해 수정했다.
- 수정 후 meeting-point focused unit/Controller 11건, test source compile, PostgreSQL Testcontainers repository 3건과 confirm transaction 46건, matching 전체 266건, Backend 전체 322건이 모두 성공했다.
- `package-lock.json` 기준 Windows `npm ci`로 의존성을 복원했고 package manager와 lockfile 의미 내용은 변경하지 않았다. WSL npm은 자체 `Exit handler never called` 오류로 완료되지 않아 Windows npm으로 재실행했다.
- Frontend 전체 Vitest 11 files 119건, `npx tsc --noEmit`, production/PWA build 성공.
- 2026-08-09 dev DB의 festival `144`, member `2`, `27`과 유효한 `ACTIVE` check-in으로 두 브라우저 수동 검증을 완료했다. 첫 번째 확정 group `21`에는 `dev-meeting-point-1`이, 취소 후 두 번째 확정 group `22`에는 `dev-meeting-point-2`가 배정되어 후보 순환과 group snapshot 저장을 확인했다.
- 첫 번째 group의 두 회원에게 동일한 장소명·주소와 `arrivalRadiusMeters=150` 안내가 표시되었고, Kakao SDK를 불러오지 못한 환경에서도 장소명·주소 fallback이 유지되었다. 실제 Kakao JavaScript Key와 허용 도메인을 사용한 지도 핀 표시는 별도 운영 환경 검증으로 남겼다.
- 저장소 전체 `git diff --check`는 이번 수정 파일이 아닌 기존 working tree의 광범위한 CRLF 변경을 trailing whitespace로 판정해 실패했다. 이번 작업 파일 대상 검사는 통과했으며 기존 파일의 줄바꿈은 일괄 변경하지 않았다.
- GPS 검증, 도착 body 변경, 자동 후보 검색, 관광공사 fallback, 장소별 반경, COMPLETED, 채팅과 Redis는 제외했다.

## [10-매칭 23차 준비] 만남 포인트·단말 위치 확인 정책 정합화

상태: 문서 정책 정리 완료, 구현 범위 결정 전

- 관광공사 축제 공식 좌표를 실제 약속 장소가 아닌 주변 POI 검색 중심점으로 재정의
- 관광공사 `locationBasedList1`은 관광 POI·fallback, Kakao Local API는 카페·편의점·주차장·음식점 등 실제 장소 후보 검색으로 역할 분리
- Kakao Maps SDK는 최종 만남 포인트 지도와 핀 표시에 사용
- `2km`는 후보 검색 범위이며 단말 위치 확인 반경이 아님을 명시
- 위치 확인 기준점을 축제 좌표가 아니라 최종 확정 만남 포인트 좌표로 정리
- 축제별 검증된 만남 장소를 복수 등록하고 그룹 확정 시 MVP 순환 방식으로
  1곳을 고정 배정하는 정책 확정
- 같은 시간대 여러 그룹에 같은 장소가 배정될 수 있으며 향후 혼잡도 기반으로
  분산하는 확장 방향 명시
- 위치기반서비스사업 신고와 관련 약관·동의를 전제로 사용자 좌표·정확도·측정
  시각을 backend에 보내 일회성 거리 판정을 수행하는 정책으로 변경
- 원본 사용자 좌표는 저장하지 않고 계산 후 폐기하며 허위 도착은 신고와 운영
  검토로 보완
- 단말 확인 반경, GPS 정확도와 측정값 유효시간은 결정 필요
- backend/frontend 코드, Flyway migration, 환경설정과 외부 API 연동은 수정하지 않음

## [10-매칭 22차] 확정 후 자발적 취소와 30분 마감 NO_SHOW

상태: 구현, dev DB·두 브라우저 수동 검증 및 Frontend 보완 완료. Windows PostgreSQL Testcontainers 전체 재실행은 별도 환경 검증으로 유지

### Windows Testcontainers 1차 실패 분석과 테스트 격리 보완

- Windows에서 관련 통합 테스트 36건 중 3건 실패 확인
- `confirmedMemberCount=3`, active member 2명을 충돌로 보던 기존 REST assertion을 현재 정책에 맞게 정상 응답으로 변경
- 실제 비정상 데이터는 active member가 2명 미만이거나 최초 확정 인원보다 많은 경우로 분리해 충돌 검증
- arrival 통합 테스트의 고정 Clock은 `NOW + 10초`였지만 경계 fixture가 `NOW`를 기준으로 계산해 10초 오차가 발생한 원인 수정
- DB 입력 시각과 fixed Clock을 `TEST_NOW`, `ChronoUnit.MICROS` 기준으로 통일
- PostgreSQL이 안정적으로 표현하지 못하는 `minusNanos(1)`을 제거하고 deadline 초과는 1초 차이로 검증
- 운영 코드의 `estimatedArrivalAt <= deadline`, `now < deadline` 비교는 변경하지 않음
- 모든 matching `@SpringBootTest` 통합 테스트에 `app.matching.scheduler.enabled=false`, `app.matching.no-show-scheduler.enabled=false`를 명시
- 사용자 환경변수와 무관하게 일반 통합 테스트 종료 후 Scheduler가 종료된 Testcontainers DB에 접근하지 않도록 격리
- Scheduler 전용 `ApplicationContextRunner` 테스트의 활성화 계약은 변경하지 않음
- 수정 후 test source compile 성공
- 비컨테이너 정책 회귀 21건 성공: current group 9건, arrival-time 8건, Scheduler 조건 4건
- 현재 WSL은 Docker command가 없고 Windows executable interop도 `UtilBindVsockAnyPort`로 실패해 요청한 Windows Testcontainers 3단계 재실행은 미완료

- 기존 V1~V13을 수정하지 않고 `V14__add_match_room_cancellation_no_show.sql` 추가
- group 확정 시 pool의 `allow_minimum_two`를 `match_group_members`에 snapshot 저장
- 기존 row는 `match_groups.attempt_id -> match_attempt_members -> match_pools` 관계로 backfill하며 매핑 실패 row가 있으면 migration 실패
- 구조화된 세 취소 사유만 받는 `PUT /api/matching/groups/me/current/cancellation` 추가
- 확정 후 3분 이내 무패널티, 이후 deadline 전 `penalty_score +1` 및 KST 당일 10/30/60분 cooldown 적용
- deadline부터 `JOINED`, `ARRIVAL_TIME_SELECTED`를 `NO_SHOW`로 처리하는 기본 비활성 Scheduler 추가
- NO_SHOW는 `penalty_score +3`, KST 당일 30/60분 cooldown이며 `manner_temperature`는 변경하지 않음
- 잠금 순서는 group row, group member ID 오름차순, cooldown/member 관련 row 순서로 고정
- group별 `REQUIRES_NEW`, `FOR UPDATE SKIP LOCKED`, 상태 재검증과 group/member/cause unique index로 반복 tick과 다중 실행 멱등성 보강
- 현재 유효 인원이 3명 이상이거나 2명 모두 최소 인원을 허용하면 group 유지
- 유지 불가 시 귀책 회원 상태를 유지하고 비귀책 회원을 `LEFT`, group을 `CANCELLED`로 전환
- `confirmedMemberCount`는 최초 확정 인원으로 유지하고 `currentMemberCount`를 별도 응답
- `MEMBER_CANCELLED`, `MEMBER_NO_SHOW`, `MATCH_CANCELLED` event와 AFTER_COMMIT 알림 추가
- MatchRoomPage에 구조화된 취소 dialog, 현재 인원과 신규 timeline 문구, 성공 후 `/matching` 이동 안내 추가
- Backend focused 47건 성공
- Frontend focused 3 files, 56건 성공 및 `npx tsc --noEmit` 성공
- Frontend 전체 10 files, 110건, production/PWA build 성공
- Backend `build -x test` 성공
- PostgreSQL focused는 Docker client 탐지 실패로 Flyway와 assertion 실행 전 initialization 실패
- matching 전체 114건 실행에서 일반 테스트 98건 통과, Testcontainers 14건은 Docker initialization 실패, scheduling 조건 회귀 2건은 원인을 수정한 뒤 focused 재실행 성공
- V14 취소·NO_SHOW PostgreSQL 통합 테스트 2건을 추가하고 compile 성공했으나 Docker 부재로 assertion 미실행
- 초기 자동 검증 당시에는 로그인 session과 local runtime이 없어 회원 `2`, `27`, festival `144` 수동 검증을 실행하지 못함
- meeting point, Kakao Maps, COMPLETED, 평가/신고, manner temperature, 자유 채팅, Redis와 재매칭은 제외

### 2026-08-04 dev DB·두 브라우저 최종 수동 검증

- festival `144`, member `1`, `2`, `27`로 확정 후 취소, NO_SHOW Scheduler와 인원 감소 시나리오 검증 완료
- deadline 이후 도착 거절, `no_show_at`, `MEMBER_NO_SHOW`, penalty `+3`, 첫 30분·당일 반복 60분 cooldown 확인
- 취소 3분 이후 `CANCEL +1`과 첫 10분 cooldown, 동일 요청 재전송 멱등성 확인
- Scheduler 반복 tick 이후 member event, penalty event와 cooldown 각 1건 유지 확인
- 3명 group에서 잔여 2명의 `allow_minimum_two`가 모두 true이면 유지하고 false 포함 시 종료되는 정책 확인
- 기존 2시간 active cooldown보다 새 NO_SHOW cooldown이 짧을 때 기존 `expires_at` 보존 확인
- deadline 이후 도착 action 노출과 종료 안내 history state 잔존 Frontend 문제 수정 및 브라우저 재검증 완료
- Frontend focused 2 files, 43건과 `tsc --noEmit` 성공
- 상세 실행 결과와 SQL 증거는 `docs/14_MATCH_ROOM_NO_SHOW_MANUAL_TEST.md`에 기록

## [10-매칭 21차] 도착 예정 선택지와 상대 변경 snackbar

상태: 구현 및 비컨테이너 자동 회귀 완료, Testcontainers와 수동 화면 검증은 환경 제약으로 미완료

- 신규 도착 예정 선택값을 `5`, `10`, `20`, `25`분으로 변경하고 `지금 도착(0)`, `30분` 신규 선택 제거
- 응답/과거 데이터 타입은 `0`, `5`, `10`, `20`, `25`, `30`을 유지해 과거 `0`을 `곧 도착 예정`, `30`도 정상 표시
- 기존 migration을 수정하지 않고 `V13__allow_25_arrival_minutes.sql` 추가
- `V13`에서 실제 constraint `chk_match_group_members_arrival_minutes`를 `NULL 또는 0,5,10,20,25,30` CHECK로 교체하며 기존 row를 변환하지 않음
- Backend request validation/service는 DB 호환 집합과 분리해 신규 `5`, `10`, `20`, `25`만 허용
- 기존 `arrivalDeadlineAt = confirmedAt + 30분`, `now < deadline`, `now + minutes <= deadline`, 멱등성, transaction, rollback과 AFTER_COMMIT 계약 유지
- 정상 REST snapshot 전후 상대 회원의 `arrivalMinutes` 또는 `arrivalTimeSelectedAt` 실제 변경만 감지해 nickname 포함 snackbar 표시
- 최초 snapshot, 본인 mutation, 동일 snapshot/멱등 refresh, 실패한 refresh와 잘못된 WebSocket payload에는 snackbar를 만들지 않음
- WebSocket은 payload를 직접 적용하지 않는 REST refresh trigger로 유지하고 polling fallback도 같은 snapshot 비교 사용
- snackbar는 하단 navigation 위 `bottom-24`, `role="status"`, `aria-live="polite"`로 표시하고 3초 뒤 자동 제거하며 연속 변경 시 기존 timer 교체
- Backend focused 41건 통과
- PostgreSQL focused 2개 class는 Docker client 탐지 실패로 assertion/migration 실행 전 initialization 실패
- matching 전체 104건 중 일반 90건 통과, Testcontainers 14개 class initialization 실패
- backend 전체 148건 중 일반 133건 통과, Testcontainers 15개 class initialization 실패
- backend `./gradlew build -x test` 성공
- frontend focused 3 files 52건, 전체 10 files 106건, `npx tsc --noEmit`, production/PWA build 성공
- repository 전체 `git diff --check`는 작업 시작 전부터 존재한 광범위한 CRLF 변경을 trailing whitespace로 판정해 실패했으며, 기존 파일을 일괄 정규화하지 않음
- 작업 시작 시 이미 수정 상태였던 `V1`~`V12`는 건드리지 않고 신규 `V13`만 추가
- `NO_SHOW`, Scheduler, 취소·패널티, meeting point, Kakao Maps, `COMPLETED`, 자유 채팅, group topic, client `SEND`, Redis는 추가하지 않음

## [10-매칭 20차] MatchRoomPage 30분 절대 도착 마감

상태: 구현 및 비컨테이너 자동 회귀 완료, Testcontainers와 두 브라우저 수동 검증은 실행 환경 제약으로 미완료

- `arrivalDeadlineAt = confirmedAt + 30분`을 공통 정책 계산으로 정의하고 current group 응답에 추가
- 기존 `confirmed_at`에서 파생하므로 Flyway migration과 schema 변경 없음
- 도착 예정 시간 transaction에서 active group, active member와 `JOINED`/`ARRIVAL_TIME_SELECTED` 상태를 잠금 후 재검증
- 현재 시각은 deadline 이전이어야 하고, 실제 값 변경의 `현재 시각 + arrivalMinutes`는 deadline 이하일 때만 허용
- `estimatedArrivalAt == arrivalDeadlineAt`은 허용하고 deadline 시각부터는 선택 거절
- deadline 전 같은 값 반복은 기존 선택 기준 시각, deadline, event와 WebSocket 알림을 변경하지 않는 멱등 계약 유지
- 다른 값 변경과 동시 요청도 파생 deadline을 연장하거나 다시 시작하지 않음
- 마감 위반은 내부 group/member 존재 여부를 노출하지 않는 `MATCHING_ARRIVAL_DEADLINE_EXCEEDED` 409 오류로 반환
- MatchRoomPage에 최종 도착 마감, 전체 남은 시간, 실제 예상 도착 시각과 예상 도착까지 남은 시간 표시
- 개별 예정 시각이 지났지만 전체 마감 전이면 `예정 시간이 지났어요`를 표시하고 남은 범위에서 재선택 가능
- 남은 전체 시간보다 긴 선택지는 비활성화하고 전체 마감부터 시간 선택 UI 차단
- 본인이 `ARRIVED`이면 도착 예정 시간과 도착 완료 action을 모두 숨기고 기존 도착 완료 시각만 표시
- frontend timer는 표시만 갱신하며 server 상태, `NO_SHOW`와 event를 생성하지 않음
- WebSocket 알림과 polling은 기존 current group/events REST refresh trigger 구조 유지
- Backend focused 단위/Controller 36건 통과, backend `build -x test` 성공
- matching 전체 실행은 102건 중 일반 테스트 88건 통과, Testcontainers 14개 class가 Docker 미탐지로 initialization 실패
- backend 전체 실행은 146건 중 일반 테스트 131건 통과, Testcontainers 15개 class가 같은 사유로 initialization 실패
- 신규 `MatchArrivalTimeServiceIntegrationTest` 경계·멱등·rollback·동시성 코드는 컴파일됐지만 현재 WSL에서 Docker Desktop integration이 없어 실행하지 못함
- frontend focused 45건, 전체 99건, `npx tsc --noEmit`, production/PWA build 성공
- `git diff --check`는 이번 범위 밖 기존 작업 트리의 CRLF 전체 변경을 trailing whitespace로 판정해 repository 전체 기준 실패
- `localhost:8080`, `localhost:5173` runtime이 없고 Windows executable interop도 `UtilBindVsockAnyPort` 오류여서 festival `144`, member `2`, `27` 두 브라우저 수동 검증 미실행
- `NO_SHOW`, Scheduler, 취소·패널티, meeting point, 지도, `COMPLETED`, 자유 채팅, group topic, client `SEND`, Redis는 추가하지 않음

## [10-매칭 19차] MatchRoomPage 시스템 이벤트 타임라인

상태: 구현 및 자동 회귀 완료, 두 브라우저 dev 수동 검증 미실행

- 자유 채팅이 아닌 읽기 전용 상태 기록으로 `MATCH_CONFIRMED`, `ARRIVAL_TIME_SELECTED`, `MEMBER_ARRIVED` 타임라인 추가
- `GET /api/matching/groups/me/current/events` 추가: 식별자 입력 없이 인증 회원의 current active group만 조회하며 active group 부재는 `200 data:null`
- 기존 확정 transaction에 actor 없는 `MATCH_CONFIRMED` audit event 저장을 추가하고 event insert 실패 시 확정 전체 rollback 회귀 검증
- raw JSON payload를 반환하지 않고 event ID/type/KST 시각, 같은 active group actor의 ID/nickname, 검증된 `arrivalMinutes`만 DTO로 공개
- 최신 50건을 `created_at DESC, id DESC`로 선택한 뒤 응답은 시간/ID 오름차순으로 반환
- 허용하지 않은 도착 분 또는 malformed `ARRIVAL_TIME_SELECTED` payload는 해당 event만 제외하고 전체 API는 성공
- 다른 group 또는 inactive/unrelated member의 nickname은 공개하지 않고 actor를 `null`로 반환
- 최초 진입·새로고침·WebSocket 연결/재연결·상태 알림·polling에서 current group과 events REST를 함께 refresh
- 동일 in-flight refresh 병합, WebSocket 중복 trigger 후속 refresh, mutation generation으로 늦은 이전 응답의 최신 snapshot 덮어쓰기 방지
- mutation 성공 시 optimistic event를 추가하지 않고 DB commit 후 events REST 결과로 timeline을 교체
- focused backend 84건, matching 전체 234건, backend 전체 278건, backend build 성공
- frontend focused 39건, 전체 94건, `npx tsc --noEmit`, production/PWA build 성공
- Windows 8080/5173 dev runtime과 식별 가능한 두 로그인 session이 없어 두 브라우저 수동 검증은 미실행
- 자유 text input, 전송 버튼, client SEND, group topic, Redis, meeting point, COMPLETED 전환은 제외
- 기존 Flyway migration과 schema는 변경하지 않았으며 기존 `idx_match_events_group_created_at`을 사용

## [10-매칭 18차] 도착 완료 동시성·rollback 검증 보강

상태: PostgreSQL 동시성·rollback·AFTER_COMMIT 자동 검증 완료, 두 브라우저 dev 수동 검증 미실행

- 신규 사용자 기능과 운영 API/schema 변경 없이 기존 `MatchArrivalTimeServiceIntegrationTest` 보강
- `pgvector/pgvector:pg16` Testcontainers와 실제 별도 thread/transaction으로 같은 group의 서로 다른 두 회원 동시 도착 검증
- 두 요청 10초 timeout 내 정상 완료, 양쪽 ARRIVED, 회원별 `MEMBER_ARRIVED` 1건, group IN_PROGRESS와 startedAt/confirmedAt 불변 계약 검증
- 양쪽 current group snapshot 일치, active member count와 confirmedMemberCount 일치, COMPLETED 미전환 검증
- 동일 회원 동시 도착 두 요청 성공, arrivedAt/startedAt 불변, event 1건과 active 회원별 알림 1회 검증
- member update, group update, MEMBER_ARRIVED insert 실패를 PostgreSQL test trigger로 각각 강제하고 member/group/event/current snapshot 전체 rollback 검증
- rollback과 멱등 요청의 AFTER_COMMIT STOMP 알림 부재 검증
- 이미 IN_PROGRESS인 group에 group update 실패 trigger를 설치해도 member 도착이 성공하여 불필요한 group update가 없음을 검증
- 실제 변경마다 active member 전원 `MEMBER_ARRIVED` 알림, 다른 group 회원 제외, 기존 ARRIVAL_TIME_SELECTED 알림 회귀 검증
- arrival PostgreSQL integration 13건, matching 전체 212건, backend 전체 266건, backend build 성공
- frontend 운영 코드/테스트 수정 없이 전체 83건, `npx tsc --noEmit`, production build 성공
- 테스트 matcher 타입 추론 compile 오류만 수정했으며 운영 코드 결함은 발견되지 않음
- Windows 8080/5173 dev runtime과 식별 가능한 두 로그인 session이 없어 두 브라우저 수동 검증은 미실행
- 기존 dev 이력의 festival `144`, member `2`, `27`을 확인했으나 현재 DB를 추정하거나 변경하지 않음
- meeting point, COMPLETED, 취소·신고·평가, 채팅, group topic, client SEND, Redis는 제외

## [10-매칭 17차] MatchRoomPage 도착 완료

상태: Windows backend gate 해소, 도착 완료 구현 및 자동 회귀 완료, 두 브라우저 dev 수동 검증 미실행

- Windows PowerShell, Azul Java 17.0.15, Docker Desktop과 `pgvector/pgvector:pg16` Testcontainers로 직전 backend 미검증 해소
- native timestamp projection, 미정의 route 500 처리, Mockito fixture와 Windows SQL fixture encoding 결함 수정
- body와 식별자 없는 `PUT /api/matching/groups/me/current/arrival` 추가
- `group row -> group member row` 잠금 후 `JOINED`/`ARRIVAL_TIME_SELECTED -> ARRIVED` 처리
- 최초 도착에서 `CONFIRMED -> IN_PROGRESS`, `started_at`을 최초 한 번만 설정
- 기존 도착 예정 값은 유지하고 동일 ARRIVED 반복의 시각/event/WebSocket 알림 중복 방지
- 실제 변경 commit 후 `MEMBER_ARRIVED`를 기존 `/user/queue/matching`으로 active member 전원 알림
- current group에 `startedAt`, `currentMemberId`, member `arrivedAt` 추가
- 확인 panel 기반 `도착했어요` 동선, 실패 snapshot 보존, 도착 시각 KST 표시 추가
- 선행 focused, PostgreSQL integration, WebSocket focused와 backend build 성공
- 신규 focused/PostgreSQL 회귀, frontend focused 42건·전체 83건, TypeScript와 production build 성공
- 신규 변경 포함 matching 전체 206건, backend 전체 260건과 최종 build 성공
- 두 브라우저 dev 수동 검증은 준비된 두 로그인 session이 없어 미실행
- 채팅, group topic, client SEND, Redis, meeting point, 지도, COMPLETED, 취소·신고·평가는 제외

## [10-매칭 16차] MatchRoomPage 도착 예정 시간 선택

상태: 구현 및 frontend 전체 자동 검증 완료, backend 자동 검증과 두 브라우저 dev 수동 검증은 실행 환경 제약으로 미실행

- 기존 V3 schema의 `arrival_minutes`, `arrival_time_selected_at`, 허용값 CHECK와 `ARRIVAL_TIME_SELECTED` event type을 사용해 신규 migration 없이 구현
- `PUT /api/matching/groups/me/current/arrival-time` 추가, `access_token` HttpOnly cookie 회원 기준으로만 처리
- request는 `arrivalMinutes`만 받으며 `0`, `5`, `10`, `20`, `30`만 validation 통과
- active group을 잠근 뒤 로그인 회원의 group member를 잠그는 `group row -> group member row` 순서 적용
- 잠금 후 group/member 상태를 재검증하고 `JOINED -> ARRIVAL_TIME_SELECTED`, 기존 선택값 변경 지원
- `ARRIVED`, inactive member, `COMPLETED`/`CANCELLED` group 변경 거절
- 같은 값 반복 요청은 snapshot을 반환하되 member update, `match_events`, WebSocket 알림을 만들지 않는 멱등 처리
- 실제 변경은 member 상태·분·선택 시각과 최소 JSON payload의 `match_events` 저장을 같은 transaction에서 처리
- 실제 commit 후 active group member 전원에게 기존 `/user/queue/matching`으로 `ARRIVAL_TIME_SELECTED` refresh 알림 fan-out
- current group member 응답에 `arrivalMinutes`, `arrivalTimeSelectedAt` 추가, 기존 2-query/N+1 방지와 결정적 정렬 유지
- MatchRoomPage에 접근 가능한 도착 예정 시간 선택 panel과 0/5/10/20/30분 선택지 추가
- mutation 중 중복 제출 방지, 성공 snapshot 즉시 반영, 실패 시 기존 snapshot 유지와 오류 안내 제공
- member 행에 `도착 시간 미정`, `곧 도착 예정`, `N분 후 도착 예정`, `도착 완료` 표시
- frontend focused 5 files 43건 통과
- frontend 전체 10 files 81건 통과
- `npx tsc --noEmit` 성공
- frontend production build 성공, 1,621 modules transformed 및 PWA service worker 생성 완료
- backend focused/unit, PostgreSQL integration, WebSocket, matching 전체, backend 전체와 build는 현재 WSL에 Java와 Docker가 없어 실행하지 못함
- 직전 15차 MatchRoomPage backend 자동 검증도 같은 이유로 여전히 미검증
- 두 브라우저 dev 수동 검증은 dev runtime과 로그인 session이 없어 미실행
- 자유 채팅, 도착 완료, group topic, client STOMP `SEND`, Redis, meeting point, 지도, 취소·신고 기능은 추가하지 않음

## [10-매칭 15차] 읽기 전용 MatchRoomPage와 current group festival 계약

상태: 구현 및 frontend 전체 자동 검증 완료, backend 자동 검증과 dev 수동 검증은 실행 환경 제약으로 미실행

- 기존 `GET /api/matching/groups/me/current`와 `access_token` HttpOnly cookie 인증 경계를 유지
- 기존 group 응답 필드를 유지하고 `festival`의 `festivalId`, `title`, `address`, `eventStartDate`, `eventEndDate` summary 추가
- active member 공개 응답에 `JOINED`, `ARRIVAL_TIME_SELECTED`, `ARRIVED` 상태 추가
- active group과 festival을 한 projection query로, active member와 공개 profile을 한 projection query로 조회해 참여자 수와 무관한 2개 query 구조 유지
- `confirmed_member_count` 불일치, 로그인 회원 누락, 다중 active group은 기존 `MATCHING_CONFLICT` 계약 유지
- `/match-room` route와 current group 전용 `useMatchRoom` 상태 복원 hook 추가
- 최초 mount, WebSocket 연결·재연결 성공, `/user/queue/matching` 정상 알림 수신 시 current group REST refresh
- WebSocket 미연결·장애 구간에는 5초 polling fallback을 사용하고 연결 성공 시 fallback timer 해제
- current group이 없으면 `/matching`으로 replace 이동하고 loading, API 오류 안내, 수동 재시도 UI 제공
- 확정 시각·인원·group 상태, 축제명·주소·기간, 확정 멤버 nickname·공개 profile image·참여 상태 표시
- 기존 `/matching`의 `MATCHED` 카드에 `상태방 들어가기` 버튼을 추가하고 확정 직후 자동 이동은 추가하지 않음
- URL에 `groupId`를 포함하지 않고 다른 group 직접 조회 route/API를 추가하지 않음
- 신규 group topic, client STOMP `SEND`, Redis, 외부 broker, SockJS, 자유 채팅, meeting point, 도착 기능, 신규 Flyway migration 없음
- frontend focused 5 files 31건 통과
- frontend 전체 10 files 70건 통과
- `npx tsc --noEmit` 성공
- frontend production build 성공, 1,621 modules transformed 및 PWA service worker 생성 완료
- backend focused/PostgreSQL/matching 전체/backend 전체/build는 현재 WSL에 Linux Java가 없고 Windows Java interop도 `UtilBindVsockAnyPort` 오류로 실행하지 못함
- 두 브라우저 dev 수동 검증은 이 작업 환경에서 로그인 세션과 dev runtime을 준비하지 않아 미실행
- 수동 검증 대상은 양쪽 동일 group·festival·member 확인, 새로고침·직접 URL·WebSocket 재연결 복원, active group 없는 회원의 `/matching` 복귀, 임의 group route·채팅 UI·group topic 부재 확인

## [10-매칭 14차] terminal pool 재신청 화면 전환

상태: frontend 구현, 전체 자동 회귀 및 두 브라우저 dev 화면 수동 검증 완료

- backend가 반환한 `CANCELLED`/`EXPIRED` terminal 상태를 `IDLE`로 바꾸지 않고 서버 상태와 로컬 retry form 모드를 분리
- `retrySourcePoolId`가 현재 최신 terminal pool ID와 같을 때만 일시적인 retry form 유지
- 같은 terminal pool을 REST로 다시 조회하거나 WebSocket 알림 후 refresh해도 retry form 유지
- 다른 최신 pool, `WAITING`, `LOCKED`, `RESPONSE_PENDING`, active proposal 또는 current group이 확인되면 retry 모드를 해제하고 서버 상태를 우선
- active cooldown 중에는 retry form 진입과 pool 제출을 모두 차단
- 재신청 `festivalId`는 `location.state.festivalId`, retry 대상 terminal pool의 `festivalId`, 개발 환경 `VITE_DEV_FESTIVAL_ID` 순서로 결정
- 사용자가 희망 인원과 최소 2명 진행 옵션을 다시 선택하고 기존 `POST /api/matching/pools`로 신규 pool을 생성
- POST 성공 응답의 새 pool을 `WAITING` 또는 `LOCKED`로 즉시 반영하고 retry 모드를 해제
- POST 실패 시 terminal 서버 상태, retry form과 사용자가 선택한 조건을 유지
- browser 새로고침과 새 mount에서는 로컬 retry 모드가 사라지고 기존 REST 우선순위로 terminal 또는 최신 서버 상태 복원
- backend API, DB schema, Flyway, Redis, WebSocket STOMP와 polling fallback 구조 및 package 의존성은 변경하지 않음
- 기존 pool, attempt, proposal, response, group 이력은 갱신·삭제하지 않고 신규 pool 생성 방식으로 보존
- frontend focused 5 files, 47 tests 통과
- frontend 전체 8 files, 59 tests 통과
- `npx tsc --noEmit` 성공
- frontend production build 성공, 1,619 modules transformed 및 PWA service worker 생성 완료
- dev DB의 festival `144`, member `2`, `27`과 유효한 `ACTIVE` check-in을 사용해 두 브라우저 화면 수동 검증 완료
- `CANCELLED` terminal 화면에서 `다시 신청하기` 클릭 후 신규 신청 form 전환 확인
- 희망 인원과 최소 2명 진행 옵션 변경 및 DevTools fetch 없이 신규 pool 신청 확인
- 신청 직후 `WAITING`, 새로고침 후 최신 pool 상태 복원 확인
- 두 브라우저 신청 후 proposal 전환, A 수락 후 A `RESPONSE_PENDING`·B proposal 유지 확인
- B 수락 후 양쪽 `MATCHED` 전환 및 `MATCHED`에서 재신청 UI 미표시 확인
- retry form 상태에서 새로고침 시 서버 terminal 카드로 복원 확인

## [10-매칭 13차] WebSocket STOMP 매칭 상태 변경 알림

상태: 전체 자동 회귀 및 두 브라우저 dev 수동 검증 완료

- PostgreSQL 최종 상태와 REST 상태 복원 계약을 유지하고 WebSocket을 즉시 변경 알림으로만 추가
- `/ws` handshake에서 `access_token` HttpOnly cookie를 검증하고 회원 ID 기반 Principal 설정
- client 구독을 본인 `/user/queue/matching`으로 제한하고 client STOMP `SEND` 거절
- proposal 생성, 응답, 인원 미달 round 2, timeout, 실패와 group 확정 변경을 회원별로 알림
- DB transaction 안에서는 application event만 발행하고 실제 STOMP 전송은 `AFTER_COMMIT`에서 수행
- payload는 `MATCHING_STATE_CHANGED`, 변경 이유, 발생 시각만 제공하고 frontend는 기존 REST 조회로 복원
- frontend는 현재 origin `/ws` 연결, 재접속 성공과 알림 수신 시 REST refresh 수행
- 기존 2초/5초 polling과 오류 backoff를 WebSocket 장애 fallback으로 유지
- local Vite `/ws -> http://localhost:8080`, `ws: true` proxy 추가
- dev nginx `/ws` Upgrade proxy 활성화
- Redis, 외부 broker, SockJS, 자유 채팅, client message endpoint, Flyway migration은 추가하지 않음
- frontend TypeScript 검사 성공
- WebSocket 포함 frontend focused 27건 통과
- Windows Temurin Java 17에서 WebSocket focused backend 6건 통과
- `pgvector/pgvector:pg16` Testcontainers matching 전체 193건 통과
- root context test도 `pgvector/pgvector:pg16` Testcontainer로 격리한 backend 전체 237건 통과
- backend build 성공
- frontend 전체 39건, TypeScript 검사와 production build 성공
- 두 브라우저 dev 수동 검증에서 양쪽 `/ws` 연결 및 `/user/queue/matching` 구독 성공
- 양쪽 pool 진입 후 proposal 화면 전환 성공
- A 수락 후 A는 `RESPONSE_PENDING`, B는 proposal 유지
- B 수락 후 양쪽 `MATCHED` 화면 전환 성공 및 동일한 확정 group 확인
- 새로고침 후 `MATCHED` 상태 복원 성공
- WebSocket 재연결 후 REST 상태 복원 성공
- terminal pool 상태에서 `다시 시도`가 신규 신청 화면으로 돌아가지 않는 문제는 완료된 기능이 아니며, 이번 WebSocket STOMP 작업에서 수정하지 않고 별도 Frontend 후속 작업으로 남김
- WebSocket STOMP는 자유 채팅이 아닌 매칭 상태 동기화 전용이며 Redis, Flyway, 자유 채팅 관련 변경은 이번 구현 범위에서 제외

## [10-매칭 12차] pool 신청 AFTER_COMMIT 후속 transaction 경계 수정

상태: 운영 코드 수정, PostgreSQL Testcontainers 통합·backend 전체 회귀·build 및 dev DB 수동 재검증 완료

- 실제 원인은 `@TransactionalEventListener(AFTER_COMMIT)` 시점에 원본 transaction이 commit됐어도 transaction resource가 thread에 남아 있을 수 있는데, 후속 claim/read/release가 기본 `REQUIRED`를 사용해 종료된 transaction 문맥에 참여한 점
- requester claim을 `REQUIRES_NEW`로 변경해 `WAITING -> LOCKED`와 `lockToken`을 proposal 생성 전에 독립 transaction으로 commit
- token 후보 batch read를 read-only `REQUIRES_NEW`로 분리해 commit된 claim만 새 persistence context에서 조회
- 기존 proposal 생성의 그룹별 `REQUIRES_NEW`를 유지해 attempt/member/proposal과 `LOCKED -> PROPOSED` 전이의 원자성 보존
- release를 `REQUIRES_NEW`로 변경해 후보 부족·proposal 실패·미사용 token의 `LOCKED`를 외부 transaction과 무관하게 `WAITING` 또는 `EXPIRED`로 복구
- orchestration 전체에는 transaction을 추가하지 않아 claim/read/create/release의 짧은 단계별 경계와 Scheduler fallback 구조 유지
- 실제 `MatchPoolEntryService.enter()` transaction commit을 두 번 거치는 AFTER_COMMIT PostgreSQL Testcontainers 통합 테스트 추가
- 첫 회원은 `WAITING`·lock 없음·attempt/proposal 0건, 두 번째 회원 commit 직후 두 pool `PROPOSED`, `POOL_ENTRY` attempt 1건, 회원별 proposal 1건 검증
- claim과 release가 외부 transaction rollback과 무관하게 독립 commit되는 transaction 검증 보강
- 기존 후보 부족, proposal 생성 실패 rollback/release, 두 trigger 동시 실행, trigger/Scheduler 동시 실행, Scheduler `created_by=SCHEDULER` 회귀 테스트 유지
- Windows Java 17 + Docker Desktop의 `pgvector/pgvector:pg16` Testcontainers에서 focused 20건 통과, `BUILD SUCCESSFUL` 59초
- 같은 Testcontainers 환경에서 matching 전체 192건 통과, `BUILD SUCCESSFUL` 1분 47초
- backend 전체 231건 통과, `BUILD SUCCESSFUL` 1분 56초
- backend build `BUILD SUCCESSFUL` 4초
- 자동 테스트는 실제 dev DB를 사용하지 않고 모두 PostgreSQL Testcontainers에서 실행
- dev DB에서 회원 `2`, `27`, 축제 `144`, 유효한 `ACTIVE` 체크인과 희망 인원 2명 조건으로 일반/시크릿 브라우저를 사용해 수동 재검증 완료
- 첫 회원 `WAITING`, 두 번째 회원 신청 후 양쪽 proposal, 양쪽 수락 후 동일한 2인 `MATCHED` 화면과 참여자 `테스트`, `dev카테` 표시 확인
- 확정 화면 캡처를 확인했고 수동 재검증 중 `TransactionRequiredException` 재발 없음
- 이 수정은 WebSocket 상태 동기화와 무관한 backend transaction 경계 버그 수정

## [10-매칭 10차] 확정 group 조회와 frontend 결과 계약

상태: REST API, PostgreSQL 동시성 통합 테스트, matching/backend 전체 회귀 및 build 완료

- `GET /api/matching/groups/me/current`를 추가해 `access_token` HttpOnly cookie의 로그인 회원이 현재 참여 중인 확정 group을 조회하도록 구현
- 요청 path, query, body에서 `memberId`와 `groupId`를 받지 않고 인증 회원 기준으로만 조회
- 현재 group이 없으면 기존 `ApiResponse` 조회 계약대로 `200 OK`, `data:null` 반환
- active group은 group `CONFIRMED`/`IN_PROGRESS`와 group member `JOINED`/`ARRIVAL_TIME_SELECTED`/`ARRIVED`의 교집합으로 판정
- `COMPLETED`/`CANCELLED` group과 `CANCELLED`/`NO_SHOW`/`LEFT` 참여자는 current 결과에서 제외
- 다중 active group, 저장된 `confirmed_member_count`와 실제 active 참여자 수 불일치, 조회 회원 누락을 `MATCHING_CONFLICT` 데이터 정합성 오류로 처리
- `MatchGroupQueryService`를 기존 pool/proposal/restriction 조회 service와 분리
- `MatchGroupRepository`가 회원 참여 기준 active group을 조회하고, `MatchGroupMemberRepository`가 회원 공개 정보를 한 번에 join 조회해 N+1 방지
- 참여자는 `match_group_members.id ASC`로 결정적 정렬하며 본인을 포함
- 응답 공개 범위는 `memberId`, `nickname`, `profileImageUrl`로 제한하고 이메일, OAuth 식별자, 전화번호, 성별, 연령대, 여행 스타일, 자기소개, 위치, penalty/cooldown은 제외
- `confirmedMemberCount`는 실제 조회된 `members.size()`를 반환하며 저장값과 다르면 응답하지 않음
- 신규 Flyway migration, frontend, 체크인, trigger/Scheduler, proposal 응답 transaction, penalty/cooldown 코드는 수정하지 않음
- Controller/service focused 단위 테스트 16건 `BUILD SUCCESSFUL` 17초
- REST API와 proposal 응답 PostgreSQL focused 통합 테스트 49건 `BUILD SUCCESSFUL` 51초
- PostgreSQL 통합 테스트에서 목표 인원 확정, round 2 최소 인원 확정, 두 참여자의 동일 group/member 조회, 비참여자 `data:null`, 종료 group 제외, 중복 응답 단일 group, 마지막 동시 수락, ACCEPT/timeout race의 확정 결과만 노출을 검증
- matching 전체 회귀 190건 `BUILD SUCCESSFUL` 1분 33초
- backend 전체 최초 실행은 개인 `.env`의 dev SSH tunnel `127.0.0.1:15432` 부재로 기존 `contextLoads()` 1건만 실패하고 나머지 228건 통과
- 격리된 일회성 `pgvector/pgvector:pg16` PostgreSQL을 사용한 최종 backend 전체 229건 `BUILD SUCCESSFUL` 2분 8초
- backend `build` 최종 `BUILD SUCCESSFUL` 9초

## [10-매칭 9차] pool 신청 AFTER_COMMIT 매칭 실행 trigger

상태: application event 운영 코드와 PostgreSQL Testcontainers 통합·backend 전체 회귀 및 build 완료

- `MatchPoolEntryService`가 `WAITING` pool을 저장한 뒤 `MatchingPoolEnteredEvent(poolId, memberId, festivalId)`를 publish하도록 연결
- `MatchingPoolEnteredEventHandler`가 동기 `@TransactionalEventListener(AFTER_COMMIT)`로 pool-entry orchestration을 실행하도록 구성
- 신청 transaction rollback 시 listener가 실행되지 않고, listener 예외는 내부에서 식별자와 함께 기록해 이미 commit된 신청 결과를 실패로 되돌리지 않도록 처리
- requester pool을 우선 포함하고 같은 축제·같은 희망 인원인 유효 `WAITING` pool만 `FOR UPDATE SKIP LOCKED`로 선점하는 trigger 전용 claim 추가
- trigger는 requester가 포함된 조합만 기존 scoring, batch reader, group composer, proposal 생성 pipeline으로 처리
- trigger attempt는 `created_by=POOL_ENTRY`, 기존 Scheduler fallback attempt는 `created_by=SCHEDULER`로 구분
- 후보 부족, proposal 생성 실패, 미사용 후보는 기존 token 기반 release로 검색 시간이 유효하면 `WAITING`, 만료됐으면 `EXPIRED` 처리
- 기존 `MatchingScheduler`의 전체 batch fallback, 만료 `WAITING` 정리, stale `LOCKED` 복구, 미사용 lock release와 기존 5초 주기를 유지
- 기존 `MatchProposalTimeoutScheduler`의 proposal/attempt timeout 책임 유지
- 동일 event 재실행, 두 pool-entry trigger 동시 실행, trigger와 Scheduler 동시 실행에서 상태 조건과 `SKIP LOCKED`로 attempt 중복 생성을 방지하는 PostgreSQL 통합 테스트 추가
- 기존 matching repository의 `festival_checkins` 유효성 조회와 SQL fixture만 사용하고 `domain/checkin/**`, 체크인 API, GPS 정책은 수정하지 않음
- 신규 migration, queue/request table, DB Trigger, `@Async`, Redis, Kafka, frontend, WebSocket은 추가하지 않음
- 신규 focused trigger 테스트 15건 `BUILD SUCCESSFUL` 25초
- 최종 코드 기준 `PROFILE_ENCRYPTION_KEY`를 테스트 프로세스에 주입한 matching 전체 회귀 179건 `BUILD SUCCESSFUL` 1분 20초
- 첫 backend 전체 실행은 local PostgreSQL 부재로 기존 `contextLoads()` 1건만 실패했고 나머지 215건은 통과
- 임시 `pgvector/pgvector:pg16` local PostgreSQL을 사용한 최종 backend 전체 218건 `BUILD SUCCESSFUL` 1분 49초
- backend `build` 최종 `BUILD SUCCESSFUL` 9초

## [10-매칭 8차] matching 최소 REST API

상태: 매칭 REST API 구현과 PostgreSQL Testcontainers 통합·matching 전체 회귀 테스트 완료

- `access_token` HttpOnly cookie의 JWT 회원 ID를 사용하는 matching REST API 5개 추가
- 매칭 신청, 내 최신 pool, 내 active proposal, proposal action, cooldown/penalty 조회 구현
- 외부 action은 `ACCEPT`, `REJECT`, `CANCEL_CURRENT_MEMBERS`만 허용하고 proposal 유형별 기존 service 입력으로 변환
- 다른 회원 proposal은 동일한 `MATCHING_RESOURCE_NOT_FOUND`로 처리해 존재 여부를 숨김
- 회원 row lock과 기존 partial unique index를 함께 사용해 동일 회원 동시 pool 신청을 방어
- pool entry는 유효한 본인 `ACTIVE` 체크인, `ACTIVE` 축제, 회원 상태, cooldown, active pool/group을 검증
- pool `tags`는 scoring 계약이 확정되지 않아 요청에서 빈 배열만 허용하고 DB에도 빈 배열 저장
- 기존 `MatchProposalResponseService`의 transaction, 잠금 순서, 멱등성, rollback 경계는 변경하지 않음
- Swagger/OpenAPI, frontend, WebSocket, `POOL_ENTRY`, DB schema/Flyway 변경은 제외
- Windows Git Bash + Docker Desktop에서 REST API/pool PostgreSQL 통합 테스트를 실행해 39초에 `BUILD SUCCESSFUL`
- 같은 환경에서 Flyway V1~V12 적용 PostgreSQL Testcontainers 기반 matching 전체 회귀를 실행해 1분 24초에 `BUILD SUCCESSFUL`
- WSL 작업 환경은 Docker integration 비활성으로 Testcontainers를 시작하지 못했지만 Windows 환경에서 최종 검증 완료
- Postman/curl 직접 검증을 위한 실행 환경, 데이터 준비, API 요청, 예상 결과와 정리 절차를 `docs/13_MATCHING_ENGINE_IMPLEMENTATION.md`에 기록

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

### [10-매칭 11차] frontend matching REST 연동과 서버 상태 복원

상태: 코드 작성 및 frontend focused 검증 완료, 기존 nickname 회귀 테스트 실패 확인

- 사용자가 수정한 `MatchingConditionPage`의 모바일 레이아웃과 상태별 카드 디자인을 유지하고 demo 상태 전환을 제거
- pool 신청/current pool, active proposal 조회·응답, restriction, current group REST API를 `matchingApi`로 연결
- current pool, active proposal, current group의 `200 OK`, `data:null`을 정상 조회 결과로 처리
- `ApiClientError`에 HTTP status와 backend `error.code`, `message`, `fields`를 보존하고 기존 `credentials: 'include'`, 401 redirect를 유지
- current group, active proposal, pool, cooldown 순서로 새로고침 후 화면 상태를 복원
- round 1은 `ACCEPT`/`REJECT`, round 2는 `ACCEPT`/`CANCEL_CURRENT_MEMBERS`만 전송
- active 상태 2초, cooldown 5초 polling과 오류 backoff, visibility 중단/복귀 즉시 조회, 중복 조회 방지, abort cleanup 적용
- `festivalId`는 `location.state.festivalId`, 개발 환경의 `VITE_DEV_FESTIVAL_ID` 순서로만 결정하며 값이 없으면 신청 비활성화
- `/matching/results` 링크와 임시 매칭 기록 숫자를 제거하고 `/matching`, `준비 중`으로 변경
- `matchSession.ts`, demo 상태 chip/timer, candidate/matchRate mock type 제거
- 신규 의존성 및 `package-lock.json` semantic 변경 없음
- `npx tsc --noEmit` 성공
- 이번 작업 focused test 25건 성공
- 전체 `npm test`는 37건 중 36건 성공, 기존 `src/utils/nickname.test.ts`의 길이 fixture 1건 실패
- Windows 의존성 환경의 `npm run build` 성공, 1,616 modules transformed
- WSL 명령은 기존 `node_modules`에 `@rollup/rollup-linux-x64-gnu`가 없어 Vitest/Vite 시작 전에 실패
- backend, check-in, meeting point, WebSocket 코드는 수정하지 않음
# [10-매칭 25차] MatchRoom 전원 도착 완료와 재매칭 점유 해제

- `V16__complete_match_rooms.sql`에 member `COMPLETED`, event `MATCH_COMPLETED`, 완료 event unique index와 active member partial unique index를 반영했다.
- 과거 `COMPLETED` group의 누락 완료 시각·유효 member·완료 event를 backfill해 기존 active 점유도 해제한다.
- group 선잠금과 전체 member ID 순 잠금 뒤 마지막 도착에서 member/group/event를 원자 완료한다.
- 마지막 도착과 완료 후 반복 요청은 완료 snapshot을 반환하고 current-group은 `null`이다. 새 active group은 과거 완료 group보다 우선한다.
- 완료 WebSocket은 AFTER_COMMIT으로 전송하며 Frontend는 기존 일회성 notice로 `/matching`에서 완료 안내를 한 번 표시한다.
- Backend focused unit, PostgreSQL 도착 통합, matching 전체, 전체 `clean build`를 성공했다. 전체 build 종료 중 이미 종료된 Testcontainers DB를 scheduler가 조회한 connection-refused 로그가 있었지만 Gradle 결과는 성공이었다.
- Frontend focused Vitest 77건, 전체 Vitest 121건, `tsc --noEmit`, production/PWA build를 성공했다.
- 작업 파일 `git diff --check`는 기존 working tree의 CRLF가 trailing whitespace로 해석되어 실패했다. 신규 `V16` 자체에는 공백 오류가 없으며 CRLF 사용자 변경은 임의 정규화하지 않았다.
- 별도 완료 버튼, 완료 이력 API, 평가·후기, GPS 판정, Redis, 배포·CI/CD 변경은 제외했다.

## [10-매칭 25차 보완] 1시간 매칭 유효시간과 완료 전용 화면

상태: 코드 구현, 자동 검증 및 완료 기능 브라우저·DB 수동 검증 완료

- 기획서 v5.0의 체크인/매칭 유효시간 2시간을 MVP 기준 각각 1시간으로 조정한다.
- 매칭 유효 종료 시각은 `confirmed_at + 1시간`이며 30분 NO_SHOW 마감은 유지한다.
- 정상 완료가 일찍 발생해도 유효 종료 시각 전에는 신규 pool 신청을 Backend에서 거절한다.
- 정상 완료 제한은 귀책 cooldown이 아니므로 `match_cooldowns` row보다 완료 group 이력에서 파생하는 방향을 사용한다.
- 별도 최대 3회 제한은 이번 범위에 추가하지 않는다.
- 현재 수동 검증에서 정상 완료 뒤 상단 안내는 맞지만 본문이 `매칭이 취소됐어요`와 `다시 신청하기`를 표시하는 Frontend 문제를 확인했다.
- `/matching`에 완료 전용 card, 유효 종료 시각과 countdown, 제한 중 비활성 action을 추가한다.
- 제한 종료 뒤 체크인이 만료됐으면 재체크인 동선으로 연결한다.
- 실제 후기 작성 UI, 최근 완료 상세 API, GPS 도착 판정은 후속 범위로 유지한다.
- `CheckinValidityPolicy`와 matching SQL의 유효 만료 상한을 1시간으로 통일했다.
- `V17__enforce_one_hour_checkin_validity.sql`로 기존 `ACTIVE` check-in의 1시간 초과
  만료시각을 보정하고 이미 지난 row를 `EXPIRED` 처리한다.
- restriction에 귀책 cooldown과 별도인 `completionLock`을 추가하고, pool 신청은
  active pool/group 우선 검증 뒤 완료 제한 중 `MATCHING_COMPLETION_LOCKED`로 거절한다.
- Frontend에 `COMPLETED` 상태와 완료 전용 card를 추가해 최신 pool `MATCHED`가
  남아 있어도 취소 문구를 표시하지 않는다. 제한 종료 뒤 retry form을 거쳐 기존
  체크인 오류의 `체크인하기` 동선으로 연결한다.
- Backend focused unit/controller, PostgreSQL Testcontainers 완료 제한 통합 10건,
  matching 전체와 전체 `clean build` 336건을 성공했다. 종료 시 이미 정지된 일부
  Testcontainers DB를 scheduler/Hikari가 조회한 connection-refused 로그가 있었지만
  Gradle 결과는 성공이었다.
- Frontend focused Vitest 63건, 전체 Vitest 128건, `npx tsc --noEmit`,
  production/PWA build를 성공했다.
- 두 브라우저에서 완료 전용 card, 유효 종료 시각/countdown, 제한 중 비활성
  action을 확인했고 완료 DB 정합성, event 단일성, penalty/cooldown 미생성,
  active 점유 해제와 completion lock을 수동 검증했다.
- 정상 완료를 취소 card로 표시하던 `ISSUE-MR-008`은 수동 재검증 후 `CLOSED`로
  판정했다.
- 새로고침 직후 신청 form이 잠깐 노출된 뒤 완료 card로 바뀌는 화면 전환은
  completion 기능과 분리해 Frontend UX 후속 이슈로 이관했다.

## [10-Frontend UX 보완 예정] 비동기 상태 복원과 화면 전환 안정화

상태: 범위 문서화 완료, 별도 브랜치 구현 전

- completion 기능 수동 검증 중 새로고침 직후 자동 매칭 신청 form이 먼저
  노출되고 restriction 응답 뒤 완료 card로 바뀌는 중간 화면을 확인했다.
- Backend/DB 정합성과 별개인 Frontend 초기 hydration 문제로 분리한다.
- 최초 snapshot 전 `LOADING`과 조회 완료 후 실제 빈 상태인 `IDLE`을 구분한다.
- 최초 진입은 skeleton, 재조회는 기존 정상 화면 유지 원칙을 적용한다.
- pool/proposal/group/restriction을 원자적인 화면 snapshot으로 판정한다.
- `/matching`만 임시 수정하지 않고 `/match-room`, 체크인, 인증/프로필,
  축제 화면의 새로고침·API 지연·일부 실패·WebSocket/polling 전환을 함께 점검한다.
- Router notice 반복, layout shift와 짧은 spinner 깜빡임도 UX 검증 범위에 포함한다.
- 자동 매칭 진입은 `1. 유효한 축제 체크인 확인 -> 2. 매칭 조건 설정·신청` 순서여야 하지만,
  현재 화면에서 조건 단계가 먼저 보인 뒤 체크인 필요 card로 바뀌어 `2 -> 1`처럼 역순으로
  인지되는 현상을 확인했다. 최초 snapshot에서 체크인 유효성을 먼저 판정하고 이후 단계만
  노출하도록 단계 표시와 hydration 우선순위를 함께 수정한다.
- terminal 화면의 `다시 신청하기`에서도 조건 form을 먼저 노출하지 않고 체크인 유효성을
  선확인해, 만료 또는 누락이면 바로 `체크인하기` 동선으로 연결한다.
- 매칭 탐색 중 사용자가 약 1분 만료를 기다리지 않고 나갈 수 있는 `매칭 취소` action과 확인
  dialog가 필요하다. 구현 전 `WAITING`/`LOCKED`/proposal 생성 경합의 종료 transaction,
  cooldown·penalty 적용 여부와 상대방 비귀책 처리를 정책으로 확정한다.
- 전반적인 화면 전환 지연은 인터넷 문제로 단정하지 않고 Chrome Network의 request waiting,
  중복·직렬 REST 호출, pool/proposal/group/restriction snapshot 조립, WebSocket/polling 재연결과
  route rendering 시간을 분리 측정한다. API가 빠른데 화면이 늦으면 Frontend 상태 전환 문제로,
  모든 API waiting이 길면 dev 서버·DB·네트워크 지연 후보로 기록한다.
- 권장 별도 브랜치명은 `feature/wbs-10-frontend-async-ux-stabilization`이다.
- 이 단계에서는 Backend 정책, DB schema, completion transaction을 변경하지 않는다.

## [10-B 다음 작업 순서] 체크인 이후 매칭 필수 요구사항 완결

상태: 현황 조사 완료, 신규 구현 전

담당 범위는 다른 담당자가 구현하는 체크인 이후의 자동 매칭, MatchRoom과 후속
기능입니다. 화면 전체가 아직 완성되지 않았으므로 ISSUE-MR-009를 포함한 Frontend
전체 UX 안정화보다 기획서 v5.0 `8.3 소그룹 자동 매칭`의 필수 요구사항을 먼저
완결합니다.

### 1. 이미 구현되어 다시 개발하지 않는 항목

- Race Condition 방어는 PostgreSQL `SELECT ... FOR UPDATE SKIP LOCKED`, pool 상태,
  `lock_token`, 짧은 claim transaction과 proposal 생성 전 최종 재검증으로 구현되어
  있습니다.
- 동일 회원 active pool/group, 동일 proposal 응답, penalty/cooldown/event 중복은
  DB 제약과 멱등 처리로 방어합니다.
- pool-entry trigger와 Scheduler의 동시 선점, 응답과 timeout 경합, 재실행과 rollback
  관련 PostgreSQL 통합 테스트가 존재합니다.
- 다음 작업에서 동시성 로직과 테스트를 처음부터 다시 작성하지 않습니다. 신규 정책이
  기존 transaction 경계를 변경하는 경우에만 관련 focused test를 먼저 실행하고,
  빠진 race 경계만 추가합니다.

### 2. 차단 회원 양방향 제외 현황

- `user_blocks`에서 `A가 B를 차단`한 관계가 하나라도 있으면 A와 B를 같은 후보
  그룹에 포함하지 않습니다.
- `A가 B를 차단`한 경우 A가 매칭을 신청할 때뿐 아니라 B가 먼저 신청한 경우에도
  서로를 제외하는 것이 양방향 제외입니다. 이는 차단 사실과 차단한 사람을 상대에게
  노출하지 않으면서 이후 만남을 막기 위한 안전 규칙입니다.
- requester 후보 조회, Scheduler batch 조합과 proposal 생성 직전 모든 member pair
  최종 검증에 반영되어 있습니다.
- 정방향·역방향 차단 repository/service 통합 테스트도 있으므로 신규 구현 항목으로
  잡지 않습니다.
- 최종 차단 검증 직후 다른 transaction에서 새 차단이 생성되는 극단적인 race는 현재
  알려진 한계입니다. isolation level, advisory lock 또는 회원 단위 직렬화의 처리량과
  deadlock 위험을 비교해야 하므로 차단 API·정책 작업 시 별도 설계합니다.

### 3. 실제 다음 신규 작업: 거절 상대 재매칭 제외 정책

기획서 v5.0 `MATCH-08`의 `재매칭 최대 5회`는 적용하지 않기로 결정했습니다.
재매칭 횟수 자체를 제한하지 않으므로 횟수 집계, 제한 API, DB counter와 동시 요청
경계는 구현하지 않습니다. 기획서와 현재 서비스 정책이 다른 항목으로 추적하고 최종
기획 문서 갱신 시 반영합니다.

남은 신규 작업은 `거절 상대 자동 제외`입니다.

확정 정책:

- 한 회원이 매칭 제안을 명시적으로 거절하면 해당 proposal에서 만난 회원끼리는
  같은 체크인이 유효한 동안 서로 다시 추천하지 않습니다.
- 제외는 양방향으로 적용하지만 누가 거절했는지 또는 제외 관계가 생겼는지는 상대에게
  노출하지 않습니다.
- 새로운 유효 체크인을 생성하면 이전 체크인에서 생긴 거절 상대 제외는 이어받지
  않습니다.
- 인원 미달, 시스템 오류처럼 사용자의 명시적 거절이 아닌 실패는 상대 제외를 만들지
  않습니다.
- 미응답 `TIMEOUT`은 proposal 종료 처리상 자동 거절에 준하지만 명시적
  `REJECTED`가 아닙니다. 기존 penalty/cooldown만 적용하고 상대 exclusion은
  생성하지 않습니다.

1. attempt/proposal 이력과 check-in 범위를 기준으로 후보 pair 제외 조회를 설계합니다.
2. requester 경로와 Scheduler batch 조합에 동일한 제외 규칙을 적용합니다.
3. proposal 생성 직전 현재 check-in과 제외 pair를 최종 재검증합니다.
4. 기존 matching transaction을 변경하는 범위에 한해 focused 동시성·멱등성
   통합 테스트를 보강합니다.

권장 브랜치명:

```text
feature/wbs-10-b-rematch-opponent-exclusion
```

### 4. 이후 순서

1. AI 임베딩 생성·동의·fallback과 scoring 결합 (**진행 중**)
2. 매칭 실패 시 솔로 코스와 재매칭 타이밍 연결(`MATCH-09`)
3. 신고·안전·후기와 관리자 연계
4. 주요 화면과 실제 API 연결 완료 후 ISSUE-MR-009를 포함한 Frontend 전체 UX 안정화

2026-08-25에 1번과 2번의 순서를 교체했습니다. `MATCH-09` 솔로 코스는 관광공사 OpenAPI
연동이 선행되어야 하는데 해당 연동이 아직 착수되지 않아 대기 상태이므로, 선행 의존성이
없는 AI 임베딩을 먼저 진행합니다. 상세 계획과 진행 상황은 문서 상단
`[10-B AI 임베딩] 취향 임베딩 도입`을 참고합니다.

AI 임베딩의 외부 API 전송 동의, 개인정보 고지, 실패 fallback과 삭제 정책 요구사항은
그대로 유효하며, 매칭 상태 정확성·중복 방지·재매칭 정책 자체를 변경하지 않는 범위에서만
진행합니다.

## [10-B 안전 후속] 차단 목록 조회·해제 Backend 1차

상태: 기본 API·정책·자동 테스트 구현 완료

- `GET /api/members/me/blocks`, `DELETE /api/members/me/blocks/{blockedMemberId}`를 추가했다.
- JWT cookie 회원을 blocker로 고정하고 정방향 목록만 최소 프로필과 함께 반환한다.
- 목록은 `blocked_at DESC, user_blocks.id DESC`, 빈 목록은 `200`과 빈 배열이다.
- 해제는 두 member ID를 조건으로 물리 삭제하며 존재 여부와 무관하게 body 없는 `204`이다.
- 타인·역방향 관계, 내부 block ID/reason/삭제 건수는 노출하거나 삭제하지 않는다.
- penalty/cooldown/event/회원 점수/group 상태는 변경하지 않으며 migration은 변경하지 않았다.
- Controller/DTO/Service/Repository 경계와 실제 PostgreSQL Testcontainers focused 테스트를 추가했다.
- proposal 생성 race 보강, matching 전체 회귀와 실제 후보 복귀 통합 검증은 2단계로 남긴다.
