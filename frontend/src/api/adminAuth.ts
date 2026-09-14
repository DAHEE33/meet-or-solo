import { apiClientVoid } from './apiClient';

/**
 * 슈퍼관리자 ID/PW 로그인(docs/30).
 *
 * SSO를 대체하지 않는다. 성공하면 소셜 로그인과 같은 HttpOnly session cookie가 내려오므로,
 * 이후 관리자 API 호출은 어느 경로로 로그인했는지 구분하지 않는다.
 *
 * 응답 body에 token이 없다. 읽을 값이 없으므로 data를 요구하지 않는 apiClientVoid를 쓴다.
 */
export const adminAuthApi = {
  login: (username: string, password: string, signal?: AbortSignal) =>
    apiClientVoid('/api/auth/admin/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
      signal,
    }),
  logout: (signal?: AbortSignal) =>
    apiClientVoid('/api/auth/logout', { method: 'POST', signal }),
};
