// 회원 알림함 데이터 접근 계층(docs/32 3.3).
//
// 1단계는 받은 알림을 localStorage에만 남겨서 기기마다 목록이 다르고 로그인해도 복원되지
// 않았다. 2단계에서 서버가 목록을 갖고, 이 파일이 그 입구다.

import { apiClient } from './apiClient';

export type ServerNotification = {
  notificationId: number;
  /** MatchingStateChangedEvent의 reason. 문구와 이동 경로는 프론트가 정한다. */
  reason: string;
  occurredAt: string;
  read: boolean;
};

export type NotificationList = {
  items: ServerNotification[];
  unreadCount: number;
  /** 보관 정책. 화면 안내 문구를 서버 값으로 쓴다. */
  retentionDays: number;
  retentionCount: number;
};

export const notificationsApi = {
  list: (size?: number, signal?: AbortSignal) =>
    apiClient<NotificationList>(
      `/api/members/me/notifications${size ? `?size=${size}` : ''}`,
      { signal },
    ),
  /** 목록을 열면 전부 읽음이다. 갱신된 목록이 그대로 돌아온다. */
  markAllRead: (size?: number, signal?: AbortSignal) =>
    apiClient<NotificationList>(
      `/api/members/me/notifications/read${size ? `?size=${size}` : ''}`,
      { method: 'PATCH', signal },
    ),
};
