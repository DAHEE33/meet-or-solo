import { describe, expect, it, vi } from 'vitest';
import type { AdminSafetyAlert, AdminSafetyAlertPage } from '../api/adminSafetyAlerts';
import {
  createAdminSafetyAlertsSession,
  type AdminSafetyAlertsState,
} from './useAdminSafetyAlerts';

const alert = (
  id: number,
  status: AdminSafetyAlert['status'] = 'OPEN',
): AdminSafetyAlert => ({
  alertId: id,
  alertType: 'REPORT_THRESHOLD',
  status,
  reportedMemberId: 22,
  reportedMemberNickname: '피신고자',
  reportedMemberProfileImageUrl: null,
  reportedMemberStatus: 'ACTIVE',
  triggerReportId: 900 + id,
  validReportCount: 3,
  handledAt: status === 'OPEN' ? null : '2026-08-15T11:00:00+09:00',
  createdAt: '2026-08-15T10:00:00+09:00',
});

const page = (
  alerts: AdminSafetyAlert[] = [alert(1)],
  nextCursor: string | null = null,
  openCount = alerts.filter((item) => item.status === 'OPEN').length,
): AdminSafetyAlertPage => ({
  alerts,
  pagination: { size: 20, hasNext: nextCursor !== null, nextCursor },
  openCount,
});

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}
const tick = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('createAdminSafetyAlertsSession', () => {
  it('기본 filter는 미확인 알림이고 openCount를 함께 노출한다', async () => {
    const list = vi.fn().mockResolvedValue(page([alert(1), alert(2)], null, 2));
    let state!: AdminSafetyAlertsState;
    const session = createAdminSafetyAlertsSession(
      { list, acknowledge: vi.fn() }, (next) => { state = next; });

    await session.load();

    expect(list.mock.calls[0][0]).toBe('OPEN');
    expect(state.status).toBe('READY');
    expect(state.openCount).toBe(2);
    expect(state.alerts.map((item) => item.alertId)).toEqual([1, 2]);
  });

  it('filter 변경은 cursor history를 초기화하고 오래된 응답을 무시한다', async () => {
    const first = deferred<AdminSafetyAlertPage>();
    const second = deferred<AdminSafetyAlertPage>();
    const list = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    let state!: AdminSafetyAlertsState;
    const session = createAdminSafetyAlertsSession(
      { list, acknowledge: vi.fn() }, (next) => { state = next; });

    void session.load();
    void session.applyFilter('ACKNOWLEDGED');
    second.resolve(page([alert(2, 'ACKNOWLEDGED')], null, 0));
    await tick();
    first.resolve(page([alert(1)]));
    await tick();

    expect(state.alerts.map((item) => item.alertId)).toEqual([2]);
    expect(state.filter).toBe('ACKNOWLEDGED');
    expect(list.mock.calls[1][1]).toBeNull();
  });

  it('cursor 다음·이전 이동에 cursor history를 사용한다', async () => {
    const list = vi.fn()
      .mockResolvedValueOnce(page([alert(3)], 'next'))
      .mockResolvedValueOnce(page([alert(2)]))
      .mockResolvedValueOnce(page([alert(3)], 'next'));
    let state!: AdminSafetyAlertsState;
    const session = createAdminSafetyAlertsSession(
      { list, acknowledge: vi.fn() }, (next) => { state = next; });

    await session.load();
    await session.next();
    expect(state.pageIndex).toBe(1);
    expect(list.mock.calls[1][1]).toBe('next');

    await session.previous();
    expect(state.pageIndex).toBe(0);
    expect(list.mock.calls[2][1]).toBeNull();
  });

  it('미확인 목록에서 확인 처리하면 그 항목만 목록에서 빠지고 미확인 수도 줄어든다', async () => {
    // 미확인 목록은 처리 대기 큐이므로 확인한 항목은 남지 않아야 badge와 어긋나지 않는다.
    const list = vi.fn().mockResolvedValue(page([alert(1), alert(2)], null, 2));
    const acknowledge = vi.fn().mockResolvedValue(alert(1, 'ACKNOWLEDGED'));
    let state!: AdminSafetyAlertsState;
    const session = createAdminSafetyAlertsSession(
      { list, acknowledge }, (next) => { state = next; });

    await session.load();
    expect(state.filter).toBe('OPEN');
    await session.acknowledge(1);

    expect(state.alerts.map((item) => item.alertId)).toEqual([2]);
    expect(state.alerts[0].status).toBe('OPEN');
    expect(state.openCount).toBe(1);
    expect(state.successMessage).toBe('알림을 확인 처리했습니다.');
  });

  it('전체 목록에서 확인 처리하면 항목을 제자리에서 갱신한다', async () => {
    const list = vi.fn()
      .mockResolvedValueOnce(page([alert(1), alert(2)], null, 2))
      .mockResolvedValueOnce(page([alert(1), alert(2)], null, 2));
    const acknowledge = vi.fn().mockResolvedValue(alert(1, 'ACKNOWLEDGED'));
    let state!: AdminSafetyAlertsState;
    const session = createAdminSafetyAlertsSession(
      { list, acknowledge }, (next) => { state = next; });

    await session.load();
    await session.applyFilter('');
    await session.acknowledge(1);

    expect(state.alerts.map((item) => item.alertId)).toEqual([1, 2]);
    expect(state.alerts[0].status).toBe('ACKNOWLEDGED');
    expect(state.alerts[1].status).toBe('OPEN');
    expect(state.openCount).toBe(1);
  });

  it('같은 알림을 이중 제출하면 요청은 한 번만 나간다', async () => {
    const list = vi.fn().mockResolvedValue(page());
    const pending = deferred<AdminSafetyAlert>();
    const acknowledge = vi.fn().mockReturnValue(pending.promise);
    let state!: AdminSafetyAlertsState;
    const session = createAdminSafetyAlertsSession(
      { list, acknowledge }, (next) => { state = next; });

    await session.load();
    const firstCall = session.acknowledge(1);
    const secondCall = session.acknowledge(1);
    expect(secondCall).toBe(firstCall);
    expect(state.acknowledgingId).toBe(1);

    pending.resolve(alert(1, 'ACKNOWLEDGED'));
    await firstCall;
    expect(acknowledge).toHaveBeenCalledTimes(1);
    expect(state.acknowledgingId).toBeNull();
  });

  it('확인 처리 실패 시 목록 snapshot과 미확인 수는 그대로 유지한다', async () => {
    const list = vi.fn().mockResolvedValue(page([alert(1)], null, 1));
    const acknowledge = vi.fn().mockRejectedValue(new Error('실패'));
    let state!: AdminSafetyAlertsState;
    const session = createAdminSafetyAlertsSession(
      { list, acknowledge }, (next) => { state = next; });

    await session.load();
    await session.acknowledge(1);

    expect(state.alerts[0].status).toBe('OPEN');
    expect(state.openCount).toBe(1);
    expect(state.acknowledgingId).toBeNull();
    expect(state.actionError).toBeInstanceOf(Error);
    expect(state.successMessage).toBeNull();
  });

  it('이미 확인된 알림을 다시 확인해도 미확인 수를 중복으로 줄이지 않는다', async () => {
    const list = vi.fn().mockResolvedValue(page([alert(1, 'ACKNOWLEDGED')], null, 0));
    const acknowledge = vi.fn().mockResolvedValue(alert(1, 'ACKNOWLEDGED'));
    let state!: AdminSafetyAlertsState;
    const session = createAdminSafetyAlertsSession(
      { list, acknowledge }, (next) => { state = next; });

    await session.load();
    await session.acknowledge(1);

    expect(state.openCount).toBe(0);
  });

  it('목록 조회 실패는 ERROR 상태가 된다', async () => {
    const list = vi.fn().mockRejectedValue(new Error('실패'));
    let state!: AdminSafetyAlertsState;
    const session = createAdminSafetyAlertsSession(
      { list, acknowledge: vi.fn() }, (next) => { state = next; });

    await session.load();

    expect(state.status).toBe('ERROR');
  });

  it('stop 이후에는 상태를 더 publish하지 않는다', async () => {
    const pending = deferred<AdminSafetyAlertPage>();
    const list = vi.fn().mockReturnValue(pending.promise);
    const onState = vi.fn();
    const session = createAdminSafetyAlertsSession({ list, acknowledge: vi.fn() }, onState);

    void session.load();
    const publishCount = onState.mock.calls.length;
    session.stop();
    pending.resolve(page());
    await tick();

    expect(onState.mock.calls.length).toBe(publishCount);
  });
});
