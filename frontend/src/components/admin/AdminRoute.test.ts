import { describe, expect, it } from 'vitest';
import { ApiClientError } from '../../api/apiClient';
import { resolveAdminRouteError } from './AdminRoute';

describe('AdminRoute', () => {
  it('403은 로그인 이동 대신 관리자 접근 거절 상태로 분리한다', () => {
    expect(resolveAdminRouteError(new ApiClientError('금지', 403, 'FORBIDDEN', undefined)))
      .toBe('FORBIDDEN');
  });
  it('401은 apiClient가 로그인 이동을 수행한 뒤 일반 오류 상태로 처리한다', () => {
    expect(resolveAdminRouteError(new ApiClientError('인증', 401, 'UNAUTHORIZED', undefined)))
      .toBe('ERROR');
  });
});
