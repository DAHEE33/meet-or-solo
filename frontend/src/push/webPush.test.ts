import { describe, expect, it } from 'vitest';
import {
  ASK_AGAIN_AFTER_MS,
  shouldAsk,
  toSubscriptionPayload,
  urlBase64ToUint8Array,
} from './webPush';

describe('shouldAsk', () => {
  const NOW = Date.parse('2026-09-19T12:00:00+09:00');

  /** 이미 허용했으면 다시 묻지 않고 바로 구독한다. */
  it('허용된 상태면 언제나 진행한다', () => {
    expect(shouldAsk('granted', NOW, NOW)).toBe(true);
    expect(shouldAsk('granted', null, NOW)).toBe(true);
  });

  /** 브라우저는 한 번 거절하면 다시 묻지 않는다. 시도해도 권한 창이 뜨지 않는다. */
  it('거절된 상태면 유예와 무관하게 다시 묻지 않는다', () => {
    expect(shouldAsk('denied', null, NOW)).toBe(false);
    expect(shouldAsk('denied', NOW - ASK_AGAIN_AFTER_MS * 10, NOW)).toBe(false);
  });

  it('아직 묻지 않았으면 묻는다', () => {
    expect(shouldAsk('default', null, NOW)).toBe(true);
  });

  /**
   * 이 테스트가 "push가 안 온다"의 조용한 원인 하나를 막는다.
   *
   * <p>예전에는 "물어봤다"를 영구 플래그로 남겼다. 사용자가 권한 팝업을 거부하지 않고 그냥
   * 닫으면 권한은 `default`로 남고 플래그만 남아, localStorage를 지우기 전까지 다시 묻지
   * 않았다. 구독이 영영 만들어지지 않는다.
   */
  it('닫기만 한 사람에게는 하루 뒤 다시 묻는다', () => {
    expect(shouldAsk('default', NOW - 1_000, NOW)).toBe(false);
    expect(shouldAsk('default', NOW - ASK_AGAIN_AFTER_MS + 1, NOW)).toBe(false);
    expect(shouldAsk('default', NOW - ASK_AGAIN_AFTER_MS, NOW)).toBe(true);
    expect(shouldAsk('default', NOW - ASK_AGAIN_AFTER_MS * 2, NOW)).toBe(true);
  });
});

describe('urlBase64ToUint8Array', () => {
  it('base64url을 패딩과 치환까지 처리해 바이트로 바꾼다', () => {
    // "ab~c" 를 base64url로 인코딩하면 "YWJ-Yw" (패딩 없음, '+' 대신 '-')
    expect(Array.from(urlBase64ToUint8Array('YWJ-Yw'))).toEqual([97, 98, 126, 99]);
  });

  it('applicationServerKey로 넘길 수 있는 길이를 유지한다', () => {
    expect(urlBase64ToUint8Array('YWJj')).toHaveLength(3);
  });
});

describe('toSubscriptionPayload', () => {
  it('endpoint와 두 키만 꺼낸다', () => {
    const subscription = {
      endpoint: 'https://push.example/abc',
      toJSON: () => ({ keys: { p256dh: 'key', auth: 'secret' } }),
    } as unknown as PushSubscription;

    expect(toSubscriptionPayload(subscription)).toEqual({
      endpoint: 'https://push.example/abc',
      p256dh: 'key',
      auth: 'secret',
    });
  });

  /** 키가 없는 구독은 서버가 거절한다. 여기서 undefined를 그대로 보내지 않는다. */
  it('키가 없으면 빈 문자열로 채운다', () => {
    const subscription = {
      endpoint: 'https://push.example/abc',
      toJSON: () => ({}),
    } as unknown as PushSubscription;

    expect(toSubscriptionPayload(subscription)).toEqual({
      endpoint: 'https://push.example/abc',
      p256dh: '',
      auth: '',
    });
  });
});
