import { afterEach, describe, expect, it, vi } from 'vitest';
import { festivalsApi } from './festivals';

// 목록 쿼리는 화면이 고른 필터를 URL로 옮기는 유일한 지점이다. 파라미터 이름 하나가 틀리면
// 서버가 조용히 기본값으로 조회해 "필터가 안 먹는" 증상만 남으므로 여기서 계약을 고정한다.
const capturedUrl = () => {
  const call = vi.mocked(globalThis.fetch).mock.calls[0];
  return String(call[0]);
};

const stubFetch = () => {
  const response = {
    ok: true,
    status: 200,
    json: () =>
      Promise.resolve({
        success: true,
        data: { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false },
      }),
  };
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));
};

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('festivalsApi.getList', () => {
  it('기간·진행 상태·정렬을 쿼리 파라미터로 넘긴다', async () => {
    stubFetch();

    await festivalsApi.getList(0, 20, undefined, {
      sort: 'BOOKMARK_COUNT_DESC',
      startDate: '2026-08-01',
      endDate: '2026-08-31',
      progress: 'ENDED',
    });

    const url = capturedUrl();
    expect(url).toContain('sort=BOOKMARK_COUNT_DESC');
    expect(url).toContain('startDate=2026-08-01');
    expect(url).toContain('endDate=2026-08-31');
    expect(url).toContain('progress=ENDED');
  });

  it('기간은 한쪽만 넘겨도 그 값만 붙인다', async () => {
    stubFetch();

    await festivalsApi.getList(0, 20, undefined, { endDate: '2026-08-31' });

    const url = capturedUrl();
    expect(url).toContain('endDate=2026-08-31');
    expect(url).not.toContain('startDate=');
  });

  it('아무 필터도 안 넘기면 기간·상태 파라미터를 붙이지 않는다', async () => {
    // progress를 붙이면 서버가 종료된 축제까지 열어준다. 홈 화면은 그러면 안 된다.
    stubFetch();

    await festivalsApi.getList();

    const url = capturedUrl();
    expect(url).not.toContain('progress=');
    expect(url).not.toContain('startDate=');
    expect(url).not.toContain('endDate=');
  });
});
