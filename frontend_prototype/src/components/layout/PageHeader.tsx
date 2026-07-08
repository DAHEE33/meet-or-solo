import { useNavigate } from 'react-router-dom';
import { ChevronLeft } from 'lucide-react';

interface PageHeaderProps {
  title: string;
  /** 뒤로가기 버튼 숨김 */
  noBack?: boolean;
}

/** 서브 페이지 공통 헤더 (뒤로가기 + 타이틀) */
export default function PageHeader({ title, noBack = false }: PageHeaderProps) {
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
      <h1 className={`text-lg font-bold text-ink ${noBack ? 'px-2' : ''}`}>{title}</h1>
    </header>
  );
}
