import { describe, expect, it } from 'vitest';
import { formatRelativeTime } from './relativeTime';

const now = new Date('2026-09-07T12:00:00+09:00');
const before = (ms: number) => new Date(now.getTime() - ms).toISOString();

const SECOND = 1000;
const MINUTE = 60 * SECOND;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

describe('formatRelativeTime', () => {
  it('1분 미만은 방금이다', () => {
    expect(formatRelativeTime(before(0), now)).toBe('방금');
    expect(formatRelativeTime(before(59 * SECOND), now)).toBe('방금');
  });

  it('분 경계에서 분 표기로 넘어간다', () => {
    expect(formatRelativeTime(before(MINUTE), now)).toBe('1분 전');
    expect(formatRelativeTime(before(59 * MINUTE), now)).toBe('59분 전');
  });

  it('시간 경계에서 시간 표기로 넘어간다', () => {
    expect(formatRelativeTime(before(HOUR), now)).toBe('1시간 전');
    expect(formatRelativeTime(before(23 * HOUR), now)).toBe('23시간 전');
  });

  it('일 경계에서 일 표기로 넘어가고 6일까지 유지한다', () => {
    expect(formatRelativeTime(before(DAY), now)).toBe('1일 전');
    expect(formatRelativeTime(before(6 * DAY), now)).toBe('6일 전');
  });

  it('7일 이상은 상대 표기 대신 날짜로 표시한다', () => {
    const label = formatRelativeTime(before(7 * DAY), now);
    expect(label).not.toContain('일 전');
    expect(label).toContain('2026');
  });

  it('서버·클라이언트 시계 차이로 생기는 미래 시각은 방금으로 접는다', () => {
    const future = new Date(now.getTime() + 5 * MINUTE).toISOString();
    expect(formatRelativeTime(future, now)).toBe('방금');
  });

  it('값이 없거나 해석할 수 없으면 빈 문자열이다', () => {
    expect(formatRelativeTime(null, now)).toBe('');
    expect(formatRelativeTime(undefined, now)).toBe('');
    expect(formatRelativeTime('not-a-date', now)).toBe('');
  });
});
