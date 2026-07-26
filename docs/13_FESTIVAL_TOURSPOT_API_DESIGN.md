# 축제 상세·관광지 연동 API 설계

이 문서는 홈/탐색/축제 상세/관광지 상세 화면에 관광공사 동기화 데이터를 바인딩하기 위한 API 설계와 구현 현황을 기록합니다.

> 이 문서는 최초 작성 시점(설계 단계)에는 코드 변경 전 설계 기록이었으나, 이후 브랜치 `feature/wbs-10-a-festival-api`에서 아래 "구현 현황"에 정리된 범위까지 실제로 구현되었습니다. 이 문서는 그 구현 결과에 맞춰 갱신된 버전입니다.

## 1. 배경

`FestivalSyncScheduler`가 관광공사 TourAPI `searchFestival2`로 축제 데이터를 `festivals` 테이블에 동기화하고 있습니다. 이 데이터를 아래 4개 화면에 실제로 노출하고, 축제뿐 아니라 주변 관광지도 함께 소개하기 위해 상세 API와 관광지 도메인을 추가했습니다.

대상 화면:

- `HomePage`
- `ExploreListPage`
- `FestivalDetailPage`
- `TourSpotDetailPage`

## 2. 구현 현황 요약

| 설계 단계(6장 기준) | 상태 | 비고 |
| --- | --- | --- |
| 1. 축제 상세 API + `FestivalDetailPage` 실연동 | ✅ 완료 | `GET /api/festivals/{id}` |
| 2. status/ddayLabel 매핑 유틸 + `HomePage`/`ExploreListPage`(축제) 실연동 | ✅ 완료 | `utils/festival.ts` |
| 3. `tour_places` 도메인 신설 | ✅ 완료 | `domain/tourplace` 패키지, sync scheduler 포함 |
| 4. 축제 ↔ 관광지 근접 관계 API + Explore(관광지)/`TourSpotDetailPage` 실연동 | ✅ 완료 | haversine 기반 |
| 5. 목록 필터 확장(`keyword`를 backend 쿼리 파라미터로) | ✅ 완료(`keyword`만) | `category`(contentTypeId)는 이미 구현돼 있었음. `region`/`sort`는 여전히 미구현 (7장 참고) |

## 3. Backend 구현 내용

### 3.1 축제 상세 API

- `GET /api/festivals/{id}` (`FestivalController.getFestival`) 추가.
- 응답은 `FestivalDetailResponse`(id, contentId, title, address, regionCode, sigunguCode, eventStartDate, eventEndDate, status, mapX, mapY, originImageUrl, thumbnailUrl) — `festivals` 테이블에 있는 필드만 사용합니다.
- `intro`/`programs`/`infoItems`처럼 TourAPI `detailCommon2`/`detailIntro2` 호출이 필요한 필드는 여전히 범위 밖입니다 (7장 참고).

### 3.2 화면 표시용 status/ddayLabel은 프론트에서 계산

backend는 데이터 정합성 상태(`ACTIVE`/`ENDED`/`HIDDEN`)만 관리하고, 화면 표시용 `ongoing`/`upcoming`/`ended`와 `ddayLabel`은 응답의 `eventStartDate`/`eventEndDate`와 KST 오늘 날짜로 frontend `utils/festival.ts`에서 파생시킵니다(설계대로 구현됨).

### 3.3 관광지(TourSpot) 도메인

`domain/tourplace` 패키지를 `festival` 도메인과 같은 구조로 신설했습니다.

- **엔티티**: `TourPlace`(`tour_places` 테이블, `V2__create_core_tables.sql`에 이미 존재하던 테이블을 그대로 사용 — 신규 migration 없음). 컬럼: `contentId`(unique), `contentTypeId`, `title`, `address`, `mapX`/`mapY`, `tel`, `imageUrl`, `status`(`TourPlaceStatus`: `ACTIVE`/`INACTIVE`/`HIDDEN`), `lastSyncedAt`, `rawData`(JSONB). `festival_images`와 달리 대표 이미지는 별도 테이블 없이 `imageUrl` 단일 컬럼입니다.
- **동기화**: `TourApiClient.searchTourPlaces(SearchTourPlaceRequest)`가 TourAPI `areaBasedList2`를 호출합니다(`KoreaTourApiRestClient`). `TourPlaceSyncScheduler` → `TourPlaceSyncService` → `TourPlaceSyncMapper`/`TourPlaceSyncWriter`가 festival 동기화와 유사한 패턴(실패 시 기존 데이터 유지, 응답에 없는 기존 `ACTIVE`는 `INACTIVE`로 정리, 지수 백오프 재시도)을 재사용하되, DB 반영은 타입별로 배치 커밋합니다.
  - `app.tour-place.sync.content-type-ids`(기본값 `12,14,28,39` = 관광지/문화시설/레포츠/음식점, `TOUR_PLACE_SYNC_CONTENT_TYPE_IDS` 환경변수로 override)로 4개 타입을 모두 순회 동기화합니다. `TourPlaceSyncProperties`가 이 값이 DB CHECK 제약과 동일한 4개 값인지 기동 시 검증합니다.
  - 그 외 설정: `initial-delay`(20s), `fixed-delay`(12h), `page-size`(100), `max-pages`(100), `region-code`(51), `batch-size`(500), `retry-max-attempts`(3), `retry-initial-delay`(1s), `retry-max-delay`(10s) — 이름 규칙은 festival sync와 동일.
  - **배치 커밋**: 테스트 API 키의 일일 호출 한도(1,000회) 안에서, 한 타입의 페이지 수집 중간에 호출이 실패해도 이미 처리된 데이터를 잃지 않도록 `batch-size`(기본 500)만큼 모일 때마다 `TourPlaceSyncWriter.upsertBatch()`로 즉시 커밋합니다(`TourPlaceSyncService.synchronizeContentType`). 예를 들어 한 타입이 1,500건이면 500건씩 3번 나눠 커밋되고, 11페이지째(1,100번째 근처)에서 실패하면 앞서 커밋된 1,000건은 유지되고 나머지만 이번 회차에 반영되지 않습니다.
  - 다만 **INACTIVE 정리**(`TourPlaceSyncWriter.markMissingInactive()`)는 배치 단위로 쪼개지 않고, 해당 타입의 모든 페이지 수집이 100% 성공했을 때만 마지막에 한 번 실행됩니다. 일부 페이지만 받은 상태에서 실행하면 아직 못 받은 페이지에 있던 정상 데이터까지 사라진 것으로 오판해 `INACTIVE` 처리할 위험이 있기 때문입니다.
  - 관광공사 API가 일일 호출 한도 초과 시 내려주는 `resultCode=22`는 `RATE_LIMIT`으로 분류되어 재시도 대상에 포함됩니다(`TourApiResponseParser`). 한도 초과는 재시도로 해소되지 않으므로, 한도 초과 시점 이후의 타입은 이번 회차에 반영되지 않고 다음 `fixed-delay` 주기를 기다립니다.
- **조회 API**: `TourPlaceController`
  - `GET /api/spots?page&size&contentTypeId` → `TourPlaceListResponse`(items: `TourPlaceListItemResponse`, 페이지 메타).
  - `GET /api/spots/{id}` → `TourPlaceDetailResponse`(id, contentId, contentTypeId, title, address, tel, mapX, mapY, status, imageUrl). `HIDDEN` 상태는 404 처리.
  - `GET /api/spots/{id}/nearby-festivals?radiusMeters&limit` → `NearbyFestivalResponse` 목록 (3.4 참고).

### 3.4 축제 ↔ 관광지 근접 관계

관계 테이블을 두지 않고, `global/geo/GeoDistanceCalculator`(haversine 공식, 지구 반지름 6,371km)로 조회 시점에 거리를 계산합니다. PostGIS는 도입하지 않았습니다.

- `GET /api/festivals/{id}/nearby-spots?radiusMeters(기본 5000, 100~20000)&limit(기본 10, 1~50)` → `FestivalQueryService.getNearbyTourPlaces` → `NearbyTourPlaceResponse`(id, title, address, contentTypeId, imageUrl, distanceMeters), 거리 오름차순 정렬.
- `GET /api/spots/{id}/nearby-festivals?radiusMeters&limit` → `TourPlaceQueryService.getNearbyFestivals` → `NearbyFestivalResponse`(id, title, address, eventStartDate, eventEndDate, status, thumbnailUrl, distanceMeters).
- 두 API 모두 좌표가 없는 기준 엔티티는 빈 목록을 반환하고, `radiusMeters` 초과 후보는 제외한 뒤 거리순으로 `limit`만큼 자릅니다.

### 3.5 목록 필터 확장 — `keyword`만 구현

당초 설계였던 `category`/`keyword`/`region`/`sort` 중 실제로 필요했던 건 `keyword`뿐이었습니다. `category`(관광지 `contentTypeId`)는 이미 3.3에서 구현돼 있었고, `region`/`sort`는 실제로 값을 넘길 UI(지역 선택 드롭다운, 정렬 기준)가 없는 장식용 칩이라 백엔드 파라미터만 먼저 만드는 건 미사용 코드가 되어 이번 범위에서 제외했습니다 (7장 참고).

- `GET /api/festivals?keyword=`, `GET /api/spots?keyword=`: 제목 부분일치(대소문자 무관) 필터. `FestivalQueryService`/`TourPlaceQueryService`가 `keyword`를 trim하고 공백뿐이면 빈 문자열로 정규화해 repository에 넘깁니다.
- **구현 시 발견한 이슈**: JPQL에서 `:keyword is null or lower(title) like lower(concat('%', :keyword, '%'))` 패턴을 쓰면, PostgreSQL이 `keyword`가 null로 바인딩될 때 파라미터 타입을 추론하지 못해(`bytea`로 오판) `function lower(bytea) does not exist` 오류가 납니다. 그래서 `is null` 분기를 없애고, keyword가 없으면 서비스 계층에서 빈 문자열(`""`)을 넘겨 항상 `LIKE '%%'` 패턴(모든 제목과 매칭)이 적용되도록 바꿨습니다. `contentTypeId`처럼 단순 동등 비교(`=`)만 하는 파라미터는 이 문제가 없어 `is null or` 패턴을 그대로 유지했습니다.
- `ExploreListPage`는 여전히 `page=0&size=100`으로 가져오지만(페이지네이션 UI 자체가 없어 이번 범위 밖), `keyword`(300ms 디바운스)와 관광지 `contentTypeId` 카테고리를 서버 쿼리 파라미터로 보내고 클라이언트 필터링은 제거했습니다.
- UI에 보이는 "지역/일정/정렬"(축제), "현재 위치/거리/정렬"(관광지) 칩은 여전히 동작하지 않는 표시용 라벨입니다. 실제 지역 선택 UI와 정렬 기준이 정해지면 backend 파라미터를 추가로 도입해야 합니다.

## 4. 화면별 연동 현황

| 화면 | 연동 API | 상태 |
| --- | --- | --- |
| `HomePage` | `GET /api/festivals`, `GET /api/festivals/{id}/nearby-spots` | ✅ mock 제거, 실 API 연동 |
| `ExploreListPage` | `GET /api/festivals?keyword=`, `GET /api/spots?contentTypeId=&keyword=` | ✅ mock 제거, 실 API 연동, `keyword`/`category` 서버 필터 적용 / ❌ `region`/`sort`는 미구현 |
| `FestivalDetailPage` | `GET /api/festivals/{id}`, `GET /api/festivals/{id}/nearby-spots` | ✅ mock 제거, 실 API 연동 |
| `TourSpotDetailPage` | `GET /api/spots/{id}`, `GET /api/spots/{id}/nearby-festivals`, `GET /api/spots` | ✅ mock 제거, 실 API 연동 |

`data/mock/festivals.ts`, `data/mock/spotDetails.ts`는 삭제되었습니다. `data/mock/tourSpots.ts`는 일부 잔존 참조가 있어 완전히 제거되지 않았습니다.

## 5. Frontend 구현 내용

- `api/festivals.ts`, `api/spots.ts` 신설. 각 함수가 `apiClient`(공통 `ApiResponse<T>` 언래핑)를 통해 backend를 호출합니다.
- `utils/festival.ts`에 `mapFestivalListItemToFestival`, `mapNearbyFestivalToFestival` 등 backend 응답 → 프론트 `Festival` 타입 매퍼를 두고, 여기서 status/ddayLabel을 계산합니다.
- `utils/tourSpot.ts`(신규)에 `mapTourPlaceListItemToTourSpot`, `mapTourPlaceDetailToTourSpot`, `mapNearbyTourPlaceToTourSpot`, `formatDistanceLabel`, `formatWalkMinutesLabel` 등 관광지 매퍼/포맷터를 둡니다.
- 페이지 컴포넌트(`HomePage`, `ExploreListPage`, `FestivalDetailPage`, `TourSpotDetailPage`)는 `data/mock/*` 대신 위 API 함수를 호출하도록 교체되었습니다.
- backend에 없는 필드(`matchingCount`, `rating`, `reviewCount` 등)는 optional로 두고 해당 UI 블록은 조건부 렌더링을 유지합니다.

## 6. 테스트 커버리지

- `FestivalControllerTest`, `FestivalQueryServiceTest` — 상세/근접 API 케이스, `keyword` 파라미터 전달/정규화(trim, 공백뿐이면 빈 문자열) 케이스 추가.
- `TourPlaceControllerTest`, `TourPlaceQueryServiceTest`, `TourPlaceSyncMapperTest`, `TourPlaceSyncServiceTest`, `TourPlaceSyncWriterTest`, `TourPlaceSyncSchedulerTest` — 관광지 도메인 전체(조회, 동기화 매핑/쓰기/재시도, 스케줄러) 신규 작성 + `keyword`/`contentTypeId` 정규화 케이스 추가.
- `FestivalSyncWriterIntegrationTest` — 실제 PostgreSQL로 `findVisibleFestivals` 조회 검증(3.5절의 null 바인딩 이슈를 이 테스트에서 발견).
- `GeoDistanceCalculator`에 대한 별도 단위 테스트는 확인되지 않았습니다 — 필요 시 후속 작업으로 추가 검토합니다.

## 7. 미해결 이슈

- `distanceKm`(사용자 GPS 기준 거리)은 아직 GPS 체크인/위치 권한 로직이 없어 노출하지 않습니다. 현재 근접 API는 축제/관광지 좌표 간 거리(`distanceMeters`)만 제공합니다.
- `matchingCount`, `matchSupported`는 매칭 도메인 구현 이후에 채워질 값입니다.
- 축제 소개글(`intro`), 이용 정보(`infoItems`), 프로그램(`programs`)은 `detailCommon2`/`detailIntro2` 등 추가 TourAPI 호출이 필요하며, 여전히 범위 밖입니다.
- 목록 필터 중 `region`(지역 선택)/`sort`(정렬 기준)는 여전히 미구현입니다. `ExploreListPage`의 "지역/일정/정렬" 등 필터 칩은 현재 UI에만 존재하고 동작하지 않는 표시용 라벨이며, 실제 지역 선택 UI·정렬 기준이 정해지기 전까지는 backend 파라미터를 추가하지 않기로 했습니다(`keyword`는 3.5절대로 구현 완료).
- `data/mock/tourSpots.ts`는 완전히 제거되지 않고 일부 남아 있습니다. 정리 필요 여부 확인이 필요합니다.
- ~~관광지 동기화가 관광공사 테스트 API 키(일일 1,000회 한도)를 축제 동기화와 공유하고 있어 한도 초과 가능성~~ — 실제 호출량 확인 결과 한도에 여유가 있음을 확인했습니다(배치 커밋 안전장치는 3.3절 참고).
