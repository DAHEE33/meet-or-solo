import { Heart } from 'lucide-react';

interface BookmarkButtonProps {
  bookmarked: boolean;
  /** engagement 조회 전이거나 실패하면 누를 수 없다. */
  disabled?: boolean;
  pending?: boolean;
  onClick: () => void;
}

/**
 * 상세 화면 헤더의 찜 버튼.
 *
 * 채운 하트 + coral은 MyPage가 이미 쓰던 찜 관용구다. 좋아요는 ThumbsUp으로 따로 구분한다
 * (docs/27 7.1).
 */
export default function BookmarkButton({
  bookmarked,
  disabled = false,
  pending = false,
  onClick,
}: BookmarkButtonProps) {
  return (
    <button
      type="button"
      aria-label={bookmarked ? '찜 해제' : '찜하기'}
      aria-pressed={bookmarked}
      aria-busy={pending || undefined}
      disabled={disabled || pending}
      onClick={onClick}
      className="flex h-11 w-11 items-center justify-center rounded-full text-ink active:bg-black/5 disabled:opacity-40"
    >
      <Heart
        size={20}
        strokeWidth={1.8}
        className={bookmarked ? 'fill-coral text-coral' : 'text-ink'}
      />
    </button>
  );
}
