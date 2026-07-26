// 관광지 목록/상세 화면용 데이터 접근 계층.
// 페이지는 이 파일의 함수만 호출하고, 응답 형태가 바뀌면 이 파일 내부만 맞추면 된다.

import { apiClient } from './apiClient';

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

export const spotsApi = {
  getList: (page = 0, size = 20, contentTypeId?: string) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (contentTypeId) params.set('contentTypeId', contentTypeId);
    return apiClient<TourPlaceListResponse>(`/api/spots?${params.toString()}`);
  },
  getDetail: (id: number) => apiClient<TourPlaceDetail>(`/api/spots/${id}`),
  getNearbyFestivals: (id: number, radiusMeters = 5000, limit = 10) =>
    apiClient<NearbyFestivalItem[]>(
      `/api/spots/${id}/nearby-festivals?radiusMeters=${radiusMeters}&limit=${limit}`,
    ),
};
