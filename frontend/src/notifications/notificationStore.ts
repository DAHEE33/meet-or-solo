import { useSyncExternalStore } from 'react';
import type { MatchingStateChangedNotification } from '../api/matchingWebSocket';
import { notificationsApi, type NotificationList } from '../api/notifications';
import { toNotificationMessage, type NotificationMessage } from './notificationMessages';

/**
 * 받은 알림 목록과 읽지 않음 수를 담는다.
 *
 * <p>모듈 수준 store인 이유는 읽는 쪽이 트리 곳곳에 있기 때문이다. 헤더의 종은
 * `MobileLayout` 안, 토스트는 `App` 최상단에 있어 한 Provider로 묶기 어색하다.
 *
 * <p><b>2단계부터 서버가 목록을 갖는다</b>(`docs/32` 3.3). 1단계는 `localStorage`에 최근 20건만
 * 남겨서 기기마다 목록이 다르고, 저장소를 지우면 사라지고, 로그인해도 못 본 알림이 복원되지
 * 않았다. 지금은 로그인 직후 서버 목록을 받아오고 읽음도 서버에 기록한다.
 *
 * <p>그래도 store가 두 종류를 함께 들고 있다. 서버에 남는 알림(`persisted`)과, 만남이
 * 진행되는 동안만 의미가 있어 저장하지 않는 알림(도착·예정 시간 선택 등)이다. 후자는 이번
 * 세션에서만 보이고 새로고침하면 사라지는 것이 맞다 — 지나고 나서 다시 볼 값이 아니다.
 */

export type StoredNotification = {
  /** 같은 알림을 두 번 쌓지 않으려는 키. 사유 + 발생 시각 조합이다. */
  id: string;
  reason: string;
  occurredAt: string;
  message: NotificationMessage;
  read: boolean;
  /** 서버 알림함에 남아 있는 알림인지. 세션 전용 알림과 구분한다. */
  persisted: boolean;
};

export const MAX_NOTIFICATIONS = 20;

export type NotificationRetention = {
  days: number;
  count: number;
};

type State = {
  items: StoredNotification[];
  unreadCount: number;
  /** 서버가 알려준 보관 정책. 목록을 한 번도 못 받았으면 null이다. */
  retention: NotificationRetention | null;
};

let state: State = { items: [], unreadCount: 0, retention: null };
let listeners: Array<() => void> = [];

function emit() {
  listeners.forEach((listener) => listener());
}

function countUnread(items: StoredNotification[]): number {
  return items.filter((item) => !item.read).length;
}

/** 서버 알림과 세션 알림을 같은 기준으로 맞춰야 중복이 생기지 않는다. */
function keyOf(reason: string, occurredAt: string): string {
  return `${reason}:${occurredAt}`;
}

function sortByOccurredAtDesc(items: StoredNotification[]): StoredNotification[] {
  return [...items].sort((left, right) => right.occurredAt.localeCompare(left.occurredAt));
}

/**
 * 알림을 추가한다. 이미 있는 알림이면 무시하고 `null`을 돌려준다.
 *
 * <p>중복 방지가 필요한 이유는 재연결이다. WebSocket이 끊겼다 붙으면 같은 알림이 다시 올 수
 * 있고, 그때마다 토스트가 뜨면 화면이 시끄러워진다. 서버 목록과 겹치는 경우도 같은 키로
 * 걸러진다.
 */
export function addNotification(
  notification: MatchingStateChangedNotification,
): StoredNotification | null {
  const id = keyOf(notification.reason, notification.occurredAt);
  if (state.items.some((item) => item.id === id)) return null;

  const added: StoredNotification = {
    id,
    reason: notification.reason,
    occurredAt: notification.occurredAt,
    message: toNotificationMessage(notification),
    read: false,
    // 서버에 남는지는 이 시점에 알 수 없다. 다음 동기화에서 서버 목록에 있으면 true가 된다.
    persisted: false,
  };
  const items = [added, ...state.items].slice(0, MAX_NOTIFICATIONS);
  state = { ...state, items, unreadCount: countUnread(items) };
  emit();
  return added;
}

/**
 * 서버 목록을 반영한다.
 *
 * <p>서버 알림이 우선이다 — 읽음 여부의 진실은 서버에 있다. 서버에 없는 세션 알림은 남겨
 * 두되, 서버가 같은 알림을 갖고 있으면 서버 쪽으로 대체한다.
 */
export function applyServerNotifications(list: NotificationList): void {
  const fromServer: StoredNotification[] = list.items.map((item) => ({
    id: keyOf(item.reason, item.occurredAt),
    reason: item.reason,
    occurredAt: item.occurredAt,
    message: toNotificationMessage({ reason: item.reason }),
    read: item.read,
    persisted: true,
  }));
  const serverIds = new Set(fromServer.map((item) => item.id));
  const sessionOnly = state.items.filter((item) => !item.persisted && !serverIds.has(item.id));
  const items = sortByOccurredAtDesc([...fromServer, ...sessionOnly]).slice(0, MAX_NOTIFICATIONS);
  state = {
    items,
    unreadCount: countUnread(items),
    retention: { days: list.retentionDays, count: list.retentionCount },
  };
  emit();
}

/**
 * 서버 알림함을 불러온다.
 *
 * <p>실패를 삼킨다. 로그인 전이거나 네트워크가 끊긴 상태에서 목록을 못 받는 것은 정상 경로고,
 * 그때도 실시간 알림은 그대로 동작해야 한다.
 */
export async function syncNotifications(): Promise<void> {
  try {
    applyServerNotifications(await notificationsApi.list(MAX_NOTIFICATIONS));
  } catch {
    // 알림함을 못 받아도 화면은 세션 알림으로 계속 동작한다.
  }
}

/** 화면에서만 읽음으로 바꾼다. 서버 기록은 {@link markAllReadEverywhere}가 한다. */
export function markAllRead(): void {
  if (state.unreadCount === 0) return;
  const items = state.items.map((item) => ({ ...item, read: true }));
  state = { ...state, items, unreadCount: 0 };
  emit();
}

/**
 * 목록을 열었을 때의 읽음 처리.
 *
 * <p>화면을 먼저 바꾸고 서버에 기록한다. 종을 눌렀는데 뱃지가 요청이 끝날 때까지 남아 있으면
 * 눌리지 않은 것처럼 보인다. 서버 기록이 실패해도 다음 동기화에서 다시 맞춰진다.
 */
export async function markAllReadEverywhere(): Promise<void> {
  markAllRead();
  try {
    applyServerNotifications(await notificationsApi.markAllRead(MAX_NOTIFICATIONS));
  } catch {
    // 다음 동기화에서 서버 상태로 다시 맞춘다.
  }
}

export function clearNotifications(): void {
  state = { items: [], unreadCount: 0, retention: state.retention };
  emit();
}

export function getNotificationState(): State {
  return state;
}

function subscribe(listener: () => void): () => void {
  listeners = [...listeners, listener];
  return () => {
    listeners = listeners.filter((registered) => registered !== listener);
  };
}

/** 헤더의 종과 알림 목록이 쓰는 구독 훅이다. */
export function useNotifications(): State {
  return useSyncExternalStore(subscribe, getNotificationState, getNotificationState);
}

/** 테스트 전용. store를 비운다. */
export function __resetNotificationsForTest(): void {
  state = { items: [], unreadCount: 0, retention: null };
  listeners = [];
}
