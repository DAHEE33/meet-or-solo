import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminMeetingPointsApi, type AdminMeetingPointUpsertRequest } from './adminMeetingPoints';

const upsertRequest: AdminMeetingPointUpsertRequest = {
  kakaoPlaceId: 'kakao-1', name: '테스트 장소', address: '강원 테스트로 1',
  longitude: 128.1, latitude: 37.1, assignmentOrder: 10,
};

function ok<T>(data: T) {
  return new Response(JSON.stringify({ success: true, data, error: null }), {
    status: 200, headers: { 'Content-Type': 'application/json' },
  });
}

describe('adminMeetingPointsApi', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('축제 ID로 목록을 조회한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok([]));
    vi.stubGlobal('fetch', fetchMock);
    await adminMeetingPointsApi.list(144);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/festivals/144/meeting-points',
      expect.objectContaining({ credentials: 'include' }),
    );
  });

  it('등록은 POST와 upsert body를 그대로 전송한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ id: 1 }));
    vi.stubGlobal('fetch', fetchMock);
    await adminMeetingPointsApi.create(144, upsertRequest);
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/festivals/144/meeting-points', expect.objectContaining({
      method: 'POST', credentials: 'include', body: JSON.stringify(upsertRequest),
    }));
  });

  it('수정은 PUT으로 pointId path를 사용한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ id: 1 }));
    vi.stubGlobal('fetch', fetchMock);
    await adminMeetingPointsApi.update(144, 1, upsertRequest);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/festivals/144/meeting-points/1',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify(upsertRequest) }),
    );
  });

  it('상태 변경 body에는 status만 포함한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ id: 1, status: 'ACTIVE' }));
    vi.stubGlobal('fetch', fetchMock);
    await adminMeetingPointsApi.changeStatus(144, 1, 'ACTIVE');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/festivals/144/meeting-points/1/status',
      expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ status: 'ACTIVE' }) }),
    );
  });
});
