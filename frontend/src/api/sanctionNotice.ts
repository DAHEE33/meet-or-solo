import { apiClientNullable } from './apiClient';
import type { SanctionNotice } from './types';

/**
 * 제재 사유·기간 조회.
 *
 * 제재로 막히는 경로는 로그인 시도(302), 세션 중 API 호출(403), token 갱신(403) 세 가지다.
 * 302에는 응답 body가 없어 서버가 세 경로 모두 단기 notice cookie를 내려주고, 화면은 이
 * endpoint 하나로 안내를 읽는다. 사유 문구는 서버가 만든다.
 *
 * cookie가 없거나 제재가 이미 해제·만료되었으면 null을 돌려준다.
 */
export const sanctionNoticeApi = {
  getMine: (signal?: AbortSignal) =>
    apiClientNullable<SanctionNotice>('/api/auth/sanction-notice', { signal }),
};
