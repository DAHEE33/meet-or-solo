import { Link } from 'react-router-dom';
import { MapPin } from 'lucide-react';
import type { TourSpot } from '../../types';
import ImagePlaceholder from '../common/ImagePlaceholder';

interface FestivalNearbyPlaceItemProps {
  spot: TourSpot;
  distanceLabel: string; // 예: '200m'
  walkLabel: string; // 예: '도보 3분'
}

/** 홈 "축제와 함께 둘러보기" 세로 목록 아이템 — 별점 없이 축제장 기준 거리·도보 시간 표기 */
export default function FestivalNearbyPlaceItem({
  spot,
  distanceLabel,
  walkLabel,
}: FestivalNearbyPlaceItemProps) {
  return (
    <Link
      to={`/spots/${spot.id}`}
      className="flex items-center gap-3 rounded-2xl bg-white p-3 shadow-[0_1px_8px_rgba(34,48,62,0.05)] active:scale-[0.99] transition-transform"
    >
      <ImagePlaceholder label="사진" className="h-16 w-16 shrink-0 rounded-xl" />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="truncate text-[15px] font-semibold text-ink">{spot.name}</span>
        <span className="text-xs text-ink/50">{spot.category}</span>
        <span className="flex items-center gap-1 text-xs text-ink/60 tabular-nums">
          <MapPin size={12} />
          축제장에서 {distanceLabel} · {walkLabel}
        </span>
      </div>
    </Link>
  );
}
