import { describe, expect, it, vi } from 'vitest';
import type { AdminMemberActionRequest, AdminMemberDetail, AdminMemberPage } from '../api/adminMembers';
import { createAdminMembersSession, type AdminMembersState } from './useAdminMembers';

const detail = (id: number, status: AdminMemberDetail['status'] = 'ACTIVE'): AdminMemberDetail => ({ memberId: id, nickname: `회원${id}`, profileImageUrl: null, role: 'USER', status, penaltyScore: 0, mannerTemperature: 36.5, suspendedAt: null, suspendedUntil: null, createdAt: '2026-08-16T09:00:00+09:00', lastLoginAt: null, reports: [], actions: [] });
const page = (items = [detail(1)], cursor: string | null = null): AdminMemberPage => ({ items, pagination: { size: 20, hasNext: cursor !== null, nextCursor: cursor } });
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>((res) => { resolve = res; }); return { promise, resolve }; }

describe('createAdminMembersSession', () => {
  it('검색 변경은 cursor를 초기화하고 늦은 목록 응답을 무시한다', async () => {
    const first = deferred<AdminMemberPage>(); const second = deferred<AdminMemberPage>(); const list = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise); let state!: AdminMembersState;
    const session = createAdminMembersSession({ list, detail: vi.fn(), act: vi.fn() }, (next) => { state = next; }); void session.load(); void session.applyFilters({ query: '새회원', status: '', role: 'USER' }); second.resolve(page([detail(2)])); await Promise.resolve(); first.resolve(page([detail(1)])); await Promise.resolve();
    expect(state.items.map((item) => item.memberId)).toEqual([2]); expect(state.filters.query).toBe('새회원'); expect(list.mock.calls[1][1]).toBeNull();
  });
  it('이중 제출은 한 번만 호출하고 실패 전 snapshot을 유지한다', async () => {
    const changed = deferred<AdminMemberDetail>(); const act = vi.fn().mockReturnValue(changed.promise); let state!: AdminMembersState;
    const session = createAdminMembersSession({ list: vi.fn().mockResolvedValue(page([detail(1), detail(2)])), detail: vi.fn().mockResolvedValue(detail(1)), act }, (next) => { state = next; }); await session.load(); await session.openDetail(1);
    const request: AdminMemberActionRequest = { action: 'BAN', reasonCode: 'SAFETY_RISK', reasonNote: null, suspensionDuration: null, reportId: null, expectedStatus: 'ACTIVE' }; session.requestAction(request); const one = session.submitAction(); const two = session.submitAction();
    expect(one).toBe(two); expect(act).toHaveBeenCalledOnce(); expect(state.items[0].status).toBe('ACTIVE'); changed.resolve(detail(1, 'BANNED')); await one;
    expect(state.items[0].status).toBe('BANNED'); expect(state.items[1].status).toBe('ACTIVE');
  });
  it('실패하면 상세와 확인 요청을 유지한다', async () => {
    let state!: AdminMembersState; const session = createAdminMembersSession({ list: vi.fn().mockResolvedValue(page()), detail: vi.fn().mockResolvedValue(detail(1)), act: vi.fn().mockRejectedValue(new Error('fail')) }, (next) => { state = next; }); await session.load(); await session.openDetail(1);
    const request: AdminMemberActionRequest = { action: 'WARNING', reasonCode: 'OTHER', reasonNote: null, suspensionDuration: null, reportId: null, expectedStatus: 'ACTIVE' }; session.requestAction(request); await session.submitAction();
    expect(state.detail?.status).toBe('ACTIVE'); expect(state.pendingAction?.action).toBe('WARNING'); expect(state.actionError).toBeInstanceOf(Error);
  });
});
