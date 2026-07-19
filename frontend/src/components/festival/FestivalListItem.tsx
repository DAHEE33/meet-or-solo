import { Link } from 'react-router-dom';
import { HeartHandshake } from 'lucide-react';
import type { Festival } from '../../types';
import { getFestivalStatusLabel, getFestivalStatusSoftClass } from '../../utils/festival';
import ImagePlaceholder from '../common/ImagePlaceholder';

interface FestivalListItemProps {
  festival: Festival;
  /** 미지정 시 `{place} · {distanceKm}km` 로 표기 (탐색 목록 기본값) */
  distanceLabel?: string;
}

/** 세로 목록용 축제 카드 (탐색 목록, 관광지 상세 "주변에서 열리는 축제") */
export default function FestivalListItem({ festival, distanceLabel }: FestivalListItemProps) {
  return (
    <Link
      to={`/festivals/${festival.id}`}
      className="flex items-center gap-3 rounded-2xl bg-white p-3 shadow-[0_1px_8px_rgba(34,48,62,0.05)] active:scale-[0.99] transition-transform"
    >
      <ImagePlaceholder label="사진" className="h-[72px] w-[72px] shrink-0 rounded-xl" />
      <div className="flex min-w-0 flex-1 flex-col gap-[3px]">
        <span
          className={`w-fit rounded-md px-[7px] py-0.5 text-[11px] font-bold tabular-nums ${getFestivalStatusSoftClass(festival.status)}`}
        >
          {getFestivalStatusLabel(festival)}
        </span>
        <span className="truncate text-[15px] font-semibold text-ink">{festival.name}</span>
        <span className="text-xs text-ink/60 tabular-nums">
          {festival.periodShort} · {distanceLabel ?? `${festival.place} · ${festival.distanceKm}km`}
        </span>
        <span className="flex items-center gap-1 text-xs font-medium text-coral tabular-nums">
          <HeartHandshake size={12} />
          현재 {festival.matchingCount}명 매칭 중
        </span>
      </div>
    </Link>
  );
}
