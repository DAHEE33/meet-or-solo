import { describe, expect, it } from 'vitest';
import { shouldAsk, toSubscriptionPayload, urlBase64ToUint8Array } from './webPush';

describe('shouldAsk', () => {
  /** 이미 허용했으면 다시 묻지 않고 바로 구독한다. */
  it('허용된 상태면 언제나 진행한다', () => {
    expect(shouldAsk('granted', true)).toBe(true);
    expect(shouldAsk('granted', false)).toBe(true);
  });

  /** 브라우저는 한 번 거절하면 다시 묻지 않는다. 시도해도 권한 창이 뜨지 않는다. */
  it('거절된 상태면 다시 묻지 않는다', () => {
    expect(shouldAsk('denied', false)).toBe(false);
  });

  it('아직 묻지 않았을 때만 한 번 묻는다', () => {
    expect(shouldAsk('default', false)).toBe(true);
    expect(shouldAsk('default', true)).toBe(false);
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
