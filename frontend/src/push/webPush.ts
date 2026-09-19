// 브라우저 Web Push 구독(docs/32 3.4).
//
// 앱이 꺼져 있어도 알림이 닿아야 하는 이유는 MATCH_PROPOSED다. 응답 시간이 30초이고 놓치면
// penalty_score +1에 쿨타임 2분이 붙는다. WebSocket은 화면이 열려 있을 때만 닿는다.

import { pushApi } from '../api/push';

/** 마지막으로 권한을 물어본 시각(epoch ms). 매번 다시 묻지 않기 위해서다. */
const ASKED_KEY = 'meet-or-solo.push.asked.v1';

/**
 * 한 번 묻고 나서 다시 묻기까지 기다리는 시간.
 *
 * <p><b>"물어봤다"를 영구 기록하면 안 된다.</b> 예전에는 `requestPermission()` 앞에서 플래그를
 * 세웠는데, 사용자가 팝업을 거부하지 않고 <b>그냥 닫으면</b> 권한은 `default`로 남고 플래그만
 * 남는다. 그러면 `shouldAsk`가 영영 false가 되어 localStorage를 지우기 전까지 구독이 만들어질
 * 수 없었다 — push가 안 오는 조용한 원인 하나였다.
 *
 * <p>거부(`denied`)는 브라우저가 기억하므로 이 값과 무관하게 다시 묻지 않는다. 이 유예는
 * "닫았을 뿐 아직 정하지 않은" 사람에게만 적용된다.
 */
export const ASK_AGAIN_AFTER_MS = 24 * 60 * 60 * 1_000;

export type PushEnableResult =
  /** 구독까지 끝났다. */
  | 'SUBSCRIBED'
  /** 이 브라우저가 push를 지원하지 않는다(iOS Safari에서 홈 화면에 설치하지 않은 경우 포함). */
  | 'UNSUPPORTED'
  /** 서버에 VAPID 키가 없다. 이 환경에서는 push를 쓰지 않는다. */
  | 'NOT_CONFIGURED'
  /** 사용자가 거절했거나 이미 거절한 상태다. */
  | 'DENIED'
  /** 이번에는 묻지 않기로 했다(이미 물어봤고 아직 허용하지 않음). */
  | 'SKIPPED'
  /** 등록 중 오류. 알림 외 기능에는 영향이 없다. */
  | 'FAILED';

export function isPushSupported(): boolean {
  return typeof window !== 'undefined'
    && 'serviceWorker' in navigator
    && 'PushManager' in window
    && 'Notification' in window;
}

/**
 * 권한을 물어볼 시점인지.
 *
 * <p>이미 허용했으면 다시 묻지 않고 바로 구독한다. 거절했으면 브라우저가 다시 묻지 않으므로
 * 시도할 이유가 없다. 아직 정하지 않았으면 마지막으로 물어본 지 {@link ASK_AGAIN_AFTER_MS}가
 * 지났을 때만 다시 묻는다 — 매칭 신청 직전이 그 자리다.
 *
 * @param lastAskedAt 마지막으로 물어본 시각(epoch ms). 물어본 적이 없으면 `null`
 */
export function shouldAsk(
  permission: NotificationPermission,
  lastAskedAt: number | null,
  now: number = Date.now(),
): boolean {
  if (permission === 'granted') return true;
  if (permission === 'denied') return false;
  if (lastAskedAt === null) return true;
  return now - lastAskedAt >= ASK_AGAIN_AFTER_MS;
}

function markAsked(now: number = Date.now()): void {
  try {
    window.localStorage.setItem(ASKED_KEY, String(now));
  } catch {
    // 사생활 보호 모드. 다음에 한 번 더 물어보게 되는 정도라 무시한다.
  }
}

function lastAskedAt(): number | null {
  try {
    const stored = window.localStorage.getItem(ASKED_KEY);
    if (stored === null) return null;
    // v1 초기에는 '1'만 적었다. 시각을 모르는 값은 "물어본 적 없음"으로 보고 다시 묻는다 —
    // 그 형식으로 저장된 사람들이 바로 영구 차단에 걸려 있던 쪽이다.
    const parsed = Number(stored);
    return Number.isFinite(parsed) && parsed > 1 ? parsed : null;
  } catch {
    return null;
  }
}

/**
 * base64url VAPID 공개키를 브라우저가 요구하는 바이트 배열로 바꾼다.
 *
 * `ArrayBuffer`를 명시해 만드는 이유는 `applicationServerKey`가 `SharedArrayBuffer`를 받지
 * 않기 때문이다. 기본 `new Uint8Array(length)`의 타입은 둘 중 무엇이든 될 수 있어 거절된다.
 */
export function urlBase64ToUint8Array(base64: string): Uint8Array<ArrayBuffer> {
  const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
  const normalized = padded.replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(normalized);
  const output = new Uint8Array(new ArrayBuffer(raw.length));
  for (let index = 0; index < raw.length; index += 1) {
    output[index] = raw.charCodeAt(index);
  }
  return output;
}

/** 브라우저 구독 객체에서 서버가 필요로 하는 세 값을 꺼낸다. */
export function toSubscriptionPayload(subscription: PushSubscription) {
  const json = subscription.toJSON();
  return {
    endpoint: subscription.endpoint,
    p256dh: json.keys?.p256dh ?? '',
    auth: json.keys?.auth ?? '',
  };
}

/**
 * 권한을 묻고 구독한다.
 *
 * <p>실패해도 던지지 않는다. push는 알림의 세 번째 경로이고, 여기서 막히면 매칭 신청 자체가
 * 막히는 것처럼 보이면 안 된다.
 */
export async function enablePush(): Promise<PushEnableResult> {
  if (!isPushSupported()) return 'UNSUPPORTED';
  if (!shouldAsk(Notification.permission, lastAskedAt())) {
    return Notification.permission === 'denied' ? 'DENIED' : 'SKIPPED';
  }

  try {
    const { publicKey } = await pushApi.publicKey();
    // 키가 없는 환경이다. 권한을 묻기 전에 확인한다 — 물어봐 놓고 아무것도 못 하면 안 된다.
    if (!publicKey) return 'NOT_CONFIGURED';

    if (Notification.permission !== 'granted') {
      markAsked();
      const permission = await Notification.requestPermission();
      if (permission !== 'granted') return 'DENIED';
    }

    const registration = await navigator.serviceWorker.ready;
    const existing = await registration.pushManager.getSubscription();
    const subscription = existing ?? await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey),
    });
    await pushApi.subscribe(toSubscriptionPayload(subscription));
    return 'SUBSCRIBED';
  } catch {
    return 'FAILED';
  }
}
