import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';

const MATCHING_DESTINATION = '/user/queue/matching';

export type MatchingStateChangedNotification = {
  type: 'MATCHING_STATE_CHANGED';
  reason: string;
  occurredAt: string;
};

export type MatchingWebSocketCallbacks = {
  onConnected: () => void;
  onDisconnected?: () => void;
  onStateChanged: (notification: MatchingStateChangedNotification) => void;
};

export function connectMatchingWebSocket({
  onConnected,
  onDisconnected,
  onStateChanged,
}: MatchingWebSocketCallbacks): () => void {
  let subscription: StompSubscription | null = null;
  const client = new Client({
    brokerURL: buildMatchingWebSocketUrl(window.location),
    reconnectDelay: 5_000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    onConnect: () => {
      subscription = client.subscribe(MATCHING_DESTINATION, (message) => {
        const notification = parseMatchingNotification(message);
        if (notification) onStateChanged(notification);
      });
      onConnected();
    },
    onWebSocketError: () => {
      onDisconnected?.();
      // REST polling이 연결 실패와 재접속 구간의 fallback을 담당한다.
    },
    onWebSocketClose: () => {
      onDisconnected?.();
    },
    onStompError: () => {
      // broker 오류를 화면 상태로 사용하지 않고 reconnect와 REST 복원에 맡긴다.
    },
  });

  client.activate();

  return () => {
    subscription?.unsubscribe();
    subscription = null;
    void client.deactivate();
  };
}

export function buildMatchingWebSocketUrl(
  location: Pick<Location, 'protocol' | 'host'>,
): string {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${location.host}/ws`;
}

export function parseMatchingNotification(
  message: Pick<IMessage, 'body'>,
): MatchingStateChangedNotification | null {
  try {
    const value = JSON.parse(message.body) as Partial<MatchingStateChangedNotification>;
    if (
      value.type !== 'MATCHING_STATE_CHANGED'
      || typeof value.reason !== 'string'
      || typeof value.occurredAt !== 'string'
    ) {
      return null;
    }
    return value as MatchingStateChangedNotification;
  } catch {
    return null;
  }
}
