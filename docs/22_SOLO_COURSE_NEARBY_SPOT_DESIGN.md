# 솔로 코스 → 체크인 기반 주변 관광지 추천 설계

이 문서는 `/solo-course`(`SoloCoursePage`) 화면을 하드코딩 mock에서, 체크인한 축제 위치를 기준으로
실제 주변 관광지를 추천하는 화면으로 바꾸기 위한 분석·설계 문서입니다.

> 검토 결과 **카테고리 필터는 제외**하고 나머지 설계대로 구현했습니다. 결정 결과는 9장, 실제 구현
> 결과는 10장을 참고합니다.

## 1. 요청 배경

`/solo-course`는 "내가 체크인한 위치를 기반으로 축제뿐 아니라 주변 관광지도 추천"하는 화면이어야
하는데, 현재는 임의의 mock 데이터를 보여주고 있습니다. 실제 동선(몇 시부터 몇 시까지 어디를 들러서
얼마나 머무는지)까지 짜는 "코스" 기능은 지금 시점에 만들기엔 이르므로, 이번 범위는 **위치 기반으로
갈 만한 곳을 거리순으로 추천하는 목록** 정도로 한정합니다.

## 2. 현재 상태 (as-is)

`SoloCoursePage`는 강원도 축제·체크인과 완전히 무관한 하드코딩 mock입니다
(`frontend/src/data/mock/soloCourses.ts` — "전주 한옥마을" 데이터). "반나절/하루" 토글과
순서·체류시간이 있는 타임라인 UI를 갖고 있지만 전부 고정값입니다.

진입 경로 2곳 모두 `festivalId`를 넘기지 않습니다.

- `HomePage`의 "혼자 즐기는 추천 코스" 배너 → `<CtaBanner to="/solo-course" .../>` (state 없음)
- `FestivalDetailPage`의 "솔로 코스 보기" 버튼 → `navigate('/solo-course')` (state 없음)

그래서 `SoloCoursePage`는 애초에 "어느 축제 기준으로 추천할지" 알 방법이 없는 상태입니다.

## 3. 핵심 전제 — "내 위치"의 실체

이 프로젝트는 원본 GPS 좌표를 저장하지 않습니다(`festival_checkins`도 `distanceMeters`만 저장하고
좌표는 버립니다). 그래서 "내 위치 기반 추천"은 사용자의 실시간 좌표가 아니라
**"현재 체크인한 축제의 좌표"를 중심으로 한 반경 검색**을 의미해야 합니다.

그리고 이 기능은 **이미 백엔드에 구현되어 있습니다** (`docs/13_FESTIVAL_TOURSPOT_API_DESIGN.md` 3.4절).

```
GET /api/festivals/{id}/nearby-spots?radiusMeters(기본 5000, 100~20000)&limit(기본 10, 1~50)
```

`FestivalQueryService.getNearbyTourPlaces`가 haversine(`GeoDistanceCalculator`)으로 축제 좌표 ↔
`tour_places`(관광지/문화시설/액티비티/맛집 4종, `TourPlaceStatus.ACTIVE`) 거리를 계산해 반경 내를
거리순으로 반환합니다. `HomePage`가 이미 이 API로 "축제와 함께 둘러보기" 섹션을 채우고 있습니다.

**즉 이번 작업은 새 API가 필요 없고, 이미 있는 API를 `SoloCoursePage`에 배선만 하면 되는 작업입니다**
(카테고리 필터를 넣기로 하는 경우는 예외 — 8장 참고).

## 4. 재사용 가능한 기존 자산

| 용도 | 이미 있는 것 |
| --- | --- |
| API 호출 | `festivalsApi.getNearbyTourPlaces(festivalId, radiusMeters, limit)` |
| 응답 → 화면 타입 매핑 | `mapNearbyTourPlaceToTourSpot` (`utils/tourSpot.ts`) |
| 거리/도보시간 표시 | `formatDistanceLabel`, `formatWalkMinutesLabel` (`utils/tourSpot.ts`) |
| 카드 UI | `FestivalNearbyPlaceItem` — `HomePage`가 이미 동일 용도로 사용 중, 클릭 시 `/spots/:id`(`TourSpotDetailPage`)로 이동 |
| "지금 체크인한 축제" 조회 | `useCurrentCheckin()` — `GET /api/festivals/checkin/me` 기반 훅 |

## 5. 축제 기준점 결정 우선순위

`SoloCoursePage`가 "어느 축제 기준으로 추천할지" 정하는 순서입니다.

1. `location.state.festivalId` — `FestivalDetailPage`에서 들어왔으면 지금 보고 있는 그 축제 기준
   (체크인 여부와 무관하게 바로 추천 가능)
2. 없으면 `useCurrentCheckin()`의 현재 체크인된 축제
3. 둘 다 없으면 → "체크인 후 이용할 수 있어요" 안내 + 체크인하기 버튼
   (`MatchingConditionPage`의 IdleForm과 동일한 패턴 재사용)

~~일관성을 위해 `HomePage`의 "혼자 즐기는 추천 코스" 배너도 `hotFestival.id`를 state로 실어 보내도록
같이 고쳐야 합니다.~~

> **[정정 — `[10-A 후속 11]`]** 이 문단은 같은 문서 9장의 결정("체크인이 전혀 없는 상태로 진입 시
> 대표 축제로 대체하지 않는다")과 정면으로 충돌했습니다. 홈 배너가 `hotFestival.id`를 넘기면 1순위
> 조건이 항상 충족돼 **체크인 안내 화면에 도달할 수 없습니다.** 9장 결정을 살리기로 하고 홈 배너에서
> `state`를 제거했습니다. 두 진입 경로의 동작이 다른 것은 의도된 것입니다 —
> `FestivalDetailPage`는 사용자가 특정 축제를 보고 있는 화면이라 그 축제를 기준으로 삼아도 되지만,
> 홈 배너는 "내 주변"을 뜻하므로 체크인이 기준이어야 합니다.

## 6. 화면 재설계

기존 "반나절/하루 코스" 토글과 순서·체류시간이 있는 타임라인은 걷어냅니다(실제 동선을 짜는 로직이
없으므로). 대신 다음으로 단순화합니다.

- 상단에 "○○축제 기준" 안내
- 관광지 카드 리스트(거리순, `FestivalNearbyPlaceItem` 그대로 재사용 → 클릭 시 기존
  `TourSpotDetailPage`로 이동)
- 반경 내 결과가 0건이면 빈 상태 안내 문구
- 체크인·기준 축제가 전혀 없으면 5장 3번의 안내 화면

화면·버튼 문구도 "코스"라는 표현 대신 "주변 관광지 추천"류로 바꿉니다(정확한 문구는 구현 시 확정).

## 7. 정리 대상 (구현 완료)

- `frontend/src/data/mock/soloCourses.ts` 삭제
- `frontend/src/types/index.ts`의 `SoloCourse`, `CourseStop` 타입 삭제
- ~~`HomePage`~~, `FestivalDetailPage`의 `/solo-course` 진입 지점에 `festivalId` state 추가
  (`HomePage` 쪽은 위 5장 정정에 따라 `[10-A 후속 11]`에서 되돌렸습니다)

## 8. 백엔드 변경 필요 여부

카테고리 필터를 제외하기로 결정해, **백엔드는 변경하지 않았습니다** — 기존 `nearby-spots` API를
그대로 재사용합니다.

## 9. 결정 결과

- **카테고리 필터** — 제외. 관광지/문화시설/액티비티/맛집 구분 없이 거리순 전체 목록만 보여준다.
- **반경(radiusMeters)** — 조절 UI 없이 API 기본값(5,000m)을 그대로 사용한다.
- **체크인이 전혀 없는 상태로 진입 시** — 대표 축제로 대체하지 않고, "체크인 후 이용할 수 있어요"
  안내와 체크인하러 가기 버튼만 보여준다.
- **목록 개수(`limit`)** — API 기본값(10) 그대로 사용한다.

## 10. 구현 결과

- `SoloCoursePage`를 재작성했다. `resolveSoloCourseFestival(locationState, checkinState)`가
  `location.state.festivalId` → 현재 체크인된 축제 → 없음(안내 화면) 순서로 기준 축제를 결정한다.
  `useCurrentCheckin()` 조회가 끝나기 전(`loading`)에는 축제가 없다고 오판하지 않도록 별도
  로딩 화면을 보여준다.
- 기준 축제가 정해지면 `festivalsApi.getDetail`(제목 표시용)과
  `festivalsApi.getNearbyTourPlaces`(목록)를 호출해 `FestivalNearbyPlaceItem` 카드로
  거리순 목록을 표시한다. 클릭하면 기존 `TourSpotDetailPage`(`/spots/:id`)로 이동한다.
- 반경 내 결과가 0건이거나 API 호출이 실패하면 각각 안내 문구를 표시한다.
- `HomePage`의 배너, `FestivalDetailPage`의 버튼 문구를 "코스"에서 "주변 관광지 추천"으로
  바꾸고 `festivalId`를 route state로 실어 보내도록 수정했다. `CtaBanner`에 이동 대상 화면에
  route state를 넘길 수 있는 `state` prop을 추가했다(재사용 컴포넌트라 다른 사용처는 영향 없음).
- Frontend 전체 Vitest 25 files/210 tests(신규 `resolveSoloCourseFestival` 5건 포함),
  `npx tsc --noEmit`, production/PWA build가 성공했다. 백엔드는 변경하지 않았다.
