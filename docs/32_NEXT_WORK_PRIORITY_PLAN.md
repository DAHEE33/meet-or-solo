# 다음 작업 우선순위 계획 — 인수인계(docs/31) + 추가 요청 통합

- 상태: **A·B·C/D·E·F 전부 구현 완료.** 수동 검증 대기(절차는 [docs/30](30_MATCHING_MANUAL_TEST_SCENARIOS.md))
- 구현 결과 요약은 8절, 남은 확인 사항은 9절

## 1. 이 문서의 목적

[`docs/31`](31_WBS10B_HANDOVER.md)로 넘겨받은 남은 작업 3건과, 사용자가 별도로 요청한 수정
3건을 **하나의 목록으로 합치고 착수 순서를 정한다.** 중복 항목을 먼저 걷어내고, 착수 전에
확정해야 하는 결정을 각 항목마다 적었다.

구현은 이 문서로 시작하지 않는다. 4절 우선순위와 5절 결정 항목을 확정한 뒤, 항목 하나씩
브랜치를 나눠 진행한다.

## 2. 항목 인벤토리와 중복 판정

| # | 항목 | 출처 | 중복 여부 |
| --- | --- | --- | --- |
| **A** | 체크인·도착 오류 문구 정리 | 추가 요청 | **신규.** 인수인계에 없다 |
| **B** | GPS 반경 검증 바이패스가 안 된다 | 추가 요청 | **부분 중복.** 기능은 이미 구현돼 있다([`docs/19`](19_ADMIN_MEMBER_SAFETY_ROADMAP.md) 4.12, `V36`). 미구현이 아니라 **사용 경로가 바뀐 것**이다 |
| **C** | 알림을 서버 테이블에 저장 | 추가 요청 | **완전 중복.** `docs/31` 3.1 "알림 2단계"와 같은 작업이다 |
| **D** | 알림 2단계 — 서버 알림함 | `docs/31` 3.1 | C와 동일 항목 |
| **E** | 알림 3단계 — PWA Web Push | `docs/31` 3.2 | 단독 |
| **F** | 30도 매칭 제한 (4.9 PR C) | `docs/31` 3.3 | 단독 |
| **G** | 매칭 즉시 조합 경로 제거 | 임베딩 검증 중 발견 | **신규.** 10절 참고 |

결론: **실제 작업 항목은 A, B, C(=D), E, F 다섯 개**다.
(G는 A~F 구현 완료 후 임베딩 검증 과정에서 발견됐다. 10절에 따로 적었다.)

## 3. 추가 요청 3건 분석

### 3.1 A — 체크인 오류 문구가 실제 원인과 다르다

#### 현상

축제 반경 밖에서 체크인하면 "범위를 벗어났어요"가 아니라
**"위치 정확도가 낮아요"**가 먼저 뜬다.

#### 원인 — 검증 순서

`FestivalCheckinService.checkIn()`은 **정확도를 거리보다 먼저** 본다.

| 위치 | 검증 |
| --- | --- |
| `FestivalCheckinService.java:70-74` | `accuracyMeters > 100`이면 `LOW_LOCATION_ACCURACY` |
| `FestivalCheckinService.java:76-85` | 그 뒤에 거리 계산 → `CHECKIN_OUT_OF_RANGE` |

임계값은 `FESTIVAL_CHECKIN_ACCURACY_THRESHOLD_METERS:100`(`application.yml:58`)이다.
PC·실내·Wi-Fi 측위에서는 `accuracy`가 수백~수천 m로 나오는 일이 흔하다. 그러면 **실제 원인이
"현장에 없다"여도 화면에는 "정확도가 낮아요"만 뜬다.** 사용자는 GPS 문제로 오해하고 같은 자리에서
다시 누른다.

#### 선택지

| 안 | 내용 | 평가 |
| --- | --- | --- |
| **A-1 문구만 교체** | `LOW_LOCATION_ACCURACY` 문구를 "현재 위치를 확인하지 못했어요"류로 바꾼다 | 가장 싸다. 다만 **범위 밖인 사람에게는 여전히 엉뚱한 안내**다 |
| **A-2 순서 교체(권장)** | 거리를 먼저 재서 반경 밖이면 `CHECKIN_OUT_OF_RANGE`, 반경 안인데 정확도가 낮으면 `LOW_LOCATION_ACCURACY` | 원인과 문구가 일치한다. 구현 몇 줄. **정확도가 낮은 좌표로 거리를 판정하게 되는 점**이 남는다 |
| **A-3 오차 반영** | `거리 - accuracy > 반경`이면 확실히 범위 밖으로 거절, 오차가 반경에 걸치고 정확도도 임계 초과면 정확도 오류, 나머지는 통과 | 가장 정확하다. 다만 **체크인이 지금보다 관대해진다**(정책 변경) |

**권장은 A-2 + 문구 개선.** A-3은 반경 검증을 느슨하게 만드는 정책 변경이라 별도 확정이 필요하다.

#### 함께 정리할 것

| 대상 | 지금 | 제안 |
| --- | --- | --- |
| `CHECKIN_OUT_OF_RANGE` | "축제 반경을 벗어난 위치예요. 축제 현장 안에서 다시 시도해주세요."(`checkinError.ts:7`) | 유지하되 **떨어진 거리 안내 추가 검토** — 성공 응답은 이미 `distanceMeters`를 돌려준다 |
| `LOW_LOCATION_ACCURACY` | "위치 정확도가 낮아요. GPS가 잘 잡히는 곳에서 다시 시도해주세요." | "위치를 정확히 잡지 못했어요. 실내나 지하라면 밖에서 다시 시도해주세요."류로 원인·행동을 분리 |
| `MATCHING_ARRIVAL_OUT_OF_RANGE` | **프론트 매핑이 없다.** 서버 문구("만남 장소 근처에서 도착을 인증해주세요.")가 그대로 노출 | `checkinError.ts`와 같은 매핑을 도착에도 두어 일관되게 |

#### 영향 범위

- backend: `FestivalCheckinService`, `ErrorCode`(문구), `FestivalCheckinServiceTest`
- frontend: `utils/checkinError.ts`, `checkinError.test.ts`, 도착 오류 매핑(신규)
- migration 없음

### 3.2 B — GPS 바이패스가 동작하지 않는 것으로 보인다

#### 코드 상태 — 기능은 이미 있다

반경 검증을 건너뛰는 경로는 **두 가지**이고, 체크인·도착 양쪽에 모두 들어가 있다.

| 경로 | 뜻 | 구현 |
| --- | --- | --- |
| 설정 `bypass-radius-check` | 환경 전체를 끈다. **모든 환경 기본값 `false`** | `FestivalCheckinService.java:69`, `MatchArrivalService.java:128` |
| `members.test_account` | 그 계정만 면제 | 같은 줄. `V36__add_member_test_account.sql` |

즉 **"바이패스가 구현되지 않은" 상태가 아니다.** `docs/10` 기록대로 dev 방침이
**환경 전체 우회 → 계정 단위 면제**로 바뀌면서 예전 방식(환경변수 `true`)이 더는 기본 경로가
아니게 된 것이다.

#### 안 되는 것으로 보이는 원인 후보

| 후보 | 근거 | 확인 방법 |
| --- | --- | --- |
| ① 환경변수 방식을 기대했다 | `.env`에 `FESTIVAL_CHECKIN_BYPASS_RADIUS_CHECK`가 없고 기본이 `false`. 도착 우회 변수는 `.env.example`에서 **의도적으로 제거**됐다(`.env.example:103`) | 계정 단위로 전환됐음을 확인 |
| ② 테스트 계정을 지정할 방법이 없다 | 지정은 `/admin/members` 화면에서 하는데 **관리자 계정이 있어야** 들어간다. 로컬 관리자 로그인([`docs/30-2`](30-2_SUPER_ADMIN_LOCAL_LOGIN_DESIGN.md))은 **코드가 이미 있다**(`AdminLoginService`, `SuperAdminAccountBootstrap`). 다만 `.env`에 `ADMIN_LOCAL_USERNAME`·`ADMIN_LOCAL_PASSWORD`가 없으면 **계정이 아예 생성되지 않는다** — `.env.example`에도 그 항목이 없어 채울 값이 있는지 알 수 없었다 | `SELECT count(*) FROM admin_credentials` |
| ③ 지정 가능한 상태가 아니다 | `ACTIVE`·`PROFILE_REQUIRED`만 허용한다(`AdminMemberService.validateTestAccountStatus`) | 회원 `status` 확인 |
| ④ 막힌 이유가 GPS가 아니다 | 면제 대상은 **반경·정확도뿐**이다. 체크인 만료(1시간), 쿨타임, 활성 그룹 중복, 희망 인원 불일치는 그대로 막는다 | 응답 `code` 확인 — `CHECKIN_OUT_OF_RANGE`가 아니면 GPS 문제가 아니다 |

#### 진단 절차 (코드 수정 전에 이것부터)

1. **응답 코드 확인.** 체크인·도착 실패 응답의 `code`가 `CHECKIN_OUT_OF_RANGE` /
   `LOW_LOCATION_ACCURACY` / `MATCHING_ARRIVAL_OUT_OF_RANGE` 중 무엇인가. 셋 다 아니면 GPS 경로가
   아니다
2. **플래그 확인.** `SELECT id, status, test_account FROM members WHERE id IN (...)`
3. **서버 로그 확인.** 면제가 실제로 걸리면 `WARN`이 남는다 — "GPS 반경 검증을 건너뛰고",
   "도착 반경 검증을 건너뛰고", 사유는 `TEST_ACCOUNT` / `CONFIG_BYPASS`

1~3에서 "플래그가 `true`인데 거절"이 확인되면 그때가 진짜 결함이다.

#### 예상 작업

진단해 보니 **코드 결함이 아니었다.** 관리자 로컬 로그인은 이미 구현돼 있었고, 막힌 지점은
`.env`에 `ADMIN_LOCAL_USERNAME`·`ADMIN_LOCAL_PASSWORD`가 없어 **슈퍼관리자 계정 자체가 생성되지
않은 것**이었다(`SuperAdminAccountBootstrap`은 둘 다 있을 때만 계정을 만든다). `.env.example`에도
그 항목이 없어, 무엇을 채워야 하는지 알 방법이 없었다.

그래서 실제 작업은 **접근 경로를 여는 쪽**이 됐다.

- `.env.example`에 `ADMIN_LOCAL_*` 항목과 "없으면 계정이 생성되지 않는다"는 설명 추가
- `scripts/diagnose-gps-bypass.sql` — 응답 코드·플래그·다른 차단 사유를 한 번에 조회하고,
  관리자 화면에 못 들어갈 때 쓸 테스트 계정 지정 SQL을 함께 둔다
- `docs/30` 4.5에 "GPS로 막혔을 때 3단계 진단"과 환경 변수 절의 관리자 계정 안내

### 3.3 C — 알림을 서버에 저장 (= `docs/31` 3.1, 완전 중복)

#### 지금 상태

`notificationStore.ts`가 `localStorage`에 **최근 20건**만 남긴다(`MAX_NOTIFICATIONS = 20`,
키 `meet-or-solo.notifications.v1`). 서버에는 아무것도 저장하지 않는다. 그래서

- 다른 기기에서 보이지 않는다
- 저장소를 지우면 사라진다
- **로그인해도 못 본 알림이 복원되지 않는다** ← 요청의 핵심

세션·쿠키가 아니라 `localStorage`이지만, "기기 로컬에만 있다"는 한계는 같다.

#### 설계 초안

| 항목 | 내용 |
| --- | --- |
| 테이블 | `notifications` — `id`, `member_id`, `reason`, `actor_member_id`, `occurred_at`, `read_at`, `created_at`. `(member_id, created_at DESC)` index |
| 조회 | `GET /api/members/me/notifications` — cursor pagination(기존 관리자 목록 패턴 재사용) |
| 읽음 | `PATCH /api/members/me/notifications/read`(전체) 또는 `/{id}/read`(개별) — 5절에서 확정 |
| 저장 지점 | `MatchingStateChangedEventHandler.handle()`. WebSocket 발송과 **같은 자리**다 |
| 프론트 | `notificationStore`를 서버 목록과 합친다. 로그인 직후 1회 조회 + WebSocket 수신분 누적 |

**저장 실패가 실시간 알림을 막으면 안 된다.** `MannerTemperatureRewardService`가
`AFTER_COMMIT` + 별도 transaction으로 분리된 것과 같은 이유다.

#### 저장 대상 사유

현재 사유는 12종이다.

| 성격 | 사유 | 저장 제안 |
| --- | --- | --- |
| 종결·행동 필요 | `MATCH_PROPOSED`, `MATCH_CONFIRMED`, `MATCH_REJECTED`, `MATCH_TIMEOUT`, `MATCH_INSUFFICIENT_MEMBERS`, `MATCH_CANCELLED`, `MATCH_COMPLETED` | **저장** |
| 중간 상태 | `MATCH_ACCEPTED`, `ARRIVAL_TIME_SELECTED`, `MEMBER_ARRIVED`, `ALL_ARRIVED`, `MEMBER_LEFT`, `MEMBER_CANCELLED` | **미저장 검토** — 남기면 목록이 시끄럽다 |

#### 함께 해결되는 것

`actor_member_id`를 실으면 `docs/31` 5절의 **"알림 자기 반향"**(내가 도착을 눌러도 나에게
`MEMBER_ARRIVED`가 오는 문제)이 같이 해결된다.

### 3.4 E — Web Push (알림 3단계)

앱이 꺼져 있어도 닿아야 하는 알림이 하나 있다. **`MATCH_PROPOSED`**다 — 응답 시간이 30초이고
놓치면 `penalty_score +1`에 쿨타임 2분이 붙는다. WebSocket은 화면이 열려 있을 때만 닿는다.

#### 구조

| 항목 | 내용 |
| --- | --- |
| 키 | VAPID 키 쌍을 환경변수로 주입(`WEB_PUSH_VAPID_PUBLIC_KEY`·`_PRIVATE_KEY`·`_SUBJECT`). **저장소에 넣지 않는다** |
| 구독 저장 | `push_subscriptions`(endpoint, p256dh, auth, member_id). endpoint가 구독의 신원이다 |
| 발송 | backend `WebPushSender` — 전용 thread pool. 요청 thread에서 외부 HTTP를 기다리지 않는다 |
| service worker | `generateSW` → **`injectManifest`**. 생성된 파일에는 `push` 핸들러를 넣을 수 없다 |
| 권한 요청 | **첫 매칭 신청 직전** 한 번. 앱을 켜자마자 물으면 무엇에 쓰는지 모르는 채 결정하게 된다 |

#### 무엇을 push로 보내는가

알림함보다 좁다. push는 잠금 화면까지 올라오는 가장 시끄러운 경로라 **지금 손을 쓰지 않으면
손해가 나는 것**만 보낸다 — `MATCH_PROPOSED`, `MATCH_CONFIRMED` 둘이다. 매칭이 끝났다는 소식
(거절·시간 초과·취소·완료)은 알림함에만 남는다.

#### 문구를 서버가 만들지 않는다

payload에는 WebSocket과 같은 `{reason, occurredAt}`만 싣고, 문구와 이동 경로는 service worker가
프론트의 매핑(`notificationMessages.ts`)으로 정한다. 서버가 문장을 담으면 문구를 고칠 때
이미 보낸 알림과 화면이 갈린다.

#### 주의

- **키가 없으면 push만 꺼진다.** local에서 VAPID 키를 만들지 않아도 WebSocket 알림과 알림함은
  그대로 동작해야 한다
- **iOS는 홈 화면에 설치해야 push가 온다**(Safari 16.4+). 설치하지 않은 iOS Safari에서는
  `isPushSupported()`가 `false`이고, 그 경우 아무것도 묻지 않는다
- `injectManifest`로 바꾸면 캐싱 라우팅이 우리 코드로 넘어온다. **OAuth 로그인이 navigation
  request라 fallback denylist(`/api`, `/ws`)를 빠뜨리면 로그인이 조용히 실패한다** — 기존
  `vite.config.ts` 주석에 남아 있던 사고이고, 같은 denylist를 `sw.ts`로 옮겼다

## 4. 최종 우선순위

| 순위 | 작업 | 출처 | 왜 이 순서인가 | 규모 | migration |
| --- | --- | --- | --- | --- | --- |
| **0** | **B — GPS 바이패스 진단** | 추가 | 이후 모든 수동 검증(`docs/30`)이 이것에 막힌다. 코드 수정이 아닐 수도 있어 먼저 확인한다 | 0.5일 | 없음 |
| **1** | **A — 체크인·도착 오류 문구** | 추가 | 독립적이고 작다. 0번을 진단하는 동안에도 계속 오진을 만들어 내는 원인이다 | 0.5일 | 없음 |
| **2** | **C/D — 알림 2단계(서버 알림함)** | 추가 + `docs/31` 3.1 | 사용자 요청과 인수인계가 겹치는 유일한 항목. 3단계(Push)의 **전제**이기도 하다 | 2~3일 | **필요** |
| **3** | **F — 30도 매칭 제한** | `docs/31` 3.3 | 정책 결정이 선행돼야 한다. 2번과 파일이 겹치지 않아 병렬 가능 | 1~2일 | 불필요(컬럼 존재) |
| **4** | **E — 알림 3단계 Web Push** | `docs/31` 3.2 | 2번이 끝나야 의미가 있다. `VitePWA`를 `injectManifest`로 바꾸는 작업이라 캐싱·오프라인 회귀 위험이 가장 크다 | 3일+ | **필요**(`push_subscriptions`) |

### 보류

| 항목 | 이유 |
| --- | --- |
| 4.9 PR D 후기(`member_reviews`) | `docs/31` 4절 — 담당 아님 |
| 4.7 잔여(임베딩 재시도·재동의) | `docs/31` 4절 — 나중 |
| 노쇼 페널티 면제 | `docs/31` 5절 — 신고 경로로 구제 가능. 고치려면 흐름 자체를 바꿔야 한다 |

### 병렬 진행

2번과 3번은 파일이 겹치지 않는다(`docs/19` 7절, `docs/31` 7절). 다만 **2번이 migration을 잡으므로
번호를 정하기 전에 공유 dev DB의 `flyway_schema_history`를 조회하고 서로 알려야 한다.**
저장소 마지막은 `V38`이지만 **저장소 파일 목록으로 번호를 정하지 않는다** — `docs/10`에 같은
사고가 3회 기록돼 있다.

## 5. 착수 전 확정할 결정

| # | 항목 | 관련 | 선택지 |
| --- | --- | --- | --- |
| 1 | 체크인 검증 순서를 바꿀 것인가 | A | A-1 문구만 / **A-2 순서 교체(권장)** / A-3 오차 반영 |
| 2 | 오류 문구에 **떨어진 거리**를 노출할 것인가 | A | 노출("약 1.2km 떨어져 있어요") / 미노출 |
| 3 | 관리자 로컬 로그인(`docs/30-2`)을 지금 구현할 것인가 | B | **이미 구현돼 있었다.** 필요한 것은 `.env` 항목과 안내였다(8절) |
| 4 | 알림 **보관 기간·건수** | C | 30일 / 100건 / 둘 다 |
| 5 | 중간 상태 알림을 **저장할 것인가** | C | 저장 안 함(권장) / 전부 저장 |
| 6 | 읽음 처리 단위 | C | 목록을 열면 전체 읽음(현행 유지) / 개별 읽음 |
| 7 | 30도 제한에 걸린 사람에게 **무엇을 보여줄 것인가** | F | 카운트다운 / 사유 없이 안내만 / 회복 방법까지 안내 |

7번은 `docs/19` 4.8의 신고자 보호와 얽힌다. **온도가 낮은 이유는 곧 "신고를 받았다"**이므로
얼마나 드러낼지를 먼저 정해야 한다.

## 6. 공통 주의

- **migration 번호는 공유 dev DB의 `flyway_schema_history`를 조회해서 정한다.** push하지 않은
  migration을 공유 dev DB에 적용하지 않는다. `flyway repair`를 쓰지 않는다
- 공유 dev DB는 **조회만** 한다. 시간 조작·초기화는 로컬 DB에서 한다(`docs/30` 4.0)
- 브랜치는 `dev`에서 분기하고 PR로 `dev`에 병합한다(`docs/12`). `main`으로 직접 올리지 않는다
- 테스트: `cd backend && ./gradlew test`(JDK 17, Docker Desktop 필요),
  `cd frontend && npx vitest run && npx tsc -b`
- **baseline 실패 2건은 정상이다** — `ContentBookmarkCommentIntegrationTest`,
  `FestivalRepositoryIntegrationTest`

## 7. 브랜치 제안

```text
fix/wbs-10-b-checkin-error-message           — A (문구·검증 순서)
feature/wbs-10-b-notification-inbox          — C/D (알림 2단계, migration 필요)
feature/wbs-10-b-matching-temperature-limit  — F (30도 제한, docs/19 7절에 예약된 이름)
feature/wbs-10-b-web-push                    — E (알림 3단계)
```

B는 진단 결과에 따라 A 브랜치에 흡수하거나 문서·스크립트 전용 브랜치로 분리한다.

## 8. 구현 결과 (2026-09-15)

### A — 체크인·도착 오류 문구

| 변경 | 내용 |
| --- | --- |
| 검증 순서 | `FestivalCheckinService`가 **거리를 먼저** 보고, 반경 안일 때만 정확도를 본다 |
| 거리 노출 | 반경 밖 거절 문구에 "축제에서 약 1.2km 떨어져 있어요. 체크인은 축제 반경 500m 안에서 할 수 있어요." — `DistanceText`가 10m 단위로 반올림한다 |
| 도착도 동일 | `MatchArrivalService`도 같은 방식으로 거리를 담는다 |
| 도착 오류 자리 | `MatchRoomPage`의 도착 확인 대화상자에 **오류 안내 자리가 없었다.** 반경 밖 거절이 화면에 아무 변화도 남기지 않았다. `matchRoomError.ts`를 만들고 도착·취소·먼저 나가기 각각에 문구를 붙였다 |
| 엉뚱한 문구 | `actionError` 한 자리를 여러 버튼이 공유해 도착 실패에 "도착 예정 시간을 저장하지 못했어요"가 떴다. `actionErrorSource`로 어느 버튼이 실패했는지 구분한다 |
| `GeolocationError` | 위치 권한·시간 초과 안내는 그대로 보여주고, `network` 같은 일반 오류 message는 화면에 내보내지 않는다 |
| 반경을 `.env`로 조절 | (2026-09-15 후속) 체크인 반경이 `festivals.checkin_radius_meters`(DB 컬럼)였는데, 그 컬럼을 축제마다 다르게 채우는 코드가 없어 모든 축제가 항상 500m로 고정돼 있었다. `FestivalCheckinProperties.radiusMeters`(env `FESTIVAL_CHECKIN_RADIUS_METERS`, 기본 500)로 옮겨 정확도 임계값과 같은 방식으로 환경변수로 조절한다. DB 컬럼은 더 이상 검증에 쓰이지 않는다(제거하지 않음 — 별도 migration 필요) |

### B — GPS 바이패스

코드 결함이 아니었다(3.2 참고). `.env.example`의 `ADMIN_LOCAL_*` 항목,
`scripts/diagnose-gps-bypass.sql`, `docs/30` 4.5 진단 절차를 추가했다.

### C/D — 알림 2단계(서버 알림함)

| 변경 | 내용 |
| --- | --- |
| migration | `V39__add_notifications.sql`. `(member_id, reason, occurred_at)` unique로 중복을 막는다 |
| 저장 지점 | `MatchingStateChangedEventHandler` — WebSocket 발송 **뒤에**, 별도 transaction으로 |
| 저장 대상 | 종결·행동 필요 7종만. 중간 상태는 저장하지 않는다(5절 5번) |
| 보관 | 30일 **과** 100건. 둘 중 먼저 걸리는 쪽이 지운다. 스케줄러 없이 쓸 때마다 정리한다 |
| 읽음 | 목록을 열면 전체 읽음(현행 유지). `PATCH /api/members/me/notifications/read` |
| 자기 반향 | 행위자 본인에게는 남기지 않는다. `MatchingStateChangedEvent`에 `actorMemberId`를 실었다 |
| 프론트 | `localStorage` 보관을 걷어내고 서버 목록과 세션 알림을 합친다. 종의 안내 문구도 서버가 준 보관 정책으로 적는다 |

### F — 30도 매칭 제한

| 변경 | 내용 |
| --- | --- |
| 정책 | `MannerTemperaturePolicy.MATCHING_MINIMUM = 30.00`. 경계값은 허용 |
| 차단 | `MatchPoolEntryService.enter()` — `MATCHING_TEMPERATURE_RESTRICTED`(409). **회원 status는 바꾸지 않는다** |
| 화면 | `MatchingRestrictionResponse.temperatureLimit` → `TEMPERATURE_RESTRICTED` 상태 → 전용 카드 |
| 노출 범위(5절 7번 결정) | **신고를 드러내지 않고, 카운트다운도 두지 않으며, 회복 방법을 안내한다.** 낮은 온도는 곧 "신고를 받았다"라서 사유를 적으면 신고자를 좁힐 수 있고(`docs/19` 4.8), 회복은 만남 완료와 시간 경과 두 경로에 달려 있어 확정된 해제 시각이 없다. 대신 현재 온도와 기준 온도(본인에게 이미 보이는 값)를 보여주고 솔로 코스는 열어 둔다 |
| 우선순위 | 쿨타임보다 먼저 보여준다. 2분 카운트다운이 끝난 뒤 다시 막히면 더 나쁘다 |

### E — Web Push

| 변경 | 내용 |
| --- | --- |
| migration | `V40__add_push_subscriptions.sql` |
| 발송 | `WebPushSender`(전용 thread), `PushNotificationService`. 404·410이면 구독을 지운다 |
| API | `GET/POST/DELETE /api/members/me/push-subscriptions` |
| service worker | `src/sw.ts`(injectManifest). `push`·`notificationclick` 핸들러, 기존 navigation fallback과 denylist 유지 |
| 권한 요청 | 첫 매칭 신청 직전 한 번(`enablePush()`). 실패해도 신청은 그대로 진행한다 |
| 정지 회원 | 알림 읽음·구독 등록/해지는 `SuspendedActivityPolicy`에서 **허용**으로 분류했다. 본인 기기 설정이고, 해지를 막으면 알림을 끌 수 없다 |

## 9. 남은 확인 사항

| 항목 | 내용 |
| --- | --- |
| **migration 번호** | `V39`·`V40`은 저장소 기준으로 잡았다. **적용 전에 공유 dev DB의 `flyway_schema_history`를 조회**해 비어 있는지 확인한다(`docs/10` 사고 기록 3회) |
| **컨테이너 통합 테스트** | 작업 PC에 Docker가 없어 Testcontainers 기반 35개 클래스를 실행하지 못했다. Docker가 있는 환경에서 `./gradlew test`를 한 번 돌려야 한다 |
| **VAPID 키** | `npx web-push generate-vapid-keys`로 만들어 `.env`와 dev/prod Secret에 넣는다. 없으면 push만 꺼진 채 나머지는 동작한다 |
| **오프라인 동작** | `injectManifest` 전환 뒤 캐싱 회귀를 실기기에서 한 번 확인한다(`npm run build` + preview) |
| **수동 검증** | `docs/30` 시나리오 A~R. 특히 B(도착 반경), R(알림) |

## 10. G — 매칭 즉시 조합 경로 제거 (2026-09-18)

### 발견 경위

개발계에서 테스터 5명으로 취향 임베딩이 매칭에 반영되는지 확인하려 했다. 전원 같은 태그,
희망 인원 2명으로 맞추고 취향만 다르게 입력했는데 결과가 취향과 무관하게 "먼저 누른 두
사람끼리" 묶였다.

### 원인

조합 경로가 둘이었고, 그중 즉시 경로가 점수가 개입할 여지를 없앴다.

| 위치 | 내용 |
| --- | --- |
| `MatchPoolEntryService` | pool 저장 직후 `MatchingPoolEnteredEvent` 발행 |
| `MatchingPoolEnteredEventHandler` | `@TransactionalEventListener(AFTER_COMMIT)`으로 즉시 조합 실행 |

즉시 경로는 **유효한 조합이 처음 생기는 순간 소진**시킨다. 그래서 대기 후보가 2명을 넘지
못했고, 가능한 조합이 1개뿐이면 조합을 정렬해서 고를 대상이 없다. 점수는 계산·저장만 됐다.
`MATCHING_SCHEDULER_ENABLED`와 무관하게 항상 동작했고 끄는 플래그도 없었다.

함께 확인된 것:

- 최소 궁합 점수 임계값이 없다. 점수는 조합 간 순위만 가리고, 그 순위도 인원 수보다 뒤다
- `MatchPoolRepository.findPoolEntryClaimablePoolsForUpdate`에만
  `preferred_group_size` 버킷 필터가 남아 있었다. `MatchGroupComposer` 주석이 "없앴다"고
  적은 그 버킷이며, scheduler claim 쿼리에는 없어 두 경로의 후보 집합 정의가 달랐다
- `docs/05` 매칭 흐름 3번은 이미 "Scheduler가 eligible pool entry를 조회한다"였다.
  즉시 경로는 문서에 없었다. 이번 작업은 새 정책 도입이 아니라 코드를 문서에 맞추는 것이다

### 처리

즉시 경로를 제거하고 조합을 scheduler tick 하나로 통일했다. 버킷 필터는 해당 쿼리가
사라지면서 함께 해소됐다. `MATCHING_SCHEDULER_ENABLED` 기본값은 `true`로 바꿨다 —
즉시 경로가 없어진 뒤에는 이 플래그가 꺼지면 매칭이 성사되지 않는다.

### 하지 않은 것

- 최소 점수 임계값: 수집 주기 전환 뒤 실사용 점수 분포를 보고 별도 판단
- 가중치 조정, `allowMinimumTwo`·`acceptsSize` 정책 변경
- 임베딩 점수와 실제 만족도의 관계 검증. 후속 경로 후보는
  `member_reviews`·매너온도 변화와 당시 `match_attempt_members.cosine_score`의 상관이다

### 남은 한계

수집 주기는 후보가 모일 기회를 주지만 보장하지 않는다. 그 시간에 두 명만 모이면 여전히 그
둘이 매칭된다. 밀도의 한계가 아니라 임계값 없이 지금 가능한 조합을 즉시 선택하는 현재 정책의
결과다. 검증 절차는 `docs/30` 7절에 있다.
