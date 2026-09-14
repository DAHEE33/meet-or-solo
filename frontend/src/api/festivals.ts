// 축제 상세 화면용 데이터 접근 계층.
// FestivalDetailPage는 이 파일의 함수만 호출하고, 응답 형태가 바뀌면 이 파일 내부만 맞추면 된다.

import { apiClient } from './apiClient';

export type FestivalSyncStatus = 'ACTIVE' | 'INACTIVE' | 'ENDED' | 'HIDDEN';

export type FestivalInfoItem = {
  label: string;
  value: string;
};

export type FestivalProgramItem = {
  name: string;
  description: string;
  time: string;
};

export type FestivalDetail = {
  id: number;
  contentId: string;
  title: string;
  address: string | null;
  regionCode: string | null;
  sigunguCode: string | null;
  eventStartDate: string | null; // ISO date (yyyy-MM-dd)
  eventEndDate: string | null;
  status: FestivalSyncStatus;
  mapX: number | null;
  mapY: number | null;
  originImageUrl: string | null;
  thumbnailUrl: string | null;
  /** 관광공사 detailCommon2를 온디맨드로 호출한 소개글. 실패 시 빈 문자열. */
  intro: string;
  /** 관광공사 detailIntro2 기반 이용정보. 실패 시 빈 배열. */
  infoItems: FestivalInfoItem[];
  /** 관광공사 detailInfo2 기반 프로그램/세부 일정. 실패 시 빈 배열. */
  programs: FestivalProgramItem[];
};

export type FestivalListItem = {
  id: number;
  contentId: string;
  title: string;
  address: string | null;
  regionCode: string | null;
  sigunguCode: string | null;
  eventStartDate: string | null;
  eventEndDate: string | null;
  status: FestivalSyncStatus;
  originImageUrl: string | null;
  thumbnailUrl: string | null;
  // 홈 화면이 브라우저에서 내 위치와의 거리를 계산하기 위한 좌표(mapX=경도, mapY=위도).
  // 사용자 좌표는 서버로 보내지 않는다 —
  // docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md 4.1 참고.
  mapX: number | null;
  mapY: number | null;
  /** 이 축제를 찜한 회원 수. 화면에는 "좋아요 수"로 표시한다. */
  bookmarkCount: number;
  /** 공개 댓글 수. 화면에는 "후기 수"로 표시한다. */
  commentCount: number;
  /** 내가 찜했는가. 목록에서 바로 찜을 토글하므로 필요하다. 비로그인이면 항상 false. */
  bookmarkedByMe: boolean;
};

export type FestivalListResponse = {
  items: FestivalListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  /**
   * 이 응답을 받는 사람이 로그인했는가. 목록 화면은 상세 화면과 달리 engagement를 부르지
   * 않으므로 로그인 여부가 여기로 온다 — 비로그인이 하트를 누르면 요청 없이 `/login`으로
   * 보내야 한다(docs/27 2.1).
   */
  viewerLoggedIn: boolean;
};

export type NearbyTourPlaceItem = {
  id: number;
  title: string;
  address: string | null;
  contentTypeId: string;
  imageUrl: string | null;
  distanceMeters: number;
  /** 이 관광지를 찜한 회원 수. 화면에는 "좋아요 수"로 표시한다. */
  bookmarkCount: number;
  /** 공개 댓글 수. */
  commentCount: number;
  /** 내가 찜했는가. 홈 "축제와 함께 둘러보기" 카드에서도 바로 찜할 수 있다. */
  bookmarkedByMe: boolean;
};

export type SoloCourseType = 'HALF' | 'FULL';

export type SoloCourseStop = {
  order: number;
  id: number;
  title: string;
  address: string | null;
  contentTypeId: string;
  imageUrl: string | null;
  distanceFromPreviousMeters: number;
  walkMinutesFromPrevious: number;
  estimatedStayMinutes: number;
};

export type SoloCourseResponse = {
  type: SoloCourseType;
  totalWalkMinutes: number;
  totalStayMinutes: number;
  totalDurationMinutes: number;
  stops: SoloCourseStop[];
};

// 날짜 정렬(시작일 빠른순/종료 임박순)은 없다. 진행 단계는 progress 필터로 고르고 기간은
// startDate/endDate로 직접 선택한다.
export type FestivalListSort = 'RECENTLY_ADDED' | 'BOOKMARK_COUNT_DESC' | 'COMMENT_COUNT_DESC';

/**
 * 진행 상태 필터. **이 값을 넘겨야 종료된 축제까지 검색된다** — 넘기지 않으면 서버가 기존과
 * 같이 진행 중·예정 축제만 돌려준다(홈 화면이 같은 API를 쓰기 때문).
 */
export type FestivalProgressFilter = 'ALL' | 'UPCOMING' | 'ONGOING' | 'ENDED';

export type FestivalListQuery = {
  page?: number;
  size?: number;
  keyword?: string;
  sigunguCode?: string;
  sort?: FestivalListSort;
  /** 기간 시작(yyyy-MM-dd). 축제 기간과 겹치면 걸린다. 한쪽만 넘겨도 된다. */
  startDate?: string;
  /** 기간 끝(yyyy-MM-dd). */
  endDate?: string;
  progress?: FestivalProgressFilter;
  matchableOnly?: boolean;
};

/** 지역 선택 항목. 실제로 데이터가 있는 시군구만 서버가 내려준다. */
export type RegionOption = {
  sigunguCode: string;
  name: string;
  count: number;
};

export const festivalsApi = {
  getList: (page = 0, size = 20, keyword?: string, query: FestivalListQuery = {}) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (keyword) params.set('keyword', keyword);
    if (query.sigunguCode) params.set('sigunguCode', query.sigunguCode);
    if (query.sort) params.set('sort', query.sort);
    if (query.startDate) params.set('startDate', query.startDate);
    if (query.endDate) params.set('endDate', query.endDate);
    if (query.progress) params.set('progress', query.progress);
    if (query.matchableOnly) params.set('matchableOnly', 'true');
    return apiClient<FestivalListResponse>(`/api/festivals?${params.toString()}`);
  },
  getRegions: () => apiClient<RegionOption[]>('/api/festivals/regions'),
  getDetail: (id: number) => apiClient<FestivalDetail>(`/api/festivals/${id}`),
  getNearbyTourPlaces: (id: number, radiusMeters = 5000, limit = 10) =>
    apiClient<NearbyTourPlaceItem[]>(
      `/api/festivals/${id}/nearby-spots?radiusMeters=${radiusMeters}&limit=${limit}`,
    ),
  getSoloCourse: (id: number, type: SoloCourseType = 'HALF') =>
    apiClient<SoloCourseResponse>(`/api/festivals/${id}/solo-course?type=${type}`),
};
