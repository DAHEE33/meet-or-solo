import { Link } from 'react-router-dom';
import type { TourSpot } from '../../types';
import ImagePlaceholder from '../common/ImagePlaceholder';

interface ExploreSpotItemProps {
  spot: TourSpot;
}

/** 탐색 목록의 관광지 카드 — 별점 없이 유형·거리·지역명만 표기 */
export default function ExploreSpotItem({ spot }: ExploreSpotItemProps) {
  return (
    <Link
      to={`/spots/${spot.id}`}
      className="flex items-center gap-3 rounded-2xl bg-white p-3 shadow-[0_1px_8px_rgba(34,48,62,0.05)] active:scale-[0.99] transition-transform"
    >
      <ImagePlaceholder label="사진" className="h-16 w-16 shrink-0 rounded-xl" />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="truncate text-[15px] font-semibold text-ink">{spot.name}</span>
        {(spot.category || spot.distanceKm !== undefined) && (
          <span className="text-xs text-ink/60 tabular-nums">
            {[spot.category, spot.distanceKm !== undefined ? `${spot.distanceKm}km` : null]
              .filter(Boolean)
              .join(' · ')}
          </span>
        )}
        <span className="truncate text-xs text-ink/50">{spot.address}</span>
      </div>
    </Link>
  );
}
