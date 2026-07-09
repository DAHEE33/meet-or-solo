import { ReactNode } from 'react';
import BottomTabBar from './BottomTabBar';

interface MobileLayoutProps {
  children: ReactNode;
  /** 상세/로그인 등 탭바가 필요 없는 화면에서 false */
  showTabBar?: boolean;
}

export default function MobileLayout({ children, showTabBar = true }: MobileLayoutProps) {
  return (
    <div className="mx-auto flex min-h-screen w-full max-w-md flex-col bg-sand shadow-xl">
      <div className={showTabBar ? 'flex-1 pb-24' : 'flex-1'}>{children}</div>
      {showTabBar && <BottomTabBar />}
    </div>
  );
}
