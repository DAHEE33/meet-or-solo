import { Link } from 'react-router-dom';
import { HeartHandshake } from 'lucide-react';
import type { Festival } from '../../types';
import { getFestivalStatusLabel, getFestivalStatusSoftClass } from '../../utils/festival';
import EngagementCounts from '../common/EngagementCounts';
import ImagePlaceholder from '../common/ImagePlaceholder';

interface FestivalListItemProps {
  festival: Festival;
  /** 미지정 시 `{place} · {distanceKm}km` 로 표기 (탐색 목록 기본값) */
  distanceLabel?: string;
  /** 이 사람이 찜했는가. 미지정이면 `festival.bookmarkedByMe`를 쓴다. */
  bookmarked?: boolean;
  /** 하트를 누를 수 있게 한다. 넘기지 않으면 표시 전용이다(찜 목록 등). */
  onToggleBookmark?: () => void;
  bookmarkPending?: boolean;
}

/**
 * 세로 목록용 축제 카드 (탐색 목록, 관광지 상세 "주변에서 열리는 축제").
 *
 * 카드 전체가 아니라 <b>본문만</b> 상세로 가는 링크다. 찜 버튼이 링크 안에 있으면 하트를 눌러도
 * 상세 화면으로 이동해 버리기 때문이다.
 */
export default function FestivalListItem({
  festival,
  distanceLabel,
  bookmarked,
  onToggleBookmark,
  bookmarkPending = false,
}: FestivalListItemProps) {
  const locationLabel =
    distanceLabel ??
    [festival.place ?? festival.address, festival.distanceKm !== undefined ? `${festival.distanceKm}km` : null]
      .filter(Boolean)
      .join(' · ');

  return (
    <div className="flex items-center gap-3 rounded-2xl bg-white p-3 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
      <Link
        to={`/festivals/${festival.id}`}
        className="flex min-w-0 flex-1 items-center gap-3 active:scale-[0.99] transition-transform"
      >
        {festival.thumbnailUrl ? (
          <img
            src={festival.thumbnailUrl}
            alt={`${festival.name} 사진`}
            className="h-[72px] w-[72px] shrink-0 rounded-xl object-cover"
          />
        ) : (
          <ImagePlaceholder
            kind="FESTIVAL"
            seed={festival.name}
            size="sm"
            className="h-[72px] w-[72px] shrink-0 rounded-xl"
          />
        )}
        <div className="flex min-w-0 flex-1 flex-col gap-[3px]">
          <span
            className={`w-fit rounded-md px-[7px] py-0.5 text-[11px] font-bold tabular-nums ${getFestivalStatusSoftClass(festival.status)}`}
          >
            {getFestivalStatusLabel(festival)}
          </span>
          <span className="truncate text-[15px] font-semibold text-ink">{festival.name}</span>
          <span className="text-xs text-ink/60 tabular-nums">
            {festival.periodShort}
            {locationLabel && ` · ${locationLabel}`}
          </span>
          {festival.matchingCount !== undefined && (
            <span className="flex items-center gap-1 text-xs font-medium text-coral tabular-nums">
              <HeartHandshake size={12} />
              현재 {festival.matchingCount}명 매칭 중
            </span>
          )}
        </div>
      </Link>
      {festival.bookmarkCount !== undefined && festival.commentCount !== undefined && (
        <EngagementCounts
          bookmarkCount={festival.bookmarkCount}
          commentCount={festival.commentCount}
          bookmarked={bookmarked ?? festival.bookmarkedByMe ?? false}
          onToggleBookmark={onToggleBookmark}
          pending={bookmarkPending}
        />
      )}
    </div>
  );
}
