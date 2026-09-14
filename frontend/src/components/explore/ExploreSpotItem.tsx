import { Link } from 'react-router-dom';
import type { TourSpot } from '../../types';
import EngagementCounts from '../common/EngagementCounts';
import ImagePlaceholder from '../common/ImagePlaceholder';
import { placeholderKindFromContentType } from '../common/imagePlaceholderPresets';

interface ExploreSpotItemProps {
  spot: TourSpot;
  /** 이 사람이 찜했는가. 미지정이면 `spot.bookmarkedByMe`를 쓴다. */
  bookmarked?: boolean;
  /** 하트를 누를 수 있게 한다. 넘기지 않으면 표시 전용이다(찜 목록 등). */
  onToggleBookmark?: () => void;
  bookmarkPending?: boolean;
}

/**
 * 탐색 목록의 관광지 카드 — 별점 없이 유형·거리·지역명만 표기.
 *
 * 축제 카드와 같은 이유로 카드 <b>본문만</b> 링크다 — 찜 버튼이 링크 안에 있으면 하트를 눌러도
 * 상세 화면으로 이동한다.
 */
export default function ExploreSpotItem({
  spot,
  bookmarked,
  onToggleBookmark,
  bookmarkPending = false,
}: ExploreSpotItemProps) {
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
