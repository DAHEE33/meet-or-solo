// 관리자 만남 장소 화면의 "축제 선택" 검색 전용 데이터 접근 계층.
//
// 공개 festivalsApi.getList(`/api/festivals`)는 일반 사용자 화면을 위해 종료일이 지난 축제를
// 항상 숨긴다. 관리자는 방금 끝난 축제의 만남 장소도 조회·수정해야 하므로 별도 admin 전용
// 엔드포인트(`/api/admin/festivals`)를 쓴다 — docs/24_ADMIN_MEETING_POINT_MANAGEMENT_DESIGN.md
// 7장 후속 과제.

import { apiClient } from './apiClient';
import type { FestivalSyncStatus } from './festivals';

export type AdminFestivalSummary = {
  id: number;
  title: string;
  address: string | null;
  eventStartDate: string | null;
  eventEndDate: string | null;
  status: FestivalSyncStatus;
  // 신규 만남 장소 등록 폼의 카카오맵 좌표 선택기가 초기 중심점으로 쓴다(mapX=경도, mapY=위도).
  mapX: number | null;
  mapY: number | null;
};

export const adminFestivalsApi = {
  search: (keyword?: string, signal?: AbortSignal) => {
    const params = new URLSearchParams();
    if (keyword) params.set('keyword', keyword);
    const query = params.toString();
    return apiClient<AdminFestivalSummary[]>(`/api/admin/festivals${query ? `?${query}` : ''}`, { signal });
  },
};
