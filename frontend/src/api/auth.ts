import { apiClientVoid } from './apiClient';

export const authApi = {
  // 서버가 refresh token을 폐기하고 access/refresh cookie를 만료시킨다.
  // 미인증 상태에서도 204를 반환하는 멱등 endpoint다.
  logout: (signal?: AbortSignal) =>
    apiClientVoid('/api/auth/logout', { method: 'POST', signal }),
};
