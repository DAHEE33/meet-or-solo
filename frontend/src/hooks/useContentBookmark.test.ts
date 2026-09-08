import { describe, expect, it, vi } from 'vitest';
import {
  bookmarkedContentId,
  bookmarkedContentTitle,
  createBookmarkedContentsSession,
  createContentBookmarkSession,
  visibleBookmarkedContents,
  type BookmarkedContentsState,
  type ContentBookmarkState,
} from './useContentBookmark';
import type { BookmarkedContent, ContentEngagement } from '../api/contentBookmarks';
import type { FestivalListItem } from '../api/festivals';
import type { TourPlaceListItem } from '../api/spots';

const engagement = (overrides: Partial<ContentEngagement> = {}): ContentEngagement => ({
  bookmarked: false,
  commentCount: 0,
  viewer: { loggedIn: true, admin: false },
  ...overrides,
});

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((ok, no) => { resolve = ok; reject = no; });
  return { promise, resolve, reject };
};

const festival = (id: number, status: FestivalListItem['status'] = 'ACTIVE'): FestivalListItem => ({
  id,
  contentId: `f${id}`,
  title: `축제${id}`,
  address: '강원 테스트로 1',
  regionCode: '32',
  sigunguCode: '1',
  eventStartDate: '2026-09-01',
  eventEndDate: '2026-09-30',
  status,
  originImageUrl: null,
  thumbnailUrl: null,
  mapX: null,
  mapY: null,
});

const tourPlace = (id: number, status: TourPlaceListItem['status'] = 'ACTIVE'): TourPlaceListItem => ({
  id,
  contentId: `p${id}`,
  contentTypeId: '12',
  title: `관광지${id}`,
  address: '강원 테스트로 2',
  status,
  imageUrl: null,
});

const festivalBookmark = (id: number, status: FestivalListItem['status'] = 'ACTIVE'): BookmarkedContent => ({
  bookmarkedAt: `2026-09-0${id}T10:00:00+09:00`,
  targetType: 'FESTIVAL',
  festival: festival(id, status),
  tourPlace: null,
});

const placeBookmark = (id: number, status: TourPlaceListItem['status'] = 'ACTIVE'): BookmarkedContent => ({
  bookmarkedAt: `2026-09-0${id}T10:00:00+09:00`,
  targetType: 'TOUR_PLACE',
  festival: null,
  tourPlace: tourPlace(id, status),
});

describe('찜 항목 헬퍼', () => {
  it('채워진 대상의 id와 제목을 읽는다', () => {
    expect(bookmarkedContentId(festivalBookmark(3))).toBe(3);
    expect(bookmarkedContentId(placeBookmark(4))).toBe(4);
    expect(bookmarkedContentTitle(festivalBookmark(3))).toBe('축제3');
    expect(bookmarkedContentTitle(placeBookmark(4))).toBe('관광지4');
  });

  it('두 대상이 모두 비어 있으면 id가 없다', () => {
    const broken: BookmarkedContent = {
      bookmarkedAt: '2026-09-01T10:00:00+09:00',
      targetType: 'FESTIVAL',
      festival: null,
      tourPlace: null,
    };
    expect(bookmarkedContentId(broken)).toBeNull();
  });
});

describe('visibleBookmarkedContents', () => {
  it('HIDDEN 대상만 제외하고 INACTIVE·종료는 남긴다', () => {
    const items = [
      festivalBookmark(1, 'HIDDEN'),
      festivalBookmark(2, 'ENDED'),
      festivalBookmark(3, 'INACTIVE'),
      placeBookmark(4, 'HIDDEN'),
      placeBookmark(5, 'INACTIVE'),
    ];
    expect(visibleBookmarkedContents(items).map(bookmarkedContentId)).toEqual([2, 3, 5]);
  });

  it('대상이 비어 있는 항목도 제외한다', () => {
    const broken: BookmarkedContent = {
      bookmarkedAt: '2026-09-01T10:00:00+09:00',
      targetType: 'FESTIVAL',
      festival: null,
      tourPlace: null,
    };
    expect(visibleBookmarkedContents([broken])).toEqual([]);
  });
});

describe('createContentBookmarkSession', () => {
  it('engagement에서 찜·로그인·관리자 여부를 함께 읽는다', async () => {
    const states: ContentBookmarkState[] = [];
    const load = vi.fn().mockResolvedValue(
      engagement({ bookmarked: true, commentCount: 12, viewer: { loggedIn: true, admin: true } }),
    );
    const session = createContentBookmarkSession(load, vi.fn(), (state) => states.push(state));

    await session.reload();

    expect(states.map((state) => state.status)).toEqual(['LOADING', 'READY']);
    expect(states.at(-1)).toMatchObject({
      bookmarked: true, commentCount: 12, loggedIn: true, admin: true,
    });
  });

  it('조회 실패는 ERROR로 두고 재시도할 수 있다', async () => {
    const states: ContentBookmarkState[] = [];
    const load = vi.fn()
      .mockRejectedValueOnce(new Error('fail'))
      .mockResolvedValueOnce(engagement());
    const session = createContentBookmarkSession(load, vi.fn(), (state) => states.push(state));

    await session.reload();
    expect(states.at(-1)?.status).toBe('ERROR');
    await session.reload();
    expect(states.at(-1)?.status).toBe('READY');
  });

  it('비로그인 상태에서는 서버를 호출하지 않는다', async () => {
    const setBookmark = vi.fn();
    const load = vi.fn().mockResolvedValue(engagement({ viewer: { loggedIn: false, admin: false } }));
    const session = createContentBookmarkSession(load, setBookmark, () => {});

    await session.reload();
    await expect(session.toggle()).resolves.toBe(false);

    expect(setBookmark).not.toHaveBeenCalled();
  });

  it('engagement 조회 전에는 토글하지 않는다', async () => {
    const setBookmark = vi.fn();
    const session = createContentBookmarkSession(
      () => deferred<ContentEngagement>().promise,
      setBookmark,
      () => {},
    );
    await expect(session.toggle()).resolves.toBe(false);
    expect(setBookmark).not.toHaveBeenCalled();
  });

  it('서버가 확인해준 상태만 반영하고 연타는 한 요청으로 수렴한다', async () => {
    const states: ContentBookmarkState[] = [];
    const pending = deferred<{ bookmarked: boolean }>();
    const setBookmark = vi.fn().mockReturnValue(pending.promise);
    const session = createContentBookmarkSession(
      vi.fn().mockResolvedValue(engagement()),
      setBookmark,
      (state) => states.push(state),
    );
    await session.reload();

    const first = session.toggle();
    const second = session.toggle();
    expect(setBookmark).toHaveBeenCalledOnce();
    expect(setBookmark).toHaveBeenCalledWith(true, expect.any(AbortSignal));
    // 응답 전에는 낙관적으로 켜지 않는다.
    expect(states.at(-1)).toMatchObject({ bookmarked: false, submitting: true });

    pending.resolve({ bookmarked: true });
    await Promise.all([first, second]);
    expect(states.at(-1)).toMatchObject({ bookmarked: true, submitting: false });
  });

  it('실패하면 이전 찜 상태를 유지한다', async () => {
    const states: ContentBookmarkState[] = [];
    const session = createContentBookmarkSession(
      vi.fn().mockResolvedValue(engagement({ bookmarked: true })),
      vi.fn().mockRejectedValue(new Error('fail')),
      (state) => states.push(state),
    );
    await session.reload();

    await expect(session.toggle()).resolves.toBe(false);
    expect(states.at(-1)).toMatchObject({ bookmarked: true, submitting: false });
    expect(states.at(-1)?.error).toBeInstanceOf(Error);
  });

  it('stop 뒤 늦게 도착한 응답을 무시한다', async () => {
    const late = deferred<ContentEngagement>();
    const states: ContentBookmarkState[] = [];
    const session = createContentBookmarkSession(() => late.promise, vi.fn(), (state) => states.push(state));

    const loading = session.reload();
    session.stop();
    late.resolve(engagement({ bookmarked: true }));
    await loading;

    expect(states).toHaveLength(1);
  });
});

describe('createBookmarkedContentsSession', () => {
  it('탭을 바꾸면 그 타입으로 다시 조회한다', async () => {
    const states: BookmarkedContentsState[] = [];
    const load = vi.fn().mockResolvedValue({ items: [placeBookmark(5)] });
    const session = createBookmarkedContentsSession(load, vi.fn(), (state) => states.push(state));

    await session.changeType('TOUR_PLACE');

    expect(load).toHaveBeenCalledWith('TOUR_PLACE', expect.any(AbortSignal));
    expect(states.at(-1)).toMatchObject({ status: 'READY', type: 'TOUR_PLACE' });
  });

  it('조회 결과에서 HIDDEN 대상을 걸러낸다', async () => {
    const states: BookmarkedContentsState[] = [];
    const load = vi.fn().mockResolvedValue({ items: [festivalBookmark(1, 'HIDDEN'), festivalBookmark(2)] });
    const session = createBookmarkedContentsSession(load, vi.fn(), (state) => states.push(state));

    await session.reload();
    expect(states.at(-1)?.items.map(bookmarkedContentId)).toEqual([2]);
  });

  it('해제는 서버 성공 후에만 목록에서 제거한다', async () => {
    const states: BookmarkedContentsState[] = [];
    const pending = deferred<unknown>();
    const setBookmark = vi.fn().mockReturnValue(pending.promise);
    const load = vi.fn().mockResolvedValue({ items: [festivalBookmark(1), festivalBookmark(2)] });
    const session = createBookmarkedContentsSession(load, setBookmark, (state) => states.push(state));
    await session.reload();

    const removing = session.remove(festivalBookmark(1));
    expect(setBookmark).toHaveBeenCalledWith({ type: 'FESTIVAL', id: 1 }, false, expect.any(AbortSignal));
    // 낙관적으로 제거하지 않는다.
    expect(states.at(-1)?.items.map(bookmarkedContentId)).toEqual([1, 2]);
    expect(states.at(-1)?.removingId).toBe(1);

    pending.resolve(undefined);
    await removing;
    expect(states.at(-1)?.items.map(bookmarkedContentId)).toEqual([2]);
    expect(states.at(-1)?.successMessage).toContain('축제1');
  });

  it('같은 항목 연타는 한 요청으로 흡수한다', async () => {
    const pending = deferred<unknown>();
    const setBookmark = vi.fn().mockReturnValue(pending.promise);
    const load = vi.fn().mockResolvedValue({ items: [festivalBookmark(1)] });
    const session = createBookmarkedContentsSession(load, setBookmark, () => {});
    await session.reload();

    const first = session.remove(festivalBookmark(1));
    await expect(session.remove(festivalBookmark(1))).resolves.toBe(false);
    expect(setBookmark).toHaveBeenCalledOnce();

    pending.resolve(undefined);
    await first;
  });

  it('해제 실패는 목록을 유지한다', async () => {
    const states: BookmarkedContentsState[] = [];
    const load = vi.fn().mockResolvedValue({ items: [festivalBookmark(1)] });
    const session = createBookmarkedContentsSession(
      load,
      vi.fn().mockRejectedValue(new Error('fail')),
      (state) => states.push(state),
    );
    await session.reload();

    await expect(session.remove(festivalBookmark(1))).resolves.toBe(false);
    expect(states.at(-1)?.items.map(bookmarkedContentId)).toEqual([1]);
    expect(states.at(-1)?.error).toBeInstanceOf(Error);
  });
});
