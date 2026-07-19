import { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronLeft } from 'lucide-react';

interface PageHeaderProps {
  title: string;
  /** 뒤로가기 버튼 숨김 */
  noBack?: boolean;
  /** 타이틀 우측에 표시할 액션 버튼 (예: 공유) */
  rightAction?: ReactNode;
}

/** 서브 페이지 공통 헤더 (뒤로가기 + 타이틀 + 선택적 우측 액션) */
export default function PageHeader({ title, noBack = false, rightAction }: PageHeaderProps) {
  const navigate = useNavigate();
  return (
    <header className="sticky top-0 z-20 flex items-center gap-1 bg-sand/90 px-3 pb-2 pt-4 backdrop-blur">
      {!noBack && (
        <button
          type="button"
          aria-label="뒤로가기"
          onClick={() => navigate(-1)}
          className="flex h-11 w-11 items-center justify-center rounded-full text-ink active:bg-black/5"
        >
          <ChevronLeft size={24} strokeWidth={1.8} />
        </button>
      )}
      <h1 className={`flex-1 truncate text-lg font-bold text-ink ${noBack ? 'px-2' : ''}`}>{title}</h1>
      {rightAction}
    </header>
  );
}
