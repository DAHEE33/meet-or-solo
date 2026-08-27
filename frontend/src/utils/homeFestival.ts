import type { Festival } from '../types';
import type { FestivalListItem } from '../api/festivals';
import { findNearest, type Coordinates } from './geo';

// 홈 화면 대표(히어로) 축제를 고르는 규칙. 컴포넌트에서 분리해 렌더링 없이 테스트한다.

export type HeroFestival = {
  festival: Festival;
  /** 내 위치 기준으로 고른 경우에만 채워진다. 폴백에서는 거리를 알 수 없다. */
  distanceMeters: number | null;
  sigunguName: string | null;
};

/**
 * GPS를 쓸 수 없을 때의 폴백. 이 규칙은 위치 기능 도입 전 동작과 동일해야 한다 —
 * 권한을 거부한 사용자의 화면이 바뀌면 안 된다.
 */
export function pickFallbackFestival(festivals: readonly Festival[]): Festival | null {
  return (
    festivals.find((festival) => festival.status === 'ongoing') ??
    festivals.find((festival) => festival.status === 'upcoming') ??
    null
  );
}

/**
 * 주소에서 시군구명을 뽑는다. 주소는 "강원특별자치도 강릉시 ..." 형태라 두 번째 토큰이
 * 시군구명이다. 시도 표기가 '강원특별자치도'/'강원'으로 섞여 있어 첫 토큰은 쓰지 않는다.
 * (서버 RegionNameResolver와 같은 규칙)
 */
export function sigunguName(address: string | null | undefined): string | null {
  if (!address) return null;
  const tokens = address.trim().split(/\s+/);
  if (tokens.length < 2 || !tokens[1]) return null;
  return tokens[1];
}

/**
 * 내 위치에서 가장 가까운 축제를 고른다. 좌표가 없는 축제는 거리를 계산할 수 없어 후보에서
 * 빠지고, 후보가 하나도 없으면 null을 반환해 호출부가 폴백을 쓰게 한다.
 *
 * 사용자 좌표는 서버로 전송되지 않고 이 계산에만 쓰인다
 * (docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md 3.1).
 */
export function pickNearestFestival(
  origin: Coordinates,
  items: readonly FestivalListItem[],
  toFestival: (item: FestivalListItem) => Festival,
): HeroFestival | null {
  const nearest = findNearest(origin, items);
  if (!nearest) return null;
  return {
    festival: toFestival(nearest.item),
    distanceMeters: nearest.distanceMeters,
    sigunguName: sigunguName(nearest.item.address),
  };
}
