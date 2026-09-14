import { useCallback, useEffect, useRef, useState } from 'react';
import { contentBookmarksApi, type ContentTarget } from '../api/contentBookmarks';

// 목록 카드에서 바로 누르는 찜 토글 세션.
//
// 상세 화면의 useContentBookmark과 분리한 이유: 저쪽은 대상 1건의 engagement를 직접 조회해
// 상태를 만들지만, 목록은 이미 받은 항목의 bookmarkedByMe로 시작하고 "그 뒤에 사용자가 바꾼
// 것"만 덮어쓰면 된다. 목록을 다시 불러오지 않고도 화면이 맞는 상태를 유지해야 한다.
//
// 로직을 컴포넌트에서 분리한 이유는 useContentBookmark과 같다 — 이 프로젝트의 vitest는 node
// 환경이고 jsdom이 없어, 렌더링 없이 상태 전이를 검증할 수 있어야 한다.

/** 사용자가 이 세션에서 바꾼 결과만 담는 덮어쓰기 맵. 키는 `FESTIVAL:12` 형태다. */
export type ListBookmarkOverrides = Record<string, boolean>;

export type ListBookmarkState = {
  overrides: ListBookmarkOverrides;
  /** 요청이 진행 중인 대상. 버튼을 비활성화해 연타를 막는다. */
  pendingKey: string | null;
};

export function targetKey(target: ContentTarget): string {
  return `${target.type}:${target.id}`;
}

export function initialListBookmarkState(): ListBookmarkState {
  return { overrides: {}, pendingKey: null };
}

/**
 * 이 대상의 현재 찜 상태. 사용자가 이 화면에서 바꾼 적이 있으면 그 값이, 없으면 목록 응답이
 * 준 값이 기준이다.
 */
export function resolveBookmarked(
  state: ListBookmarkState,
  target: ContentTarget,
  fromList: boolean,
): boolean {
  const override = state.overrides[targetKey(target)];
  return override === undefined ? fromList : override;
}

/**
 * 목록 카드가 표시할 찜 수. 서버가 준 수에 내 토글 결과만 반영한다.
 *
 * 목록을 다시 부르지 않으므로 정확한 최신 수는 알 수 없지만, 내가 방금 누른 하트가 숫자에
 * 반영되지 않으면 눌리지 않은 것처럼 보인다. ±1은 사용자가 한 행동 그대로라 어긋나지 않는다.
 */
export function resolveBookmarkCount(
  state: ListBookmarkState,
  target: ContentTarget,
  fromList: { bookmarked: boolean; bookmarkCount: number },
): number {
  const override = state.overrides[targetKey(target)];
  if (override === undefined || override === fromList.bookmarked) return fromList.bookmarkCount;
  return Math.max(0, fromList.bookmarkCount + (override ? 1 : -1));
}

type Dependencies = {
  setBookmark: (target: ContentTarget, bookmarked: boolean) => Promise<{ bookmarked: boolean }>;
  /** 비로그인 사용자를 로그인 화면으로 보낸다. 요청은 보내지 않는다. */
  goToLogin: () => void;
};

export function createListBookmarkSession(
  loggedIn: () => boolean,
  onState: (state: ListBookmarkState) => void,
  dependencies: Dependencies,
  /** 이어받을 상태. StrictMode로 세션이 다시 만들어져도 사용자가 바꾼 찜이 사라지지 않는다. */
  initial: ListBookmarkState = initialListBookmarkState(),
) {
  // 재생성 시점에 진행 중이던 요청은 이전 세션과 함께 버려지므로 pending은 이어받지 않는다.
  let state: ListBookmarkState = { overrides: initial.overrides, pendingKey: null };
  let stopped = false;

  const publish = (next: ListBookmarkState) => {
    state = next;
    if (!stopped) onState(next);
  };

  /**
   * 찜을 토글한다.
   *
   * - **비로그인이면 요청을 보내지 않는다.** 서버가 401을 주면 apiClient가 화면째로
   *   `/login`으로 리다이렉트해 사용자가 보던 목록과 스크롤 위치를 잃는다(docs/27 2.1).
   * - **낙관적 갱신을 하지 않는다.** 서버가 돌려준 `bookmarked`로만 상태를 바꾼다. 상세 화면의
   *   찜 토글과 같은 규칙이다(docs/03 "상태 관리와 방어 규칙").
   * - 요청 중인 대상은 `pendingKey`로 막아 연타가 두 번 반영되지 않게 한다.
   */
  const toggle = async (target: ContentTarget, currentlyBookmarked: boolean): Promise<void> => {
    if (!loggedIn()) {
      dependencies.goToLogin();
      return;
    }
    const key = targetKey(target);
    if (state.pendingKey !== null) return;
    publish({ ...state, pendingKey: key });

    try {
      const result = await dependencies.setBookmark(target, !currentlyBookmarked);
      if (stopped) return;
      publish({
        overrides: { ...state.overrides, [key]: result.bookmarked },
        pendingKey: null,
      });
    } catch {
      // 실패하면 상태를 바꾸지 않는다. 하트가 그대로 남아 있는 것이 "눌렸다가 되돌아간" 것보다
      // 덜 혼란스럽고, 목록 화면에는 오류를 띄울 자리가 없다.
      if (stopped) return;
      publish({ ...state, pendingKey: null });
    }
  };

  return {
    toggle,
    stop: () => {
      stopped = true;
    },
  };
}

/**
 * @param loggedIn 목록 응답의 `viewerLoggedIn`. 상세 화면과 달리 목록은 engagement를 부르지
 *                 않으므로 로그인 여부가 목록 응답으로 온다.
 */
export function useListBookmarks(loggedIn: boolean, goToLogin: () => void) {
  const [state, setState] = useState<ListBookmarkState>(initialListBookmarkState);
  const loggedInRef = useRef(loggedIn);
  const goToLoginRef = useRef(goToLogin);
  const stateRef = useRef(state);
  loggedInRef.current = loggedIn;
  goToLoginRef.current = goToLogin;
  stateRef.current = state;

  const sessionRef = useRef<ReturnType<typeof createListBookmarkSession> | null>(null);

  // 세션은 반드시 effect 안에서 만든다. 렌더 중에 만들고 cleanup에서 stop()하면 StrictMode의
  // mount → unmount → mount에서 같은 세션이 영구 정지된 채 남아, 요청은 나가지만 publish가
  // 화면에 닿지 않는다(하트가 새로고침해야만 반영되는 증상). useInfiniteList와 같은 구조다.
  useEffect(() => {
    const session = createListBookmarkSession(
      () => loggedInRef.current,
      setState,
      {
        setBookmark: (target, bookmarked) => contentBookmarksApi.setBookmark(target, bookmarked),
        goToLogin: () => goToLoginRef.current(),
      },
      stateRef.current,
    );
    sessionRef.current = session;
    return () => {
      sessionRef.current = null;
      session.stop();
    };
  }, []);

  const toggle = useCallback(
    (target: ContentTarget, currentlyBookmarked: boolean) =>
      void sessionRef.current?.toggle(target, currentlyBookmarked),
    [],
  );

  return { state, toggle };
}
