import { useEffect, useRef, useState, type RefObject } from 'react';
import { X } from 'lucide-react';
import { contentCommentsApi, type ContentComment, type ContentTarget } from '../../api/contentComments';
import { useContentComments, type ContentCommentsState } from '../../hooks/useContentComments';
import { LoadingState, LoadingMore } from '../common/Spinner';
import ContentCommentForm from './ContentCommentForm';
import ContentCommentItem from './ContentCommentItem';

interface ContentCommentSectionProps {
  target: ContentTarget;
  /** 로그인·관리자 여부는 engagement 응답에서만 온다(docs/27 2.1). */
  loggedIn: boolean;
  admin: boolean;
  /** 목록을 받기 전 헤더에 보여줄 댓글 수. engagement가 먼저 도착한다. */
  initialCount?: number;
}

/**
 * 축제 상세와 관광지 상세가 공유하는 공개 댓글 섹션.
 * 무한 스크롤이 아니라 `댓글 더 보기` 버튼이다 — 두 화면 모두 하단 고정 CTA가 있어 무한
 * 스크롤이 스크롤 종료 지점을 잡아먹는다(docs/27 7.1).
 */
export default function ContentCommentSection({
  target,
  loggedIn,
  admin,
  initialCount = 0,
}: ContentCommentSectionProps) {
  const {
    state,
    reload,
    loadMore,
    create,
    clearBodyError,
    openDelete,
    closeDelete,
    confirmDelete,
    toggleLike,
    clearSuccess,
  } = useContentComments(target);
  const openerRef = useRef<HTMLButtonElement | null>(null);
  const [hidingId, setHidingId] = useState<number | null>(null);

  // 관리자 숨김은 회원 흐름과 무관한 예외 경로라 세션에 넣지 않고 여기서 직접 처리한 뒤
  // 목록을 다시 받는다.
  const handleHide = async (comment: ContentComment) => {
    if (hidingId !== null) return;
    setHidingId(comment.id);
    try {
      await contentCommentsApi.setVisibility(comment.id, false);
      await reload();
    } finally {
      setHidingId(null);
    }
  };

  const commentCount = state.status === 'READY' ? state.totalCount : initialCount;

  return (
    <section className="flex flex-col gap-2.5" aria-busy={state.status === 'LOADING'}>
      <h3 className="text-[17px] font-bold text-ink">
        댓글 <span className="tabular-nums text-ink/50">{commentCount}</span>
      </h3>
      <div className="sr-only" role="status" aria-live="polite">
        {state.status === 'LOADING' ? '댓글을 불러오는 중입니다.' : state.successMessage ?? ''}
      </div>

      <ContentCommentForm
        loggedIn={loggedIn}
        submitting={state.submitting}
        bodyError={state.bodyError}
        onSubmit={create}
        onChangeBody={clearBodyError}
      />

      {state.status === 'LOADING' && (
        <LoadingState className="py-6" message="댓글을 불러오는 중이에요" />
      )}

      {state.status === 'ERROR' && (
        <section role="alert" className="rounded-2xl bg-white p-5">
          <p className="text-sm text-coral">댓글을 불러오지 못했어요.</p>
          <button
            type="button"
            onClick={() => void reload()}
            className="mt-3 rounded-xl border border-line px-4 py-2 text-sm font-semibold"
          >
            다시 시도
          </button>
        </section>
      )}

      {state.status === 'READY' && state.items.length === 0 && (
        <p className="rounded-2xl bg-white p-5 text-center text-sm text-ink/60">
          아직 댓글이 없어요. 첫 댓글을 남겨보세요
        </p>
      )}

      {state.status === 'READY' && state.items.length > 0 && (
        <div className="flex flex-col rounded-2xl bg-white px-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
          {state.items.map((comment) => (
            <ContentCommentItem
              key={comment.id}
              comment={comment}
              loggedIn={loggedIn}
              admin={admin}
              likePending={state.pendingLikeIds.includes(comment.id) || hidingId === comment.id}
              onToggleLike={(item) => void toggleLike(item)}
              onRequestDelete={(item, opener) => {
                openerRef.current = opener;
                openDelete(item);
              }}
              onHide={(item) => void handleHide(item)}
            />
          ))}
        </div>
      )}

      {state.loadingMore && <LoadingMore message="댓글을 더 불러오는 중" />}

      {state.status === 'READY' && state.hasNext && !state.loadingMore && (
        <button
          type="button"
          onClick={() => void loadMore()}
          className="rounded-2xl border border-line bg-white py-3 text-[14px] font-semibold text-ink/70 active:bg-sand"
        >
          댓글 더 보기
        </button>
      )}

      {state.error && state.status === 'READY' && (
        <p role="alert" className="text-[13px] text-coral">
          요청을 처리하지 못했어요. 다시 시도해주세요.
        </p>
      )}

      {state.successMessage && (
        <div role="status" aria-live="polite" className="rounded-2xl bg-ink px-4 py-3 text-sm text-white">
          {state.successMessage}
          <button type="button" className="ml-2 underline" onClick={clearSuccess}>
            닫기
          </button>
        </div>
      )}

      {state.deleteTarget && (
        <CommentDeleteDialog
          state={state}
          onClose={() => {
            closeDelete();
            queueMicrotask(() => openerRef.current?.focus());
          }}
          onSubmit={() => void confirmDelete()}
        />
      )}
    </section>
  );
}

export function CommentDeleteDialog({
  state,
  onClose,
  onSubmit,
}: {
  state: ContentCommentsState;
  onClose: () => void;
  onSubmit: () => void;
}) {
  const dialogRef = useRef<HTMLElement>(null);
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    const focusables = () => Array.from(dialog.querySelectorAll<HTMLElement>('button:not(:disabled)'));
    focusables()[0]?.focus();
    const keydown = (event: KeyboardEvent) => {
      handleCommentDeleteDialogKeyDown(event, focusables(), document.activeElement, state.deleting, onClose);
    };
    document.addEventListener('keydown', keydown);
    return () => document.removeEventListener('keydown', keydown);
  }, [onClose, state.deleting]);
  return <CommentDeleteDialogContent state={state} onClose={onClose} onSubmit={onSubmit} dialogRef={dialogRef} />;
}

export function CommentDeleteDialogContent({
  state,
  onClose,
  onSubmit,
  dialogRef,
}: {
  state: ContentCommentsState;
  onClose: () => void;
  onSubmit: () => void;
  dialogRef?: RefObject<HTMLElement>;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink/45 sm:items-center sm:p-5">
      <section
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="comment-delete-title"
        className="w-full max-w-[430px] rounded-t-3xl bg-white p-5 sm:rounded-3xl"
      >
        <div className="flex justify-between gap-3">
          <h2 id="comment-delete-title" className="text-lg font-bold">
            이 댓글을 삭제할까요?
          </h2>
          <button type="button" aria-label="댓글 삭제 창 닫기" disabled={state.deleting} onClick={onClose}>
            <X aria-hidden="true" />
          </button>
        </div>
        <p className="mt-3 text-sm leading-6 text-ink/70">삭제한 댓글은 다시 복구할 수 없습니다.</p>
        {state.error && (
          <p role="alert" aria-live="assertive" className="mt-3 rounded-2xl bg-coral/10 p-3 text-sm text-coral">
            댓글을 삭제하지 못했어요. 다시 시도해주세요.
          </p>
        )}
        <div className="mt-5 grid grid-cols-2 gap-2">
          <button
            type="button"
            disabled={state.deleting}
            onClick={onClose}
            className="rounded-2xl border border-line py-3 disabled:opacity-50"
          >
            취소
          </button>
          <button
            type="button"
            disabled={state.deleting}
            onClick={onSubmit}
            className="rounded-2xl bg-coral py-3 font-bold text-white disabled:opacity-50"
          >
            {state.deleting ? '삭제 중...' : '삭제'}
          </button>
        </div>
      </section>
    </div>
  );
}

export function handleCommentDeleteDialogKeyDown(
  event: Pick<KeyboardEvent, 'key' | 'shiftKey' | 'preventDefault'>,
  focusables: HTMLElement[],
  activeElement: Element | null,
  deleting: boolean,
  onClose: () => void,
) {
  if (event.key === 'Escape' && !deleting) {
    event.preventDefault();
    onClose();
    return;
  }
  if (event.key !== 'Tab' || focusables.length === 0) return;
  const first = focusables[0];
  const last = focusables[focusables.length - 1];
  if (event.shiftKey && activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}
