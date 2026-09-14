import {
  connectMatchingWebSocket,
  type MatchingStateChangedNotification,
  type MatchingWebSocketCallbacks,
} from './matchingWebSocket';

/**
 * 매칭 WebSocket 연결을 앱 전체에서 <b>하나</b>로 유지한다.
 *
 * <p>예전에는 `useMatchingSession`과 `useMatchRoom`이 각자 `connectMatchingWebSocket`을 불렀다.
 * 화면 안에서 연결을 만들었기 때문에 그 화면을 벗어나면 연결이 끊겼고, 홈이나 마이페이지에서는
 * 아무 알림도 받을 수 없었다. 알림을 앱 전역으로 올리려면 연결도 화면 밖으로 나와야 한다.
 *
 * <p>그렇다고 전역 연결을 하나 더 만들면 매칭 화면에서 소켓이 2개가 되고 같은 알림을 두 번
 * 받는다. 그래서 <b>구독자를 세고</b>, 첫 구독자가 생길 때 연결하고 마지막 구독자가 떠날 때
 * 끊는다. 구독 API는 {@link connectMatchingWebSocket}과 시그니처가 같아서 기존 훅은 주입만
 * 바꾸면 된다.
 *
 * <p>React context가 아니라 모듈 수준 객체인 이유는 구독자가 Provider 바깥에도 있기 때문이다.
 * 헤더의 종은 `MobileLayout` 안에, 훅은 화면 안에, 알림 센터는 App 최상단에 있다.
 */

type Listener = MatchingWebSocketCallbacks;

let listeners: Listener[] = [];
let disconnect: (() => void) | null = null;
let connected = false;

/** 테스트에서 실제 소켓 대신 다른 구현을 끼울 수 있게 분리한다. */
let connectImpl = connectMatchingWebSocket;

function openIfNeeded() {
  if (disconnect) return;
  disconnect = connectImpl({
    onConnected: () => {
      connected = true;
      listeners.forEach((listener) => listener.onConnected());
    },
    onDisconnected: () => {
      connected = false;
      listeners.forEach((listener) => listener.onDisconnected?.());
    },
    onStateChanged: (notification) => {
      listeners.forEach((listener) => listener.onStateChanged(notification));
    },
  });
}

function closeIfIdle() {
  if (listeners.length > 0 || !disconnect) return;
  const close = disconnect;
  disconnect = null;
  connected = false;
  close();
}

/**
 * 매칭 상태 알림을 구독한다. 반환값을 호출하면 구독이 끝난다.
 *
 * <p>이미 연결된 뒤에 구독한 쪽은 `onConnected`를 즉시 한 번 받는다. 구독 시점에 연결이
 * 끝나 있으면 그 신호를 영영 못 받아, 화면이 첫 동기화를 하지 못한 채 폴링만 돌게 된다.
 */
export function subscribeMatchingNotifications(callbacks: Listener): () => void {
  listeners = [...listeners, callbacks];
  openIfNeeded();
  if (connected) callbacks.onConnected();

  let stopped = false;
  return () => {
    if (stopped) return;
    stopped = true;
    listeners = listeners.filter((listener) => listener !== callbacks);
    closeIfIdle();
  };
}

/** 현재 열려 있는 소켓 수(0 또는 1). 중복 연결 회귀를 막는 테스트가 쓴다. */
export function openSocketCount(): number {
  return disconnect ? 1 : 0;
}

/** 테스트 전용. 연결 구현을 바꾸고 허브 상태를 초기화한다. */
export function __setConnectImplForTest(impl: typeof connectMatchingWebSocket | null): void {
  disconnect?.();
  disconnect = null;
  connected = false;
  listeners = [];
  connectImpl = impl ?? connectMatchingWebSocket;
}

export type { MatchingStateChangedNotification };
