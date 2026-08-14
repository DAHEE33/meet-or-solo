import { afterEach, describe, expect, it, vi } from 'vitest';
import { memberBlocksApi } from './memberBlocks';

afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

describe('memberBlocksApi', () => {
  it('본인의 공개 차단 목록을 조회한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ success: true, data: [{ blockedMemberId: 27, nickname: '테스트', profileImageUrl: null, blockedAt: '2026-08-14T07:00:00Z' }], error: null }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', fetchMock);
    await expect(memberBlocksApi.getMine()).resolves.toHaveLength(1);
    expect(fetchMock).toHaveBeenCalledWith('/api/members/me/blocks', expect.objectContaining({ credentials: 'include' }));
  });

  it('정확한 blockedMemberId로 body 없이 DELETE하고 204를 성공 처리한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);
    await expect(memberBlocksApi.unblock(27)).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith('/api/members/me/blocks/27', expect.objectContaining({ method: 'DELETE', credentials: 'include' }));
    const options = fetchMock.mock.calls[0][1] as RequestInit;
    expect(options.body).toBeUndefined();
    expect(JSON.stringify(options)).not.toContain('blockerMemberId');
  });
});
