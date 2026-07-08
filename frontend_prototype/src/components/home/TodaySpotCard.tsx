import { Link } from 'react-router-dom';
import { Star, MapPin } from 'lucide-react';
import type { TourSpot } from '../../types';
import ImagePlaceholder from '../common/ImagePlaceholder';

interface TodaySpotCardProps {
  spot: TourSpot;
}

/** 홈 상단 "오늘의 추천 관광지" 대형 카드 */
export default function TodaySpotCard({ spot }: TodaySpotCardProps) {
  return (
    <Link
      to={`/spots/${spot.id}`}
      className="block overflow-hidden rounded-3xl bg-white shadow-[0_2px_16px_rgba(34,48,62,0.08)] active:scale-[0.99] transition-transform"
    >
      {spot.imageUrl ? (
        <img src={spot.imageUrl} alt={spot.name} className="h-44 w-full object-cover" />
      ) : (
        <ImagePlaceholder label={`${spot.name} 사진`} className="h-44 w-full" />
      )}
      <div className="flex flex-col gap-2 p-4">
        <div className="flex items-center gap-2">
          <span className="rounded-full bg-coral/10 px-2.5 py-0.5 text-xs font-semibold text-coral">
            오늘의 추천
          </span>
          <span className="text-xs text-ink/50">{spot.category}</span>
        </div>
        <h3 className="text-lg font-bold text-ink">{spot.name}</h3>
        <div className="flex items-center gap-3 text-[13px] text-ink/60 tabular-nums">
          <span className="flex items-center gap-1">
            <Star size={14} className="fill-amber-400 text-amber-400" />
            {spot.rating} ({spot.reviewCount.toLocaleString()})
          </span>
          <span className="flex items-center gap-1">
            <MapPin size={14} />
            {spot.distanceKm}km
          </span>
        </div>
        <div className="flex flex-wrap gap-1.5">
          {spot.tags.map((tag) => (
            <span key={tag} className="rounded-md bg-sand px-2 py-0.5 text-xs text-ink/60">
              #{tag}
            </span>
          ))}
        </div>
      </div>
    </Link>
  );
}
