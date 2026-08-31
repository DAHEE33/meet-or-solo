import { describe, expect, it } from 'vitest';
import type { Festival } from '../types';
import type { FestivalListItem } from '../api/festivals';
import { mapFestivalListItemToFestival } from './festival';
import {
  pickFallbackFestival,
  pickNearestFestival,
  shouldReplaceHero,
  sigunguName,
} from './homeFestival';

function festival(id: number, status: Festival['status']): Festival {
  return {
    id,
    name: `축제 ${id}`,
    status,
    ddayLabel: '',
    periodShort: '',
    periodFull: '',
    address: '',
    intro: '',
    thumbnailUrl: null,
    infoItems: [],
    programs: [],
    nearbyPlaces: [],
  };
}

function listItem(id: number, mapX: number | null, mapY: number | null, address: string | null): FestivalListItem {
  return {
    id,
    contentId: String(id),
    title: `축제 ${id}`,
    address,
    regionCode: '51',
    sigunguCode: '110',
    // 오늘과 무관하게 항상 '진행 중'으로 계산되도록 넓은 기간을 쓴다.
    eventStartDate: '2020-01-01',
    eventEndDate: '2099-12-31',
    status: 'ACTIVE',
    originImageUrl: null,
    thumbnailUrl: null,
    mapX,
    mapY,
  };
}

describe('pickFallbackFestival', () => {
  // 이 규칙은 위치 기능 도입 전 동작과 같아야 한다 — 권한을 거부한 사용자의 화면이 바뀌면 안 된다.
  it('진행 중인 축제를 우선한다', () => {
    const result = pickFallbackFestival([festival(1, 'upcoming'), festival(2, 'ongoing')]);

    expect(result?.id).toBe(2);
  });

  it('진행 중이 없으면 예정 축제를 고른다', () => {
    const result = pickFallbackFestival([festival(1, 'ended'), festival(2, 'upcoming')]);

    expect(result?.id).toBe(2);
  });

  it('진행 중과 예정이 여러 개면 목록 순서상 첫 번째를 고른다', () => {
    const result = pickFallbackFestival([festival(1, 'ongoing'), festival(2, 'ongoing')]);

    expect(result?.id).toBe(1);
  });

  it('후보가 없으면 null이다', () => {
    expect(pickFallbackFestival([])).toBeNull();
    expect(pickFallbackFestival([festival(1, 'ended')])).toBeNull();
  });
});

describe('sigunguName', () => {
  it('주소 두 번째 토큰을 시군구명으로 쓴다', () => {
    expect(sigunguName('강원특별자치도 강릉시 창해로 514')).toBe('강릉시');
  });

  it('시도 표기가 축약형이어도 시군구명은 같게 뽑는다', () => {
    expect(sigunguName('강원 속초시 중앙로 183')).toBe('속초시');
  });

  it('토큰이 부족하거나 비어 있으면 null이다', () => {
    expect(sigunguName('강원특별자치도')).toBeNull();
    expect(sigunguName('')).toBeNull();
    expect(sigunguName(null)).toBeNull();
    expect(sigunguName(undefined)).toBeNull();
  });

  it('공백이 여러 개여도 처리한다', () => {
    expect(sigunguName('  강원특별자치도   강릉시   창해로 514 ')).toBe('강릉시');
  });
});

describe('pickNearestFestival', () => {
  const origin = { latitude: 37.8813, longitude: 127.73 };

  it('가장 가까운 축제와 거리, 시군구명을 반환한다', () => {
    const near = listItem(1, 127.74, 37.88, '강원특별자치도 춘천시 중앙로 1');
    const far = listItem(2, 128.8761, 37.7519, '강원특별자치도 강릉시 창해로 514');

    const result = pickNearestFestival(origin, [far, near], mapFestivalListItemToFestival);

    expect(result?.festival.id).toBe(1);
    expect(result?.sigunguName).toBe('춘천시');
    expect(result?.distanceMeters).toBeLessThan(2_000);
  });

  it('좌표가 없는 축제만 있으면 null이라서 호출부가 폴백을 쓴다', () => {
    const result = pickNearestFestival(
      origin,
      [listItem(1, null, null, '강원특별자치도 춘천시 중앙로 1')],
      mapFestivalListItemToFestival,
    );

    expect(result).toBeNull();
  });

  it('빈 목록이면 null이다', () => {
    expect(pickNearestFestival(origin, [], mapFestivalListItemToFestival)).toBeNull();
  });

  it('주소가 없으면 시군구명은 null이지만 축제는 고른다', () => {
    const result = pickNearestFestival(
      origin,
      [listItem(1, 127.74, 37.88, null)],
      mapFestivalListItemToFestival,
    );

    expect(result?.festival.id).toBe(1);
    expect(result?.sigunguName).toBeNull();
  });
});

describe('shouldReplaceHero', () => {
  // 체크인·GPS·목록 응답 도착 순서가 일정하지 않아, 늦게 온 결과가 더 확실한 근거를
  // 덮어쓰지 않아야 한다.
  it('아직 아무것도 못 정했으면 무엇이든 반영한다', () => {
    expect(shouldReplaceHero(null, 'FALLBACK')).toBe(true);
    expect(shouldReplaceHero(null, 'CHECKIN')).toBe(true);
  });

  it('체크인은 최근접·폴백을 덮어쓴다', () => {
    expect(shouldReplaceHero('FALLBACK', 'CHECKIN')).toBe(true);
    expect(shouldReplaceHero('NEAREST', 'CHECKIN')).toBe(true);
  });

  it('체크인이 이미 반영됐으면 늦게 온 GPS·폴백이 덮어쓰지 못한다', () => {
    expect(shouldReplaceHero('CHECKIN', 'NEAREST')).toBe(false);
    expect(shouldReplaceHero('CHECKIN', 'FALLBACK')).toBe(false);
  });

  it('최근접이 반영된 뒤 폴백이 늦게 와도 덮어쓰지 못한다', () => {
    expect(shouldReplaceHero('NEAREST', 'FALLBACK')).toBe(false);
  });

  it('같은 근거는 최신값으로 갱신한다', () => {
    expect(shouldReplaceHero('CHECKIN', 'CHECKIN')).toBe(true);
    expect(shouldReplaceHero('NEAREST', 'NEAREST')).toBe(true);
  });
});
