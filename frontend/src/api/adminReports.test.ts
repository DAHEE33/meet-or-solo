import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminReportsApi, type AdminReportFilters } from './adminReports';

const filters: AdminReportFilters = {
  status: 'SUBMITTED', reason: 'SAFETY',
  createdFrom: '2026-08-01T00:00:00+09:00', createdTo: '2026-09-01T00:00:00+09:00',
};

function ok<T>(data: T) {
  return new Response(JSON.stringify({ success: true, data, error: null }), {
    status: 200, headers: { 'Content-Type': 'application/json' },
  });
}

describe('adminReportsApi', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('HttpOnly cookie 기반 관리자 session을 조회한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ memberId: 2, nickname: '관리자', role: 'ADMIN' }));
    vi.stubGlobal('fetch', fetchMock);
    await expect(adminReportsApi.getSession()).resolves.toMatchObject({ role: 'ADMIN' });
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/me', expect.objectContaining({ credentials: 'include' }));
  });

  it('filter와 opaque cursor 및 size를 정확히 전송한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ items: [], pagination: { size: 10, hasNext: false, nextCursor: null } }));
    vi.stubGlobal('fetch', fetchMock);
    await adminReportsApi.list(filters, 'opaque+/=', 10);
    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).toContain('/api/admin/reports?');
    expect(url).toContain('status=SUBMITTED');
    expect(url).toContain('reason=SAFETY');
    expect(url).toContain('cursor=opaque%2B%2F%3D');
    expect(url).toContain('size=10');
  });

  it('상세 ID는 path에만 사용한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ reportId: 31 }));
    vi.stubGlobal('fetch', fetchMock);
    await adminReportsApi.detail(31);
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/reports/31', expect.objectContaining({ credentials: 'include' }));
  });

  it('상태 변경 body에는 targetStatus만 포함한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ reportId: 31, status: 'RESOLVED' }));
    vi.stubGlobal('fetch', fetchMock);
    await adminReportsApi.changeStatus(31, 'RESOLVED');
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/reports/31/status', expect.objectContaining({
      credentials: 'include', method: 'PATCH', body: JSON.stringify({ targetStatus: 'RESOLVED' }),
    }));
    expect(String(fetchMock.mock.calls[0][1].body)).not.toMatch(/admin|targetMember|reportId/);
  });
});
