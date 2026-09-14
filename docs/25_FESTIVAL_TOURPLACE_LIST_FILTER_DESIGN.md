# 축제·관광지 목록 필터·정렬·무한스크롤 설계

## 1. 배경과 범위

`docs/13_FESTIVAL_TOURSPOT_API_DESIGN.md` 3.5절과 7장에서 목록 필터 중 `region`(지역 선택)과
`sort`(정렬)는 **"실제 지역 선택 UI와 정렬 기준이 정해지기 전까지는 backend 파라미터를 추가하지
않는다"**로 보류했습니다. `ExploreListPage`의 "지역/일정/정렬"(축제), "현재 위치/거리/정렬"(관광지)
칩은 지금도 동작하지 않는 표시용 라벨입니다.

이번 설계는 그 보류를 해제하고 다음 세 가지를 확정합니다.

1. 홈 화면에서 **내 위치 기준 가장 가까운 축제**를 메인(히어로)으로 노출 — 좌표를 서버로 보내지
   않는 클라이언트 계산 방식
2. **축제 목록** — 지역(시군구)·정렬 필터, 일정·매칭 가능 필터, "가까운순" 제거, 20개 단위 무한스크롤
3. **관광지 목록** — 지역(시군구)·정렬 필터, 20개 단위 무한스크롤

원안 3번에 있던 "내 위치 기준 / 반경 10·50·100km / 가까운순·먼순"은 **3.1의 결정(GPS 좌표를
서버로 보내지 않음)에 따라 제외**했습니다. 사유는 6.4절입니다.

**진행 상태: 설계 승인 완료, 구현 진행 중.** 승인된 결정 사항은 13장에 정리했습니다.

### 1.1 문서 위치에 대한 메모

`docs/13_FESTIVAL_TOURSPOT_API_DESIGN.md`는 121줄짜리 **구현 완료 기록(as-built)**입니다. 이번
설계는 DB migration, 정책 문서 갱신, 신규 API 파라미터까지 포함하는 **예정 작업(to-be)**이라
분량이 그 문서를 넘어섭니다. `docs/22`, `23`, `24`가 모두 doc 13을 참조하는 별도 설계 문서인
저장소 관례에 맞춰 새 문서로 분리하고, doc 13 7장에 이 문서를 가리키는 한 줄만 추가했습니다.

## 2. 사전 조사 결과 — 설계의 근거

설계 전에 dev DB(SSH 터널 경유, 읽기 전용)와 코드베이스를 실측했습니다. **이 수치들이 아래 설계
결정의 근거이며, 사용자 원안 중 일부를 수정 제안하는 이유입니다.**

### 2.1 데이터 규모와 지역 분포 — 현재 강원 1개 도뿐

| 테이블 | 전체 | ACTIVE | 비고 |
| --- | --- | --- | --- |
| `festivals` | 34건 | 15건 | 좌표 있는 ACTIVE는 14건 |
| `tour_places` | 4,020건 | 4,020건 | 5 MB |

- `festivals.area_code`: `51`(강원) 33건 + `null` 1건(테스트 데이터 `Matching UI test festival`)
- `tour_places` 주소 첫 토큰: `강원특별자치도` 4,003건 / `강원` 16건 / `경상남도` 1건
- `tour_places.raw_data ->> 'lDongRegnCd'`: **전 4,020건이 `51`**
- 동기화 설정도 단일 도로 고정: `FESTIVAL_SYNC_REGION_CODE:51`, `TOUR_PLACE_SYNC_REGION_CODE:51`

> **핵심 결론: "도 단위 지역 필터"는 지금 선택지가 사실상 1개(강원)뿐이라 기능이 성립하지 않습니다.**
> 사용자 원안 3번의 "각 도단위 지역별로 관광지 보여지게"는 **전국 동기화 확장이 선행돼야 합니다**
> (10장). 지금 당장 의미 있는 지역 단위는 **시군구**입니다.

`tour_places`의 시군구 분포(`lDongSignguCd`, 주소와 일치 확인):

| 코드 | 시군구 | 건수 | | 코드 | 시군구 | 건수 |
| --- | --- | --- | --- | --- | --- | --- |
| 150 | 강릉시 | 782 | | 820 | 고성군 | 168 |
| 760 | 평창군 | 523 | | 230 | 삼척시 | 168 |
| 110 | 춘천시 | 341 | | 730 | 횡성군 | 143 |
| 130 | 원주시 | 273 | | 810 | 인제군 | 141 |
| 750 | 영월군 | 250 | | … | (총 18개 시군구) | |
| 210 | 속초시 | 226 | | | | |
| 830 | 양양군 | 224 | | | | |
| 720 | 홍천군 | 175 | | | | |

### 2.2 `tour_places`에 지역 코드 컬럼이 없다

실제 컬럼: `id, content_id, content_type_id, title, address, map_x, map_y, tel, image_url,
status, last_synced_at, raw_data, created_at, updated_at`

**`area_code`/`sigungu_code`가 없습니다.** `festivals`에는 둘 다 있고 채워져 있습니다.

다행히 `tour_places.raw_data`에 **`lDongRegnCd`(시도)와 `lDongSignguCd`(시군구)가 전 건 존재**합니다
(`areacode`/`sigungucode` 키는 없음 — 최신 TourAPI가 `lDong*`을 씁니다). 즉 **TourAPI를 다시
호출하지 않고 migration 안에서 `raw_data`로 백필할 수 있습니다.**

주소 텍스트 파싱(`split_part(address, ' ', 1)`)은 시도 표기가 `강원특별자치도`/`강원`으로 섞여 있어
정규화가 필요하므로, 시도·시군구 **코드**를 컬럼으로 승격하는 쪽이 안전합니다. 반면 시군구
**이름**은 `raw_data`에 없고 주소 2번째 토큰(`강릉시`, `평창군` …)이 일관되게 정확하므로 이름은
주소에서 얻습니다.

### 2.3 좌표 이상치 4건 — "먼 순" 정렬을 그대로 만들면 버그가 된다

한국 영역(위도 33~39, 경도 124~132) 밖 좌표가 **4건** 있습니다. 4건 모두 좌표값이 동일합니다.

| id | title | 주소 | map_x | map_y |
| --- | --- | --- | --- | --- |
| 876 | 마차진해변 | 강원특별자치도 고성군 … | 117.9925662504 | 19.6944274800 |
| 905 | 마평정수장 홍보관 | 강원특별자치도 삼척시 … | 117.9925662504 | 19.6944274800 |
| 935 | 만경대 | 강원특별자치도 동해시 … | 117.9925662504 | 19.6944274800 |
| 968 | 의암수력발전소 | 강원특별자치도 춘천시 … | 117.9925662504 | 19.6944274800 |

주소는 정상 강원인데 좌표만 남중국해 근처입니다(TourAPI 원본 오류). **"먼 순"을 반경 제한 없이
구현하면 이 4건이 항상 1~4위로 올라옵니다.** 대응은 6.4절.

### 2.4 재사용 가능한 기존 자산

| 용도 | 이미 있는 것 |
| --- | --- |
| 브라우저 GPS 1회 조회 | `utils/geolocation.ts` `getCurrentPosition()` — `{enableHighAccuracy: true, timeout: 10000, maximumAge: 0}`, 권한거부/타임아웃/미지원 한국어 메시지 매핑 완비. **그대로 재사용** |
| ~~위치 사용 동의 UI~~ | `GPSPermissionModal`은 체크인 전용으로 남겨두고 **이번 범위에서 쓰지 않습니다**(4.3) |
| 거리 계산(서버) | `global/geo/GeoDistanceCalculator.metersBetween()`, `boundingBox()` |
| 좌표 인덱스 | `idx_tour_places_status_coordinates`, `idx_festivals_status_coordinates` (V22) |
| 거리·도보 표시 | `utils/tourSpot.ts` `formatDistanceLabel`, `formatWalkMinutesLabel` |
| 페이징 응답 계약 | `FestivalListResponse`/`TourPlaceListResponse`에 `hasNext`, `totalElements`, `totalPages` **이미 존재(현재 미사용)** |
| 시군구 코드 | `festivals.sigungu_code`(채워짐), `tour_places.raw_data->>'lDongSignguCd'`(채워짐) |

### 2.5 현재 없는 것

- **무한스크롤 선례 없음.** `IntersectionObserver`, scroll 리스너, "더 보기"가 프론트 전체에 0건.
  공개 목록은 offset 페이징(`page`/`size`), 관리자만 opaque cursor 페이징이라 계약이 다릅니다.
- **지역 선택 UI 없음.** 시도 목록 배열, 주소 파싱 유틸, geocoding 전부 없음.
  `FestivalListItem.regionCode`/`sigunguCode`는 타입에만 있고 어떤 컴포넌트도 읽지 않습니다.
- **브라우징용 위치 조회 없음.** GPS는 체크인 경로(`useFestivalCheckin`)에서만 쓰이고,
  `HomePage`/`ExploreListPage`에는 좌표 획득 수단이 아예 없습니다.
- **`FestivalListItem`에 좌표가 없음** (`mapX`/`mapY` 미포함). 4장 설계에 필요합니다.
- **지역 코드→이름 매핑 자료가 코드에 없음.** `docs/tourism-api-doc/`의 `.docx`/`.xlsx`
  바이너리에만 있습니다.
- **축제 카테고리 데이터 없음.** `festivals.raw_data`의 `cat1`/`cat2`/`cat3`가 **33건 전부 `null`**.
- `ExploreListPage`의 "가까운 순"은 `<button>`도 아닌 `<span>`입니다(`ExploreListPage.tsx:181-184`).

## 3. 가장 중요한 제약 — 위치정보 정책

`docs/06_SECURITY_POLICY.md`의 "GPS와 위치정보" 절이 이번 설계를 크게 제약합니다. 원문 요지:

- 원본 좌표는 즉시 검증에만 사용하고 저장하지 않는다.
- **"GPS 좌표를 URL query, application/access log, error detail … 에 포함하지 않는다."**
- "위치기반서비스사업 신고 완료와 위치정보 약관·명시적 동의 적용 후 … 서버로 전송한다."
- "브라우저 위치 권한은 위치정보 이용에 관한 고지·동의를 대신하지 않는다."
- **"추후 위치 저장 기능이 필요하면 정책 문서를 먼저 갱신하고 별도 승인을 받는다."**

그리고 `docs/22_SOLO_COURSE_NEARBY_SPOT_DESIGN.md` 3장에서 이미 이렇게 정리했습니다.

> 이 프로젝트는 원본 GPS 좌표를 저장하지 않습니다. 그래서 "내 위치 기반 추천"은 사용자의 실시간
> 좌표가 아니라 **"현재 체크인한 축제의 좌표"를 중심으로 한 반경 검색**을 의미해야 합니다.

즉 사용자 원안의 "내 위치 기반"은 **기존 정책·설계 결정과 정면으로 충돌합니다.** 그대로 구현하면
안 되고, 두 가지 갈래가 있습니다.

| 방식 | 좌표가 서버로 가는가 | 정책 영향 |
| --- | --- | --- |
| **클라이언트 계산** — 서버는 좌표 목록만 주고, 브라우저가 자기 GPS로 거리 계산 | **아니오** | 정책 위반 없음. 신고·약관 이슈 없음 |
| **서버 계산** — 좌표를 서버로 전송 | 예 | `06_SECURITY_POLICY.md` 갱신 + 동의 UI + 신고 검토 필요. URL query 금지이므로 `POST` 본문 필수 |

**결정 원칙: 후보 건수가 적으면 클라이언트 계산, 많으면 서버 계산.**
축제는 ACTIVE 15건(전국 확장해도 수백 건)이라 클라이언트 계산이 성립합니다. 관광지는 4,020건
(전국 확장 시 6~8만 건 추정)이라 불가능합니다.

### 3.1 확정된 결정 — GPS 좌표를 서버로 보내지 않는다

사용자 결정으로 다음이 확정됐습니다.

> **어떤 기능이든 GPS 좌표를 서버로 전송하지 않는다. `docs/06_SECURITY_POLICY.md`는 갱신하지 않는다.**

이 제약을 적용한 결과:

| 기능 | 결과 |
| --- | --- |
| 홈 화면 최근접 축제 (원안 1) | **진행.** 브라우저에서만 계산해 좌표 전송이 없음(4장) |
| 관광지 내 위치 모드 (원안 3의 "현재 위치") | **제외.** 4,020건은 서버 계산이 불가피하고 그러려면 좌표 전송이 필요함 |
| 관광지 반경 10/50/100km 필터 | **제외.** 중심 좌표가 서버에 없으면 반경 계산이 불가능 |
| 관광지 가까운순/먼순 정렬 | **제외.** 중심점이 없어 거리 정렬이 원리적으로 불가능 |
| 관광지 지역 선택 모드 | **진행.** 시군구 기준(6장) |

따라서 관광지 목록은 **지역 선택 단일 모드**가 됩니다. 6장을 그에 맞춰 다시 썼고, 원래 6.2절
(좌표 `POST` 전송)과 6.5절(SQL haversine 정렬)은 **이번 범위에서 삭제**했습니다.

> **기존 체크인 기능은 그대로 둡니다.** `POST /api/festivals/{id}/checkin`은 좌표를 본문으로
> 전송하지만, `docs/06_SECURITY_POLICY.md`가 "GPS는 축제 체크인과 확정된 만남 포인트의 도착
> 검증에 사용합니다"로 이미 허용한 용도입니다. 이번 결정은 신규 기능에 적용되며 체크인을
> 되돌리라는 뜻이 아닙니다.

## 4. 설계 1 — 홈 화면: 내 위치 기준 가장 가까운 축제

### 4.1 방식: 클라이언트 계산 (좌표를 서버로 보내지 않음)

ACTIVE 축제가 15건뿐이고 `HomePage`는 **이미** `festivalsApi.getList(0, 20)`으로 목록을 받아옵니다.
여기에 좌표만 실려 오면 브라우저가 자기 GPS로 최근접 축제를 고를 수 있습니다.

- **서버 변경**: `FestivalListItemResponse`에 `mapX`, `mapY` 추가. `FestivalSummary` 프로젝션과
  `findVisibleFestivals` JPQL에 두 컬럼 추가.
  - Tier A(`docs/10_PROGRESS_LOG.md` `[10-A 후속 7]`)에서 프로젝션으로 컬럼을 줄였는데 다시 늘리는
    셈이지만, 되돌리는 대상은 `raw_data` JSONB(평균 775바이트)였고 좌표는 `numeric` 2개(약 20바이트)라
    실측상 영향이 없습니다.
- **프론트 변경**: `utils/geo.ts`(신규)에 haversine을 추가하고, `getCurrentPosition()`으로 얻은
  좌표와 목록의 `mapX`/`mapY`로 최근접 축제를 고릅니다.
  - 서버 `GeoDistanceCalculator`와 **같은 공식·같은 지구 반지름(6,371,000m)**을 쓰고, 이를
    주석에 명시합니다(두 곳에 같은 계산이 존재하는 이유를 남김).

이 방식의 결정적 장점은 **좌표가 기기를 벗어나지 않는다는 점**입니다. 정책 갱신도, 위치기반
서비스 신고 검토도, 서버 로그 마스킹도 필요 없습니다.

### 4.2 대표 축제 결정 우선순위

```
1. GPS 허용 + 좌표 있는 ACTIVE 축제 존재 → 최근접 축제 (거리 라벨 함께 노출)
2. GPS 거부 / 타임아웃 / 미지원 / 좌표 없는 축제만 존재 → 현재 로직 유지
   (진행중 첫 번째 ?? 예정 첫 번째)
```

- 2번 폴백은 **현재 동작과 완전히 동일**하므로, GPS를 쓰지 않는 사용자의 화면은 바뀌지 않습니다.
- 좌표가 `null`인 축제(현재 1건, 테스트 데이터)는 거리 계산 대상에서 제외합니다.
- GPS 조회는 화면 진입을 막지 않습니다. 목록이 도착하면 **폴백 기준으로 먼저 히어로를 렌더**하고,
  GPS 결과가 오면 최근접 축제로 교체합니다. 방금 적용한 `Promise.all` 분리와 같은 원칙입니다.

### 4.3 앱 자체 동의 모달은 두지 않는다

초안에서는 `GPSPermissionModal`을 브라우징용으로 확장하고 "최초 1회만 묻기"를 제안했지만,
검토 결과 **과잉이라 철회했습니다.**

- **법적 동의가 아닙니다.** 4.1에서 좌표를 서버로 보내지 않으므로 고지·동의의 대상이 되는
  위치정보 처리가 없습니다. 체크인의 모달은 좌표를 실제로 전송하니 고지 의미가 있지만 홈은
  성질이 다릅니다.
- **브라우저 권한창이 이미 그 역할을 합니다.** 브라우저가 허용/거부 상태를 기억하므로 앱이
  따로 기억할 필요가 없습니다.
- **`localStorage` 새 패턴을 열지 않게 됩니다.** 프론트에 `localStorage` 사용 선례가 0건인데
  "동의 여부 boolean" 하나 때문에 도입하는 건 비용 대비 이득이 없습니다.

따라서 홈 화면은 `getCurrentPosition()`을 바로 호출하고, 브라우저 권한창 결과에 따라 4.2의
폴백을 적용합니다. 권한을 거부한 사용자는 **현재와 완전히 동일한 화면**을 봅니다.

### 4.4 하드코딩된 지역 라벨 정리

`HomePage.tsx:73`에 **`전북 전주시의 축제`**가 하드코딩돼 있습니다(`onClick` 없는 장식용 버튼).
데이터는 강원이고 PWA manifest도 `강원도 축제 현장 매칭 PWA`라 서로 모순입니다.

- 최근접 축제가 정해지면 그 축제의 시군구명(주소 2번째 토큰)으로 교체합니다.
- GPS 미사용 폴백 상태에서는 라벨을 숨깁니다(잘못된 지역을 보여주지 않음).

## 5. 설계 2 — 축제 목록

### 5.1 지역 필터: "도" 대신 "시군구"

2.1에서 확인한 대로 도 단위는 선택지가 1개라 무의미합니다. `festivals.sigungu_code`가 이미 있고
채워져 있으므로 **시군구 단위**로 만듭니다.

시군구 **이름**의 출처가 문제입니다. 코드→이름 매핑 자료가 코드에 없고(2.5), 프론트에 18개를
하드코딩하면 **데이터에 없는 시군구까지 노출**됩니다(예: 축제가 없는 시군구를 선택하면 항상 빈 목록).

> **주의: 이 프로젝트가 쓰는 코드는 법정동 코드(`lDongRegnCd`/`lDongSignguCd`)입니다.**
> `KoreaTourApiRestClient.java:362-363, 388-389`가 TourAPI에 `lDongRegnCd`/`lDongSignguCd`
> 파라미터로 보냅니다. 관광공사 매뉴얼은 이 법정동 코드 체계와 구형 `areaCode` 체계를 구분하며
> **두 체계가 동일하다고 보장하지 않습니다.** 따라서 코드→이름 매핑을 임의로 하드코딩하면
> 틀릴 수 있습니다. 정확한 매핑이 필요하면 TourAPI의 `ldongCode2`/`areaCode2` 오퍼레이션이
> 런타임에 코드→이름 목록을 반환하므로 그쪽을 출처로 삼아야 합니다.

**해결: 지역 목록 전용 API를 만들어 "실제 데이터에 존재하는 지역만" 내려줍니다.**

```
GET /api/festivals/regions
→ [{ "sigunguCode": "150", "name": "강릉시", "count": 3 }, ...]
```

- `name`은 주소 2번째 토큰에서 얻습니다(2.2에서 일관성 확인).
- `count`는 현재 조회 가능한 ACTIVE 축제 수 → 사용자가 빈 지역을 고를 일이 없습니다.
- 관광지도 같은 형태로 `GET /api/spots/regions`를 둡니다.
- 이 목록은 거의 변하지 않으므로, 필요해지면 나중에 캐시 대상으로 검토합니다(지금은 불필요 —
  Tier A 실측 결과 이 규모에서 쿼리 비용이 무의미).

목록 조회 파라미터에는 `sigunguCode`를 추가합니다.

```
GET /api/festivals?page=&size=&keyword=&sigunguCode=&sort=
```

### 5.2 정렬

> **이 절은 14장에서 개정됐습니다.** 날짜 정렬(`START_DATE_ASC`/`END_DATE_ASC`)은
> 제거됐고 좋아요·후기 정렬이 추가됐습니다. 아래는 개정 전 기록입니다.

"가까운순"은 사용자 요청대로 **제거**합니다(축제 목록에는 거리 개념을 넣지 않음).

| 값 | 정렬 키 | 용도 |
| --- | --- | --- |
| `START_DATE_ASC` (기본) | `event_start_date ASC, id ASC` | 현재 동작과 동일 — 기본값 유지 |
| `END_DATE_ASC` | `event_end_date ASC, id ASC` | "종료 임박순" — 놓치기 전에 가기 |
| `RECENTLY_ADDED` | `created_at DESC, id DESC` | "최근 등록순" — 새로 올라온 축제 발견 |

- 기본값을 현재와 동일하게 두면 **정렬을 안 고른 사용자의 화면이 바뀌지 않습니다.**
- 정렬 키에 항상 `id`를 tie-breaker로 붙입니다. 무한스크롤에서 정렬이 불안정하면 페이지 경계에서
  **같은 항목이 중복되거나 누락**되기 때문입니다.
- `V22`에 `idx_festivals_status_event_end_date`가 이미 있어 `END_DATE_ASC`는 인덱스가 준비돼
  있습니다(다만 34행 규모에서는 Seq Scan이 선택됩니다 — Tier A 실측 참고).

### 5.3 무한스크롤 (20개 단위)

**백엔드 변경이 필요 없습니다.** 현재 응답에 `hasNext`, `totalElements`, `totalPages`가 이미
있는데 화면에서 쓰지 않고 있을 뿐입니다(2.4).

- `ExploreListPage`의 `getList(0, 100)` → `getList(page, 20)`으로 바꾸고 결과를 **누적**합니다.
- `IntersectionObserver`로 목록 끝 sentinel을 감시해 `hasNext`일 때만 다음 페이지를 요청합니다.
- **반드시 지킬 것**: `keyword`, `sigunguCode`, `sort`, 세그먼트, 카테고리 중 하나라도 바뀌면
  `page=0`으로 리셋하고 **누적 배열을 비웁니다.** 이걸 놓치면 필터를 바꿔도 이전 결과가 남습니다.
- 중복 요청 방지: 로딩 중이면 observer 콜백을 무시하는 가드가 필요합니다(스크롤 중 콜백이 연속
  발생함).

offset 페이징의 알려진 한계: 스크롤 중에 데이터가 바뀌면 항목이 중복/누락될 수 있습니다. 다만
동기화 주기가 6시간이라 실무상 발생 확률이 매우 낮고, cursor 페이징으로 바꾸면 공개 API 계약을
관리자 API 방식으로 통일해야 하는 큰 변경입니다. **offset을 유지하고 한계를 문서화**합니다.

> 만약 나중에 keyset(cursor) 페이징으로 옮긴다면 바퀴를 새로 만들 필요는 없습니다. 저장소에
> 이미 선례가 있습니다 — `AdminReportCursorCodec`/`AdminMemberCursorCodec`가 HMAC 서명 cursor +
> **필터 fingerprint 검증**(필터가 바뀐 cursor를 거부) + `size + 1` fetch로 `hasNext` 판정을
> 구현해 뒀습니다. 다만 두 codec이 이미 중복 구현이고 HMAC secret을 하나 공유하고 있어
> (`app.admin.report.cursor-hmac-secret`), 세 번째 사용처를 만들기 전에 공통화가 필요합니다.

### 5.4 추가 필터 분석 (사용자 요청 사항)

"그외 필터로 설정할 만한것들 분석설계" 요청에 대한 데이터 기반 판정입니다.

| 후보 | 데이터 근거 | 판정 |
| --- | --- | --- |
| **일정/기간** (진행중·이번 주말·이번 달·직접 지정) | `event_start_date`, `event_end_date` 존재 | **채택 권장.** UI 칩에 이미 "일정"이 있어 자리도 잡혀 있음 |
| **매칭 가능한 축제만** | `festival_meeting_points`에 ACTIVE 행이 있는 축제. 현재 15/15 보유 | **채택 권장.** 이 서비스의 존재 이유가 매칭이고, `MatchPoolEntryService`가 만남 장소 없는 축제의 풀 진입을 `MATCHING_MEETING_POINT_NOT_READY`로 막음. 신규 축제는 백필 전까지 공백이 생기므로 필터 가치가 실재함 |
| 카테고리(축제 종류) | `raw_data`의 `cat1/cat2/cat3`가 **33건 전부 null** | **불가.** 데이터가 없음. doc 13도 축제 세그먼트에 카테고리 칩을 노출하지 않기로 이미 결정 |
| 무료/유료, 실내/실외, 관람 연령 | `detailIntro2` 응답에만 있고 DB에 저장하지 않음(온디맨드 조회) | **불가.** 목록 필터로 쓰려면 동기화 대상에 추가하는 별도 작업이 선행 |
| 진행 상태(ongoing/upcoming) | 프론트 `resolveDisplayStatus`가 계산하는 값 | **주의.** 서버 필터로 만들면 doc 13 3.2의 "화면 표시용 status는 backend에 두지 않고 프론트에서 계산한다" 결정을 뒤집게 됨. **"일정" 필터로 흡수**하는 편이 결정 충돌을 피함 |

> **주의: 현재 dev 데이터는 진행중 0건 / 예정 14건입니다.** "진행중" 필터를 기본값으로 두면
> 빈 화면이 기본이 됩니다. 기본값은 반드시 "전체"여야 합니다.

## 6. 설계 3 — 관광지 목록 (지역 선택 단일 모드)

3.1의 결정에 따라 **내 위치 기준 조회·반경 필터·거리 정렬은 이번 범위에서 제외**합니다. 관광지는
4,020건(전국 확장 시 6~8만 건)이라 클라이언트 계산이 불가능하고, 서버 계산은 좌표 전송을
요구하기 때문입니다. 초안의 6.2절(`POST` 본문 좌표 전송)과 6.5절(SQL haversine 정렬·페이징)은
삭제했습니다.

### 6.1 단일 모드 구성

| 항목 | 내용 |
| --- | --- |
| 지역 | 시군구 선택 (기본값 전체) |
| 카테고리 | 기존 `contentTypeId` 유지 (전체/관광지/문화시설/액티비티/맛집) |
| 검색 | 기존 `keyword` 유지 (300ms 디바운스) |
| 정렬 | `TITLE_ASC`(기본, 현재 동작과 동일), `RECENTLY_ADDED` |
| 페이지 | 20개 단위 무한스크롤 |

축제(5장)와 파라미터 구조가 같아져서 두 세그먼트의 필터 UI를 같은 방식으로 만들 수 있습니다.

```
GET /api/spots?page=&size=&keyword=&contentTypeId=&sigunguCode=&sort=
```

### 6.2 정렬

> **이 절은 14장에서 개정됐습니다.** 좋아요·후기 정렬이 추가됐습니다. 아래는 개정 전 기록입니다.

거리 기준 정렬이 빠지므로 남는 선택지는 두 개입니다.

| 값 | 정렬 키 | 비고 |
| --- | --- | --- |
| `TITLE_ASC` (기본) | `title ASC, id ASC` | **현재 동작과 동일** — 정렬을 안 고른 사용자의 화면이 바뀌지 않음 |
| `RECENTLY_ADDED` | `created_at DESC, id DESC` | 새로 동기화된 장소 발견 |

`ExploreListPage.tsx:181-184`의 장식용 `<span>` **"가까운 순"은 제거**합니다. 거리 정렬을 제공하지
않으므로 그 라벨은 사실과 다릅니다(원래도 동작하지 않는 표시용이었습니다). 필터 행의 "현재 위치"
칩과 "거리" 칩도 이번 범위에서 제공하지 않으므로 함께 제거하고, "지역"과 "정렬"만 남깁니다.

### 6.3 무한스크롤

축제(5.3)와 완전히 동일한 패턴·동일한 주의사항을 적용합니다. 백엔드 변경이 필요 없습니다
(`hasNext`가 이미 응답에 있습니다).

### 6.4 제외한 요구사항과 그 이유

| 원안 요구 | 상태 | 이유 |
| --- | --- | --- |
| "현재위치는 내위치 기준" | 제외 | 4,020건은 서버 계산 필요 → 좌표 전송 필요 → 3.1 결정에 위배 |
| "거리도 ~10키로 50키로 100키로 단위 선택가능" | 제외 | 중심 좌표가 서버에 없으면 반경 계산 불가 |
| "정렬은 가까운순, 먼순" | 제외 | 중심점이 없어 거리 정렬이 원리적으로 불가능 |
| "각 도단위 지역별로" | **시군구로 변경** | 데이터가 강원 1개 도뿐(2.1). 전국 확장은 10장 |
| "가까운순 버튼은 빼기" | **반영** | 6.2 |

> **참고: 축제 기준 주변 관광지는 이미 있고 그대로 유지됩니다.**
> `GET /api/festivals/{id}/nearby-spots`는 중심이 **축제 좌표**(서버에 이미 저장된 값)라서 좌표
> 전송이 없고, 3.1 결정과 무관합니다. `HomePage`의 "축제와 함께 둘러보기"와 `SoloCoursePage`가
> 계속 사용합니다. 즉 "내 주변" 대신 **"체크인/선택한 축제 주변"**이라는 기존 접근
> (`docs/22_SOLO_COURSE_NEARBY_SPOT_DESIGN.md` 3장)이 여전히 유효한 대안입니다.
>
> 2.3의 좌표 이상치 4건은 이 API에서 반경(최대 20km) 필터에 걸려 자연히 배제되므로 추가 조치가
> 필요하지 않습니다. 다만 sync 단계 좌표 검증은 데이터 위생 차원에서 여전히 권장하며 10.2에
> 남겨 뒀습니다.

## 7. DB migration

`V23__add_tour_place_region_codes.sql` (신규, 기존 V1~V22는 수정하지 않음)

1. `tour_places`에 `area_code varchar`, `sigungu_code varchar` 컬럼 추가(nullable).
2. `raw_data ->> 'lDongRegnCd'`, `raw_data ->> 'lDongSignguCd'`로 기존 4,020건 백필.
   **TourAPI 재호출이 필요 없습니다**(2.2).
3. 지역 필터 조회용 인덱스 `tour_places (status, sigungu_code)` 추가.
   - `festivals`에는 **이미 `idx_festivals_area ON festivals (area_code, sigungu_code)` 복합
     인덱스가 있습니다**(`V2__create_core_tables.sql:103`). 시군구 필터에 그대로 쓸 수 있어
     축제 쪽은 **인덱스를 추가하지 않습니다.**
   - 단 Tier A 실측 교훈대로 **34행 테이블에는 어떤 인덱스도 사용되지 않습니다**
     (V22의 축제 인덱스 3개는 `idx_scan = 0`). 관광지(4,020건)만 지금 추가할 가치가 있습니다.
4. `TourPlaceSyncMapper`/`TourPlaceSyncData`가 앞으로 두 컬럼을 채우도록 수정(신규 동기화분 반영).
   - `SearchTourPlaceItem`은 이미 `lDongRegnCd`→`regionCode`, `lDongSignguCd`→`sigunguCode`로
     노출하고 있으나, `TourPlaceSyncData`에 해당 컴포넌트가 없어 버려지고 있습니다
     (`TourPlaceSyncMapper.java:53-64`). 축제 쪽은 이미 저장합니다(`Festival.java:115-116`).

참고로 `map_x`/`map_y`는 두 테이블 모두 `NUMERIC(13,10)`입니다 — 6.5절의 타입 캐스팅 함정과
직접 관련됩니다.

## 8. API 계약 변경 요약

| 엔드포인트 | 변경 |
| --- | --- |
| `GET /api/festivals` | `sigunguCode`, `sort`, `schedule`, `matchableOnly` 파라미터 추가. 응답 `items[]`에 `mapX`, `mapY` 추가 (`schedule`은 14장에서 `startDate`/`endDate`/`progress`로 교체됨) |
| `GET /api/festivals/regions` | **신규** — 데이터에 존재하는 시군구 목록 + 건수 |
| `GET /api/spots` | `sigunguCode`, `sort` 추가. 거리 정렬·반경은 지원하지 않음(3.1) |
| `GET /api/spots/regions` | **신규** — 위와 동일 |
| `GET /api/festivals/{id}/nearby-spots` | 변경 없음(홈 "축제와 함께 둘러보기", 솔로 코스가 계속 사용) |
| `GET /api/spots/{id}/nearby-festivals` | 변경 없음 |

**기존 응답 필드를 제거하거나 이름을 바꾸지 않습니다. 기존 파라미터의 기본값도 바꾸지 않습니다.**
추가만 하므로 파라미터를 안 보내는 클라이언트는 현재와 동일한 결과를 받습니다.

좌표를 서버로 보내는 신규 엔드포인트는 **없습니다**(3.1).

## 9. 사용자 원안 대비 확정된 변경

| 원안 | 확정 | 이유 |
| --- | --- | --- |
| 관광지를 "각 도단위 지역별로" | **시군구 단위**로 시작 | 데이터가 강원 1개 도뿐이라 도 단위 선택지가 1개(2.1). 전국 확장은 10장 |
| 축제 "지역" 필터 | **시군구 단위** | 동일 |
| "내 위치 기반" (홈) | **채택 — 클라이언트 계산** | 좌표를 서버로 보내지 않아 3.1 제약을 만족. 축제가 15건이라 가능(4.1) |
| "내 위치 기반" (관광지) | **제외** | 4,020건은 서버 계산이 불가피 → 좌표 전송 필요 → 3.1 제약 위배 |
| "거리 10/50/100km 단위 선택" | **제외** | 중심 좌표가 서버에 없어 반경 계산 불가. `radiusMeters` 상한(20km)도 그대로 유지 |
| "정렬은 가까운순, 먼순" | **제외** | 중심점이 없어 거리 정렬이 원리적으로 불가능 |
| "가까운순 버튼은 빼기" | **채택** | 장식용 `<span>` 제거. "현재 위치"·"거리" 칩도 함께 제거(6.2) |
| 축제 정렬 기준 미지정 | **시작일순/종료임박순/최근등록순** | 기본값은 현재 동작 유지(5.2) |
| 추가 필터 | **일정 + 매칭 가능 여부 채택.** 카테고리·요금은 데이터가 없어 불가 | 5.4 |
| 20개씩 무한스크롤 | **채택 (축제·관광지 둘 다)** | 백엔드 변경 없이 가능(5.3) |
| 앱 위치 동의 모달 최초 1회 | **제외** | 좌표 전송이 없어 법적 동의 대상이 아니고, 브라우저 권한창이 이미 그 역할(4.3) |

관광지에서 "내 주변"을 원하는 사용자 요구 자체는 **`GET /api/festivals/{id}/nearby-spots`**로
이미 충족됩니다(중심이 축제 좌표이므로 좌표 전송 없음). 홈의 "축제와 함께 둘러보기"와
`SoloCoursePage`가 그 경로입니다 — 6.4절 참고.

## 10. 선행 과제 — 전국 동기화 확장

도 단위 지역 필터를 제대로 하려면 동기화 범위를 강원(`51`)에서 전국으로 넓혀야 합니다. 규모를
가늠해 두면 좋겠습니다.

- 강원 1개 도 = 관광지 4,020건. 17개 시도로 단순 환산하면 **약 6~8만 건**.
- TourAPI `areaBasedList2`는 페이지당 100건 → 전체 동기화 1회에 **600~800회 호출**.
- `docs/13_FESTIVAL_TOURSPOT_API_DESIGN.md` 7장에 **테스트 키 일일 1,000회 한도**가 기록돼 있습니다.
  전국 동기화 1회가 하루 한도를 거의 소진합니다.
- 따라서 전국 확장은 **운영 키 한도 상향** 또는 **시도별 분할 동기화**(하루 2~3개 도씩 순회)가
  선행돼야 합니다. 데이터도 5MB → 약 85MB로 늘어납니다.

### 10.1 전국 확장의 진짜 blocker — 관광지 INACTIVE 스윕에 지역 개념이 없다

호출 한도보다 심각한 구조적 문제가 있습니다.

- **축제**의 INACTIVE 스윕은 지역 범위가 있습니다. `FestivalRepository.markActiveMissingInScopeInactive`
  /`markAllActiveInScopeInactive`가 `and festival.areaCode = :regionCode`로 필터하고
  (`FestivalRepository.java:48, 68`), `FestivalSyncScope`가 `regionCode`를 들고 다닙니다.
- **관광지**의 INACTIVE 스윕은 **`contentTypeId`만 봅니다.** `TourPlaceRepository.markActiveMissingInScopeInactive`
  (`TourPlaceRepository.java:84-105`)에 지역 조건이 없고, `TourPlaceSyncScope`
  (`TourPlaceSyncScope.java:7-11`)에 `regionCode` 필드 자체가 없습니다.

> **결과: 시도별로 분할 동기화를 돌리면, 강원을 동기화하는 순간 "이번 실행에서 관측되지 않은"
> 다른 모든 도의 관광지가 전부 `INACTIVE`로 뒤집힙니다.** 다음 도를 돌리면 강원이 다시 죽습니다.
> 즉 현재 코드로는 다중 지역 동기화가 원리적으로 불가능합니다.

전국 확장을 하려면 `TourPlaceSyncScope`에 `regionCode`를 추가하고 스윕 쿼리를
`(contentTypeId, areaCode)` 범위로 좁히는 선행 수정이 필수입니다. 이 수정은 7장의 `area_code`
컬럼 추가에 의존합니다(현재는 스윕을 지역으로 좁힐 컬럼조차 없습니다).

`docs/09_TEST_AND_QUALITY_STRATEGY.md` 기준으로 이건 데이터 전체를 날릴 수 있는 변경이므로,
전국 확장 작업 시 **Testcontainers 통합 테스트를 먼저** 작성해야 합니다. 현재
`TourPlaceSyncScope`에는 지역 차원이 없어 관련 테스트도 존재하지 않습니다.

**이번 범위에서는 전국 확장을 하지 않습니다.** 시군구 단위로 먼저 만들고, 지역 필터 구조를
`(area_code, sigungu_code)` 2단으로 설계해 두면 전국 확장 시 UI만 2단 드롭다운으로 바꾸면 됩니다.

### 10.2 그 외 이번 범위 제외

- 전국 동기화 확장(위) + 관광지 INACTIVE 스윕 지역 범위화(10.1)
- 좌표 이상치 sync 단계 검증(`TourPlaceSyncMapper` 좌표 유효성) — 데이터 위생 과제로 분리
- `일정` 필터의 "직접 날짜 지정" UI — 우선 프리셋(진행중/이번 주말/이번 달)만
- 관리자 지역 관리 화면
- `mock/tourSpots.ts`, `mock/checkIns.ts` 정리 — `MyPage.tsx:6-7`이 아직 import 중이며 전주
  mock 데이터가 화면에 남아 있음. 별도 정리 과제
- `api/home.ts` 미사용 mock 파일 제거

## 11. 구현 순서

1. `V23` migration(`tour_places`에 지역 코드 추가 + `raw_data` 백필 + 인덱스)
2. `TourPlace` 엔티티 / `TourPlaceSyncData` / `TourPlaceSyncMapper`에 지역 코드 반영
3. 정렬·필터 enum(`FestivalListSort`, `TourPlaceListSort`, `FestivalScheduleFilter`)
4. `GET /api/festivals` 확장(`sigunguCode`, `sort`, `schedule`, `matchableOnly`) + 응답에 `mapX`/`mapY`
5. `GET /api/spots` 확장(`sigunguCode`, `sort`)
6. `GET /api/festivals/regions`, `GET /api/spots/regions`
7. Backend 테스트(단위 + Testcontainers + Controller)
8. 프론트 API 클라이언트·타입 갱신
9. `utils/geo.ts` + 홈 화면 최근접 축제(폴백 포함)
10. `ExploreListPage` 지역·정렬·일정 필터 UI + 무한스크롤, "가까운 순"·"현재 위치"·"거리" 칩 제거
11. 프론트 테스트(순수 함수 분리 방식)
12. `docs/10_PROGRESS_LOG.md` 기록

**위치정보 정책 갱신에 의존하는 단계는 없습니다.** 3.1 결정으로 해당 작업 자체가 범위에서
빠졌기 때문입니다.

## 12. 테스트 계획

`docs/09_TEST_AND_QUALITY_STRATEGY.md`에 따라 장애 영향이 큰 쪽을 우선합니다.

Backend

- 정렬 tie-breaker(`id`)로 페이지 경계 중복/누락이 없는지 — page 0/1 결과 합집합에 중복이 없음
  (무한스크롤의 정확성이 여기 달려 있음)
- `sigunguCode` 필터가 해당 지역만 반환하는지, 미지정 시 전체를 반환하는지
- `schedule` 필터(진행중/이번 주말/이번 달) 경계 날짜 처리 — KST 기준
- `matchableOnly`가 ACTIVE 만남 장소가 있는 축제만 반환하는지
- 지역 목록 API가 **데이터에 없는 시군구를 반환하지 않는지**
- `V23` 백필이 `raw_data`의 `lDong*`을 정확히 옮기는지 (Testcontainers)
- 신규 파라미터를 아무것도 안 보냈을 때 **기존 응답과 동일한지**(회귀 방지)
- 기존 `FestivalControllerTest`(9 tests)·`TourPlaceControllerTest`(5 tests)에 신규 파라미터
  검증 케이스 추가
- 참고: `TourPlaceController`의 `contentTypeId`는 현재 웹 계층에서 `12/14/28/39` 검증을 하지
  않습니다. `sigunguCode`/`sort`를 추가할 때 같은 누락을 반복하지 않도록 `sort`는 enum 바인딩을
  씁니다(잘못된 값이면 400).

Frontend

- 프론트 haversine이 서버 `GeoDistanceCalculator`와 동일한 값을 내는지 (고정 좌표쌍 대조 —
  같은 계산이 두 곳에 존재하므로 **반드시 필요**)
- 최근접 축제 선택 분기: GPS 성공 / 권한 거부 / 타임아웃 / 좌표 있는 축제 없음 → 4개 폴백
- 무한스크롤 누적 로직: 필터 변경 시 리셋, 로딩 중 중복 요청 차단, `hasNext=false`에서 정지

> **테스트 인프라 제약**: 프론트는 vitest **node 환경**이고 `@testing-library/react`·`jsdom`이
> 없습니다(선례: pure factory + `vi.stubGlobal`). `IntersectionObserver`와 geolocation을 쓰는 로직은
> **컴포넌트에서 분리한 순수 함수/팩토리로 만들어** 기존 방식대로 테스트합니다
> (`useAdminMembers.ts`, `useMatchRoom.ts`가 이미 이 패턴). jsdom 도입은 하지 않습니다.

## 13. 확정된 결정 사항

| # | 결정 |
| --- | --- |
| 1 | **`docs/06_SECURITY_POLICY.md`를 갱신하지 않는다. GPS 좌표를 서버로 보내지 않는다.** 관광지는 지역 선택 모드만 제공하고 거리 필터·거리 정렬은 넣지 않는다 |
| 2 | 지역 단위는 **시군구**로 시작한다. 전국 확장(도 단위)은 별도 과제(10장) |
| 3 | 축제 정렬은 **시작일순(기본)/종료임박순/최근등록순** 3종 |
| 4 | 추가 필터로 **일정**과 **매칭 가능한 축제만** 2개를 채택한다 |
| 5 | 홈 화면 **앱 자체 동의 모달은 두지 않는다**(브라우저 권한창만 사용, `localStorage` 미사용) |

미결 사항: 브랜치명. `feature/wbs-10-a-list-filter-sort-pagination`을 제안하며, 현재는
`feature/wbs-10-a-festival-course` 브랜치에 Tier A 미커밋 변경이 남아 있어 브랜치 전환은
사용자 확인 후 진행합니다.

## 14. 개정 — 기간 직접 선택, 진행 상태 필터, 좋아요·후기 정렬

사용자 요청으로 5.2·5.4·6.2를 개정했습니다. **5.4에서 "일정 필터로 흡수"하기로 했던 진행
상태를 독립 필터로 승격하고, 프리셋 일정 필터를 기간 직접 선택으로 바꿉니다.**

### 14.1 일정 프리셋 → 기간 직접 선택

`schedule`(`ALL`/`ONGOING`/`THIS_WEEKEND`/`THIS_MONTH`)과 `FestivalScheduleFilter` enum을
**삭제**하고 `startDate`/`endDate`(각각 선택, `yyyy-MM-dd`)로 교체했습니다.

- 겹침 판정 규칙은 그대로입니다 — `event_start_date <= endDate AND event_end_date >= startDate`.
  날짜가 `null`인 축제(동기화 데이터 불완전)는 열린 구간으로 취급해 배제하지 않습니다.
- 한쪽만 넘겨도 됩니다. 종료일만 넘기면 "그 날짜 전에 시작하는 축제"가 됩니다.
- 화면은 브라우저 기본 `<input type="date">` 2개(`DateRangeFilter`)를 씁니다. `FilterSelect`가
  네이티브 `<select>`를 쓰기로 한 것과 같은 이유입니다 — 달력을 직접 그리면 키보드 조작·연도
  이동·모바일 네이티브 피커를 전부 다시 만들어야 합니다.

### 14.2 진행 상태 필터 `progress` — 가시성을 넓히는 유일한 스위치

`progress`(`ALL`/`UPCOMING`/`ONGOING`/`ENDED`)를 추가했습니다. 판정 규칙은 frontend
`resolveDisplayStatus`와 일치시킵니다(`ENDED` 상태는 날짜와 무관하게 마감).

**여기가 이 개정의 핵심 제약입니다.** 기존 목록 쿼리는 `status = ACTIVE`이면서 종료일이 지나지
않은 축제만 반환했습니다. 즉 **"진행 마감" 축제는 원래 목록에 나오지 않았습니다.**

- `progress`를 넘기면 `status IN ('ACTIVE', 'ENDED')`로 넓히고 종료일 컷을 풉니다.
- **`progress`를 넘기지 않으면 기존 동작 그대로입니다.** 같은 `GET /api/festivals`를 홈 화면
  (`HomePage`)과 관광지 상세가 함께 쓰고 있어, 기본값을 바꾸면 그쪽 화면에 지난 축제가
  섞입니다. 탐색 화면만 항상 `progress`를 보냅니다(기본 선택 `ALL`).
- 5.4의 "서버 status 필터는 doc 13 3.2 결정을 뒤집는다"는 우려는 이 방식으로 비껴갑니다 —
  서버는 여전히 화면 표시용 status를 **응답에 담지 않고**, 같은 규칙으로 **걸러내기만** 합니다.

### 14.3 정렬 — 날짜 정렬 삭제, 집계 정렬 추가

| 대상 | 정렬 값 |
| --- | --- |
| 축제 | `RECENTLY_ADDED`(기본) / `BOOKMARK_COUNT_DESC` / `COMMENT_COUNT_DESC` |
| 관광지 | `TITLE_ASC`(기본) / `RECENTLY_ADDED` / `BOOKMARK_COUNT_DESC` / `COMMENT_COUNT_DESC` |

- 축제의 `START_DATE_ASC`·`END_DATE_ASC`는 삭제했습니다. 진행 단계는 14.2의 필터로 고르고,
  기간은 14.1로 직접 선택합니다.
- **감수한 비용: 축제 목록의 기본 정렬이 바뀝니다.** `START_DATE_ASC`가 없어졌으므로 정렬을
  넘기지 않는 `HomePage`도 `RECENTLY_ADDED`를 받습니다. 홈 화면의 "다가오는 축제" 목록과
  히어로 폴백(`pickFallbackFestival`)이 둘 다 배열 순서에 의존하므로, 홈이 받은 목록을
  브라우저에서 시작일 순으로 다시 정렬합니다(`sortByStartDate`). 서버 정렬을 되살리지 않은
  이유는 "날짜 정렬을 다 지운다"가 이번 요청이기 때문입니다.

### 14.4 목록 응답에 좋아요·후기 수 추가

`FestivalListItemResponse`와 `TourPlaceListItemResponse`에 `bookmarkCount`, `commentCount`를
추가하고 목록 카드 오른쪽에 표시합니다(`EngagementCounts`).

- "좋아요 수"는 **찜(`content_bookmarks`) 수**입니다. 콘텐츠 단위 반응이 찜 하나뿐이고,
  좋아요는 댓글 단위(`content_comment_likes`)라 목록 지표가 되지 못합니다.
- "후기 수"는 `status = 'VISIBLE'` 댓글만 셉니다. 상세 화면 `commentCount`와 같은 기준입니다.
- **목록 카드의 하트는 눌러서 바로 찜할 수 있습니다**(14.8). 탐색 목록(축제·관광지)과 관광지
  상세의 "주변에서 열리는 축제"가 대상이고, 찜 목록은 표시 전용으로 둡니다.
- 내 찜 목록(`GET /api/members/me/bookmarks`)도 같은 카드를 재사용하므로 같은 집계를 함께
  내려줍니다. 안 그러면 찜 목록에서만 항상 0으로 보입니다.

### 14.5 목록 쿼리가 native query로 바뀐 이유

축제·관광지 목록 쿼리를 JPQL 생성자 표현식에서 **native query + 인터페이스 프로젝션**으로
교체했습니다.

- 정렬 키가 엔티티 속성이 아니라 집계 값이라 `Pageable`의 `Sort`로 표현할 수 없습니다. 정렬을
  응용 계층에서 하면 페이지 경계가 어긋납니다(무한스크롤이 같은 항목을 중복/누락).
- 집계를 파생 테이블 안에서 한 번 계산하고 바깥에서 정렬합니다. PostgreSQL은 `ORDER BY` 식
  안의 이름을 출력 별칭이 아니라 입력 컬럼으로 해석하므로, 감싸지 않으면 같은 서브쿼리를
  `ORDER BY`에 한 번 더 써야 합니다.
- 정렬 분기는 `CASE WHEN :sort = '...' THEN ... END`입니다. 마지막 tie-breaker는 여전히 `id`라
  무한스크롤 페이지 경계가 안정적입니다.
- 프로젝션 인터페이스 방식은 `FestivalCheckinRepository.CheckinHistoryProjection` 선례를
  따릅니다.

### 14.6 V35 — 집계용 인덱스

`content_bookmarks (festival_id)`와 `content_bookmarks (tour_place_id)` partial index를
추가했습니다(`V35__add_content_engagement_count_indexes.sql`).

- V26의 `uq_content_bookmarks_member_festival`/`_place`는 `member_id`가 선행 컬럼이라 대상별
  집계에 쓰이지 않습니다. 관광지가 4,000건대라 없으면 목록 한 페이지마다 전체 스캔이 반복됩니다.
- 댓글은 V26의 `idx_content_comments_festival_visible`/`_place_visible`이 이미
  `(대상, id DESC) WHERE status = 'VISIBLE'` partial index라 집계에 그대로 쓰여 추가하지
  않았습니다.

### 14.7 남은 위험

동기화(`FestivalSyncScheduler`)가 다가오는 축제 위주라 **dev DB에 `ENDED` 축제가 거의 없을 수
있습니다.** 그러면 "진행 마감" 필터는 동작이 맞아도 결과가 비어 보입니다. 기능 결함이 아니라
데이터 범위 문제이며, 확인하려면 dev DB 조회가 필요합니다.

## 15. 개정 — 목록에서 바로 찜하기

14.4에서 목록 카드의 하트를 표시 전용으로 뒀으나, 사용자 요청으로 **목록에서 바로 찜을 토글**할
수 있게 고쳤습니다.

### 15.1 적용 범위

| 화면 | 찜 버튼 |
| --- | --- |
| 탐색 목록 축제 세그먼트 | 누를 수 있음 |
| 탐색 목록 관광지 세그먼트 | 누를 수 있음 |
| 관광지 상세 "주변에서 열리는 축제" | 누를 수 있음 |
| 홈 "축제와 함께 둘러보기" | 누를 수 있음 |
| 마이페이지 찜 목록 | **표시 전용** |

찜 목록을 제외한 이유: 거기서 해제하면 그 항목이 자기 목록에서 사라져야 하는지(즉시 제거 /
다음 진입까지 유지) 별도 결정이 필요합니다. 사용자 확인 결과 이번 범위에서 제외했습니다.

### 15.2 목록 조회가 optional-login이 됐다

`GET /api/festivals`, `GET /api/spots`, `GET /api/spots/{id}/nearby-festivals`,
`GET /api/festivals/{id}/nearby-spots`가 `OptionalMemberResolver`를 쓰는 공개 조회로 바뀌었습니다.

- 응답 항목에 `bookmarkedByMe`가 추가됐습니다. 하트를 채울지 판단하려면 개수만으로는 부족합니다.
- **비로그인·만료 토큰도 그대로 `200`이고 `bookmarkedByMe`가 전부 `false`입니다.** 여기서
  `401`을 내면 frontend `apiClient`의 전역 리다이렉트 때문에 탐색 화면을 열기만 해도 로그인으로
  튕깁니다(docs/27 2.1).
- 목록 응답(페이지 레벨)에 `viewerLoggedIn`을 추가했습니다. 목록 화면은 상세 화면과 달리
  `engagement`를 부르지 않아 로그인 여부를 알 방법이 없는데, 비로그인이 하트를 누르면 요청 없이
  `/login`으로 보내야 하기 때문입니다. 주변 축제는 관광지 상세가 이미 `engagement`로 로그인
  여부를 알고 있어 배열 응답 그대로 뒀습니다.

### 15.3 "내가 찜했는지"는 목록 쿼리에 넣지 않았다

찜 수·댓글 수와 달리 `bookmarkedByMe`는 **native query 밖에서** 조회합니다
(`ContentEngagementSummaryReader.bookmarkedIds`).

- 정렬 키가 아니므로 쿼리 안에 있을 이유가 없고, 한 페이지 id들로 `IN` 조회 1건이면 끝납니다
  (`ContentCommentLikeRepository.findLikedCommentIds`와 같은 방식, N+1 없음).
- 넣었다면 native query에 회원 id null 바인딩을 위한 `CAST` 분기가 하나 더 늘어납니다.

반경 검색 두 곳(주변 축제, 축제와 함께 둘러보기)은 정렬 기준이 거리라 찜 수·댓글 수도 쿼리로
집계할 수 없어, 같은 reader의 `summarize`가 **반경·정렬·개수 제한을 모두 끝낸 뒤** 최종 목록에
대해서만 집계합니다. 먼저 집계하면 bounding box 후보 중 버려질 행까지 세게 됩니다.

### 15.4 카드 구조 — 하트는 링크 바깥에 있어야 한다

`FestivalListItem`과 `ExploreSpotItem`은 **카드 전체가 `<Link>`였습니다.** 그 안에 버튼을 넣으면
잘못된 HTML인 데다 하트를 눌러도 상세로 이동합니다. 그래서 카드를 `<div>`로 바꾸고 본문만
`<Link>`로 감쌌습니다. 버튼 쪽에서도 `preventDefault`/`stopPropagation`으로 한 번 더 막습니다.

`EngagementCounts`는 `onToggleBookmark`를 받았을 때만 버튼으로 렌더합니다. 핸들러가 없으면
기존 표시 전용 그대로라 찜 목록은 영향을 받지 않습니다.

### 15.5 토글 상태 — 낙관적 갱신 없음, 목록 재조회 없음

`useListBookmarks`(`createListBookmarkSession`)가 사용자가 이 화면에서 바꾼 것만 overlay map으로
들고 있습니다.

- **낙관적 갱신을 하지 않습니다.** 서버가 돌려준 `bookmarked`로만 상태를 바꿉니다. 상세 화면의
  찜 토글과 같은 규칙입니다.
- 표시 개수는 서버가 준 수에 내 토글 결과만 ±1 합니다. 목록을 다시 부르지 않으므로 최신 총합은
  아니지만, 내가 누른 하트가 숫자에 반영되지 않으면 눌리지 않은 것처럼 보입니다.
- 요청 중인 대상은 `pendingKey`로 막아 연타가 두 번 반영되지 않게 합니다.
- **실패하면 상태를 바꾸지 않습니다.** 목록 화면에는 오류를 띄울 자리가 없고, 하트가 그대로
  남는 편이 "눌렸다가 되돌아간" 것보다 덜 혼란스럽습니다.

### 15.6 세션은 반드시 effect 안에서 만든다 (실제로 터진 버그)

`useListBookmarks`가 세션을 **렌더 중에** 만들고 `useEffect` cleanup에서 `stop()`했습니다.
React StrictMode는 개발 모드에서 mount → unmount → mount로 effect를 두 번 돌리므로, 첫 cleanup이
세션을 영구 정지시키고 두 번째 mount는 그 정지된 세션을 그대로 씁니다.

증상은 **"하트를 눌러도 화면이 안 바뀌고 새로고침하면 반영됨"**이었습니다. 요청과 서버 저장은
정상이고 `publish`만 화면에 닿지 않았기 때문입니다.

- 세션 생성을 `useEffect` 안으로 옮겼습니다(`useInfiniteList`·`useContentBookmark`와 같은 구조).
- 재생성 시 이전 `overrides`를 이어받습니다. 안 그러면 두 번째 세션이 빈 상태로 시작해 다음
  토글이 이전 결과를 지웁니다. `pendingKey`는 이어받지 않습니다 — 그 요청은 이전 세션과 함께
  버려졌고, 이어받으면 버튼이 영원히 비활성으로 남습니다.

**이 저장소에서 closure 세션을 쓰는 hook은 전부 effect 안에서 만들어야 합니다.**
