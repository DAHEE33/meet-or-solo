// 관리자 만남 장소(festival_meeting_points) 관리 화면용 데이터 접근 계층.
// 신규 API를 추가하지 않고 기존 /api/admin/festivals/{festivalId}/meeting-points 계약을 그대로 사용한다.
// 설계 배경은 docs/24_ADMIN_MEETING_POINT_MANAGEMENT_DESIGN.md 4.2절 참고.

import { apiClient } from './apiClient';

export type AdminMeetingPointStatus = 'ACTIVE' | 'INACTIVE';

export type AdminMeetingPoint = {
  id: number;
  festivalId: number;
  kakaoPlaceId: string;
  name: string;
  address: string;
  longitude: number;
  latitude: number;
  status: AdminMeetingPointStatus;
  assignmentOrder: number;
  createdAt: string;
  updatedAt: string;
};

export type AdminMeetingPointUpsertRequest = {
  kakaoPlaceId: string;
  name: string;
  address: string;
  longitude: number;
  latitude: number;
  assignmentOrder: number;
};

export const adminMeetingPointsApi = {
  list: (festivalId: number, signal?: AbortSignal) =>
    apiClient<AdminMeetingPoint[]>(`/api/admin/festivals/${festivalId}/meeting-points`, { signal }),
  create: (festivalId: number, request: AdminMeetingPointUpsertRequest, signal?: AbortSignal) =>
    apiClient<AdminMeetingPoint>(`/api/admin/festivals/${festivalId}/meeting-points`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
      signal,
    }),
  update: (festivalId: number, pointId: number, request: AdminMeetingPointUpsertRequest, signal?: AbortSignal) =>
    apiClient<AdminMeetingPoint>(`/api/admin/festivals/${festivalId}/meeting-points/${pointId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
      signal,
    }),
  changeStatus: (
    festivalId: number,
    pointId: number,
    status: AdminMeetingPointStatus,
    signal?: AbortSignal,
  ) =>
    apiClient<AdminMeetingPoint>(`/api/admin/festivals/${festivalId}/meeting-points/${pointId}/status`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status }),
      signal,
    }),
};
