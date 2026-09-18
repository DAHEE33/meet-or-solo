import { describe, expect, it, vi } from 'vitest';
import {
  COMMENT_BODY_MAX_LENGTH,
  createContentCommentsSession,
  dedupeCommentsById,
  validateCommentBody,
  type ContentCommentsState,
} from './useContentComments';
import type { ContentComment, ContentCommentPage } from '../api/contentComments';

const comment = (id: number, overrides: Partial<ContentComment> = {}): ContentComment => ({
  id,
  nickname: `회원${id}`,
  body: `댓글 ${id}`,
  likeCount: 0,
  likedByMe: false,
  mine: false,
  createdAt: '2026-09-07T10:00:00+09:00',
  ...overrides,
});

const page = (items: ContentComment[], overrides: Partial<ContentCommentPage> = {}): ContentCommentPage => ({
  items,
  page: 0,
  size: 20,
  totalElements: items.length,
  totalPages: 1,
  hasNext: false,
  ...overrides,
});

const deps = (overrides: Partial<Parameters<typeof createContentCommentsSession>[0]> = {}) => ({
  list: vi.fn().mockResolvedValue(page([])),
  create: vi.fn().mockResolvedValue(comment(1, { mine: true })),
  remove: vi.fn().mockResolvedValue(undefined),
  setLike: vi.fn().mockResolvedValue({ liked: true, likeCount: 1 }),
  ...overrides,
});

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((ok, no) => { resolve = ok; reject = no; });
  return { promise, resolve, reject };
};

describe('validateCommentBody', () => {
  it('빈 문자열과 공백만 있는 본문을 거절한다', () => {
    expect(validateCommentBody('')).toContain('입력');
    expect(validateCommentBody('   \n  ')).toContain('입력');
  });

  it('trim 후 1자면 통과한다', () => {
    expect(validateCommentBody('  좋  ')).toBeNull();
  });

  it('trim 후 500자는 통과하고 501자는 거절한다', () => {
    expect(validateCommentBody('a'.repeat(COMMENT_BODY_MAX_LENGTH))).toBeNull();
    expect(validateCommentBody(` ${'a'.repeat(COMMENT_BODY_MAX_LENGTH)} `)).toBeNull();
    expect(validateCommentBody('a'.repeat(COMMENT_BODY_MAX_LENGTH + 1))).toContain('500');
  });
});

describe('dedupeCommentsById', () => {
  it('id가 겹치는 항목을 버리고 순서를 유지한다', () => {
    const merged = dedupeCommentsById([comment(3), comment(2)], [comment(2), comment(1)]);
    expect(merged.map((item) => item.id)).toEqual([3, 2, 1]);
  });

  it('겹치는 항목이 없으면 그대로 이어붙인다', () => {
    expect(dedupeCommentsById([comment(3)], [comment(2)]).map((item) => item.id)).toEqual([3, 2]);
  });

  it('같은 페이지 안에서 중복이 와도 한 번만 남긴다', () => {
    expect(dedupeCommentsById([], [comment(1), comment(1)]).map((item) => item.id)).toEqual([1]);
  });
});

describe('createContentCommentsSession 목록', () => {
  it('loading, 목록, 오류와 재시도를 상태로 제공한다', async () => {
    const states: ContentCommentsState[] = [];
    const list = vi.fn()
      .mockRejectedValueOnce(new Error('fail'))
      .mockResolvedValueOnce(page([comment(2), comment(1)], { totalElements: 2 }));
    const session = createContentCommentsSession(deps({ list }), (state) => states.push(state));

    await session.reload();
    expect(states.map((state) => state.status)).toEqual(['LOADING', 'ERROR']);

    await session.reload();
    expect(states.at(-1)).toMatchObject({ status: 'READY', totalCount: 2 });
    expect(states.at(-1)?.items.map((item) => item.id)).toEqual([2, 1]);
  });

  it('다음 페이지를 이어붙일 때 id가 겹치는 항목을 버린다', async () => {
    const states: ContentCommentsState[] = [];
    const list = vi.fn()
      .mockResolvedValueOnce(page([comment(3), comment(2)], { hasNext: true, totalElements: 3 }))
      .mockResolvedValueOnce(page([comment(2), comment(1)], { page: 1, totalElements: 3 }));
    const session = createContentCommentsSession(deps({ list }), (state) => states.push(state));

    await session.reload();
    await session.loadMore();

    expect(states.at(-1)?.items.map((item) => item.id)).toEqual([3, 2, 1]);
  });

  it('더 받을 것이 없으면 loadMore가 요청하지 않는다', async () => {
    const list = vi.fn().mockResolvedValue(page([comment(1)]));
    const session = createContentCommentsSession(deps({ list }), () => {});
    await session.reload();
    await session.loadMore();
    expect(list).toHaveBeenCalledOnce();
  });
});

describe('createContentCommentsSession 등록', () => {
  it('본문 검증 실패는 서버를 호출하지 않고 bodyError만 남긴다', async () => {
    const states: ContentCommentsState[] = [];
    const dependencies = deps();
    const session = createContentCommentsSession(dependencies, (state) => states.push(state));
    await session.reload();

    await expect(session.create('   ')).resolves.toBe(false);
    expect(dependencies.create).not.toHaveBeenCalled();
    expect(states.at(-1)?.bodyError).toContain('입력');
  });

  it('이중 제출을 한 요청으로 수렴하고 성공하면 맨 앞에 추가한다', async () => {
    const states: ContentCommentsState[] = [];
    const pending = deferred<ContentComment>();
    const create = vi.fn().mockReturnValue(pending.promise);
    const list = vi.fn().mockResolvedValue(page([comment(1)], { totalElements: 1 }));
    const session = createContentCommentsSession(deps({ list, create }), (state) => states.push(state));
    await session.reload();

    const first = session.create('새 댓글');
    const second = session.create('새 댓글');
    expect(create).toHaveBeenCalledOnce();
    expect(create).toHaveBeenCalledWith('새 댓글', expect.any(AbortSignal));

    pending.resolve(comment(2, { mine: true }));
    await Promise.all([first, second]);

    expect(states.at(-1)?.items.map((item) => item.id)).toEqual([2, 1]);
    expect(states.at(-1)).toMatchObject({ totalCount: 2, submitting: false });
  });

  it('등록 실패는 목록을 유지하고 재시도할 수 있다', async () => {
    const states: ContentCommentsState[] = [];
    const create = vi.fn()
      .mockRejectedValueOnce(new Error('fail'))
      .mockResolvedValueOnce(comment(2, { mine: true }));
    const list = vi.fn().mockResolvedValue(page([comment(1)]));
    const session = createContentCommentsSession(deps({ list, create }), (state) => states.push(state));
    await session.reload();

    await expect(session.create('본문')).resolves.toBe(false);
    expect(states.at(-1)?.items.map((item) => item.id)).toEqual([1]);
    expect(states.at(-1)?.submitting).toBe(false);

    await expect(session.create('본문')).resolves.toBe(true);
    expect(create).toHaveBeenCalledTimes(2);
  });
});

describe('createContentCommentsSession 삭제', () => {
  it('확인 후 성공한 댓글만 목록에서 제거한다', async () => {
    const states: ContentCommentsState[] = [];
    const list = vi.fn().mockResolvedValue(page([comment(2, { mine: true }), comment(1)], { totalElements: 2 }));
    const dependencies = deps({ list });
    const session = createContentCommentsSession(dependencies, (state) => states.push(state));
    await session.reload();

    session.openDelete(comment(2, { mine: true }));
    await expect(session.confirmDelete()).resolves.toBe(true);

    expect(dependencies.remove).toHaveBeenCalledWith(2, expect.any(AbortSignal));
    expect(states.at(-1)?.items.map((item) => item.id)).toEqual([1]);
    expect(states.at(-1)).toMatchObject({ deleteTarget: null, totalCount: 1 });
  });

  it('이중 확인을 한 요청으로 수렴한다', async () => {
    const pending = deferred<void>();
    const remove = vi.fn().mockReturnValue(pending.promise);
    const list = vi.fn().mockResolvedValue(page([comment(2, { mine: true })]));
    const session = createContentCommentsSession(deps({ list, remove }), () => {});
    await session.reload();

    session.openDelete(comment(2, { mine: true }));
    const first = session.confirmDelete();
    const second = session.confirmDelete();
    expect(remove).toHaveBeenCalledOnce();

    pending.resolve();
    await Promise.all([first, second]);
  });
});

describe('createContentCommentsSession 좋아요', () => {
  it('서버가 돌려준 실제 카운트로만 갱신한다', async () => {
    const states: ContentCommentsState[] = [];
    const list = vi.fn().mockResolvedValue(page([comment(1, { likeCount: 4 })]));
    const setLike = vi.fn().mockResolvedValue({ liked: true, likeCount: 5 });
    const session = createContentCommentsSession(deps({ list, setLike }), (state) => states.push(state));
    await session.reload();

    await session.toggleLike(comment(1, { likeCount: 4 }));

    expect(setLike).toHaveBeenCalledWith(1, true, expect.any(AbortSignal));
    expect(states.at(-1)?.items[0]).toMatchObject({ likedByMe: true, likeCount: 5 });
    expect(states.at(-1)?.pendingLikeIds).toEqual([]);
  });

  it('이미 좋아요한 댓글은 해제를 요청한다', async () => {
    const list = vi.fn().mockResolvedValue(page([comment(1, { likedByMe: true, likeCount: 1 })]));
    const setLike = vi.fn().mockResolvedValue({ liked: false, likeCount: 0 });
    const session = createContentCommentsSession(deps({ list, setLike }), () => {});
    await session.reload();

    await session.toggleLike(comment(1, { likedByMe: true, likeCount: 1 }));
    expect(setLike).toHaveBeenCalledWith(1, false, expect.any(AbortSignal));
  });

  it('같은 댓글 연타는 한 요청으로 흡수한다', async () => {
    const pending = deferred<{ liked: boolean; likeCount: number }>();
    const setLike = vi.fn().mockReturnValue(pending.promise);
    const list = vi.fn().mockResolvedValue(page([comment(1)]));
    const session = createContentCommentsSession(deps({ list, setLike }), () => {});
    await session.reload();

    const first = session.toggleLike(comment(1));
    await expect(session.toggleLike(comment(1))).resolves.toBe(false);
    expect(setLike).toHaveBeenCalledOnce();

    pending.resolve({ liked: true, likeCount: 1 });
    await first;
  });

  it('실패해도 목록 카운트를 바꾸지 않는다', async () => {
    const states: ContentCommentsState[] = [];
    const list = vi.fn().mockResolvedValue(page([comment(1, { likeCount: 4 })]));
    const setLike = vi.fn().mockRejectedValue(new Error('fail'));
    const session = createContentCommentsSession(deps({ list, setLike }), (state) => states.push(state));
    await session.reload();

    await expect(session.toggleLike(comment(1, { likeCount: 4 }))).resolves.toBe(false);
    expect(states.at(-1)?.items[0]).toMatchObject({ likedByMe: false, likeCount: 4 });
    expect(states.at(-1)?.error).toBeInstanceOf(Error);
  });
});

describe('createContentCommentsSession stop', () => {
  it('stop 뒤 늦게 도착한 목록 응답을 무시한다', async () => {
    const late = deferred<ContentCommentPage>();
    const states: ContentCommentsState[] = [];
    const session = createContentCommentsSession(
      deps({ list: () => late.promise }),
      (state) => states.push(state),
    );

    const loading = session.reload();
    session.stop();
    late.resolve(page([comment(1)]));
    await loading;

    expect(states).toHaveLength(1);
    expect(states[0].status).toBe('LOADING');
  });

  it('stop 뒤 늦게 도착한 등록 실패를 무시한다', async () => {
    const late = deferred<ContentComment>();
    const states: ContentCommentsState[] = [];
    const session = createContentCommentsSession(
      deps({ create: () => late.promise }),
      (state) => states.push(state),
    );
    await session.reload();
    const submitting = session.create('본문');
    const publishedBeforeStop = states.length;

    session.stop();
    late.reject(new Error('late'));
    await expect(submitting).resolves.toBe(false);

    expect(states).toHaveLength(publishedBeforeStop);
  });
});
