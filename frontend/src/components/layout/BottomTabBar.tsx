import { NavLink } from 'react-router-dom';
import { House, Map, HeartHandshake, Route, CircleUserRound } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

interface Tab {
  to: string;
  label: string;
  icon: LucideIcon;
}

const TABS: Tab[] = [
  { to: '/', label: '홈', icon: House },
  { to: '/spots', label: '관광지', icon: Map },
  { to: '/matching', label: '매칭', icon: HeartHandshake },
  { to: '/solo-course', label: '코스', icon: Route },
  { to: '/mypage', label: '마이', icon: CircleUserRound },
];

export default function BottomTabBar() {
  return (
    <nav className="fixed inset-x-0 bottom-0 z-30 mx-auto max-w-md border-t border-line bg-white pb-[env(safe-area-inset-bottom)]">
      <div className="grid grid-cols-5">
        {TABS.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              `flex h-16 flex-col items-center justify-center gap-1 text-[11px] font-medium ${
                isActive ? 'text-coral' : 'text-ink/45'
              }`
            }
          >
            <Icon size={22} strokeWidth={1.8} />
            {label}
          </NavLink>
        ))}
      </div>
    </nav>
  );
}
