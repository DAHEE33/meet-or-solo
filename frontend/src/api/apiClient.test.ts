import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiClient, apiClientNullable, ApiClientError } from './apiClient';

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('apiClient', () => {
  it('data:null을 정상 응답으로 보존하고 cookie 인증 옵션을 유지한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ success: true, data: null, error: null }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiClientNullable('/api/matching/pools/me/current')).resolves.toBeNull();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/matching/pools/me/current',
      expect.objectContaining({ credentials: 'include' }),
    );
  });

  it('비 nullable 호출에서는 data:null을 계약 오류로 처리한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ success: true, data: null, error: null })));
    await expect(apiClient('/api/non-null')).rejects.toMatchObject({ status: 200 });
  });

  it('HTTP status와 backend error 정보를 보존한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(
          {
            success: false,
            data: null,
            error: {
              code: 'MATCHING_COOLDOWN_ACTIVE',
              message: '쿨다운 중입니다.',
              fields: [{ field: 'festivalId', message: '필수입니다.' }],
            },
          },
          409,
        ),
      ),
    );

    const error = await apiClient('/api/test').catch((caught) => caught);
    expect(error).toBeInstanceOf(ApiClientError);
    expect(error).toMatchObject({
      status: 409,
      code: 'MATCHING_COOLDOWN_ACTIVE',
      message: '쿨다운 중입니다.',
      fields: [{ field: 'festivalId', message: '필수입니다.' }],
    });
  });

  it('401이면 기존 login redirect를 유지한다', async () => {
    const replace = vi.fn();
    vi.stubGlobal('window', { location: { pathname: '/matching', replace } });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(
          { success: false, data: null, error: { code: 'UNAUTHORIZED', message: '인증이 필요합니다.' } },
          401,
        ),
      ),
    );

    await expect(apiClient('/api/test')).rejects.toMatchObject({ status: 401, code: 'UNAUTHORIZED' });
    expect(replace).toHaveBeenCalledWith('/login');
  });

  it('AbortError를 일반 API 오류로 변환하지 않는다', async () => {
    const abortError = new DOMException('aborted', 'AbortError');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(abortError));
    await expect(apiClientNullable('/api/test', { signal: new AbortController().signal })).rejects.toBe(abortError);
  });
});
