import { ThumbsUp } from 'lucide-react';
import { useState } from 'react';
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
 * 작성자 표시는 프로필 사진 + 닉네임이다. 사진은 **로그인한 회원에게만** 내려오므로
 * (docs/27 6.2) 비로그인 화면과 사진을 등록하지 않은 회원은 닉네임 이니셜 아바타를 본다.
 * 본문은 200자 컷 아코디언(ExpandableText)을 재사용한다.
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
      <CommentAvatar comment={comment} />
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

/**
 * 작성자 아바타. 사진이 있으면 사진, 없으면 닉네임 첫 글자다.
 *
 * <p>사진을 못 불러오면 이니셜로 되돌린다. 프로필 사진은 우리 서버를 거치는 경로
 * (`/api/members/{id}/profile-image`)나 카카오·네이버 CDN에서 오는데, 로그인이 풀렸거나
 * 소셜 쪽 URL이 만료되면 깨진 이미지 아이콘이 남는다. 댓글 목록에서 그건 눈에 띄는 고장이다.
 */
export function CommentAvatar({ comment }: { comment: ContentComment }) {
  const [failed, setFailed] = useState(false);

  if (comment.profileImageUrl && !failed) {
    return (
      <img
        src={comment.profileImageUrl}
        alt=""
        aria-hidden="true"
        loading="lazy"
        onError={() => setFailed(true)}
        className="h-9 w-9 shrink-0 rounded-full object-cover"
      />
    );
  }
  return (
    <div
      aria-hidden="true"
      className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-coral/10 text-[13px] font-bold text-coral"
    >
      {comment.nickname.slice(0, 1)}
    </div>
  );
}
