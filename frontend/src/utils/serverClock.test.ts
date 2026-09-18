import { describe, expect, it } from 'vitest';
import {
  calculateServerOffsetMs,
  correctedNowMs,
  remainingSeconds,
  stabilizeRemainingSeconds,
} from './serverClock';

describe('serverClock', () => {
  it('client clock 편차가 달라도 같은 serverNow와 deadline이면 남은 시간이 같다', () => {
    const serverNow = '2026-08-14T12:00:00+09:00';
    const deadline = '2026-08-14T12:00:30+09:00';
    const fastClient = Date.parse('2026-08-14T12:05:00+09:00');
    const slowClient = Date.parse('2026-08-14T11:55:00+09:00');
    expect(remainingSeconds(deadline, calculateServerOffsetMs(serverNow, fastClient), fastClient)).toBe(30);
    expect(remainingSeconds(deadline, calculateServerOffsetMs(serverNow, slowClient), slowClient)).toBe(30);
  });

  it('REST refresh의 최신 serverNow로 offset을 다시 보정한다', () => {
    const clientNow = Date.parse('2026-08-14T12:00:00+09:00');
    const first = calculateServerOffsetMs('2026-08-14T12:00:03+09:00', clientNow);
    const refreshed = calculateServerOffsetMs('2026-08-14T12:00:01+09:00', clientNow);
    expect(correctedNowMs(first, clientNow)).toBe(clientNow + 3_000);
    expect(correctedNowMs(refreshed, clientNow)).toBe(clientNow + 1_000);
  });

  it('같은 deadline의 큰 역방향 점프를 제한한다', () => {
    expect(stabilizeRemainingSeconds({ deadlineKey: 'a', seconds: 10 }, 'a', 25)).toBe(10);
  });

  it('round 또는 deadline key가 바뀌면 실제 연장을 반영한다', () => {
    expect(stabilizeRemainingSeconds({ deadlineKey: 'round-1', seconds: 1 }, 'round-2', 30)).toBe(30);
  });
});
