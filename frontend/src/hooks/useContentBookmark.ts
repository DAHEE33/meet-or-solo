import { useCallback, useEffect, useRef, useState } from 'react';
import {
  contentBookmarksApi,
  type BookmarkedContent,
  type ContentEngagement,
  type ContentTarget,
  type ContentTargetType,
} from '../api/contentBookmarks';

// 상세 화면의 찜 상태와 "이 사용자가 로그인했는가"를 함께 들고 있는 세션.
// 로직을 컴포넌트에서 분리한 이유는 useMemberBlocks와 같다 — 이 프로젝트의 vitest는 node
// 환경이고 jsdom이 없어, 렌더링 없이 상태 전이를 검증할 수 있어야 한다.

export type ContentBookmarkState = {
  status: 'LOADING' | 'READY' | 'ERROR';
  bookmarked: boolean;
  commentCount: number;
  /** docs/27 2.1절 — 로그인 여부는 engagement 응답으로만 판단한다. */
  loggedIn: boolean;
  admin: boolean;
  submitting: boolean;
  error: Error | null;
};

const initialState: ContentBookmarkState = {
  status: 'LOADING',
  bookmarked: false,
  commentCount: 0,
  loggedIn: false,
  admin: false,
  submitting: false,
  error: null,
};

export function initialContentBookmarkState(): ContentBookmarkState {
  return initialState;
}

/**
 * 찜 버튼을 눌렀을 때 무엇을 할지 결정한다.
 *
 * 비로그인 사용자에게 요청을 보내면 서버가 401을 주고 apiClient가 화면째로 `/login`으로
 * 리다이렉트한다(docs/27 2.1). 그래서 요청 전에 화면이 먼저 로그인으로 안내한다.
 * 두 상세 화면이 같은 규칙을 쓰도록 순수 함수로 분리했다.
 */
export function resolveBookmarkAction(
  state: Pick<ContentBookmarkState, 'status' | 'loggedIn'>,
): 'LOGIN' | 'TOGGLE' | 'IGNORE' {
  if (state.status !== 'READY') return 'IGNORE';
  return state.loggedIn ? 'TOGGLE' : 'LOGIN';
}

export function createContentBookmarkSession(
  load: (signal: AbortSignal) => Promise<ContentEngagement>,
  setBookmark: (bookmarked: boolean, signal: AbortSignal) => Promise<{ bookmarked: boolean }>,
  onState: (state: ContentBookmarkState) => void,
) {
  let state = initialState;
  let loadId = 0;
  let submitId = 0;
  let loadController: AbortController | null = null;
  let submitController: AbortController | null = null;
  let inFlight: Promise<boolean> | null = null;
  let stopped = false;
  const publish = (next: ContentBookmarkState) => {
    state = next;
    if (!stopped) onState(next);
  };

  const reload = async () => {
    loadController?.abort();
    const controller = new AbortController();
    loadController = controller;
    const id = ++loadId;
    publish({ ...state, status: 'LOADING', error: null });
    try {
      const engagement = await load(controller.signal);
      if (stopped || controller.signal.aborted || id !== loadId) return;
      publish({
        ...state,
        status: 'READY',
        bookmarked: engagement.bookmarked,
        commentCount: engagement.commentCount,
        loggedIn: engagement.viewer.loggedIn,
        admin: engagement.viewer.admin,
        error: null,
      });
    } catch (error) {
      if (stopped || controller.signal.aborted || id !== loadId) return;
      publish({
        ...state,
        status: 'ERROR',
        error: error instanceof Error ? error : new Error('찜 상태 조회 실패'),
      });
    }
  };

  return {
    reload,
    /**
     * 비로그인 사용자는 호출 자체를 하지 않는다. 호출하면 서버가 401을 주고 apiClient가
     * 화면째로 `/login`으로 리다이렉트하므로, 화면이 먼저 로그인 안내로 보낸다(docs/27 7.1).
     */
    toggle: (): Promise<boolean> => {
      if (inFlight) return inFlight;
      if (stopped || !state.loggedIn || state.status !== 'READY') return Promise.resolve(false);
      const next = !state.bookmarked;
      const controller = new AbortController();
      submitController = controller;
      const id = ++submitId;
      publish({ ...state, submitting: true, error: null });
      const operation = setBookmark(next, controller.signal)
        .then((result) => {
          if (stopped || controller.signal.aborted || id !== submitId) return false;
          // 낙관적 갱신을 하지 않는다. 서버가 확인해준 상태만 반영한다.
          publish({ ...state, bookmarked: result.bookmarked, submitting: false, error: null });
          return true;
        })
        .catch((error: unknown) => {
          if (stopped || controller.signal.aborted || id !== submitId) return false;
          publish({
            ...state,
            submitting: false,
            error: error instanceof Error ? error : new Error('찜 변경 실패'),
          });
          return false;
        })
        .finally(() => {
          if (submitController === controller) submitController = null;
          if (inFlight === operation) inFlight = null;
        });
      inFlight = operation;
      return operation;
    },
    stop: () => {
      stopped = true;
      loadId += 1;
      submitId += 1;
      loadController?.abort();
      submitController?.abort();
      inFlight = null;
    },
  };
}

export function useContentBookmark(target: ContentTarget | null) {
  const [state, setState] = useState(initialState);
  const sessionRef = useRef<ReturnType<typeof createContentBookmarkSession> | null>(null);
  const targetKey = target ? `${target.type}:${target.id}` : '';

  useEffect(() => {
    if (!target) return;
    const session = createContentBookmarkSession(
      (signal) => contentBookmarksApi.getEngagement(target, signal),
      (bookmarked, signal) => contentBookmarksApi.setBookmark(target, bookmarked, signal),
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
    toggle: useCallback(() => sessionRef.current?.toggle() ?? Promise.resolve(false), []),
  };
}

// ─────────────────────────────────────────────────────────────
// 내 찜 목록(/mypage/favorites)
// ─────────────────────────────────────────────────────────────

export type BookmarkedContentsState = {
  status: 'LOADING' | 'READY' | 'ERROR';
  type: ContentTargetType;
  items: BookmarkedContent[];
  /** 해제 요청이 진행 중인 대상 id. 같은 항목 연타를 흡수한다. */
  removingId: number | null;
  error: Error | null;
  successMessage: string | null;
};

const initialListState: BookmarkedContentsState = {
  status: 'LOADING',
  type: 'FESTIVAL',
  items: [],
  removingId: null,
  error: null,
  successMessage: null,
};

/** 찜 항목이 가리키는 대상 id. 축제/관광지 중 채워진 쪽을 쓴다. */
export function bookmarkedContentId(item: BookmarkedContent): number | null {
  return item.festival?.id ?? item.tourPlace?.id ?? null;
}

/** 찜 항목의 표시 이름. 목록·안내 문구에 함께 쓴다. */
export function bookmarkedContentTitle(item: BookmarkedContent): string {
  return item.festival?.title ?? item.tourPlace?.title ?? '';
}

/**
 * 운영자가 숨긴(`HIDDEN`) 대상은 목록에서 제외한다(docs/27 7.3).
 * 서버도 제외하지만, 공개 조회에서 사라진 대상이 찜 목록에만 남는 상황을 화면에서도 막는다.
 * `INACTIVE`·종료 대상은 남긴다 — 사용자가 명시적으로 저장한 항목이다.
 */
export function visibleBookmarkedContents(
  items: readonly BookmarkedContent[],
): BookmarkedContent[] {
  return items.filter((item) => {
    if (bookmarkedContentId(item) === null) return false;
    if (item.festival) return item.festival.status !== 'HIDDEN';
    if (item.tourPlace) return item.tourPlace.status !== 'HIDDEN';
    return false;
  });
}

export function createBookmarkedContentsSession(
  load: (
    type: ContentTargetType,
    signal: AbortSignal,
  ) => Promise<{ items: BookmarkedContent[] }>,
  setBookmark: (target: ContentTarget, bookmarked: boolean, signal: AbortSignal) => Promise<unknown>,
  onState: (state: BookmarkedContentsState) => void,
) {
  let state = initialListState;
  let loadId = 0;
  let loadController: AbortController | null = null;
  const removeControllers = new Map<number, AbortController>();
  let stopped = false;
  const publish = (next: BookmarkedContentsState) => {
    state = next;
    if (!stopped) onState(next);
  };

  const load_ = async (type: ContentTargetType) => {
    loadController?.abort();
    const controller = new AbortController();
    loadController = controller;
    const id = ++loadId;
    publish({ ...state, status: 'LOADING', type, items: [], error: null });
    try {
      const page = await load(type, controller.signal);
      if (stopped || controller.signal.aborted || id !== loadId) return;
      publish({
        ...state,
        status: 'READY',
        type,
        items: visibleBookmarkedContents(page.items),
        error: null,
      });
    } catch (error) {
      if (stopped || controller.signal.aborted || id !== loadId) return;
      publish({
        ...state,
        status: 'ERROR',
        type,
        error: error instanceof Error ? error : new Error('찜 목록 조회 실패'),
      });
    }
  };

  return {
    reload: () => load_(state.type),
    changeType: (type: ContentTargetType) => load_(type),
    clearSuccess: () => publish({ ...state, successMessage: null }),

    /** 해제. 낙관적 갱신을 하지 않고 서버가 성공한 항목만 목록에서 제거한다. */
    remove: (item: BookmarkedContent): Promise<boolean> => {
      const contentId = bookmarkedContentId(item);
      if (stopped || contentId === null || removeControllers.has(contentId)) {
        return Promise.resolve(false);
      }
      const controller = new AbortController();
      removeControllers.set(contentId, controller);
      publish({ ...state, removingId: contentId, error: null });
      return setBookmark({ type: item.targetType, id: contentId }, false, controller.signal)
        .then(() => {
          if (stopped || controller.signal.aborted) return false;
          publish({
            ...state,
            items: state.items.filter((current) => bookmarkedContentId(current) !== contentId),
            removingId: null,
            error: null,
            successMessage: `${bookmarkedContentTitle(item)} 찜을 해제했어요.`,
          });
          return true;
        })
        .catch((error: unknown) => {
          if (stopped || controller.signal.aborted) return false;
          publish({
            ...state,
            removingId: null,
            error: error instanceof Error ? error : new Error('찜 해제 실패'),
          });
          return false;
        })
        .finally(() => {
          if (removeControllers.get(contentId) === controller) removeControllers.delete(contentId);
        });
    },

    stop: () => {
      stopped = true;
      loadId += 1;
      loadController?.abort();
      removeControllers.forEach((controller) => controller.abort());
      removeControllers.clear();
    },
  };
}

export function useBookmarkedContents() {
  const [state, setState] = useState(initialListState);
  const sessionRef = useRef<ReturnType<typeof createBookmarkedContentsSession> | null>(null);

  useEffect(() => {
    const session = createBookmarkedContentsSession(
      (type, signal) => contentBookmarksApi.getMine(type, 0, 50, signal),
      (target, bookmarked, signal) => contentBookmarksApi.setBookmark(target, bookmarked, signal),
      setState,
    );
    sessionRef.current = session;
    void session.reload();
    return () => {
      sessionRef.current = null;
      session.stop();
    };
  }, []);

  return {
    state,
    reload: useCallback(() => sessionRef.current?.reload(), []),
    changeType: useCallback(
      (type: ContentTargetType) => sessionRef.current?.changeType(type),
      [],
    ),
    remove: useCallback(
      (item: BookmarkedContent) => sessionRef.current?.remove(item) ?? Promise.resolve(false),
      [],
    ),
    clearSuccess: useCallback(() => sessionRef.current?.clearSuccess(), []),
  };
}
