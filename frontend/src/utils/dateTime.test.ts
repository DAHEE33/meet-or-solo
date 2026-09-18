import { describe, expect, it } from 'vitest';
import { formatSeoulDateTime, isSameSeoulDate, seoulDateKey } from './dateTime';

describe('formatSeoulDateTime', () => {
  it('UTC 시각을 같은 절대 시점의 한국 시각으로 표시한다', () => {
    expect(formatSeoulDateTime('2026-07-10T06:50:45.579678Z')).toBe('2026-07-10 15:50:45');
  });

  it('offset이 있는 동일 시점은 같은 한국 시각으로 표시한다', () => {
    expect(formatSeoulDateTime('2026-07-10T15:50:45+09:00')).toBe('2026-07-10 15:50:45');
  });

  it('KST API 응답에 9시간을 중복 가산하지 않는다', () => {
    expect(formatSeoulDateTime('2026-07-10T16:32:14.061761+09:00')).toBe('2026-07-10 16:32:14');
  });

  it.each([null, undefined, '', 'invalid-date'])('null 또는 유효하지 않은 값은 대시로 표시한다', (value) => {
    expect(formatSeoulDateTime(value)).toBe('-');
  });
});

describe('seoulDateKey', () => {
  it('UTC 시각을 서울 기준 날짜로 바꾼다', () => {
    // 09-09T16:00Z는 서울에서 이미 09-10 01:00이다.
    expect(seoulDateKey('2026-09-09T16:00:00Z')).toBe('2026-09-10');
  });

  it('offset이 실린 값은 같은 절대 시점의 서울 날짜로 본다', () => {
    expect(seoulDateKey('2026-09-10T00:30:00+09:00')).toBe('2026-09-10');
  });

  it.each([null, undefined, '', 'invalid-date'])('읽을 수 없는 값은 null이다', (value) => {
    expect(seoulDateKey(value)).toBeNull();
  });
});

describe('isSameSeoulDate', () => {
  it('서울 기준 같은 날이면 true다', () => {
    expect(isSameSeoulDate('2026-09-10T00:05:00+09:00', '2026-09-10T23:55:00+09:00')).toBe(true);
  });

  it('자정을 넘기면 5분 차이여도 다른 날이다', () => {
    expect(isSameSeoulDate('2026-09-09T23:58:00+09:00', '2026-09-10T00:03:00+09:00')).toBe(false);
  });

  it('UTC로 같은 날이어도 서울 기준으로 갈리면 다른 날이다', () => {
    expect(isSameSeoulDate('2026-09-09T14:00:00Z', '2026-09-09T16:00:00Z')).toBe(false);
  });

  it.each([
    [null, '2026-09-10T00:00:00+09:00'],
    ['2026-09-10T00:00:00+09:00', undefined],
    [null, null],
  ])('한쪽이라도 읽을 수 없으면 false다', (left, right) => {
    expect(isSameSeoulDate(left, right)).toBe(false);
  });
});
