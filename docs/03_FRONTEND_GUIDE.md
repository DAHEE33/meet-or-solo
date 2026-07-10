# 프론트엔드 가이드

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
| `SplashPage` | 초기 로딩과 세션 bootstrap |
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

`MatchRoomPage`는 자유 채팅방이 아닙니다.

시스템 이벤트 타임라인과 제한형 버튼 인터랙션을 제공하는 상태 동기화 화면입니다.

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

## Kakao Maps

Kakao Maps는 추후 다음 용도로 사용합니다.

- 축제 위치 표시
- 만남 포인트 핀 표시
- 주변 관광지 표시
- 솔로 코스 맥락 제공

Kakao JavaScript Key는 환경 설정으로 주입하고 저장소에 커밋하지 않습니다.
