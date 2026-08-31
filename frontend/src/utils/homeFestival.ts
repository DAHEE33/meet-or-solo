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
 * 히어로 축제를 무엇을 근거로 골랐는지. 체크인·GPS·목록 조회가 각각 비동기라 도착 순서가
 * 일정하지 않으므로, 늦게 온 결과가 더 확실한 근거를 덮어쓰지 않도록 우선순위로 비교한다.
 *
 * CHECKIN이 가장 높은 이유: 사용자가 이미 "나 여기 있다"고 선언한 축제이므로 GPS로 계산한
 * 최근접 축제보다 확실하다. 다른 축제에 체크인한 채로 홈에 오면 그 축제가 보여야 한다.
 */
export type HeroSource = 'FALLBACK' | 'NEAREST' | 'CHECKIN';

const HERO_SOURCE_PRIORITY: Record<HeroSource, number> = {
  FALLBACK: 1,
  NEAREST: 2,
  CHECKIN: 3,
};

/** 새 근거가 지금 화면에 반영된 근거보다 우선하거나 같으면(=최신값으로 갱신) true. */
export function shouldReplaceHero(current: HeroSource | null, next: HeroSource): boolean {
  if (current === null) return true;
  return HERO_SOURCE_PRIORITY[next] >= HERO_SOURCE_PRIORITY[current];
}

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
