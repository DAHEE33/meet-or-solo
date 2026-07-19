import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';

interface FestivalMatchingBannerProps {
  festivalName: string;
}

/** 홈 "선택한 축제에서 매칭 시작" coral 배너 — 대표(선택) 축제 선택 상태에 종속 */
export default function FestivalMatchingBanner({ festivalName }: FestivalMatchingBannerProps) {
  return (
    <Link
      to="/matching"
      className="flex items-center justify-between gap-3 rounded-2xl bg-coral px-5 py-4 text-white active:scale-[0.99] transition-transform"
    >
      <div className="flex min-w-0 flex-col gap-0.5">
        <span className="text-[11px] font-semibold opacity-75">선택한 축제 기준</span>
        <span className="truncate text-[15px] font-bold">{festivalName} 함께 갈 사람 찾기</span>
        <span className="text-[13px] opacity-85">같은 축제에 가는 여행자와 동행을 만들어보세요</span>
      </div>
      <span className="flex shrink-0 items-center gap-0.5 rounded-full bg-white px-3.5 py-2 text-[13px] font-bold text-coral">
        매칭 시작
        <ChevronRight size={14} />
      </span>
    </Link>
  );
}
