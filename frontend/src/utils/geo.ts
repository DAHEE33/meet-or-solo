// 좌표 두 점 사이의 거리 계산. backend global/geo/GeoDistanceCalculator와 **같은 공식·같은
// 지구 반지름**을 쓴다.
//
// 같은 계산이 서버와 클라이언트 양쪽에 있는 이유: 사용자 GPS 좌표를 서버로 보내지 않기로 했기
// 때문이다(docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md 3.1). 서버는 축제 좌표만 목록에
// 실어 보내고, "내 위치에서 가장 가까운 축제"는 브라우저가 직접 계산한다. 두 구현의 결과가
// 어긋나면 화면과 서버 계산(주변 관광지 등)이 달라지므로 값이 일치해야 한다.

const EARTH_RADIUS_METERS = 6_371_000;

function toRadians(degrees: number): number {
  return (degrees * Math.PI) / 180;
}

/** haversine 공식으로 두 좌표 사이 지표면 거리를 미터로 반환한다(서버와 동일하게 정수로 반올림). */
export function metersBetween(
  latitude1: number,
  longitude1: number,
  latitude2: number,
  longitude2: number,
): number {
  const phi1 = toRadians(latitude1);
  const phi2 = toRadians(latitude2);
  const deltaPhi = toRadians(latitude2 - latitude1);
  const deltaLambda = toRadians(longitude2 - longitude1);

  const a =
    Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
    Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Math.round(EARTH_RADIUS_METERS * c);
}

export type Coordinates = { latitude: number; longitude: number };

/** mapX(경도)/mapY(위도)를 가진 항목. 좌표는 동기화 데이터가 불완전할 수 있어 null을 허용한다. */
export type MapPoint = { mapX: number | null; mapY: number | null };

export type NearestResult<T> = { item: T; distanceMeters: number };

/** 카카오맵 웹 길찾기로 새 탭을 여는 딥링크. 좌표가 없으면 만들 수 없으므로 호출부가
 * mapX/mapY(또는 latitude/longitude)를 미리 null 체크해야 한다. */
export function buildKakaoDirectionsUrl(name: string, latitude: number, longitude: number): string {
  return `https://map.kakao.com/link/to/${encodeURIComponent(name)},${latitude},${longitude}`;
}

/**
 * 기준 좌표에서 가장 가까운 항목을 고른다. 좌표가 없는 항목은 거리를 계산할 수 없으므로
 * 후보에서 제외하고, 후보가 하나도 없으면 null을 반환해 호출부가 폴백을 쓰게 한다.
 */
export function findNearest<T extends MapPoint>(
  origin: Coordinates,
  items: readonly T[],
): NearestResult<T> | null {
  let nearest: NearestResult<T> | null = null;
  for (const item of items) {
    if (item.mapX === null || item.mapY === null) continue;
    const distanceMeters = metersBetween(origin.latitude, origin.longitude, item.mapY, item.mapX);
    if (nearest === null || distanceMeters < nearest.distanceMeters) {
      nearest = { item, distanceMeters };
    }
  }
  return nearest;
}
