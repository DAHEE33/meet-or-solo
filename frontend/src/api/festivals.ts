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
};

export type FestivalListResponse = {
  items: FestivalListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
};

export type NearbyTourPlaceItem = {
  id: number;
  title: string;
  address: string | null;
  contentTypeId: string;
  imageUrl: string | null;
  distanceMeters: number;
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

export type FestivalListSort = 'START_DATE_ASC' | 'END_DATE_ASC' | 'RECENTLY_ADDED';

export type FestivalScheduleFilter = 'ALL' | 'ONGOING' | 'THIS_WEEKEND' | 'THIS_MONTH';

export type FestivalListQuery = {
  page?: number;
  size?: number;
  keyword?: string;
  sigunguCode?: string;
  sort?: FestivalListSort;
  schedule?: FestivalScheduleFilter;
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
    if (query.schedule) params.set('schedule', query.schedule);
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
