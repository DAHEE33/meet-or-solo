import { describe, expect, it } from 'vitest';
import { getOAuthLoginPath } from './oauth';

describe('getOAuthLoginPath', () => {
  it('네이버 로그인은 backend endpoint로 이동한다', () => {
    expect(getOAuthLoginPath('naver')).toBe('/api/auth/naver/login');
  });

  it('기존 카카오 로그인 endpoint를 유지한다', () => {
    expect(getOAuthLoginPath('kakao')).toBe('/api/auth/kakao/login');
  });
});
