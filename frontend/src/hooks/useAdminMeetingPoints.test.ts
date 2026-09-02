import { describe, expect, it, vi } from 'vitest';
import type { AdminMeetingPoint, AdminMeetingPointUpsertRequest } from '../api/adminMeetingPoints';
import { createAdminMeetingPointsSession, type AdminMeetingPointsState } from './useAdminMeetingPoints';

const point = (id: number, status: AdminMeetingPoint['status'] = 'INACTIVE'): AdminMeetingPoint => ({
  id, festivalId: 144, kakaoPlaceId: `kakao-${id}`, name: `장소 ${id}`, address: '강원 테스트로 1',
  longitude: 128.1, latitude: 37.1, status, assignmentOrder: id * 10,
  createdAt: '2026-08-26T10:00:00+09:00', updatedAt: '2026-08-26T10:00:00+09:00',
});
const upsertRequest: AdminMeetingPointUpsertRequest = {
  kakaoPlaceId: 'kakao-new', name: '새 장소', address: '강원 신규로 1',
  longitude: 128.2, latitude: 37.2, assignmentOrder: 20,
};
function deferred<T>() { let resolve!: (value: T) => void; let reject!: (error: unknown) => void; const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; }); return { promise, resolve, reject }; }
const tick = () => new Promise((resolve) => setTimeout(resolve, 0));

function makeDeps(overrides: Partial<Parameters<typeof createAdminMeetingPointsSession>[0]> = {}) {
  return {
    searchFestivals: vi.fn().mockResolvedValue([]),
    listPoints: vi.fn().mockResolvedValue([]),
    create: vi.fn(),
    update: vi.fn(),
    changeStatus: vi.fn(),
    ...overrides,
  };
}

describe('createAdminMeetingPointsSession', () => {
  it('축제를 선택하면 해당 축제의 장소 목록을 조회한다', async () => {
    const listPoints = vi.fn().mockResolvedValue([point(1)]);
    let state!: AdminMeetingPointsState;
    const session = createAdminMeetingPointsSession(makeDeps({ listPoints }), (next) => { state = next; });
    await session.selectFestival(144, '강릉 단오제');
    expect(listPoints).toHaveBeenCalledWith(144, expect.anything());
    expect(state.pointsStatus).toBe('READY');
    expect(state.points.map((p) => p.id)).toEqual([1]);
  });

  it('다른 축제를 다시 선택하면 이전 목록 조회의 늦은 응답을 무시한다', async () => {
    const first = deferred<AdminMeetingPoint[]>();
    const second = deferred<AdminMeetingPoint[]>();
    const listPoints = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    let state!: AdminMeetingPointsState;
    const session = createAdminMeetingPointsSession(makeDeps({ listPoints }), (next) => { state = next; });
    void session.selectFestival(144, '강릉 단오제');
    void session.selectFestival(150, '다른 축제');
    second.resolve([point(2)]);
    await tick();
    first.resolve([point(1)]);
    await tick();
    expect(state.points.map((p) => p.id)).toEqual([2]);
    expect(state.selectedFestival?.id).toBe(150);
  });

  it('빠른 이중 등록 제출은 API를 한 번만 호출하고 성공 시 목록에 추가한다', async () => {
    const created = deferred<AdminMeetingPoint>();
    const create = vi.fn().mockReturnValue(created.promise);
    let state!: AdminMeetingPointsState;
    const session = createAdminMeetingPointsSession(makeDeps({ create }), (next) => { state = next; });
    await session.selectFestival(144, '강릉 단오제');
    session.openCreateForm();
    const first = session.submitForm(upsertRequest);
    const repeated = session.submitForm(upsertRequest);
    expect(first).toBe(repeated);
    expect(create).toHaveBeenCalledOnce();
    created.resolve(point(9));
    await expect(first).resolves.toBe(true);
    expect(state.points.map((p) => p.id)).toEqual([9]);
    expect(state.formOpen).toBe(false);
    expect(state.successMessage).toContain('활성화');
  });

  it('제출 실패는 기존 목록과 폼을 유지해 재시도할 수 있다', async () => {
    const create = vi.fn().mockRejectedValue(new Error('fail'));
    let state!: AdminMeetingPointsState;
    const session = createAdminMeetingPointsSession(makeDeps({ create }), (next) => { state = next; });
    await session.selectFestival(144, '강릉 단오제');
    session.openCreateForm();
    await expect(session.submitForm(upsertRequest)).resolves.toBe(false);
    expect(state.points).toEqual([]);
    expect(state.formOpen).toBe(true);
    expect(state.formError).toBeInstanceOf(Error);
  });

  it('수정 제출은 대상 장소만 갱신하고 다른 장소는 그대로 둔다', async () => {
    const listPoints = vi.fn().mockResolvedValue([point(1), point(2)]);
    const updated = { ...point(1), name: '수정된 장소' };
    const update = vi.fn().mockResolvedValue(updated);
    let state!: AdminMeetingPointsState;
    const session = createAdminMeetingPointsSession(makeDeps({ listPoints, update }), (next) => { state = next; });
    await session.selectFestival(144, '강릉 단오제');
    session.openEditForm(1);
    await session.submitForm(upsertRequest);
    expect(update).toHaveBeenCalledWith(144, 1, upsertRequest, expect.anything());
    expect(state.points.find((p) => p.id === 1)?.name).toBe('수정된 장소');
    expect(state.points.find((p) => p.id === 2)?.name).toBe('장소 2');
  });

  it('상태 토글은 반대 상태로 요청하고 성공 시 해당 장소만 갱신한다', async () => {
    const listPoints = vi.fn().mockResolvedValue([point(1, 'INACTIVE')]);
    const changeStatus = vi.fn().mockResolvedValue(point(1, 'ACTIVE'));
    let state!: AdminMeetingPointsState;
    const session = createAdminMeetingPointsSession(makeDeps({ listPoints, changeStatus }), (next) => { state = next; });
    await session.selectFestival(144, '강릉 단오제');
    await session.toggleStatus(state.points[0]);
    expect(changeStatus).toHaveBeenCalledWith(144, 1, 'ACTIVE', expect.anything());
    expect(state.points[0].status).toBe('ACTIVE');
    expect(state.togglingPointId).toBeNull();
  });

  it('같은 장소를 두 번 빠르게 토글해도 API는 한 번만 호출한다', async () => {
    const listPoints = vi.fn().mockResolvedValue([point(1, 'ACTIVE')]);
    const toggled = deferred<AdminMeetingPoint>();
    const changeStatus = vi.fn().mockReturnValue(toggled.promise);
    let state!: AdminMeetingPointsState;
    const session = createAdminMeetingPointsSession(makeDeps({ listPoints, changeStatus }), (next) => { state = next; });
    await session.selectFestival(144, '강릉 단오제');
    const target = state.points[0];
    const first = session.toggleStatus(target);
    const repeated = session.toggleStatus(target);
    expect(first).toBe(repeated);
    expect(changeStatus).toHaveBeenCalledOnce();
    toggled.resolve(point(1, 'INACTIVE'));
    await expect(first).resolves.toBe(true);
  });
});
