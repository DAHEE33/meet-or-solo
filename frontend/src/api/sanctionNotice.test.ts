import { afterEach, describe, expect, it, vi } from 'vitest';
import { sanctionNoticeApi } from './sanctionNotice';

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('sanctionNoticeApi', () => {
  it('cookie 인증으로 사유와 종료 시각을 조회한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
      success: true,
      data: {
        status: 'SUSPENDED',
        suspendedUntil: '2026-09-15T10:00:00+09:00',
        reasonCode: 'NO_SHOW_ABUSE',
        reasonMessage: '반복적인 약속 불이행',
        contactEmail: null,
      },
      error: null,
    }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(sanctionNoticeApi.getMine()).resolves.toMatchObject({
      status: 'SUSPENDED',
      reasonCode: 'NO_SHOW_ABUSE',
      suspendedUntil: '2026-09-15T10:00:00+09:00',
    });
    // notice cookie는 HttpOnly라 자동 전송에 의존한다.
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/sanction-notice',
      expect.objectContaining({ credentials: 'include' }),
    );
  });

  it('제재가 없으면 null을 그대로 돌려준다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      jsonResponse({ success: true, data: null, error: null }),
    ));

    await expect(sanctionNoticeApi.getMine()).resolves.toBeNull();
  });

  /**
   * 신고자 보호. 서버 응답 계약에 제재 시작 시각이나 신고 관련 값이 없어야 한다.
   * 화면이 쓸 수 있는 값이 늘어나면 노출 심사를 다시 해야 한다.
   */
  it('안내 계약에 제재 시작 시각과 신고 관련 값이 없다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({
      success: true,
      data: {
        status: 'BANNED',
        suspendedUntil: null,
        reasonCode: 'SAFETY_RISK',
        reasonMessage: '다른 이용자의 안전을 위협하는 행위',
        contactEmail: null,
      },
      error: null,
    })));

    const notice = await sanctionNoticeApi.getMine();

    expect(Object.keys(notice ?? {})).toEqual([
      'status', 'suspendedUntil', 'reasonCode', 'reasonMessage', 'contactEmail',
    ]);
    expect(notice?.reasonMessage).not.toContain('신고');
  });
});
