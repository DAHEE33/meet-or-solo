import { afterEach, describe, expect, it, vi } from 'vitest';
import { contentCommentsApi } from './contentComments';
import { contentBookmarksApi, contentTargetSegment } from './contentBookmarks';

afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify({ success: true, data, error: null }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });

const comment = {
  id: 1024, nickname: '춘천사람', body: '좋았어요',
  likeCount: 4, likedByMe: false, mine: false, createdAt: '2026-09-07T10:12:00+09:00',
};

describe('contentTargetSegment', () => {
  it('관광지는 /api/spots segment를 쓴다', () => {
    expect(contentTargetSegment('FESTIVAL')).toBe('festivals');
    expect(contentTargetSegment('TOUR_PLACE')).toBe('spots');
  });
});

describe('contentCommentsApi', () => {
  it('축제 댓글 목록을 offset 페이징으로 조회한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({
      items: [comment], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false,
    }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(contentCommentsApi.getList({ type: 'FESTIVAL', id: 298 })).resolves.toMatchObject({
      totalElements: 1,
    });
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/festivals/298/comments?page=0&size=20',
      expect.objectContaining({ credentials: 'include' }),
    );
  });

  it('관광지 댓글 목록은 spots 경로로 조회한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({
      items: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false,
    }));
    vi.stubGlobal('fetch', fetchMock);

    await contentCommentsApi.getList({ type: 'TOUR_PLACE', id: 7 }, 1, 10);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/spots/7/comments?page=1&size=10',
      expect.objectContaining({ credentials: 'include' }),
    );
  });

  it('등록은 body만 담아 POST한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(comment, 201));
    vi.stubGlobal('fetch', fetchMock);

    await contentCommentsApi.create({ type: 'FESTIVAL', id: 298 }, '좋았어요');
    const [url, options] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/festivals/298/comments');
    expect(options.method).toBe('POST');
    expect(options.body).toBe(JSON.stringify({ body: '좋았어요' }));
    expect(JSON.stringify(options)).not.toContain('memberId');
  });

  it('삭제는 body 없이 DELETE하고 204를 성공 처리한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(contentCommentsApi.remove(1024)).resolves.toBeUndefined();
    const [url, options] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/comments/1024');
    expect(options.method).toBe('DELETE');
    expect(options.body).toBeUndefined();
  });

  it('좋아요는 PUT + 상태 body 하나로 토글한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({ liked: true, likeCount: 5 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(contentCommentsApi.setLike(1024, true)).resolves.toEqual({ liked: true, likeCount: 5 });
    const [url, options] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/comments/1024/like');
    expect(options.method).toBe('PUT');
    expect(options.body).toBe(JSON.stringify({ liked: true }));
  });

  it('관리자 숨김은 admin 경로로 PUT한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({ visible: false }));
    vi.stubGlobal('fetch', fetchMock);

    await contentCommentsApi.setVisibility(1024, false);
    const [url, options] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/admin/comments/1024/visibility');
    expect(options.method).toBe('PUT');
    expect(options.body).toBe(JSON.stringify({ visible: false }));
  });
});

describe('contentBookmarksApi', () => {
  it('engagement는 대상별 공개 경로로 조회한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({
      bookmarked: false, commentCount: 0, viewer: { loggedIn: false, admin: false },
    }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(contentBookmarksApi.getEngagement({ type: 'FESTIVAL', id: 298 })).resolves.toMatchObject({
      viewer: { loggedIn: false },
    });
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/festivals/298/engagement',
      expect.objectContaining({ credentials: 'include' }),
    );
  });

  it('찜은 PUT + 상태 body 하나로 토글한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({ bookmarked: true }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(contentBookmarksApi.setBookmark({ type: 'TOUR_PLACE', id: 7 }, true)).resolves.toEqual({
      bookmarked: true,
    });
    const [url, options] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/spots/7/bookmark');
    expect(options.method).toBe('PUT');
    expect(options.body).toBe(JSON.stringify({ bookmarked: true }));
  });

  it('내 찜 목록은 type 쿼리로 구분한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({
      items: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false,
    }));
    vi.stubGlobal('fetch', fetchMock);

    await contentBookmarksApi.getMine('TOUR_PLACE');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/members/me/bookmarks?type=TOUR_PLACE&page=0&size=20',
      expect.objectContaining({ credentials: 'include' }),
    );
  });
});
