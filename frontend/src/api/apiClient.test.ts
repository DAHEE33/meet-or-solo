import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiClient, apiClientNullable, apiClientVoid, ApiClientError } from './apiClient';

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

  it('403 정지는 화면을 이동시키지 않고 안내 이벤트를 쏜다', async () => {
    const replace = vi.fn();
    const dispatchEvent = vi.fn();
    vi.stubGlobal('window', { location: { pathname: '/mypage', replace }, dispatchEvent });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(
          {
            success: false,
            data: null,
            error: {
              code: 'MEMBER_SUSPENDED',
              message: '이용이 일시 정지된 계정입니다.',
              sanction: {
                status: 'SUSPENDED',
                suspendedUntil: '2026-09-15T10:00:00+09:00',
                reasonCode: 'HARASSMENT',
                reasonMessage: '다른 이용자에 대한 부적절한 언행',
                contactEmail: null,
              },
            },
          },
          403,
        ),
      ),
    );

    const error: unknown = await apiClient('/api/festivals/1/checkin', { method: 'POST' })
      .catch((caught) => caught);
    expect(error).toBeInstanceOf(ApiClientError);
    expect((error as ApiClientError).sanction)
      .toMatchObject({ status: 'SUSPENDED', reasonCode: 'HARASSMENT' });
    // 정지 회원은 로그인 상태로 조회를 계속하므로 로그인 화면으로 보내면 안 된다.
    expect(replace).not.toHaveBeenCalled();
    expect(dispatchEvent).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'member-sanction' }),
    );
  });

  it('403 영구제한은 로그인 화면의 제재 안내로 보낸다', async () => {
    const replace = vi.fn();
    const dispatchEvent = vi.fn();
    vi.stubGlobal('window', { location: { pathname: '/mypage', replace }, dispatchEvent });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(
          {
            success: false,
            data: null,
            error: {
              code: 'MEMBER_BANNED',
              message: '이용이 영구 제한된 계정입니다.',
              sanction: {
                status: 'BANNED',
                suspendedUntil: null,
                reasonCode: 'SAFETY_RISK',
                reasonMessage: '다른 이용자의 안전을 위협하는 행위',
                contactEmail: null,
              },
            },
          },
          403,
        ),
      ),
    );

    await expect(apiClient('/api/members/me')).rejects.toMatchObject({ code: 'MEMBER_BANNED' });
    expect(replace).toHaveBeenCalledWith('/login?oauthError=account_restricted');
    // 영구제한은 로그인 화면 안내가 맡으므로 앱 안 dialog를 띄우지 않는다.
    expect(dispatchEvent).not.toHaveBeenCalled();
  });

  it('제재가 아닌 403은 화면을 이동시키지 않는다', async () => {
    const replace = vi.fn();
    vi.stubGlobal('window', { location: { pathname: '/admin', replace } });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(
          { success: false, data: null, error: { code: 'FORBIDDEN', message: '권한이 없습니다.' } },
          403,
        ),
      ),
    );

    await expect(apiClient('/api/admin/members')).rejects.toMatchObject({ status: 403 });
    expect(replace).not.toHaveBeenCalled();
  });

  it('이미 로그인 화면이면 영구제한 403으로 다시 이동하지 않는다', async () => {
    const replace = vi.fn();
    vi.stubGlobal('window', { location: { pathname: '/login', replace } });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(
          { success: false, data: null, error: { code: 'MEMBER_BANNED', message: '영구 제한된 계정입니다.' } },
          403,
        ),
      ),
    );

    await expect(apiClient('/api/members/me')).rejects.toMatchObject({ code: 'MEMBER_BANNED' });
    expect(replace).not.toHaveBeenCalled();
  });

  it('AbortError를 일반 API 오류로 변환하지 않는다', async () => {
    const abortError = new DOMException('aborted', 'AbortError');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(abortError));
    await expect(apiClientNullable('/api/test', { signal: new AbortController().signal })).rejects.toBe(abortError);
  });

  it('body 없는 HTTP 204를 성공으로 처리한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    await expect(apiClientVoid('/api/members/me/blocks/27', { method: 'DELETE' })).resolves.toBeUndefined();
  });
});
