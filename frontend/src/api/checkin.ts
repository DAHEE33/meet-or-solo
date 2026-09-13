// 축제 GPS 체크인 데이터 접근 계층.
// 원본 좌표는 이 파일에서 요청 본문에만 실어 보내고, 응답에는 담기지 않는다(서버도 저장 안 함).

import { apiClient, apiClientNullable, apiClientVoid } from './apiClient';

export type FestivalCheckinStatus = 'ACTIVE' | 'EXPIRED' | 'CANCELLED';

export type CheckInResponse = {
  id: number;
  festivalId: number;
  distanceMeters: number;
  status: FestivalCheckinStatus;
  checkedInAt: string;
  expiresAt: string;
};

export type CurrentCheckinResponse = {
  checkinId: number;
  festivalId: number;
  festivalName: string | null;
  checkedInAt: string;
  expiresAt: string;
};

export type CheckinHistoryItem = {
  checkinId: number;
  festivalId: number;
  festivalTitle: string;
  festivalAddress: string | null;
  /** 축제 좌표와의 거리. 원본 위경도는 서버가 저장하지 않으므로 응답에도 없다. */
  distanceMeters: number;
  /**
   * 서버가 판정한 표시 상태다. DB에는 `EXPIRED`가 기록되지 않고 만료는 `expiresAt` 경과로만
   * 표현되므로, 화면에서 다시 계산하면 서버의 유효기간 정책과 갈라진다.
   */
  status: FestivalCheckinStatus;
  checkedInAt: string;
  expiresAt: string;
};

export type CheckinHistory = {
  items: CheckinHistoryItem[];
  pagination: { size: number; hasNext: boolean; nextCursor: string | null };
};

export const checkinApi = {
  checkIn: (festivalId: number, latitude: number, longitude: number, accuracyMeters?: number) =>
    apiClient<CheckInResponse>(`/api/festivals/${festivalId}/checkin`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ latitude, longitude, accuracyMeters }),
    }),
  // 인증 회원의 현재 유효한 체크인. 없으면 null(활성 체크인이 없다는 뜻이지 오류가 아니다).
  getCurrent: () => apiClientNullable<CurrentCheckinResponse>('/api/festivals/checkin/me'),
  cancelCurrent: () => apiClientVoid('/api/festivals/checkin/me', { method: 'DELETE' }),
  // 마이페이지 체크인 기록. 만료·취소된 체크인도 함께 최신순으로 온다.
  getMyHistory: (cursor?: string | null, signal?: AbortSignal) =>
    apiClient<CheckinHistory>(
      cursor ? `/api/members/me/check-ins?cursor=${encodeURIComponent(cursor)}`
        : '/api/members/me/check-ins',
      { signal },
    ),
};
