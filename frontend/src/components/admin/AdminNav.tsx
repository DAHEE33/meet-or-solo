import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { adminSafetyAlertsApi } from '../../api/adminSafetyAlerts';

const MENU_ITEMS = [
  { to: '/admin', label: '대시보드' },
  { to: '/admin/reports', label: '신고 관리' },
  { to: '/admin/members', label: '회원 관리' },
  { to: '/admin/meeting-points', label: '만남 장소 관리' },
] as const;

/** 관리자 화면 4곳(대시보드/신고/회원/만남 장소)에 공통으로 쓰는 상단 메뉴바. */
export default function AdminNav() {
  const location = useLocation();
  const openSafetyAlertCount = useOpenSafetyAlertCount();
  return (
    <AdminNavContent
      pathname={location.pathname}
      openSafetyAlertCount={openSafetyAlertCount}
    />
  );
}

/**
 * 미확인 안전 알림 수. 조회에 실패하면 badge를 표시하지 않는다.
 * 알림 수는 부가 정보이므로 관리자 화면 자체를 막지 않는다.
 */
function useOpenSafetyAlertCount(): number {
  const [count, setCount] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    adminSafetyAlertsApi.list('OPEN', null, 1, controller.signal)
      .then((page) => {
        if (!controller.signal.aborted) setCount(page.openCount);
      })
      .catch(() => {
        if (!controller.signal.aborted) setCount(0);
      });
    return () => controller.abort();
  }, []);
  return count;
}

export function AdminNavContent({
  pathname,
  openSafetyAlertCount = 0,
}: {
  pathname: string;
  openSafetyAlertCount?: number;
}) {
  return (
    <nav aria-label="관리자 메뉴" className="border-t border-line bg-white">
      <ul className="mx-auto flex max-w-6xl gap-5 overflow-x-auto px-6 text-sm font-semibold">
        {MENU_ITEMS.map((item) => {
          const active = pathname === item.to;
          const showBadge = item.to === '/admin/reports' && openSafetyAlertCount > 0;
          return (
            <li key={item.to}>
              <Link
                to={item.to}
                aria-current={active ? 'page' : undefined}
                className={`inline-flex items-center gap-1.5 border-b-2 py-3 whitespace-nowrap ${
                  active ? 'border-coral text-coral' : 'border-transparent text-ink/55'
                }`}
              >
                {item.label}
                {showBadge && (
                  <span
                    className="rounded-full bg-coral px-1.5 py-0.5 text-[11px] font-bold text-white tabular-nums"
                    aria-label={`미확인 안전 알림 ${openSafetyAlertCount}건`}
                  >
                    {openSafetyAlertCount}
                  </span>
                )}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
