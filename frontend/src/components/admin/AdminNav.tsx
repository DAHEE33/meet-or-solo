import { Link, useLocation } from 'react-router-dom';

const MENU_ITEMS = [
  { to: '/admin', label: '대시보드' },
  { to: '/admin/reports', label: '신고 관리' },
  { to: '/admin/members', label: '회원 관리' },
  { to: '/admin/meeting-points', label: '만남 장소 관리' },
] as const;

/** 관리자 화면 4곳(대시보드/신고/회원/만남 장소)에 공통으로 쓰는 상단 메뉴바. */
export default function AdminNav() {
  const location = useLocation();
  return <AdminNavContent pathname={location.pathname} />;
}

export function AdminNavContent({ pathname }: { pathname: string }) {
  return (
    <nav aria-label="관리자 메뉴" className="border-t border-line bg-white">
      <ul className="mx-auto flex max-w-6xl gap-5 overflow-x-auto px-6 text-sm font-semibold">
        {MENU_ITEMS.map((item) => {
          const active = pathname === item.to;
          return (
            <li key={item.to}>
              <Link
                to={item.to}
                aria-current={active ? 'page' : undefined}
                className={`inline-block border-b-2 py-3 whitespace-nowrap ${
                  active ? 'border-coral text-coral' : 'border-transparent text-ink/55'
                }`}
              >
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
