import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { subscribeMatchingNotifications } from '../../api/matchingNotificationHub';
import { isAlreadyVisible } from '../../notifications/notificationMessages';
import {
  addNotification,
  syncNotifications,
  type StoredNotification,
} from '../../notifications/notificationStore';

/** 알림 소켓을 열지 않는 경로. 로그인 전에는 인증이 없어 연결이 계속 실패한다. */
const ANONYMOUS_PATHS = ['/login', '/signup'];

/** INFO 토스트가 화면에 머무는 시간. */
export const TOAST_VISIBLE_MS = 5_000;

export function shouldConnect(pathname: string): boolean {
  return !ANONYMOUS_PATHS.some((path) => pathname === path || pathname.startsWith(`${path}/`));
}

/**
 * 앱 어디에 있든 매칭 알림을 받아 띄운다({@code docs/19} 4.11.5 1단계).
 *
 * <p>예전에는 `/matching`과 `/match-room` 두 화면만 WebSocket을 구독했다. 홈이나 마이페이지에
 * 있으면 매칭이 성사돼도 아무것도 뜨지 않았고, 특히 응답 시간이 30초인 매칭 제안을 그대로
 * 놓쳤다. 이 컴포넌트를 `App` 최상단에 한 번 두어 연결을 화면 밖으로 꺼낸다.
 *
 * <p>소켓은 `matchingNotificationHub`가 하나만 유지한다. 여기서 새로 연결을 만들면 매칭 화면에서
 * 소켓이 2개가 되어 같은 알림을 두 번 받는다.
 */
export default function NotificationCenter() {
  const location = useLocation();
  const navigate = useNavigate();
  const [toast, setToast] = useState<StoredNotification | null>(null);
  const [banner, setBanner] = useState<StoredNotification | null>(null);

  useEffect(() => {
    if (!shouldConnect(location.pathname)) return undefined;
    return subscribeMatchingNotifications({
      /*
        연결이 붙는 시점에 서버 알림함을 받아온다(docs/32 3.3).

        mount 시점이 아니라 여기인 이유는 로그인 때문이다. 이 컴포넌트는 App 최상단에 있어
        로그인 화면에서도 마운트돼 있고, 그때 조회하면 인증이 없어 실패한 채로 끝난다.
        소켓은 인증이 있어야 붙으므로 `onConnected`가 "지금 받아올 수 있다"는 신호다.

        재연결에도 다시 받아오는 것이 맞다. 끊겨 있는 동안 온 알림이 그때 채워진다.
      */
      onConnected: () => {
        void syncNotifications();
      },
      onStateChanged: (notification) => {
        const added = addNotification(notification);
        // 재연결로 같은 알림이 다시 온 경우다. 목록에도 화면에도 다시 올리지 않는다.
        if (!added) return;
        if (added.message.level === 'URGENT') {
          setBanner(added);
          return;
        }
        // 이미 그 화면을 보고 있으면 토스트를 띄우지 않는다. 상태방에서 도착 알림이
        // 화면 갱신과 토스트로 두 번 보이는 것을 막는다.
        if (isAlreadyVisible(added.message, location.pathname)) return;
        setToast(added);
      },
    });
  }, [location.pathname]);

  useEffect(() => {
    if (!toast) return undefined;
    const timer = window.setTimeout(() => setToast(null), TOAST_VISIBLE_MS);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const open = (notification: StoredNotification) => {
    setBanner(null);
    setToast(null);
    navigate(notification.message.path);
  };

  return (
    <>
      {banner && (
        <div
          role="alert"
          aria-live="assertive"
          className="fixed left-1/2 top-4 z-50 w-[calc(100%-2.5rem)] max-w-[390px] -translate-x-1/2 rounded-2xl bg-coral px-4 py-3 text-white shadow-lg"
        >
          <button
            type="button"
            onClick={() => open(banner)}
            className="block w-full text-left"
          >
            <p className="text-[15px] font-bold">{banner.message.title}</p>
            {banner.message.body && (
              <p className="mt-0.5 text-[13px] opacity-90">{banner.message.body}</p>
            )}
          </button>
          <button
            type="button"
            aria-label="알림 닫기"
            onClick={() => setBanner(null)}
            className="absolute right-3 top-3 text-[12px] font-semibold opacity-80"
          >
            닫기
          </button>
        </div>
      )}

      {toast && (
        <button
          type="button"
          onClick={() => open(toast)}
          role="status"
          aria-live="polite"
          className="fixed bottom-24 left-1/2 z-40 w-[calc(100%-2.5rem)] max-w-[390px] -translate-x-1/2 rounded-2xl bg-ink px-4 py-3 text-left text-white shadow-lg"
        >
          <span className="block text-[14px] font-semibold">{toast.message.title}</span>
          {toast.message.body && (
            <span className="mt-0.5 block text-[12px] opacity-80">{toast.message.body}</span>
          )}
        </button>
      )}
    </>
  );
}
