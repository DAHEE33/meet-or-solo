// 관광지 목록/상세 화면용 데이터 접근 계층.
// 페이지는 이 파일의 함수만 호출하고, 응답 형태가 바뀌면 이 파일 내부만 맞추면 된다.

import { apiClient } from './apiClient';
// 지역 선택 항목은 축제/관광지가 같은 형태를 쓰므로 한 곳에만 정의한다.
import type { RegionOption } from './festivals';

export type { RegionOption };

export type TourPlaceStatus = 'ACTIVE' | 'INACTIVE' | 'HIDDEN';

export type TourPlaceListItem = {
  id: number;
  contentId: string;
  contentTypeId: string;
  title: string;
  address: string | null;
  status: TourPlaceStatus;
  imageUrl: string | null;
};

export type TourPlaceListResponse = {
  items: TourPlaceListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
};

export type TourPlaceDetail = {
  id: number;
  contentId: string;
  contentTypeId: string;
  title: string;
  address: string | null;
  tel: string | null;
  mapX: number | null;
  mapY: number | null;
  status: TourPlaceStatus;
  imageUrl: string | null;
};

export type NearbyFestivalItem = {
  id: number;
  title: string;
  address: string | null;
  eventStartDate: string | null;
  eventEndDate: string | null;
  status: 'ACTIVE' | 'INACTIVE' | 'ENDED' | 'HIDDEN';
  thumbnailUrl: string | null;
  distanceMeters: number;
};

// 거리 정렬(가까운순/먼순)은 제공하지 않는다. 사용자 좌표를 서버로 보내지 않아 서버에
// 거리 정렬의 기준점이 없기 때문이다(docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md 3.1).
// "내 주변" 성격의 조회는 축제 좌표를 중심으로 하는 festivalsApi.getNearbyTourPlaces가 담당한다.
export type TourPlaceListSort = 'TITLE_ASC' | 'RECENTLY_ADDED';

export type TourPlaceListQuery = {
  sigunguCode?: string;
  sort?: TourPlaceListSort;
};

export const spotsApi = {
  getList: (
    page = 0,
    size = 20,
    contentTypeId?: string,
    keyword?: string,
    query: TourPlaceListQuery = {},
  ) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (contentTypeId) params.set('contentTypeId', contentTypeId);
    if (keyword) params.set('keyword', keyword);
    if (query.sigunguCode) params.set('sigunguCode', query.sigunguCode);
    if (query.sort) params.set('sort', query.sort);
    return apiClient<TourPlaceListResponse>(`/api/spots?${params.toString()}`);
  },
  getRegions: (contentTypeId?: string) => {
    const params = new URLSearchParams();
    if (contentTypeId) params.set('contentTypeId', contentTypeId);
    const query = params.toString();
    return apiClient<RegionOption[]>(`/api/spots/regions${query ? `?${query}` : ''}`);
  },
  getDetail: (id: number) => apiClient<TourPlaceDetail>(`/api/spots/${id}`),
  getNearbyFestivals: (id: number, radiusMeters = 5000, limit = 10) =>
    apiClient<NearbyFestivalItem[]>(
      `/api/spots/${id}/nearby-festivals?radiusMeters=${radiusMeters}&limit=${limit}`,
    ),
};
