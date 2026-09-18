// Web Push 구독 데이터 접근 계층(docs/32 3.4).

import { apiClient, apiClientVoid } from './apiClient';

export type PushSubscriptionPayload = {
  endpoint: string;
  p256dh: string;
  auth: string;
};

export const pushApi = {
  /** 빈 문자열이면 이 환경에 VAPID 키가 없다는 뜻이다. */
  publicKey: () =>
    apiClient<{ publicKey: string }>('/api/members/me/push-subscriptions/public-key'),
  subscribe: (payload: PushSubscriptionPayload) =>
    apiClientVoid('/api/members/me/push-subscriptions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }),
  unsubscribe: (payload: PushSubscriptionPayload) =>
    apiClientVoid('/api/members/me/push-subscriptions', {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }),
};
