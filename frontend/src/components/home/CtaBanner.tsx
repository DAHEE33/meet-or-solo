import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';

interface CtaBannerProps {
  to: string;
  title: string;
  description: string;
  /** 'coral' = 매칭, 'teal' = 솔로 코스 */
  tone: 'coral' | 'teal';
  /** 이동 대상 화면에 넘길 route state (예: 현재 축제 festivalId) */
  state?: Record<string, unknown>;
}

const TONE_CLASS: Record<CtaBannerProps['tone'], string> = {
  coral: 'bg-coral text-white',
  teal: 'bg-teal text-white',
};

export default function CtaBanner({ to, title, description, tone, state }: CtaBannerProps) {
  return (
    <Link
      to={to}
      state={state}
      className={`flex items-center justify-between gap-3 rounded-2xl px-5 py-4 active:scale-[0.99] transition-transform ${TONE_CLASS[tone]}`}
    >
      <div className="flex flex-col gap-0.5">
        <span className="text-[15px] font-bold">{title}</span>
        <span className="text-[13px] opacity-85">{description}</span>
      </div>
      <ChevronRight size={20} className="shrink-0 opacity-80" />
    </Link>
  );
}
