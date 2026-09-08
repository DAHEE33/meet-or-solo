import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import FavoritesPage from './FavoritesPage';
import FestivalDetailPage from './FestivalDetailPage';
import TourSpotDetailPage from './TourSpotDetailPage';
import MyPage from './MyPage';
import { bookmarkedContentPath, mergeFavoritePreview } from './MyPage';
import { resolveBookmarkAction } from '../hooks/useContentBookmark';
import type { BookmarkedContent } from '../api/contentBookmarks';

const render = (element: React.ReactElement) =>
  renderToStaticMarkup(<MemoryRouter>{element}</MemoryRouter>);

describe('FavoritesPage', () => {
  it('축제·관광지 탭과 로딩 상태를 그린다', () => {
    // useEffect가 돌지 않는 SSR 마크업이라 초기 상태(LOADING)가 그대로 나온다.
    const html = render(<FavoritesPage />);
    expect(html).toContain('찜한 곳');
    expect(html).toContain('축제');
    expect(html).toContain('관광지');
    expect(html).toContain('찜한 곳을 불러오는 중이에요');
    expect(html).toContain('aria-busy="true"');
  });
});

describe('MyPage 찜 요약', () => {
  it('마이페이지에 찜 전체 보기 진입을 제공한다', () => {
    const html = render(<MyPage />);
    expect(html).toContain('찜한 곳');
    expect(html).toContain('전체 보기');
    expect(html).toContain('href="/mypage/favorites"');
  });

  it('mock 데이터에 있던 찜 관광지 이름을 더 이상 그리지 않는다', () => {
    // 회귀 방지: data/mock/tourSpots.ts를 실데이터로 교체했다(docs/27 7.2).
    const html = render(<MyPage />);
    expect(html).toContain('찜한 곳을 불러오는 중이에요');
    expect(html).not.toContain('찜한 관광지');
  });
});

describe('bookmarkedContentPath', () => {
  const festivalItem: BookmarkedContent = {
    bookmarkedAt: '2026-09-03T10:00:00+09:00',
    targetType: 'FESTIVAL',
    festival: {
      id: 298, contentId: 'f298', title: '축제', address: null, regionCode: null,
      sigunguCode: null, eventStartDate: null, eventEndDate: null, status: 'ACTIVE',
      originImageUrl: null, thumbnailUrl: null, mapX: null, mapY: null,
    },
    tourPlace: null,
  };
  const placeItem: BookmarkedContent = {
    bookmarkedAt: '2026-09-05T10:00:00+09:00',
    targetType: 'TOUR_PLACE',
    festival: null,
    tourPlace: {
      id: 7, contentId: 'p7', contentTypeId: '12', title: '관광지',
      address: null, status: 'ACTIVE', imageUrl: null,
    },
  };

  it('대상 종류에 맞는 상세 경로를 만든다', () => {
    expect(bookmarkedContentPath(festivalItem)).toBe('/festivals/298');
    expect(bookmarkedContentPath(placeItem)).toBe('/spots/7');
  });

  it('대상이 비어 있으면 경로가 없다', () => {
    expect(
      bookmarkedContentPath({ ...festivalItem, festival: null }),
    ).toBeNull();
  });

  it('축제와 관광지를 최신순으로 합치고 상위 N건만 남긴다', () => {
    const merged = mergeFavoritePreview([festivalItem], [placeItem], 2);
    expect(merged.map((item) => item.targetType)).toEqual(['TOUR_PLACE', 'FESTIVAL']);
    expect(mergeFavoritePreview([festivalItem], [placeItem], 1)).toHaveLength(1);
  });

  it('대상이 비어 있는 항목은 요약에서 제외한다', () => {
    expect(mergeFavoritePreview([{ ...festivalItem, festival: null }], [])).toEqual([]);
  });
});

/**
 * docs/27 2.1 회귀 방지 — 비로그인 사용자가 찜 버튼을 눌러도 요청을 보내지 않는다.
 * 요청을 보내면 401 → apiClient 전역 리다이렉트로 공개 화면이 통째로 튕긴다.
 */
describe('resolveBookmarkAction', () => {
  it('로그인 상태면 토글한다', () => {
    expect(resolveBookmarkAction({ status: 'READY', loggedIn: true })).toBe('TOGGLE');
  });

  it('비로그인 상태면 요청 대신 로그인으로 보낸다', () => {
    expect(resolveBookmarkAction({ status: 'READY', loggedIn: false })).toBe('LOGIN');
  });

  it('engagement 조회 전·실패에는 아무것도 하지 않는다', () => {
    expect(resolveBookmarkAction({ status: 'LOADING', loggedIn: false })).toBe('IGNORE');
    expect(resolveBookmarkAction({ status: 'ERROR', loggedIn: true })).toBe('IGNORE');
  });
});

/**
 * 상세 화면은 초기 상태가 loading이라 SSR 마크업으로 댓글 섹션까지 도달할 수 없다.
 * 찜·댓글 배선이 모듈 로드와 첫 렌더를 깨지 않는지만 확인하고, 실제 UI 계약은
 * ContentCommentSection.test.tsx가 검증한다.
 */
describe('상세 화면 배선', () => {
  it('축제 상세는 첫 렌더에서 로딩 상태를 그린다', () => {
    const html = render(<FestivalDetailPage />);
    expect(html).toContain('축제 상세');
  });

  it('관광지 상세는 첫 렌더에서 로딩 상태를 그린다', () => {
    const html = render(<TourSpotDetailPage />);
    expect(html).toContain('관광지 상세');
  });
});
