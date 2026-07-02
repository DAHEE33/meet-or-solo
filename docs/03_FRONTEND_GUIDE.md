# 프론트엔드 가이드

## 프론트엔드 방향

프론트엔드는 React + TypeScript + Vite 기반 PWA입니다. 모바일 축제 방문자를 우선하고, 관리자 화면은 같은 코드베이스에서 확장합니다.

주요 목표:

- QR 또는 URL로 빠르게 접근한다.
- 홈 화면 추가를 지원한다.
- PWA shell을 준비한다.
- 매칭 상태를 명확하게 보여준다.
- 자유 채팅이 아니라 버튼 기반 제한형 인터랙션을 제공한다.

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
