import { describe, expect, it } from 'vitest';
import { findNearest, metersBetween } from './geo';

describe('metersBetween', () => {
  it('같은 좌표는 0m이다', () => {
    expect(metersBetween(37.8813, 127.73, 37.8813, 127.73)).toBe(0);
  });

  // backend GeoDistanceCalculator와 같은 공식(haversine, 지구 반지름 6,371,000m)을 쓰므로
  // 같은 좌표쌍에서 같은 값이 나와야 한다. 두 구현이 어긋나면 화면 거리와 서버 계산이 달라진다.
  it('춘천 - 강릉 직선거리를 계산한다', () => {
    // 춘천시청(37.8813, 127.7300) - 강릉시청(37.7519, 128.8761)
    const distance = metersBetween(37.8813, 127.73, 37.7519, 128.8761);

    // 실제 직선거리는 약 101km다. 공식이 바뀌면 이 범위를 벗어난다.
    expect(distance).toBeGreaterThan(100_000);
    expect(distance).toBeLessThan(102_000);
  });

  it('거리는 방향과 무관하게 같다', () => {
    const forward = metersBetween(37.8813, 127.73, 37.7519, 128.8761);
    const backward = metersBetween(37.7519, 128.8761, 37.8813, 127.73);

    expect(forward).toBe(backward);
  });

  it('정수 미터로 반올림한다', () => {
    expect(Number.isInteger(metersBetween(37.8813, 127.73, 37.7519, 128.8761))).toBe(true);
  });
});

describe('findNearest', () => {
  const origin = { latitude: 37.8813, longitude: 127.73 };

  it('가장 가까운 항목과 거리를 반환한다', () => {
    const near = { id: 1, mapX: 127.74, mapY: 37.88 };
    const far = { id: 2, mapX: 128.87, mapY: 37.75 };

    const result = findNearest(origin, [far, near]);

    expect(result?.item).toBe(near);
    expect(result?.distanceMeters).toBeLessThan(2_000);
  });

  it('좌표가 없는 항목은 후보에서 제외한다', () => {
    const noCoordinates = { id: 1, mapX: null, mapY: null };
    const withCoordinates = { id: 2, mapX: 128.87, mapY: 37.75 };

    const result = findNearest(origin, [noCoordinates, withCoordinates]);

    expect(result?.item).toBe(withCoordinates);
  });

  it('mapX만 있고 mapY가 없으면 제외한다', () => {
    const result = findNearest(origin, [{ id: 1, mapX: 127.74, mapY: null }]);

    expect(result).toBeNull();
  });

  it('후보가 하나도 없으면 null이다 — 호출부가 폴백을 쓴다', () => {
    expect(findNearest(origin, [])).toBeNull();
    expect(findNearest(origin, [{ id: 1, mapX: null, mapY: null }])).toBeNull();
  });

  it('거리가 같으면 먼저 나온 항목을 유지한다', () => {
    const first = { id: 1, mapX: 127.74, mapY: 37.8813 };
    const second = { id: 2, mapX: 127.74, mapY: 37.8813 };

    expect(findNearest(origin, [first, second])?.item).toBe(first);
  });
});
