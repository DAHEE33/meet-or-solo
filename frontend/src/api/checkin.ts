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
};
