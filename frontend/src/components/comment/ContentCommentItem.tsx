import { ThumbsUp } from 'lucide-react';
import type { ContentComment } from '../../api/contentComments';
import { formatRelativeTime } from '../../utils/relativeTime';
import ExpandableText from '../common/ExpandableText';

interface ContentCommentItemProps {
  comment: ContentComment;
  loggedIn: boolean;
  admin: boolean;
  likePending: boolean;
  onToggleLike: (comment: ContentComment) => void;
  /** 삭제 dialog를 닫은 뒤 포커스를 되돌리기 위해 누른 버튼을 함께 넘긴다. */
  onRequestDelete: (comment: ContentComment, opener: HTMLButtonElement) => void;
  onHide: (comment: ContentComment) => void;
}

/**
 * 댓글 1건.
 *
 * 작성자 표시는 닉네임 + 이니셜 아바타만 쓴다. 프로필 이미지는 공개 화면에 노출하지 않는다
 * (docs/27 6.2). 본문은 200자 컷 아코디언(ExpandableText)을 재사용한다.
 */
export default function ContentCommentItem({
  comment,
  loggedIn,
  admin,
  likePending,
  onToggleLike,
  onRequestDelete,
  onHide,
}: ContentCommentItemProps) {
  return (
    <article className="flex gap-3 border-b border-line py-3.5 last:border-none">
      <div
        aria-hidden="true"
        className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-coral/10 text-[13px] font-bold text-coral"
      >
        {comment.nickname.slice(0, 1)}
      </div>
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex items-center gap-2">
          <span className="truncate text-[14px] font-semibold text-ink">{comment.nickname}</span>
          <time dateTime={comment.createdAt} className="shrink-0 text-[12px] text-ink/45 tabular-nums">
            {formatRelativeTime(comment.createdAt)}
          </time>
        </div>
        <ExpandableText text={comment.body} className="text-[14px] text-ink/75" />
        <div className="mt-0.5 flex items-center justify-between gap-3">
          <button
            type="button"
            aria-label={comment.likedByMe ? '좋아요 취소' : '좋아요'}
            aria-pressed={comment.likedByMe}
            aria-busy={likePending || undefined}
            disabled={!loggedIn || likePending}
            onClick={() => onToggleLike(comment)}
            className={`flex items-center gap-1 rounded-full px-2 py-1 text-[13px] font-semibold tabular-nums disabled:opacity-50 ${
              comment.likedByMe ? 'bg-coral/10 text-coral' : 'text-ink/50 active:bg-sand'
            }`}
          >
            <ThumbsUp size={14} className={comment.likedByMe ? 'fill-coral' : undefined} />
            {comment.likeCount}
          </button>
          <div className="flex shrink-0 items-center gap-2">
            {admin && !comment.mine && (
              <button
                type="button"
                onClick={() => onHide(comment)}
                className="text-[13px] font-semibold text-ink/45"
              >
                숨김
              </button>
            )}
            {comment.mine && (
              <button
                type="button"
                aria-label="내 댓글 삭제"
                onClick={(event) => onRequestDelete(comment, event.currentTarget)}
                className="text-[13px] font-semibold text-ink/45"
              >
                삭제
              </button>
            )}
          </div>
        </div>
      </div>
    </article>
  );
}
