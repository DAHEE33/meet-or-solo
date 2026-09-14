import { Heart, MessageSquare } from 'lucide-react';

interface EngagementCountsProps {
  /** 찜 수. 화면 문구는 "좋아요"다 — 콘텐츠 단위 반응이 찜 하나뿐이라 둘이 같은 값이다. */
  bookmarkCount: number;
  /** 공개 댓글 수. */
  commentCount: number;
  /** 이 사람이 찜했는가. 하트를 채울지 정한다. */
  bookmarked?: boolean;
  /**
   * 하트를 눌렀을 때. 넘기지 않으면 표시 전용으로 그린다 — 찜 목록처럼 토글이 화면의 전제를
   * 무너뜨리는 곳이 있어, 누를 수 있게 만드는 쪽을 호출부가 명시하게 했다.
   */
  onToggleBookmark?: () => void;
  /** 요청 중. 연타를 막는다. */
  pending?: boolean;
  className?: string;
}

/**
 * 목록 카드 오른쪽에 붙는 좋아요(찜)·후기(댓글) 수.
 *
 * 축제 카드와 관광지 카드가 같은 자리에 같은 모양으로 쓰기 위해 분리했다. 채운 하트 + coral은
 * 상세 화면 `BookmarkButton`과 같은 관용구이고, 좋아요는 ThumbsUp으로 따로 구분한다(docs/27 7.1).
 *
 * <b>이 컴포넌트는 카드의 `<Link>` 바깥에 놓여야 한다.</b> 링크 안에 버튼을 넣으면 하트를 눌러도
 * 상세로 이동해 버린다.
 */
export default function EngagementCounts({
  bookmarkCount,
  commentCount,
  bookmarked = false,
  onToggleBookmark,
  pending = false,
  className = '',
}: EngagementCountsProps) {
  const heartClass = bookmarked ? 'fill-coral text-coral' : 'text-ink/35';

  return (
    <div className={`flex shrink-0 flex-col items-end gap-1 ${className}`}>
      {onToggleBookmark ? (
        <button
          type="button"
          aria-label={bookmarked ? '찜 해제' : '찜하기'}
          aria-pressed={bookmarked}
          aria-busy={pending || undefined}
          disabled={pending}
          onClick={(event) => {
            // 카드를 감싼 링크로 이벤트가 올라가면 상세 화면으로 이동해 버린다.
            event.preventDefault();
            event.stopPropagation();
            onToggleBookmark();
          }}
          className="-mr-1 flex items-center gap-1 rounded-full px-1 py-0.5 text-[12px] text-ink/50 tabular-nums active:bg-black/5 disabled:opacity-50"
        >
          <Heart size={13} className={heartClass} aria-hidden />
          {bookmarkCount}
        </button>
      ) : (
        <span
          className="flex items-center gap-1 text-[12px] text-ink/50 tabular-nums"
          aria-label={`좋아요 ${bookmarkCount}개`}
        >
          <Heart size={13} className={bookmarked ? 'fill-coral text-coral' : 'text-coral'} aria-hidden />
          {bookmarkCount}
        </span>
      )}
      <span
        className="flex items-center gap-1 text-[12px] text-ink/50 tabular-nums"
        aria-label={`후기 ${commentCount}개`}
      >
        <MessageSquare size={13} className="text-ink/35" aria-hidden />
        {commentCount}
      </span>
    </div>
  );
}
