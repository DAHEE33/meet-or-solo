// 축제 GPS 체크인 데이터 접근 계층.
// 원본 좌표는 이 파일에서 요청 본문에만 실어 보내고, 응답에는 담기지 않는다(서버도 저장 안 함).

import { apiClient } from './apiClient';

export type FestivalCheckinStatus = 'ACTIVE' | 'EXPIRED' | 'CANCELLED';

export type CheckInResponse = {
  id: number;
  festivalId: number;
  distanceMeters: number;
  status: FestivalCheckinStatus;
  checkedInAt: string;
  expiresAt: string;
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
};
