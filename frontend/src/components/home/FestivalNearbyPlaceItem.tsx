import { Link } from 'react-router-dom';
import { MapPin } from 'lucide-react';
import type { TourSpot } from '../../types';
import EngagementCounts from '../common/EngagementCounts';
import ImagePlaceholder from '../common/ImagePlaceholder';
import { placeholderKindFromContentType } from '../common/imagePlaceholderPresets';

interface FestivalNearbyPlaceItemProps {
  spot: TourSpot;
  distanceLabel: string; // 예: '200m'
  walkLabel: string; // 예: '도보 3분'
  /** 이 사람이 찜했는가. 미지정이면 `spot.bookmarkedByMe`를 쓴다. */
  bookmarked?: boolean;
  /** 하트를 누를 수 있게 한다. 넘기지 않으면 표시 전용이다. */
  onToggleBookmark?: () => void;
  bookmarkPending?: boolean;
}

/**
 * 홈 "축제와 함께 둘러보기" 세로 목록 아이템 — 별점 없이 축제장 기준 거리·도보 시간 표기.
 *
 * 탐색 목록 카드와 같은 이유로 카드 <b>본문만</b> 링크다 — 찜 버튼이 링크 안에 있으면 하트를
 * 눌러도 상세 화면으로 이동한다.
 */
export default function FestivalNearbyPlaceItem({
  spot,
  distanceLabel,
  walkLabel,
  bookmarked,
  onToggleBookmark,
  bookmarkPending = false,
}: FestivalNearbyPlaceItemProps) {
  return (
    <div className="flex items-center gap-3 rounded-2xl bg-white p-3 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
      <Link
        to={`/spots/${spot.id}`}
        className="flex min-w-0 flex-1 items-center gap-3 active:scale-[0.99] transition-transform"
      >
        {spot.imageUrl ? (
          <img
            src={spot.imageUrl}
            alt={`${spot.name} 사진`}
            className="h-16 w-16 shrink-0 rounded-xl object-cover"
          />
        ) : (
          <ImagePlaceholder
            kind={placeholderKindFromContentType(spot.contentTypeId)}
            seed={spot.name}
            size="sm"
            className="h-16 w-16 shrink-0 rounded-xl"
          />
        )}
        <div className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className="truncate text-[15px] font-semibold text-ink">{spot.name}</span>
          {spot.category && <span className="text-xs text-ink/50">{spot.category}</span>}
          <span className="flex items-center gap-1 text-xs text-ink/60 tabular-nums">
            <MapPin size={12} />
            축제장에서 {distanceLabel} · {walkLabel}
          </span>
        </div>
      </Link>
      {spot.bookmarkCount !== undefined && spot.commentCount !== undefined && (
        <EngagementCounts
          bookmarkCount={spot.bookmarkCount}
          commentCount={spot.commentCount}
          bookmarked={bookmarked ?? spot.bookmarkedByMe ?? false}
          onToggleBookmark={onToggleBookmark}
          pending={bookmarkPending}
        />
      )}
    </div>
  );
}
