import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { adminSafetyAlertsApi } from '../../api/adminSafetyAlerts';
import { adminInquiriesApi, EMPTY_ADMIN_INQUIRY_FILTERS } from '../../api/adminInquiries';

const MENU_ITEMS = [
  { to: '/admin', label: '대시보드' },
  { to: '/admin/reports', label: '신고 관리' },
  { to: '/admin/members', label: '회원 관리' },
  { to: '/admin/meeting-points', label: '만남 장소 관리' },
  { to: '/admin/inquiries', label: '문의 관리' },
] as const;

/** 관리자 화면 5곳(대시보드/신고/회원/만남 장소/문의)에 공통으로 쓰는 상단 메뉴바. */
export default function AdminNav() {
  const location = useLocation();
  const openSafetyAlertCount = useOpenSafetyAlertCount();
  const openInquiryCount = useOpenInquiryCount();
  return (
    <AdminNavContent
      pathname={location.pathname}
      openSafetyAlertCount={openSafetyAlertCount}
      openInquiryCount={openInquiryCount}
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

/**
 * 미처리 문의 수. 조회에 실패하면 badge를 표시하지 않는다.
 * 안전 알림 badge와 같은 방식이며, 관리자 화면 자체를 막지 않는다.
 */
function useOpenInquiryCount(): number {
  const [count, setCount] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    adminInquiriesApi.list(EMPTY_ADMIN_INQUIRY_FILTERS, null, 1, controller.signal)
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

/**
 * 메뉴별 badge. 0건이면 badge를 붙이지 않는다.
 * 라벨을 여기서 함께 정하므로 스크린리더 문구와 숫자가 어긋나지 않는다.
 */
export function badgeOf(
  to: string,
  openSafetyAlertCount: number,
  openInquiryCount: number,
): { count: number; label: string } | null {
  if (to === '/admin/reports' && openSafetyAlertCount > 0) {
    return { count: openSafetyAlertCount, label: `미확인 안전 알림 ${openSafetyAlertCount}건` };
  }
  if (to === '/admin/inquiries' && openInquiryCount > 0) {
    return { count: openInquiryCount, label: `미처리 문의 ${openInquiryCount}건` };
  }
  return null;
}

export function AdminNavContent({
  pathname,
  openSafetyAlertCount = 0,
  openInquiryCount = 0,
}: {
  pathname: string;
  openSafetyAlertCount?: number;
  openInquiryCount?: number;
}) {
  return (
    <nav aria-label="관리자 메뉴" className="border-t border-line bg-white">
      <ul className="mx-auto flex max-w-6xl gap-5 overflow-x-auto px-6 text-sm font-semibold">
        {MENU_ITEMS.map((item) => {
          const active = pathname === item.to;
          const badge = badgeOf(item.to, openSafetyAlertCount, openInquiryCount);
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
                {badge && (
                  <span
                    className="rounded-full bg-coral px-1.5 py-0.5 text-[11px] font-bold text-white tabular-nums"
                    aria-label={badge.label}
                  >
                    {badge.count}
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
