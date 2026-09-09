import { describe, expect, it } from 'vitest';
import { BANNED_CODE, SANCTION_LOGIN_PATH } from '../../api/apiClient';
import { resolveSplashTarget, shouldSkipSplash } from './splashPolicy';

describe('shouldSkipSplash', () => {
  it('탭 세션에서 이미 봤으면 건너뛴다 — 새로고침마다 로고를 다시 보여주면 방해가 된다', () => {
    expect(shouldSkipSplash({ pathname: '/', alreadyShown: true })).toBe(true);
  });

  it('첫 진입이면 재생한다', () => {
    expect(shouldSkipSplash({ pathname: '/', alreadyShown: false })).toBe(false);
  });

  it('로그인 화면에서는 건너뛴다 — 미로그인 회원이 도착하는 목적지다', () => {
    expect(shouldSkipSplash({ pathname: '/login', alreadyShown: false })).toBe(true);
  });

  it('관리자 화면에서는 건너뛴다 — AdminRoute가 자체 권한 확인 화면을 갖고 있다', () => {
    expect(shouldSkipSplash({ pathname: '/admin', alreadyShown: false })).toBe(true);
    expect(shouldSkipSplash({ pathname: '/admin/reports', alreadyShown: false })).toBe(true);
  });

  it('경로 앞부분만 같은 화면은 관리자 화면이 아니다', () => {
    expect(shouldSkipSplash({ pathname: '/administrator', alreadyShown: false })).toBe(false);
  });

  it('일반 화면 딥링크는 그대로 재생한다', () => {
    expect(shouldSkipSplash({ pathname: '/festivals/12', alreadyShown: false })).toBe(false);
  });
});

describe('resolveSplashTarget', () => {
  it('로그인 상태면 원래 목적지를 유지한다', () => {
    expect(resolveSplashTarget({ status: 200, code: null })).toBeNull();
  });

  it('401이면 로그인 화면으로 보낸다', () => {
    expect(resolveSplashTarget({ status: 401, code: null })).toBe('/login');
  });

  it('영구제한은 사유 안내가 붙은 로그인 경로로 보낸다', () => {
    expect(resolveSplashTarget({ status: 403, code: BANNED_CODE })).toBe(SANCTION_LOGIN_PATH);
  });

  it('이용정지는 로그인 상태이므로 이동하지 않는다 — SanctionNoticeDialog가 안내를 맡는다', () => {
    expect(resolveSplashTarget({ status: 403, code: 'MEMBER_SUSPENDED' })).toBeNull();
  });

  it('네트워크 실패로는 이동하지 않는다 — 로그인된 회원을 일시 장애로 로그인 화면에 떨어뜨리면 안 된다', () => {
    expect(resolveSplashTarget({ status: 0, code: null })).toBeNull();
  });

  it('서버 오류로도 이동하지 않는다 — 각 화면이 자기 오류 상태를 갖고 있다', () => {
    expect(resolveSplashTarget({ status: 500, code: null })).toBeNull();
  });
});
