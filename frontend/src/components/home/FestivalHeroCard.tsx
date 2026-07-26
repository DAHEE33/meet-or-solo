import { Link } from 'react-router-dom';
import { Calendar, MapPin, Users, HeartHandshake, ChevronRight } from 'lucide-react';
import type { Festival } from '../../types';
import ImagePlaceholder from '../common/ImagePlaceholder';

interface FestivalHeroCardProps {
  festival: Festival;
}

/** 홈 "지금 가장 핫한 축제" 대표 카드 */
export default function FestivalHeroCard({ festival }: FestivalHeroCardProps) {
  return (
    <Link
      to={`/festivals/${festival.id}`}
      className="block overflow-hidden rounded-3xl bg-white shadow-[0_2px_16px_rgba(34,48,62,0.08)] active:scale-[0.99] transition-transform"
    >
      {festival.thumbnailUrl ? (
        <img
          src={festival.thumbnailUrl}
          alt={`${festival.name} 대표 사진`}
          className="h-44 w-full object-cover"
        />
      ) : (
        <ImagePlaceholder label={`${festival.name} 대표 사진`} className="h-44 w-full" />
      )}
      <div className="flex flex-col gap-2 p-4">
        <div className="flex items-center gap-2">
          <span className="rounded-full bg-coral/10 px-2.5 py-0.5 text-xs font-semibold text-coral">
            지금 가장 핫한 축제
          </span>
          {festival.category && <span className="text-xs text-ink/50">{festival.category}</span>}
        </div>
        <h3 className="text-lg font-bold text-ink">{festival.name}</h3>
        <div className="flex flex-col gap-1 text-[13px] text-ink/60 tabular-nums">
          <span className="flex items-center gap-1">
            <Calendar size={14} />
            {festival.periodShort}
          </span>
          <span className="flex items-center gap-1">
            <MapPin size={14} />
            {festival.place ?? festival.address}
            {festival.distanceKm !== undefined && ` · 현재 위치에서 ${festival.distanceKm}km`}
          </span>
        </div>
        {(festival.expectedAttendees !== undefined || festival.matchingCount !== undefined) && (
          <div className="flex gap-1.5">
            {festival.expectedAttendees !== undefined && (
              <span className="flex items-center gap-1 rounded-md bg-sand px-2 py-[3px] text-xs text-ink/60 tabular-nums">
                <Users size={12} />
                참가 예정 {festival.expectedAttendees.toLocaleString()}명
              </span>
            )}
            {festival.matchingCount !== undefined && (
              <span className="flex items-center gap-1 rounded-md bg-coral/[0.08] px-2 py-[3px] text-xs font-medium text-coral tabular-nums">
                <HeartHandshake size={12} />
                매칭 중 {festival.matchingCount}명
              </span>
            )}
          </div>
        )}
        <div className="flex items-center justify-end gap-0.5 text-[13px] font-medium text-coral">
          축제 상세 보기
          <ChevronRight size={14} />
        </div>
      </div>
    </Link>
  );
}
