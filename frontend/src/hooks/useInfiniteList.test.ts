import { describe, expect, it, vi } from 'vitest';
import {
  createInfiniteListSession,
  initialInfiniteListState,
  type InfiniteListState,
  type PageResult,
} from './useInfiniteList';

type Item = { id: number };
type Query = { keyword: string };

function page(ids: number[], pageIndex: number, hasNext: boolean): PageResult<Item> {
  return { items: ids.map((id) => ({ id })), page: pageIndex, hasNext };
}

function session(
  fetchPage: (query: Query, pageIndex: number, size: number) => Promise<PageResult<Item>>,
  initialQuery: Query = { keyword: '' },
) {
  const states: InfiniteListState<Item>[] = [];
  const instance = createInfiniteListSession<Item, Query>(
    { fetchPage: (query, pageIndex, size) => fetchPage(query, pageIndex, size) },
    initialQuery,
    (state) => states.push(state),
  );
  return { instance, states, last: () => states[states.length - 1] };
}

describe('createInfiniteListSession', () => {
  it('첫 페이지는 목록을 교체한다', async () => {
    const { instance, last } = session(async () => page([1, 2], 0, true));

    await instance.reload();

    expect(last().items.map((item) => item.id)).toEqual([1, 2]);
    expect(last().status).toBe('READY');
    expect(last().hasNext).toBe(true);
  });

  it('loadMore는 다음 페이지를 이어붙인다', async () => {
    const fetchPage = vi.fn(async (_query: Query, pageIndex: number) =>
      pageIndex === 0 ? page([1, 2], 0, true) : page([3, 4], 1, false),
    );
    const { instance, last } = session(fetchPage);

    await instance.reload();
    await instance.loadMore();

    expect(last().items.map((item) => item.id)).toEqual([1, 2, 3, 4]);
    expect(last().page).toBe(1);
    expect(last().hasNext).toBe(false);
  });

  it('hasNext가 false면 loadMore가 요청을 보내지 않는다', async () => {
    const fetchPage = vi.fn(async () => page([1], 0, false));
    const { instance } = session(fetchPage);

    await instance.reload();
    await instance.loadMore();
    await instance.loadMore();

    expect(fetchPage).toHaveBeenCalledTimes(1);
  });

  it('로딩 중에는 loadMore가 중복 요청을 보내지 않는다', async () => {
    // IntersectionObserver 콜백은 요소가 보이는 동안 연속으로 발생하므로 이 가드가 필요하다.
    const deferred: { resolve?: (value: PageResult<Item>) => void } = {};
    const fetchPage = vi.fn(
      (_query: Query, pageIndex: number) =>
        pageIndex === 0
          ? Promise.resolve(page([1], 0, true))
          : new Promise<PageResult<Item>>((resolve) => {
              deferred.resolve = resolve;
            }),
    );
    const { instance } = session(fetchPage);

    await instance.reload();
    const first = instance.loadMore();
    void instance.loadMore();
    void instance.loadMore();

    expect(fetchPage).toHaveBeenCalledTimes(2);
    deferred.resolve?.(page([2], 1, false));
    await first;
  });

  it('applyQuery는 누적된 목록을 버리고 처음부터 다시 받는다', async () => {
    const fetchPage = vi.fn(async (query: Query, pageIndex: number) =>
      query.keyword === '' ? page([1, 2], pageIndex, true) : page([9], 0, false),
    );
    const { instance, last } = session(fetchPage);

    await instance.reload();
    await instance.loadMore();
    await instance.applyQuery({ keyword: '축제' });

    // 필터를 바꿨으니 이전 결과가 남아 있으면 안 된다.
    expect(last().items.map((item) => item.id)).toEqual([9]);
    expect(last().page).toBe(0);
  });

  it('applyQuery는 바뀐 query를 fetchPage에 넘긴다', async () => {
    const fetchPage = vi.fn(async () => page([], 0, false));
    const { instance } = session(fetchPage);

    await instance.applyQuery({ keyword: '강릉' });

    expect(fetchPage).toHaveBeenCalledWith({ keyword: '강릉' }, 0, 20);
  });

  it('실패하면 ERROR 상태가 되고 이전 목록은 유지된다', async () => {
    let shouldFail = false;
    const { instance, last } = session(async (_query, pageIndex) => {
      if (shouldFail) throw new Error('boom');
      return page([1], pageIndex, true);
    });

    await instance.reload();
    shouldFail = true;
    await instance.loadMore();

    expect(last().status).toBe('ERROR');
    expect(last().items.map((item) => item.id)).toEqual([1]);
    expect(last().loadingMore).toBe(false);
  });

  it('stop 이후에는 상태를 더 발행하지 않는다', async () => {
    const { instance, states } = session(async () => page([1], 0, true));

    instance.stop();
    await instance.reload();

    expect(states).toHaveLength(0);
  });

  it('기본 페이지 크기는 20이다', async () => {
    const fetchPage = vi.fn(async () => page([], 0, false));
    const { instance } = session(fetchPage);

    await instance.reload();

    expect(fetchPage).toHaveBeenCalledWith({ keyword: '' }, 0, 20);
  });
});

describe('initialInfiniteListState', () => {
  it('로딩 상태로 시작한다', () => {
    expect(initialInfiniteListState<Item>()).toEqual({
      status: 'LOADING',
      items: [],
      page: 0,
      hasNext: false,
      loadingMore: false,
    });
  });
});
