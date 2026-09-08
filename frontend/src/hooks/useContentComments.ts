import { useCallback, useEffect, useRef, useState } from 'react';
import {
  contentCommentsApi,
  type ContentComment,
  type ContentCommentLikeResult,
  type ContentCommentPage,
  type ContentTarget,
} from '../api/contentComments';

// 공개 댓글 목록·등록·삭제·좋아요를 한 세션으로 묶는다.
// 로직을 컴포넌트에서 분리한 이유는 useMemberBlocks와 같다 — 이 프로젝트의 vitest는 node
// 환경이고 jsdom이 없어, 렌더링 없이 상태 전이를 검증할 수 있어야 한다.

export const COMMENT_BODY_MAX_LENGTH = 500;
export const COMMENT_PAGE_SIZE = 20;

export type ContentCommentsState = {
  status: 'LOADING' | 'READY' | 'ERROR';
  items: ContentComment[];
  page: number;
  hasNext: boolean;
  /** 화면의 `댓글 N` 표기용. 서버 totalElements를 그대로 쓴다. */
  totalCount: number;
  loadingMore: boolean;
  submitting: boolean;
  /** 본문 길이 검증 실패 메시지. 서버 왕복 없이 즉시 보여준다. */
  bodyError: string | null;
  deleteTarget: ContentComment | null;
  deleting: boolean;
  /** 좋아요 요청이 진행 중인 댓글 id. 같은 댓글 연타를 흡수한다. */
  pendingLikeIds: number[];
  error: Error | null;
  successMessage: string | null;
};

const initialState: ContentCommentsState = {
  status: 'LOADING',
  items: [],
  page: 0,
  hasNext: false,
  totalCount: 0,
  loadingMore: false,
  submitting: false,
  bodyError: null,
  deleteTarget: null,
  deleting: false,
  pendingLikeIds: [],
  error: null,
  successMessage: null,
};

export function initialContentCommentsState(): ContentCommentsState {
  return initialState;
}

/** 본문 검증. 서버 CHECK(`char_length(btrim(body)) BETWEEN 1 AND 500`)와 같은 규칙이다. */
export function validateCommentBody(body: string): string | null {
  const trimmed = body.trim();
  if (trimmed.length === 0) return '댓글을 입력해주세요.';
  if (trimmed.length > COMMENT_BODY_MAX_LENGTH) {
    return `댓글은 ${COMMENT_BODY_MAX_LENGTH}자까지 쓸 수 있어요.`;
  }
  return null;
}

/**
 * 다음 페이지를 이어붙일 때 id가 겹치는 항목을 버린다.
 *
 * offset 페이징이라 페이지 요청 사이에 새 댓글이 달리면 같은 댓글이 두 페이지에 걸쳐 내려올 수
 * 있다(docs/27 5.5의 알려진 한계). 화면에서 중복이 보이는 증상만 여기서 막는다.
 */
export function dedupeCommentsById(
  existing: readonly ContentComment[],
  incoming: readonly ContentComment[],
): ContentComment[] {
  const seen = new Set(existing.map((comment) => comment.id));
  const merged = [...existing];
  for (const comment of incoming) {
    if (seen.has(comment.id)) continue;
    seen.add(comment.id);
    merged.push(comment);
  }
  return merged;
}

type Dependencies = {
  list: (page: number, size: number, signal: AbortSignal) => Promise<ContentCommentPage>;
  create: (body: string, signal: AbortSignal) => Promise<ContentComment>;
  remove: (commentId: number, signal: AbortSignal) => Promise<void>;
  setLike: (
    commentId: number,
    liked: boolean,
    signal: AbortSignal,
  ) => Promise<ContentCommentLikeResult>;
};

export function createContentCommentsSession(
  dependencies: Dependencies,
  onState: (state: ContentCommentsState) => void,
  size = COMMENT_PAGE_SIZE,
) {
  let state = initialState;
  let loadId = 0;
  let submitId = 0;
  let deleteId = 0;
  let loadController: AbortController | null = null;
  let submitController: AbortController | null = null;
  let deleteController: AbortController | null = null;
  let submitInFlight: Promise<boolean> | null = null;
  let deleteInFlight: Promise<boolean> | null = null;
  const likeControllers = new Map<number, AbortController>();
  let stopped = false;
  const publish = (next: ContentCommentsState) => {
    state = next;
    if (!stopped) onState(next);
  };

  const load = async (page: number) => {
    loadController?.abort();
    const controller = new AbortController();
    loadController = controller;
    const id = ++loadId;
    const replacing = page === 0;
    publish(
      replacing
        ? { ...state, status: 'LOADING', items: [], page: 0, hasNext: false, loadingMore: false, error: null }
        : { ...state, loadingMore: true, error: null },
    );
    try {
      const result = await dependencies.list(page, size, controller.signal);
      if (stopped || controller.signal.aborted || id !== loadId) return;
      publish({
        ...state,
        status: 'READY',
        items: replacing ? result.items : dedupeCommentsById(state.items, result.items),
        page: result.page,
        hasNext: result.hasNext,
        totalCount: result.totalElements,
        loadingMore: false,
        error: null,
      });
    } catch (error) {
      if (stopped || controller.signal.aborted || id !== loadId) return;
      publish({
        ...state,
        status: replacing ? 'ERROR' : state.status,
        loadingMore: false,
        error: error instanceof Error ? error : new Error('댓글 조회 실패'),
      });
    }
  };

  return {
    reload: () => load(0),

    loadMore: () => {
      if (state.loadingMore || state.status !== 'READY' || !state.hasNext) {
        return Promise.resolve();
      }
      return load(state.page + 1);
    },

    clearSuccess: () => publish({ ...state, successMessage: null }),

    /**
     * 등록. 목록 맨 앞에 서버가 돌려준 항목을 붙인다(최신순 정렬과 같은 방향).
     * 낙관적 추가를 하지 않으므로 실패하면 목록이 그대로다.
     */
    create: (body: string): Promise<boolean> => {
      if (submitInFlight) return submitInFlight;
      if (stopped) return Promise.resolve(false);
      const bodyError = validateCommentBody(body);
      if (bodyError) {
        publish({ ...state, bodyError });
        return Promise.resolve(false);
      }
      const controller = new AbortController();
      submitController = controller;
      const id = ++submitId;
      publish({ ...state, submitting: true, bodyError: null, error: null });
      const operation = dependencies
        .create(body.trim(), controller.signal)
        .then((created) => {
          if (stopped || controller.signal.aborted || id !== submitId) return false;
          publish({
            ...state,
            status: 'READY',
            items: dedupeCommentsById([created], state.items),
            totalCount: state.totalCount + 1,
            submitting: false,
            bodyError: null,
            error: null,
            successMessage: '댓글을 등록했어요.',
          });
          return true;
        })
        .catch((error: unknown) => {
          if (stopped || controller.signal.aborted || id !== submitId) return false;
          publish({
            ...state,
            submitting: false,
            error: error instanceof Error ? error : new Error('댓글 등록 실패'),
          });
          return false;
        })
        .finally(() => {
          if (submitController === controller) submitController = null;
          if (submitInFlight === operation) submitInFlight = null;
        });
      submitInFlight = operation;
      return operation;
    },

    clearBodyError: () => publish({ ...state, bodyError: null }),

    openDelete: (deleteTarget: ContentComment) => {
      deleteId += 1;
      deleteController?.abort();
      deleteController = null;
      deleteInFlight = null;
      publish({ ...state, deleteTarget, deleting: false, error: null });
    },

    closeDelete: () => {
      if (state.deleting) return;
      deleteId += 1;
      deleteController?.abort();
      deleteController = null;
      deleteInFlight = null;
      publish({ ...state, deleteTarget: null, error: null });
    },

    confirmDelete: (): Promise<boolean> => {
      if (deleteInFlight) return deleteInFlight;
      if (stopped || !state.deleteTarget) return Promise.resolve(false);
      const target = state.deleteTarget;
      const controller = new AbortController();
      deleteController = controller;
      const id = ++deleteId;
      publish({ ...state, deleting: true, error: null });
      const operation = dependencies
        .remove(target.id, controller.signal)
        .then(() => {
          if (stopped || controller.signal.aborted || id !== deleteId) return false;
          publish({
            ...state,
            items: state.items.filter((comment) => comment.id !== target.id),
            totalCount: Math.max(0, state.totalCount - 1),
            deleteTarget: null,
            deleting: false,
            error: null,
            successMessage: '댓글을 삭제했어요.',
          });
          return true;
        })
        .catch((error: unknown) => {
          if (stopped || controller.signal.aborted || id !== deleteId) return false;
          publish({
            ...state,
            deleting: false,
            error: error instanceof Error ? error : new Error('댓글 삭제 실패'),
          });
          return false;
        })
        .finally(() => {
          if (deleteController === controller) deleteController = null;
          if (deleteInFlight === operation) deleteInFlight = null;
        });
      deleteInFlight = operation;
      return operation;
    },

    /**
     * 좋아요 토글. 같은 댓글에 대한 요청이 진행 중이면 무시한다(연타 흡수).
     * 카운트는 서버가 돌려준 실제값으로만 갱신한다 — 낙관적 증감을 하지 않는다.
     */
    toggleLike: (comment: ContentComment): Promise<boolean> => {
      if (stopped || likeControllers.has(comment.id)) return Promise.resolve(false);
      const controller = new AbortController();
      likeControllers.set(comment.id, controller);
      publish({ ...state, pendingLikeIds: [...state.pendingLikeIds, comment.id], error: null });
      const settle = (next: ContentCommentsState) =>
        publish({
          ...next,
          pendingLikeIds: next.pendingLikeIds.filter((id) => id !== comment.id),
        });
      return dependencies
        .setLike(comment.id, !comment.likedByMe, controller.signal)
        .then((result) => {
          if (stopped || controller.signal.aborted) return false;
          settle({
            ...state,
            items: state.items.map((item) =>
              item.id === comment.id
                ? { ...item, likedByMe: result.liked, likeCount: result.likeCount }
                : item,
            ),
            error: null,
          });
          return true;
        })
        .catch((error: unknown) => {
          if (stopped || controller.signal.aborted) return false;
          settle({
            ...state,
            error: error instanceof Error ? error : new Error('좋아요 변경 실패'),
          });
          return false;
        })
        .finally(() => {
          if (likeControllers.get(comment.id) === controller) likeControllers.delete(comment.id);
        });
    },

    stop: () => {
      stopped = true;
      loadId += 1;
      submitId += 1;
      deleteId += 1;
      loadController?.abort();
      submitController?.abort();
      deleteController?.abort();
      likeControllers.forEach((controller) => controller.abort());
      likeControllers.clear();
      submitInFlight = null;
      deleteInFlight = null;
    },
  };
}

export function useContentComments(target: ContentTarget | null) {
  const [state, setState] = useState(initialState);
  const sessionRef = useRef<ReturnType<typeof createContentCommentsSession> | null>(null);
  const targetKey = target ? `${target.type}:${target.id}` : '';

  useEffect(() => {
    if (!target) return;
    const session = createContentCommentsSession(
      {
        list: (page, size, signal) => contentCommentsApi.getList(target, page, size, signal),
        create: (body, signal) => contentCommentsApi.create(target, body, signal),
        remove: (commentId, signal) => contentCommentsApi.remove(commentId, signal),
        setLike: (commentId, liked, signal) =>
          contentCommentsApi.setLike(commentId, liked, signal),
      },
      setState,
    );
    sessionRef.current = session;
    void session.reload();
    return () => {
      sessionRef.current = null;
      session.stop();
    };
    // target은 {type, id} 리터럴로 넘어오므로 값으로 비교한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [targetKey]);

  return {
    state,
    reload: useCallback(() => sessionRef.current?.reload(), []),
    loadMore: useCallback(() => sessionRef.current?.loadMore(), []),
    create: useCallback(
      (body: string) => sessionRef.current?.create(body) ?? Promise.resolve(false),
      [],
    ),
    clearBodyError: useCallback(() => sessionRef.current?.clearBodyError(), []),
    openDelete: useCallback(
      (comment: ContentComment) => sessionRef.current?.openDelete(comment),
      [],
    ),
    closeDelete: useCallback(() => sessionRef.current?.closeDelete(), []),
    confirmDelete: useCallback(
      () => sessionRef.current?.confirmDelete() ?? Promise.resolve(false),
      [],
    ),
    toggleLike: useCallback(
      (comment: ContentComment) => sessionRef.current?.toggleLike(comment) ?? Promise.resolve(false),
      [],
    ),
    clearSuccess: useCallback(() => sessionRef.current?.clearSuccess(), []),
  };
}
