import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminAuthApi } from './adminAuth';
import { ADMIN_LOGIN_PATH, loginPathFor, SANCTION_LOGIN_PATH } from './apiClient';

function ok() {
  return new Response(JSON.stringify({ success: true, data: null, error: null }), {
    status: 200, headers: { 'Content-Type': 'application/json' },
  });
}

describe('adminAuthApi', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('아이디와 비밀번호를 JSON body로 보낸다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok());
    vi.stubGlobal('fetch', fetchMock);

    await adminAuthApi.login('admin', 'secret');

    expect(String(fetchMock.mock.calls[0][0])).toContain('/api/auth/admin/login');
    const init = fetchMock.mock.calls[0][1];
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ username: 'admin', password: 'secret' });
    // cookie를 받아야 하므로 credentials가 빠지면 로그인 자체가 무의미해진다.
    expect(init.credentials).toBe('include');
  });

  it('로그아웃은 기존 공용 endpoint를 쓴다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok());
    vi.stubGlobal('fetch', fetchMock);

    await adminAuthApi.logout();

    expect(String(fetchMock.mock.calls[0][0])).toContain('/api/auth/logout');
  });
});

describe('loginPathFor', () => {
  it('관리자 화면의 401은 관리자 로그인으로 보낸다', () => {
    expect(loginPathFor('/admin', false)).toBe(ADMIN_LOGIN_PATH);
    expect(loginPathFor('/admin/members', false)).toBe(ADMIN_LOGIN_PATH);
  });

  it('일반 화면의 401은 소셜 로그인으로 보낸다', () => {
    expect(loginPathFor('/', false)).toBe('/login');
    expect(loginPathFor('/festivals/1', false)).toBe('/login');
  });

  /** 접두만 같고 관리자 화면이 아닌 경로까지 가져가면 안 된다. */
  it('접두가 겹치는 다른 경로는 관리자로 보지 않는다', () => {
    expect(loginPathFor('/administrators', false)).toBe('/login');
  });

  it('이미 로그인 화면이면 이동하지 않는다', () => {
    expect(loginPathFor('/login', false)).toBeNull();
    expect(loginPathFor(ADMIN_LOGIN_PATH, false)).toBeNull();
  });

  /** 영구 제한은 계정 자체가 막힌 상태라 사유 안내가 있는 소셜 로그인으로 보낸다. */
  it('영구 제한은 관리자 화면에서도 안내가 있는 로그인 화면으로 보낸다', () => {
    expect(loginPathFor('/admin/members', true)).toBe(SANCTION_LOGIN_PATH);
    expect(loginPathFor('/login', true)).toBeNull();
  });
});
