import { describe, expect, it } from 'vitest';
import { toPlacePick } from './KakaoPlaceSearch';
import type { KakaoPlaceSearchItem } from '../matching/KakaoMeetingPointMap';

const item = (overrides: Partial<KakaoPlaceSearchItem> = {}): KakaoPlaceSearchItem => ({
  id: '12345',
  place_name: '춘천시청',
  address_name: '강원 춘천시 중앙로 1',
  road_address_name: '강원 춘천시 중앙로1가 1',
  x: '127.729999',
  y: '37.881300',
  ...overrides,
});

describe('toPlacePick', () => {
  it('도로명주소가 있으면 도로명주소를 쓴다', () => {
    const result = toPlacePick(item());

    expect(result.address).toBe('강원 춘천시 중앙로1가 1');
  });

  it('도로명주소가 없으면 지번주소로 대체한다', () => {
    const result = toPlacePick(item({ road_address_name: '' }));

    expect(result.address).toBe('강원 춘천시 중앙로 1');
  });

  it('문자열 좌표를 숫자로 바꾼다', () => {
    const result = toPlacePick(item());

    expect(result.longitude).toBe(127.729999);
    expect(result.latitude).toBe(37.8813);
  });

  it('이름과 카카오 장소 ID를 그대로 옮긴다', () => {
    const result = toPlacePick(item());

    expect(result.name).toBe('춘천시청');
    expect(result.kakaoPlaceId).toBe('12345');
  });
});
