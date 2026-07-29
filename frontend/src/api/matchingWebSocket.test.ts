import { describe, expect, it } from 'vitest';
import {
  buildMatchingWebSocketUrl,
  parseMatchingNotification,
} from './matchingWebSocket';

describe('matchingWebSocket', () => {
  it('현재 origin의 protocol과 host로 /ws URL을 만든다', () => {
    expect(buildMatchingWebSocketUrl({ protocol: 'http:', host: 'localhost:5173' })).toBe(
      'ws://localhost:5173/ws',
    );
    expect(buildMatchingWebSocketUrl({ protocol: 'https:', host: 'example.com' })).toBe(
      'wss://example.com/ws',
    );
  });

  it('matching 상태 변경 알림만 파싱한다', () => {
    expect(
      parseMatchingNotification({
        body: JSON.stringify({
          type: 'MATCHING_STATE_CHANGED',
          reason: 'MATCH_PROPOSED',
          occurredAt: '2026-07-29T12:00:00+09:00',
        }),
      }),
    ).toEqual({
      type: 'MATCHING_STATE_CHANGED',
      reason: 'MATCH_PROPOSED',
      occurredAt: '2026-07-29T12:00:00+09:00',
    });
    expect(parseMatchingNotification({ body: '{"type":"CHAT_MESSAGE"}' })).toBeNull();
    expect(parseMatchingNotification({ body: 'invalid-json' })).toBeNull();
  });
});
