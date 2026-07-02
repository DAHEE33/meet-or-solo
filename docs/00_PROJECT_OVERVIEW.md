# 프로젝트 개요

## 서비스 정의

`meet-or-solo`는 강원도 축제 현장에서 혼자 방문한 사용자를 2~4인 소그룹으로 즉석 매칭하는 현장형 PWA 서비스입니다.

이 서비스는 일반적인 소셜 네트워크나 자유 채팅 앱이 아닙니다. 축제 현장, GPS 체크인, 제한형 인터랙션, 안전한 만남, 실패 시 솔로 코스 전환에 집중합니다.

핵심 조건은 다음과 같습니다.

- 같은 축제에 방문한 사용자만 매칭한다.
- GPS 체크인을 통과한 사용자만 매칭풀에 진입한다.
- 사용자는 희망 인원과 태그를 선택한다.
- 시스템이 후보 그룹을 만들고 매칭 제안을 보낸다.
- 사용자는 버튼으로 수락/거절한다.
- 확정 후에는 `MatchRoomPage`에서 상태를 동기화한다.
- 매칭 실패 시 관광공사 OpenAPI 기반 솔로 코스를 추천한다.

## 핵심 사용자 흐름

1. PWA 접속
2. 로그인
3. 약관 및 위치정보 동의
4. 프로필 설정
5. 강원도 축제 피드 탐색
6. 축제 상세 확인
7. GPS 체크인
8. 희망 인원과 태그 선택
9. 자동 매칭 대기
10. 매칭 제안 수락/거절
11. 매칭 확정 시 `MatchRoomPage` 진입
12. 도착 시간, 도착 확인, 취소, 신고, 안전 알림 사용
13. 매칭 실패 시 솔로 코스 추천으로 전환

## 관광공사 OpenAPI 활용 방향

관광공사 OpenAPI는 단순한 부가 정보가 아니라 서비스 맥락을 만드는 핵심 데이터 레이어입니다.

예정 활용 API:

- `searchFestival1`: 강원도 축제 목록, 일정, 지역, 좌표 조회
- `detailCommon1`: 축제 상세, 주소, 개요, 기본 정보 조회
- `detailImage1`: 축제 및 주변 장소 이미지 조회
- `locationBasedList1`: 주변 관광지, 음식점, 카페, 액티비티 조회

활용 목적:

- 축제 피드 구성
- 축제 상세 화면 구성
- GPS 체크인 기준점 제공
- 매칭 태그와 축제 맥락 보강
- 매칭 실패 후 솔로 코스 추천
- 관리자 운영 및 API 호출 로그 증빙

API Key는 서버에서만 관리하고 저장소에 커밋하지 않습니다.

## PWA 선택 이유

MVP는 빠른 현장 검증과 낮은 운영 비용이 중요합니다. 따라서 React PWA를 우선 선택합니다.

PWA를 선택하는 이유:

- App Store/Play Store 심사 없이 배포할 수 있다.
- 축제 현장에서 QR 또는 URL로 바로 접근할 수 있다.
- Android Chrome에서 홈 화면 추가가 가능하다.
- Web Push를 통해 매칭 알림을 제공할 수 있다.
- 사용자 화면과 관리자 화면을 같은 React 코드베이스에서 관리할 수 있다.
- Nginx가 정적 `dist` 파일을 직접 서빙할 수 있다.

## React Native가 아니라 React PWA를 사용하는 이유

MVP에서는 React Native보다 React PWA가 적합합니다.

React Native를 제외하는 이유:

- 네이티브 앱 패키징과 배포 절차가 무겁다.
- 앱 심사가 현장 테스트 속도를 늦출 수 있다.
- Push, Location, Deep Link 설정이 초기 비용을 높인다.
- 첫 유입 경로는 앱스토어 검색보다 QR/URL 공유가 될 가능성이 높다.

React PWA를 선택하는 이유:

- 개발과 배포가 빠르다.
- Vite 기반 빌드 결과를 Nginx에서 바로 서빙할 수 있다.
- 모바일 우선 UI를 만들기 쉽다.
- 필요하면 추후 TWA 또는 Capacitor 래핑으로 확장할 수 있다.

## 역할 분리

모노레포는 책임 기준으로 나눕니다.

- `frontend`: React + TypeScript + Vite + PWA 사용자/관리자 화면
- `backend`: Spring Boot API, 보안, Scheduler, 매칭 상태, WebSocket STOMP
- `db`: Flyway migration 및 DB 초기화 자산
- `infra`: nginx, certbot, 배포 인프라 설정 초안
- `.github`: GitHub Actions workflow 초안
- `docs`: 프로젝트 규칙, 설계, 정책 문서

백엔드는 비즈니스 규칙과 Secret을 소유합니다. 프론트엔드는 화면과 클라이언트 상태를 소유합니다. 인프라는 트래픽 라우팅과 운영 경계를 소유합니다.

## MVP 원칙

MVP 1단계는 개발환경 세팅만 진행합니다. 비즈니스 기능은 별도 승인 전까지 구현하지 않습니다.

Redis는 MVP 1단계에서 제외합니다. 초기 매칭 상태, TTL, 동시성 처리는 PostgreSQL의 `status`, `expires_at`, transaction lock, Spring Scheduler로 처리합니다.
