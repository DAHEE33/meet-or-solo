// 찜(북마크)과 engagement 조회용 데이터 접근 계층.
// 설계는 docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 4장을 따른다.
//
// 찜과 댓글은 축제(festival)와 관광지(tour place) 두 대상을 공유하므로, 대상 타입과 URL
// segment 변환을 이 파일에 한 번만 정의하고 contentComments.ts가 재사용한다
// (festivals.ts의 RegionOption을 spots.ts가 재사용하는 것과 같은 방식).

import { apiClient } from './apiClient';
import type { FestivalListItem } from './festivals';
import type { TourPlaceListItem } from './spots';

export type ContentTargetType = 'FESTIVAL' | 'TOUR_PLACE';

export type ContentTarget = {
  type: ContentTargetType;
  id: number;
};

/** 관광지 공개 API의 URL segment는 기존 규칙대로 `/api/spots`다(`/api/tour-places` 아님). */
export function contentTargetSegment(type: ContentTargetType): 'festivals' | 'spots' {
  return type === 'FESTIVAL' ? 'festivals' : 'spots';
}

/**
 * 상세 화면 진입 시 한 번 조회하는 공개 응답.
 *
 * `viewer.loggedIn`이 이 응답에 함께 오는 이유는 docs/27 2.1절이다 — apiClient가 모든 401을
 * `/login` 전역 리다이렉트로 처리하므로, 공개 화면에서 로그인 여부를 알기 위해
 * `GET /api/members/me`를 호출하면 비로그인 사용자가 화면째로 튕긴다.
 */
export type ContentEngagement = {
  bookmarked: boolean;
  commentCount: number;
  viewer: {
    loggedIn: boolean;
    admin: boolean;
  };
};

/**
 * 내 찜 목록 항목. 축제/관광지 중 하나만 채워진다(`content_bookmarks`의 대상 CHECK와 같은 규칙).
 * 목록 카드를 기존 `FestivalListItem`/`ExploreSpotItem`으로 그대로 그리기 위해 기존 목록 타입을
 * 그대로 담는다.
 */
export type BookmarkedContent = {
  bookmarkedAt: string;
  targetType: ContentTargetType;
  festival: FestivalListItem | null;
  tourPlace: TourPlaceListItem | null;
};

export type BookmarkedContentPage = {
  items: BookmarkedContent[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
};

export const contentBookmarksApi = {
  getEngagement: (target: ContentTarget, signal?: AbortSignal) =>
    apiClient<ContentEngagement>(
      `/api/${contentTargetSegment(target.type)}/${target.id}/engagement`,
      { signal },
    ),

  // 토글은 POST/DELETE 쌍이 아니라 PUT + 상태 body 하나다(docs/27 4장).
  // 멱등이므로 연타·동시 요청이 같은 결과를 만든다.
  setBookmark: (target: ContentTarget, bookmarked: boolean, signal?: AbortSignal) =>
    apiClient<{ bookmarked: boolean }>(
      `/api/${contentTargetSegment(target.type)}/${target.id}/bookmark`,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ bookmarked }),
        signal,
      },
    ),

  getMine: (type: ContentTargetType, page = 0, size = 20, signal?: AbortSignal) => {
    const params = new URLSearchParams({ type, page: String(page), size: String(size) });
    return apiClient<BookmarkedContentPage>(`/api/members/me/bookmarks?${params.toString()}`, {
      signal,
    });
  },
};
