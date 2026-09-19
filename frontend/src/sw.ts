/// <reference lib="webworker" />

// Service Worker 본체(docs/32 3.4).
//
// 예전에는 workbox `generateSW`로 service worker를 "생성"만 했다. 생성된 파일에는 우리 코드를
// 넣을 수 없어 `push` 핸들러를 붙일 자리가 없었고, 그래서 앱이 꺼져 있으면 알림이 닿지 않았다.
// `injectManifest`로 바꾸면서 이 파일이 실제 service worker가 된다.
//
// 캐싱 동작은 이전과 같게 유지한다 — precache + navigation fallback, 그리고 /api·/ws 제외.
// OAuth 로그인이 navigation request라 fallback에 걸리면 302 대신 index.html이 돌아가
// 로그인이 조용히 실패한다(이전 vite.config.ts 주석에 남아 있던 사고다).

import { createHandlerBoundToURL, precacheAndRoute } from 'workbox-precaching';
import { NavigationRoute, registerRoute } from 'workbox-routing';
import { toNotificationMessage } from './notifications/notificationMessages';

declare const self: ServiceWorkerGlobalScope;

precacheAndRoute(self.__WB_MANIFEST);

registerRoute(new NavigationRoute(createHandlerBoundToURL('/index.html'), {
  denylist: [/^\/api(\/|$)/, /^\/ws(\/|$)/],
}));

// 새로 설치된 service worker가 기다리지 않고 바로 일하게 한다. registerType: 'autoUpdate'와
// 짝이다 — 이것이 없으면 push 핸들러가 다음 방문에야 적용된다.
self.addEventListener('install', () => {
  void self.skipWaiting();
});
self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

type PushPayload = {
  type?: string;
  reason?: string;
  occurredAt?: string;
};

/** 서버가 보내는 것은 사유뿐이다. 문구와 이동 경로는 화면과 같은 매핑을 쓴다. */
function readPayload(event: PushEvent): PushPayload | null {
  if (!event.data) return null;
  try {
    return event.data.json() as PushPayload;
  } catch {
    return null;
  }
}

/**
 * 화면을 보고 있는 탭이 있는지.
 *
 * <p>앱이 열려 있으면 WebSocket이 같은 알림을 이미 전달했고 `NotificationCenter`가 화면에
 * 띄운다. 거기에 push까지 OS 알림을 올리면 <b>같은 일이 두 번</b> 보인다 — 앱 안에 하나,
 * 알림 센터에 하나. 게다가 OS 알림은 탭하기 전까지 알림함에 남아 "알림이 안 사라진다"가 된다.
 */
async function hasVisibleClient(): Promise<boolean> {
  const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
  return windows.some((client) => client.visibilityState === 'visible');
}

self.addEventListener('push', (event) => {
  const payload = readPayload(event);
  if (!payload?.reason) return;
  const message = toNotificationMessage({ reason: payload.reason });
  event.waitUntil((async () => {
    /*
      보고 있는 탭이 있으면 OS 알림을 띄우지 않는다.

      `userVisibleOnly: true`로 구독했으므로 원칙적으로 push마다 알림을 띄워야 하지만,
      브라우저는 그 origin의 탭이 화면에 보이는 동안에는 이 요구를 강제하지 않는다. 앱이
      이미 같은 내용을 보여주고 있기 때문이다.
    */
    if (await hasVisibleClient()) return;
    await self.registration.showNotification(message.title, {
      body: message.body,
      // 같은 사유의 알림이 여러 번 오면 쌓지 않고 최신 것으로 덮는다.
      tag: payload.reason,
      renotify: true,
      data: { path: message.path },
    } as NotificationOptions);
  })());
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const path = (event.notification.data as { path?: string } | undefined)?.path ?? '/';
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    // 이미 열려 있는 탭이 있으면 그 탭을 쓴다. 알림을 누를 때마다 새 탭이 생기면 안 된다.
    for (const client of windows) {
      if ('focus' in client) {
        await client.focus();
        if ('navigate' in client) await client.navigate(path);
        return;
      }
    }
    await self.clients.openWindow(path);
  })());
});
