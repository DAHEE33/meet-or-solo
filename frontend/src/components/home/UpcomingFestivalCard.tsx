import { Link } from 'react-router-dom';
import type { Festival } from '../../types';
import ImagePlaceholder from '../common/ImagePlaceholder';

interface UpcomingFestivalCardProps {
  festival: Festival;
}

/** 홈 "곧 시작하는 축제" 가로 스크롤 카드 */
export default function UpcomingFestivalCard({ festival }: UpcomingFestivalCardProps) {
  return (
    <Link
      to={`/festivals/${festival.id}`}
      className="flex w-[150px] shrink-0 flex-col overflow-hidden rounded-2xl bg-white shadow-[0_1px_8px_rgba(34,48,62,0.05)] active:scale-[0.99] transition-transform"
    >
      <div className="relative">
        <ImagePlaceholder label="축제 사진" className="h-24 w-full" />
        <span className="absolute left-2 top-2 rounded-md bg-ink px-1.5 py-0.5 text-[11px] font-bold text-white tabular-nums">
          {festival.ddayLabel}
        </span>
      </div>
      <div className="flex flex-col gap-0.5 px-3 pb-3 pt-2.5">
        <span className="truncate text-sm font-semibold text-ink">{festival.name}</span>
        <span className="text-xs text-ink/60 tabular-nums">{festival.periodShort}</span>
        <span className="text-xs text-ink/50">
          {festival.region} · {festival.distanceKm}km
        </span>
      </div>
    </Link>
  );
}
