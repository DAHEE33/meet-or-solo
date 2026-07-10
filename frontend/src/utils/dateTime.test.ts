import { describe, expect, it } from 'vitest';
import { formatSeoulDateTime } from './dateTime';

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
