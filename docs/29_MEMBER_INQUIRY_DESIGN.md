# 1:1 문의 센터 설계

## 1. 배경과 대상

`docs/19_ADMIN_MEMBER_SAFETY_ROADMAP.md` 4.5절이 예약해 둔 항목입니다. 사용자가 프로필에서
1:1 문의를 남기고, 관리자가 별도 메뉴에서 확인·답변합니다.

이 기능의 1순위 사용자는 "궁금한 게 있는 사람"이 아니라 **제재에 이의를 제기하려는 회원**입니다.
4.8절(제재 사유·기간 통보)이 이런 상태로 끝나 있기 때문입니다.

> 문의 경로 — 문의센터 화면(4.5)이 보류라 고객센터 이메일을 안내에 표시한다. (…)
> 영구정지 사용자가 이의를 제기할 유일한 경로다.

## 2. 설계를 지배하는 제약 3개

### 2.1 영구정지(`BANNED`) 회원은 문의 API에 도달할 수 없다

`MemberAccessInterceptor`는 `/api/**` 전체에서 활동이 아닌 요청에도 `requireBrowsable`을
걸고, `MemberAccessPolicy.requireBrowsable`은 `BANNED`를 차단합니다. 애초에
`requireSignedIn`도 `BANNED`를 막으므로 access token 자체가 발급되지 않습니다. 인터셉터 제외
경로는 `/api/auth/**` 하나뿐입니다.

기존 제재 안내 cookie를 자격증명으로 재사용하는 방안도 검토했지만 채택하지 않았습니다.
`SanctionNoticeCookieService`의 cookie는 path가 `/api/auth/sanction-notice`로 좁혀져 있고,
읽는 즉시 `expire()`되는 1회용이며, token TTL이 5분입니다. 문의 작성에 쓰려면 4.8이
의도적으로 좁혀둔 세 가지를 모두 되돌려야 합니다.

**확정: `BANNED`는 고객센터 이메일(`SUPPORT_CONTACT_EMAIL`) 안내를 유지합니다.** 인앱 문의는
`ACTIVE`, `PROFILE_REQUIRED`, `SUSPENDED`까지입니다. 제재 판정을 우회하는 경로를 만들지
않는 쪽을 택했습니다.

### 2.2 관리자 답변을 사용자에게 밀어줄 수단이 없다

4.8절이 이미 조사해 결론을 낸 사실입니다. STOMP는 `/matching`·`/match-room`에서만 연결되고,
Web Push는 VAPID 키·구독 table·권한 UI가 전무하며(`vite-plugin-pwa`가 `generateSW` 전략이라
custom service worker 파일 자체가 없음), 메일 발송 인프라도 없습니다.

**따라서 답변 도달은 사용자가 목록을 다시 여는 pull 방식뿐입니다.** 그래서 미확인 답변
badge가 부가 기능이 아니라 **필수**입니다. `MyPage` 진입점에 badge가 없으면 사용자는 답변이
온 사실을 알 수 없습니다.

### 2.3 새 endpoint를 `SuspendedActivityPolicy`에 등재해야 한다

`SuspendedActivityPolicyCoverageTest`가 `@RestController`를 훑어 상태를 바꾸는 모든 endpoint가
`RESTRICTED` 또는 `ALLOWED`에 분류되어 있는지 전수 검사합니다. 문의 등록·추가 질문은
`ALLOWED`에 넣습니다 — 근거는 기존 신고 허용 근거와 같습니다. 제재 사유를 다툴 수 없으면
제재가 일방적이 됩니다.

## 3. 확정 사항 10건

| # | 항목 | 확정값 | 근거 |
| --- | --- | --- | --- |
| 1 | `BANNED` 이의제기 경로 | 이메일 유지 | 2.1절 |
| 2 | 본문 저장 | 평문 `VARCHAR(2000)` | 3.1절 |
| 3 | 테이블 | `inquiries` + `inquiry_messages` 2개 | 3.2절 |
| 4 | 스레드(추가 질문) | 허용 | 3.2절 |
| 5 | 긴급(`URGENT`) 지정 | 관리자만 | 사용자가 고르면 사실상 전부 긴급으로 들어와 우선순위가 무의미해진다 |
| 6 | 첨부파일 | 제외 | 3.3절 |
| 7 | 보관 기간 | 종결 후 1년, 본문만 익명화 | 5.6절 |
| 8 | 도배 제한 | 미답변 3건 | 문의는 연타보다 누적이 문제다. 댓글의 5초 규칙과 성격이 다르다 |
| 9 | 안전 카테고리 | 두지 않음 | 3.4절 |
| 10 | 담당자 지정 | `assigned_admin_id` 미도입 | 관리자 소수 운영에 과잉. 필요해지면 컬럼을 추가한다 |

### 3.1 본문은 암호화하지 않는다

비공개 1:1이라는 성격만 보면 `member_reviews.comment_encrypted` 쪽에 가깝고,
`ProfileFieldCrypto`(AES-256-GCM)도 이미 있습니다. 그래도 평문을 택했습니다.

- **관리자 키워드 검색이 불가능해집니다.** 문의 관리에서 검색이 안 되면 운영이 성립하지 않습니다.
- **DB `CHECK` 제약을 잃습니다.** 이 저장소는 `char_length(btrim(body)) BETWEEN 1 AND N`을 DB에서
  고정하는 방식인데 `BYTEA`에는 걸 수 없어 애플리케이션 검증만 남습니다.
- **키 분실 시 복구가 불가능합니다.** `PROFILE_ENCRYPTION_KEY`를 공유하면 폭발 반경이 커집니다.

대신 노출을 통제합니다. 관리자만 조회하고, 문의 본문을 로그에 남기지 않고, 작성 폼에
**"전화번호·주소 같은 개인정보는 적지 마세요"** 안내를 둡니다.

### 3.2 테이블을 2개로 나누고 스레드를 허용한다

1테이블(`inquiries.answer_body`)로 하면 답변을 다시 쓸 때 이전 답변이 `UPDATE`로 사라지고,
사용자가 "답변 받았는데 해결이 안 됐어요"를 말할 곳이 없어 **새 문의를 또 만듭니다.** 그러면
관리자가 같은 건을 두 번 보게 됩니다.

2테이블은 목록 조회에서 헤더만 읽고 상세에서만 join하므로 비용도 크지 않습니다. 대가는
`last_message_at`·`last_answered_at` 비정규화 값을 메시지 INSERT와 같은 transaction에서
갱신해야 한다는 것입니다.

### 3.3 첨부파일은 제외한다

현재 파일 업로드는 프로필 이미지 1건뿐이고 private bucket **본인 전용 중계**
(`GET /api/members/me/profile-image`)입니다. 문의 첨부는 관리자가 봐야 하므로 "타인이 남의
파일을 보는" 중계 경로를 새로 설계해야 하고, 인가·용량·MIME 검증·수명주기가 전부 새 범위가
됩니다. 화면 오류 제보는 텍스트로 받습니다.

### 3.4 안전(신고 성격) 카테고리를 두지 않는다

`docs/19` 4.8이 확정한 **신고자 보호 제약**과 충돌합니다.

> 신고자 identity를 노출하지 않는다. `reasonCode` 수준까지만 노출하고 "신고 3건 누적"처럼
> 신고자 수를 추정할 수 있는 문구는 사용하지 않는다.

문의 답변은 관리자 자유 입력입니다. 안전 카테고리를 열어두면 관리자가 답변에 신고 관련
사실을 적어 신고자를 좁히는 단서를 줄 위험이 생깁니다. 구조화 신고 경로
(`POST /api/match-groups/{groupId}/reports`)가 이미 있으므로, 작성 폼에서 "동행 중 문제는
신고 기능을 이용해 주세요"로 안내합니다.

## 4. DB 설계 (`V31__add_member_inquiries.sql`)

### 4.1 Migration 적용 주의

기존 `V1`~`V30`은 수정하지 않습니다. 적용 전 공유 dev DB의 `flyway_schema_history`에서
`V31`이 비어 있는지 확인합니다.

**이 절의 경고가 실제로 발생했습니다.** 처음에는 저장소 파일 목록상 마지막이 `V27`이라
`V28`로 만들었는데, 협업자가 저장소에 push하지 않은 채 공유 dev DB에 `V28`~`V30`을 먼저
적용해 둔 상태였습니다. 그 결과 Flyway가 `Migration checksum mismatch for migration version 28`
로 부팅을 막았고, 번호를 `V31`로 옮겨 해결했습니다.

**교훈: 번호는 저장소 파일 목록이 아니라 공유 dev DB의 `flyway_schema_history`를 기준으로
정해야 합니다.** push되지 않은 마이그레이션은 저장소에 보이지 않습니다.

### 4.2 `inquiries` (헤더)

| 항목 | 내용 |
| --- | --- |
| 목적 | 문의 스레드 1건의 상태와 목록·badge용 파생값을 담는다. |
| 주요 컬럼 | `id`, `member_id`, `category`, `title`, `status`, `priority`, `last_message_at`, `last_answered_at`, `member_read_at`, `closed_at`, `anonymized_at`, `created_at`, `updated_at` |
| FK | `member_id -> members.id` `ON DELETE RESTRICT` |
| CHECK | `category`/`status`/`priority` 열거, `title` 1~100자, `(status = 'CLOSED') = (closed_at IS NOT NULL)`, `status IN ('ANSWERED','CLOSED')`이면 `last_answered_at NOT NULL`, `anonymized_at`은 `CLOSED`에서만 |
| INDEX | `idx_inquiries_member_created_at`, `idx_inquiries_status_created_at`, `idx_inquiries_open`(partial), `idx_inquiries_retention`(partial) |
| 개인정보/보안 | 제목에도 개인정보가 들어올 수 있어 보관 정책 대상이다. 관리자만 조회한다. |

`chk_inquiries_closed_at`은 `content_comments.chk_content_comments_deleted_at`과 같은
관용구입니다 — 상태와 시점 컬럼을 서로 묶어 DB에서 고정합니다.

**미확인 답변은 컬럼으로 저장하지 않습니다.**
`last_answered_at IS NOT NULL AND (member_read_at IS NULL OR member_read_at < last_answered_at)`
로 조회 시점에 계산합니다. boolean 컬럼을 두면 `like_count`와 같은 카운터 정합성 문제를
새로 만듭니다(`docs/27` 2.2).

### 4.3 `inquiry_messages` (본문)

| 항목 | 내용 |
| --- | --- |
| 목적 | 스레드의 발화 1건. 사용자 문의와 관리자 답변을 같은 table에 담는다. |
| 주요 컬럼 | `id`, `inquiry_id`, `author_type`, `author_member_id`, `body`, `created_at` |
| FK | `inquiry_id -> inquiries.id`, `author_member_id -> members.id` 모두 `ON DELETE RESTRICT` |
| CHECK | `author_type IN ('USER','ADMIN')`, `body` 1~2000자 |
| INDEX | `idx_inquiry_messages_inquiry`, `idx_inquiry_messages_author_created_at` |

`author_type`을 따로 두는 이유: `author_member_id != inquiries.member_id`로 관리자를
유추하면 관리자가 자기 문의에 답할 때 판정이 깨집니다.

## 5. 로직 설계

### 5.1 문의 등록 — `POST /api/members/me/inquiries`

`@Transactional`

1. 인증 필수. `BANNED`는 `MemberAccessInterceptor`가 이미 막는다(2.1).
2. `category`는 enum 검증. `priority`는 **요청에서 받지 않는다**(확정 5번).
3. `title` 1~100자, `body` 1~2000자를 trim 후 검증.
4. **미답변 3건 제한**: 같은 회원의 `status IN ('RECEIVED','IN_PROGRESS')`가 3건 이상이면 `429`.
   - **한계**: 동시 요청은 둘 다 통과할 수 있다. 사람이 반복 등록하는 수준만 막는 완화책이며,
     엄격히 막으려면 회원 단위 advisory lock이 필요해 MVP 과잉으로 제외했다(`docs/27` 5.2와
     같은 판단).
5. `inquiries` INSERT → `inquiry_messages` INSERT(`author_type = 'USER'`) → `201`.

`PROFILE_REQUIRED` 회원도 **허용합니다.** 댓글은 표시할 닉네임이 없어 거절하지만
(`CONTENT_COMMENT_PROFILE_REQUIRED`), 가입이 막혀서 문의하는 경우가 실제 시나리오입니다.

### 5.2 내 문의 목록 — `GET /api/members/me/inquiries`

`@Transactional(readOnly = true)`. offset 페이징(`page`/`size`)을 씁니다 — 본인 문의는 건수가
적어 cursor가 필요 없고, `ContentCommentListResponse`와 같은 형태를 재사용합니다.

각 항목에 `hasUnansweredReply`(4.2의 계산식)를 담습니다.

### 5.3 스레드 상세 — `GET /api/members/me/inquiries/{inquiryId}`

`@Transactional`(읽기 전용이 아님)

1. 본인 문의가 아니면 `403`, 없으면 `404`.
2. 메시지를 `id` 오름차순으로 반환.
3. **`member_read_at`을 현재 시각으로 갱신한다.**

`GET`이 상태를 바꾸는 것은 논쟁 여지가 있지만, 화면이 상세를 열었다는 사실 자체가 읽음이고
별도 `PUT .../read`로 호출을 2번으로 늘릴 이유가 약해 이 쪽을 택했습니다. 갱신은
`member_read_at < :now` 조건부라 멱등합니다.

### 5.4 추가 질문 — `POST /api/members/me/inquiries/{inquiryId}/messages`

`@Transactional`

1. 본인 문의 검증. `CLOSED`면 `409`(`INQUIRY_CLOSED`) — 새 문의로 등록하도록 안내한다.
2. `body` 검증 후 INSERT(`author_type = 'USER'`).
3. `last_message_at` 갱신. **`ANSWERED`였으면 `IN_PROGRESS`로 되돌린다** — 그러지 않으면
   관리자 미처리 목록에 다시 뜨지 않아 재질문이 묻힌다.

### 5.5 관리자 답변 — `POST /api/admin/inquiries/{inquiryId}/messages`

`@Transactional`

1. `AdminAuthorizationService.requireAdmin`.
2. `CLOSED`면 `409`.
3. INSERT(`author_type = 'ADMIN'`, `author_member_id = 관리자`).
4. `status = 'ANSWERED'`, `last_answered_at = now`, `last_message_at = now`.

`admin_actions`에는 기록하지 않습니다. 그 table은 `action_type` CHECK가 제재·신고 처리
값으로 고정되어 있고(`WARNING`/`SUSPEND`/`REPORT_RESOLVE` 등), 문의 답변은 회원 제재가
아닙니다. 새 `action_type`을 추가하면 `V31`이 기존 CHECK를 건드려야 합니다.

### 5.6 보관 정책 — 종결 후 1년

`InquiryRetentionScheduler`가 `InquiryRetentionService.anonymizeBatch()`를 호출합니다.
`MemberSuspensionExpiryScheduler`와 같은 형태(`@ConditionalOnProperty`로 기본 비활성,
`fixedDelayString` 설정 주입)입니다.

- 대상: `status = 'CLOSED' AND closed_at < now - 1년 AND anonymized_at IS NULL`
- 처리: `inquiry_messages.body`를 고정 문구(`보관 기간이 지나 삭제된 내용입니다.`)로 덮고,
  `inquiries.title`도 같은 방식으로 덮은 뒤 `anonymized_at`을 기록한다.
- 남기는 것: `category`, `status`, `created_at`, `closed_at`. 통계와 감사 목적이다.

`anonymized_at`이 재처리를 막는 원인 key입니다. 본문을 지웠는지 문구 비교로 판정하면
사용자가 같은 문구를 입력한 경우와 구분되지 않습니다.

### 5.7 관리자 목록 — `GET /api/admin/inquiries`

`AdminReportService.list`와 같은 구조입니다. HMAC 서명 cursor + filter fingerprint를 쓰고
정렬 키는 `(created_at DESC, id DESC)`입니다.

**긴급 우선 정렬은 넣지 않습니다.** `ORDER BY`에 `priority`를 넣으면 cursor payload에도
`priority`가 들어가야 하고, 정렬 키와 cursor 키가 어긋나면 페이지 경계에서 항목이
중복·누락됩니다. 대신 `priority` 필터를 제공해 관리자가 `URGENT`만 따로 볼 수 있게 합니다.

### 5.8 상태 전이

| 현재 | `IN_PROGRESS` | `ANSWERED` | `CLOSED` |
| --- | --- | --- | --- |
| `RECEIVED` | 관리자 `PATCH` | 관리자 답변 | 관리자 `PATCH` |
| `IN_PROGRESS` | — | 관리자 답변 | 관리자 `PATCH` |
| `ANSWERED` | 사용자 추가 질문 | 관리자 답변 | 관리자 `PATCH` |
| `CLOSED` | 불가 | 불가 | — |

`CLOSED`는 종단입니다. 재개하려면 새 문의를 등록합니다.

### 5.9 잠금 순서

`docs/19` 5장이 "member → report 순서"를 규칙으로 고정해 뒀습니다. 문의는 회원 row를 잠글
필요가 없어(제재·penalty를 건드리지 않음) `inquiries`만 `FOR UPDATE`로 잠급니다. 나중에
문의 처리가 회원 상태를 바꾸게 되면 **member → inquiry 순서**를 따라야 합니다.

## 6. API 계약

### 사용자

```http
POST   /api/members/me/inquiries                       201
GET    /api/members/me/inquiries?page=&size=            200
GET    /api/members/me/inquiries/unread-count           200
GET    /api/members/me/inquiries/{inquiryId}            200
POST   /api/members/me/inquiries/{inquiryId}/messages   201
```

### 관리자

```http
GET    /api/admin/inquiries?status=&category=&priority=&createdFrom=&createdTo=&cursor=&size=
GET    /api/admin/inquiries/{inquiryId}
POST   /api/admin/inquiries/{inquiryId}/messages
PATCH  /api/admin/inquiries/{inquiryId}
```

`PATCH`는 `status`와 `priority`를 함께 받습니다(둘 다 optional). 긴급 지정이 관리자 전용이라
별도 endpoint를 두지 않았습니다.

### 신규 `ErrorCode`

| code | status | 용도 |
| --- | --- | --- |
| `INQUIRY_INVALID_REQUEST` | 400 | 제목·본문·카테고리 검증 실패 |
| `INQUIRY_NOT_FOUND` | 404 | 없는 문의 |
| `INQUIRY_FORBIDDEN` | 403 | 남의 문의 접근 |
| `INQUIRY_CLOSED` | 409 | 종결된 문의에 추가 질문·답변 |
| `INQUIRY_TOO_MANY_OPEN` | 429 | 미답변 3건 초과 |
| `ADMIN_INQUIRY_INVALID_REQUEST` | 400 | 관리자 filter·요청 값 |
| `ADMIN_INQUIRY_NOT_FOUND` | 404 | 관리자 조회 실패 |
| `ADMIN_INQUIRY_STATUS_CONFLICT` | 409 | 허용되지 않는 상태 전이 |

## 7. 개인정보와 보안

- 본문·제목은 평문이지만 **관리자와 작성자 본인에게만** 노출합니다.
- 사용자 응답에 관리자 `memberId`·닉네임을 담지 않습니다. 답변 작성자는 `author_type`으로만
  구분하고 화면은 "운영팀"으로 표시합니다. 관리자 개인을 특정할 이유가 없습니다.
- 관리자 응답에는 작성자 회원 요약(`memberId`, `nickname`, `status`)을 담습니다 — 제재 이의제기
  판단에 회원 상태가 필요합니다. `AdminReportMemberSummaryResponse`와 같은 범위입니다.
- 문의 본문을 로그에 남기지 않습니다.
- 작성 폼에 개인정보 입력 자제 안내를 노출합니다(3.1).

## 8. 화면 설계

### 8.1 사용자

```text
/mypage                       "1:1 문의" 행 + 미확인 답변 badge
/mypage/inquiries             목록 + "문의하기" 버튼
/mypage/inquiries/new         작성 폼 (카테고리 · 제목 · 본문 + 개인정보 안내)
/mypage/inquiries/:inquiryId  스레드 (사용자/운영팀 말풍선 + 추가 질문 입력)
```

목록은 `BlockedMembersPage`의 상태 분기 패턴(loading / 빈 상태 / 오류·재시도)을 따릅니다.
`AccountRestrictionNotice`에서 `SUSPENDED` 회원은
`/mypage/inquiries/new?category=SANCTION_APPEAL`로 유도할 수 있습니다.

### 8.2 관리자

`AdminNav.MENU_ITEMS`에 `/admin/inquiries`를 5번째로 추가하고, 미처리(`RECEIVED`·`IN_PROGRESS`)
건수 badge를 붙입니다. `/admin/reports`의 `openSafetyAlertCount` 선례대로 조회 실패 시 badge를
표시하지 않고 관리자 화면 자체는 막지 않습니다.

`AdminInquiriesPage`는 `AdminReportsPage` 구조(필터 select + 목록 + 상세 dialog +
`useDialogKeyboard`)를 재사용합니다.

## 9. 이번 범위에서 제외

- 첨부파일(3.3)
- Web Push·이메일 답변 알림(2.2)
- `BANNED` 회원의 인앱 이의제기(2.1)
- 관리자 담당자 배정·SLA 관리(확정 10번)
- 문의 통계(대시보드) — `docs/19` 2장의 "기본 통계"에서 함께 다룬다
- 답변 템플릿·매크로

## 10. 테스트 우선순위

`AGENTS.md`가 인증·인가를 테스트 우선 대상으로 지정합니다.

1. **인가** — 남의 문의 조회·추가 질문 차단, 비관리자의 `/api/admin/inquiries` 차단
2. **`SuspendedActivityPolicyCoverageTest` 통과** — 신규 endpoint 분류(2.3)
3. **`SUSPENDED` 회원의 문의 등록 성공** — 이의제기 경로가 실제로 살아 있는지
4. **상태 전이** — 답변 시 `ANSWERED` + `last_answered_at`, 추가 질문 시 `ANSWERED → IN_PROGRESS`,
   `CLOSED` 후 추가 질문·답변 거절
5. **미확인 답변 판정** — `member_read_at`/`last_answered_at` 경계
6. **미답변 3건 제한**
7. **보관 익명화** — 1년 경과 대상만, `anonymized_at`으로 재처리 방지
8. **cursor 페이징** — filter fingerprint 불일치 거절
