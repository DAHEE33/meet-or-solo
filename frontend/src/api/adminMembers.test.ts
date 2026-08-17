import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminMembersApi, type AdminMemberActionRequest, type AdminMemberFilters } from './adminMembers';

function ok<T>(data: T) { return new Response(JSON.stringify({ success: true, data, error: null }), { status: 200, headers: { 'Content-Type': 'application/json' } }); }

describe('adminMembersApi', () => {
  afterEach(() => vi.unstubAllGlobals());
  it('검색·상태·role과 opaque cursor를 전송한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ items: [], pagination: { size: 10, hasNext: false, nextCursor: null } })); vi.stubGlobal('fetch', fetchMock);
    const filters: AdminMemberFilters = { query: '회원', status: 'SUSPENDED', role: 'USER' };
    await adminMembersApi.list(filters, 'opaque+/=', 10);
    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).toContain('query=%ED%9A%8C%EC%9B%90'); expect(url).toContain('status=SUSPENDED'); expect(url).toContain('role=USER'); expect(url).toContain('cursor=opaque%2B%2F%3D');
  });
  it('제재 요청은 UUID key와 최소 구조화 body만 전송한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ memberId: 2, status: 'SUSPENDED' })); vi.stubGlobal('fetch', fetchMock);
    const request: AdminMemberActionRequest = { action: 'SUSPEND', reasonCode: 'SAFETY_RISK', reasonNote: null, suspensionDuration: 'SEVEN_DAYS', reportId: 31, expectedStatus: 'ACTIVE' };
    await adminMembersApi.act(2, request, '00000000-0000-0000-0000-000000000001');
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/members/2/actions', expect.objectContaining({ method: 'POST', headers: expect.objectContaining({ 'Idempotency-Key': '00000000-0000-0000-0000-000000000001' }), body: JSON.stringify(request) }));
    expect(String(fetchMock.mock.calls[0][1].body)).not.toMatch(/adminMemberId|token|provider|gps/i);
  });
});
