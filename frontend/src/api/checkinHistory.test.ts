import { afterEach, describe, expect, it, vi } from 'vitest';
import { checkinApi } from './checkin';

afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

function respond(data: unknown) {
  return vi.fn().mockResolvedValue(new Response(
    JSON.stringify({ success: true, data, error: null }),
    { status: 200, headers: { 'Content-Type': 'application/json' } },
  ));
}

const EMPTY = { items: [], pagination: { size: 20, hasNext: false, nextCursor: null } };

describe('checkinApi.getMyHistory', () => {
  it('cookie를 포함해 회원 ID 없이 조회한다', async () => {
    const fetchMock = respond(EMPTY);
    vi.stubGlobal('fetch', fetchMock);

    await expect(checkinApi.getMyHistory()).resolves.toEqual(EMPTY);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/members/me/check-ins',
      expect.objectContaining({ credentials: 'include' }),
    );
    // 조회 대상은 서버가 JWT로 정한다. 회원 ID를 요청에 실으면 타인 이력 조회 시도가 된다.
    expect(JSON.stringify(fetchMock.mock.calls[0])).not.toContain('memberId');
  });

  it('cursor를 encode해서 다음 page를 요청한다', async () => {
    const fetchMock = respond(EMPTY);
    vi.stubGlobal('fetch', fetchMock);

    await checkinApi.getMyHistory('checkin-history:v1 a+b/c=');

    expect(fetchMock.mock.calls[0][0]).toBe(
      '/api/members/me/check-ins?cursor=checkin-history%3Av1%20a%2Bb%2Fc%3D',
    );
  });

  it('cursor가 없으면 query를 붙이지 않는다', async () => {
    const fetchMock = respond(EMPTY);
    vi.stubGlobal('fetch', fetchMock);

    await checkinApi.getMyHistory(null);

    expect(fetchMock.mock.calls[0][0]).toBe('/api/members/me/check-ins');
  });
});
