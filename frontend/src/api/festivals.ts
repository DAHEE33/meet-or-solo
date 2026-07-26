// 축제 상세 화면용 데이터 접근 계층.
// FestivalDetailPage는 이 파일의 함수만 호출하고, 응답 형태가 바뀌면 이 파일 내부만 맞추면 된다.

import { apiClient } from './apiClient';

export type FestivalSyncStatus = 'ACTIVE' | 'INACTIVE' | 'ENDED' | 'HIDDEN';

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

export const festivalsApi = {
  getList: (page = 0, size = 20) =>
    apiClient<FestivalListResponse>(`/api/festivals?page=${page}&size=${size}`),
  getDetail: (id: number) => apiClient<FestivalDetail>(`/api/festivals/${id}`),
  getNearbyTourPlaces: (id: number, radiusMeters = 5000, limit = 10) =>
    apiClient<NearbyTourPlaceItem[]>(
      `/api/festivals/${id}/nearby-spots?radiusMeters=${radiusMeters}&limit=${limit}`,
    ),
};
