import { describe, expect, it, vi } from 'vitest';
import type { AdminReportDetail, AdminReportFilters, AdminReportListItem, AdminReportPage } from '../api/adminReports';
import { createAdminReportsSession, type AdminReportsState } from './useAdminReports';

const filters: AdminReportFilters = { status: '', reason: '', createdFrom: '', createdTo: '' };
const detail = (id: number, status: AdminReportDetail['status'] = 'SUBMITTED'): AdminReportDetail => ({
  reportId: id, group: { groupId: 41, status: 'COMPLETED', confirmedAt: '2026-08-15T09:00:00+09:00' },
  reasonCode: 'SAFETY', status,
  reporter: { memberId: 21, nickname: '신고자', profileImageUrl: null, memberStatus: 'ACTIVE' },
  reportedMember: { memberId: 22, nickname: '피신고자', profileImageUrl: null, memberStatus: 'ACTIVE' },
  createdAt: '2026-08-15T10:00:00+09:00', updatedAt: '2026-08-15T10:00:00+09:00',
  resolvedAt: status === 'RESOLVED' ? '2026-08-15T11:00:00+09:00' : null,
});
const page = (items: Array<AdminReportListItem | AdminReportDetail> = [detail(1)], nextCursor: string | null = null): AdminReportPage => ({
  items: items.map((item) => ('groupId' in item ? item : { ...item, groupId: item.group?.groupId ?? null })),
  pagination: { size: 20, hasNext: nextCursor !== null, nextCursor },
});
function deferred<T>() { let resolve!: (value: T) => void; let reject!: (error: unknown) => void; const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; }); return { promise, resolve, reject }; }
const tick = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('createAdminReportsSession', () => {
  it('filter 변경은 cursor history를 초기화하고 이전 늦은 응답을 무시한다', async () => {
    const first = deferred<AdminReportPage>(); const second = deferred<AdminReportPage>();
    const list = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    let state!: AdminReportsState;
    const session = createAdminReportsSession({ list, detail: vi.fn(), changeStatus: vi.fn() }, (next) => { state = next; });
    void session.load();
    const changed = { ...filters, status: 'REVIEWING' as const };
    void session.applyFilters(changed);
    second.resolve(page([detail(2, 'REVIEWING')])); await tick();
    first.resolve(page([detail(1)])); await tick();
    expect(state.items.map((item) => item.reportId)).toEqual([2]);
    expect(state.filters.status).toBe('REVIEWING');
    expect(list.mock.calls[1][1]).toBeNull();
  });

  it('cursor 다음·이전 이동에 cursor history를 사용한다', async () => {
    const list = vi.fn()
      .mockResolvedValueOnce(page([detail(3)], 'next'))
      .mockResolvedValueOnce(page([detail(2)]))
      .mockResolvedValueOnce(page([detail(3)], 'next'));
    let state!: AdminReportsState;
    const session = createAdminReportsSession({ list, detail: vi.fn(), changeStatus: vi.fn() }, (next) => { state = next; });
    await session.load(); await session.next();
    expect(state.pageIndex).toBe(1); expect(list.mock.calls[1][1]).toBe('next');
    await session.previous();
    expect(state.pageIndex).toBe(0); expect(list.mock.calls[2][1]).toBeNull();
  });

  it('빠른 이중 제출은 API 한 번만 호출하고 성공 시 대상 row와 상세만 갱신한다', async () => {
    const changed = deferred<AdminReportDetail>();
    const changeStatus = vi.fn().mockReturnValue(changed.promise);
    let state!: AdminReportsState;
    const session = createAdminReportsSession({ list: vi.fn().mockResolvedValue(page([detail(1), detail(2)])), detail: vi.fn().mockResolvedValue(detail(1)), changeStatus }, (next) => { state = next; });
    await session.load(); await session.openDetail(1); session.requestAction('RESOLVED');
    const first = session.submitAction(); const repeated = session.submitAction();
    expect(first).toBe(repeated); expect(changeStatus).toHaveBeenCalledOnce(); expect(state.items[0].status).toBe('SUBMITTED');
    changed.resolve(detail(1, 'RESOLVED')); await expect(first).resolves.toBe(true);
    expect(state.detail?.status).toBe('RESOLVED'); expect(state.items[0].status).toBe('RESOLVED');
    expect(state.items[1].status).toBe('SUBMITTED');
  });

  it('action 실패는 기존 snapshot과 확인 대상을 유지해 재시도할 수 있다', async () => {
    let state!: AdminReportsState;
    const session = createAdminReportsSession({ list: vi.fn().mockResolvedValue(page()), detail: vi.fn().mockResolvedValue(detail(1)), changeStatus: vi.fn().mockRejectedValue(new Error('fail')) }, (next) => { state = next; });
    await session.load(); await session.openDetail(1); session.requestAction('REJECTED');
    await expect(session.submitAction()).resolves.toBe(false);
    expect(state.detail?.status).toBe('SUBMITTED'); expect(state.items[0].status).toBe('SUBMITTED');
    expect(state.targetStatus).toBe('REJECTED'); expect(state.actionError).toBeInstanceOf(Error);
  });
});
