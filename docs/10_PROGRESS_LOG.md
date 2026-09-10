# 진행 상태 기록

## [10-B 동의] 가입 필수 동의 2종의 원문 노출 (docs/19 4.7.1)

상태: Frontend 구현·자동 테스트 완료. 브라우저 수동 검증 대기

배경은 한 줄이다. **동의 체크박스에 볼 것이 없었다.**

`AiConsentSection`의 AI 관련 동의 2종은 체크박스마다 `자세히`가 있어 목적·수집 항목·보관
기간·거부 시 불이익이 펼쳐진다. 그런데 정작 법정 필수 동의인 이용약관과 개인정보 수집·이용은
`SignupPage`에 맨몸 체크박스 두 개로 있었고 라벨 한 줄이 전부였다. 저장소 어디에도 약관
원문이 없었다(프론트 전체에서 관련 텍스트는 체크박스 라벨 2줄과 `LoginPage`의 안내 1줄).

### 이번에 만든 것

- `components/consent/legalDocuments.ts` — 이용약관 15개 조, 개인정보처리방침 14개 절 원문.
  `termsDocument()` / `privacyDocument()`가 문의처 이메일을 채워 문서를 만든다.
- `components/consent/LegalDocumentModal.tsx` — 전문 모달. 헤더·푸터는 고정하고 본문만
  스크롤한다.
- `components/consent/ConsentCheckbox.tsx` — `AiConsentSection` 안에 있던 내부 컴포넌트를
  파일로 분리했다. `documentLabel`·`onOpenDocument`를 넘기면 `전문 보기` 버튼이 붙는다.
  마크업은 옮기기 전과 같아서 AI 동의 화면은 동작이 바뀌지 않는다.
- `consentNotice.ts`에 `TERMS_NOTICE`·`PRIVACY_NOTICE` 요약 추가.

### 확정한 것

- **페이지 이동이 아니라 모달이다.** 약관을 여는 곳은 가입 마지막 단계이고 그 화면에는
  닉네임·성별·연령대·여행 스타일·취향 글이 이미 입력돼 있다. `/terms` 같은 페이지로 보내면
  그 입력이 전부 사라진다. 새 탭도 PWA에서 뒤로가기 흐름이 어긋난다.
- **동의 저장 흐름은 건드리지 않았다.** `agreeAll(SIGNUP_CONSENT_TYPES)` 호출과 체크 상태
  판정, 저장 순서 모두 그대로다. 보기 버튼만 늘었다.
- **원문은 구현에서 뽑았다.** 없는 기능을 약속하지 않았고(자유 대화 없음을 약관에 명시),
  하는 일을 빠뜨리지도 않았다. 체크인이 좌표를 전송받아 거리만 계산하고 좌표를 저장하지
  않는다는 사실(`festival_checkins`에 `distance_meters`만 있다)을 처리방침에 그대로 적었다.
  탈퇴 시 삭제 항목과 보관 항목의 구분도 `Member.withdraw()`가 비우는 컬럼 목록과 맞췄다.
- **문서 버전은 서버 동의 기록과 묶는다.** `LEGAL_DOCUMENT_VERSION = '1.0'`이
  `MemberConsentType.TERMS/PRIVACY.currentVersion()`과 같아야 한다. 한쪽만 올리면 "무엇에
  동의했는지"를 나중에 특정할 수 없다. 테스트가 이 값을 고정한다.
- **문의처가 비면 소리 없이 지우지 않는다.** `VITE_SUPPORT_CONTACT_EMAIL`이 없으면 문구
  자리에 설정되지 않았다는 사실을 그대로 남긴다. 제재 안내에서 환경변수 누락이 문의 문구를
  조용히 없앴던 경로와 같은 실수를 막기 위한 것이다.

### 하지 않기로 한 것

- **동의 조회 API 확장.** `GET /api/members/me/consents`를 4종으로 넓히는 안은 보류했다.
  프론트 소비처가 `hasAllAiConsents` 하나뿐이고 그것은 AI 2종만 보므로 **읽는 곳이 없다.**
  값이 생기는 시점은 마이페이지 동의 내역 화면이 생길 때다. 근거는 `docs/19` 4.7.2.
- 백엔드 변경, 신규 migration, 재동의 강제. 재동의는 조회 조건만 바꿔도 기존 동의자가
  일괄로 취향을 잃는다.

### 공개 운영 전 확정 필요 (운영자 결정)

`legalDocuments.ts` 상단 `TODO(운영)`에 4건을 남겼다 — 개인정보 보호책임자 성명·직책,
신고·제재 기록 보유 기간, 오브젝트 스토리지 수탁 사업자명, 만 14세 제한 유지 여부.
**이 원문은 법률 검토를 받지 않은 초안이다.**

### 검증

- frontend: `npx vitest run` 69 files / 648 tests 전체 통과(신규 22건), `npx tsc -b` 통과,
  `npx vite build` 통과.
- backend: 변경 없음. 작업 시작 전 `origin/dev` 기준 baseline은 973건 중 실패 2건
  (`ContentBookmarkCommentIntegrationTest`의 만료 쿠키 공개 조회, `FestivalRepositoryIntegrationTest`의
  `@Sql` OVERRIDE)으로 문서에 기록된 것과 같았다. 둘 다 이 브랜치와 무관하다.
- 수동 검증 대기: ①가입 화면에서 `자세히`·`전문 보기` 동작, ②모달을 열고 닫은 뒤 입력값이
  남아 있는지, ③`VITE_SUPPORT_CONTACT_EMAIL` 미설정 시 안내 문구 노출, ④AI 동의 화면이
  이전과 같게 보이는지(컴포넌트 분리 회귀 확인).

### 작업 환경 메모

이 작업은 별도 git worktree(`C:\dev\meet-or-solo-wt-consent`)에서 진행했다. 같은 저장소에서
다른 세션이 `docs/19` 4.9(관리자 매너온도 조정)를 동시에 진행해 작업 트리를 공유하면 브랜치와
인덱스가 섞이기 때문이다. dev DB와 Flyway 번호는 worktree로도 갈라지지 않으므로 여전히 공유
자원이다.

## [사고 기록] 적용된 migration 수정으로 인한 checksum 충돌 2건

상태: 원인 확정. 1건 해결(V28 → V31 번호 이동), 1건은 `flyway repair` 대기(V31)

같은 뿌리에서 나온 두 사고를 함께 남긴다. 둘 다 **Flyway 체크섬은 주석까지 포함한다**는 점과
**공유 DB의 이력이 저장소보다 앞서 있을 수 있다**는 점을 놓쳐 발생했다.

### 사고 1 — 번호 충돌 (`V28`)

저장소 파일 목록상 마지막이 `V27`이라 새 migration을 `V28`로 만들었다. 그런데 협업자가
저장소에 push하지 않은 채 공유 dev DB에 `V28`~`V30`을 먼저 적용해 둔 상태였다.

```text
Migration checksum mismatch for migration version 28
```

`flyway repair`를 쓰지 않았다 — 협업자의 `V28`을 내 파일로 위장하게 되고 그쪽 스키마 변경이
기록에서 사라진다. **번호를 `V31`로 옮겨 해결했다.**

교훈: **번호는 저장소 파일 목록이 아니라 공유 dev DB의 `flyway_schema_history`를 기준으로
정한다.** push되지 않은 migration은 저장소에 보이지 않는다.

### 사고 2 — 적용된 migration 수정 (`V31`)

`V31`이 DB에 적용된 뒤, 문서 번호 충돌을 정리하려고 저장소 전체에서 `docs/28` → `docs/29`를
일괄 치환했다. **그 치환이 이미 적용된 `V31`의 주석 11줄을 함께 바꿨다.**

```text
Migration checksum mismatch for migration version 31
-> Applied to database : -1125150006
-> Resolved locally    : -769530868
```

Flyway 체크섬은 **주석을 포함**해 계산하므로, 문서 번호 참조만 바뀌어도 부팅이 막힌다.

이 건은 DDL이 완전히 동일함을 확인했다(달라진 11줄 전부 `--` 주석, 주석 제외 DDL 문자열 일치).
따라서 DB 스키마는 이미 현재 파일과 정확히 일치하고, **기록된 체크섬만 갱신하면 된다.**

```sql
UPDATE flyway_schema_history
SET checksum = -769530868
WHERE version = '31' AND script = 'V31__add_member_inquiries.sql';
```

번호를 또 옮기는 방식은 쓰지 않는다. `V31`은 이미 정상 적용되어 테이블이 만들어져 있으므로
새 번호로 옮기면 같은 테이블을 두 번 만들게 된다.

### 재발 방지

`docs/08_AI_WORKING_RULES.md`에 두 규칙을 추가했다.

- **Flyway migration 수정 금지** — 적용된 migration은 주석 한 줄도 고치지 않는다. 저장소 전체
  일괄 치환 대상에서 `backend/src/main/resources/db/migration/`을 제외한다.
- **Migration 번호 결정 규칙** — 공유 dev DB의 `flyway_schema_history`를 기준으로 정한다.

### 남은 수동 작업

- [x] 위 `UPDATE` 실행(또는 `flyway repair`). **2026-09-10 확인 완료** — 공유 dev DB의
      `flyway_schema_history`에서 `version='31'`의 checksum이 `-769530868`(= 저장소 파일 기준
      값)이다. 그 뒤 `V31`은 **동결**이며 어떤 이유로도 수정하지 않는다
- [ ] 공유 dev DB라면 협업자도 같은 오류를 겪는다. `V31` 파일을 push한 뒤 repair를 함께 안내한다

## [10-공통 환경 정리] 관광공사 서비스키 환경변수 이름 통일 (`TOURISM_API_KEY`)

상태: 완료. 로컬 `.env` 반영까지 마침. **dev/prod 서버 `.env`와 GitHub Secrets는 수동 반영 필요**

### 문제

같은 값을 가리키는 환경변수 이름이 세 개였다.

| 위치 | 이름 |
| --- | --- |
| 로컬 루트 `.env` | `TOURISM-API-KEY` |
| `application.yml` | `TOUR_API_KEY` (없으면 `TOURISM-API-KEY`로 fallback) |
| `docker-compose.dev.yml`·`.env.dev.example` | `TOUR_API_KEY` |

`docs/28_INTEGRATION_TEST_PLAN.md` 1.1절 항목 6이 이미 이 불일치를 위험으로 올려두었다.
fallback이 있어 로컬은 조용히 동작하지만, **dev/prod는 어느 이름이 비었는지 추적할 수 없어
축제 sync가 인증 실패로만 나타난다.**

### 확정

**`TOURISM_API_KEY` 하나로 통일했다.** 사용자 지정 값이다. `application.yml`의 이중 fallback
(`${TOUR_API_KEY:${TOURISM-API-KEY:}}`)을 제거해 이름이 하나만 남게 했다 — fallback을 남기면
통일한 의미가 없고, 옛 이름이 남은 환경이 계속 동작해 이름 불일치가 다시 자란다.

`TOUR_API_BASE_URL`·`TOUR_API_MOBILE_OS`·`TOUR_API_MOBILE_APP`·`TOUR_API_CONNECT_TIMEOUT`·
`TOUR_API_READ_TIMEOUT`은 **그대로 뒀다.** 사용자 요청 범위가 서비스키였고, 이들은 Secret이
아니라 기본값이 있는 설정값이라 이름 불일치로 인한 조용한 실패가 발생하지 않는다.

### 변경 파일

- `backend/src/main/resources/application.yml` — `service-key: ${TOURISM_API_KEY:}`
- `backend/.../tourapi/client/KoreaTourApiRestClient.java` — 누락 시 예외 메시지의 변수명
- `infra/docker/docker-compose.dev.yml`, `infra/env/.env.dev.example`
- `README.md`, `docs/04_BACKEND_GUIDE.md`, `docs/06_SECURITY_POLICY.md`,
  `docs/07_DEPLOYMENT.md`, `docs/28_INTEGRATION_TEST_PLAN.md`(항목 6 해소 처리)
- 로컬 루트 `.env` — 키 이름만 교체(값 보존). `.env`는 `.gitignore` 대상이라 커밋되지 않는다

`docs/10_PROGRESS_LOG.md`의 과거 항목(`TOURISM-API-KEY`와 `TOUR_API_KEY` 지원)은 그 시점의
기록이므로 고치지 않고 남겨 둔다.

### 검증

- `compileJava` 통과, 관광공사 client 테스트 통과
- Backend 전체 628건 중 31건 실패 — **전부 `*IntegrationTest`/`MeetOrSoloApplicationTests`이며
  이 개발 머신에 Docker가 없어 Testcontainers가 뜨지 않은 것이다.** 비통합 테스트 실패 0건

### 남은 수동 작업

- [ ] **dev/prod 서버 `.env`의 키 이름을 `TOURISM_API_KEY`로 변경.** fallback을 없앴으므로
      옛 이름만 있으면 서비스키가 빈 값이 되고 축제 sync가 인증 실패한다
- [ ] GitHub Secrets에 `TOUR_API_KEY`가 등록되어 있으면 `TOURISM_API_KEY`로 재등록
- [ ] 팀원 각자의 로컬 `.env` 키 이름 변경 안내

## [10-B] 회원 탈퇴와 관리자 강제 탈퇴 (docs/19 4.4)

상태: 완료. Backend/Frontend 구현·자동 테스트·dev 브라우저 수동 검증 모두 끝냈다(2026-09-10)

브랜치는 `feature/wbs-10-b-member-withdrawal`이며 `dev`(`a491761`)에서 분기했다.

### 사용자 결정으로 확정한 것

작업 전 4건을 제안하고 결정을 받았다. 그중 재가입 정책은 사용자가 값을 직접 정했다.

| 항목 | 확정값 |
| --- | --- |
| 삭제 방식 | soft delete + 익명화. 물리 삭제 없음 |
| 진행 중 매칭 | 탈퇴를 거부하지 않고 정리. penalty 미부과 |
| 재가입 | **탈퇴 후 7일 이내 불가, 이후 같은 소셜 계정으로도 가능** |
| 강제 탈퇴 | 재가입 영구 거부. 단 탈퇴 대행은 예외(`blockRejoin=false`) |
| 범위 | 본인 탈퇴 ①과 관리자 강제 탈퇴 ②를 한 브랜치에. 온도 수동 조정 ③과 후기 회복 ④는 다음 브랜치에 함께 |

### 계획 단계에서 사용자가 잡아낸 오류

내가 "영구차단 회원이 스스로 탈퇴한 뒤 7일 후 재가입하는 시나리오"를 걱정했는데,
사용자가 **"관리자가 영구차단하면 아예 로그인을 못하는데 어떻게 회원 탈퇴를 눌러?"** 라고
지적했다. 확인해 보니 맞았다. `MemberAccessInterceptor`가 모든 `/api/**`를
`requireAccessible` 또는 `requireBrowsable`로 통과시키고 **둘 다 `BANNED`를 던진다.** 로그인도
OAuth callback에서 막힌다. 그 시나리오는 발생할 수 없다.

이 지적으로 설계가 단순해지고 **실제 빈틈이 드러났다. 영구차단 회원은 자기 개인정보를 지울
방법이 없다.** 그래서 관리자 강제 탈퇴가 ①과 같은 브랜치에 있어야 했다. ①만 했다면 영구차단
회원의 삭제 요청을 처리할 수단이 아예 없었다.

```text
영구차단 회원이 안내 화면의 고객센터 이메일로 삭제 요청
  → 관리자가 /admin/members에서 강제 탈퇴 → 익명화 완료, 재가입은 계속 차단
```

컬럼 이름도 이 지적을 반영해 `withdrawn_rejoin_blocked`(정책)와
`withdrawn_by_admin`(사실)로 나눴다. 정책이 바뀌어도 사실은 안 바뀐다.

### 조사 단계에서 발견해 설계를 바꾼 것

**기존 제재 컬럼을 재사용할 수 없었다.** `V19`의 `chk_members_suspension_period`가
`status <> 'SUSPENDED'`이면 `suspended_at`·`suspended_until`을 `NULL`로 강제하고, `V27`의
`chk_members_sanction_reason_presence`도 제재 상태가 아니면 사유를 금지한다. **정지 회원이
탈퇴하면 잔여 정지 기간을 그 컬럼에 둘 수 없다.**

두 제약을 완화하는 대신 **탈퇴 스냅샷 컬럼 5개를 따로 뒀다.** 완화하면 정지 해제와 만료 복구의
버그를 잡아온 불변식 두 개가 동시에 헐거워진다. `V27`이 사용자 노출용 사유와 관리자 내부용
사유를 컬럼 수준에서 나눈 것과 같은 방식이다.

`chk_admin_actions_type`에 `FORCED_WITHDRAWAL`이 없어 감사 로그를 남길 수 없었던 것도 조사에서
발견했다(`V20`이 같은 이유로 `UNSUSPEND`를 추가한 선례가 있다).

### 구현에서 중요한 판단

**닉네임은 `NULL`로 지우고 표시 문구는 조회 SQL이 만든다(`V30`).** 처음에는 `V28`에서
고정 문구(`탈퇴한 회원`)를 컬럼에 덮었다 — 닉네임을 읽는 경로가 `members`를 join하는 SQL
여러 곳이라 컬럼을 덮으면 그 경로를 건드리지 않아도 된다는 이유였다. **사용자 검증에서
재가입한 계정에 그 문구가 그대로 뜨는 것이 확인되어 뒤집었다.** 자세한 경위는 아래
"닉네임 문구를 컬럼에서 뺀 이유" 절에 있다.

**재가입 거부 안내에 4.8 인프라를 그대로 썼다.** OAuth callback은 302라 body가 없다. 새 경로를
만들면 4.8이 고친 "소셜 로그인에 실패했습니다" 오안내가 재발한다. 그래서 재가입 거부도
`MemberSanctionException`(`MEMBER_REJOIN_BLOCKED`)으로 던진다. `GlobalExceptionHandler:43`이
**`ErrorCode`가 아니라 예외 타입에만 걸려 있어** `AuthController`와 `GlobalExceptionHandler`를
한 줄도 고치지 않고 302 경로와 403 경로가 모두 동작한다.

**잔여 정지 기간을 재가입 시 이어받는다.** 없으면 30일 정지가 "탈퇴 후 7일 재가입"으로 23일
세탁된다. 재가입 시 `status_before_sanction`은 `PROFILE_REQUIRED`로 둔다. 프로필이 익명화되어
비어 있으므로 정지 해제 후 돌아갈 곳이 가입 화면인 게 맞고, `completeProfile`이 정지 중 프로필
완성 시 이 값을 `ACTIVE`로 올려주는 기존 동작과 맞물린다.

**정지 회원의 탈퇴 dialog에 잔여 기간 안내를 넣었다**(사용자 요청). 이 안내가 없으면 사용자가
탈퇴를 제재 해제 수단으로 오해하고 누른다. 종료 시각을 함께 보여준다.

**기존 취소 서비스를 재사용하지 않았다.** `MatchCancellationService.cancel`은 도착 마감이
지나면 예외를 던지고 `MatchPoolCancellationService.cancel`은 쿨타임·penalty를 매긴다. 그대로
쓰면 **탈퇴가 그 시점에 실패해 회원이 탈퇴할 수 없다.** 별도
`MemberWithdrawalMatchCleanupService`를 만들고 `Propagation.MANDATORY`로 묶어 탈퇴 transaction
밖에서 실행되지 않게 했다.

**새 매칭 이벤트 타입을 만들지 않았다.** 남은 그룹원에게 기존
`MEMBER_CANCELLED`/`MATCH_CANCELLED`를 보낸다. `MatchRoomPage`가 이미 처리하는 값이라 프론트를
건드릴 필요가 없다.

**동의는 row를 남기고 `revoked_at`만 기록했다.** "동의를 받았다"는 사실이 개인정보 처리 근거의
증빙이다. `revoked_at` 컬럼이 이미 있어 migration이 필요 없었다.

**`provider_user_id`를 익명화하지 않았다.** 지우면 누가 돌아왔는지 알 수 없어 7일 쿨오프 판정
자체가 불가능하다.

**강제 탈퇴를 `AdminMemberActionType`에 넣지 않았다.** `docs/19` 4.4의 "관리자 `BAN`과 회원
`WITHDRAWN`을 같은 상태 전이나 API로 처리하지 않는다" 원칙을 지켰다. `BAN`은 되돌릴 수 있고
강제 탈퇴는 익명화라 되돌릴 수 없다. 관리자 dialog 문구에도 이 차이를 명시했다.

### 테스트가 잡아낸 실제 버그 1건

**그룹에 속한 회원이 탈퇴하면 탈퇴 자체가 실패했다.** `V14`의
`chk_match_group_members_cancel_reason`이 사용자가 직접 고르는 취소 사유
(`MatchCancellationReason`: `SCHEDULE_CHANGED`/`TRANSPORTATION_ISSUE`/`OTHER`)만 허용하는데,
정리 코드가 `cancel_reason='WITHDRAWN'`을 쓰므로 DB가 UPDATE를 거부한다. 만남이 확정된 회원의
탈퇴가 500으로 죽는 경로였다.

`V29`로 `WITHDRAWN`을 허용했다. `OTHER`로 뭉개지 않은 이유는 감사 이력에서 "본인이 사정상
취소"와 "계정이 사라져 이탈"을 구분할 수 없으면 노쇼·패널티 분석이 흐려지기 때문이다.

**`V28`을 고치지 않고 `V29`로 분리한 이유**: 전체 테스트를 한 번 돌린 시점에 `V28`이 **이미
공유 dev DB에 적용됐다**(`flyway_schema_history`에서 2026-09-09 15:35 적용 확인). Testcontainers를
쓰지 않는 통합 테스트 3개가 `.env`의 터널로 공유 dev DB에 붙기 때문이다. `V28`을 수정하면 그
DB에서 checksum 검증이 깨진다.

이 버그는 **계획에 넣었지만 처음에 빠뜨렸던 테스트**를 뒤늦게 채우면서 발견했다. 그룹 정리는
이 작업에서 가장 복잡한 코드였는데 테스트가 0건이었다. 계획한 테스트를 실제로 다 썼는지
대조하는 단계가 필요하다.

### 처음에 빠뜨렸다가 채운 테스트 2건

- 진행 중 그룹 탈퇴 → 그룹 취소·남은 사람 정리·이벤트 발행. `leaveActiveGroups`가 무검증이었다
- 탈퇴 판정이 프로필 갱신보다 먼저 실행되는지. **"순서가 바뀌면 익명화된 프로필이 OAuth
  응답으로 다시 채워진다"고 직접 위험하다고 적어놓고 그것을 지키는 테스트가 없었다.**
  `AuthServiceRejoinTest`가 `InOrder`로 고정한다

### 제재 세탁 방지를 못 박은 테스트

- `MemberWithdrawalTest` — 정지 중 탈퇴가 제재 컬럼을 비우고 잔여 기간을 스냅샷으로 옮기는지,
  재가입이 그 기간을 이어받는지. 영구차단 회원의 본인 탈퇴 거부
- `MemberRejoinPolicyTest` — **6일 23시간 거부 / 정확히 7일 허용 / 7일 1분 허용** 경계.
  재가입 차단된 강제 탈퇴는 3년이 지나도 영구 거부. 재가입 안내에 신고 관련 문구 없음
- `MemberWithdrawalIntegrationTest` — 실제 PostgreSQL. `V28` CHECK 3개가 익명화 누락과 스냅샷
  잔존을 거부하는지, 차단 목록에서 닉네임이 복원되지 않는지, 반복 탈퇴 멱등, 동의 철회,
  강제 탈퇴 감사 로그와 `blockRejoin` metadata, 관리자 메모가 회원 record로 새지 않는지.
  2인 그룹 탈퇴 시 그룹 취소·남은 사람 `LEFT` 처리·이벤트 발행, 도착 마감이 지난 그룹에서도
  탈퇴 성공(기존 취소 서비스를 재사용하지 않은 이유)
- `AuthServiceRejoinTest` — 탈퇴 판정이 프로필 갱신·접근 검사보다 먼저 실행되는지
- Frontend — 정지 회원 dialog의 잔여 기간 안내, 강제 탈퇴 dialog의 "되돌릴 수 없다" 문구,
  `blockRejoin` 기본 체크, 이중 제출 1회 호출

### 건드리지 않은 것

- 관리자 온도 수동 조정과 후기 기반 회복(4.9). 다음 브랜치에서 **함께** 한다
- `member_reviews`. 테이블만 있고 코드가 0줄이다
- 4.5 문의센터, 4.7 동의 후속
- 본인 탈퇴 이력 보존용 별도 테이블. 재가입 시 `withdrawn_at`을 지우므로 본인 탈퇴 이력은
  남지 않는다. 관리자 강제 탈퇴는 `admin_actions`에 남는다
- `MemberSanctionException` rename. 제재가 아닌 재가입 거부에도 쓰여 이름이 부정확하지만,
  8개 파일과 프론트 타입·테스트가 함께 흔들려 javadoc으로 범위를 명시하는 선택을 했다

### 검증

- Backend baseline을 **실측으로 확인**했다. 구현 전 `build/test-results`의 2026-09-08 실행분이
  **871건 / 실패 2 / skip 1**이고 실패 2건은 문서에 적힌 상대방 유래 그대로였다
  (`ContentBookmarkCommentIntegrationTest`, `FestivalRepositoryIntegrationTest`)
- Backend 최종 **916건 / 실패 2 / skip 1.** 실패 2건은 baseline과 동일한 상대방 유래다
- Frontend `vitest` 534건 통과(baseline 517 + 신규 17), `tsc --noEmit` 통과

### 닉네임 문구를 컬럼에서 뺀 이유 (`V30`)

**사용자가 재가입 후 닉네임이 `탈퇴한 회원`으로 뜨는 것을 발견해 설계를 뒤집었다.**

`V28`은 탈퇴 시 `nickname`을 고정 문구로 덮었다. 조사해 보니 구조적 결함이 두 개였다.

1. **표시 문구를 상태 flag로 쓰고 있었다.** `Member.updateSocialProfile`이
   `WITHDRAWN_NICKNAME.equals(nickname)`으로 "익명화됐는지"를 판정했다. 그래서 OAuth가 닉네임을
   주지 않으면(카카오는 닉네임 제공이 선택 동의다) 덮어쓰기 조건에 걸리지 않아 **살아 있는
   계정에 문구가 남았다.** 문구를 바꾸면 기존 행이 판정에서 빠져 영구히 복구되지 않는 문제도
   같이 있었다.
2. **이미 박힌 행은 재로그인 전까지 남았다.** 그동안 댓글·매칭 기록·차단 목록에 노출된다.

컬럼에 `NULL`을 저장하고 표시 문구는 `members`를 join하는 조회 SQL이 `status = 'WITHDRAWN'`일
때 만들도록 바꿨다. 치환 지점은 8개 쿼리(`MatchGroupMemberRepository` 3, `MatchEventRepository`,
`MemberBlockRepository`, `AdminReportRepository` 2, `AdminSafetyAlertRepository`,
`AdminMemberRepository`)다.

**`ContentCommentRepository`는 제외했다.** 탈퇴가 `softDeleteAllOnWithdrawal`로 작성 댓글을
`VISIBLE` → `DELETED`로 내리고 목록은 `VISIBLE`만 조회하므로 탈퇴 회원이 결과에 들어오지 않는다.
정책이 바뀌면 여기에도 넣어야 한다는 근거를 그 파일 javadoc에 남겼다.

**DTO와 프론트엔드는 한 줄도 바꾸지 않았다.** 원래 계획은 "읽는 쪽에서 status로 판정"이었는데
조사 중에 프론트 4곳이 `nickname.slice(0, 1)`로 첫 글자를 뽑는 것을 발견했다
(`ContentCommentItem.tsx:38`, `BlockedMembersPage.tsx:34`, `MatchHistoryPage.tsx:196`,
`MatchingConditionPage.tsx:842`). **`null`을 내려보내면 빈 칸이 아니라 `TypeError`로 렌더링이
죽는다.** 그래서 치환을 서버에서 끝냈다. 진행 로그가 "누락 시 빈 칸"이라고 적어둔 것보다
위험한 조건이었다.

**문구가 SQL 리터럴로 8곳에 흩어지는 것이 이 방식의 비용이다.**
`WithdrawnNicknameLabelConsistencyTest`가 `Member.WITHDRAWN_NICKNAME`과 SQL 8곳, `V30`의
일치를 고정한다. 상수만 바꾸면 화면이 조용히 옛 문구를 계속 보여주기 때문이다. 치환이
`status = 'WITHDRAWN'` 조건과 함께 있는지도 같은 테스트가 센다 — 조건 없이 문구만 넣으면 살아
있는 회원의 닉네임까지 덮인다.

**`V30`이 기존 잘못된 행을 정리한다.** 살아 있는 회원 중 문구가 남은 행을 `NULL`로 되돌리므로
dev DB의 그 계정은 재로그인 없이 정상화된다. `chk_members_withdrawn_anonymized`에 `nickname`을
추가하고, 신규 `chk_members_nickname_not_withdrawn_label`로 어떤 상태에서도 그 문구를 컬럼에
저장할 수 없게 했다.

**관리자 회원 검색은 그대로 뒀다.** `m.nickname ILIKE`로 `탈퇴한 회원`을 검색하면 탈퇴 회원이
다 나오던 동작이 사라진다. 상태 filter(`status=WITHDRAWN`)가 이미 있어 대체 수단이 있다.

**살아 있는 계정의 위장은 이미 `@Pattern`이 막고 있었다.**
`UpdateMemberProfileRequest`의 `^[가-힣A-Za-z0-9]+$`가 공백을 허용하지 않아 `탈퇴한 회원`은
Bean Validation에서 `400`이다. `MemberProfileService.requireSelectableNickname`과 `V30`의
CHECK는 그 패턴이 완화되거나 이 DTO를 거치지 않는 경로가 생겼을 때를 위한 이중 방어다.

### 남은 것

- 브라우저 수동 검증. 특히 정지 회원 탈퇴 dialog의 잔여 기간 안내와 7일 쿨오프 안내 화면
- `V28`, `V29`는 로컬 최고 번호 `V27` 다음이고 원격 브랜치 선점이 없음을 확인했다
  (`origin/dev` `V27`, 나머지 브랜치는 그 이하)
- **`V28`은 이미 공유 dev DB에 적용됐다.** 수정하면 checksum 검증이 깨지므로 추가 변경은
  `V29` 이후 번호로 넣어야 한다. `V29`는 다음 전체 테스트 실행 때 dev DB에 적용된다

### 수동 검증 절차

브라우저 2개(일반 + 시크릿)를 쓴다. 하나는 관리자, 하나는 검증 대상 회원(`role=USER`)이다.

**주의: 탈퇴는 되돌릴 수 없다.** 닉네임·이메일·프로필이 실제로 지워지므로 아끼는 계정으로
검증하지 말고, 아래 "원상 복구"로 되살리더라도 개인정보는 OAuth 재로그인으로만 다시 채워진다.

#### 0. dev DB 접속 (호스트에 psql이 없어 컨테이너로 붙는다)

SSH 터널(`127.0.0.1:15432`)이 열려 있어야 한다. 접속 정보는 저장소 루트 `.env`에서 읽고
명령에 직접 쓰지 않는다. 저장소 루트에서 실행한다.

```bash
set -a; . ./.env; set +a
alias devdb='docker run --rm -i -e PGPASSWORD="$POSTGRES_PASSWORD" pgvector/pgvector:pg16 \
  psql -h host.docker.internal -p 15432 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Docker Desktop for Windows에서는 `--network host`가 호스트 loopback을 공유하지 않으므로
`host.docker.internal`을 써야 한다.

대상 회원 id를 먼저 찾아둔다.

```sql
SELECT id, nickname, role, status FROM members ORDER BY id;
```

#### 1. 본인 탈퇴

마이페이지 최하단 `회원 탈퇴` → dialog에 삭제 대상과 **7일 재가입 제한**이 보이고, 실행하면
`/login`으로 이동한다.

```sql
SELECT status, nickname, email, intro, profile_image_url,
       gender_encrypted IS NULL AS 성별지움, age_range_encrypted IS NULL AS 연령대지움,
       withdrawn_at, withdrawn_from_status, withdrawn_by_admin, withdrawn_rejoin_blocked
  FROM members WHERE id = :회원id;
```

`status='WITHDRAWN'`이고 `nickname`을 포함한 개인정보가 전부 `NULL`이어야 한다. 화면에
보이는 `탈퇴한 회원`은 컬럼 값이 아니라 조회 SQL이 만든 표시 문구다(`V30`).

부수 데이터도 함께 본다.

```sql
SELECT (SELECT count(*) FROM member_travel_styles       WHERE member_id = :회원id) AS 취향,
       (SELECT count(*) FROM member_preference_embeddings WHERE member_id = :회원id) AS 임베딩,
       (SELECT count(*) FROM content_bookmarks          WHERE member_id = :회원id) AS 찜,
       (SELECT count(*) FROM content_comments WHERE member_id = :회원id AND status='VISIBLE') AS 보이는댓글,
       (SELECT count(*) FROM member_consents WHERE member_id = :회원id AND revoked_at IS NULL AND agreed) AS 유효동의,
       (SELECT count(*) FROM refresh_tokens  WHERE member_id = :회원id AND revoked_at IS NULL) AS 유효세션;
```

앞의 다섯 개가 모두 `0`이어야 한다. `member_consents` row 자체는 남아 있어야 한다
(동의를 받았다는 증빙).

#### 2. 프로필 이미지 실물 삭제 (OCI Object Storage)

로컬에도 stub이 없고 `.env`의 OCI endpoint로 실제 붙으므로 실물 삭제를 확인할 수 있다.

1. 탈퇴 **전에** 마이페이지에서 프로필 사진을 업로드한다.
2. object key를 미리 적어둔다. 탈퇴 후에는 컬럼이 `NULL`이 되어 읽을 수 없다.

```sql
SELECT profile_image_object_key FROM members WHERE id = :회원id;
```

3. 탈퇴한다.
4. OCI 콘솔에서 bucket(`OCI_OBJECT_STORAGE_BUCKET`)의 그 key가 사라졌는지 본다. 경로는
   `{OCI_OBJECT_STORAGE_PROFILE_PREFIX}/{회원id}/{uuid}.{확장자}` 형식이다.

삭제는 transaction commit 이후에 일어나고 **실패해도 탈퇴를 되돌리지 않는다.** 키가 남아 있으면
저장소 장애이지 탈퇴 실패가 아니므로, DB가 위 1번을 만족하면 탈퇴 자체는 정상이다.

#### 3. 7일 쿨오프

탈퇴 직후 같은 소셜 계정으로 로그인하면 `/login?oauthError=account_restricted`로 가고
**"탈퇴한 계정이에요"** 와 재가입 가능 시각이 뜬다.
**"소셜 로그인에 실패했습니다. 잠시 후 다시 시도해 주세요."가 뜨면 실패다** — 4.8에서 고친
버그가 되살아난 것이다.

DevTools에서 `sanction_notice` cookie가 생겼다가 안내 조회 후 사라지는지도 함께 본다
(`Path=/api/auth/sanction-notice`, `HttpOnly`).

쿨오프를 지나게 만들려면 탈퇴 시각을 과거로 옮긴다.

```sql
UPDATE members SET withdrawn_at = withdrawn_at - INTERVAL '8 days' WHERE id = :회원id;
```

다시 로그인하면 가입 화면(`PROFILE_REQUIRED`)으로 들어가고 프로필을 새로 입력하게 된다.

```sql
SELECT status, nickname, email, withdrawn_at FROM members WHERE id = :회원id;
```

`status='PROFILE_REQUIRED'`, `withdrawn_at IS NULL`, 닉네임·이메일이 OAuth 값으로 다시
채워져 있어야 한다.

#### 4. 정지 중 탈퇴와 잔여 기간 이어받기 (제재 세탁 방지)

1. 관리자가 `/admin/members`에서 대상을 `이용정지` `THIRTY_DAYS`로 정지시킨다.
2. 대상이 마이페이지에서 `회원 탈퇴`를 누르면 dialog에
   **"남은 이용정지 기간은 탈퇴로 사라지지 않아요"** 와 종료 시각이 보여야 한다.
3. 탈퇴 후 스냅샷을 확인한다.

```sql
SELECT status, withdrawn_from_status, withdrawn_suspended_until, withdrawn_sanction_reason_code,
       suspended_at, suspended_until, sanction_reason_code
  FROM members WHERE id = :회원id;
```

`withdrawn_from_status='SUSPENDED'`이고 `withdrawn_suspended_until`에 원래 종료 시각이 있어야
한다. **제재 컬럼 3개(`suspended_at`, `suspended_until`, `sanction_reason_code`)는 `NULL`이어야
한다** — 남아 있으면 `V19`·`V27` 제약이 저장을 거부했을 것이다.

4. 쿨오프를 지나게 하고 재로그인한다.

```sql
UPDATE members SET withdrawn_at = withdrawn_at - INTERVAL '8 days' WHERE id = :회원id;
```

가입 화면이 아니라 **정지 안내**가 떠야 한다.

```sql
SELECT status, suspended_at, suspended_until, sanction_reason_code, status_before_sanction
  FROM members WHERE id = :회원id;
```

`status='SUSPENDED'`, `suspended_until`이 원래 종료 시각, `status_before_sanction='PROFILE_REQUIRED'`
여야 한다. 여기서 `ACTIVE`나 `PROFILE_REQUIRED`가 나오면 **30일 정지가 세탁된 것이므로 실패다.**

#### 5. 관리자 강제 탈퇴

`/admin/members` → 회원 행 클릭 → `회원 상세` dialog 하단의 `강제 탈퇴`(점선 테두리).

dialog에 **"영구차단은 되돌릴 수 있지만 강제 탈퇴는 되돌릴 수 없습니다"** 가 보이고
`재가입 영구 차단`이 **기본 체크**여야 한다.

```sql
SELECT m.status, m.withdrawn_by_admin, m.withdrawn_rejoin_blocked,
       a.action_type, a.reason, a.reason_code,
       a.metadata->>'beforeStatus' AS 이전상태, a.metadata->>'blockRejoin' AS 재가입차단
  FROM members m
  LEFT JOIN admin_actions a ON a.target_member_id = m.id AND a.action_type = 'FORCED_WITHDRAWAL'
 WHERE m.id = :회원id;
```

`action_type='FORCED_WITHDRAWAL'`, `withdrawn_by_admin=t`, `withdrawn_rejoin_blocked=t`.
메모를 남겼다면 `reason`에만 있고 회원 record에는 없어야 한다.

쿨오프를 지나게 해도 **로그인이 계속 거부**되어야 한다.

```sql
UPDATE members SET withdrawn_at = withdrawn_at - INTERVAL '8 days' WHERE id = :회원id;
```

`재가입 영구 차단`이므로 7일이 지나도 `/login`에서 안내가 뜨고 들어갈 수 없어야 한다.
들어가지면 실패다.

#### 6. 영구차단 회원의 강제 탈퇴 (삭제 요청 처리 경로)

관리자가 `영구차단`한 뒤에도 `강제 탈퇴` 버튼이 보이고 동작해야 한다. **영구차단 회원은
로그인이 막혀 본인 탈퇴를 할 수 없으므로 이것이 개인정보 삭제 요청을 처리하는 유일한
경로다.**

```sql
SELECT status, withdrawn_from_status, email, nickname FROM members WHERE id = :회원id;
```

`withdrawn_from_status='BANNED'`, `email IS NULL`, `nickname IS NULL`.

#### 7. 탈퇴 대행 (재가입 차단 해제)

`재가입 영구 차단` 체크를 **해제**하고 강제 탈퇴한 뒤 쿨오프를 지나게 하면 정상 재가입이
되어야 한다.

```sql
SELECT withdrawn_by_admin, withdrawn_rejoin_blocked FROM members WHERE id = :회원id;
```

`withdrawn_by_admin=t`, `withdrawn_rejoin_blocked=f`. 재로그인하면 가입 화면으로 들어간다.

#### 8. 진행 중 매칭 정리

2인 그룹을 확정시킨 뒤 한 명이 탈퇴한다. 남은 사람의 `MatchRoomPage`에 매칭 취소가 반영되어야
한다.

```sql
SELECT g.id AS 그룹, g.status AS 그룹상태, g.cancel_reason,
       m.member_id, m.status AS 멤버상태, m.cancel_reason AS 멤버사유
  FROM match_groups g JOIN match_group_members m ON m.group_id = g.id
 WHERE g.id = :그룹id ORDER BY m.member_id;
```

그룹은 `CANCELLED` + `INSUFFICIENT_ACTIVE_MEMBERS`, 탈퇴자는 `CANCELLED` + `WITHDRAWN`,
남은 사람은 `LEFT`여야 한다. **탈퇴자의 `cancel_reason`이 `WITHDRAWN`으로 저장되는지가
중요하다** — `V29` 없이는 이 UPDATE가 CHECK 위반으로 실패한다.

이벤트와 penalty도 확인한다.

```sql
SELECT event_type, member_id FROM match_events WHERE group_id = :그룹id ORDER BY id;

SELECT (SELECT count(*) FROM match_penalty_events WHERE member_id = :회원id) AS penalty,
       (SELECT count(*) FROM match_cooldowns      WHERE member_id = :회원id) AS 쿨타임,
       (SELECT penalty_score FROM members         WHERE id = :회원id)        AS 점수;
```

`MEMBER_CANCELLED` → `MATCH_CANCELLED` 순서로 남고, penalty·쿨타임·점수는 모두 `0`이어야
한다. 탈퇴에는 penalty를 부과하지 않는다.

#### 9. 정지 회원도 탈퇴할 수 있는지

정지 중에도 `회원 탈퇴`가 `403`으로 막히지 않아야 한다. 개인정보 권리라
`SuspendedActivityPolicy.ALLOWED`에 등재했다. 정지 안내 dialog가 뜨고 탈퇴가 실패하면 실패다.

#### 원상 복구 (재검증용)

개인정보는 되살아나지 않지만 상태는 되돌릴 수 있다. **스냅샷 6개를 같은 UPDATE에서 모두
지워야 한다** — 하나라도 남으면 `chk_members_withdrawal_snapshot`이 거부한다.

```sql
UPDATE members
   SET status = 'PROFILE_REQUIRED',
       nickname = '검증복구',
       withdrawn_at = NULL,
       withdrawn_from_status = NULL,
       withdrawn_by_admin = NULL,
       withdrawn_rejoin_blocked = NULL,
       withdrawn_suspended_until = NULL,
       withdrawn_sanction_reason_code = NULL,
       suspended_at = NULL,
       suspended_until = NULL,
       sanction_reason_code = NULL,
       status_before_sanction = NULL
 WHERE id = :회원id;
```

이후 소셜 재로그인하면 OAuth가 닉네임·이메일·프로필 이미지를 다시 채운다. 강제 탈퇴
감사 로그를 지우려면 `DELETE FROM admin_actions WHERE target_member_id = :회원id AND
action_type='FORCED_WITHDRAWAL';`을 함께 실행한다.

#### 자동 테스트가 이미 덮는 것 (수동으로 다시 하지 않아도 된다)

- 7일 경계 자체(6일 23시간 / 정확히 7일 / 7일 1분) — `MemberRejoinPolicyTest`가 `Clock` 고정
- `V28`·`V29` CHECK 제약의 거부 동작 — `MemberWithdrawalIntegrationTest`
- 재가입 판정이 프로필 갱신보다 먼저 실행되는 순서 — `AuthServiceRejoinTest`
- 반복 탈퇴 멱등, 강제 탈퇴 `Idempotency-Key` 재요청

수동 검증의 목적은 **화면 흐름과 실제 브라우저·OCI 왕복**이다. 프론트엔드에 jsdom이 없어
클릭과 비동기 갱신은 자동 테스트로 재현되지 않는다.

#### 수동 검증 결과 (2026-09-10, dev)

회원 27번 하나를 되살려 재사용하며 4라운드로 돌렸다. 관리자는 1번, 매칭 상대는 2번을 썼다.
DB 확인은 SSH 터널로 직접 조회했다.

| 검증 | 결과 | 근거 |
| --- | --- | --- |
| 본인 탈퇴 익명화 | 통과 | 개인정보 컬럼 전부 `NULL`, 유효 세션 0 |
| 부수 데이터 정리 | 통과 | 취향·임베딩·찜 0, 댓글 `DELETED`, 동의 row 유지 + 철회 |
| 진행 중 매칭 정리 | 통과 | 그룹 `CANCELLED`/`INSUFFICIENT_ACTIVE_MEMBERS`, 탈퇴자 `cancel_reason='WITHDRAWN'`, 남은 사람 `LEFT` |
| 매칭 취소 실시간 전파 | 통과 | 남은 사람 `MatchRoomPage`가 새로고침 없이 전환 |
| penalty 미부과 | 통과 | 탈퇴자·남은 사람 양쪽 penalty 이벤트·쿨타임 신규 0 |
| 체크인·pool·proposal 정리 | 통과 | `ACTIVE`/`WAITING`/`SENT` 0 |
| 7일 이내 재가입 거부 | 통과 | 사유와 재가입 가능 시각 함께 노출 |
| 쿨오프 경과 재가입 | 통과 | `PROFILE_REQUIRED`로 부활, 닉네임·이메일 OAuth 복구 |
| **잔여 정지 승계** | 통과 | `suspended_until`이 탈퇴 전 값 그대로. 세탁되지 않음 |
| **강제 탈퇴 + 재가입 영구 차단** | 통과 | 쿨오프가 지난 상태에서도 로그인 거부. 안내에 재가입 시각 없음 |
| 탈퇴 대행(`blockRejoin=false`) | 통과 | 쿨오프 경과 후 정상 재가입 |
| **영구차단 회원 강제 탈퇴** | 통과 | `admin_actions.beforeStatus='BANNED'` 기록 |
| 표시 문구 렌더링 | 통과 | 2번의 매칭 기록 5개 그룹에서 `탈퇴한 회원` 표시, 오류 없음 |
| 감사 이력 보존 | 통과 | 신고·관리자 조치 유지 |

**검증에서 찾은 버그 1건.** 관리자 회원 상세가 탈퇴 회원에게도 `경고` 버튼을 노출했다.
backend `validateWarningStatus`는 `ACTIVE`·`PROFILE_REQUIRED`·`SUSPENDED`만 허용하므로 누르면
`ADMIN_MEMBER_STATUS_CONFLICT`가 확정이고, 익명화된 회원에게는 통보 대상 자체가 없다. 화면
조건이 `status !== 'BANNED'`라는 부정 조건이어서 새 상태가 추가될 때마다 새는 형태였다. 같은
파일의 이용정지·영구차단 버튼처럼 허용 목록 방식으로 바꾸고 상태별 테스트 6건을 추가했다.

**결정으로 확정해 `docs/19`에 옮긴 것 2건.** 재가입 시 매너온도·`penalty_score`를 승계한다는
정책(4.4.3)과, 탈퇴 회원은 운영자가 검색으로 특정할 수 없다는 사실(4.4.10)이다. 둘 다 코드를
읽어야만 알 수 있는 상태였다.

`WD-23`(이미 탈퇴한 회원에게 강제 탈퇴)은 화면에서 버튼이 숨어 도달 자체가 불가능해 수동
검증에서 제외했다. `MemberWithdrawalIntegrationTest`가 덮는다.


## [10-B 문의] 1:1 문의 센터 (docs/19 4.5, docs/29)

상태: Backend/Frontend 구현·자동 테스트 완료. 브라우저 수동 검증 대기

**브랜치는 `feature/wbs-10-a-festival-course`다.** `docs/19` 7절 권장 브랜치는
`feature/wbs-10-b-inquiry-center`였지만, 사용자가 "지금 브랜치에서 그냥 구현하면 된다"고
결정해 그대로 진행했다. 브랜치 규칙(`docs/12`)의 예외임을 여기 남긴다.

### 무엇을 메웠나

`docs/19` 4.5는 "미착수, 별도 설계 후 구현"으로 예약돼 있었고 `inquiries`는 `docs/11`에서
보류 table이었다. 그런데 이 기능의 실제 1순위 사용자는 "궁금한 게 있는 사람"이 아니다.
4.8(제재 사유·기간 통보)이 이렇게 끝나 있었다.

> 문의 경로 — 문의센터 화면(4.5)이 보류라 고객센터 이메일을 안내에 표시한다. (…)
> **영구정지 사용자가 이의를 제기할 유일한 경로다.**

즉 이 기능은 제재 이의제기 창구다. 이 전제가 아래 제약으로 이어졌다.

### 설계를 지배한 제약 3개

**1. 영구정지(`BANNED`)는 인앱 문의를 쓸 수 없다.** `MemberAccessInterceptor`가 `/api/**`
전체에서 활동이 아닌 요청에도 `requireBrowsable`을 걸고, 그 판정이 `BANNED`를 차단한다.
애초에 `requireSignedIn`도 `BANNED`를 막아 access token 자체가 발급되지 않는다. 제외 경로는
`/api/auth/**` 하나뿐이다.

제재 안내 cookie를 자격증명으로 재사용하는 방안도 검토했지만 채택하지 않았다.
`SanctionNoticeCookieService`의 cookie는 path가 `/api/auth/sanction-notice`로 좁혀져 있고,
읽는 즉시 `expire()`되는 1회용이며, TTL이 5분이다. 문의 작성에 쓰려면 4.8이 의도적으로 좁혀둔
세 가지를 모두 되돌려야 한다.

**사용자 결정: 이메일 안내 유지.** 인앱 문의는 `ACTIVE`/`PROFILE_REQUIRED`/`SUSPENDED`까지다.
제재 판정을 우회하는 경로를 만들지 않는 쪽을 택했다. **이 기능으로도 영구정지 사용자의
이의제기 UX는 개선되지 않았다** — 남은 과제로 명시해 둔다.

**2. 관리자 답변을 밀어줄 채널이 없다.** 4.8이 이미 조사한 사실이다. STOMP는 `/matching`·
`/match-room`에서만 연결되고, Web Push는 VAPID 키·구독 table·권한 UI가 전무하며
(`vite-plugin-pwa`가 `generateSW` 전략이라 custom service worker 파일 자체가 없다), 메일 발송
인프라도 없다. 그래서 답변 도달은 사용자가 목록을 다시 여는 pull 방식뿐이고, **미확인 답변
badge가 부가 기능이 아니라 기능의 일부**다.

**3. `SuspendedActivityPolicy`에 등재해야 한다.** `SuspendedActivityPolicyCoverageTest`가
상태를 바꾸는 모든 endpoint의 분류를 전수 검사한다. 문의 등록·추가 질문을 `ALLOWED`에 넣었다 —
근거는 기존 신고 허용 근거와 같다. 제재 사유를 다툴 수 없으면 제재가 일방적이 된다.

### 사용자 결정 4건

| 항목 | 확정값 | 대가 |
| --- | --- | --- |
| `BANNED` 이의제기 | 이메일 유지 | 영구정지 UX는 그대로 |
| 본문 저장 | 평문 | 비공개 1:1인데 암호화하지 않음 |
| 긴급 지정 | 관리자만 | 사용자가 급한 건을 표시할 수 없음 |
| 보관 기간 | 종결 후 1년 | 익명화 스케줄러가 새로 필요 |

암호화를 포기한 이유는 세 가지다. 관리자 키워드 검색이 불가능해지고, 이 저장소가 의존하는
`char_length` CHECK 제약을 `BYTEA`에는 걸 수 없고, 키 분실 시 복구가 안 된다. 대신 노출을
통제했다 — 관리자·본인만 조회, 로그에 본문 미기록, 작성 폼에 개인정보 입력 자제 안내.

나머지 6건(2테이블 구조, 스레드 허용, 첨부 제외, 미답변 3건 제한, 안전 카테고리 미도입,
담당자 지정 미도입)은 권고안대로 확정했다. 근거는 `docs/29` 3절에 있다.

### DB (`V31__add_member_inquiries.sql`)

`inquiries`(헤더) + `inquiry_messages`(발화) 2개다. 1테이블로 하면 답변 재작성 시 이전 답변이
`UPDATE`로 사라지고, 사용자 추가 질문이 새 문의로 쪼개져 관리자가 같은 건을 두 번 본다.

- 미확인 답변 여부를 **컬럼으로 저장하지 않는다.** `last_answered_at`과 `member_read_at`
  비교로 계산한다. boolean 컬럼은 `content_comments.like_count`와 같은 카운터 정합성 문제를
  새로 만든다.
- `chk_inquiries_closed_at`은 `content_comments.chk_content_comments_deleted_at`과 같은
  관용구로 상태와 시점 컬럼을 묶어 고정한다. **이 CHECK 때문에 잠금이 필요했다** — 관리자
  답변(`status = ANSWERED`)과 다른 관리자의 종결(`CLOSED` + `closed_at`)이 겹치면
  `ANSWERED`인데 `closed_at`이 남은 위반 조합이 만들어진다. 그래서 상태를 바꾸는 모든 경로가
  `findByIdForUpdate`로 헤더를 먼저 잠근다.
- `anonymized_at`이 보관 기간 익명화의 재처리를 막는 원인 key다. 본문 문구 비교로 판정하면
  사용자가 우연히 같은 문구를 입력한 경우와 구분되지 않는다.
- `admin_actions`는 건드리지 않았다. 그 table의 `action_type` CHECK는 제재·신고 처리 값으로
  고정돼 있고 문의 답변은 회원 제재가 아니다.

### Backend

`domain/inquiry`와 `domain/inquiry/admin`으로 나눴다 — `safety/report`와 `safety/report/admin`
구조를 그대로 따랐다.

- 사용자: `InquiryService`, `MemberInquiryController`
  (`POST`/`GET` 목록/`GET` unread-count/`GET` 상세/`POST` messages)
- 관리자: `AdminInquiryService`, `AdminInquiryController`, `AdminInquiryRepository`(JDBC 목록),
  `AdminInquiryCursorCodec`, `AdminInquiryFilter`
- 보관: `InquiryRetentionService`, `InquiryRetentionScheduler`
  (`@ConditionalOnProperty`로 기본 비활성 — `MemberSuspensionExpiryScheduler`와 같은 형태)
- `ErrorCode` 8건 추가, `SuspendedActivityPolicy.ALLOWED`에 2건 등재

cursor codec은 `AdminSafetyAlertCursorCodec` 선례대로 `app.admin.report.cursor-hmac-secret`을
공유하고 payload prefix(`inquiry:v1:`)로 도메인을 분리했다. **새 환경변수를 요구하지 않는다.**

관리자 목록 정렬 키는 `(created_at DESC, id DESC)`이고 **긴급 우선 정렬을 넣지 않았다.**
`ORDER BY`에 `priority`를 넣으면 cursor payload에도 들어가야 하고, 정렬 키와 cursor 키가
어긋나면 페이지 경계에서 항목이 중복·누락된다. 대신 `priority` filter를 제공한다.

`AdminInquiryTargetStatus`는 `IN_PROGRESS`·`CLOSED` 2개다. `ANSWERED`는 답변 등록으로만
만들어진다 — 답변 없이 상태만 바꾸면 사용자 badge가 답변이 온 것처럼 켜진다.

### Frontend

- 사용자: `/mypage/inquiries`, `/mypage/inquiries/new`, `/mypage/inquiries/:inquiryId`
  (`MyInquiriesPage`, `InquiryNewPage`, `InquiryDetailPage`)
- 관리자: `/admin/inquiries`(`AdminInquiriesPage`), `AdminNav` 5번째 메뉴 + 미처리 badge
- `MyPage`에 "1:1 문의" 진입점 + 미확인 답변 badge
- `api/inquiries.ts`, `api/adminInquiries.ts`. 라벨·배지 색을 한 곳에 모아 화면마다 code를
  문구로 바꾸지 않게 했다.

`AdminNav`의 badge 라벨과 숫자를 `badgeOf()` 한 곳에서 함께 정하도록 리팩터링했다. 기존에는
안전 알림 badge만 있어 `showBadge` 불리언이었는데, 두 번째 badge가 붙으면 count와 라벨이
어긋날 자리가 생긴다.

### 검증

- Backend 신규 23건 통과: `InquiryServiceTest` 10, `AdminInquiryServiceTest` 9,
  `InquiryRetentionServiceTest` 4. `SuspendedActivityPolicyCoverageTest` 7건도 통과 —
  신규 endpoint 분류 누락이 없다.
- Backend 전체 586건 중 43건 실패. **전부 `*IntegrationTest`와 `MeetOrSoloApplicationTests`이며
  이 개발 머신에 Docker가 없어 Testcontainers가 뜨지 않은 것이다**(`docs/19` 인수인계 노트의
  기존 이슈). 비통합 테스트 실패는 0건이다. 내 변경 탓으로 오판하지 말 것.
- Frontend 65 files / 578 tests 통과(신규 `InquiryPages.test.tsx` 22건, `AdminNav.test.ts`
  3건 추가). `npx tsc -b`와 `npm run build` 통과.
- 기존 `AdminNav.test.ts`가 메뉴 4개를 고정하고 있어 5개로 갱신했다 — 의도한 동작 변경이다.

### 남은 것

- **브라우저 수동 검증 미실시.** 실제 화면에서 등록→답변→badge 소거 흐름을 확인해야 한다.
- **`V31` 미적용.** `ddl-auto: validate`이므로 적용 전에는 부팅되지 않는다.
- **마이그레이션 번호 충돌을 겪었다.** 처음 `V28`로 만들었는데 협업자가 저장소에 push하지 않은
  채 공유 dev DB에 `V28`~`V30`을 먼저 적용해 둔 상태였다. Flyway가
  `Migration checksum mismatch for migration version 28`로 부팅을 막았고 `V31`로 옮겨 해결했다.
  `flyway repair`는 쓰지 않았다 — 협업자의 `V28`을 내 파일로 위장하게 되고 그쪽 스키마 변경이
  기록에서 사라진다.
  - **번호는 저장소 파일 목록이 아니라 공유 dev DB의 `flyway_schema_history`를 기준으로
    정해야 한다.** push되지 않은 마이그레이션은 저장소에 보이지 않는다.
  - **협업자가 `V28`~`V30`을 push하기 전까지는 이 브랜치도 부팅되지 않는다.** Flyway는 DB에
    적용됐는데 로컬에 파일이 없는 버전을 `Detected applied migration not resolved locally`로
    막는다. 번호를 옮긴 것과 별개인 문제이며, 협업자 push를 기다리는 것이 정상 경로다.
- **영구정지 회원의 인앱 이의제기 경로 없음**(위 제약 1). 이메일 안내 유지가 사용자 결정이다.
- **4.4 회원 탈퇴와의 연결.** 탈퇴 시 `inquiry_messages.body`에 개인정보가 남을 수 있어 4.4의
  익명화 범위가 이 컬럼을 포함해야 한다. `ContentCommentService.softDeleteAllOnWithdrawal`과
  같은 TODO 위치다.
- `INQUIRY_RETENTION_SCHEDULER_ENABLED`는 기본 `false`다. dev/prod에서 켜야 보관 정책이 실제로
  동작한다.

## [10-B 안전 후속] 회원 제재 사유·기간 통보와 제재 범위 정리 (docs/19 4.8)

상태: Backend/Frontend 구현·자동 테스트 완료. 브라우저 수동 검증 대기

브랜치는 `feature/wbs-10-b-member-sanction-notice`이며 `dev`(`156c7c8`)에서 분기했고,
작업 중 `dev`(`b62b1d0`, V26 포함)를 병합했다.

### 사용자 결정으로 확정한 제재 범위

작업 중 사용자가 제재의 의미 자체를 정했다. **원래 구현은 정지 회원을 전면 차단했는데,
"정지는 조회는 되고 활동만 막힌다"로 바꿨다.**

| 상태 | 로그인 | 조회 | 활동(체크인·동행 매칭·댓글) |
| --- | --- | --- | --- |
| `SUSPENDED` 이용정지 | **가능** | **가능** | 차단 |
| `BANNED` 영구정지 | 차단 | 차단 | 차단 |

이 서비스는 **로그인 필수**다. 홈(`/`)이 로드 시점에 `GET /api/members/me`를 부르므로
비로그인 사용자는 홈을 볼 수 없고 `/login`으로 보내진다. 공개 열람은 공유 링크로 축제
상세에 직접 들어오는 경로에만 존재한다. 정지 회원의 "조회 가능"은 이 로그인 필수 전제
안에서의 범위다.

`MemberAccessPolicy`를 목적이 다른 판정 셋으로 나눴다.

| 메서드 | 쓰이는 곳 | `SUSPENDED` |
| --- | --- | --- |
| `requireSignedIn` | 로그인·token 갱신 | 허용 |
| `requireBrowsable` | 조회 요청 | 허용 |
| `requireAccessible` | 활동 요청·관리자·WebSocket | 차단 |

관리자 기능과 WebSocket은 `requireAccessible`을 유지했다. 관리자 권한을 정지 중에 유지할
이유가 없고, STOMP는 매칭 상태 동기화 전용이라 활동에 준한다.

### 활동 판정은 차단 목록 방식, 누락은 테스트로 막았다

사용자 선택에 따라 `SuspendedActivityPolicy`에 **차단할 활동만 등재**한다. 차단 목록은
새 활동 endpoint를 등재하지 않으면 정지 회원이 그 기능을 조용히 쓸 수 있다는 약점이 있다.
그래서 `SuspendedActivityPolicyCoverageTest`가 `@RestController`를 훑어 **상태를 바꾸는
모든 endpoint가 차단 또는 허용으로 분류되어 있는지 전수 검사**한다. 분류를 빠뜨린 endpoint가
하나라도 있으면 실패한다. scanner가 망가져 빈 목록으로 통과하는 것을 막기 위해 endpoint
개수 하한(20)도 함께 둔다.

허용 쪽에서 특히 중요한 판단이 있다. **신고·차단은 정지 중에도 허용한다.** 정지는 신고
권리를 박탈하는 조치가 아니고, `MatchReportWindowPolicy`가 만남 종료 후 14일을 허용하는데
그 사이 정지되면 신고 경로 자체가 사라진다. 동의 철회도 개인정보 권리라 막지 않는다.
프로필 수정·찜·취향 임베딩은 사용자 결정에 따라 허용했다.

### 문제

관리자가 회원을 정지해도 **사용자가 사유와 기간을 알 수 없었다.** 그런데 조사해 보니 정도가
문서에 적힌 것보다 나빴다. `docs/19` 4.8은 `MemberAccessPolicy`가 `403`으로 막고 메시지가
고정이라는 점만 지적했지만, **정작 사용자가 가장 먼저 부딪히는 로그인 경로는 `403`이 아니었다.**

`AuthController`의 OAuth callback은 `catch (RuntimeException)`으로 모든 예외를 묶어
`/login?oauthError=oauth_failed`로 보낸다. 제재 예외도 여기 삼켜져 로그인 화면이
**"소셜 로그인에 실패했습니다. 잠시 후 다시 시도해 주세요."** 를 띄웠다. 사유·기간이 없는
정도가 아니라 틀린 안내를 하고 있었고, 사용자는 재시도만 반복하게 된다.

프론트엔드도 막혀 있었다. `apiClient`의 `redirectToLoginIfUnauthorized`가 401만 처리해서
`403` 제재는 화면마다 "불러오지 못했어요" 수준으로 흘렀다.

### 문서 전제 수정

`docs/19` 4.8의 **"`403` 응답에 담는 방식이 유일한 저비용 경로"는 틀렸다.** 제재로 막히는
경로가 세 개이고 그중 하나는 body가 없다.

| 경로 | 응답 | body |
| --- | --- | --- |
| OAuth 로그인 (`/api/auth/*/callback`) | 302 redirect | **없음** |
| 세션 중 API 호출 (`MemberAccessInterceptor`, `/api/**`) | 403 | 있음 |
| token 갱신 (`AuthService.refresh`) | 403 | 있음 |

### 확정한 정책 3건

| 항목 | 확정값 |
| --- | --- |
| 로그인 경로 통보 | 단기 notice cookie + 조회 API. query parameter 방식은 채택하지 않음 |
| 사용자 노출용 사유 저장 | `members.sanction_reason_code` 컬럼으로 denormalize (`V27`) |
| `403` body 범위 | `status`, `suspendedUntil`, `reasonCode`, `reasonMessage`, `contactUrl` |

### 구현에서 중요한 판단

**통보 경로를 하나로 합쳤다.** `GlobalExceptionHandler`가 제재 예외를 처리할 때 `403` body와
notice cookie를 함께 내려주고, OAuth callback도 같은 cookie를 실어
`/login?oauthError=account_restricted`로 보낸다. 세 경로 모두 로그인 화면이
`GET /api/auth/sanction-notice` 하나로 안내를 읽는다. **문구와 판정이 한 곳에만 남는다.**

**사유·기간을 query parameter로 넘기지 않았다.** URL·nginx access log·브라우저 history에
제재 정보가 남고, 누구나 URL을 위조해 안내 화면을 띄울 수 있다. notice cookie는 서버가 서명한
5분 만료 JWT(`typ: sanction_notice`)이고 path를 조회 endpoint로 좁혔다.

**notice token은 session이 아니다.** 조회 endpoint는 **현재 제재 중인 회원일 때만** 안내를
반환한다. 유출되어도 "그 회원이 제재 상태인지" 외에는 얻을 수 있는 것이 없다. 안내를 읽은
뒤에는 cookie를 즉시 만료시킨다.

**사유를 `admin_actions` 조회가 아니라 `members` 컬럼에 뒀다.** `MemberAccessPolicy`는 member
도메인이라 admin repository를 참조하면 계층이 역전되고, `403` 경로에 추가 query가 붙는다.
컬럼으로 분리하면 **사용자 노출용(`sanction_reason_code`)과 관리자 내부용
(`admin_actions.reason` 자유 입력 note)이 컬럼 수준에서 갈라진다.**

**제재 사유 문구를 `MemberSanctionReason` enum 한 곳에만 뒀다.** 프론트가 code를 문구로
바꾸면 신고자 보호 심사를 두 곳에서 해야 한다. 서버가 `reasonMessage`까지 내려준다.

**제재 시작 시각(`suspendedAt`)은 노출하지 않는다.** 제재 시점이 신고 시점을 좁히는 단서가
된다. `suspendedUntil`만 담는다.

**제재 안내 화면에서 로그인 버튼을 감췄다.** 제재된 계정은 다시 로그인해도 같은 화면으로
돌아온다. 버튼을 남겨두면 사용자가 무한히 재시도한다.

**정지와 영구정지의 안내 경로를 분리했다.** 정지 회원은 로그인 상태로 조회를 계속하므로
`403`을 받았을 때 로그인 화면으로 보내면 안 된다. 그래서 `apiClient`가 두 갈래로 처리한다.

| 응답 | 프론트엔드 동작 |
| --- | --- |
| `403 MEMBER_BANNED` | `/login?oauthError=account_restricted`로 이동 → 안내 card |
| `403 MEMBER_SUSPENDED` | 화면 이동 없음. `member-sanction` 이벤트 → 앱 안 dialog |

**정지 안내를 화면마다 붙이지 않았다.** `apiClient`가 모든 요청의 단일 통로이므로, 여기서
이벤트를 쏘고 `App` 최상단의 `SanctionNoticeDialog`가 받는다. 체크인·매칭·댓글 어느 화면에서
403이 나도 안내가 뜨고, 활동 화면이 새로 생겨도 따로 붙일 것이 없다. 상대방이 추가한 댓글
컴포넌트를 건드리지 않아도 되는 것도 이 방식의 이점이다.

### 구현 중 발견해 함께 고친 것

**만료 복구 batch가 새 CHECK 제약에 걸릴 상태였다.**
`AdminMemberRepository.restoreExpiredSuspensions`는 bulk `UPDATE`로 상태를 되돌리는데
`sanction_reason_code`를 지우지 않으면 `chk_members_sanction_reason_presence`가 update를
거부해 정지 만료 스케줄러가 죽는다. 같은 `UPDATE`에 `sanction_reason_code=NULL`을 추가했다.

**`Member.ban()`의 검증 순서를 바꿨다.** 사유 검증이 `statusBeforeSanction` 변경 뒤에 있어
검증 실패 시 엔티티가 반쯤 바뀐 채로 남았다. 검증을 앞으로 옮겼다.

### 신고자 보호를 못 박은 테스트

이 항목의 핵심이라 제약을 테스트로 고정했다.

- `MemberSanctionReasonTest` — 사유 문구 전수 검사. "신고"·"누적"·"건수"·"제보"·"고발"·
  "피해자"가 들어가면 실패하고, 숫자가 들어가면 실패한다. 문구를 "신고 3건 누적"으로 바꾸면
  여기서 먼저 걸린다. 관리자 조치 사유 code와 값 목록이 갈라지는 것도 함께 잡는다.
- `AdminMemberIntegrationTest` — 관리자가 `reasonNote`에 `"신고 3건 누적, 신고자 진술 확인
  완료"`를 넣고 정지시킨 뒤, 그 문자열이 `admin_actions.reason`에는 남고 사용자 안내에는
  없음을 확인한다.
- `GlobalExceptionHandlerSanctionTest` — `403` body 직렬화 결과에 `suspendedAt`·"신고"·
  `reporter`·`reportId`가 없음을 확인한다.
- `MemberAccessPolicyTest` — 제재가 해제·만료된 회원은 안내를 받지 못한다.
- 프론트엔드 — 안내 card가 신고 관련 문구를 덧붙이지 않고, 제재 사유를 URL에서 읽지 않는다.

### 건드리지 않은 것

- Web Push·메일 발송 인프라. `docs/19` 4.8 범위 밖이다.
- 인앱 알림 목록. 정지 회원이 조회를 할 수 있게 되었으니 이제 기술적으로는 가능하지만
  이번 범위에 넣지 않았다. 영구정지는 여전히 로그인 자체가 막혀 못 쓴다.
- `MEMBER_SUSPENDED`/`MEMBER_BANNED` error code와 HTTP status. 기존 클라이언트 처리와
  호환을 유지했다.
- 문의센터 화면. `inquiries` 테이블이 보류 상태(`docs/19` 4.5)라 화면은 만들지 않고
  **고객센터 이메일**을 안내에 표시한다. `app.support.contact-email`이 읽고, 값은
  **환경변수 `SUPPORT_CONTACT_EMAIL`로만** 주입한다. 저장소 기본값은 비어 있고, 비어 있으면
  문의 문구를 숨긴다. Secret은 아니지만 공개 저장소 이력에 남으면 스팸 수집 대상이 된다.
  주소는 `mailto:` 링크와 함께 **문구에 그대로 노출**한다. 링크만 걸면 메일 앱이 없는
  환경에서 주소를 알 수 없다. 영구정지와 이용정지의 안내 문구는 다르게 둔다.
- 정지 회원의 홈 상단 상시 배너. 활동 시도 시 dialog로 알리므로 필수는 아니다. 필요하면
  후속으로 추가한다.

### 검증

- Backend `V27` migration 적용과 CHECK 제약 2개 동작을 `AdminMemberIntegrationTest`에서
  실제 PostgreSQL로 확인했다. 제재가 아닌 상태에 사유를 남기려 하면 DB가 거부한다.
- `SuspendedActivityPolicyCoverageTest`가 상태 변경 endpoint 전수 분류를 검사한다.
- Frontend `vitest` 505건, `tsc --noEmit` 통과.

### 남은 것

- 브라우저 수동 검증. 검증 항목과 관리자 버튼 절차는 아래 "수동 검증 절차"를 따른다.
- `V27` 번호는 다른 브랜치가 `V26`을 먼저 쓰기로 해 `V27`로 잡았다. `dev` 병합 후
  `dev`(`b62b1d0`)를 이 브랜치로 받아와 번호가 26 → 27로 연속됨을 확인했다.

### 수동 검증 절차

브라우저 2개(일반 + 시크릿)를 쓴다. 하나는 관리자, 하나는 검증 대상 회원(`role=USER`)이다.
관리자 화면은 `/admin/members` → 회원 행 클릭 → `회원 상세` dialog에 조치 버튼이 뜬다.
버튼은 대상 상태에 따라 조건부다.

| 버튼 | 내부 action | 나타나는 조건 |
| --- | --- | --- |
| 경고 | `WARNING` | `BANNED`가 아닐 때 |
| 이용정지 | `SUSPEND` | `ACTIVE` 또는 `PROFILE_REQUIRED` |
| 영구차단 | `BAN` | `ACTIVE`, `PROFILE_REQUIRED`, `SUSPENDED` |
| 정지 해제 | `UNSUSPEND` | `SUSPENDED` |
| 차단 해제 | `UNBAN` | `BANNED` |

`이용정지`를 누르면 사유 select와 **정지 기간** select(`ONE_DAY`/`THREE_DAYS`/`SEVEN_DAYS`
기본/`THIRTY_DAYS`)가 함께 뜬다. 기간 select는 `SUSPEND`에만 나타난다. 메모에
`password`·`token`·`secret`·`oauth`·`gps`·`위도`·`경도`·`비밀번호`를 넣으면 서버가 거부한다.

1. **정지 회원은 로그인되고 조회가 된다** — 관리자가 `이용정지` 후, 대상이 로그인하면
   정상 진입하고 축제 목록·상세를 볼 수 있어야 한다. 로그인이 막히면 실패다.
2. **정지 회원의 활동 화면은 안내로 대체된다** — `/matching`과 `/check-in`은 활동 UI 대신
   사유·기간 안내 card를 보여야 한다. "매칭 완료" 카드나 체크인 버튼이 뜨면 실패다.
3. **댓글 작성은 시도 시 dialog로 막힌다** — 축제 상세의 댓글 작성은 화면을 막지 않으므로
   시도하면 사유·기간 dialog가 뜨고 요청이 실패해야 한다.
4. **마이페이지** — 상단에 사유·기간 안내 card가 뜨고, **프로필 수정은 정상 동작해야 한다.**
   수정 후에도 상태가 `SUSPENDED`로 유지되어야 한다.
5. **영구정지는 로그인 자체가 막힌다** — `영구차단` 후 로그인하면
   `/login?oauthError=account_restricted`로 이동하고 "영구정지된 계정이에요"가 떠야 한다.
   **"소셜 로그인에 실패했습니다. 잠시 후 다시 시도해 주세요."가 뜨면 실패다** — 이번에
   고친 기존 버그가 되살아난 것이다.
6. **notice cookie** — 5번 도중 DevTools에서 `sanction_notice`가 생겼다가 안내 조회 후
   사라지는지 확인한다(`Path=/api/auth/sanction-notice`, `HttpOnly`).
7. **해제 후 정상 복귀** — `차단 해제`(사유 select는 `ADMIN_CORRECTION`/`OTHER` 2개만) 후
   로그인하면 안내 없이 정상 이용된다. 대상 회원 상태가 `ACTIVE`로 돌아온다.

정지 만료 경로는 `suspended_until`을 과거로 바꾸는 dev DB 쓰기가 필요하므로 수동 검증에서
제외했다. 만료 복구는 `MemberAccessPolicyTest`(lazy 복구)와 `AdminMemberIntegrationTest`
(batch 복구 시 사유 code 제거)가 덮는다.

### 수동 검증에서 발견해 고친 정지 회원 버그 3건

정지 회원이 로그인·조회를 할 수 있게 되면서 **전에는 도달할 수 없던 경로가 열렸고**,
거기서 버그 3건이 드러났다. 전부 이번 브랜치에서 고쳤다.

**1. 프로필 수정이 정지 회원을 거부했다.** `MemberProfileService.completeProfile`이
`PROFILE_REQUIRED`/`ACTIVE`만 허용해 `SUSPENDED`는 `INVALID_INPUT_VALUE`로 막혔다.
자기 정보 관리는 제재 대상이 아니므로 `SUSPENDED`를 허용 목록에 넣었다.

**2. 프로필 수정이 제재를 조용히 풀 수 있었다.** `Member.completeProfile`이 status를
**무조건 `ACTIVE`로 덮었다.** 정지 회원이 프로필을 수정하면 제재가 풀리는데
`suspended_until`과 사유는 남아 `chk_members_suspension_period`·
`chk_members_sanction_reason_presence` 위반으로 저장이 실패한다. 승격을
`PROFILE_REQUIRED`일 때만 하도록 좁혔다. **DB CHECK 제약이 없었다면 프로필 수정이 제재
해제 우회로가 됐을 것이다.** 정지 중 프로필을 완성한 경우 `status_before_sanction`도
`ACTIVE`로 올려, 해제 후 다시 가입 화면으로 보내지지 않게 했다.

**3. 매칭 화면에서 정지 회원이 빠져나갈 수 없었다.** `deriveMatchingState`는
`completionLock.groupId`가 있으면 상태를 `COMPLETED`로 만들고, `findLatestCompletedByMemberId`는
**회원이 새 pool에 들어가기 전까지** 지난 완료 그룹을 계속 반환한다(`NOT EXISTS newer_pool`).
그래서 과거에 매칭을 완료한 정지 회원은 "매칭 완료" 카드를 계속 보고, 그 카드를 지우는 유일한
방법인 새 매칭 신청이 `403`으로 막혀 화면에 갇힌다. `MATCH_VALIDITY`가 1시간이라 `active`는
이미 `false`인데 판정이 `groupId`를 보기 때문이다.

세 번째는 매칭 상태 판정을 바꾸지 않고 **화면이 제재 상태를 미리 알게 해서** 풀었다.
`GET /api/members/me`가 제재 안내(`sanction`)를 함께 내려주고, 매칭·체크인 화면은 정지
회원에게 활동 UI 대신 안내 card를 보여준다. 활동을 시도해 `403`을 받은 뒤 dialog로 알리는
것만으로는 이 막다른 길을 풀 수 없다.

마이페이지에도 같은 안내 card를 넣었다. 상태 이름("이용정지")만 보여서는 사유와 남은 기간을
알 수 없었다.

`useMemberSanction` 훅이 이 조회를 담당하며, 조회 실패 시 제재가 없는 것으로 본다. 안내를 못
읽는 것 때문에 정상 회원의 화면이 막히는 편이 더 나쁘다.

### 2차 수동 검증에서 고친 것

**마이페이지에서 제재 팝업이 매번 떴다.** 마이페이지가 관리자 메뉴 노출 여부를 판단하려고
`GET /api/admin/reports/session`을 조회하는데, `AdminAuthorizationService.requireAdmin`이
**역할 확인보다 제재 확인을 먼저** 했다. 그래서 정지된 **일반 회원**이 `FORBIDDEN`이 아니라
`MEMBER_SUSPENDED`를 받아 `apiClient`가 팝업 이벤트를 쐈다. 역할을 먼저 보도록 순서를 바꿨다.
관리자가 아니면 제재와 무관하게 `FORBIDDEN`이고, 관리자 여부 확인은 활동이 아니라 조회다.
정지된 관리자는 여전히 관리자 기능을 쓸 수 없다.

**팝업에 `닫기`와 `하루 동안 보지 않기`를 넣었다.** 정지는 조회가 계속 가능한 상태라 팝업을
강제할 이유가 없다. `하루 동안 보지 않기`는 `localStorage`에 만료 시각만 저장하며, 저장소를
못 읽는 환경(시크릿 모드·site data 차단)에서는 "설정 없음"으로 본다. 안내를 놓치는 것보다
한 번 더 보는 편이 낫다.

**마이페이지 안내는 팝업이 아니라 상단 배너다.** `AccountRestrictionNotice`에 `prominent`
모드를 넣어 coral 테두리와 배경으로 강조한다. 흰 카드로 두면 다른 카드와 섞여 묻힌다.

### 이 작업 중 드러난 별개 문제 (후속 후보, 이번 범위 아님)

작업 중 사용자 질문을 따라가며 확인한 것들이다. 모두 이 브랜치 밖이다.

1. **프론트엔드가 `/api/auth/refresh`를 부르지 않는다.** backend에 endpoint와 refresh token이
   있는데 호출부가 없다. access token이 30분에 만료되면 `apiClient`가 401을 받아 `/login`으로
   보내므로 **로그인 사용자가 30분마다 로그아웃된다.** 축제 현장에서 체크인하고 매칭을
   기다리는 동선과 정면으로 충돌한다. 규모가 작지 않아(동시 401 처리, refresh 실패 시
   로그아웃 처리) 별도 작업이 필요하다.
2. **`application-local.yml`만 `refresh-token-expires-minutes: 30`이다.** dev·prod는
   `20160`(14일)이다. 지금은 refresh를 아무도 부르지 않아 증상이 없지만 1번을 고치는 순간
   로컬에서만 30분 뒤 갱신이 실패한다. 복붙 잔재로 보인다.
3. **`MemberAccessInterceptor`가 파싱 불가 `access_token` cookie를 `401`로 막는다.**
   상대방이 `OptionalMemberResolver.resolveOrNull`로 공개 조회의 만료 cookie를 익명 처리하려
   했는데, interceptor가 controller 도달 전에 401을 던져 그 코드가 실행되지 않는다.
   `ContentBookmarkCommentIntegrationTest`의 `만료되거나_위조된_쿠키로도_공개_조회는_200이다`가
   이것 때문에 실패한다. 상대방 코드 범위라 이 브랜치에서 고치지 않았다.
4. **`FestivalRepositoryIntegrationTest:94` 실패는 테스트 작성 버그다.** 클래스 레벨과 메서드
   레벨 `@Sql`이 둘 다 있고 `@SqlMergeMode`가 없어 기본값 `OVERRIDE`로 클래스 fixture가
   대체된다. 그래서 `repo-fixture-ended`가 insert되지 않는다. `findForAdmin` 쿼리에는 날짜
   필터도 `ORDER BY`도 없어 프로덕션 코드 문제가 아니다. `@SqlMergeMode(MergeMode.MERGE)`를
   붙이거나 `ENDED` fixture를 메서드 `@Sql`에 넣으면 된다.

3번과 4번은 `dev`(`b62b1d0`) 병합으로 들어온 실패이며, 이 브랜치의 변경을 stash하고 돌려도
동일하게 실패함을 확인했다.

## [10-C 콘텐츠 참여] 찜(북마크)과 공개 댓글·좋아요

상태: 설계·Backend·Frontend 구현 완료. **Backend 통합 테스트 미실행(Docker 없음), 런타임 미검증**

기획서·WBS에 없던 신규 범위이며, 사용자 승인에 따라 10-B의 다음 작업으로 잡혀 있던
`신고·안전·후기와 관리자 연계`보다 **먼저** 진행했다. 설계 전문은
`docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md`이고 DB는 `docs/11_DATABASE_DESIGN.md`,
화면 규칙은 `docs/03_FRONTEND_GUIDE.md`, 서버 규칙은 `docs/04_BACKEND_GUIDE.md`, 보안은
`docs/06_SECURITY_POLICY.md`, 검증 기준은 `docs/09_TEST_AND_QUALITY_STRATEGY.md`에 반영했다.

작업 브랜치는 사용자 선택에 따라 새로 만들지 않고 `feature/wbs-10-a-festival-course`에서
이어서 작업했다. 댓글 본문이 재사용하는 `ExpandableText`가 그 브랜치에만 있었기 때문이다.

### 먼저 확정한 판단 3건

| 항목 | 확정값 |
| --- | --- |
| 공개 조회의 인증 | 비로그인·만료·무효 쿠키 전부 `200`. 로그인 여부는 `engagement`의 `viewer.loggedIn`으로만 판단 |
| 좋아요 카운터 | `content_comment_likes`의 실제 영향 행 수가 1일 때만 `like_count` 증감 |
| 댓글 신고 | 이번 범위 제외. 작성자 삭제 + 관리자 숨김으로 최소 모더레이션 확보 |

첫 번째가 가장 중요하다. frontend `apiClient`는 **모든 `401`을
`window.location.replace('/login')` 전역 리다이렉트로 처리**한다. 그래서 공개 상세 화면에 붙는
조회 API가 `401`을 주거나 로그인 여부를 알기 위해 `GET /api/members/me`를 호출하면, 비로그인
사용자가 축제·관광지 상세를 **열기만 해도 로그인 화면으로 튕긴다.** 이 제약이 `engagement`
endpoint와 `OptionalMemberResolver`의 존재 이유다.

세 번째는 `reports`에 target 개념이 없기 때문이다(`reported_member_id NOT NULL` + match group
한정). 댓글을 붙이려면 `admin_actions.report_id`, `match_penalty_events.related_report_id`,
`admin_safety_alerts.trigger_report_id`와 V25가 만든 30일 유효 신고 누적 자동 제재 파이프라인까지
함께 건드려야 해서, 매너온도·제재 정책 변경이 된다.

### DB

`V26__add_content_bookmarks_comments.sql`로 `content_bookmarks`, `content_comments`,
`content_comment_likes` 3개를 생성했다. 기존 테이블과 constraint는 변경하지 않았다.

대상(축제/관광지) 표현은 `target_type` + `target_id`가 아니라 nullable FK 2개 +
`정확히 하나` CHECK다. `recommendation_click_logs`의 기존 방식을 따르되 CHECK를 `OR`(둘 다 채워도
통과)에서 배타적 조건으로 조였다. 동기화가 축제를 물리 삭제하지 않고 `INACTIVE` 표시만 하므로
(`FestivalSyncWriter.markMissingFestivalsInactive`) `ON DELETE RESTRICT`가 안전하다.

댓글은 `deleted_at` 단독 soft delete 선례가 없어 `status` 상태 머신
(`VISIBLE`/`DELETED`/`HIDDEN`) + 시점 컬럼 관용구를 따랐고,
`(status = 'VISIBLE') = (deleted_at IS NULL)` 짝 CHECK는 V25 `admin_safety_alerts`와 같은 방식이다.

**적용 전 공유 dev DB의 `flyway_schema_history`에서 V26이 비어 있는지 확인해야 한다.**

### Backend

`domain/content/{support,bookmark,comment,engagement}`에 entity·repository·service·controller·DTO를
추가하고 `ErrorCode`에 `CONTENT_TARGET_NOT_FOUND`,
`CONTENT_COMMENT_{INVALID_REQUEST,NOT_FOUND,FORBIDDEN,PROFILE_REQUIRED,TOO_FREQUENT}`를 넣었다.
`429`는 `handleBusinessException`이 `errorCode.getStatus()`를 쓰므로 handler 변경이 없었다.

endpoint는 `GET|PUT /api/{festivals|spots}/{id}/engagement|bookmark`,
`GET|POST /api/{festivals|spots}/{id}/comments`, `DELETE /api/comments/{id}`,
`PUT /api/comments/{id}/like`, `PUT /api/admin/comments/{id}/visibility`,
`GET /api/members/me/bookmarks?type&page&size`다. 토글은 `POST`/`DELETE` 쌍이 아니라
`PUT` + 상태 body 하나로 뒀다 — 기존 `PUT .../cancellation`·`.../acknowledgement` 방식과 같고
멱등성과 "현재 카운트를 응답으로 돌려준다"를 동시에 만족한다.

정지·차단·비활성 회원의 신규 작성은 기존 `MemberAccessInterceptor`가 자동으로 막으므로 별도
구현하지 않았다. `PROFILE_REQUIRED`는 닉네임이 없어 표시할 이름이 없으므로 따로 막는다.

### Frontend

`useMemberBlocks` 패턴대로 `createContentBookmarkSession`/`createContentCommentsSession` 세션
팩토리와 얇은 hook을 만들고, 두 상세 화면의 `PageHeader.rightAction`에 `BookmarkButton`을 공유
버튼과 나란히 두고 `<main>` 마지막에 `ContentCommentSection`을 붙였다. 댓글 본문은
`ExpandableText`(200자 컷)를 재사용하고 좋아요는 `ThumbsUp`으로 찜(`Heart`)과 구분한다.

`MyPage`의 mock 찜 섹션(`data/mock/tourSpots.ts`)을 실데이터로 교체하고
`/mypage/favorites`(`FavoritesPage`)를 추가했다. `data/mock/tourSpots.ts`는 이제 참조되지 않으므로
후속 작업에서 삭제 대상이다.

낙관적 갱신은 하지 않는다. 좋아요 카운트도 서버가 돌려준 실제값으로 덮는다.

### 검증

- Frontend: `tsc --noEmit` 통과, vitest **55 파일 / 478 테스트 통과**(기존 399 + 신규 79),
  `npm run build` 성공. 코디네이터가 직접 재실행해 확인했다.
- Backend: `compileJava`/`compileTestJava` 통과, `ContentCommentServiceTest` 18건과
  `OptionalMemberResolverTest` 5건 통과.
- **Backend 통합 테스트 `ContentBookmarkCommentIntegrationTest`(24건)는 컴파일만 되고 실행되지
  않았다.** 작업 머신에 Docker가 없어 Testcontainers가 `ContainerFetchException`으로 실패한다.
  이 저장소의 Spring context 테스트는 전부 Testcontainers를 요구해 우회 경로가 없다.
- 그래서 **신규 JPQL의 Hibernate 파싱**과 **`ddl-auto: validate`의 entity↔V26 일치**가 런타임으로
  검증되지 않았다. Docker 없이 가능한 정적 교차 확인은 마쳤다 — 참조하는 모든 entity 경로가 실제
  필드명과 일치하고(특히 `Festival`의 지역 필드는 `regionCode`가 아니라 **`areaCode`**),
  constructor projection record 타입이 selection과 맞고, entity 컬럼명·nullable·length가 V26 DDL과
  일치한다.
- Gradle은 이 머신에서 `JAVA_HOME="C:\java\zulu17"`을 지정해야 빌드된다(기본 `JAVA_HOME`이 JDK 8).

### 남은 작업

1. **Docker 환경에서 통합 테스트와 애플리케이션 부팅 1회 확인** — 그 전까지 "코드 완성, 런타임
   미검증"으로 취급한다.
2. **탈퇴 연동 미배선.** `ContentCommentService.softDeleteAllOnWithdrawal`과
   `ContentBookmarkService.deleteAllOnWithdrawal`은 구현·테스트까지 되어 있지만, 이 저장소에는
   회원 탈퇴 서비스 자체가 없다(`members.withdrawn_at` 컬럼만 있고 탈퇴 endpoint가 없다.
   문서에 등장하는 `DELETE /api/members/me`는 미구현). 탈퇴 기능 담당자가 같은 transaction에서
   두 메서드를 호출해야 하며 `ContentCommentService`에 TODO를 남겼다.
3. 두 브라우저 수동 검증 — 비로그인 진입, 찜 토글, 댓글 등록·삭제, 좋아요 연타, 관리자 숨김.
4. `data/mock/tourSpots.ts` 삭제.
5. 이번 범위 제외 항목은 `docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md` 9장 참고 — 대댓글, 댓글 신고,
   인기순 정렬, 찜 공개 카운트, 목록 화면 하트, cursor 페이징.

## [10-A 후속 11] 홈에서 솔로 코스 진입 시 체크인 게이트 복구

상태: 구현 완료(Frontend 전용). 두 브라우저 수동 검증 전

- 증상: 체크인하지 않고 홈의 "혼자 즐기는 주변 관광지 추천" 배너를 누르면 체크인 안내가 아니라
  코스 화면이 떴다.
- 원인: `HomePage`의 `CtaBanner`가 `state={{ festivalId: hotFestival.id }}`를 넘기고 있었다.
  `resolveSoloCourseFestival`은 `location.state.festivalId`를 1순위로 쓰므로 이 값이 항상 채워져
  **체크인 안내 분기에 도달할 수 없었다.** 이번 작업에서 생긴 문제가 아니라
  `[10-A 후속 3]`(docs/22 구현) 때부터의 동작이다.
- **`docs/22_SOLO_COURSE_NEARBY_SPOT_DESIGN.md` 내부에 모순이 있었다.** 5장 말미와 7장·10장은
  "홈 배너도 `hotFestival.id`를 state로 넘기도록 고쳐야 한다"고 했지만, 9장은 "체크인이 전혀 없는
  상태로 진입 시 대표 축제로 대체하지 않고 체크인 안내만 보여준다"로 결정했다. 전자를 구현하면
  후자가 도달 불가능해진다.
- 사용자 기대가 9장과 같아 **9장 결정을 살리고** 홈 배너에서 `state`를 제거했다. 문서 5장·7장에
  정정 메모를 추가했다.
  - `FestivalDetailPage`에서 넘기는 `festivalId`는 그대로 둔다(5장 1순위). 그 화면은 사용자가 특정
    축제를 보고 있으므로 체크인 없이 기준으로 삼아도 된다. 두 진입 경로의 동작 차이는 의도된 것이다 —
    홈 배너는 "내 주변"을 뜻하므로 체크인이 기준이어야 한다.
  - 배너 설명 문구도 "선택한 축제 주변"에서 "체크인한 축제 주변"으로 바꿨다.
- `SoloCoursePage`의 안내 버튼을 `/check-in` → `/spots`로 바꿨다(`체크인할 축제 고르기`).
  `[10-A 후속 10]`에서 `CheckInPage`가 축제 미지정 진입 시 `/spots`로 튕기게 했으므로, 그대로 두면
  화면이 한 번 깜빡인다.
- `[10-A 후속 10]`에서 놓친 로딩 표시도 함께 정리했다 — `SoloCoursePage`, `ProfileEditPage`,
  `SignupPage`, `MyPage`의 텍스트 전용 로딩을 `LoadingState`/`Spinner`로 교체했다.
- 회귀 방지 테스트 2건을 `SoloCoursePage.test.ts`에 추가했다(state 없이 체크인 없음 → `festivalId`
  null, state 없이 체크인 있음 → 그 축제).
- 검증: `tsc -b` 통과, vitest **341개 전부 통과**, `npm run build` 성공. Backend 변경 없음.


## [10-B 안전 후속] 만남 종료 후 신고 진입점 (docs/19 4.10)

상태: Backend/Frontend 구현·전체 회귀·수동 검증 완료. PR 대기

브랜치는 `feature/wbs-10-b-match-report-entry`이며 `dev`(`08197fa`)에서 분기했다.

### 문제

접수 API는 만남 종료 후 30일까지 신고를 허용하는데 **그 기간에 신고할 화면이 없었다.**
`신고하기` 버튼이 `MatchRoomPage`에만 있고 그 화면은
`matching_group.status IN ('CONFIRMED','IN_PROGRESS')`인 현재 그룹에만 의존한다. 만남이 끝나면
진입점이 사라지므로 **실질 신고 가능 기간이 0이었다.** 4.3에서 신고 누적 자동화까지
만들어 뒀는데 정작 신고를 넣을 화면이 없는 상태였다.

### 먼저 확정한 정책 4건

| 항목 | 확정값 |
| --- | --- |
| 신고 가능 기간 | 14일, `completed_at`/`cancelled_at` 기준 (기준 컬럼은 그대로) |
| 화면 위치 | MyPage "매칭 기록" 카드 → `/mypage/matches` 별도 화면 |
| 노출 범위 | `COMPLETED` + `CANCELLED` 전체 이력, 기간 지난 건은 버튼만 비활성화 |
| 이미 신고한 상대 | 버튼 비활성화 + `신고됨` 배지 |

### 구현에서 중요한 판단

**기간 정책을 한 곳으로 모았다.** `MatchReportWindowPolicy`를 신설해 접수 API와 목록이
같은 상수와 판정을 쓰게 했다. 목록이 자체 계산을 하면 화면에서 "신고 가능"으로 보인 항목이
접수에서 `REPORT_WINDOW_EXPIRED`로 거절되는 어긋남이 생긴다.

**기간·신고여부 판정을 서버에 뒀다.** 응답에 `reportable`, `reportableUntil`, 참가자별
`reported`를 담는다. 프론트가 날짜를 계산하면 같은 문제가 재발한다.

**기존 조회를 재사용하지 않았다.** `findLatestCompletedByMemberId`는 매칭 완료 판정용이라
조건이 다르고, `findCompletedMembersWithProfileByGroupId`는 `status = 'COMPLETED'`로 좁혀져
취소 그룹의 참가자를 담지 못한다. 각각 이력 전용 조회를 추가했다.

**"신고됨" 판정에서 사유를 접었다.** `reports`의 유니크 키는
`(reporter, reported, group, reason_code)`라 같은 상대를 다른 사유로 여러 번 신고할 수 있다.
화면 표시는 사유와 무관하게 "이 만남에서 이 사람을 신고한 적 있음"이어야 하므로 `DISTINCT`로
접었다.

**cursor에 HMAC을 붙이지 않았다.** 관리자 목록 codec들은 filter fingerprint를 서명하지만,
이 조회는 JWT 회원으로 고정돼 있고 filter가 없어 위조해도 자기 목록 안에서 위치만 바뀐다.
관리자 cursor secret을 회원 API가 공유하는 것도 부적절하다.

**신고 dialog에 groupId를 직접 들려 보냈다.** 상대 ID로 목록을 되짚으면 같은 사람과 여러 번
만났을 때 다른 만남에 신고가 붙을 수 있다.

### 건드리지 않은 것

기간을 30일 → 14일로 줄였지만 목적이 다른 두 window는 그대로 뒀다.

- `ReportConfirmationService.AGGREGATION_WINDOW_DAYS` (30일) — 신고 누적 집계 window
- `MatchBlockService.BLOCK_WINDOW_DAYS` (30일) — 차단 허용 기간

migration, 신고 접수 API, 매칭 transaction 경계도 변경하지 않았다.

### 검증

- `MatchHistoryIntegrationTest` 10건(PostgreSQL Testcontainers) 통과 — 종료·취소 함께 반환,
  진행 중 제외, 본인 제외, 미참여자 차단, 14일 경계, 사유 무관 신고됨 표시, 타인 신고
  비노출, cursor 연속성, 동일 시각 id tiebreaker, 위조 cursor 거절.
- 기존 `MatchReportIntegrationTest`의 30일 경계 테스트 3건을 14일로 갱신했다.
- Backend 전체 **737 tests 실패 0건**(기존 727 + 신규 10).
- Frontend 45 files/379 tests 통과, `npx tsc --noEmit` 통과.

테스트 픽스처에서 두 가지에 걸렸다. `match_groups.attempt_id`는 `uq_match_groups_attempt`로
유일해서 기록을 여러 건 만들려면 그룹마다 attempt가 따로 있어야 한다. 그리고
`uq_match_group_members_member_active`가 회원당 활성 참여를 1건으로 제한하므로, 종료된 그룹의
참여 상태를 실제와 같이 `COMPLETED`/`CANCELLED`로 넣어야 한다.

Frontend 테스트에서 `expect(html).not.toContain('disabled')`는 Tailwind의 `disabled:` 클래스가
마크업에 항상 남아 **거짓 양성**이었다. `disabled=""` 속성으로 바꿨다.

### 브라우저 수동 검증 PASS

dev DB에 연결한 로컬 환경에서 실제 계정 2개(카카오 `dev카테` id 2, 네이버 `테스트` id 27)로
확인했다.

- 매칭 확정 → 양쪽 `도착했어요` → 그룹 `COMPLETED` 전환.
  `MatchArrivalService`가 활성 참가자 전원 `ARRIVED`인 순간 그룹을 완료 처리한다.
- `/mypage/matches`에서 해당 만남이 신고 가능 상태로 노출.
- 신고 접수 후 목록이 다시 읽히며 `신고됨` 배지로 전환.
- 접수된 신고가 관리자 화면에 정상 노출.
- 기존 매칭 기록(2026-08-14 이전, 14일 초과분)은 `신고 기간 종료`로 버튼이 잠기고, 취소된
  만남은 `취소됨` 배지로 구분됐다.

### 남은 것

- 차단 허용 기간 30일과 신고 14일이 갈라진 점. 버그는 아니지만 정책 일관성 검토 후보다.

### 검증 중 확인한 별건

`frontend/.env.local`의 `VITE_DEV_FESTIVAL_ID=144`가 남아 있어, 체크인 없이 `/matching`에
들어가면 `MatchingConditionPage.resolveFestivalId`의 개발 fallback이 걸려 축제 선택 없이
`Matching UI test festival`(dev DB id 144)로 바로 체크인 화면이 뜬다. 이 축제는 좌표가
`NULL`인 더미다.

`.env.local`은 커밋 대상이 아니고 fallback도 의도된 개발 편의 장치라 **버그는 아니다.** 다만
체크인이 실제로 붙은 지금은 값이 낡았다. 축제 선택 단계를 건너뛰는 인지 문제 자체는
`ISSUE-MR-009` 후속(비동기 상태 복원·화면 전환 안정화)의 "자동 매칭 진입이 `2 -> 1` 역순으로
인지된다"와 같은 뿌리다.


## [10-B 안전 후속] 로그아웃 구현과 소셜 계정 전환 (docs/19 4.6)

상태: 완료 (PR #52, dev 병합 완료)

브랜치는 `feature/wbs-10-b-logout`이며 `dev`(`d7c04c5`)에서 분기했다. 로그아웃 버튼이
`navigate('/login')`만 호출해 **누르고도 로그아웃이 되지 않던** 문제를 없앤다. cookie,
refresh token, WebSocket session이 전부 살아 있어 공용 기기에서 보안 문제였다.

### 먼저 확정한 정책 4건

| 항목 | 확정값 |
| --- | --- |
| 미인증 로그아웃 호출 | `204` 멱등. cookie 만료 헤더는 항상 내려준다 |
| WebSocket session | 함께 종료. 관리자 제재와 같은 revoke → `AFTER_COMMIT` → `closeAll` 순서 |
| access token 무효화 | cookie 만료만. denylist는 도입하지 않고 한계를 `docs/06`에 기록 |
| 진행 중 매칭 pool/proposal/group | 로그아웃은 허용하되 정리하지 않는다 |

세 번째가 중요하다. access token은 stateless JWT(기본 30분)라 **서버가 강제로 무효화할 수
없다.** 브라우저는 cookie가 사라져 즉시 `401`이지만 이미 유출된 raw token은 남은 만료
시간까지 유효하다. `docs/NEXT_PROMPT.md`의 "로그아웃 후 access token으로 401" 테스트 항목은
cookie를 지우는 것만으로 성립하지 않아, "cookie 없이 호출하면 `401`" + "폐기된 refresh token은
거절"로 바꿔 검증했다.

네 번째는 로그아웃으로 매칭을 종료시키면 penalty 회피 경로가 열리기 때문이다. 로그아웃은
매칭 취소가 아니고, 미응답은 기존 proposal timeout·penalty 정책이 그대로 처리한다.

### 구현

Backend

- `POST /api/auth/logout` 신설. 항상 `204`, `access_token`·`refresh_token`을 `Max-Age=0`으로
  만료한다. cookie 속성이 발급 때와 하나라도 다르면 브라우저가 지우지 않으므로 발급용
  `tokenCookie(...)`에 `Duration.ZERO`를 넘겨 재사용했다.
- `AuthService.logout(rawAccessToken)`은 토큰이 없거나 만료·변조되면 조용히 종료한다.
- `AuthService.revokeSession(memberId)`이 `revokeByMemberId` 폐기와 `MemberLoggedOutEvent`
  발행을 담당한다. 4.4 회원 탈퇴가 이 경로를 재사용한다.
- `domain/auth/event/MemberLoggedOutEvent`·`MemberLoggedOutEventHandler`를 신설했다.
  `AdminMemberAccessRevokedEvent`를 그대로 쓰지 않은 이유는 admin 도메인 이벤트를 auth가
  발행하는 도메인 역전을 피하기 위해서다.
- `SecurityConfig`, `WebMvcConfig`, migration은 변경하지 않았다. `/api/auth/**`는 이미
  `MemberAccessInterceptor` 제외 경로여서 정지된 회원도 로그아웃할 수 있다.

Frontend

- `src/api/auth.ts`를 신설하고 `MyPage`의 버튼이 `authApi.logout()` 호출 후
  `/login`으로 `replace` 이동하도록 고쳤다.
- 호출이 실패해도 공용 기기에 화면을 남기지 않도록 이동은 하되 오류 문구를 노출하고,
  `isLoggingOut`으로 중복 클릭을 막는다.

### 검증

- Backend 전체 **727 tests 실패 0건**(기존 716 + 신규 11).
- `AuthLogoutIntegrationTest` 4건은 실제 PostgreSQL Testcontainers로 refresh token 폐기 후
  `refresh` 거절, 재호출 멱등성, 변조 토큰의 무영향, commit 이후 WebSocket session 종료를
  확인한다.
- `AuthControllerTest`는 로그아웃 cookie의 속성을 **로그인 응답 cookie와 직접 비교**해
  `Path`/`HttpOnly`/`Secure`/`SameSite` 불일치를 잡는다. 문자열을 하드코딩하지 않았다.
- Frontend 43 files/370 tests 통과, `npx tsc --noEmit` 통과. jsdom이 없어 클릭 재현은
  불가능하므로 호출 계약은 `src/api/auth.test.ts`가 검증한다.

### 수동 검증에서 드러난 후속 — 소셜 계정 전환

로그아웃 자체는 브라우저에서 PASS했다. `access_token`·`refresh_token` cookie가 모두
삭제되는 것을 확인했다.

그 과정에서 별개 문제를 확인했다. **로그아웃 후 카카오 버튼을 누르면 아이디 입력 없이
직전 계정으로 즉시 재로그인된다.** 우리 로그아웃 버그가 아니라, 우리 세션(cookie + DB
refresh token + WebSocket)과 카카오 계정 세션(`kakao.com` cookie)이 별개이기 때문이다.
카카오 입장에서는 이미 로그인된 사용자라 인가 코드를 바로 돌려준다. 소셜 로그인의 표준
동작이지만 **다른 계정으로 바꿀 수 없다**는 실사용 문제가 된다.

검토한 선택지는 셋이었다.

| 방법 | 결과 | 대가 |
| --- | --- | --- |
| `prompt=select_account` | 계정 선택 화면 | 없음. 채택 |
| `prompt=login` | 매번 재인증 | 평소 로그인도 매번 아이디 입력 |
| 카카오계정과 함께 로그아웃 | 카카오 세션 종료 | 다른 카카오 서비스도 로그아웃. `logout_redirect_uri` 콘솔 사전 등록. 로그아웃이 `204`로 끝날 수 없어 흐름 재설계 |

세 번째는 로그아웃 API 계약 자체를 바꿔야 해서 제외했다. 채택한 구현은 authorize URL에
파라미터 하나씩 추가하는 것이다.

- 카카오 `prompt=select_account` — [공식 문서](https://developers.kakao.com/docs/ko/kakaologin/rest-api)로 확인했다.
- 네이버 `auth_type=reauthenticate` — 공식 문서(`developers.naver.com`)가 이 환경에서 열리지
  않아 커뮤니티 자료로만 확인했으나, **브라우저에서 재인증 화면이 실제로 뜨는 것을 확인했다.**

두 파라미터 모두 브라우저에서 계정 선택·재인증 화면이 뜨는 것을 확인했다.

### 남은 것

- 4.4 회원 탈퇴에서 `revokeSession` 재사용.


## [10-B FIX] 기존 실패 테스트 15건 복구와 체크인 취소 pool 정리 트랜잭션 버그

상태: 완료 (PR #51, dev 병합 완료)

브랜치는 `fix/wbs-10-b-failing-tests-and-checkin-pool-transaction`이며 `dev`(`586f112`)에서
분기했다. 오래 "기존 실패"로 방치돼 있던 15건을 0으로 만드는 작업이다. 조사 결과 원인이 둘로
갈렸고 **그중 1건은 테스트 문제가 아니라 실제 운영 버그였다.**

### (A) 14건 — `@WebMvcTest` 슬라이스에 `JwtProvider` 누락

`FestivalControllerTest` 9건, `TourPlaceControllerTest` 5건이다. 둘 다
`@WebMvcTest(...)` + `@Import(SecurityConfig.class)` 구성인데, `SecurityConfig`가 요구하는
`JwtProvider` mock이 없어 컨텍스트 로딩 자체가 `NoSuchBeanDefinitionException`으로 실패했다.
테스트 본문이 아니라 컨텍스트 로딩에서 터지므로 클래스 내 전체 테스트가 함께 실패한다.

같은 구조인데 통과하는 `MemberConsentControllerTest`와의 차이가 정확히 한 줄이었다.

```java
@MockitoBean
private JwtProvider jwtProvider;
```

두 파일에 위 선언과 `import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;`를 추가했다.

### (B) 1건 — `AFTER_COMMIT` 리스너에서 쓰기 트랜잭션이 열리지 않던 실제 버그

`FestivalCheckinCancelledEventHandlerIntegrationTest`의
`다른_축제로_재체크인하면_기존_축제의_WAITING_pool이_CANCELLED로_정리된다`가
`expected: "CANCELLED" but was: "WAITING"`으로 실패했다. 원인은 테스트 리포트 XML의
`system-out`에 남아 있었다.

```text
ERROR ... FestivalCheckinCancelledEventHandler : 체크인 취소에 따른 match pool 정리에 실패했습니다.
org.springframework.dao.InvalidDataAccessApiUsageException: no transaction is in progress
```

메커니즘은 다음과 같다.

- `FestivalCheckinCancelledEventHandler`가 `@TransactionalEventListener(AFTER_COMMIT)`이다.
- 거기서 호출하는 `MatchPoolCheckinCancellationService.cancelWaitingPool()`이
  `@Transactional` 기본값 `REQUIRED`였다.
- `AFTER_COMMIT` 시점에는 원본 트랜잭션이 이미 커밋됐지만 트랜잭션 동기화는 살아 있다.
  그래서 Spring이 새 트랜잭션을 열지 않고 이미 완료된 트랜잭션에 참여하려 한다.
- 그 상태에서 `@Modifying` UPDATE를 실행하면 `no transaction is in progress`로 터진다.
- handler가 `catch (RuntimeException)`으로 로그만 남기고 삼키므로 **운영에서도 조용히 실패했다.**

**즉 수정 전 dev/운영에서는 다른 축제로 재체크인해도 기존 축제의 `WAITING` pool이 정리되지
않았다.** `docs/21_CHECKIN_MATCH_POOL_INTEGRATION_DESIGN.md` 설계대로 동작하지 않은 것이다.

수정은 pool entry 매칭 경로와 동일하게 `REQUIRES_NEW`로 맞췄다.

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public int cancelWaitingPool(long memberId, long festivalId, OffsetDateTime now) { ... }
```

**왜 지금까지 안 걸렸나**: 같은 클래스의 `LOCKED`/`PROPOSED` 테스트 3건은 "건드리지 않아야
한다"를 검증하므로 handler가 아예 동작하지 않아도 통과한다. 실제 동작을 검증하는 것은 실패한
1건뿐이었고, 그게 "기존 실패"로 분류돼 방치됐다.

### 함께 점검한 `AFTER_COMMIT` 리스너 전수 조사

같은 결함이 더 있는지 `@TransactionalEventListener`를 전수 확인했다. 4개가 있었다.

| 리스너 | DB 쓰기 | 전파 속성 | 판정 |
| --- | --- | --- | --- |
| `FestivalCheckinCancelledEventHandler` | 있음 | `REQUIRED` → **`REQUIRES_NEW`로 수정** | 이번 수정 대상 |
| `MatchingPoolEnteredEventHandler` | 있음 | 하위 서비스 전부 `REQUIRES_NEW` | 이상 없음 |
| `MatchingStateChangedEventHandler` | 없음(WebSocket 전송만) | — | 이상 없음 |
| `AdminMemberAccessRevokedEventHandler` | 없음(session 종료만) | — | 이상 없음 |

`MatchingPoolEnteredEventHandler`가 부르는 `PoolEntryMatchingOrchestrationService`는 자신에게
`@Transactional`이 없고, 하위 `PoolEntryMatchPoolClaimService`, `MatchingBatchReader`,
`MatchProposalCreationService`, `MatchPoolReleaseService`가 모두 `REQUIRES_NEW`다. 즉
`REQUIRES_NEW`가 이 프로젝트의 확립된 패턴이었고 `MatchPoolCheckinCancellationService`만
빠져 있었다.

### (C) 수정이 드러낸 공허하게 통과하던 테스트 1건

(B)를 고치자 같은 클래스의
`같은_축제_재체크인은_그_축제의_WAITING_pool을_취소하지_않는다`가 실패로 바뀌었다. 이 테스트도
handler가 아예 돌지 않아 그동안 자기 주장을 검증한 적이 없었다. handler가 실제로 돌기 시작하니
"같은 축제로 재체크인해도 pool은 `WAITING`으로 남는다"는 주장이 거짓임이 드러났다. 취소되는
체크인마다 이벤트가 발행되므로 같은 축제의 pool도 `CANCELLED`가 된다.

**운영 코드가 아니라 테스트를 고치기로 했다.** 근거는 두 가지다.

첫째, 이 테스트의 주장에 문서 근거가 없다. `docs/21` 6장이 요구한 것은 다음 한 줄이다.

> 같은 축제로 재체크인(기존 3.2절 케이스, **이번 이벤트와 무관**) 시 기존 동작이 깨지지 않는지
> 회귀 확인.

여기서 "기존 동작"은 같은 문서 1.1절 4번 단계의 내용, 즉 취소 UPDATE를 새 INSERT보다 먼저
flush해 `uq_festival_checkins_member_festival_active` 부분 unique index 위반을 막는 것이다.
pool 상태에 대한 요구가 아니고, 오히려 "이번 이벤트와 무관"이라고 명시했다. 실패한 테스트는
문서가 요구한 것보다 강한 주장을 스스로 추가한 것이었다.

둘째, 이 경로는 화면으로 도달할 수 없다.

| 값 | 실제 | 위치 |
| --- | --- | --- |
| 체크인 유효기간 | 1시간 | `CheckinValidityPolicy.VALIDITY` |
| `WAITING` pool 검색 window | 60초 | `MatchPoolEntryService` — `now.plusSeconds(60)` |
| 같은 축제 체크인 버튼 | 체크인이 유효한 동안 숨김 | `FestivalDetailPage.tsx` — `!isCheckedIntoThisFestival` |

`WAITING` pool은 60초만 산다. 그 60초 안에 같은 축제로 재체크인해야 문제가 되는데, 그 시점에
체크인은 아직 55분 넘게 유효해서 버튼이 보이지 않는다. 체크인이 1시간 뒤 만료돼 재체크인할
때는 pool이 이미 59분 전에 `EXPIRED`라 취소 대상이 없다. 즉 도달 불가능한 시나리오를 위해
운영 코드에 분기를 넣는 셈이 된다.

그래서 테스트를 `docs/21` 6장이 실제로 요구한 회귀 가드로 다시 썼다. 이름을
`같은_축제_재체크인은_unique_index_위반_없이_기존_체크인을_대체한다`로 바꾸고, 새 체크인이
`ACTIVE`로 생성되고 기존 체크인이 `CANCELLED`가 되며 해당 축제의 `ACTIVE` 체크인이 1건만
남는지를 검증한다. pool이 `CANCELLED`가 되는 현재 동작도 함께 명시하고, 왜 이 경로가 화면으로
도달 불가능한지를 Javadoc에 남겨 다음 사람이 같은 혼동을 겪지 않게 했다.

### 함께 정리한 문서

- `docs/19_ADMIN_MEMBER_SAFETY_ROADMAP.md`에 4.10 "만남 종료 후 신고 진입점 후속" 절을
  신설하고 7장 권장 브랜치 순서에 `feature/wbs-10-b-match-report-entry`를 추가했다.
  4.3 검증 중 발견한 "접수 API는 종료 후 30일까지 신고를 허용하는데 그 기간에 신고할 화면
  경로가 없다"가 이 진행 로그에만 있고 로드맵에는 절 번호가 없어 묻힐 상태였다.
- 같은 문서의 4.3 상태를 "병합 전"에서 "완료 (PR #50)"로 갱신했다. PR #50이 이미 `dev`에
  병합됐는데 문서만 남아 있었다.

### 후속 참고

`ci.yml`이 `./gradlew build -x test`, `deploy-dev.yml`이 `bootJar -x test`라 현재 CI에서
테스트가 실행되지 않는다. 실패가 0건이 된 지금은 `-x test`를 뗄 수 있는 상태다. 다만 이번
범위에 CI 변경은 포함하지 않았다.


## [10-B 4.3] 신고 누적·안전 자동화

상태: 정책 확정, Backend·Frontend 구현, 자동 테스트 완료. dev 수동 검증 전

`docs/19_ADMIN_MEMBER_SAFETY_ROADMAP.md` 4.3절 작업이다. 브랜치는
`feature/wbs-10-b-report-safety-automation`이며 `dev`(`7ede73d`)에서 분기했다.

### 착수 전 확인

- `origin/dev` 단독에서 기존 실패 15건을 baseline으로 재현했다
  (`FestivalControllerTest` 9, `TourPlaceControllerTest` 5,
  `FestivalCheckinCancelledEventHandlerIntegrationTest` 1). Controller 테스트를 추가하는
  작업이라 원인 오판을 막기 위해 선행 확인이 필요했다.
- 공유 dev DB `flyway_schema_history`는 조회하지 못했다. 저장소 `.env`는 local Docker
  PostgreSQL 전용이고 dev DB 접속 정보가 없다. **`V25` 번호는 PR 전에 dev DB에서 다시
  확인해야 한다.**

### 정책보다 먼저 나온 구조적 충돌 3건

코드보다 정책이 먼저인 작업이었고, 조사 과정에서 기획서 후보 정책을 그대로 쓸 수 없는
이유가 세 가지 나왔다.

- **기획서 3개를 그대로 합치면 유효 신고 1건이 영구 매칭 제한이 된다.** 시작값 `36.50`에서
  `-10`이면 `26.50`이 되어 "30도 이하 매칭 제한"에 즉시 걸린다. `member_reviews`는 `V4`에
  table만 있고 코드가 없어 온도를 올릴 경로가 하나도 없다.
- **`match_cooldowns`는 회원당 `ACTIVE` row가 1개다.**
  `uq_match_cooldowns_member_active` 때문에 신고 기반 cooldown은 기존 매칭 cooldown과
  충돌한다. 그래서 cooldown을 만들지 않는다.
- **`admin_actions.admin_member_id`가 `NOT NULL`이다.** 관리자 없이 생성되는 자동 알림을
  담을 수 없어 별도 table이 필요했다.

### 확정 정책

상세는 `docs/05_MATCHING_POLICY.md`의
`관리자 유효 판정 신고 (REPORT_CONFIRMED)` 절과 `docs/19` 4.3절에 있다.

| 항목 | 확정값 |
| --- | --- |
| 유효 신고 정의 | `reports.status IN ('RESOLVED','ACTION_TAKEN')`. `REJECTED` 제외 |
| 누적 집계 | 30일 rolling, `(reporter_member_id, group_id)` distinct, 임계 3 |
| penalty | `+5`, cooldown 미생성 |
| 매너온도 | `-5.00`, 하한 `20.00`, 증분 적용, 재계산 batch 없음 |
| 적용 시점 | 관리자 `RESOLVED` transaction 내 동기. Scheduler 없음 |
| 자동 제한 | 회원 `status` 미변경. 관리자 알림과 "제한 검토 대상" 표시까지만 |
| 알림 | `admin_safety_alerts` queue + 조회·확인 API + `AdminNav` badge |
| 멱등성 key | `match_penalty_events.related_report_id`, `admin_safety_alerts.trigger_report_id` |

자동 제한을 넣지 않은 이유는 세 가지다. `docs/19` 2장의 필수 요구가 "자동 알림"이고 자동
제한은 후보다. `AdminMemberService.act()`가 active 매칭 회원의 `SUSPEND`를 `409`로 거절하는데
자동 경로는 이 거절을 사용자에게 전달할 곳이 없다. 그리고 트리거가 관리자의 `RESOLVED`
클릭이라 동기 자동 정지는 관리자 클릭 하나를 줄이는 대신 의도하지 않은 정지 위험만 진다.

Scheduler를 두지 않은 이유는 기존 Scheduler가 모두 시간 경과로 조건이 바뀌는 대상을
처리하는데, 신고 누적은 관리자 행위로만 변하기 때문이다. 30일 window는 저장 카운터 없이
조회 시점에 계산해 batch를 없앴다.

### 구현 중 발견한 deadlock

`AdminReportService.changeStatus()`에 `members` 갱신을 넣자 기존 동시성 테스트
`RESOLVED와_REJECTED_동시성은_단일_terminal과_감사로그만_남긴다`에서 실제 deadlock이 났다.

- `RESOLVED`는 `members` FOR UPDATE 뒤 `reports`를 잠근다.
- `REJECTED`는 `reports`를 잠근 뒤 `admin_actions`를 INSERT하고, **그 FK 검사가 `members`
  row에 KEY SHARE lock을 건다.**
- 두 방향이 엇갈려 cycle이 생겼다.

그래서 `RESOLVED`뿐 아니라 **terminal 전이 전체**가 member를 먼저 잠그도록 고쳤다. FK가
암묵적으로 거는 row lock도 잠금 순서 설계에 포함해야 한다는 것이 이번 교훈이다.

### 구현 범위

- `V25__add_report_safety_automation.sql`: `match_penalty_events.related_report_id`와
  `manner_temperature_delta`, 부분 unique index, 30일 집계용 `reports` 복합 index,
  신규 `admin_safety_alerts` table
- `ReportConfirmationService`: 유효 판정 적용과 누적 집계, 임계 알림 생성
- `Member.increasePenaltyScore()`, `Member.decreaseMannerTemperature()` 하한 clamp
- `AdminReportService.changeStatus()` 잠금 순서 통일과 `RESOLVED` 경로 연결
- `AdminMemberService.act()`의 `SUSPEND`/`BAN` 시 미종료 알림 `CLOSED` 처리
- `GET /api/admin/safety-alerts`, `PUT /api/admin/safety-alerts/{id}/acknowledgement`
- 관리자 회원 상세에 `recentValidReportCount`, `safetyReviewRequired` 추가
- Frontend `AdminSafetyAlertSection`, `AdminNav` 미확인 badge, 회원 상세 표시

### 검증 결과

- Backend 전체 `test` 714건 중 실패 15건. **baseline 15건과 동일하며 신규 실패 0건이다.**
- `ReportSafetyAutomationIntegrationTest` PostgreSQL Testcontainers 20건 통과
  (권한 경계, 값 적용, 멱등성, 동시 판정, 사유별 3건 압축, 하한 clamp, 30일 window 경계,
  `ACTION_TAKEN` 집계, 알림 확인·종료, cursor pagination, deadlock 회귀).
- 그중 `신고_접수부터_판정_알림_확인_제재까지_실제_HTTP_경로로_동작한다`는 **SQL INSERT를
  전혀 쓰지 않고** 실제 endpoint만 사용한다.
  `POST /api/match-groups/{groupId}/reports` → `PATCH /api/admin/reports/{id}/status` →
  `GET /api/admin/safety-alerts` → `PUT .../acknowledgement` →
  `POST /api/admin/members/{id}/actions` 순서로 접수의 참여자·기간 검증까지 함께 태운다.
  접수만으로는 penalty·매너온도·알림이 생기지 않는 것도 같은 테스트에서 확인한다.
- `접수_API는_참여하지_않은_group과_기간이_지난_group을_거절한다`로 비참여자 `404`와
  30일 초과 `REPORT_WINDOW_EXPIRED` `409`를 확인했다.
- `MemberSafetyPenaltyTest` 7건 통과.
- 기존 `AdminReportIntegrationTest`의 "처리 전후 회원 점수가 변하지 않는다"는 확정 정책이
  의도적으로 바꾸는 동작이라, 매칭·회원 상태 불변 검증과 기각 시 불변 검증으로 나눠 갱신했다.
- Frontend 42 files/367 tests, `npx tsc --noEmit`, production/PWA build 성공.

### dev 실사용 검증 결과 (2026-09-04)

실제 dev DB와 실제 HTTP endpoint로 확인했다. 관리자 토큰을 발급해 API를 직접 호출했고,
화면은 브라우저로 확인했다.

| 단계 | penalty | 매너온도 | 30일 누적 | OPEN 알림 | REPORT cooldown |
| --- | ---: | ---: | ---: | ---: | ---: |
| 판정 전 | 18 | 36.50 | 1 | 0 | 0 |
| report 4 판정 | 23 | 31.50 | **1 유지** | 0 | 0 |
| report 5 판정 | 28 | 26.50 | 2 | 0 | 0 |
| report 6 판정 | 33 | 21.50 | 3 | **1** | 0 |

- report 4는 report 1과 같은 `(reporter, group)` 쌍이라 penalty·매너온도만 적용되고 누적은
  늘지 않았다. 사유별 압축이 실사용에서 확인됐다.
- 3건째에서 알림이 생성됐고 회원 상태는 `ACTIVE`를 유지했다. 자동 제한을 넣지 않은 정책대로다.
- penalty event는 판정 1건당 1행씩 `REPORT_CONFIRMED / +5 / -5.00`으로 저장됐다.
- 같은 신고 재판정은 `200`을 반환하지만 penalty event 3건과 점수 33이 그대로 유지됐다.
- 권한 경계는 일반 회원 `403`, 미인증 `401`, 없는 알림 `404`, 잘못된 status `400`이었다.
- 관리자 화면에서 `AdminNav` badge, 알림 섹션, 회원 상세의 누적 건수와 "이용 제한 검토 대상"
  표시를 확인했다.

### 검증 중 수정한 Frontend 동작

미확인 목록에서 알림을 `확인` 처리하면 badge는 즉시 줄어드는데 목록 행은 남아 있어
새로고침 전까지 filter와 어긋났다. 미확인 목록은 처리 대기 큐이므로 확인한 항목을 목록에서
즉시 제거하도록 바꿨다. `전체`·`확인` filter에서는 결과를 볼 수 있게 제자리 갱신을 유지한다.
- MATCH-09 교훈에 따라 `AdminReportsPage.test.tsx`로 안전 알림 섹션이 신고 목록 상태와
  무관하게 실제 화면에 붙어 있는지 확인했다. 섹션은 `state.status` 분기 밖에 있어 목록
  조회가 실패해도 노출된다.

### 검증 중 발견 — 과거 완료 만남을 신고할 화면 경로가 없다

수동 검증 절차를 만들다가 확인했다. 이번 작업 범위는 아니지만 기록해 둔다.

- `신고하기` 버튼은 `MatchRoomPage`에만 있고, 그 화면은
  `GET /api/matching/groups/me/current`에 의존한다.
- 그 쿼리는 `matching_group.status IN ('CONFIRMED','IN_PROGRESS')`와 활성 member만 반환한다.
- 반면 접수 API는 `COMPLETED`/`CANCELLED` 이후 30일까지 신고를 허용한다.
- 결과적으로 **만남이 끝난 뒤에는 신고 가능 기간이 남아 있어도 신고할 화면이 없다.**
  `docs/05_MATCHING_POLICY.md`의 "MatchRoom 신고 UI는 후속 범위" 항목과 이어진다.
- 수동 검증에서 서로 실제로 신고하려면 group이 `CONFIRMED`/`IN_PROGRESS`인 동안 해야 하고,
  임계 3건에 도달하려면 서로 다른 group 3개가 필요하다.

### 남은 일

- ~~dev 브라우저 수동 검증~~ (**완료**. 위 `dev 실사용 검증 결과` 참고)
- ~~`V25` 번호를 공유 dev DB `flyway_schema_history`에서 재확인~~ (**확인 완료**.
  2026-09-02 16:33:26에 `V25`가 `success=t`로 dev DB에 적용됐다. V24가 최신이었으므로
  번호 충돌은 없었다. local profile의 `.env`가 `127.0.0.1:15432` SSH 터널로 dev DB를
  가리키기 때문에 backend 실행 시 Flyway가 적용했다.)
- 후속 항목 2건을 `docs/19` 4.8·4.9로 분리했다. 4.8 회원 제재 사유·기간 통보는 **현재
  관리자가 수동 정지해도 사용자가 이유와 기간을 알 수 없다**는 문제이고, 4.9는 매너온도
  회복과 30도 매칭 제한이다.

## [10-B MATCH-09] 매칭 실패 → 솔로 코스 전환 연결

상태: 구현·Frontend 자동 검증·dev 수동 검증 완료(Frontend 전용). Backend·migration 변경 없음

### 착수 전 범위 재산정

기획서 `MATCH-09`를 그대로 새 작업으로 잡으면 `[10-A 후속 3·4·5]`와 중복되므로, 착수 전에 남은
범위를 다시 산정했다. 결과는 `docs/26_MATCH_FAILURE_SOLO_COURSE_LINK_DESIGN.md`에 정리했다.

코스 생성 API(`GET /api/festivals/{id}/solo-course`)와 `SoloCoursePage` 타임라인은 이미 완료
상태였고, 실제 공백은 **전환 트리거**와 **문서 정합성** 두 가지였다.

### 발견 — 종료 카드가 화면에 뜨지 않았다

`[10-A 후속 5]`가 넣은 솔로 코스 링크는 매칭 실패의 대표 상황에서 노출되지 않았다.

- `deriveMatchingState()`가 cooldown이 없는 terminal pool을 곧바로 `IDLE`로 접었고, 링크가 붙어
  있는 `CancelledCard`는 `CANCELLED`/`EXPIRED`/`COOLDOWN`에서만 렌더됐다. 그 세 상태는 cooldown이
  active일 때만 만들어진다.
- 그런데 60초 탐색 만료는 `MatchPoolRepository.expireWaitingPools()`가 `EXPIRED`로만 바꾸고
  cooldown을 만들지 않는다(`docs/05_MATCHING_POLICY.md`). 결과적으로 링크가 실제로 노출되는 건
  `TIMEOUT`/`REJECT`/`POOL_CANCEL` cooldown이 걸린 경우뿐이었고, **"60초 안에 상대를 못 찾음"에서는
  사용자가 종료 카드를 한 번도 보지 못하고 신청 화면으로 되돌아갔다.**
- 기존 링크 테스트는 `MatchBody({ status: 'EXPIRED' })`를 직접 호출해 `deriveMatchingState`를
  우회하므로 이 구멍을 잡지 못했다.

### 수정

- `deriveMatchingState(snapshot, sessionObservedPoolId)`로 시그니처를 확장했다. **이 세션에서
  진행 상태를 실제로 관측한 pool**이 종료된 경우에만 cooldown 없이도 종료 상태를 반환하고, 새
  mount에서 발견한 과거 terminal pool은 기존대로 `IDLE`로 돌려보낸다. 단순 롤백이 아니라
  `[10-A 후속 2]`의 "종료 화면 고착" 수정을 유지하기 위한 조건이다.
- 관측 기록은 순수 함수 `observedActivePoolId(snapshot, current)`로 계산해 hook의 ref에 보관한다.
  `WAITING`/`LOCKED`/`PROPOSED`는 그대로 기록하고, `MATCHED`는 group이 실제로 있을 때만 기록한다
  (group 없이 남은 `MATCHED`는 이미 취소된 과거 이력이라, 이것까지 세면 새 mount에서 종료 카드가
  다시 뜬다).
- `CancelledCard`를 `다시 신청하기` / `솔로 코스 추천 보기` 두 버튼 구조로 바꿨다. 문구는 이동
  대상 화면 헤더(`솔로 코스 추천`)에 맞췄고, `festivalId`가 없으면 솔로 코스 버튼은 숨긴다.

### 확정 정책 — 버튼 2개, 선택은 사용자에게

"솔로 코스로 유도할지 재매칭을 기다리게 할지"를 서버나 화면이 판단하지 않는다. 잔여 cooldown이나
체크인 잔여시간 기준의 임계값 유도 규칙은 검토했으나 채택하지 않았다. 기존 cooldown 동작이 이미
같은 일을 하기 때문이다 — cooldown이 있으면 재신청 버튼이 잠기고 카운트다운이 뜨므로 솔로 코스가
자연스럽게 유일한 선택지가 되고, cooldown이 없으면 둘 다 열린다. `docs/05_MATCHING_POLICY.md`
`매칭 실패 후 솔로 코스 전환 정책`에 반영했다.

재매칭 가능 시점 안내(R7) 자체는 이미 구현되어 있었다(`cooldown.remainingSeconds` 카운트다운,
버튼 비활성화, 만료 시 자동 `refresh()`, `serverNow` 기준 offset 보정). 추가 구현은 없다.

### 전환 이력을 저장하지 않기로 한 결정

`solo_courses`는 V4에 있고 `source_attempt_id` FK까지 잡혀 있지만 backend가 아무것도 쓰지 않는다
(Entity·Repository 없음, dev DB 0건). 검토 결과 **이번 범위에서 저장하지 않기로 했다.**

- 저장된 코스를 다시 읽어가는 기능이 없다. 저장은 순수하게 전환율 통계 용도인데 MVP 단계에서 그
  숫자를 볼 화면도 볼 사람도 없다.
- 테이블과 FK가 그대로 남아 있어 나중에 도입해도 새 migration 없이 Entity와 저장 시점만 추가하면
  된다. 다만 **전환 이력은 소급 생성이 불가능**하므로 도입 시점부터의 데이터만 쌓인다.
- `recommendation_click_logs`도 함께 제외했다. CHECK 제약이
  `tour_place_id IS NOT NULL OR solo_course_id IS NOT NULL`이라 `solo_courses` row 없이는 전환을
  기록할 수단이 없어 두 테이블은 함께 살거나 함께 빠진다.
- `docs/11_DATABASE_DESIGN.md`의 세 테이블 MVP 표기를 "필수"에서 "V4 선반영, MVP 범위에서 미사용"
  으로 정정하고 재도입 조건을 남겼다.

### 문서 정정 — "솔로 45분 코스"

`docs/11_DATABASE_DESIGN.md`의 "45분"은 구현되지 않았다. 실제 값은 `SoloCourseStayPolicy`의
`HALF` 240분 / `FULL` 480분이다. 45분은 현재 상수 조합으로 성립하지 않는다 — 체류시간 최소값이
문화시설 45분이고 `walkMinutes()`가 최소 1분을 보장해 `1 + 45 = 46 > 45`가 되므로 어떤 후보도
예산을 통과하지 못하고 항상 빈 코스가 나온다.

### 검증

- Frontend 전체 Vitest 40 files/352 tests 통과(신규 10건 포함). `npx tsc --noEmit`,
  production/PWA `generateSW` build 성공.
- 신규 테스트는 `deriveMatchingState`를 실제로 통과한다. 기존 테스트가 `MatchBody`를 직접 호출해
  놓친 구멍을 덮는 것이 목적이다.
- Backend 소스, API 계약, DB schema, Flyway migration은 변경하지 않았다.
- **dev 수동 검증 완료.** 네 경로를 모두 확인했다.
  1. 신청 후 60초 탐색 만료 → 종료 카드가 뜨고 두 버튼이 모두 활성이다.
     수정 전에는 이 경우 카드 자체가 뜨지 않았다.
  2. 제안 거절(`REJECT`) → cooldown 상태에서 `솔로 코스 추천 보기`가 활성으로 유지된다.
  3. 종료 카드를 본 뒤 다른 화면에 갔다 돌아오면 카드가 다시 뜨지 않고 신청 화면으로 리셋된다.
     `[10-A 후속 2]` 종료 화면 고착 수정이 유지된다.
  4. `솔로 코스 추천 보기` → `/solo-course`가 기준 축제로 코스를 표시한다.

### 이번 범위에서 제외

- 전환 이력 저장 3개 테이블
- 코스 알고리즘 파라미터 조정(`MAX_HOP_METERS`, `MAX_STOPS`, 체류시간 추정치) — `docs/23` 담당자 범위
- `[10-A 후속 3·4]`의 코스 품질 수동 검증 — 만든 담당자가 판단할 영역

## [10-A 후속 10] 홈 체크인 축제 우선, 로딩 표시 통일, 필터 UI 개선

상태: 구현 완료(Frontend 전용). Backend 소스 변경 없음. 두 브라우저 수동 검증 전

사용자 피드백 7건을 반영했다.

1. **홈 히어로에 체크인한 축제 우선.** 체크인 > GPS 최근접 > 폴백(진행중 ?? 예정) 순으로 정한다.
   세 근거가 각각 비동기로 도착해 순서가 일정하지 않으므로, 늦게 온 결과가 더 확실한 근거를
   덮어쓰지 않도록 `shouldReplaceHero(current, next)` 우선순위 비교를 두고 컴포넌트 밖 순수
   함수로 분리해 테스트했다. 체크인 응답에는 축제 id/이름만 있어 카드에 필요한 기간·이미지는
   `GET /api/festivals/{id}`로 채우며(목록 20건 안에 없을 수 있어 목록에서 찾지 않는다),
   이를 위해 `mapFestivalDetailToFestival`을 추가했다. 배지 문구도 근거에 따라
   "체크인한 OO의 축제" / "내 위치에서 가까운 OO의 축제"로 나뉜다.
2. **축제 목록의 "매칭 가능" 필터 칩 제거.** Backend의 `matchableOnly` 파라미터와 테스트는
   그대로 두었다 — 동작이 검증돼 있어 다시 노출할 때 배선만 하면 되고, 지우는 편이 변경 폭이
   더 크기 때문이다. 현재 이 파라미터를 보내는 화면은 없다.
3. **다른 축제 체크인 시 기존 체크인 해제**는 이미 구현돼 있어 변경하지 않았다.
   `FestivalCheckinService.checkIn()`이 같은 회원의 기존 ACTIVE 체크인(다른 축제 포함)을 전부
   취소하고 축제별 `FestivalCheckinCancelledEvent`를 발행하며, 같은 축제 재체크인 시 부분 unique
   index 위반을 피하려 취소 UPDATE를 명시적으로 flush한다.
4. **체크인 화면의 막다른 안내 제거.** `checkInNavigationTarget(null)`이 `/check-in` 대신
   `/spots`를 반환하도록 바꿨고, `CheckInPage`도 `festivalId` 없이 진입하면(직접 URL·새로고침)
   `/spots`로 `replace` 이동한다. 어느 축제인지 모르면 그 화면이 할 수 있는 일이 없어 안내가
   한 단계 낭비였다.
5. **로딩 표시를 문구에서 애니메이션으로.** `components/common/Spinner.tsx`(신규)에 `Spinner`,
   `LoadingState`(영역 전체), `LoadingMore`(무한스크롤 하단)를 두고, 탐색·축제 상세·차단 목록·
   관리자 3개 화면의 텍스트 전용 로딩을 교체했다. `PrimaryButton`에 `pending` prop을 추가해
   처리 중 버튼에도 스피너가 붙는다(체크인 화면·축제 상세 체크인 버튼 적용). 문구만 바뀌면
   눌린 건지 멈춘 건지 구분이 안 되던 문제를 없앴다. 애니메이션은 `aria-hidden`이고 안내는
   문구가 담당하며 `role="status"`로 감쌌다.
6. **필터 셀렉트를 바텀시트로 교체.** 기존 `FilterSelect`는 네이티브 `<select>`를 투명하게
   덮는 방식이라 열었을 때 목록이 OS 기본 스타일로 떠 앱 디자인과 따로 놀았다. 앱이 직접 그리는
   바텀시트(손잡이·선택 체크·coral 강조)로 바꿔 팔레트와 둥근 모서리를 맞췄다. Escape·바깥
   클릭으로 닫히고 열릴 때 포커스가 이동한다.
7. 기존 정책과 충돌하는 항목은 없었다. 1번은 오히려 `docs/22` 3장의 "내 위치 기반 추천은
   체크인한 축제 기준" 방향과 일치하며, 좌표를 서버로 보내지 않는다는 제약도 그대로 지켜진다.

- 검증: `tsc -b` 통과, vitest **278개 전부 통과**(`shouldReplaceHero` 5개 추가),
  `npm run build` 성공, backend `compileJava`/`compileTestJava` 통과.
- 이번 작업 중 dev DB로 가던 SSH 터널이 끊겨 실서버 재검증은 하지 못했다. 다만 이 작업에서
  backend 소스는 변경하지 않았고, API 파라미터 동작은 `[10-A 후속 9]`에서 실서버로 이미 확인했다.

## [10-A 후속 9] 축제·관광지 목록 지역·정렬·일정 필터와 무한스크롤, 홈 최근접 축제

상태: 구현 완료. Docker 미설치로 Testcontainers 통합 테스트 미실행, dev 배포·두 브라우저 수동 검증 전

- 사전 설계는 `docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md`로 정리했다. `docs/13` 3.5/7장에서
  보류했던 `region`/`sort` 파라미터를 이번에 확정해 구현했다.
- **확정된 정책 제약: GPS 좌표를 서버로 전송하지 않는다.** `docs/06_SECURITY_POLICY.md`는 갱신하지
  않았다. 그 결과 관광지 목록의 "내 위치 기준 / 반경 10·50·100km / 가까운순·먼순"은 범위에서
  제외했고(4,020건이라 서버 계산이 불가피 → 좌표 전송 필요), 관광지는 **지역 선택 단일 모드**가
  됐다. 기존 체크인의 좌표 전송은 정책이 이미 허용한 용도라 그대로 유지했다.
- 홈 화면 최근접 축제는 **클라이언트 계산**으로 구현했다. 서버는 목록 응답에 `mapX`/`mapY`만
  추가하고, 브라우저가 `utils/geo.ts`의 haversine으로 최근접 축제를 고른다. 좌표가 기기를 벗어나지
  않으므로 정책·신고 이슈가 없다. ACTIVE 축제가 15건이라 이 방식이 성립한다.
  - 폴백 순서: GPS 허용 → 최근접 축제 / 권한거부·타임아웃·미지원·좌표없음 → 기존 로직(진행중
    첫 번째 ?? 예정 첫 번째). **권한을 거부한 사용자의 화면은 이전과 완전히 동일하다.**
  - 앱 자체 동의 모달은 두지 않았다 — 좌표 전송이 없어 법적 동의 대상이 아니고 브라우저
    권한창이 이미 그 역할을 한다. `localStorage` 신규 사용도 피했다.
  - `HomePage.tsx`에 하드코딩돼 있던 `전북 전주시의 축제`(데이터는 강원인데 전북으로 표기돼
    있던 장식용 버튼)를 실제 시군구명으로 교체했다. 폴백 상태에서는 기준 지역이 없어 숨긴다.
- **지역 단위는 도가 아니라 시군구다.** dev DB 실측 결과 `festivals.area_code`는 33/34건이 `51`,
  `tour_places`의 `lDongRegnCd`는 4,020건 전부 `51`(강원)이라 도 단위 선택지가 1개뿐이다. 시군구는
  18개로 나뉘어 실제로 의미가 있다. 전국 확장은 별도 과제로 남겼다(설계 문서 10장).
- `V23__add_tour_place_region_codes.sql`: `tour_places`에 `area_code`/`sigungu_code` 추가 +
  `raw_data`의 `lDongRegnCd`/`lDongSignguCd`로 백필 + `(status, sigungu_code)` 인덱스.
  **TourAPI 재호출 없이 기존 4,020건을 그대로 채운다.** `TourPlaceSyncData`/`TourPlaceSyncMapper`도
  앞으로 두 컬럼을 저장하도록 고쳤다(`SearchTourPlaceItem`은 이미 값을 노출하는데 버려지고 있었다).
- API 변경(추가만, 기존 필드·기본값은 그대로):
  - `GET /api/festivals`에 `sigunguCode`, `sort`(`START_DATE_ASC`/`END_DATE_ASC`/`RECENTLY_ADDED`),
    `schedule`(`ALL`/`ONGOING`/`THIS_WEEKEND`/`THIS_MONTH`), `matchableOnly` 추가. 응답 `items[]`에
    `mapX`/`mapY` 추가.
  - `GET /api/spots`에 `sigunguCode`, `sort`(`TITLE_ASC`/`RECENTLY_ADDED`) 추가.
  - `GET /api/festivals/regions`, `GET /api/spots/regions` 신규 — **실제 데이터에 존재하는 시군구만**
    건수와 함께 반환한다. 시군구 이름이 DB에 없어(원본 raw_data에도 없다) 그룹별 대표 주소의 두
    번째 토큰에서 뽑는다(`global/region/RegionNameResolver`). 시도 표기가 `강원특별자치도`/`강원`으로
    섞여 있어 첫 토큰은 쓰지 않는다.
  - 파라미터를 아무것도 안 보내면 기존과 동일한 응답이 나온다(정렬 기본값 유지).
- 추가 필터는 **일정**과 **매칭 가능한 축제만** 2개를 채택했다. 축제 카테고리는
  `raw_data`의 `cat1`/`cat2`/`cat3`가 33건 전부 `null`이라 불가, 무료/유료·실내외는 `detailIntro2`에만
  있고 DB 미저장이라 불가로 판정했다.
- 무한스크롤(20개 단위)은 **백엔드 변경 없이** 구현했다. 응답에 `hasNext`가 이미 있는데 화면에서
  쓰지 않고 있었을 뿐이다. `hooks/useInfiniteList.ts`(누적·리셋·중복요청 차단) +
  `hooks/useInfiniteScrollSentinel.ts`(IntersectionObserver)로 분리했다. 프론트에 무한스크롤 선례가
  없어 신규 패턴이다.
  - 필터·검색·정렬·세그먼트가 바뀌면 `page=0`으로 리셋하고 누적 배열을 비운다.
  - offset 페이징의 중복/누락 위험은 동기화 주기가 6~12시간이라 감수하고 문서에 남겼다.
- `ExploreListPage`에서 동작하지 않던 표시용 칩을 정리했다. 장식용 `<span>` "가까운 순"과
  관광지의 "현재 위치"·"거리" 칩을 제거하고, 실제 동작하는 지역·일정·정렬·매칭가능 필터로
  교체했다(`components/explore/FilterSelect.tsx` 신규). 기존 `getList(0, 100)` 고정 조회도 없앴다.
- 정렬 키에는 항상 `id`를 tie-breaker로 붙였다. 무한스크롤에서 정렬이 불안정하면 페이지 경계에서
  항목이 중복·누락된다.
- `FestivalRepository.findVisibleFestivals`의 `matchableOnly`는 boolean이 아니라 `int`(0/1)로
  넘긴다. JPQL에서 boolean 파라미터를 리터럴과 비교할 때 타입 추론이 흔들릴 수 있어, `keyword`에서
  이미 겪은 것과 같은 종류의 문제를 피했다.
- 테스트: `FestivalScheduleFilterTest`(주말·월말·윤년 경계), `RegionNameResolverTest`,
  `geo.test.ts`(서버 haversine과 값 대조), `homeFestival.test.ts`(폴백 4분기),
  `useInfiniteList.test.ts`(누적·리셋·중복차단·실패유지) 신규. 기존 서비스/컨트롤러 테스트는 새
  시그니처에 맞춰 수정하고 기본값 회귀 테스트를 추가했다.
  - Frontend는 `tsc -b` 통과, vitest 273개 전부 통과.
  - Backend 단위 테스트는 통과. **Testcontainers 통합 테스트는 이 PC에 Docker Desktop이 설치돼
    있지 않아 실행하지 못했다** — `V23` 백필과 신규 JPQL(지역 필터, `exists` 서브쿼리, 집계
    constructor expression)은 실제 PostgreSQL 검증이 남아 있다.
  - `FestivalControllerTest`/`TourPlaceControllerTest`는 **이번 작업 전부터 깨져 있다**(`@WebMvcTest`
    컨텍스트가 `JwtProvider` 빈을 못 찾음). HEAD에서도 동일하게 실패하는 것을 worktree로 확인했다.
    이번 변경과 무관하며 별도 수정 과제다.
- **구현 중 발견·수정한 버그: `LocalDate.MAX`로 인한 `GET /api/festivals` 500.**
  일정 필터 `ALL`의 상한을 `LocalDate.MAX`(+999999999-12-31)로 뒀더니 PostgreSQL이
  `ERROR: date out of range: "169104628-12-09 BC +09"`(SQLState 22008)로 거부했다. 이 프로젝트는
  `hibernate.jdbc.time_zone: Asia/Seoul`로 타임존을 변환하는데, 최대 연도에 +9시간이 더해지며
  오버플로가 나 BC 날짜로 뒤집히기 때문이다. `FestivalScheduleFilter.MAX_SCHEDULE_DATE`
  (`9999-12-31`) 상수로 교체했다.
  - 처음에 순수 JDBC로 `LocalDate.MAX` 바인딩을 시험했을 때는 통과했는데, 그 시험이 Hibernate의
    타임존 변환 경로를 우회해서 **잘못된 검증**이었다. 이후 앱을 별도 포트(8099)로 띄워 실제
    스택트레이스로 원인을 확정했다.
- 실제 서버(로컬 backend + 터널 dev DB) 수동 검증 완료: 파라미터 없는 기본 조회, `sort` 3종,
  `schedule` 4종, `matchableOnly`, `sigunguCode`, 조합 조회, `keyword`, 관광지 `sort`/`sigunguCode`/
  `contentTypeId` 조합, 두 `regions` 엔드포인트가 모두 200이다. 잘못된 enum 값은 400을 반환한다.
  `GET /api/spots/regions?contentTypeId=39`가 `강릉시 542` 등을 정확히 반환해 **V23 백필과 지역명
  추출이 실데이터에서 동작함**을 확인했다.
- 이번 범위에서 제외: 전국 동기화 확장(설계 문서 10.1의 관광지 INACTIVE 스윕 지역 범위화가 선행
  필요 — 현재 구조로는 다중 지역 동기화 시 다른 도의 데이터가 전부 INACTIVE로 뒤집힌다),
  좌표 이상치 4건 sync 검증, 일정 필터의 직접 날짜 지정 UI.

## [10-A 후속 8] 홈 화면 첫 렌더 차단 제거

상태: 구현 완료

- `HomePage`가 프로필과 축제 목록을 `Promise.all`로 묶어, 둘 중 느린 쪽이 끝날 때까지 화면에
  아무것도 그려지지 않았다. 한쪽이 실패하면 다른 쪽 데이터까지 버려졌다.
- 두 요청을 독립 체인으로 분리해 각 응답이 도착하는 대로 렌더한다. 인사말 닉네임은 '여행자님'
  폴백이 있어 프로필이 늦어도 화면이 성립한다. 프로필 조회 실패 시 축제 목록이 통째로 안 보이던
  문제도 함께 해결됐다.
- 배경: dev DB 실측 결과 이 프로젝트의 로컬 개발 구성(backend 로컬 + SSH 터널로 dev DB)에서는
  쿼리 1건당 왕복이 약 150ms다. 서버 실행 시간은 2ms 미만이라 관측 지연의 대부분이 네트워크
  왕복이고, 따라서 **왕복 횟수와 직렬 구조**가 체감을 지배한다. Tier A(후속 7)의 bounding box
  최적화로 주변 관광지 조회는 1,596ms → 177ms로 줄었으나(4,020건 전송 → 38건), 목록 조회는
  261ms → 130ms 수준이라 체감이 작았다.
- `nearby-spots`의 직렬 요청은 화면 최하단 섹션을 채우므로 첫 화면 체감에 영향이 작아 그대로 뒀다.
  없애려면 신규 집계 API와 백엔드에 화면 status 로직 복제가 필요해 비용 대비 효과가 낮다.

## [10-A 후속 7] festivals/tour_places 목록·반경 조회 성능 개선 1차(Tier A)

상태: 구현 완료. 로컬 JDK 17 부재로 컴파일·테스트 실행 미확인, dev DB EXPLAIN 비교와 두 브라우저 수동 검증 전

- 배경: `festivals` 목록(`GET /api/festivals`, `HomePage`/`ExploreListPage`가 사용)이 느리다는
  문제 제기로 코드 분석을 진행했다. 원인은 캐시 부재보다 먼저 (1) 목록 조회가 화면에 쓰지 않는
  `raw_data` JSONB까지 매번 전체 엔티티로 읽어오고, (2) `HomePage` 진입 시 함께 호출되는
  `nearby-spots`/`nearby-festivals`가 반경 필터 없이 `tour_places`/`festivals` 전체를 앱 메모리로
  가져와 haversine 계산 후 필터링하며, (3) 키워드 검색이 앞뒤 `%` LIKE라 인덱스를 타지 못하는 데
  있었다. 캐시(Tier B, Redis 미사용 인프로세스 캐시)는 이번 범위에서 다루지 않았다.
- `FestivalRepository.findVisibleFestivals`/`TourPlaceRepository.findVisiblePlaces`를 JPQL
  `select new ...(...)` 생성자 표현식으로 바꿔 `raw_data`·좌표 등 목록에 쓰이지 않는 컬럼을 읽지
  않도록 했다. Festival 쪽은 이미지가 별도 테이블이라 신규 프로젝션 `FestivalSummary`(`festival`
  패키지 dto, JPA 엔티티 아님)를 목록/이미지 매핑 전용으로만 쓰고, `FestivalListItemResponse` 등
  공개 API 응답 계약은 변경하지 않았다. TourPlace 쪽은 이미지 URL이 자체 컬럼이라 기존
  `TourPlaceListItemResponse`로 바로 프로젝션했다.
- `FestivalQueryService.getNearbyTourPlaces`/`TourPlaceQueryService.getNearbyFestivals`가
  전체 테이블을 가져오던 `findAllVisibleWithCoordinates` 대신, 중심점과 반경으로 계산한 위경도
  bounding box로 후보를 먼저 좁히는 `findAllVisibleWithinBoundingBox`를 쓰도록 바꿨다. 실제
  반경 판정·정렬은 그대로 haversine으로 다시 계산해 결과가 달라지지 않는다(순수 성능 최적화).
  bounding box 계산은 `GeoDistanceCalculator.boundingBox()`로 분리했다. `SoloCourseService`가
  쓰는 기존 `TourPlaceRepository.findAllVisibleWithCoordinates`(반경 제한이 없는 후보 조회)는
  동작이 달라질 수 있어 이번 범위에서 건드리지 않았다.
- `V22__add_festival_tourplace_query_indexes.sql`을 추가했다(기존 V1~V21은 수정하지 않음).
  `pg_trgm` 확장, `festivals(status, event_end_date)`/`festivals(status, map_x, map_y)`/
  `tour_places(status, map_x, map_y)` 복합 인덱스, `festivals`/`tour_places` 제목 GIN trigram
  인덱스를 추가했다. 기존 단일 컬럼 인덱스(`idx_festivals_status` 등)는 다른 조회(만료 처리
  batch)에서 계속 쓰이므로 삭제하지 않았다.
- 기존 `FestivalQueryServiceTest`/`TourPlaceQueryServiceTest`/`FestivalSyncWriterIntegrationTest`를
  새 반환 타입에 맞춰 수정했고, 신규 `GeoDistanceCalculatorTest`(bounding box 계산),
  `TourPlaceRepositoryIntegrationTest`(신규 파일), 기존 `FestivalRepositoryIntegrationTest`에
  projection·bounding box PostgreSQL 통합 테스트를 추가했다.
- 이 Windows 환경에 JDK 17이 없어(이전 후속 6과 같은 제약) `compileJava`/`compileTestJava`/테스트
  실행을 확인하지 못했다. repository 반환 타입을 바꾸는 범위가 넓은 편이라 모든 호출부·기존 테스트를
  grep으로 전수 확인하고 수정했지만, 실제 컴파일·PostgreSQL 통합 테스트 통과는 JDK 17이 있는
  환경에서 별도로 확인이 필요하다.
- 캐시(Tier B), 전체 EXPLAIN ANALYZE 비교, dev DB·두 브라우저 체감 성능 확인은 이번 범위에서
  제외했다.

## [10-A 후속 6] 관리자 만남 장소 관리 화면과 0건 축제 자동 백필

상태: 구현 완료. Frontend 자동 검증 완료, Backend는 로컬 JDK 17 부재로 컴파일·테스트 실행 미확인, 두 브라우저·dev DB 수동 검증 전

- 사전 분석·설계는 `docs/24_ADMIN_MEETING_POINT_MANAGEMENT_DESIGN.md`로 정리했다. `[10-매칭 24차]`에서
  구현된 만남 장소 관리 API(`AdminFestivalMeetingPointController`)는 그대로 재사용하고, 이번 범위는
  Frontend 화면 추가와 백필 로직 추가로 한정했다.
- Backend: 만남 장소가 0건인 `ACTIVE` 축제를 찾는 `FestivalRepository.findAllByStatusWithoutMeetingPoint()`와
  신규 `FestivalMeetingPointBackfillService`를 추가했다. 기준은 "행이 0건"이며 "`ACTIVE` 행이 0건"이
  아니다 — 관리자가 이미 등록해둔 장소가 전부 `INACTIVE`라도 다시 시딩하지 않는다(관리자 조정값을
  스케줄러가 덮어쓰지 않는다는 원칙 유지). `FestivalSyncService.synchronizeFestivals()`가
  `writer.upsert(...)` 다음 단계로 이 서비스를 호출하며, 백필이 실패해도 축제 동기화 자체는 성공으로
  처리한다(다음 실행에서 재시도). `FestivalSyncResult`에 `seededMeetingPointCount`를 추가해
  `FestivalSyncScheduler` 로그에 노출했다. 좌표가 없는 축제는 skip하고 `WARN` 로그만 남긴다.
  Flyway migration은 추가하지 않았다(기존 `V15` 재사용).
- Frontend: 관리자 화면 4곳(대시보드/신고 관리/회원 관리/만남 장소 관리)에 공통 상단 메뉴바
  `AdminNav`를 추가하고, 각 페이지의 임시 되돌아가기 링크를 여기로 통합했다. `AdminDashboardPage`의
  기존 바로가기 카드(신고 검토/회원 조회·제재)는 메뉴바와 중복돼 제거했다. 대시보드에만 있던
  "meet·or·solo + 화면별 부제" 최상단 타이틀도 공통 `AdminHeader` 컴포넌트로 분리해 4개 화면 모두
  같은 형태로 보이도록 통일했다(각 페이지는 `title`만 다르게 전달).
- 신규 `/admin/meeting-points` 화면(`AdminMeetingPointsPage`)을 추가했다. 기존 공개
  `GET /api/festivals` 검색으로 축제를 고르고, 선택한 축제의 만남 장소 목록·등록·수정·활성/비활성
  전환을 기존 admin API 그대로 사용해 제공한다. 신규 API는 추가하지 않았다. 마지막 `ACTIVE` 장소를
  비활성화하려는 경우 클라이언트 confirm 경고만 표시하며(서버 차단은 없음), 새 장소는 계약대로
  `INACTIVE`로 등록되므로 등록 후 활성화가 필요함을 안내한다.
- Backend는 이 Windows 환경에 JDK 17이 설치돼 있지 않아(JDK 8만 존재) Gradle 9.5.1 실행 자체가
  막혀 `compileJava`/`compileTestJava`/테스트 실행을 확인하지 못했다. IntelliJ 번들 JBR 25로
  Gradle 구동은 됐지만 컴파일 툴체인(JDK 17) auto-download 저장소가 설정돼 있지 않아 같은 문제로
  막혔다. 작성한 `FestivalMeetingPointBackfillServiceTest`, 수정한 `FestivalSyncServiceTest`,
  `FestivalSyncSchedulerTest`, 신규 `FestivalRepositoryIntegrationTest`는 코드 리뷰 수준으로만
  작성했고 실제 컴파일·통과 여부는 별도 환경(JDK 17 설치)에서 확인이 필요하다.
- Frontend는 전체 Vitest 32 files/242 tests(신규 `AdminNav.test.ts`, `AdminHeader.test.ts`,
  `adminMeetingPoints.test.ts`, `useAdminMeetingPoints.test.ts`, `AdminMeetingPointsPage.test.ts`
  포함), `npx tsc -b`, production/PWA build가 모두 성공했다.
- 두 브라우저·dev DB 수동 검증, 카카오 로컬 API 연동 장소 검색 UI, 만남 장소 hard delete API는
  이번 범위에서 제외했다(`docs/24` 7장).

## [10-A 후속 5] 솔로 코스 도보시간 표시 수정과 매칭 실패 화면 연결

상태: 구현·Frontend 자동 검증 완료, 두 브라우저 dev 수동 검증 전

- `SoloCoursePage`의 스톱 카드가 백엔드가 계산해 응답에 넣어준 `stop.walkMinutesFromPrevious`를
  쓰지 않고, `stop.distanceFromPreviousMeters`를 프론트 `formatWalkMinutesLabel`(반올림)로
  다시 계산해 표시하고 있었다. 백엔드 `SoloCourseStayPolicy.walkMinutes()`는 올림(`ceil`)을
  쓰기 때문에 같은 구간인데도 상단 "예상 소요 약 N시간(도보 M분 포함)" 합계와 카드별 개별
  도보시간이 서로 다르게 보일 수 있었다. 카드가 `stop.walkMinutesFromPrevious`를 그대로 쓰도록
  고쳐 두 값의 합이 항상 일치하게 했다. Backend 계산 로직 자체는 변경하지 않았다.
- 매칭이 실패로 종료된 화면(`CANCELLED`/`EXPIRED`/`COOLDOWN` — 예: 60초 탐색 시간 안에 상대를
  못 찾아 `EXPIRED`로 끝난 경우)에 "대신 주변 코스 보러가기" 링크를 추가했다. 현재 회원의
  `festivalId`(체크인 우선, 없으면 navigation state 순서로 이미 계산돼 있던 값)를 `state`로
  실어 `/solo-course`로 이동한다. `festivalId`가 없으면 링크 자체를 숨긴다.
- Frontend 전체 Vitest 25 files/216 tests(신규 2건 포함), `npx tsc --noEmit`,
  production/PWA build가 성공했다. Backend는 변경하지 않았다.
- 두 브라우저·dev DB 수동 검증은 아직 실행하지 않았다.

## [10-A 후속 4] /solo-course 최근접 이웃 기반 코스(동선) 1차

상태: 구현·Backend/Frontend 자동 검증 완료, 두 브라우저 dev 수동 검증 전

- `[10-A 후속 3]`에서 만든 "거리순 목록"을 실제로 걸을 수 있는 순서가 있는 "코스"로 확장했다.
  사전 분석·설계는 `docs/23_SOLO_COURSE_ITINERARY_DESIGN.md`로 정리했고, 초안 수치 그대로
  진행하기로 결정했다.
- 축제 좌표를 시작점으로 "현재 위치에서 가장 가까운 미방문 후보"를 순서대로 고르는 greedy
  nearest-neighbor로 스톱 순서를 정한다. 도보시간은 기존 `formatWalkMinutesLabel`과 동일하게
  도보 속도 약 4km/h(67m/분)로 추정하고, 체류시간은 실측 데이터가 없어 `contentTypeId`별 고정
  추정치(관광지 60분/문화시설 45분/액티비티 90분/맛집 50분)를 쓴다.
- `HALF`(240분)/`FULL`(480분) 예산을 넘기기 직전까지 후보를 채우고, 한 번의 이동이
  `MAX_HOP_METERS`(1,500m)를 넘거나 최대 스톱 수(`MAX_STOPS`=6)에 도달하면 멈춘다.
- 순수 거리순으로만 고르면 같은 카테고리(특히 맛집/카페)가 연속으로 뽑히는 문제가 있어, 가장
  가까운 후보가 직전 스톱과 같은 카테고리면 `DIVERSITY_TOLERANCE`(1.5배) 이내에 다른 카테고리
  대안이 있는지 찾아 대신 선택하는 경량 규칙을 1차에 포함했다. 대안이 없으면 원래 가장 가까운
  후보를 그대로 선택해, 다양성 때문에 억지로 먼 곳까지 끌고 가지 않는다.
- 신규 `GET /api/festivals/{id}/solo-course?type=HALF|FULL`을 추가했다. 기존 `nearby-spots`가
  쓰는 후보 조회(`TourPlaceRepository.findAllVisibleWithCoordinates`)를 그대로 재사용해 새
  repository 쿼리, Flyway migration, TourAPI 외부 호출을 추가하지 않았다.
- 정책 상수(`MAX_HOP_METERS`/`MAX_STOPS`/체류시간표/`DIVERSITY_TOLERANCE`/예산)는
  `SoloCourseStayPolicy`로 분리했다.
- Frontend `SoloCoursePage`에 반나절/하루 토글과 순서·도보시간이 보이는 타임라인 UI를 다시
  넣었다(예전 mock과 비슷한 모양이지만 전부 실제 계산값). 코스가 비어 있으면 안내 문구를 표시한다.
- Backend 신규 `SoloCourseStayPolicyTest`, `SoloCourseServiceTest`(최근접 이웃 순서가 단순
  거리순과 달라지는 경우, hop·예산·최대 스톱 경계, 카테고리 연속 방지 규칙의 대안 선택·폴백·
  첫 스톱 예외)와 `FestivalControllerTest` 신규 케이스가 통과했다. Backend 전체 336건 중
  이번 변경과 무관한 기존 pgvector Docker 이미지 fetch 실패 21건을 제외하고 전부 통과했고
  `./gradlew build -x test`도 성공했다.
- Frontend 전체 Vitest 25 files/214 tests, `npx tsc --noEmit`, production/PWA build가 성공했다.
- 실제 영업시간/휴무일 반영, 본격적인 카테고리 비율 다양성 보정, 식사 시간대 슬롯 배치, 수동
  편집, 코스 저장/공유는 2차 이후로 남겨뒀다(`docs/23` 7장).

## [10-A 후속 3] /solo-course 체크인 기반 주변 관광지 추천

상태: 구현·Frontend 자동 검증 완료, 두 브라우저 dev 수동 검증 전

- `SoloCoursePage`가 강원도 축제·체크인과 무관한 하드코딩 mock("전주 한옥마을" 반나절/하루
  코스)이었고, 진입 경로 2곳(`HomePage` 배너, `FestivalDetailPage` 버튼) 모두 `festivalId`를
  넘기지 않아 애초에 어느 축제 기준으로 추천할지 알 방법이 없었다.
- 사전 분석·설계는 `docs/22_SOLO_COURSE_NEARBY_SPOT_DESIGN.md`로 정리했다. 검토 결과
  카테고리 필터(관광지/문화시설/액티비티/맛집)는 제외하고 나머지 설계대로 진행했다.
- 원본 GPS 좌표를 저장하지 않는 프로젝트 원칙상 "내 위치 기반 추천"은 실시간 좌표가 아니라
  "현재 체크인한 축제 좌표 기반" 반경 검색을 의미한다. 이미 구현되어 있던
  `GET /api/festivals/{id}/nearby-spots`(`docs/13_FESTIVAL_TOURSPOT_API_DESIGN.md` 3.4절,
  haversine 기반 축제 ↔ 관광지 거리 계산)를 그대로 재사용해 **백엔드는 변경하지 않았다**.
- `SoloCoursePage`를 재작성해 `resolveSoloCourseFestival()`이 `location.state.festivalId` →
  `useCurrentCheckin()`의 현재 체크인 축제 → 없음(안내 화면) 순서로 기준 축제를 정하도록 했다.
  체크인 조회가 끝나기 전(`loading`)에는 축제가 없다고 오판하지 않도록 별도 로딩 화면을 둔다.
- 실제 동선(시간대별 순서·체류시간)을 짜는 "코스" 기능은 이번 범위에서 제외하고, 기준 축제 주변
  관광지를 거리순으로 보여주는 목록으로 단순화했다. 기존 "반나절/하루" 토글과 타임라인 UI,
  `data/mock/soloCourses.ts`, `types/index.ts`의 `SoloCourse`/`CourseStop` 타입을 제거했다.
- `HomePage`의 배너와 `FestivalDetailPage`의 버튼 문구를 "코스"에서 "주변 관광지 추천"으로 바꾸고
  `festivalId`를 route state로 실어 보내도록 수정했다. 재사용 컴포넌트인 `CtaBanner`에 이동 대상
  화면에 route state를 넘기는 `state` prop을 추가했다(다른 사용처는 영향 없음).
- 목록 카드는 기존 `FestivalNearbyPlaceItem`을 그대로 재사용해 클릭 시 기존
  `TourSpotDetailPage`(`/spots/:id`)로 이동한다. 반경(API 기본값 5,000m)·목록 개수(기본값 10)는
  조절 UI 없이 API 기본값을 그대로 쓰고, 체크인이 전혀 없으면 대표 축제로 대체하지 않고 안내와
  체크인하러 가기 버튼만 보여준다.
- Frontend 전체 Vitest 25 files/210 tests(신규 `resolveSoloCourseFestival` 5건 포함),
  `npx tsc --noEmit`, production/PWA build가 성공했다. 두 브라우저·dev DB 수동 검증은 아직
  실행하지 않았다.

## [10-A 후속 2] terminal pool 재mount 시 종료 화면 고착 수정

상태: 구현·Frontend 자동 검증 완료, 두 브라우저 dev 수동 검증 전

- `[10-매칭 14차]`에서 "backend가 반환한 `CANCELLED`/`EXPIRED` terminal 상태를 `IDLE`로
  바꾸지 않고 서버 상태와 로컬 retry form 모드를 분리"하고 "새 mount에서는 로컬 retry
  모드가 사라지고 terminal 서버 상태를 복원"하도록 의도적으로 설계했으나, `[10-매칭
  13차]`에서 이미 "terminal pool 상태에서 `다시 시도`가 신규 신청 화면으로 돌아가지
  않는 문제"로 별도 Frontend 후속 작업으로 남겨둔 채 완결되지 않은 상태였다.
- 실제 증상: 매칭이 timeout 등으로 종료된 뒤 cooldown이 전혀 없는 상태에서도, `/matching`을
  벗어났다가 다시 들어오면 매번 "매칭이 종료됐어요" 카드가 다시 뜨고 "다시 신청하기"를
  눌러야만 신청 화면으로 돌아갈 수 있었다.
- `useMatchingSession`에 `isInitialLoadRef`를 추가해, 새로 mount된 뒤 첫 REST 조회
  결과에서만 `initialRetrySourcePoolId()`(기존 `canBeginRetry`와 동일한 조건 — terminal +
  pool 존재 + cooldown/완료 제한 비활성)를 적용해 곧바로 retry form을 연다. 세션 도중
  실시간으로 종료를 감지한 경우(폴링 등)는 기존 `retrySourceAfterRefresh`를 그대로 사용해
  종료 사유를 최소 한 번은 보여준다.
- cooldown 또는 완료 제한이 아직 활성 상태면 기존대로 terminal 카드와 남은 시간을 그대로
  보여주며, 이 경우는 이번 수정과 무관하다.
- Backend API 계약, DB schema는 변경하지 않았다.
- Frontend 전체 Vitest 24 files/205 tests(신규 6건 포함), `npx tsc --noEmit`,
  production/PWA build가 성공했다. 두 브라우저·dev DB 수동 검증은 아직 실행하지 않았다.

## [10-A 후속] 체크인 유효시간 통일과 /matching 현재 체크인 노출·취소

상태: 구현·Backend/Frontend 자동 검증 완료, dev DB·브라우저 수동 검증 전

- `/matching` 화면이 실제 체크인 상태를 조회하지 않고 navigation state/개발 전용
  fallback으로만 `festivalId`를 판단해, 새로고침이나 다른 경로로 들어오면 이미
  체크인되어 있어도 "체크인하기" 버튼이 다시 뜨는 문제를 확인했다. 체크인 취소
  API 자체도 없었다.
- 체크인 row `expires_at`(과거 설정값 기본 6시간)과 매칭 자격 상한(체크인 후
  1시간 하드코딩, `docs/05_MATCHING_POLICY.md`)이 서로 다른 기준이던 기존
  불일치를 확인했다. 이번 작업에서 정책 변경 없이 **1시간으로 통일**했다 —
  `FestivalCheckinService.checkIn()`이 `domain/checkin/CheckinValidityPolicy.VALIDITY`를
  사용하도록 바꾸고 `FestivalCheckinProperties.validDuration`/
  `FESTIVAL_CHECKIN_VALID_DURATION` 환경변수를 제거했다.
- `GET /api/festivals/checkin/me`(현재 유효 체크인 조회, 없으면 `200 data:null`),
  `DELETE /api/festivals/checkin/me`(취소, 활성 체크인 없으면 `404`)를 추가했다.
  취소는 기존 `checkIn()`의 "기존 ACTIVE 취소 + `FestivalCheckinCancelledEvent`
  발행" 로직을 재사용해 matching 도메인의 `WAITING` pool 정리로 이어진다.
- Frontend `/matching` IDLE 화면은 이제 실제 조회 결과를 `festivalId` 판단에
  우선 사용하고, 체크인이 있으면 축제명·만료 시각과 "체크인 취소" 버튼을
  표시한다. 취소는 확인 dialog(Escape/Tab 순환 포함)를 거치며, `WAITING` 이상
  진행 중인 매칭 상태에서는 취소 버튼을 노출하지 않는다(`LOCKED`/`PROPOSED`
  취소 정책은 `docs/21_CHECKIN_MATCH_POOL_INTEGRATION_DESIGN.md` 7장 미해결
  이슈로 유지).
- Backend festival/checkin focused unit·controller·PostgreSQL 통합 테스트
  35건, Backend 전체 320건 중 pgvector Testcontainers 이미지 fetch 실패로
  인한 기존 환경 제약 21건을 제외하고 전부 통과했다(이번 변경과 무관 — 매칭
  임베딩 등 다른 도메인의 기존 pgvector 통합 테스트가 이 환경에서 Docker
  이미지를 받아오지 못했다). `./gradlew build -x test`가 성공했다.
- Frontend 전체 Vitest 24 files/199 tests, `npx tsc --noEmit`, production/PWA
  `generateSW` build가 성공했다.
- `docs/21_CHECKIN_MATCH_POOL_INTEGRATION_DESIGN.md`, `docs/05_MATCHING_POLICY.md`를
  갱신했다. dev DB·브라우저 수동 검증은 아직 실행하지 않았다.

## [10-B AI 임베딩] 취향 임베딩 도입

상태: 1~5단계 Backend·Frontend 구현 완료, 점수 분해 저장 반영, 자동 테스트 통과,
분해 저장 실사용 검증 완료 (2026-09-01 기준)

실사용 검증 중 코사인 유사도의 실질 변별 구간이 0~100이 아니라 약 62~85라는 것이
드러났습니다. 저장 정합성과는 별개 문제이며 4-5절 `실측으로 드러난 코사인 스케일 문제`에
기록했습니다.

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
- (4-4절에서 해소) 저장되는 점수가 총점 하나뿐이라 사후 분석이 어려웠습니다. `V24`로
  `match_attempt_members`에 `jaccard_score`, `cosine_score`, `embedding_applied`,
  `embedding_pair_count`를 추가해 제안 생성 시점에 함께 저장합니다.
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

### 4-2. 5단계 — 매칭 신청 전 취향 안내와 마이페이지 상태 노출

브랜치 `feature/wbs-10-b-preference-guard`에서 진행했습니다.

**취향 미입력자의 매칭 신청을 막지 않기로 확정**

막을지 안내만 할지가 이 단계의 유일한 설계 결정이었고, **막지 않는 쪽으로 확정**했습니다. 근거는
네 가지입니다.

- 매칭 설계가 이미 취향 미보유를 정상 케이스로 전제합니다. `PairCompatibilityScorer.score()`는
  한쪽이라도 임베딩이 없으면 Jaccard 점수만 쓰고, 이 fallback이 `cosine = jaccard`와 수학적으로
  같아 점수 스케일이 왜곡되지 않습니다. 미보유자를 pool에서 뺄 기술적 이유가 없습니다.
- 막으면 선택 동의가 사실상 강제 동의가 됩니다. 취향 저장은 `AI_PROCESSING`과
  `OVERSEAS_TRANSFER`를 모두 요구하는데, 두 동의를 거부했다고 핵심 기능인 자동 매칭을 못 쓰게
  하면 "동의하지 않으면 서비스 거부"가 됩니다. 태그만으로 매칭이 정상 동작한다는 사실 자체가
  이 처리가 서비스 제공에 필수적이지 않다는 근거이므로 필수 동의로 재분류할 수도 없습니다.
  4-1절에서 국외 이전 고지에 거부권을 명시한 것과도 충돌합니다.
- 1~4단계 내내 유지한 "임베딩 실패가 서비스를 막지 않는다" 원칙과 충돌합니다. 동의를 받아도
  OpenAI 호출이 실패하면 `FAILED`로 남고 임베딩은 없습니다. 막는 구조라면 API Key 미설정이나
  OpenAI 장애 시 동의한 회원까지 전원 매칭 불가가 됩니다.
- 기존 회원이 잠깁니다. 동의 기록 구조 이전 가입자와 4단계 이후 AI 동의 없이 가입한 `ACTIVE`
  회원이 매칭에서 제외됩니다. 4-1절에서 `TERMS`·`PRIVACY` 소급 적용을 뺀 것과 같은 문제입니다.

전제 확인도 함께 했습니다. 회원가입 시 필수 동의는 `TERMS`·`PRIVACY` 2개뿐이고 AI 관련 2개는
선택입니다. `SignupPage.handleComplete()`가 `agreedTerms`·`agreedPrivacy`만 검사하고,
`savePreference()`는 취향 입력이 없으면 그대로 통과합니다. 즉 AI 동의를 한 번도 하지 않고 가입을
끝낸 회원이 정상 경로로 존재하므로 "이미 가입 때 받았으니 강제해도 된다"는 전제는 성립하지
않습니다.

**대신 신청 직전에 한 번 물어봅니다**

상시 배너 대신 확인 창을 택했습니다. 배너는 지나치기 쉬워 참여율이 오르지 않고, 배너와 창을 같이
두면 같은 말을 두 번 하기 때문입니다. 참여율은 실제로 중요한데, `score()`가 짝 단위 계산이라
**양쪽 모두 임베딩을 가져야** 코사인 항이 작동합니다. 혼자 입력해도 상대가 없으면 태그 계산과
같습니다.

- `자동 매칭 신청`을 누를 때 가로채 확인 창을 띄우고, `건너뛰고 신청`은 기존과 동일하게
  `enterPool()`을 호출합니다. `canApply` 조건은 바꾸지 않았습니다.
- 취향 미입력은 "취향을 입력하면 매칭 정확도가 올라가요", 분석 실패는 "취향 분석에 실패했어요"로
  문구를 나눕니다. 지금까지 `FAILED`는 사용자가 어디서도 알 수 없는 상태였는데, 이 창과
  마이페이지 섹션이 유일한 복구 수단인 재저장 경로를 열어 줍니다.
- 아래 경우에는 창을 띄우지 않고 곧바로 신청합니다: 분석 완료·분석 중(이미 입력한 사람),
  조회 로딩 중(신청을 지연시키지 않음), 조회 실패(부가 정보 조회 실패로 매칭을 막지 않음),
  이 화면에서 이미 안내함(탐색 만료·거절·취소 후 재신청이 잦아 매번 띄우면 방해가 됨).
- 마이페이지에는 상태 배지와 안내, 프로필 수정 링크를 두었습니다. 조회에 실패하면 섹션을 조용히
  숨깁니다.
- `지금 입력하기`로 넘어갈 때 route state에 `returnTo: '/matching'`을 실어 보냅니다. 프로필 수정
  화면은 이 값이 있을 때만 저장 성공 후 `매칭 신청하러 가기` 버튼을 보여 줍니다. 매칭하려다 들어온
  사용자가 프로필 수정 화면에 갇히던 문제를 수동 검증에서 확인해 보완한 것입니다. 마이페이지에서
  스스로 들어온 경우에는 `저장했어요. 이 화면에서 계속 고칠 수 있어요.` 문구로 그 화면에 머무는
  기존 4단계 동작을 그대로 유지합니다. 복귀 경로는 `readPreferenceReturnTo()`가 앱 내부 경로만
  허용하고 외부 URL과 프로토콜 상대 경로는 무시합니다.

**공통 모듈**

`components/preference/preferenceStatus.ts`에 상태 매핑과 판단·문구를 모았습니다. 마이페이지,
프로필 수정, 매칭 신청 세 화면이 같은 배지 문구를 쓰게 하려는 목적이고, jsdom이 없어 클릭 흐름을
테스트할 수 없으므로 판단 로직을 순수 함수로 빼는 것이 곧 테스트 수단이기도 합니다.
`ProfileEditPage`에 인라인으로 있던 배지 삼항식을 이 함수로 교체했습니다(동작·문구 변화 없음).

**남은 제약**

- 안내 여부를 화면 진입 기준으로만 기억하므로, 프로필 수정에 갔다가 입력하지 않고 돌아오면 다음
  신청에서 한 번 더 뜹니다. 기기·세션 단위 영구 저장은 한 번 건너뛴 회원에게 영영 안 뜨는 문제가
  있어 도입하지 않았습니다.
- 취향 참여율이 낮으면 임베딩이 대부분의 짝에서 작동하지 않습니다. 참여율과 실제 매칭 품질 변화를
  볼 수 있는 점수 분해 저장은 4-4절에서 추가했고, 실제 판단은 V24 이후 매칭 데이터가 쌓여야
  가능합니다.
- 취향 상태를 화면 진입 시 한 번만 조회하므로(`MatchingConditionPage`의 조회 `useEffect` 의존성
  배열이 비어 있음) 화면 밖에서 상태가 바뀌면 재진입 전까지 안내에 반영되지 않습니다. 다른 기기에서
  동의를 철회한 경우가 여기 해당합니다. 4-3절 실측 검증 중 확인했습니다. 신청 시점에 다시 조회하면
  매 신청마다 요청이 늘고 조회 지연이 신청을 늦추므로, 현재의 "부가 정보 조회가 매칭을 막지 않는다"
  원칙과 함께 검토해야 합니다.

### 4-3. 임베딩 점수 실측 검증 (2026-08-28)

브랜치 `test/wbs-10-b-embedding-score-verification`에서 진행했습니다. 1~5단계 구현과 자동 테스트는
끝났지만 "임베딩이 실제 매칭 점수에 반영되는가"를 실제 2인 매칭으로 확인한 적이 없어 이번에
실측했습니다.

**지금까지 근거가 없던 이유**

dev DB에 남아 있던 가장 최근 2인 매칭은 `attempt 33`(2026-08-28 14:27)이고 `member_score`가
33.33이었습니다. 이 값은 Jaccard 단독 점수와 같아 얼핏 임베딩 미반영으로 보이지만, 두 회원의
`member_preference_embeddings` row는 14:58과 14:59에 생성됐습니다. 매칭 시점에 임베딩이 아예
없었던 것이고 fallback이 정상 동작한 결과입니다. 즉 구현 이후 "양쪽이 임베딩을 보유한 상태로
매칭한 사례"가 한 번도 없었던 것이 근거 부재의 원인이었습니다.

**검증 설계 — 단일 변수**

`PairCompatibilityScorer.score()`는 양쪽 모두 `COMPLETED` 임베딩을 가진 경우에만 가중 합산하고
한쪽이라도 없으면 Jaccard만 씁니다. 그래서 취향 태그를 고정한 채 임베딩 가용 여부만 바꾸면 점수
차이가 곧 임베딩 반영 여부가 됩니다. 회원 2·27, 축제 144로 두 라운드를 돌렸습니다.

- 라운드 A: 두 회원 모두 `COMPLETED`
- 라운드 B: 회원 27만 `embedding_status = 'FAILED'`로 전환

`MatchingBatchReader`가 `COMPLETED`만 읽으므로 벡터와 원문을 지우지 않고 상태만 뒤집어도 "한쪽만
보유" 상황이 재현됩니다. 되돌릴 수 있고 취향 원문이 유실되지 않아 이 방식을 택했습니다.
`member_travel_styles`와 `.env` 가중치는 건드리지 않았습니다.

**측정 지점을 제안 생성 시점으로 잡은 근거**

`member_score`는 `MatchProposalCreationService.createInitial()`이 proposal을 만들 때 확정 저장되고
이후 수락·거절·타임아웃으로 바뀌지 않습니다. 따라서 제안이 뜨는 것까지만 가면 측정이 끝나고
응답할 필요가 없습니다. 이 덕분에 명시적 거절을 피할 수 있었고, 거절이 없으니
`match_opponent_exclusions`도 생기지 않아 두 라운드가 같은 check-in을 재사용할 수 있었습니다.

**기대값 산출**

기대값을 먼저 계산해 두고 실측과 대조했습니다. 회원 2의 태그는 `{ACTIVE}`, 회원 27은
`{ACTIVE, FOOD, PHOTO}`이므로 Jaccard는 1/3 = 33.33입니다. 코사인 유사도는 pgvector의
`1 - (a.embedding <=> b.embedding)`으로 0.717581, 즉 71.76입니다. 로컬 `.env` 가중치는 실험값
0.50/0.50입니다.

- 라운드 A 기대: `0.50 × 33.33 + 0.50 × 71.76 = 52.545` → 52.55
- 라운드 B 기대: Jaccard 단독 → 33.33

**실측 결과**

브라우저 2계정(회원 2·27)으로 실제 `자동 매칭 신청`을 눌러 매칭을 성사시켰습니다. check-in API가
아직 없어 `festival_checkins`만 SQL로 시딩했고, 나머지는 실사용 경로 그대로입니다.

| 항목 | 라운드 A | 라운드 B |
| --- | --- | --- |
| attempt_id | 34 (`POOL_ENTRY`, 16:22:01) | 35 (`SCHEDULER`, 16:25:02) |
| 회원 2 / 27 check-in | 194 / 195 | 194 / 195 (동일) |
| 회원 27 `embedding_status` | `COMPLETED` | `FAILED` |
| Jaccard | 33.33 | 33.33 |
| cosine × 100 | 71.76 | 해당 없음 |
| 계산 방식 | Jaccard + 임베딩 | Jaccard 단독 |
| `member_score` (회원 2) | **52.55** | **33.33** |
| `member_score` (회원 27) | **52.55** | **33.33** |
| `match_attempts.score` | 52.55 | 33.33 |
| 기대값 대비 오차 | 0.00 | 0.00 |

**19.22점 차이로 임베딩이 매칭 점수에 실제로 반영되는 것을 확인했습니다.** 두 라운드가 같은
check-in(194/195)과 같은 태그 조합을 썼으므로 다른 변수의 개입 여지가 없습니다. 또한 저장 값이
기대값과 소수점 둘째 자리까지 일치해 "반영된다"에 더해 "설계한 계산식 그대로 계산된다"까지
확인됐습니다. `match_attempts.score`(그룹 점수)와 `member_score`가 일치하는 것도 3절의 "선정 근거와
저장 점수가 갈라지지 않는다"를 실측으로 재확인한 것입니다.

**부수로 확인된 사항**

- 실측값 52.55는 현재 backend가 `.env`의 0.50/0.50을 로드했다는 증거이기도 합니다. 가중치는
  어디에도 로그로 남지 않지만, 같은 조건에서 0.70/0.30이면 44.86, 임베딩 미반영이면 33.33이 나오므로
  저장 점수 하나로 세 경우가 구분됩니다. 가중치 적용 여부를 확인하는 실용적인 수단입니다.
- 응답하지 않아 타임아웃된 라운드에서 `match_opponent_exclusions`가 생기지 않는 것을 실측으로
  확인했습니다. 제외 기록은 `MatchProposalResponseService`가 1회차 명시적 `REJECTED`일 때만
  생성합니다. 타임아웃은 자동 거절이지만 제외 대상이 아닙니다.
- 두 라운드 모두 `INITIAL_MATCH_INSUFFICIENT`로 종료했고, 타임아웃 처리된 회원에게만 2분 쿨타임이
  붙었습니다. 상대 회원은 `EXCLUDED`로 pool이 반환됩니다.
- 라운드 B에서 회원 27에게 "취향 분석에 실패했어요" 안내 창이 뜨지 않았습니다. 결함이 아니라 검증
  방법 때문입니다. `MatchingConditionPage`는 취향 상태를 화면 진입 시 한 번만 조회하고(조회
  `useEffect`의 의존성 배열이 비어 있음) 이후 갱신하지 않는데, 상태를 `FAILED`로 바꾼 시점이 화면
  마운트 이후였습니다.
- 위 추론을 그대로 두지 않고 별도로 확인했습니다. `embedding_status`를 `FAILED`로 되돌린 뒤 매칭
  화면을 새로고침하고 `자동 매칭 신청`을 누르니 `취향 분석에 실패했어요` 안내 창이 정상적으로
  떴습니다(`건너뛰고 신청` / `다시 저장하기`). 5단계에서 구현한 `FAILED` 안내 경로가 실사용에서
  동작하는 것을 처음으로 확인한 것이고, 지금까지 사용자가 알 수 없던 `FAILED` 상태의 유일한 복구
  경로가 실제로 열려 있음을 뜻합니다. 확인 후 `COMPLETED`로 원복했습니다.
- 다만 "다른 기기에서 동의를 철회하는 등 화면 밖에서 상태가 바뀌면 재진입 전까지 반영되지 않는다"는
  제약이 실물로 드러난 것이므로 아래 제약 목록에 추가합니다.

**검증 절차 스크립트**

`scripts/verify-embedding-score.sql`에 사전 점검, check-in 시딩, 측정, 임베딩 상태 전환, 원복을
단계별로 담았습니다. 측정 쿼리는 저장된 `member_score`와 DB에서 다시 계산한 기대값을 한 행에 나란히
출력하고 차이를 `diff_a` / `diff_b`로 보여 줍니다. 상단 파라미터 5개(회원 2명, 축제, 가중치 2개)만
바꾸면 다른 조합으로 재현할 수 있습니다.

주의할 점은 세 가지입니다. 전체를 한 번에 실행하지 않고 라운드 사이에 브라우저 조작과 쿨타임 대기가
필요합니다. `uq_festival_checkins_member_festival_active` 때문에 새 check-in을 넣기 전에 기존
`ACTIVE`를 먼저 내려야 합니다. 그리고 6단계 원복을 건너뛰면 대상 회원의 취향이 매칭에서 계속
무시됩니다.

**점수 분해 저장 제안 (이번 범위 밖, 구현하지 않음)**

이번 검증은 태그와 가중치를 알고 있어서 역산이 가능했지만, 그래서 오히려 4절이 지적한 제약이
분명해졌습니다. 저장된 값이 총점 하나뿐이라 `52.55`를 보고 임베딩이 쓰였는지 알려면 그 시점의
태그 구성, 벡터, 가중치를 모두 다시 모아 재계산해야 합니다. 임베딩은 회원이 취향을 수정하면 덮어
써지고 가중치는 환경변수라 나중에 바뀔 수 있으므로, 시간이 지나면 이 역산이 불가능해집니다. 실제로
이번에도 `attempt 33`의 33.33이 "임베딩 미반영"인지 "임베딩은 쓰였는데 점수가 낮은 것"인지
`member_preference_embeddings.created_at`을 따로 확인하고 나서야 판별할 수 있었습니다.

`match_attempt_members`에 아래 3개를 추가하면 사후 분석이 가능해집니다.

- `jaccard_score NUMERIC(10,2)` — 태그 점수
- `cosine_score NUMERIC(10,2)` — 임베딩 점수, 미사용 시 NULL
- `embedding_applied BOOLEAN` — 가중 합산이 적용됐는지 여부

`cosine_score`를 NULL 허용으로 두면 fallback 여부가 값 자체로 드러나므로 `embedding_applied`는
중복일 수 있으나, 조회 편의와 인덱싱을 위해 함께 두는 편이 낫다고 봅니다. 가중치까지 남길지는
별도 판단이 필요합니다.

이 제안은 4-4절에서 구현했습니다. 다만 3인 이상 혼합 상황을 검토하면서 세 컬럼만으로는 정의가
성립하지 않는 것이 드러나 컬럼 하나를 더 추가했고, 그 판단 과정을 4-4절에 적었습니다.

### 4-4. 점수 분해 저장 (2026-08-28)

브랜치 `feature/wbs-10-b-score-breakdown`에서 진행했습니다. 4-3절이 제안한
`match_attempt_members` 컬럼 추가를 실제로 구현한 단계입니다.

**설계 결정 — 3인 이상 혼합 pair에서 무엇을 저장할 것인가**

이 작업의 어려운 부분은 컬럼 추가가 아니라 정의였습니다. `memberScore()`는 대상 회원이 낀
pair들의 총점 평균인데, `PairCompatibilityScorer.score()`는 pair마다 독립적으로 임베딩 사용
여부를 판단합니다. 그래서 3인 A·B·C에서 A·B만 임베딩을 가지면 A 한 명 안에서 pair(A,B)는 가중
합산, pair(A,C)는 Jaccard 단독이 됩니다. 2인은 pair가 1개라 이 분기가 회원 단위로 그대로 올라와
문제가 드러나지 않습니다.

즉 **회원 단위 집계 자체가 손실 압축**이고, 어떤 손실을 감수할지가 결정 사항입니다. 판단 기준을
두 가지로 두었습니다.

- (가) 복원 가능성 — 저장된 분해값과 가중치로 `member_score`를 재구성할 수 있는가. 4-3절이 지적한
  "총점 하나로는 역산이 불가능해진다"가 이 작업의 동기이므로 1순위입니다.
- (나) 지표 순수성 — `cosine_score`가 실제 임베딩 신호만 담는가.

검토한 후보와 탈락 근거는 아래와 같습니다.

| 후보 | 정의 | 판정 |
| --- | --- | --- |
| AND | 모든 pair가 임베딩일 때만 기록, 아니면 `cosine_score` NULL | 탈락 |
| OR + 실제 코사인만 평균 | 임베딩 pair가 하나라도 있으면 true, 코사인은 그 pair들만 평균 | 탈락 |
| pair 단위 별도 테이블 | pair별 jaccard/cosine을 그대로 적재 | 보류 |
| **fallback을 `cosine = jaccard`로 간주** | 전체 pair를 분모로 두고 fallback pair는 Jaccard를 임베딩 항 투입값으로 봄 | **채택** |

- AND는 혼합 회원 A의 `cosine_score`가 NULL인데 `member_score`(26.50)는 Jaccard 평균(25.00)과
  다릅니다. "NULL이면 Jaccard 단독"이라는 읽는 쪽의 유일한 해석 규칙이 깨지고, 4-3절이 겪은 "이
  값이 임베딩 반영인지 아닌지 모르겠다"가 3인 이상에서 그대로 재발합니다.
- OR + 실제 코사인만 평균은 `jaccard`와 `cosine`의 분모가 서로 달라집니다(전체 pair vs 임베딩
  pair). A의 경우 `0.70 × 25.00 + 0.30 × 60.00 = 35.50`으로 실제 저장값 26.50과 어긋나고, 이는
  반올림 오차가 아니라 구조적 불일치라 복원이 원리적으로 불가능합니다.
- pair 단위 테이블은 (가)(나)를 모두 만족하지만 attempt당 최대 6행이 늘고 4-3절의 제안 범위를
  넘습니다. 지금 필요한 질문은 회원 단위 집계로 답할 수 있으므로 과도하다고 보고, 회원 단위
  분해로 부족해지는 시점에 재검토 대상으로 남깁니다.

채택안은 이미 문서에 두 번(3절, 4-2절) 확정해 둔 "이 fallback은 `cosine = jaccard`로 간주하는
것과 수학적으로 같다"는 등가성을 **점수 계산뿐 아니라 저장 정의에도 그대로 적용**한 것입니다.

```text
jaccard_score        전체 pair의 Jaccard 평균
cosine_score         전체 pair의 "임베딩 항 투입값" 평균
                     (임베딩 pair는 실제 코사인, fallback pair는 그 pair의 Jaccard)
                     임베딩 pair가 하나도 없으면 NULL
embedding_applied    임베딩 pair가 1개 이상인가 (OR). cosine_score IS NOT NULL과 동치
embedding_pair_count 실제 임베딩이 적용된 pair 수
```

이 정의에서 모든 인원수와 모든 혼합 조합에 대해 아래가 성립합니다.

```text
cosine_score IS NULL     -> member_score = jaccard_score
cosine_score IS NOT NULL -> member_score = jaccard_weight * jaccard_score
                                         + embedding_weight * cosine_score
```

3인 혼합 예시입니다. A는 `{PHOTO}`와 벡터, B는 `{PHOTO, FOOD}`와 벡터, C는 `{FOOD, ACTIVE}`이고
벡터가 없으며, `cos(A,B) = 0.60`, 가중치는 0.70/0.30입니다.

| 회원 | pair 점수 | `member_score` | `jaccard_score` | `cosine_score` | `embedding_applied` | `embedding_pair_count` | 검산 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| A | AB 53.00 / AC 0.00 | 26.50 | 25.00 | 30.00 | true | 1 | 0.7×25.00 + 0.3×30.00 = 26.50 |
| B | AB 53.00 / BC 33.33 | 43.17 | 41.67 | 46.67 | true | 1 | 0.7×41.67 + 0.3×46.67 = 43.17 |
| C | AC 0.00 / BC 33.33 | 16.67 | 16.67 | NULL | false | 0 | Jaccard 단독 = 16.67 |

**컬럼을 하나 더 둔 이유**

채택안의 대가는 `cosine_score`가 혼합 회원에서 합성값이 된다는 점입니다. A의 30.00은 절반이
Jaccard에서 온 값이라 "실제 임베딩 신호"로 읽으면 오독입니다. (나)를 일부 포기한 것이므로 혼합
비율을 남기지 않으면 분석자가 이 사실을 알 수 없습니다. 그래서 4-3절의 3개 안에
`embedding_pair_count`를 더했습니다. 회원당 전체 pair 수는 `match_attempts.target_group_size - 1`로
복원되므로 분모 컬럼은 필요하지 않고, 이 컬럼 하나로 세 질의가 모두 열립니다.

```sql
embedding_pair_count = 0                          -- 완전 fallback
embedding_pair_count = target_group_size - 1      -- 완전 임베딩 (순수 비교군)
0 < embedding_pair_count < target_group_size - 1  -- 혼합 (cosine_score 오독 주의)
```

**가중치는 컬럼으로 남기지 않습니다.** 세 점수가 있으면
`embedding_weight = (member_score - jaccard_score) / (cosine_score - jaccard_score)`로 행마다
역산됩니다. 소수 둘째 자리 반올림 탓에 근사값이지만, 4-3절이 확인한 "0.50/0.50이면 52.55,
0.70/0.30이면 44.86" 수준의 구분에는 충분합니다. `cosine_score = jaccard_score`인 퇴화 케이스에서만
역산이 안 되는데, 그때는 가중치와 무관하게 점수가 같으므로 알 필요가 없습니다.

**알려진 오차** — pair 점수가 pair마다 반올림된 뒤 평균되므로 3~4인에서는 위 항등식이 0.01까지
어긋날 수 있습니다. 실제로 4인 검증 케이스에서 재구성값 39.16과 저장값 39.15가 갈리는 사례가
나왔습니다. 기존 `member_score` 계산 순서를 바꾸지 않는 한 제거할 수 없는 오차이므로 그대로 두고,
테스트는 2인 정확 일치 / 3~4인 0.01 허용으로 나눴습니다.

**구현**

- `V24__add_match_attempt_member_score_breakdown.sql`로 컬럼 4개를 추가했습니다. V1~V21은 손대지
  않았습니다. 처음에는 V22로 만들었다가 아래 "migration 번호 충돌"에서 V24로 옮겼습니다.
- **기존 row는 백필하지 않았습니다.** 그 시점의 벡터와 가중치를 복원할 수 없고, 추정값을 넣으면
  "임베딩이 실제로 쓰였는가"를 판정하려고 만든 컬럼이 오히려 거짓 근거가 됩니다. NULL이 "분해 저장
  도입 이전 데이터"를 뜻합니다.
- CHECK 제약으로 네 컬럼이 서로 어긋나지 않게 강제합니다. 전부 NULL(과거 row)이거나 전부 채워진
  상태만 허용하고, 후자에서 `(cosine_score IS NOT NULL) = embedding_applied`와
  `(embedding_pair_count > 0) = embedding_applied`, 점수 0~100 범위를 검사합니다.
- 인덱스는 추가하지 않았습니다. `embedding_applied`는 2값이라 선택도가 낮고 분석 질의는
  `attempt_id` 기준이라 기존 `idx_match_attempt_members_attempt_status`로 충분합니다.
- `PairCompatibilityScorer.scoreDetailed()`가 `PairScore`(jaccard, cosine, embeddingApplied, total)를
  반환하고, **기존 `score()`는 `scoreDetailed().total()`만 반환**하도록 바꿨습니다. 계산 경로가
  하나뿐이라 `MatchGroupComposer`의 그룹 점수와 저장 점수가 갈라질 수 없습니다(3절 원칙).
  `MatchGroupComposer`는 코드도 동작도 바뀌지 않았습니다.
- 회원 단위 집계는 `MemberScoreBreakdown.of(List<PairScore>)` 한 곳에 모았습니다.
  `MatchProposalCreationService.memberScore()`를 `memberBreakdown()`으로 바꿨고,
  `total`은 기존과 같은 "pair 총점 평균"이라 **저장되는 `member_score` 값은 달라지지 않습니다.**
- 분해값은 API 응답 DTO에 노출하지 않습니다. 사후 분석용 데이터이지 사용자에게 보여줄 값이
  아닙니다.

**테스트**

`MatchProposalCreationServiceIntegrationTest`의 기존 `@ValueSource(ints = {2,3,4})` 하네스에
얹었습니다. 새 테스트 클래스나 fixture는 만들지 않았습니다.

- `prepareGroup(size)`는 다른 테스트 20여 개가 "전원 동일 태그 = 100.00"에 의존하므로 동작을
  유지한 채 후보 구성을 받는 오버로드를 추가했습니다. 기존 파라미터 테스트에는 분해 컬럼 검증만
  얹었습니다(Jaccard 단독이므로 `cosine_score IS NULL`).
- 다양화한 후보로 2인 fallback, 3인 혼합, 4인 전원 보유를 덮고, 3인은 전원 보유·전원 미보유·한
  명만 보유를 추가해 `embedding_pair_count` 0·1·2를 모두 지나게 했습니다.
- "한 명만 보유"는 전원 미보유와 저장 분해가 완전히 같다는 것을 명시적으로 확인합니다. 4-2절의
  "혼자 입력해도 상대가 없으면 태그 계산과 같다"가 저장 값으로 드러나는 지점입니다.
- 벡터는 OpenAI를 호출하지 않고 코사인이 0.60/0.80/0.96/0.00으로 딱 떨어지는 소차원 단위 벡터를
  썼습니다. 가중치는 `@SpringBootTest` properties로 0.70/0.30에 고정해 환경변수
  `MATCHING_SCORING_*`의 영향을 차단했습니다.
- 단위 테스트로 `PairCompatibilityScorerTest`에 `scoreDetailed()` 4건(태그·임베딩 조합 전수에 대해
  `scoreDetailed().total() == score()` 확인 포함), `MemberScoreBreakdownTest` 9건을 추가했습니다.
  3인 혼합 산술이 가장 두꺼운 곳이라 컨테이너 없이 빠르게 도는 단위 테스트에 두었습니다.

**실제 임베딩이 컬럼까지 도달하는지 별도 검증**

위 저장 검증은 `float[]`를 `MatchingCandidate`에 직접 주입하므로 `MatchingBatchReader`를 지나지
않습니다. 즉 산술과 저장은 덮이지만 아래 구간이 비어 있었고, 저장소 전체에
`member_preference_embeddings`에 벡터를 넣는 테스트가 하나도 없었습니다.

```text
member_preference_embeddings (vector(1536), COMPLETED)
  -> MatchingBatchReader.read()
  -> MatchingCandidate.preferenceEmbedding
  -> match_attempt_members.cosine_score
```

4-3절이 "구현 이후 양쪽이 임베딩을 보유한 매칭 사례가 한 번도 없었다"고 한 바로 그 구간이라
`MatchingOrchestrationServiceIntegrationTest`에 scheduler tick 전체를 태우는 테스트를
추가했습니다.

- 후보를 회원 9110001·9110002·9110006·9110007 넷으로 좁혀 희망 인원 4의 조합이 하나뿐이 되게
  하고, 그룹 선정 결과를 결정적으로 만들었습니다. fixture의 회원 9110007 ACTIVE cooldown과
  9110001-9110006 차단을 지우지 않으면 후보가 4명이 되지 않습니다.
- pgvector에 앞 두 성분만 값을 갖는 1536차원 단위 벡터를 넣어 상호 코사인이 0.60/0.80/0.96으로
  떨어지게 했습니다. OpenAI는 호출하지 않습니다.
- 회원 9110007에게는 **벡터를 가진 `FAILED` row**를 넣었습니다. reader의 `COMPLETED` 필터가
  동작하면 이 회원은 어느 pair에도 임베딩이 적용되지 않아야 하고, 실제로
  `cosine_score IS NULL`·`embedding_pair_count = 0`으로 저장됩니다.
- 저장된 값이 손으로 계산한 기대값과 소수점 둘째 자리까지 일치했습니다
  (그룹 점수 52.08, 회원별 42.33 / 68.38 / 36.49 / 61.11).

**migration 번호 충돌 (2026-08-30)**

로컬 `bootRun`이 기동에 실패했습니다.

```text
Migration checksum mismatch for migration version 22
-> Applied to database : 1209229362
-> Resolved locally    : -1281492574
```

원인은 파일 손상이 아니라 **번호 충돌**이었습니다. 공유 dev DB의 `flyway_schema_history`에는
다른 작업의 V22(`add festival tourplace query indexes`, 2026-08-26)와
V23(`add tour place region codes`, 2026-08-27)이 이미 적용돼 있었습니다. 두 파일은 아직
`origin/dev`에 병합되지 않아 저장소에는 보이지 않지만 dev DB에는 반영된 상태였고, 여기에 같은
번호의 V22를 올리자 Flyway가 체크섬 불일치로 막은 것입니다.

- **`flyway repair`로 넘기지 않았습니다.** repair는 기록된 체크섬을 현재 파일 기준으로 덮어쓰므로,
  dev DB에는 상대 작업의 인덱스만 있는데 "점수 분해 migration이 적용됨"으로 기록됩니다.
  `ddl-auto=validate`가 없는 환경에서 조용히 깨지는 상태가 됩니다.
- 파일을 `V24`로 옮겼습니다. dev DB의 최신 적용 번호가 23이라 24가 다음 빈 번호입니다.
- **교훈**: 새 migration 번호는 저장소의 파일 목록만 보고 정하면 안 되고 공유 dev DB의
  `flyway_schema_history`도 함께 확인해야 합니다. 미병합 브랜치가 dev DB에 먼저 적용해 둔 번호는
  저장소에서 보이지 않습니다.
- **이 문제는 자동 테스트로 잡히지 않습니다.** Testcontainers는 매번 빈 DB에서 시작해 22번이 비어
  있으므로 정상 통과합니다. 이미 migration이 쌓인 실제 DB에 붙어 봐야만 드러납니다. 4-4절
  작성 당시 "실사용 검증은 선택"으로 적었던 판단이 잘못이었고, 아래 남은 제약에 반영했습니다.

**남은 제약**

- V24 이전에 생성된 `match_attempt_members` row는 분해값이 NULL입니다. 백필하지 않기로 한 결과이며
  의도된 상태입니다. CHECK 제약의 "전부 NULL" 분기는 `MatchingRestApiIntegrationTest`가 네 컬럼
  없이 직접 INSERT하면서 실제 PostgreSQL에서 통과하는 것을 확인했습니다. 다만 **데이터가 있는
  dev DB에 V24를 적용하는 것**은 Testcontainers가 항상 빈 DB에서 시작하므로 자동 검증 범위 밖이고,
  배포 시점에 확인해야 합니다. 위 번호 충돌이 정확히 이 공백에서 나왔으므로, 코드 변경 후에는
  실제 DB에 한 번 붙여 보는 절차를 생략하지 않습니다.
- `match_attempts.score`(그룹 점수)는 여전히 총점만 저장합니다. 그룹 점수는 후보 조합 선정용이라
  회원 단위 분석에 쓰이지 않아 이번 범위에서 제외했습니다.
- 회원 단위 집계라 pair별 원값은 남지 않습니다. 혼합 회원의 `cosine_score`가 합성값이라는 한계는
  `embedding_pair_count`로 판별만 할 수 있고, pair별 실제 코사인이 필요해지면 별도 테이블이
  필요합니다.
- 실제로 "임베딩이 매칭 품질을 높였는가"를 판단하려면 V24 이후 매칭 데이터가 쌓여야 합니다. 이번
  단계는 그 판단에 필요한 데이터를 남기기 시작한 것까지입니다.

### 4-5. 점수 분해 저장 실사용 검증 (2026-09-01)

브랜치 `test/wbs-10-b-score-breakdown-verification`에서 진행했습니다. 4-4절의 V24 분해 컬럼이
Testcontainers에 심은 벡터가 아니라 **실제 OpenAI 임베딩·실제 태그·실제 체크인**을 거쳐서도
올바르게 채워지는지 dev DB에서 확인한 단계입니다. 실행 코드는 변경하지 않았습니다.

**검증 설계 — 체크인을 고정한 3라운드**

회원 1(`네이버테스트`)과 2(`dev카테`)로 축제 20에서 세 번 매칭했습니다. 세 라운드 모두 같은
체크인(209 / 210)을 재사용했고 태그도 양쪽 `{ACTIVE}` 하나로 고정이라, 라운드 사이에 바뀐 것은
임베딩뿐입니다. 4-3절과 같은 단일 변수 구조인데 두 가지가 다릅니다.

- 4-3절 라운드 B의 fallback은 `COMPLETED`를 SQL로 `FAILED`로 뒤집어 만든 것이었습니다. 이번
  attempt 36은 회원 1이 취향을 한 번도 입력한 적이 없어 생긴 **자연 발생 fallback**입니다.
  회원 1은 `AI_PROCESSING`만 있고 `OVERSEAS_TRANSFER`가 없어 임베딩 저장이 애초에 막혀 있었고,
  4-1절이 정한 "두 동의를 모두 보유해야 전송 허용"이 실사용에서 그대로 작동한 결과입니다.
- attempt 37 → 38은 회원 2가 취향 원문을 실제 화면에서 다시 저장해 벡터가 갱신된 라운드입니다.
  저장 12초 뒤 매칭에 새 벡터가 반영됐습니다.

| 항목 | attempt 36 | attempt 37 | attempt 38 |
| --- | --- | --- | --- |
| 시각 | 06:52:57 | 07:00:21 | 07:05:21 |
| 생성 경로 | `POOL_ENTRY` | `POOL_ENTRY` | `POOL_ENTRY` |
| 체크인 (회원 2 / 1) | 209 / 210 | 209 / 210 | 209 / 210 |
| 태그 (회원 1 / 2) | `{ACTIVE}` / `{ACTIVE}` | 동일 | 동일 |
| 회원 1 임베딩 | 없음 | `COMPLETED` (07:00:10 생성) | 동일 |
| 회원 2 임베딩 | `COMPLETED` | 동일 | 07:05:09 갱신 |
| `jaccard_score` | 100.00 | 100.00 | 100.00 |
| `cosine_score` | NULL | 76.50 | 72.45 |
| `embedding_applied` | false | true | true |
| `embedding_pair_count` | 0 | 1 | 1 |
| `member_score` (양쪽) | **100.00** | **88.25** | **86.23** |
| `match_attempts.score` | 100.00 | 88.25 | 86.23 |
| 재구성값 대비 오차 | 0.00 | 0.00 | 0.00 |

**대조 결과**

- Jaccard: 양쪽 태그가 `{ACTIVE}` 하나뿐이라 교집합 1 / 합집합 1 = 100.00. 저장값과 일치합니다.
- 코사인: pgvector `1 - (a.embedding <=> b.embedding)`을 직접 잰 값이 각각 76.50, 72.45로
  저장된 `cosine_score`와 소수점 둘째 자리까지 같습니다.
- 총점: `0.50 × 100.00 + 0.50 × 76.50 = 88.25`, `0.50 × 100.00 + 0.50 × 72.45 = 86.225 → 86.23`.
  2인이라 pair가 1개여서 반올림 누적이 없고 저장값과 정확히 일치합니다.
- `match_attempts.score`(그룹 점수)와 `member_score`가 세 라운드 모두 같습니다. 3절의 "선정
  근거와 저장 점수가 갈라지지 않는다"를 재확인했습니다.
- 양쪽 회원 row의 `cosine_score`가 같은 값인 것은 정상입니다. 2인은 pair가 1개이고 코사인이
  대칭이라 회원 단위 평균이 같은 수 하나가 됩니다. 값이 갈라지는 것은 3인 이상부터입니다.

**4-3절이 지적한 문제의 해소 확인**

세 라운드 모두 `member_preference_embeddings`를 조회하지 않고 저장된 컬럼만으로 판별됩니다.

- attempt 36의 100.00: `embedding_applied = false`, `embedding_pair_count = 0`,
  `cosine_score IS NULL` → 임베딩 미반영. V24 이전이라면 "태그가 같아서 100인지, 임베딩까지
  반영해서 100인지" 그 시점의 벡터를 복원해야만 알 수 있었습니다.
- attempt 37·38: `embedding_applied = true`, `pair_count = 1`, `cosine_score` 값까지 남아
  기여도를 바로 읽을 수 있습니다.

dev DB 전체로도 분류가 성립합니다. `match_attempt_members` 62행 중 4행이 `임베딩 반영됨`,
2행이 `임베딩 미반영 — Jaccard 단독`, 나머지 56행(attempt 11~35)이 `판별 불가 — 분해 저장 도입
이전`입니다. 마지막 분류는 4-4절이 백필하지 않기로 한 의도된 NULL입니다.

**가중치 역산 확인**

`(member_score - jaccard_score) / (cosine_score - jaccard_score)`로 attempt 37은 **0.5000**,
attempt 38은 **0.4998**이 나왔습니다. `.env`의 `MATCHING_SCORING_EMBEDDING_WEIGHT = 0.50`과
일치합니다. 38의 0.4998은 저장값이 이미 소수 둘째 자리로 반올림된 데서 오는 근사이며, 4-4절이
"근사값이지만 0.50/0.30 수준의 구분에는 충분하다"고 적은 그대로입니다. 가중치 컬럼을 두지 않은
판단이 실데이터에서 성립합니다.

#### 실측으로 드러난 코사인 스케일 문제

이번 검증의 부수 소득이자, 4-4절이 예상하지 못한 발견입니다. **저장 정합성과는 별개 문제이며
이번 범위에서 코드를 고치지 않았습니다.**

dev DB에 있는 `COMPLETED` 임베딩 3건의 상호 코사인입니다.

| 조합 | 코사인 × 100 | 취향 원문 요약 |
| --- | --- | --- |
| 1 ↔ 27 | **61.84** | "사진 많이 찍고 싶음 / 파워 E" ↔ **"ㅁ / ㅁ ㅁ"** |
| 2 ↔ 27 | 65.21 | "맛있는 거 / 잘 돌아다니는 사람, 사진 잘찍는사람" ↔ "ㅁ / ㅁ ㅁ" |
| 1 ↔ 2 | 72.45 | 위 두 문장 |

**의미 없는 자음 `ㅁ` 세 글자와 비교해도 61.84가 나옵니다.** `text-embedding-3-small`은
같은 언어·같은 도메인의 짧은 문장끼리 값이 크게 내려가지 않는 모델이고, 여기에
`buildPreferenceText()`가 붙이는 `하고 싶은 것:` / `편한 사람:` 라벨 접두어가 공통으로 들어가
바닥이 더 올라갑니다(회원 1은 35자 중 13자가 라벨). 즉 이 지표의 실질 변별 구간은 0~100이
아니라 대략 **62~85**이고, 관측된 3 pair의 폭은 10.61점입니다.

그런데 `PairCompatibilityScorer`는 코사인 × 100을 0~100 전 구간을 실제로 쓰는 Jaccard와 같은
스케일로 놓고 가중 합산합니다. 결과는 두 가지입니다.

- 임베딩 항이 **변별력은 좁으면서 총점을 일률적으로 끌어내리는** 항으로 작동합니다. attempt 37은
  태그가 완전히 같은 최상 궁합(Jaccard 100.00)인데 임베딩이 11.75점을 깎았습니다. 3절이 정한
  "태그를 주 신호, 임베딩을 동점 판별자로" 배분 의도와 어긋납니다.
- fallback 등가성과 맞물리면 **취향 입력이 역인센티브**가 됩니다. 임베딩 미보유 pair는
  `cosine = jaccard`로 간주해 감점이 없는데, 보유 pair는 코사인이 baseline 근처라 감점됩니다.
  attempt 36 → 37이 정확히 그 사례입니다. 같은 두 사람이 취향을 입력했더니 궁합 점수가
  100.00에서 88.25로 **떨어졌습니다.** 4-2절은 "혼자 입력해도 상대가 없으면 태그 계산과 같다"는
  중립까지만 검토했는데, 양쪽 다 입력하면 손해가 날 수 있다는 것이 이번에 실측으로 드러났습니다.

동시에 이번 검증은 **임베딩 항이 총점을 낮추는 방향으로도 작동한다**는 것을 처음 실증한
사례이기도 합니다. 4-3절은 33.33 → 52.55로 올라가는 방향만 봤습니다. 임베딩이 보너스 가산이
아니라 독립적으로 계산되는 항이라는 근거입니다.

해소 방향은 세 가지인데 성격이 다릅니다.

- **가중치 조정** — `.env`의 `MATCHING_SCORING_JACCARD_WEIGHT` / `MATCHING_SCORING_EMBEDDING_WEIGHT`로
  코드 변경 없이 즉시 가능합니다. 임베딩 비중을 낮추면 왜곡 폭은 줄어듭니다. 다만 코사인의 분포
  자체가 좁은 것은 그대로라 변별력이 회복되지는 않습니다.
- **baseline 정규화** — 관측된 하한(약 0.60)을 빼고 재스케일해 코사인이 0~100을 실제로 쓰게
  만드는 방식입니다. `PairCompatibilityScorer` 변경이 필요합니다.
- **라벨 접두어를 임베딩 입력에서 제외** — 저장 형식(`preference_text`)은 두고 OpenAI에 보낼 때만
  라벨을 떼는 방식입니다. `MemberPreferenceEmbeddingService` 변경과 기존 벡터 재생성이 필요합니다.

셋 다 스코어링 정책 판단이 선행되어야 하고, 판단 근거가 되는 실사용 데이터는 아직 3 pair뿐입니다.
따라서 이번 단계에서는 **기록만 하고 조정하지 않습니다.** 아래 남은 제약에 옮겼습니다.

**검증 스크립트**

`scripts/verify-score-breakdown.sql`에 조회 6종을 담았습니다. 상단 파라미터
(`attempt_id`, 회원 2명, 가중치 2개)만 바꾸면 다른 조합으로 재현됩니다.

1. 대상 attempt 개요와 회원당 pair 수(`target_group_size - 1`)
2. 분해값 검산 — `rebuilt_score` / `diff` / `diff_vs_group` / `derived_embedding_weight`와
   4-4절 3분류(`완전 fallback` / `완전 임베딩` / `혼합`)를 한 행에 출력
3. 같은 회원쌍의 라운드 비교 — `checkin_id`를 함께 출력해 단일 변수 비교인지 확인
4. 저장된 컬럼만으로 임베딩 반영 여부 판별 — `member_preference_embeddings`를 join하지 않음
5. 코사인 baseline 분포 — 위 스케일 문제를 언제든 다시 측정
6. 태그 원본에서 Jaccard 재계산(2인 전용 교차 확인)

기존 `scripts/verify-embedding-score.sql`과 역할이 다릅니다. 그쪽은 V24 이전 스크립트라 임베딩
상태를 뒤집어 가며 2라운드로 비교하느라 **데이터를 씁니다.** 이 스크립트는 V24가 저장해 둔 컬럼만
읽으므로 전부 SELECT이고 공유 dev DB에서 안전합니다. 두 파일 모두 남깁니다.

`diff`는 재구성값을 먼저 소수 둘째 자리로 반올림한 뒤 뺍니다. backend가 pair 점수를 반올림해
저장하므로 반올림 전 값과 비교하면 정확히 맞는 행도 0.01로 보입니다(attempt 38에서 실제로 겪어
수정했습니다).

**남은 제약**

- **코사인 스케일 문제는 미해소입니다.** 위 소절 참고. 실질 변별 구간이 62~85인 값을 0~100 스케일의
  Jaccard와 같은 비중으로 합산하고 있고, 그 결과 양쪽이 취향을 입력하면 총점이 오히려 내려갈 수
  있습니다. 가중치 조정은 `.env`로 즉시 가능하지만 baseline 정규화와 라벨 제외는 코드 변경입니다.
  판단에 필요한 데이터가 3 pair뿐이라 데이터가 더 쌓인 뒤 착수합니다.
- 3인 이상 실사용 검증은 아직 없습니다. `embedding_pair_count`가 0과 `target_group_size - 1`
  사이인 **혼합 케이스**, 즉 `cosine_score`가 합성값이 되는 경우는 자동 테스트로만 덮여 있습니다.
  실사용 3~4인 매칭에는 계정 3개 이상 동시 조작이 필요합니다.
- 3~4인의 반올림 오차 0.01 허용 구간도 실사용으로는 확인되지 않았습니다. 이번 검증은 전부 2인이라
  오차가 구조적으로 0입니다.
- 검증에 쓴 취향 원문 3건 중 1건이 `ㅁ` 같은 무의미 입력입니다. baseline 측정에는 오히려 유용했지만,
  실제 품질 판단에는 정상적인 취향 문장이 더 쌓여야 합니다.

### 5. 검증 결과

- (4-5단계) 실사용 검증은 실행 코드를 바꾸지 않았습니다. 변경 대상이
  `docs/10_PROGRESS_LOG.md`와 신규 `scripts/verify-score-breakdown.sql` 둘뿐이라
  backend/frontend 자동 테스트를 다시 실행하지 않았습니다. 검증 근거는 4-5절의 dev DB
  실측값(`attempt 36` / `attempt 37` / `attempt 38`)이고, dev DB는 SELECT만 수행했습니다.
- (4-4단계) Backend 전체 513 tests가 failures/errors/skipped 0건으로 통과했습니다. Docker Desktop을
  실행한 상태로 검증해 Testcontainers 통합 테스트가 실제로 수행됐습니다. 신규
  `MemberScoreBreakdownTest` 9건, `PairCompatibilityScorerTest` 4건,
  `MatchProposalCreationServiceIntegrationTest` 6건(파라미터 3 + focused 3),
  `MatchingOrchestrationServiceIntegrationTest` 1건(실제 `vector(1536)` end-to-end)을 포함합니다.
  실행 순서는 focused 점수 분해 → matching 전체 → backend 전체였습니다. JDK 17로 실행합니다.
  기본 `JAVA_HOME`이 JDK 8이면 실패합니다.
- (4-4단계) Frontend는 변경하지 않아 테스트를 다시 실행하지 않았습니다. 분해값은 API 응답에
  노출하지 않으므로 frontend 계약이 바뀌지 않습니다.
- (4-3단계) 실측 검증은 실행 코드를 바꾸지 않았습니다. 변경 대상이 `docs/10_PROGRESS_LOG.md`와
  신규 `scripts/verify-embedding-score.sql` 둘뿐이라 backend/frontend 자동 테스트를 다시 실행하지
  않았습니다. 검증 근거는 4-3절의 dev DB 실측값(`attempt 34` / `attempt 35`)입니다.
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

## [10-A] GPS 체크인 API

상태: 코드·테스트 작성 완료, 실제 dev 재배포 확인 제외

- `GET /api/festivals/{id}/checkin` 대신 `POST /api/festivals/{id}/checkin` 추가 — 브라우저 Geolocation 좌표를 받아 서버가 축제 좌표와의 거리를 계산하고, 반경(`festivals.checkin_radius_meters`) 이내일 때만 `festival_checkins`에 저장한다.
- 원본 위경도는 요청 처리 중에만 쓰고 DB/응답 어디에도 남기지 않는다. 저장되는 값은 계산된 `distanceMeters`뿐이다.
- 위치 정확도(`accuracyMeters`)가 임계값(`app.festival.checkin.accuracy-threshold-meters`, 기본 100m)을 넘으면 거절한다.
- 체크인 유효 기간은 `app.festival.checkin.valid-duration`(기본 6h)로 설정 가능.
- 한 회원이 동시에 여러 곳에 있을 수 없다는 전제로, 새로 체크인하면 같은 회원의 기존 `ACTIVE` 체크인(다른 축제 포함)은 전부 `CANCELLED` 처리한다. 같은 축제로 재체크인해도 동일하게 취소 후 새로 생성된다.
- 실제 PostgreSQL로 검증하는 과정에서, 기존 ACTIVE 체크인을 취소(UPDATE)하고 같은 트랜잭션에서 새 체크인을 INSERT할 때 Hibernate의 기본 flush 순서(INSERT 우선) 때문에 `uq_festival_checkins_member_festival_active` 부분 unique index를 위반하는 버그를 발견해, 취소 후 명시적으로 `flush()`하도록 수정했다.
- 매칭 풀(`match_pools`) 정리는 이번 범위에 포함하지 않았다 — `domain/matching` 엔진 코드가 아직 없어서, 체크인 취소 시 매칭 풀도 함께 취소하는 로직은 매칭 엔진 구현 시점으로 미뤘다(코드에 TODO로 남겨둠).
- Frontend: `FestivalDetailPage`에 "체크인하기" 버튼 추가. 버튼 클릭 시에만 위치를 1회 읽고(백그라운드 추적 없음), 성공/실패(권한 거부/시간 초과/범위 초과 등) 상태를 화면에 표시한다.
- dev 서버가 아직 HTTPS를 지원하지 않아, `localhost`가 아닌 dev 서버 도메인에서는 브라우저가 Geolocation 권한 요청 자체를 차단할 수 있다. 로컬 개발(`localhost:5173`)에서는 문제없이 동작한다.

## [10-A] 축제·탐색 화면 디자인 handoff 프론트엔드 구현 (mock 기반)

상태: 코드 작성 및 frontend build 완료, 실제 backend 축제 API 연동 제외

- `frontend/design_handoff_festival_matching/` 디자인 handoff 스펙 기준으로 홈/탐색/축제 상세/관광지 상세 4개 화면 재구성
- 기존 관광지 추천 중심 홈 화면을 축제·매칭 중심 섹션 구성으로 교체 (`HomePage.tsx`)
- 축제·관광지 탐색 화면을 유형 세그먼트 + 카테고리 + 검색 구조로 재구성하고 `TourSpotListPage.tsx`를 `ExploreListPage.tsx`로 이름 변경
- 축제 상세 화면(`FestivalDetailPage.tsx`) 신규 추가: 매칭 현황 요약 카드, 이용 정보, 주요 프로그램, 하단 고정 매칭 CTA
- 관광지 상세 화면(`TourSpotDetailPage.tsx`)을 방문 정보, 추천 포인트, 주변 축제, 포함 코스, 길찾기/주변 축제 보기 2버튼 액션으로 재구성
- `Festival` 타입과 축제 mock 데이터(`data/mock/festivals.ts`), 관광지 상세 부가 mock 데이터(`data/mock/spotDetails.ts`) 추가
- 데이터 접근은 기존 관례대로 각 페이지가 `data/mock/*`를 직접 참조하며, 홈 화면만 기존 `api/home.ts` wrapper를 경유
- `tsc --noEmit`, `npm run build` 통과 확인

주의:

- 실제 backend 축제 목록/상세 API 연동은 이번 범위에서 하지 않았다 (mock 데이터만 사용).
- 이 저장소에 아직 GPS 체크인/위치 권한 로직이 없어 위치 권한 없음 상태, 비동기 로딩/오류 상태 UI는 구현하지 않았다.
- nginx, docker-compose, GitHub Actions, backend 코드는 수정하지 않았다.

## [10-A] 축제 전체 페이지 DB 동기화와 Scheduler

상태: 코드·설정·문서·테스트 작성 완료, 실제 dev 재배포 확인 제외

- `Festival` Entity와 `FestivalRepository`를 기존 `festivals` schema에 매핑
- `searchFestival2` 전체 페이지를 수신한 뒤에만 DB 저장 단계로 이동
- 응답 페이지 누락, 중간 호출 실패, 최대 페이지 초과 시 writer를 호출하지 않고 기존 DB 데이터 유지
- 전체 페이지 조회 성공 시 같은 조회 기간·지역 범위에서 응답에 없는 기존 `ACTIVE` 축제를 `INACTIVE`로 변경
- 빈 정상 응답도 해당 조회 범위의 `ACTIVE` 축제를 `INACTIVE`로 변경하며 `HIDDEN`, `ENDED` 상태는 보존
- 누락 비교에는 매핑 성공 건뿐 아니라 응답에서 확인한 유효한 `contentId` 전체를 사용해 일부 항목 매핑 실패에 따른 오판 방지
- 외부 DTO의 날짜, 좌표, 주소, 법정동 코드를 `FestivalSyncData`로 변환하고 `content_id` 중복 제거
- `FestivalSyncWriter`의 단일 transaction에서 `content_id` 기준 신규/기존 축제 upsert
- `last_synced_at`과 API DTO 기반 `raw_data` JSONB 저장
- Scheduler 소유권을 `local=false`, `dev=true`, `prod=false`로 고정하고 dev backend 한 인스턴스만 자동 동기화
- 네트워크 오류, HTTP 5xx, 429만 최대 3회(최초 호출 포함), 1초부터 지수 지연으로 제한 재시도
- 실제 API 호출 시도마다 기존 `tour_api_call_logs`에 operation, 안전한 request key, status, 성공 여부, 응답 시간, 결과 건수, 오류 분류 저장
- 호출 로그에는 API Key, 전체 URL, 응답 본문과 원본 예외 메시지를 저장하지 않으며 로그 저장 실패가 원래 API 결과를 변경하지 않음
- dev 시작 후 최초 실행 및 `fixedDelay` 반복 실행
- 최초 실패이며 DB가 비어 있으면 `NO_DATA`, 기존 데이터가 있으면 `STALE_DATA`로 안전하게 기록하고 다음 주기에 재시도
- 기본 조회 범위 KST 오늘 기준 이전 30일~이후 365일, 강원 코드 `51`, 분류 `EV/EV01`
- local/prod Scheduler 비활성화로 일반 test와 local backend 시작 시 의도하지 않은 외부 API 호출 방지
- 축제 동기화, 재시도, 호출 로그 단위 테스트와 PostgreSQL rollback 통합 테스트 작성
- 전체 backend test 75건 중 74건 통과, opt-in live test 1건 기본 skip
- 기존 Flyway migration과 `GlobalExceptionHandler`는 수정하지 않음

## [10-A] 축제 종료 상태·대표 이미지·목록 조회 API

상태: 코드·문서·테스트 작성 완료, 실제 dev 재배포 확인 제외

- 성공한 동기화에서 KST 오늘보다 `event_end_date`가 지난 `ACTIVE/INACTIVE` 축제를 `ENDED`로 일괄 변경
- 종료 당일 축제와 운영자가 숨긴 `HIDDEN` 상태는 유지
- `searchFestival2`의 `firstimage`, `firstimage2`를 기존 `festival_images` 테이블의 대표 이미지 1건으로 저장·갱신
- API 응답에서 이미지가 누락되면 기존 대표 이미지를 삭제하지 않고 마지막 정상 이미지 유지
- HTTP/HTTPS가 아닌 이미지 URL은 저장 대상에서 제외
- `GET /api/festivals?page=0&size=20` 공개 목록 API 추가
- `ACTIVE`이면서 KST 기준 종료되지 않은 축제만 `event_start_date`, `id` 순으로 페이지 조회
- 대표 이미지를 일괄 조회하여 목록 N+1 query 방지
- `page >= 0`, `1 <= size <= 100` validation과 공통 `ApiResponse` 적용
- 기존 `festival_images` schema를 사용하여 Flyway migration 추가·수정 없음
- 전체 backend test 74건 중 73건 통과, opt-in live test 1건 기본 skip
- 축제 상세 API와 frontend 연동은 후속 작업으로 분리

## [10-A] 한국관광공사 TourAPI 공통 Client와 searchFestival2 조회

상태: 코드·문서·테스트 작성, 실제 API smoke test와 호출 로그 DB 저장 완료, 서비스 API 제외

- Spring MVC의 `RestClient` 기반 한국관광공사 공통 HTTP client 추가
- `external/tourapi`를 `client`, `config`, `dto`, `exception`, `log`, `support` 책임별 package로 분리
- 국문 `KorService2/searchFestival2` 한 페이지 조회와 요청 계층 검증 구현
- 루트 `.env`의 기존 `TOURISM-API-KEY`와 표준 `TOUR_API_KEY` 환경변수 지원
- 디코딩 키와 인코딩 키를 모두 한 번만 query parameter 인코딩하도록 처리
- 성공 JSON의 `resultCode=0000`, 빈 `items`, HTTP 200 XML 공공데이터포털 오류 응답 처리
- API Key와 전체 요청 URL을 로그와 예외 메시지에 남기지 않음
- `TourApiErrorType`에 기술 오류 기본 메시지를 모으고 원격 오류 코드와 HTTP status는 예외 필드로 분리
- `GlobalExceptionHandler` 직접 연결 없이 축제 service가 fallback 또는 `BusinessException` 변환을 결정하도록 경계 유지
- `MockRestServiceServer` 단위 테스트 5건 통과
- Spring local profile이 실제 `.env` 키를 읽는 opt-in live smoke test로 강원도 `51`, 축제 분류 `EV/EV01` 조회 성공
- 전체 backend test 통과
- 축제 상세 Controller와 frontend는 후속 작업으로 분리

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

1. ~~AI 임베딩 생성·동의·fallback과 scoring 결합~~ (**완료**, 4-5절 실사용 검증까지 종료.
   코사인 스케일 조정은 데이터가 더 쌓인 뒤 별도 작업)
2. ~~매칭 실패 시 솔로 코스와 재매칭 타이밍 연결(`MATCH-09`)~~ (**완료**, dev 수동 검증까지 종료.
   문서 상단 `[10-B MATCH-09] 매칭 실패 → 솔로 코스 전환 연결` 참고)
3. 신고·안전·후기와 관리자 연계 (**다음 작업**)
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

## [10-관리자 후속] 만남 장소 화면 — 마감 축제 검색 + 진행중/예정/마감 3분류

상태: 완료

- `/admin/meeting-points`가 재사용하던 공개 `GET /api/festivals`는 `festival.eventEndDate >= 오늘`
  조건을 항상 걸어(`FestivalRepository.findVisibleFestivals`) 종료된 축제를 숨기는 사양이라,
  관리자가 방금 끝난 축제의 만남 장소를 찾을 수 없었다(`docs/24_...` 7장에서 후속 과제로 남겨뒀던
  항목). 이번 작업이 그 후속 과제를 처리한다.
- Backend: `GET /api/admin/festivals?keyword=`(`AdminFestivalController` → `FestivalAdminQueryService`
  → `FestivalRepository.findForAdmin`)를 신설했다. `eventEndDate` 필터 없이 `status in
  (ACTIVE, ENDED)`만 걸어 검색하고, `HIDDEN`/`INACTIVE`는 제외한다. 인가는 다른 신규 admin
  서비스와 같은 `AdminAuthorizationService.requireAdmin`을 쓴다(기존 `FestivalMeetingPointAdminService`의
  로컬 `requireAdmin`과는 다른, 더 최신 공통 패턴).
- Frontend: `utils/festival.ts`에 `groupFestivalsByDisplayStatus`를 추가해 기존
  `resolveDisplayStatus` 규칙(오늘 날짜 vs `eventStartDate`/`eventEndDate`/`status`) 그대로
  진행 중/진행 예정/마감 3그룹으로 나눈다. `AdminMeetingPointsPage`는 검색어 없이 진입해도 항상
  이 3그룹을 채워 보여주고, 마감 그룹은 기본은 접어두되 선택된 축제가 그 안에 있으면 펼쳐서
  시작한다.
- 마감된 축제도 장소 등록/수정/활성화를 계속 허용한다 — 매칭 진입 자체는 어차피
  `FestivalCheckinService`가 체크인 시점에 `ACTIVE`만 허용해 막아주므로, 화면에서 추가로
  제약할 이유가 없다.
- 공개 사용자 화면(`festivalsApi.getList`, 홈/탐색 목록)은 건드리지 않았다 — 종료 축제 숨김은
  그 화면들에서는 여전히 의도된 정책이다.
- backend 신규 unit(`FestivalAdminQueryServiceTest`)·controller(`AdminFestivalControllerTest`)
  테스트와 `FestivalRepositoryIntegrationTest`에 `findForAdmin` 케이스를 추가했고, frontend는
  `utils/festival.test.ts`(`groupFestivalsByDisplayStatus`)를 신규로, `useAdminMeetingPoints.test.ts`는
  변경된 `searchFestivals` 응답 형태에 맞춰 갱신했다.

## [10-관리자 후속 2] 만남 장소 등록 폼 — 카카오맵 검색·좌표 선택기 연동

상태: 완료

- 위도/경도를 숫자로 직접 입력하던 등록/수정 폼에 카카오맵 기반 보조 UI를 추가했다. Kakao
  Local REST API(매칭 엔진의 후보 검색용으로 이미 계획된 것, 서버 전용 키 필요)가 아니라, 이미
  로드하는 Kakao Maps JS SDK의 `services` 라이브러리(`Places.keywordSearch`)를 썼다 — 새 키나
  백엔드 변경 없이 SDK 로드 URL에 `&libraries=services`만 추가했다.
- `components/admin/KakaoPlaceSearch.tsx`(장소/주소 검색 → 이름·주소·좌표·`kakaoPlaceId` 자동
  채움)와 `components/admin/KakaoCoordinatePicker.tsx`(선택된 좌표를 지도로 보여주고 클릭하면
  그 지점으로 좌표를 옮기는 미세조정용 지도)를 신규 추가하고, `AdminMeetingPointFormDialogContent`
  에 연결했다. 위도/경도 숫자 입력 필드는 fallback으로 그대로 남겨 검색/지도가 실패해도 등록이
  막히지 않는다.
- `components/matching/KakaoMeetingPointMap.tsx`의 `KakaoMaps` SDK 타입에 `services`/`event`를
  추가해 재사용했다(`loadKakaoMaps` 로더는 그대로).
- 신규 장소 등록 폼의 좌표 선택기 초기 중심점을 선택된 축제의 좌표로 잡기 위해, admin 축제
  검색 응답(`AdminFestivalSummaryResponse`, `AdminFestivalSummary`)에 `mapX`/`mapY`를 추가했다
  (`FestivalAdminQueryService`가 이미 갖고 있던 `FestivalSummary.mapX/mapY`를 그대로 옮김).
- 검색 결과 매핑(`toPlacePick`), 중심점 기본값 계산(`resolveCenter`), 신규 등록 초기값 계산
  (`toFormState`)을 순수 함수로 분리해 유닛 테스트를 추가했다 — 이 저장소 vitest 설정에는
  jsdom이 없어(`MyPage.test.tsx` 주석 참고) 클릭 같은 DOM 상호작용은 직접 테스트하지 못하고,
  로직만 순수 함수로 뽑아 검증했다.

## [10-UI 후속] 이미지 없는 콘텐츠의 기본 이미지 개편

상태: 완료

- 관광공사 API가 이미지를 주지 않는 콘텐츠의 기본 이미지를 빗금 스트라이프 + `사진` 배지에서
  분류별 아이콘 타일로 바꿨다. 기존 스트라이프는 로딩 스켈레톤 신호와 겹쳐 사용자가 이미지를
  계속 기다리게 되고, 크기 구분이 없어 56px 썸네일과 240px 히어로가 같은 모습이었다.
- 계산 로직은 `components/common/imagePlaceholderPresets.ts`(순수 함수), 렌더링은
  `components/common/ImagePlaceholder.tsx`로 분리했다 — jsdom이 없는 이 저장소 vitest에서
  검증할 수 있어야 하기 때문이다.
- 프리셋 6종(`12`/`14`/`28`/`39` + `FESTIVAL` + `DEFAULT`)은 관광지 동기화 대상
  `contentTypeId`(`TourPlaceSyncProperties.ALLOWED_CONTENT_TYPE_IDS`)를 그대로 따랐다. 배경은
  프리셋 accent를 `sand`에 옅게 섞은 그라데이션이고 톤 변형은 제목 해시(FNV-1a)로 골라, 같은
  콘텐츠는 항상 같은 톤이 나오고 같은 분류 카드끼리도 서로 구분된다.
- 크기 단계 `sm`/`md`/`lg`를 도입해 노출 요소를 다르게 했고, 문구가 사라지는 `sm`에서도 읽히도록
  `role="img"` + `aria-label`을 항상 붙인다.
- 좌표가 없어 지도를 못 그리는 자리(축제 상세·관광지 상세 `오시는 길`)는 원인이 달라
  `components/common/MapPlaceholder.tsx`로 분리하고, `지도 미리보기` 대신 좌표가 없다는 이유를
  문구로 밝힌다.
- `TourSpot`에 `contentTypeId`(optional)를 추가하고 `utils/tourSpot.ts`의 mapper 3개가 채우게
  했다. 관광지 목록/상세/근접 조회 응답이 모두 이미 `contentTypeId`를 내려주므로 backend 변경은
  없다. mock 데이터에는 값이 없어 `DEFAULT`로 떨어진다.
- 호출부 11곳(`FestivalHeroCard`, `UpcomingFestivalCard`, `FestivalNearbyPlaceItem`,
  `FestivalListItem`, `ExploreSpotItem`, `PlaceScrollCard`, `SoloCoursePage`,
  `FestivalDetailPage` 2곳, `TourSpotDetailPage` 2곳)을 모두 새 규칙으로 교체했다.
- 신규 테스트는 `imagePlaceholderPresets.test.ts`(13건, 프리셋 매핑·해시 결정성·색 혼합 경계)와
  `ImagePlaceholder.test.tsx`(5건, 크기 단계별 노출 요소와 접근성 속성)이다.

## [10-UI 후속 2] 앱 진입 스플래시 화면(로고 애니메이션)과 세션 bootstrap

상태: 완료

- 앱을 열면 로고가 애니메이션과 함께 약 1.2초 보이고, 미로그인이면 SSO 로그인 화면으로,
  로그인 상태면 원래 목적지로 넘어가는 진입 첫 화면을 추가했다. 흔히 스플래시 화면
  (splash screen), PWA 문맥에서는 런치 스크린(launch screen)이라 부르는 화면이다.
  `docs/03_FRONTEND_GUIDE.md` 라우팅 표에 `SplashPage`로만 적혀 있고 구현이 없던 항목이다.
- 장식만이 아니라 세션 bootstrap을 겸한다. 기존에는 미로그인 사용자가 `/`에 들어오면
  `HomePage`가 먼저 마운트되고 `memberProfileApi.getMine()`이 401을 받은 뒤 `apiClient`가
  `/login`으로 튕겨, 홈 화면이 잠깐 보이고 로그인으로 점프하는 깜빡임이 있었다. 이제
  스플래시가 그 구간을 덮는다.
- `components/splash/SplashGate.tsx`가 `App`의 `Routes`를 감싼다. 재생 구간(`PLAYING`)에서는
  children을 마운트하지 않는다 — 마운트하면 `HomePage`가 오버레이 뒤에서 GPS 권한 팝업을
  띄우고 축제·체크인 API를 쏘기 시작한다. 목적지가 정해진 `FADING` 구간에서 children과
  오버레이를 함께 렌더해 260ms 크로스페이드로 넘긴다.
- "2초 고정 대기"로 두지 않았다. 이미 로그인된 회원이 진입마다 2초를 기다리게 되기 때문이다.
  최소 노출 1.2초(`SPLASH_MIN_VISIBLE_MS`)와 세션 확인을 `Promise.all`로 병렬 실행하고 둘 다
  끝나면 해제한다. 로고는 항상 한 번 온전히 보이고, 느린 네트워크에서만 그보다 오래 머문다.
- 탭 세션당 1회만 재생한다(`sessionStorage`). OAuth는 같은 탭에서 카카오/네이버를 다녀오고
  `AuthController`가 `/` 또는 `/signup`으로 돌려보내므로, 플래그가 살아 있어 로그인 직후
  스플래시가 다시 뜨지 않는다. `/login`과 `/admin/*`은 건너뛴다 — 전자는 미로그인 회원의
  목적지이고, 후자는 `AdminRoute`가 자체 권한 확인 화면을 갖고 있어 안내가 겹친다.
- 세션 확인은 `api/session.ts`의 `probeSession()`으로 한다. `apiClient`를 쓰지 않은 이유는
  `apiClient`가 401·영구제한에서 `window.location.replace('/login')`을 호출해 전체 페이지
  리로드가 끼고 애니메이션이 끊기기 때문이다. 공유 클라이언트를 고치는 대신 부트스트랩
  시점에만 쓰는 조회를 따로 뒀고, 영구제한 code를 공유하려고 `apiClient`의 `BANNED_CODE`만
  export로 바꿨다.
- 목적지 판정(`resolveSplashTarget`)은 401과 영구제한 403만 로그인 화면으로 보낸다.
  네트워크 실패나 5xx에서는 이동하지 않는다 — 로그인된 회원을 일시 장애로 로그인 화면에
  떨어뜨리면 안 되고, 각 화면이 이미 자기 오류 상태를 갖고 있다. 이용정지
  (`MEMBER_SUSPENDED`)도 로그인 상태이므로 이동하지 않고 `SanctionNoticeDialog`가 맡는다.
- 로고 자산은 하이브리드로 처리했다. 핀 심볼은 `components/splash/BrandPin.tsx`에 SVG로 다시
  그렸고, 워드마크는 이미지 없이 `LoginPage`와 같은 텍스트 구성으로 렌더한다. `frontend/logo.png`
  (1448x1086, alpha 없음, 852KB)를 그대로 쓰면 핀·광선·워드마크가 한 장에 픽셀로 녹아 있어
  조각별 애니메이션이 불가능하고, `frontend/` 루트는 Vite `publicDir` 밖이라 `vite build` 시
  `dist`에 복사되지도 않는다. 결과적으로 스플래시는 새 이미지 파일 없이 동작하고 `logo.png`는
  브랜드 원본으로만 남는다.
- 핀 SVG 좌표는 눈대중이 아니라 원본 PNG를 격자로 스캔해 옮겼다. 두 사람은 좌우 대칭이므로
  한 경로(`FIGURE_BODY`)를 `scale(-1 1)`로 반전해 재사용하고, 몸통이 원 아래에서 잘리는
  원본 특징은 안쪽 원 `clipPath`로 재현했다. 색은 원본 오렌지(`#F65F27`)가 아니라 팔레트
  `coral`(`#E8593A`)을 쓴다 — 워드마크가 `coral` 텍스트라서 핀만 원본색이면 같은 화면에서
  오렌지 두 개가 어긋난다.
- 애니메이션은 라이브러리 없이 `tailwind.config.ts`의 `keyframes`/`animation`으로만 만들었다.
  핀 낙하(0~460ms) → 바닥 그림자 확장(300~620ms) → 광선 3줄 stagger 팝(520~900ms) →
  워드마크 페이드업(760~1140ms) 순서다. 광선은 지연을 별도 utility로 덧붙이면 `animation`
  축약형이 delay를 0으로 되돌릴 수 있어, 지연까지 포함한 utility 3개(`ray-pop-1..3`)로 나눴다.
  `prefers-reduced-motion`에서는 전부 정적 표시로 대체한다(`motion-reduce:animate-none`).
- 파일명 주의: 순수 함수 모듈을 `splashGate.ts`로 두면 `SplashGate.tsx`와 대소문자만 달라
  Windows에서 TypeScript가 거부한다(TS1149/TS1261). `splashPolicy.ts`로 분리했다.
- 테스트는 이 저장소 vitest에 jsdom이 없어(`MyPage.test.tsx` 주석 참고) 순수 함수와 정적
  마크업만 검증한다. `splashPolicy.test.ts`(12건, skip 조건과 상태코드별 목적지 매핑),
  `SplashScreen.test.tsx`(6건, 워드마크 구성·접근성·광선 3줄·페이드아웃 상태)를 추가했다.
- 검증은 `npm test`(64 files / 547 tests), `npx tsc -b`, `npm run build`를 통과했고, 핀 SVG는
  headless 렌더 결과를 원본 PNG와 같은 배율로 겹쳐 비교했다.
- 이번 범위에서 뺀 것: `favicon`·PWA `manifest` 아이콘·`theme_color`/`background_color`
  정리(여전히 `icons/placeholder.svg`), iOS `apple-touch-startup-image`, 첫 방문자 온보딩
  카드(`OnboardingPage`). `HomePage`도 `getMine()`을 호출하므로 진입 시
  `/api/members/me`가 2번 불리는데, 없애려면 프로필 Context를 만들어 호출부를 모두 바꿔야
  해서 남겨뒀다. 프로필 미완성 회원을 스플래시가 `/signup`으로 보내는 처리도 넣지 않았다 —
  현재 OAuth 복귀 시점에만 backend가 처리하는 동작이다.

## [10-B 안전 후속] 프론트엔드 token refresh 흐름

상태: 완료

- 배경: 프론트엔드가 `POST /api/auth/refresh`를 한 번도 부르지 않았다(`grep -rn "auth/refresh"
  frontend/src` 0건). `apiClient`는 401을 받으면 곧바로 `/login`으로 보내므로 access token
  30분이 지나면 로그인 사용자가 그대로 로그아웃됐다. 축제 현장에서 체크인하고 매칭을 기다리는
  동선에서는 30분이 짧다. backend endpoint와 refresh token 발급·저장은 이미 있었다.
- `fetchWithRefresh`를 추가해 `request`와 `apiClientVoid`가 같은 fetch 경로를 쓰게 했다. 401이면
  갱신을 한 번 시도하고 원래 요청을 한 번만 재시도한다. 재시도는 요청당 1회다.
- 동시 401에서 갱신이 중복 호출되지 않도록 모듈 스코프의 단일 in-flight promise
  (`refreshInFlight`)로 묶었다. 편의가 아니라 정확성 문제다 — `AuthService.refresh`가 refresh
  token을 회전시킨 뒤 저장된 hash와 대조하므로, 동시에 두 번 부르면 두 번째 호출이 이미 교체된
  token을 들고 와 401이 되고 session 자체가 끊긴다.
- `/api/auth/**`는 갱신 대상에서 접두로 제외했다. 로그인·로그아웃·갱신·제재 안내가 모두 이
  아래에 있어, 갱신 자체의 401이 다시 갱신을 부르는 무한 재귀를 구조적으로 막는다.
- 갱신 실패와 재시도 후 재차 401은 원래 401 응답을 그대로 흘려 기존
  `redirectToLoginIfUnauthorized`가 `/login`으로 보낸다. 그 사이 대기 중이던 다른 요청도 각자
  `ApiClientError(401)`로 실패한다(사용자 결정). 화면 코드는 바꾸지 않았다.
- 갱신 호출은 `authApi`를 거치지 않고 `apiClient.ts` 내부 raw fetch로 한다. `auth.ts`가
  `apiClient`를 import하므로 순환 import가 되고, 갱신 실패가 401 처리 흐름을 다시 타면 재귀가
  된다.
- 정지(`SUSPENDED`) 회원도 갱신된다. `AuthService.refresh`가 `requireSignedIn`을 쓰므로 의도된
  동작이고, 활동 차단은 403 제재 응답과 `SANCTION_EVENT`가 그대로 담당한다.
- 만료 전 선제 갱신은 넣지 않았다(401 반응형만). access token이 httpOnly cookie라 프론트가 만료
  시각을 알 수 없어, 하려면 만료 시각을 응답에 싣는 backend 변경이 선행돼야 한다.
- `application-local.yml`의 `refresh-token-expires-minutes` 기본값을 30분에서 `20160`(14일)으로
  올렸다. dev/prod는 이미 `20160`이었다. 루트 `.env`도 같이 `20160`으로 맞췄다(커밋 대상 아님)
  — yml만 고치면 `.env`가 덮어써서 로컬은 여전히 30분이었다.
- WebSocket은 변경하지 않았다. `matchingWebSocket`이 `reconnectDelay: 5000`으로 cookie 기반
  재접속을 반복하므로, 갱신으로 cookie가 되살아나면 자동 복구된다.
- 테스트: `apiClient.test.ts`에 6건을 추가했다 — 401→갱신→재시도 성공, 동시 3요청에서 갱신 1회,
  갱신 실패 시 재시도 없이 `/login`, `/api/auth/**`는 갱신 시도 없음, 재시도가 또 401이면 재갱신
  없음, body 없는 204 endpoint도 같은 흐름. jsdom이 없어 `fetch` stub의 호출 URL 순서와 횟수로
  검증했다.

## [10-B 보완] 완료 card 표시 기간과 임베딩 실패 가시화

상태: 코드 구현과 자동 검증 완료, dev 배포 후 수동 재검증 대기

배경은 두 가지 화면 제보다.

1. `/matching` 첫 화면에 이틀 전(`2026-09-08`) 완료 card가 그대로 떠 있었다.
2. dev 서버에서만 취향 임베딩이 계속 실패했다. 같은 계정 흐름에서 "저장했어요"를 본 뒤
   매칭 화면에서 "취향 분석에 실패했어요"가 떴고, 다른 계정에서는 새로 작성해도
   "아직 입력하지 않았어요"로 되돌아갔다. 로컬에서는 정상이었다.

### 완료 card 표시 기간 (Frontend)

- 원인은 backend `findLatestCompletedByMemberId`가 "그 뒤로 새 pool에 들어가지 않은 최신
  완료 group"을 기간 제한 없이 돌려주고, `deriveMatchingState`가 `completionLock.groupId`만
  보고 `COMPLETED`로 판정한 것이다. 새 매칭을 신청하기 전까지 카드가 영구히 남았다.
  `[10-A 후속 2]`에서 과거 terminal pool에 대해 고친 고착과 같은 종류인데 완료 경로에는
  같은 처리가 없었다.
- `isCompletedCardVisible(restriction)`을 추가했다. 잠금이 살아 있거나(`active`), 완료
  시각과 `serverNow`가 서울 기준 같은 날일 때만 카드를 보여준다. 아니면 `IDLE`로 떨어져
  신청 화면이 된다. 두 조건을 OR로 묶어 자정 직전 완료가 몇 분 만에 사라지지 않게 했다.
- backend는 바꾸지 않았다. 재매칭 잠금은 그대로 `confirmed_at + 1시간`이고, 이번 변경은
  화면 표시 기간만 정한다. 판정 기준 시각은 단말 시계가 아니라 restriction의 `serverNow`다.
- 완료 card에서도 체크인 줄을 보여주도록 `CheckinSummaryCard`를 분리했다. 예전에는 이 줄이
  `IdleForm` 안에만 있어 완료 상태에서 체크인 만료 시각과 `체크인 취소`가 사라졌다.

### 임베딩 실패 (Backend/Frontend)

- **취향 저장이 통째로 롤백되던 경로를 막았다.** `MemberPreferenceEmbeddingService`가
  `BusinessException`만 잡고 있어서, 그 밖의 예외가 나면 트랜잭션이 되돌아가 방금 저장한
  취향 행 자체가 사라졌다. 회원 화면에서는 "입력한 적 없음"으로 보인다. `RuntimeException`
  전체를 잡아 `FAILED`로 기록한다. "임베딩 실패가 서비스를 막지 않는다"를 저장에도 적용한 것이다.
- 실패 이유를 `EmbeddingFailureReason`(API_KEY_MISSING / UNAUTHORIZED / RATE_LIMITED /
  INVALID_REQUEST / UPSTREAM_ERROR / TIMEOUT / CONNECT_FAILED / INVALID_RESPONSE / UNKNOWN)으로
  분류해 로그와 DB에 남긴다. `V32`로 `member_preference_embeddings.embedding_error_reason`을
  추가했다. **회원 API 응답에는 노출하지 않는다** — 회원에게는 "분석 실패"로 충분하고 이 값은
  운영자용이다.
- `OPENAI_API_KEY`의 앞뒤 공백·줄바꿈을 기동 시 제거하고 경고를 남긴다. Windows에서 편집한
  `.env`를 서버로 옮기면 값 끝에 `CR`이 남아 `Bearer sk-...`가 되고 헤더 자체가 거절되는데,
  키를 "제대로 넣었는데 실패하는" 대표 경로다. 키가 비어도 기동은 실패시키지 않는다.
- 관리자 진단 `GET /api/admin/diagnostics/embedding`을 추가했다. 회원 데이터를 쓰지 않고
  고정 문장으로 왕복만 시켜 `ok`/`reason`/`apiKeyPresent`/`elapsedMs`를 돌려준다. 실패해도
  200이다 — 실패 이유가 곧 응답이다. SSH 없이 dev 상태를 확인할 수 있다.
- 프로필 수정에서 분석이 실패했는데도 초록색 "저장했어요. 이제 더 잘 맞는 사람을 찾을 수
  있어요"가 뜨던 것을 고쳤다. `preferenceSaveNotice`로 판정을 모으고, 실패면 경고 배너와
  `다시 분석하기`를 보여준다. 회원가입도 같은 기준(`preferenceSignupNotice`)을 쓴다.
  매칭 신청 전 안내창 자체는 바꾸지 않았다 — 실패해도 건너뛰고 신청할 수 있어야 한다.
- 재시도 로직은 넣지 않았다. 원인이 키·차단이면 재시도가 소용없고 저장 응답 대기만 배로
  늘어난다. 진단으로 `TIMEOUT`이 확인되면 별도로 다룬다.

### 검증

- frontend: `npm test` 67 files / 620 tests, `npx tsc -b`, production build 통과.
- backend: 임베딩 관련 focused test 통과, 전체 test는 기존 baseline과 대조.
- dev 수동 재검증 대기: ①이틀 전 완료 계정으로 `/matching` 진입 시 신청 화면, ②당일 완료
  직후 카드 유지, ③저장 실패 시 실패 배너, ④진단 endpoint의 `reason` 확인, ⑤DB
  `embedding_error_reason` 확인.

### 이번 범위에서 제외

- `infra/`, `.github/` (다른 브랜치와 충돌)
- dev 서버 `.env`의 실제 key 값 조정 — 저장소 밖 운영 작업이다.
- 임베딩 재시도·비동기화, 가중치 조정, 매칭 알고리즘 변경

## [10-B] 관리자 매너온도 수동 조정 (docs/19 4.9 · PR A)

상태: Backend/Frontend 구현·자동 테스트 완료. dev 브라우저 수동 검증 대기

브랜치는 `feature/wbs-10-b-admin-manner-temperature-adjust`이며 `dev`(`066256b`)에서 분기했다.
4.9는 덩치가 커서 3개 PR로 쪼갰고 이번은 그 중 **PR A**다.

| PR | 범위 | 상태 |
| --- | --- | --- |
| A | 관리자 온도 수동 조정 | 이번 작업 |
| B | 후기 작성 기능(`member_reviews`) | 미착수 |
| C | 온도 반영 공식 + 30도 매칭 제한 | 미착수 |

### 왜 이걸 먼저 했나

`manner_temperature`는 신고 확정으로만 내려가는 **하강 전용 지표**였다. 상승 경로가 코드에
아예 없어서, 관리자가 신고를 잘못 판정했다는 것을 나중에 알아도 되돌릴 방법이 없었다.
후기 기능(PR B)은 `member_reviews` 테이블만 `V4`에 있고 코드가 0줄이라 새로 만들어야 하는
큰 덩어리인데, 복구 경로는 그것을 기다릴 이유가 없다.

### 착수 전에 확정한 것

- **온도 하강폭 `5.00` → `2.00` 변경은 이번 범위가 아니다(PR C).** 현재 값으로는 신고 확정
  2건에 `26.50`이 되어 30도 아래로 떨어지는데, 관리자 안전 알림 임계는 3건이다. 즉 30도
  매칭 제한을 지금 도입하면 **관리자 알림보다 자동 제한이 먼저 발동**해 4.3에서 확정한
  "자동 제한은 회원 status를 바꾸지 않고 관리자 알림까지만" 원칙을 우회한다. 하강폭만 먼저
  낮추면 제한 없이 제재만 약해지므로 30도 제한과 같은 PR에서 함께 바꾼다.
- **이미 깎인 회원은 재계산하지 않는다.** 과거 신고 판정 이력을 되짚어 일괄 재계산하면 그
  사이 관리자가 손댄 값과 충돌한다. 이 수동 조정으로 개별 복구한다.

### 상한을 새로 도입했다

`MannerTemperaturePolicy`를 만들어 허용 범위를 한곳에 뒀다. 하한 `20.00`은
`ReportConfirmationService`에 있던 값을 옮긴 것이고 **상한 `42.00`은 이번에 새로 생겼다.**
지금까지는 하강 전용이라 상한이 필요 없었지만, 상승 경로가 생기면 온도가 무한히 올라가
지표로서 의미를 잃는다. `ReportConfirmationService.MANNER_TEMPERATURE_FLOOR`는 상수를 다시
적지 않고 policy를 참조한다 — 두 곳에 적으면 자동 하강의 하한과 수동 조정의 범위가 갈라진다.

시작값 `36.50` 기준으로 하한까지 `-16.50`, 상한까지 `+5.50`으로 비대칭인데 의도한 것이다.
신뢰를 잃는 것은 빠르고 되찾는 것은 느리다. 상한을 더 낮추면 상위 구간에서 후기가 온도에
아무 영향을 주지 못해 후기를 쓸 이유가 사라진다.

### 목표값을 받는다, 차감량이 아니라

`Member.adjustMannerTemperature(target, floor, ceiling)`은 조정 후 값을 받고 변경 전 값을
반환한다. 관리자는 "36.5로 되돌린다"를 직관적으로 다루고, 감사 로그에 변경 전후를 함께
남기므로 delta 방식과 추적력이 같다.

**범위를 벗어난 값은 clamp하지 않고 거절한다.** 자동 하강(`decreaseMannerTemperature`)이
하한으로 clamp하는 것과 의도적으로 다르다. 자동 경로는 거절할 상대가 없지만, 수동 조정에서
clamp하면 관리자가 입력한 값과 저장된 값이 조용히 달라져 감사 로그를 읽는 사람이 관리자의
의도를 알 수 없다.

### 제재와 같은 API로 처리하지 않는다

`POST /api/admin/members/{id}/manner-temperature`를 별도로 뒀다. `/actions`에 넣지 않은 이유는
제재가 **상태 전이**이고 온도 조정은 상태를 전혀 바꾸지 않기 때문이다. 낙관적 잠금 대상도
다르다 — 제재는 `expectedStatus`, 온도 조정은 `expectedTemperature`다. 두 관리자가 같은
상세 화면을 열어 두고 각자 조정하면 나중 요청이 앞 조정을 조용히 덮으므로, 화면에서 본 값을
함께 보내 다르면 `409 ADMIN_MEMBER_MANNER_TEMPERATURE_CONFLICT`로 거절한다.

**세션을 끊지 않고 안전 알림도 닫지 않는다.** `SUSPEND`/`BAN`은 refresh token을 폐기하고
WebSocket을 끊지만 온도 조정은 접근 권한을 바꾸지 않는다. 안전 알림을 닫지 않는 이유는 온도를
올려도 누적 유효 신고 건수는 그대로이기 때문이다 — 알림은 신고 누적에 대한 대응 요구이고,
온도 복구로 알림이 사라지면 제재 검토가 조용히 취소된다.

### 상태 판정은 허용 목록으로

`ACTIVE`·`PROFILE_REQUIRED`·`SUSPENDED`·`BANNED`만 조정할 수 있다. `BANNED`를 포함한 이유는
차단 해제 후에도 온도가 그대로면 복구가 의미 없어져, 해제 전에 미리 조정할 수 있어야 하기
때문이다. 탈퇴·삭제 회원은 익명화됐고 다시 매칭에 들어올 일이 없으므로 제외한다.

**부정 조건(`status !== 'WITHDRAWN'`)으로 쓰지 않았다.** 직전 커밋(`c4c3324`)에서 관리자
회원 상세의 경고 버튼이 그 형태여서 탈퇴 회원에게 노출되던 것을 고쳤다. 같은 결함을 새로
만들지 않도록 backend `validateMannerTemperatureStatus`와 frontend
`MANNER_TEMPERATURE_ADJUSTABLE_STATUSES`를 모두 허용 목록으로 썼고, 상태별 노출 테스트 6건이
고정한다.

### 감사 로그

`V33`으로 `admin_actions.action_type`에 `MANNER_TEMPERATURE_ADJUST`를 추가했다(`V20`·`V28`과
같은 CHECK 교체 방식). `MANUAL_PENALTY`와 합치지 않는다 — 그쪽은 `penalty_score`를 올리는
제재이고 온도 조정은 올리는 쪽이 주 용도인 복구 수단이라, 섞으면 감사 로그에서 "관리자가
제재했다"와 "관리자가 복구했다"를 구분할 수 없다. `metadata`에 `beforeTemperature`와
`afterTemperature`를 남긴다.

**제재 이력 목록(`findActions`)에 섞지 않고 조회를 분리했다.** 그쪽은 `action_type`을
`AdminMemberActionType`으로 변환하는데, 그 enum은 조치 **요청** 타입이다. 온도 조정을 넣으면
요청할 수 없는 값이 요청 enum에 섞이고, 변환에서 터지면 회원 상세 조회 전체가 실패한다.
응답에는 `mannerTemperatureAdjustments`를 따로 담고 화면도 구역을 나눠 보여준다 — 값만 보고는
그것이 자동 하강의 결과인지 누가 손댄 결과인지 알 수 없어 두 관리자가 중복 조정한다.

### Migration 번호

`V33`은 저장소 파일 목록과 **공유 dev DB의 `flyway_schema_history` 양쪽에서** 비어 있음을
확인하고 잡았다(둘 다 최고 번호가 `V32`). 확인 과정에서 `V31` checksum repair가 이미
실행되어 있다는 것도 함께 확인했다(위 `[사고 기록]` 절 갱신).

### 테스트

- Backend: `MemberMannerTemperatureAdjustTest` 6건(경계값, clamp하지 않고 거절, scale,
  자동 하강 경로와 하한 공유), `AdminMemberIntegrationTest`에 7건 추가(감사 로그 전후 값,
  멱등성, `expectedTemperature` 충돌, 범위 밖 거절, 탈퇴 거절·영구차단 허용, 세션·신고 집계
  불변, 관리자 계정 거절).
- Frontend: `AdminMembersMannerTemperature.test.tsx` 14건(상태별 버튼 노출 6건, 이력 렌더,
  범위 밖 경고와 제출 차단, 세션 성공·이중 제출·실패 유지).
- 전체 회귀: backend 986건 중 실패 2건(둘 다 `dev` 유래 기존 실패), frontend 640건 전체 통과,
  `tsc --noEmit`과 production build 통과.

**테스트에서 밟은 함정 2건.**

- `members`를 SQL로 직접 `BANNED`/`WITHDRAWN`으로 바꾸면 `chk_members_banned_previous_status`와
  `chk_members_withdrawal_snapshot`에 걸린다. 상태 fixture는 SQL이 아니라 실제 서비스 경로
  (`act(BAN)`, `forceWithdraw`)로 만들어야 한다.
- 화면 테스트에서 버튼 라벨(`매너온도 조정`)과 이력 구역 제목(`매너온도 조정 이력`)이 앞부분이
  같다. 문자열 포함만 보면 **버튼이 숨어 있어도 제목 때문에 통과**하므로 닫는 태그까지 붙여
  (`>매너온도 조정</button>`) 버튼만 집는다.

### 이번 범위에서 제외

- 온도 하강폭 조정과 30도 매칭 제한(PR C)
- 후기 작성 기능과 `member_reviews`(PR B)
- 시간 경과에 따른 온도 자동 회복 — 근거가 부족해 PR C에서 별도 판단한다
- 회원 화면의 매너온도 노출 방식 변경

## [10-B] 매너온도 상승 경로와 회원 노출 (docs/19 4.9 · PR B)

상태: Backend/Frontend 구현·자동 테스트 완료. dev 브라우저 수동 검증 대기

브랜치는 `feature/wbs-10-b-manner-temperature-recovery`이며 **PR A 브랜치 위에서 분기했다.**
`MannerTemperaturePolicy`를 직접 확장하므로 `dev`에서 분기하면 그 클래스를 다시 만들게 되고
병합 충돌이 확정이다. **PR A를 먼저 병합해야 이 PR의 diff가 정상으로 보인다.**

### 왜 이 순서가 됐나

PR A를 올린 뒤 사용자가 두 가지를 물었다. "관리자가 온도를 조정할 일이 있나"와 "자동으로는
어떻게 올라가나"다. 코드를 확인한 답은 **올라가는 경로가 하나도 없다**였다.
`manner_temperature`에 값을 쓰는 곳은 셋뿐이었고(엔티티 기본값, 신고 확정 하강, PR A의
관리자 조정) 시간이 지나도 매칭을 잘해도 오르지 않았다.

원래 계획된 상승 경로는 후기였지만 `member_reviews`는 `V4`에 테이블만 있고 코드가 0줄이다.
그래서 사용자가 **"만남을 끝까지 마치면 후기가 없어도 올려주자"**를 제안했고 이쪽을 먼저
넣었다. 후기보다 나은 점이 셋이다.

- 후기는 상대가 안 써주면 잘 참여한 회원도 못 오른다. 완료는 본인 행동만으로 결정된다.
- 조작이 어렵다. 축제 현장 GPS 체크인, 매칭 성사, 만남 장소 전원 도착이 모두 필요하고
  완료 후 1시간 재매칭 잠금이 걸린다.
- 노쇼(`penalty_score +3`)에 대응하는 보상이 없던 비대칭이 해소된다.

**댓글 작성량 반영은 채택하지 않았다.** 매너온도는 "만남에서의 매너" 지표인데 댓글은 만남과
무관하고, 혼자 무한히 쓸 수 있어 조작이 너무 쉽다. 반영하면 "매너 좋은 사람"이 아니라 "글
많이 쓴 사람"의 온도가 높아진다.

### 확정한 값 체계

| 경로 | 값 | 상한 |
| --- | --- | --- |
| 시작 | `36.50` | — |
| 신고 유효 판정 | `-2.00` (기존 `-5.00`) | 하한 `20.00` |
| 만남 완료 | `+0.50` | `42.00` |
| 시간 경과 | `30일마다 +0.50` | **`36.50`** |
| 노쇼 | 변화 없음 | — |

**차감량을 낮춘 이유.** `-5.00`이면 신고 확정 2건에 `26.50`이 되어 30도 아래로 떨어지는데
관리자 안전 알림 임계는 3건이다. 즉 PR C의 30도 제한이 **관리자 알림보다 먼저 발동**해
`docs/19` 4.3의 "자동 제한은 status를 바꾸지 않고 알림까지만"을 우회한다. `-2.00`이면 신고
4건이 필요해 알림이 먼저 뜬다. 회복 관점에서도 `-5.00`은 완료 10번을 요구해 회복 경로가 있는
척만 하는 셈이었다. 지금은 신고 1건 = 완료 4번으로 정확히 맞는다.

**시간 경과 회복의 상한만 시작값이다.** 상한(`42.00`)까지 올리면 아무 활동도 하지 않은 회원이
가만히 있다가 상한에 도달해 지표가 "가입한 지 얼마나 됐나"를 뜻하게 된다. 시작값을 넘는
구간은 실제로 만남을 마쳐야 오른다.

**완료 보상이 `+1.00`이 아니라 `+0.50`인 이유.** `+1.00`이면 완료 6번에 상한을 찍어 열심히
참여한 회원이 전부 `42.00`에 몰리고 관리자가 지표를 봐도 구분이 안 된다.

### 시간 경과 회복이 필수인 이유 — 데드락

논의 중에 나온 문제다. PR C에서 30도 제한을 넣으면 **만남 기반 상승만으로는 원리적
데드락**이 생긴다.

```
신고 4건 → 28.5도 → 매칭 금지 → 만남 불가 → 완료·후기 불가 → 회복 불가 → 영구 배제
```

후기도 마찬가지다. 만남이 있어야 후기를 받는다. 관리자 수동 조정(PR A)이 탈출구이긴 하지만
사람 손에만 의존하면 운영 부담이 된다. 그래서 시간 경과 회복을 함께 넣었다.

회복 주기 30일은 `ReportConfirmationService.AGGREGATION_WINDOW_DAYS`와 같은 값이다. 신고
카운트는 30일이 지나면 집계에서 빠지는데 온도만 영구 하강으로 남던 비대칭
(`docs/19` 4.9가 정리 대상으로 지목한 항목)을 없앤다.

### 완료 보상은 AFTER_COMMIT에서 별도 transaction으로 지급한다

`MatchArrivalService`의 완료 분기에서 직접 올리지 않고 `MatchCompletedEvent`를 발행한다.
이유가 둘이다.

1. **보상 실패가 만남 완료를 롤백하면 안 된다.** 온도는 부가 지표이고 완료는 사용자가 실제로
   수행한 사실이다. 핸들러가 예외를 삼키고 로그만 남기는 이유도 같다.
2. **lock 순서.** 완료 transaction은 `match_groups` → `match_group_members` 순으로 잠근다.
   같은 transaction에서 `members`까지 잠그면 `members` → `match_groups` 순으로 잠그는 탈퇴
   경로(`MemberWithdrawalService`)와 **교차 deadlock**이 생긴다.

기존 `MatchingStateChangedEvent`를 재사용하지 않았다. 그쪽은 WebSocket 상태 동기화 전용이라
`groupId`를 담지 않는데, 보상은 그룹당 1회라 `groupId` 없이는 중복 지급을 막을 수 없다.

**중복 지급은 DB가 막는다.** `uq_manner_temperature_events_match_completed(member_id,
event_type, related_group_id)`다. 완료 API는 반복 호출되는 것이 정상 흐름이라(마지막 도착자
외의 회원이 새로고침) 애플리케이션 가드만 두면 동시 요청에서 뚫린다. 조회 가드는 정상 경로에서
제약 위반 예외로 로그가 오염되는 것을 막는 용도다.

### `V34` 이력 테이블

`match_penalty_events`에 넣지 않았다. 그 테이블의 `chk_match_penalty_events_score_delta`가
`score_delta <> 0`을 강제해서 **온도만 오르고 `penalty_score`는 그대로인 사건을 표현할 수
없다.** 이름 그대로 penalty 사건 기록이기도 해서 보상을 섞으면 "제재 이력"을 읽을 수 없다.

`manner_temperature_events`는 **상승만 담는다.** 하강은 기존 `match_penalty_events`의
`manner_temperature_delta`가, 관리자 조정은 `admin_actions`의 `MANNER_TEMPERATURE_ADJUST`가
이미 기록한다. 기존 두 경로를 이쪽으로 옮기지 않았다.

회복 대상 조회(`MannerTemperatureRecoveryRepository`)는 그래서 두 테이블의 최신 시각 중 **더
최근** 쪽을 기준으로 30일을 센다. 하강만 보면 회복이 매 batch마다 일어나고, 상승만 보면 하강
직후에 바로 회복이 시작돼 제재 효과가 사라진다. 변동 이력이 아예 없으면 가입 시각을 쓴다.

### 회원 노출

매너온도는 지금까지 **회원 화면과 회원용 API 어디에도 없었다.** 관리자 전용 지표였다.
`GET /api/members/me`와 매칭 restriction 응답에 추가하고 두 곳에 그린다.

| 위치 | 형태 |
| --- | --- |
| 마이페이지 | 게이지 + 숫자 + 구간별 안내(`MannerTemperatureBadge`) |
| 매칭 화면 | 우상단 한 줄(`compact`) |

**본인 것만 내려준다.** 다른 회원의 온도는 응답에도 담지 않는다 — 낮은 온도는 "신고를 받은
적이 있다"를 그대로 드러내고, 같은 만남에 있던 사람이 보면 누가 신고했는지 좁힐 수 있다.

**`penalty_score`는 계속 노출하지 않는다.** `MatchingRestrictionResponse`에 예전부터 담겨
있었으나 화면이 그린 적이 없고 이번에도 그리지 않는다. 노쇼 쿨타임을 거는 내부 운영 값이다.

매칭 화면에서는 `IdleForm` 안이 아니라 **페이지 상단**에 뒀다. 안에 두면 대기·완료 화면에서
사라져, 정작 "왜 온도가 올랐지"를 확인하고 싶은 완료 직후에 보이지 않는다. 제재 안내 화면
에서는 감춘다 — 그 화면의 목적은 제재 사유 전달이다.

낮은 구간의 문구는 "왜 낮은가"가 아니라 **"만남을 끝까지 마치면 올라가요"**로 썼다. 회원이
할 수 있는 행동을 알려주지 않으면 숫자만 보고 좌절한다.

### 테스트

- Backend 신규 17건 — `MemberMannerTemperatureRiseTest` 7건(경로별 상한 분리, 상한 clamp,
  신고 1건=완료 4번, 신고 3건까지 30도 위), `MannerTemperatureIntegrationTest` 10건(전원 지급,
  중복 지급 차단, 상한 회원 이력 미생성, 30일 경계, 직전 회복 기준, 시작값 초과 금지, 탈퇴
  회원 제외, 이력 없을 때 가입 시각 기준).
- Frontend 신규 10건 — `MannerTemperatureBadge.test.tsx`(구간 판정, 게이지 범위 clamp,
  null/undefined 미표시, compact, 접근성 라벨, "패널티" 미노출).
- 전체 회귀: backend 1003건 중 실패 2건(둘 다 `dev` 유래 기존 실패), frontend 650건 전체 통과,
  `tsc --noEmit`과 production build 통과.

**차감량 변경으로 기존 테스트 6건이 깨졌고 고쳤다.**
`ReportSafetyAutomationIntegrationTest`가 `31.50`·`21.50` 같은 값을 하드코딩하고 있었다.
숫자를 바꾸는 대신 `afterConfirmedReports(count)` 헬퍼로 정책 상수에서 파생하게 바꿨다 —
차감량은 30도 제한과 맞물려 또 조정될 수 있는 값이라 그때마다 기대값을 전부 고칠 수 없다.

하한 clamp 테스트는 **다시 썼다.** 차감량이 `2.00`이 되면서 시작값에서 하한까지 신고 9건이
필요한데 `uq_reports_reporter_reported_group_reason` 때문에 (reporter, group, reason) 조합을
그만큼 만들 수 없다. 검증 대상은 "몇 건에 도달하는가"가 아니라 "하한을 넘지 않는가"이므로
시작 온도를 하한 바로 위로 옮겨 두고 확인한다.

### 이번 범위에서 제외

- **30도 매칭 제한(PR C).** 값 체계가 dev에서 실제로 어떻게 움직이는지 며칠 보고 넣는 것이
  안전하다. 사용자를 실제로 막는 기능이다.
- 후기 작성 기능과 `member_reviews`(PR D)
- **노쇼의 온도 하강.** 현행 유지다. 노쇼는 `penalty_score`와 쿨타임이 담당하고 매너온도는
  신고 기반 신뢰도로 남긴다. 둘을 섞으면 지표가 무슨 뜻인지 흐려진다.
- 다른 회원의 매너온도 노출
- 이미 깎인 회원의 온도 일괄 재계산 — 시간 경과 회복이 자동으로 끌어올린다
