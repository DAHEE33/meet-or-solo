import { Link } from 'react-router-dom';
import { Star } from 'lucide-react';
import type { TourSpot } from '../../types';
import ImagePlaceholder from '../common/ImagePlaceholder';

interface NearbySpotItemProps {
  spot: TourSpot;
}

/** 주변 인기 관광지 리스트 아이템 */
export default function NearbySpotItem({ spot }: NearbySpotItemProps) {
  return (
    <Link
      to={`/spots/${spot.id}`}
      className="flex items-center gap-3 rounded-2xl bg-white p-3 shadow-[0_1px_8px_rgba(34,48,62,0.05)] active:scale-[0.99] transition-transform"
    >
      {spot.imageUrl ? (
        <img
          src={spot.imageUrl}
          alt={spot.name}
          className="h-16 w-16 shrink-0 rounded-xl object-cover"
        />
      ) : (
        <ImagePlaceholder label="사진" className="h-16 w-16 shrink-0 rounded-xl" />
      )}
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="truncate text-[15px] font-semibold text-ink">{spot.name}</span>
        <span className="text-xs text-ink/50">{spot.category}</span>
        <span className="flex items-center gap-1 text-xs text-ink/60 tabular-nums">
          <Star size={12} className="fill-amber-400 text-amber-400" />
          {spot.rating} · {spot.distanceKm}km
        </span>
      </div>
    </Link>
  );
}
