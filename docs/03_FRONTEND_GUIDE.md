# 프론트엔드 가이드

## Matching 서버 시각과 종료 사유

- `GET /api/matching/me/restrictions`의 `serverNow`와 REST 왕복 중간 client 시각으로 offset을
  계산하며 탐색, proposal, cooldown, 완료 제한 countdown 모두 `Date.now() + offset`을 쓴다.
- offset은 REST refresh마다 갱신한다. 동일 attempt/round/deadline에서는 countdown 증가를
  막고 round 또는 deadline 변경은 실제 연장으로 반영한다.
- countdown 0초는 client timeout 상태를 만들지 않고 REST refresh만 요청한다. WebSocket,
  재연결, polling fallback과 visible 복귀도 모두 REST snapshot 갱신만 유도한다.
- 최신 pool의 `terminationReason` 네 값으로 직접 거절, 비귀책 종료, 본인 timeout, 시스템
  종료 문구를 구분한다. 비귀책 종료에는 cooldown countdown을 표시하지 않는다.

## Kakao Maps 만남 장소

Kakao Maps JavaScript SDK App Key는 `VITE_KAKAO_MAPS_APP_KEY`로만 주입합니다.
example에는 placeholder만 두고 실제 Key를 저장소에 기록하지 않습니다. SDK가 없거나
로드에 실패해도 MatchRoom의 장소명과 주소는 계속 표시합니다.

## 소셜 로그인 버튼

로그인 화면은 기존 카카오 버튼과 네이버 텍스트 버튼을 함께 제공한다. 두 버튼 모두 backend의 `/api/auth/{provider}/login`으로 이동하며 authorize URL을 frontend에서 조립하지 않는다. 이동 중에는 중복 클릭을 막고 OAuth 실패는 provider 공통 안내로 표시한다.

Home과 MyPage의 회원 nickname, email, 여행 스타일은 mock이 아니라 `GET /api/members/me` 응답을 사용한다. OAuth email이 제공되지 않은 회원은 이메일 미등록 안내를 표시한다.

## 닉네임 입력 제한

- 닉네임은 `2~12자`로 제한한다.
- 허용 문자는 한글, 영문 대소문자, 숫자만 사용한다.
- 공백, 이모지, 특수문자는 허용하지 않는다.
- `SignupPage`와 `ProfileEditPage`는 같은 안내 문구와 client 선검증을 사용한다.
- backend validation이 최종 기준이며 frontend 제한은 사용자 편의를 위한 사전 안내다.

## 프로필 이미지 표시와 업로드

- MyPage와 ProfileEditPage는 `profileImageUrl`이 있으면 이미지를 표시하고, `null`이면 기존 닉네임 기반 placeholder를 표시합니다.
- ProfileEditPage는 JPEG, PNG, WEBP 파일 선택과 미리보기를 제공하며 최대 5MB를 client에서도 선검증합니다.
- 저장 시 프로필 정보 갱신 후 선택한 파일을 `POST /api/members/me/profile-image`의 `file` 필드로 전송합니다.
- `Content-Type`은 브라우저가 multipart boundary와 함께 만들도록 직접 지정하지 않습니다.
- backend 상대 이미지 URL은 `VITE_API_BASE_URL` 설정을 반영해 표시합니다.

## 인증 만료 이동

- 공통 `apiClient`가 backend의 `401 UNAUTHORIZED` 응답을 받으면 `/login` 페이지로 이동합니다.
- 이미 `/login`에 있는 경우에는 다시 이동하지 않아 redirect loop를 방지합니다.
- `window.location.replace`를 사용해 만료 직전의 인증 필요 화면이 browser 뒤로가기에 남지 않게 합니다.

## 프론트엔드 방향

프론트엔드는 React + TypeScript + Vite 기반 PWA입니다. 모바일 축제 방문자를 우선하고, 관리자 화면은 같은 코드베이스에서 확장합니다.

주요 목표:

- QR 또는 URL로 빠르게 접근한다.
- 홈 화면 추가를 지원한다.
- PWA shell을 준비한다.
- 매칭 상태를 명확하게 보여준다.
- 자유 채팅이 아니라 버튼 기반 제한형 인터랙션을 제공한다.

## 현재 5단계 범위

현재 frontend는 PWA 기본 스캐폴딩, backend `GET /api/health` 연동 확인, backend 공통 `ApiResponse<T>` 응답 처리를 위한 최소 API 공통 구조까지만 구성합니다.

구성된 항목:

- React + TypeScript + Vite 기본 구조
- `vite-plugin-pwa` 기반 PWA 기본 설정
- `manifest` 기본 값
- placeholder icon
- 상대 경로 `/api/health` 기반 health API 호출
- Vite proxy 기반 backend local 연결
- 개발 확인용 `HealthCheckPage`
- backend `ApiResponse<T>` wrapper를 해석하는 `apiClient`
- `ApiResponse<T>`, `ApiError`, `FieldError` 타입

현재 구현하지 않는 항목:

- 실제 서비스 화면
- 로그인
- 축제 목록/상세
- GPS 체크인
- 매칭
- `MatchRoomPage`
- Web Push 실제 권한 흐름
- Kakao Maps
- 관리자 화면
- frontend 테스트 코드

## 기본 폴더 구조

현재 최소 구조는 아래 기준입니다.

```text
frontend/
├─ public/
│  └─ icons/
├─ src/
│  ├─ api/
│  ├─ components/
│  ├─ pages/
│  └─ styles/
├─ index.html
├─ package.json
├─ tsconfig.json
└─ vite.config.ts
```

`src/components`는 추후 공통 UI가 필요할 때 사용합니다. 지금은 개발 확인용 화면을 `src/pages/HealthCheckPage.tsx`에 둡니다.

## 환경변수

frontend는 Vite 환경변수 `VITE_API_BASE_URL`을 사용합니다.

로컬 예시:

```text
VITE_API_BASE_URL=
```

파일:

- `frontend/.env.local.example`
- `frontend/.env.production.example`

local 개발에서는 `VITE_API_BASE_URL`을 비워두고 Vite proxy로 `/api` 요청을 backend에 전달합니다. 실제 IP, 도메인, API Key, Secret은 저장소에 기록하지 않습니다. 운영 예시는 placeholder만 사용합니다.

local 개발에서 frontend 코드는 backend URL을 직접 하드코딩하지 않고 `/api/health` 상대 경로를 호출합니다.

추후 dev/prod에서 별도 API base URL이 필요하면 `VITE_API_BASE_URL`을 사용할 수 있도록 구조를 열어둡니다. 단, 실제 IP, 도메인, API Key, Secret은 코드나 예시 파일에 하드코딩하지 않습니다.

## 공통 API 처리

frontend API 호출은 `src/api/apiClient.ts`의 공통 `apiClient`를 기본으로 사용합니다.

기본 원칙:

- local 개발은 Vite proxy를 우선 사용한다.
- 기본 API 경로는 `/api/...` 상대 경로를 사용한다.
- `VITE_API_BASE_URL`이 비어 있으면 상대 경로 그대로 요청한다.
- `VITE_API_BASE_URL`이 있으면 해당 base URL과 `/api/...` 경로를 조합한다.
- backend 응답은 `ApiResponse<T>`로 파싱한다.
- HTTP status가 2xx가 아니거나 `success=false`이면 공통 에러 메시지로 처리한다.

공통 타입은 `src/api/types.ts`에 둡니다.

## 날짜·시간 표시

- API의 ISO-8601 시각 문자열은 원본 계약을 유지합니다.
- 화면 표시가 필요하면 `src/utils/dateTime.ts`의 `formatSeoulDateTime`을 사용합니다.
- formatter는 브라우저 기본 timezone에 의존하지 않고 `Asia/Seoul`을 명시합니다.
- 기본 표시 형식은 `yyyy-MM-dd HH:mm:ss`이며 밀리초와 offset은 화면에 노출하지 않습니다.
- API가 `+09:00` offset으로 반환한 값도 `Date`가 동일한 절대 시점으로 해석한 뒤 KST로 표시하므로 9시간이 중복 가산되지 않습니다.
- null, 빈 문자열, 유효하지 않은 값은 `-`로 표시합니다.
- `plusHours(9)` 같은 수동 보정은 사용하지 않습니다.

```ts
export type FieldError = {
  field: string;
  message: string;
};

export type ApiError = {
  code: string;
  message: string;
  fields?: FieldError[];
};

export type ApiResponse<T> = {
  success: boolean;
  data: T | null;
  error: ApiError | null;
};
```

## 실행 방법

의존성을 설치합니다.

```bash
cd frontend
npm install
```

로컬 환경변수 파일을 준비합니다.

```bash
cp .env.local.example .env.local
```

dev server를 실행합니다.

```bash
npm run dev
```

backend local profile을 먼저 실행한 뒤 Vite dev server URL에 접속하면 `GET /api/health` 응답 상태를 확인할 수 있습니다.

local 개발 서버 포트:

- backend: `http://localhost:8080`
- frontend: `http://localhost:5173`

브라우저에서 직접 확인할 주소는 `http://localhost:5173/`입니다. backend 직접 확인은 `http://localhost:8080/api/health`로 합니다.

Vite dev server는 `/api` 요청을 backend로 proxy합니다.

```text
Browser -> http://localhost:5173/api/health -> Vite proxy -> http://localhost:8080/api/health
```

`frontend/vite.config.ts`의 proxy 설정을 바꾸면 frontend dev server를 재시작해야 합니다.

## Health API 연동

health API 호출 코드는 `src/api/healthApi.ts`에 둡니다.

요청:

```text
GET /api/health
```

예상 응답:

```json
{
  "success": true,
  "data": {
    "status": "OK",
    "service": "meet-or-solo-backend"
  },
  "error": null
}
```

`healthApi`는 공통 `apiClient`를 사용해 `ApiResponse<HealthResponse>` wrapper를 처리하고, page에는 `data.status`, `data.service`를 반환합니다.

현재 화면은 loading, success, error 상태만 표시합니다. 이는 개발환경 연결 확인용이며 비즈니스 기능이 아닙니다.

frontend 화면에서 `연결 성공`, `status`, `service`가 표시되면 frontend-backend 연동 확인이 완료된 것입니다.

## PWA 기본 설정

PWA 기본 설정은 `vite.config.ts`의 `VitePWA`로 구성합니다.

현재 설정:

- `registerType: autoUpdate`
- `manifest.name: meet-or-solo`
- `manifest.short_name: meet-or-solo`
- `display: standalone`
- placeholder icon: `public/icons/placeholder.svg`
- `navigateFallback: /index.html`

현재 PWA는 기본 shell, manifest, service worker 생성 설정, placeholder icon 수준입니다. `npm run build`를 실행하면 `dist/manifest.webmanifest`, `dist/sw.js`, `dist/registerSW.js` 같은 빌드 결과물이 생성됩니다.

`frontend/dist/`는 build 결과물이므로 커밋하지 않습니다. `frontend/public/icons/placeholder.svg`는 소스 리소스이므로 커밋 대상입니다.

실제 설치 안내 UI, offline fallback 화면, Web Push 권한 흐름, 실제 앱 아이콘 세트, 브랜딩 작업은 추후 별도 승인 후 구현합니다.

## 개발/운영 차이

개발 환경:

- Vite dev server 사용
- 로컬 backend API 연결
- 개발용 CORS 사용
- PWA 기능은 일부 mock 또는 비활성 상태일 수 있다.

운영 환경:

- `vite build`로 `dist` 생성
- Nginx가 `dist` 정적 파일 서빙
- Nginx가 `/api`, `/ws`를 Spring Boot로 proxy
- 브라우저 라우팅은 `index.html` fallback 처리

## 라우팅 초안

페이지 이름은 구현 중 조정할 수 있지만, 서비스 흐름은 아래 구조를 따른다.

| Page | 목적 |
| --- | --- |
| `SplashGate` | 진입 첫 화면(로고 스플래시)과 세션 bootstrap. route가 아니라 `Routes`를 감싸는 gate로 구현했다 |
| `OnboardingPage` | 서비스 안내와 권한 요청 맥락 설명 |
| `LoginPage` | OAuth 로그인 진입 |
| `TermsPage` | 약관, 개인정보, 위치정보 동의 |
| `ProfileSetupPage` | 닉네임, 연령대, 성별, 태그 설정 |
| `FestivalFeedPage` | 강원도 축제 목록/feed |
| `FestivalDetailPage` | 축제 상세, 이미지, 지도, 체크인 진입 |
| `CheckInPage` | GPS 권한과 축제 반경 검증 |
| `MatchingConditionPage` | 희망 인원, 태그, 2명 진행 허용 여부 선택 |
| `MatchingWaitingPage` | 탐색 타이머와 매칭 제안 대기 |
| `MatchRoomPage` | 확정 매칭 상태방. 자유 채팅방 아님 |
| `MatchingFailedPage` | 매칭 실패, 재시도, 솔로 전환 안내 |
| `SoloCoursePage` | 관광공사 데이터 기반 솔로 코스 추천 |
| `PlaceDetailPage` | 주변 장소 상세 |
| `ReviewPage` | 매칭 후 평가와 매너 피드백 |
| `MyPage` | 프로필, 설정, 이력, 탈퇴 |
| `AdminReportPage` | 신고 처리 |
| `AdminMemberPage` | 회원 및 제재 관리 |
| `AdminFestivalPage` | 축제/API 데이터 관리 |
| `AdminBatchLogPage` | batch/API 호출 로그 모니터링 |

## Modal/Popup

| Component | 목적 |
| --- | --- |
| `GPSPermissionModal` | GPS 권한 필요 이유 안내 |
| `CheckInSuccessModal` | 축제 체크인 성공 안내 |
| `MatchProposalModal` | 30초 응답 타이머가 있는 매칭 제안 |
| `MatchResponseWaitingModal` | 수락 후 다른 사용자 응답 대기 |
| `InsufficientMembersModal` | 목표 인원 미달 시 현재 인원 진행 여부 확인 |
| `MemberArrivedModal` | 상대 도착 알림 |
| `MemberCancelledModal` | 상대 취소 알림 |
| `SafetyReminderModal` | 안전 리마인드 |
| `WebPushPermissionModal` | 알림 권한 요청 |
| `PwaInstallGuideModal` | 홈 화면 추가 안내 |

## Bottom Sheet

| Component | 목적 |
| --- | --- |
| `ArrivalTimeBottomSheet` | 예상 도착 시간 선택 |
| `CancelReasonBottomSheet` | 구조화된 취소 사유 선택 |
| `ReportReasonBottomSheet` | 신고 사유 선택 |
| `TagSelectBottomSheet` | 매칭/축제 태그 선택 |

## MatchRoomPage

도착 완료는 본인이 `JOINED` 또는 `ARRIVAL_TIME_SELECTED`일 때 확인 panel을
거쳐 실행합니다. 성공 snapshot 전에는 ARRIVED로 표시하지 않고 실패 시 기존
snapshot을 유지하며, `arrivedAt`은 KST formatter로 표시합니다.

`MatchRoomPage`는 자유 채팅방이 아닙니다.

시스템 이벤트 타임라인과 제한형 버튼 인터랙션을 제공하는 상태 동기화 화면입니다.

현재 구현된 첫 단계는 `/match-room`의 읽기 전용 상태방입니다. URL에 `groupId`를
포함하지 않고 `GET /api/matching/groups/me/current`로 로그인 회원의 active
group을 복원합니다. 최초 mount, `/ws` 연결·재연결과 `/user/queue/matching`
알림 수신 시 REST를 다시 조회하며, WebSocket 장애 중에는 5초 polling을
fallback으로 사용합니다. current group이 없으면 `/matching`으로 replace
이동합니다.

현재 읽기 전용 표시 범위:

- 확정 시각, 확정 인원과 `CONFIRMED`/`IN_PROGRESS` 안내
- 축제명, 주소와 행사 기간
- 멤버 nickname, 공개 가능한 profile image와 참여 상태
- loading, API 오류 안내와 재시도

도착 예정 시간 선택 단계에서는 기존 상태방에 다음 제한형 인터랙션만
추가합니다.

- 신규 선택 요청은 `5분`, `10분`, `20분`, `25분` panel만 제공
- 과거 응답 `0`은 `곧 도착 예정`, 과거 `30`과 신규 `25`는 `N분 후 도착 예정`으로 표시
- 상대 회원의 도착 분 또는 선택 시각이 정상 REST refresh 전후 실제 변경되면
  하단 navigation 위에 3초 동안 nickname 포함 snackbar 표시
- 최초 snapshot, 본인 변경, 동일 snapshot, 실패한 refresh와 잘못된 WebSocket
  payload에는 상대 변경 snackbar를 표시하지 않음
- 제출 중 중복 선택 방지
- 성공 응답의 current group snapshot 즉시 반영
- 실패 시 기존 snapshot 유지와 재선택 안내
- 멤버별 `도착 시간 미정`, `곧 도착 예정`, `N분 후 도착 예정`, `도착 완료` 표시

`도착했어요` 버튼, 확정 후 취소, NO_SHOW 종료 복원과 시스템 이벤트 타임라인은
구현되어 있습니다. 현재 `도착했어요`는 서버 상태 전이만 수행하며 실제 단말
GPS 반경 확인은 아직 연결하지 않았습니다. 만남 포인트 지도, 단말 위치 확인,
신고와 안전 기능은 후속 범위입니다.

정상 완료 후 `/matching` 화면은 취소·NO_SHOW terminal card를 재사용하지
않습니다. 현재 수동 검증에서 완료 안내와 함께 `매칭이 취소됐어요`,
`다시 신청하기`가 노출되는 문제가 확인되었으며 다음 Frontend 보완 범위에서
완료 전용 card로 분리합니다.

완료 전용 card의 계약은 다음과 같습니다.

- 제목은 `만남이 완료됐어요`로 표시하고 취소 문구와 아이콘을 사용하지 않음
- `confirmedAt + 1시간`인 매칭 유효 종료 시각과 남은 시간을 표시
- 유효시간 중에는 새 매칭 신청 action을 비활성화
- 제한 종료 뒤 체크인이 만료되었으면 `다시 체크인하기`, 유효하면
  `다시 매칭하기` 제공
- 완료 안내는 Router state에서 한 번만 소비하고 새로고침·새 매칭에서 반복하지 않음
- 후기 작성 action과 최근 완료 이력 복원은 후속 범위로 유지

이 화면 보완은 구현되었습니다. `useMatchingSession`은 active group/proposal/pool
상태를 먼저 적용한 뒤 `completionLock.groupId`가 있으면 최신 `MATCHED` pool을
취소로 해석하지 않고 `COMPLETED`로 복원합니다. 완료 card는 성공 아이콘, 종료
시각과 countdown을 표시하고 `completionLock.active=true` 동안 action을
비활성화합니다. 제한 종료 뒤 `다시 매칭하기`로 기존 retry form을 열며, 실제
체크인이 만료됐다면 기존 신청 API 오류를 통해 `체크인하기` 동선으로 연결합니다.
Router 완료 notice는 기존처럼 한 번만 소비하고 card 복원은 restriction 응답이
담당합니다. 자동 테스트는 완료했으며 실제 브라우저 수동 재검증은 남아 있습니다.

## 비동기 화면 전환 안정화 후속 범위

matching 완료 기능과 별도로 Frontend 전체의 최초 상태 복원과 화면 전환을
점검합니다. 서버 응답 전의 `unknown`을 실제 데이터가 없는 `IDLE`로 해석하면
새로고침 직후 신청 form이 먼저 노출되고 완료 card로 바뀌는 잘못된 중간 화면이
발생합니다. 이 보완은 별도 Frontend UX 브랜치에서 수행합니다.

공통 원칙:

- 최초 snapshot을 아직 받지 않은 `LOADING`, 조회 완료 후 실제 데이터가 없는
  `IDLE`, 완료·취소 같은 terminal 상태를 구분
- 최초 진입에서만 중립적인 skeleton을 사용하고 신청 form을 placeholder로 사용하지 않음
- WebSocket, polling과 수동 refresh 중에는 마지막 정상 화면을 유지하고
  백그라운드에서 새 snapshot을 반영
- pool, proposal, group, restriction 조회를 하나의 논리 snapshot으로 판정하고
  일부 응답 순서대로 중간 화면을 연속 렌더링하지 않음
- active group, proposal, active pool, 완료 제한, cooldown, terminal pool,
  `IDLE`의 우선순위를 한곳에서 일관되게 적용
- card와 skeleton의 최소 높이를 맞춰 layout shift를 줄임
- Router notice는 표시 직후 history state에서 소비하고 새로고침에서 반복하지 않음
- API 지연, 일부 실패, WebSocket 재연결, polling, 뒤로 가기와 새로고침을
  화면별 자동·수동 테스트에 포함

우선 점검 대상은 `/matching`, `/match-room`, 체크인, 로그인/프로필 복원,
축제 목록·상세입니다. 이 절은 후속 작업 계약이며 현재 completion 브랜치에서
함께 구현하지 않습니다.

필수 요소:

- 매칭 확정 안내 카드
- 참여자 상태 목록
- 만남 포인트 지도 카드
- Kakao Maps 핀 표시
- 도착 시간 선택 버튼
- "도착했어요" 버튼
- "못 갈 것 같아요" 버튼
- 시스템 이벤트 타임라인
- 상대 도착 알림
- 상대 취소 알림
- 안전 리마인드
- 신고 버튼
- 긴급 도움 버튼

자유 텍스트 입력창은 구현하지 않습니다.

확정 후 취소는 deadline 전 `JOINED`, `ARRIVAL_TIME_SELECTED`인 본인에게만
`못 갈 것 같아요` action을 표시합니다. 사유는 `갑자기 일정이 생겼어요`,
`이동이 어려워졌어요`, `다른 이유가 있어요` 버튼만 제공하고 자유 입력은
제공하지 않습니다. 성공 전 optimistic 상태 변경을 하지 않으며 종료 결과는
안내와 함께 `/matching`으로 이동합니다.

## PWA 동작

예정 PWA 기능:

- `manifest.json`
- Service Worker
- install prompt 처리
- offline fallback shell
- 홈 화면 추가 안내
- Web Push 권한 흐름

GPS, Push, 설치 권한은 사용자가 이유를 이해할 수 있는 시점에 요청합니다.

## Web Push

Web Push 예정 용도:

- 매칭 제안
- 매칭 확정
- 도착 리마인드
- 취소 알림
- 안전 리마인드

VAPID Key는 하드코딩하지 않습니다.

## 매칭 WebSocket STOMP

- matching 화면은 현재 origin의 `/ws`에 native WebSocket으로 연결합니다.
- local Vite dev server는 `/ws`를 `http://localhost:8080`으로 `ws: true` proxy합니다.
- 인증은 브라우저가 WebSocket handshake에 함께 보내는 `access_token` HttpOnly cookie를 사용합니다.
- `/user/queue/matching`에서 상태 변경 알림을 받으면 기존 matching REST 조회를 다시 실행합니다.
- 알림 payload를 최종 상태로 사용하지 않으며 PostgreSQL과 REST 응답을 기준으로 화면을 복원합니다.
- 연결 실패와 재접속 중에는 기존 polling이 fallback으로 계속 동작합니다.
- 재접속 성공 시 즉시 REST 상태를 다시 조회하고 unmount 시 STOMP 연결을 정리합니다.

## Kakao Maps

Kakao Maps는 추후 다음 용도로 사용합니다.

- 축제 위치 표시
- 만남 포인트 핀 표시
- 주변 관광지 표시
- 솔로 코스 맥락 제공

Kakao JavaScript Key는 환경 설정으로 주입하고 저장소에 커밋하지 않습니다.

만남 포인트 기능의 API 역할은 다음처럼 분리합니다.

- 관광공사 축제 좌표: 주변 POI 검색의 중심점
- Kakao Local API: 중심점 주변 카페·편의점·주차장·음식점 등 장소 후보 검색
- Kakao Maps SDK: 확정된 만남 포인트 지도와 핀 표시
- 브라우저 Geolocation API: `도착했어요` 실행 시 현재 위치 측정

축제 좌표를 곧바로 만남 장소로 표시하지 않습니다. 선택된 실제 장소명, 주소와
좌표를 current group 응답으로 받은 뒤 `도착했어요` action보다 위에 표시합니다.
브라우저 위치 권한은 action 실행 시점에 사용 이유를 먼저 설명한 뒤 요청합니다.
위치 권한 거부, 측정 실패, 정확도 부족, 반경 밖과 좌표 미준비 상태를 각각
구분해 안내하며 성공 응답 전에는 `ARRIVED`를 낙관적으로 표시하지 않습니다.
신고 완료와 위치정보 약관·동의 적용을 전제로 Frontend는 사용자가
`도착했어요`를 누른 시점의 위도·경도, 정확도와 측정 시각을 도착 API로
전송합니다. 거리 판정은 Backend가 group snapshot의 만남 포인트를 기준으로
수행하며 성공 응답 전에는 위치정보를 재사용하지 않습니다. 화면 문구는 과도한
보증을 피하고 `도착 확인이 완료됐어요`로 표시합니다. GPS 조작 가능성은 남기
때문에 실제 장소에 없는 허위 도착은 신고 기능과 운영 검토로 보완합니다.

## MatchRoomPage 시스템 이벤트 타임라인

- `/match-room` 최초 진입과 새로고침에서 current group과 current group events REST를 함께 조회합니다.
- `/user/queue/matching` 연결·재연결·상태 알림과 WebSocket 장애 polling은 두 REST를 다시 조회하는 trigger입니다.
- event WebSocket payload를 직접 append하거나 optimistic event를 만들지 않고 PostgreSQL commit 뒤 REST 결과를 사용합니다.
- current group 조회가 성공하고 events만 실패하면 기존 group 화면을 유지하고 상태 기록 영역에 별도 재시도를 제공합니다.
- 타임라인은 `MATCH_CONFIRMED`, `ARRIVAL_TIME_SELECTED`, `MEMBER_ARRIVED`만 표시하며 KST formatter를 사용합니다.
- 자유 text input, 메시지 작성, 전송 버튼과 client STOMP `SEND`는 제공하지 않습니다.

## MatchRoomPage 구조화 신고

- current group snapshot의 `groupId`, 상대 카드의 `memberId`만 신고 API에 사용하며
  본인 카드에는 신고 action을 표시하지 않습니다.
- 신고 dialog는 `무례한 행동`, `성희롱`, `나타나지 않음`, `사기 의심`,
  `안전 문제`, `기타`의 구조화 사유만 제공하고 자유 입력을 제공하지 않습니다.
- 사유 선택 뒤 대상과 한국어 사유를 다시 보여주는 최종 확인 단계를 거칩니다.
- `POST /api/match-groups/{groupId}/reports`에는 `reportedMemberId`, `reasonCode`만
  전송합니다. reporter는 HttpOnly JWT cookie를 해석하는 Backend 책임입니다.
- 제출 중 동기 in-flight guard로 이중 클릭을 막고 dialog 취소·대상 변경 시
  `AbortController`와 request identity를 함께 갱신해 늦은 응답을 무시합니다.
- 실패하면 current group snapshot을 변경하지 않고 dialog에서 재시도하며, 성공하면
  dialog를 닫고 접수 안내만 표시합니다. current group 재조회, WebSocket event 전송,
  차단 또는 자동 제재는 실행하지 않습니다.
- `SAFETY` 선택 시 긴급 상황은 신고 접수만 기다리지 말고 112 등 긴급 기관에
  연락하라는 짧은 안내를 제공합니다.
- dialog는 접근 가능한 title/label과 `Escape` 닫기, 최초 버튼 focus 및 닫은 뒤
  기존 focus 복원을 제공합니다. 제출 중에는 닫기와 이전 이동을 비활성화합니다.

## MatchRoomPage 상대 회원 차단

- 본인을 제외한 각 상대 카드에 신고와 독립된 `차단하기` action을 표시합니다.
- 최종 확인 dialog는 대상 nickname과 향후 양방향 매칭 제외, 상대에게 차단 사실·주체를
  알리지 않음, 현재 화면에서 해제 불가를 안내합니다.
- current group snapshot의 `groupId`와 선택한 상대 카드의 `memberId`만 사용해
  `POST /api/match-groups/{groupId}/blocks`에 `blockedMemberId` 한 필드만 전송합니다.
- 차단 session은 동기 in-flight guard, `AbortController`와 request identity로 빠른
  이중 클릭, dialog 취소, 대상 변경, unmount 뒤 늦은 응답을 방어합니다.
- 실패하면 대상과 dialog를 유지해 재시도합니다. 성공하면 접근 가능한 완료 안내만
  표시하고 기존 group과 상대 카드를 유지하며 REST 재조회나 WebSocket event를 만들지 않습니다.
- 차단 성공은 현재 MatchRoom의 퇴장·종료 명령이 아닙니다. 현재 상태방과 상대 카드는
  그대로 유지하고, 완료 안내에서 차단 효과가 다음 매칭부터 적용됨을 설명합니다.
- 신고도 접수만으로 현재 group이나 상대 카드를 제거하지 않습니다. 신고는 운영 검토로,
  차단은 향후 양방향 후보 제외로 이어지며 현재 상태방의 도착·취소·완료 흐름과 분리합니다.
- dialog title과 설명을 연결하고 최초 focus, `Escape`, 닫은 뒤 focus 복원과 `Tab`
  순환을 제공합니다. 제출 중에는 닫기·취소·확인 action을 비활성화합니다.

## 마이페이지 차단 회원 관리

- 마이페이지의 `차단 회원 관리`는 `/mypage/blocks`로 이동하며 본인이 생성한 정방향
  차단만 조회합니다. nickname, 공개 profile image와 차단 시각 외 내부 ID·reason·차단
  주체는 표시하지 않습니다.
- loading, `차단한 회원이 없어요`, 오류와 재시도 상태를 구분합니다. 최종 확인 dialog는
  향후 재매칭 가능성, 현재 MatchRoom 불변과 상대 알림 부재를 안내합니다.
- DELETE의 body 없는 `204`를 신규·반복 해제 모두 성공으로 처리합니다. 성공 전에는 목록을
  낙관적으로 제거하지 않고, 성공한 `blockedMemberId` 항목만 제거합니다.
- 동기 in-flight guard, `AbortController`와 request identity로 이중 클릭, 대상 변경,
  화면 이탈·unmount 및 늦은 성공·실패를 방어합니다. 해제 성공 뒤 current MatchRoom을
  재조회하거나 WebSocket `SEND`를 하지 않습니다.
- dialog는 `role=dialog`, `aria-modal`, title/description 연결, 최초 focus, `Escape`, focus
  복원과 `Tab` 순환을 제공하며 loading·성공·오류는 live region으로 알립니다.

## 찜(북마크)과 댓글·좋아요

설계 근거는 `docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md`입니다.

### 로그인 여부 판단 규칙

- **공개 상세 화면에서 `GET /api/members/me`를 호출하지 않습니다.** `apiClient`가 모든 `401`을
  `window.location.replace('/login')`으로 처리하므로, 비로그인 사용자가 축제·관광지 상세를 열기만
  해도 로그인 화면으로 튕깁니다.
- 로그인 여부는 `GET /api/{festivals|spots}/{id}/engagement` 응답의 `viewer.loggedIn`으로만
  판단합니다. 이 응답은 비로그인과 만료 쿠키에서도 `200`입니다.
- 비로그인 상태에서 찜 버튼이나 댓글 입력을 누르면 요청을 보내지 않고 화면이 직접 `/login`으로
  이동합니다. 이 분기 판단은 순수 함수 `resolveBookmarkAction(state)`
  (`'LOGIN' | 'TOGGLE' | 'IGNORE'`)로 뽑아 두 상세 화면이 공유하고 단위 테스트로 고정합니다.

### 축제 상세 / 관광지 상세

- `FestivalDetailPage`와 `TourSpotDetailPage`는 `PageHeader.rightAction`에 `BookmarkButton`과
  기존 공유 버튼을 나란히 둡니다. 찜 상태는 `Heart`를 `fill-coral text-coral`로 채워 표시하며,
  이는 `MyPage`가 쓰던 관용구와 같습니다.
- 댓글 섹션(`ContentCommentSection`)은 `<main>`의 마지막 요소입니다. 두 화면이 같은 컴포넌트를
  `target={{ type: 'FESTIVAL' | 'TOUR_PLACE', id }}`로 재사용합니다.
- 댓글 본문은 `ExpandableText`(200자 컷)를 재사용해 길어지면 접습니다.
- 좋아요는 `ThumbsUp`으로 찜(`Heart`)과 구분합니다. `Star`는 유보된 리뷰 도메인
  (`TourSpot.rating`)과 충돌하므로 쓰지 않습니다.
- 목록은 무한 스크롤이 아니라 `댓글 더 보기` 버튼입니다. 두 상세 화면 모두 하단 고정 CTA가 있어
  무한 스크롤이 스크롤 종료 지점을 잡아먹습니다.
- 본문 길이 제한은 500자이며 `validateCommentBody`로 trim 후 검증합니다. 입력 중 남은 글자 수를
  표시합니다.
- 삭제 버튼은 `mine`이 `true`일 때만 노출합니다. 관리자 숨김 버튼은 `viewer.admin`이 `true`일 때만
  노출하며, 이 두 값 모두 이미 받은 응답에서 나오므로 추가 요청이 없습니다.

### 상태 관리와 방어 규칙

- `useContentBookmark`, `useContentComments`는 `useMemberBlocks` 패턴을 따릅니다. framework 없는
  `createContentBookmarkSession` / `createContentCommentsSession` closure가 상태와 in-flight
  guard, `AbortController`, request identity를 들고 있고 hook은 얇은 wrapper입니다. 이 저장소
  vitest는 node 환경이고 jsdom이 없어 렌더링 없이 검증할 수 있어야 하기 때문입니다.
- **낙관적 갱신을 하지 않습니다.** 찜 토글, 댓글 등록·삭제, 좋아요는 서버 응답 성공 후에만
  화면 상태를 바꿉니다. 좋아요 카운트는 서버가 돌려준 실제값(`{liked, likeCount}`)으로 덮습니다.
- 이중 클릭은 동기 in-flight guard로 흡수하고, unmount 뒤 늦은 응답은 request identity와
  `stop()`으로 무시합니다.
- 다음 페이지를 불러올 때 `dedupeCommentsById`로 `id` 중복을 제거합니다. offset 페이징이라
  새 댓글이 등록되면 다음 페이지에 1건이 중복될 수 있습니다
  (`docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md` 5.5절의 알려진 한계).
- 성공 안내는 기존 `successMessage` + `clearSuccess` 인라인 pill 패턴을 씁니다. toast 컴포넌트는
  도입하지 않았습니다.

## 마이페이지 찜 목록

- 마이페이지의 `찜한 곳` 섹션은 mock(`data/mock/tourSpots.ts`)을 제거하고 실제 API로 교체했습니다.
  축제와 관광지를 각각 조회해 클라이언트에서 병합합니다(합쳐 주는 단일 엔드포인트는 없습니다).
- 헤더 우측 `전체 보기`는 `/mypage/favorites`(`FavoritesPage`)로 이동합니다. `/mypage/blocks`와
  같은 계층이며 loading, `아직 찜한 곳이 없어요`, 오류·재시도 상태를 구분하는
  `BlockedMembersPage` 패턴을 따릅니다.
- 상단 `Chip` 탭으로 축제와 관광지를 나눕니다. 항목은 `FestivalListItem`과 `ExploreSpotItem`을
  재사용하므로 진행 중·예정·마감 배지가 기존 규칙 그대로 표시됩니다.
- 목록에서 찜을 해제할 때도 낙관적으로 제거하지 않고, 서버 성공 후 해당 항목만 제거합니다.
- `HIDDEN` 대상은 서버가 목록에서 제외합니다. `INACTIVE`와 종료된 축제는 목록에 남기고 배지로만
  구분합니다 — 사용자가 명시적으로 저장한 항목이 동기화 사정으로 사라지면 안 됩니다.

## 이미지 없는 콘텐츠의 기본 이미지

관광공사 동기화 데이터는 `firstimage`가 비어 있는 콘텐츠가 적지 않습니다. 이때 쓰는 기본
이미지 규칙입니다.

- 프리셋 계산은 `components/common/imagePlaceholderPresets.ts`(순수 함수), 렌더링은
  `components/common/ImagePlaceholder.tsx`가 담당합니다. 이 저장소 vitest는 node 환경이고
  jsdom이 없어 계산 로직을 렌더링 없이 검증할 수 있어야 하기 때문입니다.
- 프리셋은 관광지 동기화 대상 `contentTypeId` 4종(`12` 관광지 / `14` 문화시설 / `28` 액티비티 /
  `39` 맛집)에 축제(`FESTIVAL`)와 중립 fallback(`DEFAULT`)을 더한 6종입니다. 관광지 호출부는
  `placeholderKindFromContentType(spot.contentTypeId)`로 프리셋을 구하고, 축제 호출부는
  `kind="FESTIVAL"`을 직접 넘깁니다. 동기화 대상이 늘거나 값이 없으면 `DEFAULT`로 떨어집니다.
- 분류 문구는 `utils/tourSpot.ts`의 `contentTypeLabel`과 같은 어휘(관광지/문화시설/액티비티/맛집)를
  씁니다. 두 곳을 함께 바꿔야 화면 어휘가 어긋나지 않습니다.
- 배경색은 프리셋 accent를 앱 배경(`sand`)에 옅게 섞은 그라데이션이고, 톤 변형은 `seed`
  문자열(보통 콘텐츠 제목) 해시로 고릅니다. **같은 콘텐츠는 항상 같은 톤**이라 목록을 다시
  열어도 색이 바뀌지 않고, 같은 분류 카드가 여러 개 나와도 서로 구분됩니다.
- 스트라이프·펄스처럼 "곧 채워질 것"으로 읽히는 패턴은 쓰지 않습니다. 로딩 스켈레톤과
  혼동되면 사용자가 이미지를 계속 기다리게 됩니다.
- 크기 단계를 반드시 넘깁니다. `sm`(약 80px 이하)은 아이콘만, `md`(약 80~150px)는 아이콘과 분류
  문구, `lg`(약 150px 이상)는 능선 라인아트까지 그립니다. 56px 썸네일에 문구를 넣으면 답답하고
  240px 히어로에 아이콘만 두면 미완성처럼 보입니다.
- 문구가 없는 `sm`에서도 읽히도록 `role="img"`과 `aria-label`("{분류} 사진 준비 중")을 항상
  붙입니다.
- 좌표(`mapX`/`mapY`)가 없어 지도를 못 그리는 경우는 원인이 달라 `MapPlaceholder`를 씁니다.
  격자 배경으로 지도 자리임을 알리고 `좌표 정보가 없어 지도를 표시할 수 없어요`로 이유를
  밝힙니다. 사진 없음과 같은 얼굴로 보이면 안 됩니다.
- `TourSpot.contentTypeId`는 `utils/tourSpot.ts`의 세 mapper가 채웁니다. 관광지 목록/상세/근접
  조회 응답이 모두 `contentTypeId`를 내려주므로 backend 변경은 필요하지 않습니다.

## 1:1 문의 화면

설계는 [docs/28_MEMBER_INQUIRY_DESIGN.md](28_MEMBER_INQUIRY_DESIGN.md)를 따릅니다.

- 화면은 `/mypage/inquiries`(목록), `/mypage/inquiries/new`(작성), `/mypage/inquiries/:inquiryId`
  (스레드) 3개이고, 진입점은 `MyPage`의 "1:1 문의" 행입니다. 목록은 `BlockedMembersPage`의
  상태 분기 패턴(loading / 빈 상태 / 오류·재시도)을 따릅니다.
- **미확인 답변 badge는 부가 기능이 아니라 필수입니다.** 관리자 답변을 사용자에게 밀어줄 채널이
  없습니다 — STOMP는 `/matching`·`/match-room`에서만 연결되고, Web Push는 VAPID 키·구독
  table·권한 UI가 전무하며, 메일 발송 인프라도 없습니다. badge가 유일한 도달 신호이므로
  진입점에서 빼면 사용자는 답변이 온 사실을 알 수 없습니다.
- badge 값은 `GET /api/members/me/inquiries/unread-count`로 읽습니다. 목록 전체를 불러오지
  않습니다. 조회에 실패하면 badge를 감추고 진입점은 그대로 둡니다(안전 알림 badge와 같은 방식).
- 스레드 상세를 여는 것만으로 서버가 열람 시각을 갱신해 badge가 꺼집니다. 별도 "읽음" 호출이
  없습니다.
- **작성 폼에 개인정보 입력 자제 안내를 반드시 노출합니다.** 본문을 평문으로 저장하기 때문입니다
  (`docs/28` 3.1). 함께 "동행 중 문제는 신고 기능을 이용해 주세요"도 안내합니다 — 안전 카테고리를
  두지 않는 결정의 화면 쪽 대응입니다(`docs/28` 3.4).
- **작성 폼에 긴급 선택 입력을 두지 않습니다.** 긴급 지정은 관리자만 합니다. 사용자가 고르게
  하면 사실상 전부 긴급으로 들어와 우선순위가 무의미해집니다.
- 관리자 답변은 작성자를 특정하지 않고 **"운영팀"** 으로만 표시합니다. 응답에 관리자
  `memberId`·닉네임이 애초에 없습니다.
- 관리자 화면은 `/admin/inquiries`(`AdminInquiriesPage`)이며 `AdminNav`의 5번째 메뉴입니다.
  미처리(`RECEIVED`·`IN_PROGRESS`) badge는 `AdminInquiryPageResponse.openCount`를 읽고,
  `/admin/reports`의 안전 알림 badge와 같은 실패 처리 규칙을 따릅니다. badge 라벨과 숫자는
  `badgeOf()` 한 곳에서 함께 정해 서로 어긋나지 않게 합니다.
- 관리자 목록에 **긴급 우선 정렬을 제공하지 않습니다.** 정렬 키와 cursor 키가 어긋나면 페이지
  경계에서 항목이 중복·누락됩니다. 대신 긴급 filter를 둡니다(`docs/28` 5.7).
- 문의 유형·상태 라벨과 상태 배지 색은 `api/inquiries.ts`에만 둡니다. 화면마다 code를 문구로
  바꾸면 노출 심사를 여러 곳에서 해야 합니다.
