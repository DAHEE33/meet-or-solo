import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { subscribeMatchingNotifications } from '../../api/matchingNotificationHub';
import { TOP_NOTICE_POSITION } from '../common/TopNotice';
import {
  NOTICE_VISIBLE_MS,
  isAlreadyVisible,
} from '../../notifications/notificationMessages';
import {
  addNotification,
  syncNotifications,
  type StoredNotification,
} from '../../notifications/notificationStore';

/** 알림 소켓을 열지 않는 경로. 로그인 전에는 인증이 없어 연결이 계속 실패한다. */
const ANONYMOUS_PATHS = ['/login', '/signup'];

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
 *
 * <p><b>알림 자리는 하나다.</b> 예전에는 긴급 알림이 위(배너), 일반 알림이 아래(토스트)로
 * 갈려서 같은 순간에 위아래 둘 다 떠 있을 수 있었고, 알림 종류가 나뉜 것처럼 보였다. 지금은
 * 한 자리에 최신 한 건만 띄우고 긴급도는 색과 머무는 시간으로만 구분한다. 밀려난 알림은
 * 사라지는 것이 아니라 헤더의 종에 쌓인다.
 */
export default function NotificationCenter() {
  const location = useLocation();
  const navigate = useNavigate();
  const [notice, setNotice] = useState<StoredNotification | null>(null);

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
        // 이미 그 화면을 보고 있으면 띄우지 않는다. 상태방에서 도착 알림이 화면 갱신과
        // 알림으로 두 번 보이는 것을 막는다.
        if (isAlreadyVisible(added.message, location.pathname)) return;
        setNotice(added);
      },
    });
  }, [location.pathname]);

  /*
    저절로 사라진다. 긴급 알림에 타이머가 없던 것이 "알림이 안 사라진다"의 원인이었다 —
    닫기를 누르기 전까지 남았고, 화면을 옮겨도 따라다녔고, 응답 시간 30초가 지나 이미 끝난
    제안의 배너가 그대로 떠 있었다.
  */
  useEffect(() => {
    if (!notice) return undefined;
    const timer = window.setTimeout(
      () => setNotice(null),
      NOTICE_VISIBLE_MS[notice.message.level],
    );
    return () => window.clearTimeout(timer);
  }, [notice]);

  /*
    알림이 가리키는 화면에 도착하면 내린다. 알림을 누르지 않고 직접 그 화면으로 간 경우다.
    "가서 보라"는 신호가 이미 보고 있는 화면 위에 남아 있을 이유가 없다.
  */
  useEffect(() => {
    if (notice && isAlreadyVisible(notice.message, location.pathname)) setNotice(null);
  }, [location.pathname, notice]);

  if (!notice) return null;

  const urgent = notice.message.level === 'URGENT';

  return (
    <div
      role={urgent ? 'alert' : 'status'}
      aria-live={urgent ? 'assertive' : 'polite'}
      className={`${TOP_NOTICE_POSITION} z-50 text-white ${urgent ? 'bg-coral' : 'bg-ink'}`}
    >
      <button
        type="button"
        onClick={() => {
          setNotice(null);
          navigate(notice.message.path);
        }}
        className="block w-full pr-10 text-left"
      >
        <p className={urgent ? 'text-[15px] font-bold' : 'text-[14px] font-semibold'}>
          {notice.message.title}
        </p>
        {notice.message.body && (
          <p className="mt-0.5 text-[13px] opacity-90">{notice.message.body}</p>
        )}
      </button>
      <button
        type="button"
        aria-label="알림 닫기"
        onClick={() => setNotice(null)}
        className="absolute right-3 top-3 text-[12px] font-semibold opacity-80"
      >
        닫기
      </button>
    </div>
  );
}
