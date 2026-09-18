import { useCallback, useEffect, useRef, useState } from 'react';

// 20개씩 불러와 스크롤할 때마다 이어붙이는 목록 세션.
// 로직을 컴포넌트에서 분리한 이유: 이 프로젝트의 vitest는 node 환경이고 jsdom/testing-library가
// 없어서, 렌더링 없이 순수 함수로 검증할 수 있어야 한다(useAdminMembers와 같은 패턴).
//
// 서버 페이징은 offset(page/size) 방식이다. 동기화가 6~12시간 주기라 스크롤 중 데이터가 바뀔
// 확률이 낮아 offset의 중복/누락 위험을 감수했다
// (docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md 5.3).

export const INFINITE_LIST_PAGE_SIZE = 20;

export type InfiniteListState<T> = {
  status: 'LOADING' | 'READY' | 'ERROR';
  items: T[];
  page: number;
  hasNext: boolean;
  /** 첫 페이지 로딩과 구분해야 스켈레톤과 하단 스피너를 다르게 보여줄 수 있다. */
  loadingMore: boolean;
};

export type PageResult<T> = { items: T[]; page: number; hasNext: boolean };

type Dependencies<T, Q> = {
  fetchPage: (query: Q, page: number, size: number, signal: AbortSignal) => Promise<PageResult<T>>;
};

export function initialInfiniteListState<T>(): InfiniteListState<T> {
  return { status: 'LOADING', items: [], page: 0, hasNext: false, loadingMore: false };
}

export function createInfiniteListSession<T, Q>(
  dependencies: Dependencies<T, Q>,
  initialQuery: Q,
  onState: (state: InfiniteListState<T>) => void,
  size = INFINITE_LIST_PAGE_SIZE,
) {
  let state = initialInfiniteListState<T>();
  let query = initialQuery;
  let requestId = 0;
  let controller: AbortController | null = null;
  let stopped = false;

  const publish = (next: InfiniteListState<T>) => {
    state = next;
    if (!stopped) onState(next);
  };

  // page 0은 목록을 교체하고, 그 이후는 이어붙인다. 필터가 바뀌면 항상 page 0으로 돌아오므로
  // 이전 필터의 결과가 남지 않는다.
  const load = async (page: number) => {
    controller?.abort();
    const current = new AbortController();
    controller = current;
    const id = ++requestId;
    const replacing = page === 0;
    publish(
      replacing
        ? { ...state, status: 'LOADING', items: [], page: 0, hasNext: false, loadingMore: false }
        : { ...state, loadingMore: true },
    );
    try {
      const result = await dependencies.fetchPage(query, page, size, current.signal);
      if (stopped || current.signal.aborted || id !== requestId) return;
      publish({
        status: 'READY',
        items: replacing ? result.items : [...state.items, ...result.items],
        page: result.page,
        hasNext: result.hasNext,
        loadingMore: false,
      });
    } catch {
      if (stopped || current.signal.aborted || id !== requestId) return;
      publish({ ...state, status: 'ERROR', loadingMore: false });
    }
  };

  return {
    reload: () => load(0),
    /** 필터·검색·정렬이 바뀔 때 호출한다. 누적된 목록을 버리고 처음부터 다시 받는다. */
    applyQuery: (next: Q) => {
      query = next;
      return load(0);
    },
    /**
     * 스크롤이 끝에 닿을 때 호출한다. IntersectionObserver 콜백은 연속으로 여러 번 발생하므로
     * 진행 중이거나 더 받을 것이 없으면 아무것도 하지 않는다.
     */
    loadMore: () => {
      if (state.loadingMore || state.status === 'LOADING' || !state.hasNext) {
        return Promise.resolve();
      }
      return load(state.page + 1);
    },
    stop: () => {
      stopped = true;
      requestId++;
      controller?.abort();
    },
  };
}

export function useInfiniteList<T, Q>(dependencies: Dependencies<T, Q>, query: Q) {
  const [state, setState] = useState(initialInfiniteListState<T>);
  const sessionRef = useRef<ReturnType<typeof createInfiniteListSession<T, Q>> | null>(null);
  // 객체 리터럴로 넘어오는 query를 그대로 의존성에 두면 매 렌더마다 재조회하므로 값으로 비교한다.
  const queryKey = JSON.stringify(query);
  const queryRef = useRef(query);
  queryRef.current = query;

  useEffect(() => {
    const session = createInfiniteListSession(dependencies, queryRef.current, setState);
    sessionRef.current = session;
    void session.reload();
    return () => {
      sessionRef.current = null;
      session.stop();
    };
    // dependencies는 모듈 상수(api 객체)라 재생성되지 않는다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryKey]);

  return {
    state,
    loadMore: useCallback(() => sessionRef.current?.loadMore(), []),
    reload: useCallback(() => sessionRef.current?.reload(), []),
  };
}
