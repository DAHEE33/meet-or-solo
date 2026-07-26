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
  getList: (page = 0, size = 20, keyword?: string) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (keyword) params.set('keyword', keyword);
    return apiClient<FestivalListResponse>(`/api/festivals?${params.toString()}`);
  },
  getDetail: (id: number) => apiClient<FestivalDetail>(`/api/festivals/${id}`),
  getNearbyTourPlaces: (id: number, radiusMeters = 5000, limit = 10) =>
    apiClient<NearbyTourPlaceItem[]>(
      `/api/festivals/${id}/nearby-spots?radiusMeters=${radiusMeters}&limit=${limit}`,
    ),
};
