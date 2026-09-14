import { useSyncExternalStore } from 'react';
import type { MatchingStateChangedNotification } from '../api/matchingWebSocket';
import { toNotificationMessage, type NotificationMessage } from './notificationMessages';

/**
 * 받은 알림 목록과 읽지 않음 수를 담는다.
 *
 * <p>모듈 수준 store인 이유는 읽는 쪽이 트리 곳곳에 있기 때문이다. 헤더의 종은
 * `MobileLayout` 안, 토스트는 `App` 최상단에 있어 한 Provider로 묶기 어색하다.
 *
 * <p><b>서버에 저장하지 않는다.</b> 1단계는 프론트 전용이라 새로고침하면 목록이 사라진다.
 * 그래서 최근 몇 건만 `localStorage`에 남겨 새로고침을 견디게 한다. 앱을 껐다 켜도 남는
 * 알림함과 푸시는 서버 테이블이 필요해 2·3단계로 미뤘다(`docs/19` 4.11.5).
 */

export type StoredNotification = {
  /** 같은 알림을 두 번 쌓지 않으려는 키. 사유 + 발생 시각 조합이다. */
  id: string;
  reason: string;
  occurredAt: string;
  message: NotificationMessage;
  read: boolean;
};

export const MAX_NOTIFICATIONS = 20;
const STORAGE_KEY = 'meet-or-solo.notifications.v1';

type State = {
  items: StoredNotification[];
  unreadCount: number;
};

let state: State = { items: [], unreadCount: 0 };
let listeners: Array<() => void> = [];

function emit() {
  listeners.forEach((listener) => listener());
}

function persist() {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state.items));
  } catch {
    // 사생활 보호 모드나 저장 용량 초과. 알림 자체는 계속 동작해야 하므로 무시한다.
  }
}

function countUnread(items: StoredNotification[]): number {
  return items.filter((item) => !item.read).length;
}

/** 새로고침 직후 목록을 복원한다. 실패하면 빈 목록으로 시작한다. */
export function restoreNotifications(): void {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return;
    const parsed = JSON.parse(raw) as StoredNotification[];
    if (!Array.isArray(parsed)) return;
    const items = parsed.slice(0, MAX_NOTIFICATIONS);
    state = { items, unreadCount: countUnread(items) };
    emit();
  } catch {
    // 형식이 바뀌었거나 깨진 값. 복원을 포기하고 새로 시작한다.
  }
}

/**
 * 알림을 추가한다. 이미 있는 알림이면 무시하고 `null`을 돌려준다.
 *
 * <p>중복 방지가 필요한 이유는 재연결이다. WebSocket이 끊겼다 붙으면 같은 알림이 다시 올 수
 * 있고, 그때마다 토스트가 뜨면 화면이 시끄러워진다.
 */
export function addNotification(
  notification: MatchingStateChangedNotification,
): StoredNotification | null {
  const id = `${notification.reason}:${notification.occurredAt}`;
  if (state.items.some((item) => item.id === id)) return null;

  const added: StoredNotification = {
    id,
    reason: notification.reason,
    occurredAt: notification.occurredAt,
    message: toNotificationMessage(notification),
    read: false,
  };
  const items = [added, ...state.items].slice(0, MAX_NOTIFICATIONS);
  state = { items, unreadCount: countUnread(items) };
  persist();
  emit();
  return added;
}

export function markAllRead(): void {
  if (state.unreadCount === 0) return;
  const items = state.items.map((item) => ({ ...item, read: true }));
  state = { items, unreadCount: 0 };
  persist();
  emit();
}

export function clearNotifications(): void {
  state = { items: [], unreadCount: 0 };
  persist();
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
  state = { items: [], unreadCount: 0 };
  listeners = [];
}
