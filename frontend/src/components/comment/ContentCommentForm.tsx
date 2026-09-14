import { useState } from 'react';
import { Link } from 'react-router-dom';
import { COMMENT_BODY_MAX_LENGTH } from '../../hooks/useContentComments';
import PrimaryButton from '../common/PrimaryButton';

interface ContentCommentFormProps {
  loggedIn: boolean;
  /** 로그인했지만 아직 쓸 자격이 없을 수 있다 — 축제는 체크인 이력이 필요하다(docs/27 5.2). */
  canComment: boolean;
  /** 자격 안내에 "체크인하러 가기"를 띄울지. 축제 상세에서만 true다. */
  festivalId?: number;
  submitting: boolean;
  bodyError: string | null;
  onSubmit: (body: string) => Promise<boolean>;
  onChangeBody: () => void;
}

/**
 * 댓글 입력.
 *
 * 비로그인 사용자에게는 입력창 대신 로그인 링크를 보여준다. 입력창을 주고 등록에서 401을 받으면
 * apiClient가 화면째로 `/login`으로 리다이렉트해 작성 중인 내용이 사라진다(docs/27 2.1).
 *
 * 로그인했지만 체크인 이력이 없는 축제에도 같은 이유로 입력창을 주지 않는다 — 다 쓰고 나서
 * 403을 받으면 작성한 내용이 버려진다.
 */
export default function ContentCommentForm({
  loggedIn,
  canComment,
  festivalId,
  submitting,
  bodyError,
  onSubmit,
  onChangeBody,
}: ContentCommentFormProps) {
  const [body, setBody] = useState('');

  if (!loggedIn) {
    return (
      <Link
        to="/login"
        className="flex w-full items-center justify-center rounded-2xl border border-line bg-white py-3.5 text-[14px] font-semibold text-ink/70 active:bg-sand"
      >
        로그인하고 댓글 남기기
      </Link>
    );
  }

  if (!canComment) {
    return (
      <div className="flex flex-col items-center gap-2 rounded-2xl border border-line bg-white px-4 py-4 text-center">
        <p className="text-[13px] leading-relaxed text-ink/60">
          현장에 다녀온 분들의 후기만 모으고 있어요.
          <br />이 축제에 체크인하면 댓글을 남길 수 있어요.
        </p>
        {festivalId !== undefined && (
          <Link
            to="/check-in"
            state={{ festivalId }}
            className="rounded-full border border-line px-4 py-2 text-[13px] font-bold text-ink active:bg-sand"
          >
            체크인하러 가기
          </Link>
        )}
      </div>
    );
  }

  const handleSubmit = async () => {
    const succeeded = await onSubmit(body);
    if (succeeded) setBody('');
  };

  return (
    <div className="flex flex-col gap-2 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
      <label className="sr-only" htmlFor="content-comment-body">
        댓글 내용
      </label>
      <textarea
        id="content-comment-body"
        value={body}
        onChange={(event) => {
          setBody(event.target.value);
          if (bodyError) onChangeBody();
        }}
        rows={3}
        maxLength={COMMENT_BODY_MAX_LENGTH}
        placeholder="댓글을 남겨보세요"
        className="w-full resize-none rounded-xl bg-sand px-3 py-2.5 text-[14px] leading-relaxed text-ink placeholder:text-ink/35 focus:outline-none"
      />
      {bodyError && (
        <p role="alert" className="text-[13px] text-coral">
          {bodyError}
        </p>
      )}
      <div className="flex items-center justify-between gap-3">
        <span className="text-[12px] text-ink/45 tabular-nums">
          {body.trim().length}/{COMMENT_BODY_MAX_LENGTH}
        </span>
        <PrimaryButton
          pending={submitting}
          disabled={submitting || body.trim().length === 0}
          onClick={() => void handleSubmit()}
          className="w-auto px-5 py-2.5 text-[14px]"
        >
          {submitting ? '등록 중...' : '등록'}
        </PrimaryButton>
      </div>
    </div>
  );
}
