import { afterEach, describe, expect, it, vi } from 'vitest';
import { authApi } from './auth';

afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

describe('authApi.logout', () => {
  it('cookie를 포함해 body 없이 POST하고 204를 성공 처리한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(authApi.logout()).resolves.toBeUndefined();

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/logout',
      expect.objectContaining({ method: 'POST', credentials: 'include' }),
    );
    const options = fetchMock.mock.calls[0][1] as RequestInit;
    expect(options.body).toBeUndefined();
  });

  it('서버 오류는 호출자에게 전달해 실패를 알릴 수 있게 한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ success: false, data: null, error: { code: 'INTERNAL_SERVER_ERROR', message: '서버 오류' } }),
      { status: 500, headers: { 'Content-Type': 'application/json' } },
    ));
    vi.stubGlobal('fetch', fetchMock);

    await expect(authApi.logout()).rejects.toThrow('서버 오류');
  });
});
