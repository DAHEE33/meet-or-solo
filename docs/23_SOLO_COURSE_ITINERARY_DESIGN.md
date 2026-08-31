# 체크인 기반 솔로 코스(동선) 설계 — 1차

이 문서는 `docs/22_SOLO_COURSE_NEARBY_SPOT_DESIGN.md`로 구현한 "거리순 목록"을, 실제로 걸을 수
있는 순서가 있는 "코스"로 확장하기 위한 1차 분석·설계 문서입니다.

> 초안 수치(9장) 그대로 구현했습니다. 구현 결과는 10장을 참고합니다.

## 1. 배경과 범위

지금 `/solo-course`는 체크인한 축제 기준으로 가까운 관광지를 **순서 없이 거리순으로만** 보여줍니다
(docs/22 결과). 실제 "코스"라고 부르려면 최소한 "어디부터 어디로 이동하면 되는지" 순서가 있어야
합니다.

1차 범위는 **좌표만으로 계산 가능한 동선 + 휴리스틱 체류시간**으로 한정합니다. 실제 영업시간·휴무일·
인기도 반영, 출발 시각 기반 식사 슬롯 배치는 2차 이후로 미룹니다(7장 참고). 다만 순수 거리순으로만
고르면 같은 카테고리(특히 39 맛집/카페)가 연속으로 뽑히는 문제가 흔히 발생하므로, 가벼운 카테고리
연속 방지 규칙은 1차에 포함합니다(3.3절).

## 2. 데이터 제약 재확인

`tour_places`에 실제로 있는 필드는 좌표(`mapX`/`mapY`), `contentTypeId`(12 관광지/14 문화시설/28
액티비티/39 맛집만 동기화), 제목·주소·이미지뿐입니다. 영업시간·휴무일·평균 체류시간·인기도·실측
도보 경로는 없습니다. 거리 계산은 기존과 동일하게 `GeoDistanceCalculator`(haversine, 직선거리)를
그대로 씁니다.

**결론**: 정교한 코스는 못 만들지만, 좌표만으로 "그럴듯하게 걸을 수 있는 순서"는 만들 수 있습니다.
체류시간은 실측값이 없으므로 카테고리별 고정 추정치를 쓰고, 화면에는 "예상"임을 명시합니다(도보
시간을 이미 직선거리 기준 추정치로 표시하고 있는 것과 같은 방식 — `utils/tourSpot.ts`의
`formatWalkMinutesLabel` 주석 참고).

## 3. 알고리즘 — 축제를 시작점으로 한 greedy 최근접 이웃(Nearest Neighbor)

### 3.1 순서 결정

1. 축제 좌표를 "현재 위치"의 시작값으로 둔다.
2. 후보 pool: 축제 좌표 기준 반경 내 `TourPlaceStatus.ACTIVE` 관광지(기존
   `FestivalQueryService.getNearbyTourPlaces`가 쓰는 것과 같은 후보 집합).
3. "현재 위치에서 가장 가까운 미방문 후보"를 순서대로 하나씩 고른다(고전적 nearest-neighbor
   TSP 근사). 후보 수가 적어서(반경 내 최대 수십 개) 이 방식으로 충분히 실용적입니다.
4. 고른 후보를 다음 스톱으로 확정하면 "현재 위치"를 그 후보의 좌표로 옮기고 반복한다.

### 3.2 언제 멈추는가 (반나절/하루 예산)

기존 mock의 반나절(4시간)/하루(8시간) 개념을 실제 계산으로 되살립니다.

- **이동 시간**: 스톱 사이 직선거리를 도보 속도 약 4km/h로 환산(기존 `formatWalkMinutesLabel`과
  동일한 가정 — `distanceMeters / 67`분, 반올림, 최소 1분).
- **체류 시간**: 실측 데이터가 없으므로 `contentTypeId`별 고정 추정치를 정책 상수로 둡니다.

  | contentTypeId | 분류 | 추정 체류시간 |
  | --- | --- | --- |
  | 12 | 관광지 | 60분 |
  | 14 | 문화시설 | 45분 |
  | 28 | 액티비티 | 90분 |
  | 39 | 맛집 | 50분 |

- 누적 (이동+체류) 시간이 선택한 예산(`HALF`=240분, `FULL`=480분)을 넘기기 **직전까지** 다음
  후보를 계속 추가합니다. 다음 후보를 추가했을 때 예산을 넘기면 그 후보는 건너뛰고, 예산 안에
  들어오는 다음으로 가까운 후보가 있으면 그걸 대신 추가합니다(사용 못한 후보가 남아 있는데 코스가
  일찍 끝나는 것을 방지).
- **안전장치**: 한 번의 이동(hop)이 지나치게 멀어지는 것을 막기 위해 스톱 간 최대 이동거리 상한을
  둡니다(초안 1,500m — 이 값을 넘는 후보는 그 라운드에서 제외). 코스가 한없이 늘어나지 않도록
  최대 스톱 개수 상한도 둡니다(초안 6개).
- 후보가 하나도 없거나(반경 내 관광지 없음) 첫 이동부터 상한을 넘기면 빈 코스(스톱 0개)를
  반환합니다 — 화면에서 안내 문구로 처리합니다.

### 3.3 같은 카테고리 연속 방지 (경량 규칙)

순수 거리순으로만 고르면 축제장 주변에 몰려있는 같은 유형(특히 `39` 맛집/카페 골목)이 연달아
뽑히기 쉽습니다. 완전한 "다양성 보정"(정확한 카테고리 비율 유지 등, 2차 범위)까지는 아니지만,
아주 가벼운 선호 규칙 하나로 흔한 경우는 막을 수 있어 1차에 포함합니다.

> 다음 스톱을 고를 때, 가장 가까운 후보가 **직전 스톱과 같은 `contentTypeId`**이면, 조건(최대
> 이동거리·예산)을 만족하면서 **다른 `contentTypeId`인 후보가 `DIVERSITY_TOLERANCE`(초안 1.5배)
> 거리 이내에 있으면 그 후보를 대신 선택**합니다. 그런 대안이 없으면(그 카테고리 후보뿐이면) 원래
> 가장 가까운 후보를 그대로 선택합니다 — 다양성 때문에 억지로 먼 곳까지 끌고 가지 않습니다.
>
> 첫 스톱(직전 스톱이 없는 경우)에는 이 규칙을 적용하지 않습니다.

이 규칙은 "이번 라운드에서 후보를 고르는 방식"만 바꾸고, 3.2절의 예산·최대 이동거리 조건이나
4장의 응답 스펙에는 영향을 주지 않습니다.

### 3.4 알고리즘 요약 (의사코드)

```text
current = festival.coordinate
previousContentTypeId = null
remainingBudget = type == HALF ? 240 : 480
candidates = 반경 내 ACTIVE tour_places (festival 기준 거리 필터, 이미 있는 조회 재사용)
stops = []

while candidates가 남아있고 stops.size < MAX_STOPS:
    거리순으로 candidates 정렬 (current 기준)
    feasible = candidates 중 hop <= MAX_HOP_METERS 이고
               walkMinutes(hop) + STAY_MINUTES[contentTypeId] <= remainingBudget 인 것들 (거리순 유지)
    if feasible이 비어있음: break   // 예산/거리 조건을 만족하는 후보가 없으면 종료

    nearest = feasible[0]
    if previousContentTypeId != null and nearest.contentTypeId == previousContentTypeId:
        // 직전과 같은 카테고리면, 조금 더 멀어도 다른 카테고리 대안을 찾는다
        alternative = feasible 중 nearest와 다른 contentTypeId이면서
                      distance(current, it) <= distance(current, nearest) * DIVERSITY_TOLERANCE
                      인 것 중 가장 가까운 것
        chosen = alternative ?? nearest   // 대안이 없으면 원래 가장 가까운 후보를 그대로 선택
    else:
        chosen = nearest

    walkMinutes = ceil(distance(current, chosen) / 67)
    stayMinutes = STAY_MINUTES[chosen.contentTypeId]
    stops.add(chosen, order=stops.size+1, walkMinutes, stayMinutes)
    remainingBudget -= (walkMinutes + stayMinutes)
    current = chosen.coordinate
    previousContentTypeId = chosen.contentTypeId
    candidates.remove(chosen)

return stops
```

## 4. API 설계

새 엔드포인트를 추가합니다. 기존 `nearby-spots`(순서 없는 목록, `HomePage` 등 다른 화면이 계속
사용)는 그대로 두고 건드리지 않습니다.

```
GET /api/festivals/{id}/solo-course?type=HALF|FULL
```

- `type`: `HALF`(기본값) 또는 `FULL`. 그 외 값은 `400 VALIDATION_ERROR`.
- 인증·체크인 검증은 하지 않습니다 — 기존 `nearby-spots`와 동일하게 공개 정보이고, "어느 축제
  기준으로 조회할지"는 이미 프론트(`resolveSoloCourseFestival`)가 결정해서 넘겨줍니다.
- 좌표 없는 축제, `HIDDEN` 축제는 기존 `nearby-spots`와 동일하게 처리(빈 코스 또는 404).

### 4.1 응답 스펙

```jsonc
{
  "type": "HALF",
  "totalWalkMinutes": 18,
  "totalStayMinutes": 205,
  "totalDurationMinutes": 223,
  "stops": [
    {
      "order": 1,
      "id": 501,
      "title": "○○전망대",
      "address": "강원특별자치도 ...",
      "contentTypeId": "12",
      "imageUrl": "https://...",
      "distanceFromPreviousMeters": 420,
      "walkMinutesFromPrevious": 7,
      "estimatedStayMinutes": 60
    }
  ]
}
```

- `distanceFromPreviousMeters`/`walkMinutesFromPrevious`의 "이전"은 1번 스톱 기준으로는 축제
  좌표입니다.
- `totalDurationMinutes` = `totalWalkMinutes` + `totalStayMinutes` (예산 이하).

## 5. Backend 구현 계획

- 신규 `SoloCourseService`(또는 `FestivalQueryService`에 메서드 추가) — `getNearbyTourPlaces`가
  이미 하는 "축제 조회 + 반경 내 ACTIVE 관광지 후보 계산"을 재사용하고, 그 위에 3장의 greedy
  순서/예산 로직을 추가합니다.
- 체류시간 정책(3.2절 표), `MAX_HOP_METERS`/`MAX_STOPS`/`HALF`·`FULL` 예산(분),
  `DIVERSITY_TOLERANCE`(3.3절)는 `CheckinValidityPolicy`처럼 정책 상수 클래스(예:
  `SoloCourseStayPolicy`)로 분리해 테스트하기 쉽게 만듭니다.
- 신규 Flyway migration은 필요 없습니다(기존 `tour_places`/`festivals` 컬럼만 사용).
- 신규 TourAPI 외부 호출은 없습니다(이미 동기화된 DB 데이터만 사용).

## 6. Frontend 반영 계획

- `SoloCoursePage`가 `festivalsApi.getNearbyTourPlaces` 대신(또는 함께) 신규
  `festivalsApi.getSoloCourse(festivalId, type)`를 호출합니다.
- docs/22에서 걷어냈던 "반나절/하루" 토글을 다시 넣되, 이번엔 실제 API `type` 파라미터에
  연결합니다.
- 목록 UI 대신 순서(①②③...)와 스톱 간 도보시간이 보이는 타임라인 UI로 되돌립니다(예전 mock과
  비슷한 모양이지만 데이터는 전부 실제 계산값). 각 스톱 카드는 기존처럼 클릭 시 `/spots/:id`로
  이동합니다.
- 상단에 "총 예상 소요 약 N시간(도보 M분 포함)" 요약을 표시합니다.
- 빈 코스(스톱 0개)는 "반경 내 추천할 코스를 만들지 못했어요" 안내로 처리합니다.
- 체크인/기준 축제 결정 로직(`resolveSoloCourseFestival`)은 변경하지 않습니다.

## 7. 이번 범위에서 제외 (2차 이후)

- 실제 영업시간(`usetime`)·휴무일(`restdate`) 반영 — 관광공사 `detailIntro2`는 콘텐츠 타입별로
  필드셋이 달라 매핑 작업이 필요하고, 코스에 들어갈 스톱마다 온디맨드 호출이 늘어 API 호출 한도도
  고려해야 합니다.
- 정확한 카테고리 비율을 맞추는 수준의 본격적인 다양성 보정(3.3절은 "직전과 같으면 가벼운 대안
  탐색"만 함), 식사 시간대 맛집 슬롯 배치 — 후자는 출발 시각 입력이 있어야 의미가 있어 이번
  범위에서는 하지 않습니다.
- 사용자가 특정 스톱을 빼거나 순서를 바꾸는 수동 편집 UI.
- 코스 저장/공유, 완료 체크 같은 이력 관리 기능.

## 8. 테스트 우선순위

- 후보 0건/1건, 예산을 넘겨 일부만 선택되는 경우, 첫 이동부터 `MAX_HOP_METERS` 초과인 경우의
  순서·예산 계산 단위 테스트
- `HALF`/`FULL` 예산 값과 최대 스톱 개수 경계
- 3.3절 카테고리 연속 방지: (a) 다른 카테고리 대안이 허용 범위 내에 있으면 그 대안을 선택하는지,
  (b) 대안이 범위 밖이거나 없으면 원래 가장 가까운 후보를 그대로 선택하는지, (c) 첫 스톱에는
  규칙이 적용되지 않는지
- 좌표 없는 축제, `HIDDEN` 축제, 잘못된 `type` 파라미터 처리
- 후보 필터링(반경, `ACTIVE` 상태)은 기존 `getNearbyTourPlaces` 회귀 테스트로 이미 커버되므로
  중복 작성하지 않고 재사용 가능 여부만 확인

## 9. 결정 결과

- `MAX_HOP_METERS`(1,500m), `MAX_STOPS`(6개), 카테고리별 체류시간(3.2절 표 값),
  `DIVERSITY_TOLERANCE`(1.5배) — 초안 수치 그대로 적용.
- `HALF`/`FULL` 두 값 고정, 사용자가 직접 시간을 입력하는 UI는 두지 않음.
- 반경은 쿼리 파라미터로 열지 않고 코스는 고정 반경(5,000m, `nearby-spots` 기본값과 동일)만 사용.

## 10. 구현 결과

- Backend: `SoloCourseStayPolicy`(정책 상수), `SoloCourseService`(3~4장 알고리즘),
  `SoloCourseType`/`SoloCourseResponse`/`SoloCourseStopResponse`(DTO)를 신설하고
  `FestivalController`에 `GET /api/festivals/{id}/solo-course?type=HALF|FULL`을 추가했다.
  기존 `getNearbyTourPlaces`가 쓰는 `TourPlaceRepository.findAllVisibleWithCoordinates`를
  그대로 재사용해 새 repository 쿼리나 Flyway migration을 추가하지 않았다.
- `type` 쿼리 파라미터에 `HALF`/`FULL` 외 값을 주면 Spring의 enum 바인딩 실패로
  `400 INVALID_INPUT_VALUE`가 반환된다(4장 초안의 `VALIDATION_ERROR` 표기를 실제 동작에 맞게
  정정).
- Backend `SoloCourseStayPolicyTest`(예산·도보시간·체류시간 계산), `SoloCourseServiceTest`(좌표
  없음/HIDDEN/후보 0건, 최근접 이웃 순서가 단순 거리순과 달라지는 경우, `MAX_HOP_METERS`·
  `MAX_STOPS`·예산 경계, 카테고리 연속 방지 규칙의 대안 선택/폴백/첫 스톱 예외)와
  `FestivalControllerTest` 신규 케이스를 추가했다. Backend 전체 336건 중 이번 변경과 무관한
  기존 pgvector Docker 이미지 fetch 실패 21건을 제외하고 전부 통과했다.
  `./gradlew build -x test`도 성공했다.
- Frontend: `SoloCoursePage`가 `festivalsApi.getSoloCourse(festivalId, type)`를 호출하도록
  바뀌었다. docs/22에서 걷어냈던 반나절/하루 토글과 타임라인 UI를 되살리되, 이번엔 스톱 순서·
  이전 스톱과의 도보시간·예상 체류시간이 전부 실제 계산값이다. 각 스톱 카드는 기존처럼 클릭 시
  `/spots/:id`로 이동한다. 코스가 비어 있으면 "반경 내에서 추천할 코스를 만들지 못했어요" 안내를
  표시한다.
- `utils/tourSpot.ts`에 `contentTypeId` → 한글 라벨 변환 `contentTypeLabel`을 추가했다(3.2절
  표와 동일한 4종 + 기타).
- 축제/체크인 기준 결정 로직(`resolveSoloCourseFestival`)은 docs/22에서 만든 것을 그대로 쓴다.
- Frontend 전체 Vitest 25 files/214 tests(신규 `formatDurationLabel` 4건 포함),
  `npx tsc --noEmit`, production/PWA build가 성공했다.
- dev DB·두 브라우저 수동 검증은 아직 실행하지 않았다.
