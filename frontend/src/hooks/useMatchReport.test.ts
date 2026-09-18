import { describe, expect, it, vi } from 'vitest';
import { createMatchReportSession, type MatchReportState } from './useMatchReport';

const response = {
  reportId: 1,
  groupId: 30,
  reportedMemberId: 2,
  reasonCode: 'RUDE' as const,
  status: 'SUBMITTED' as const,
  createdAt: '2026-08-12T12:00:00+09:00',
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

describe('createMatchReportSession', () => {
  it('선택한 상대·사유와 current group ID로 한 번만 제출한다', async () => {
    const pending = deferred<typeof response>();
    const submit = vi.fn().mockReturnValue(pending.promise);
    const states: MatchReportState[] = [];
    const session = createMatchReportSession(submit, (state) => states.push(state));
    session.open({ memberId: 2, nickname: '여행자B' });
    session.selectReason('RUDE');
    session.confirm();

    const first = session.submit(30);
    const second = session.submit(30);
    expect(submit).toHaveBeenCalledOnce();
    expect(submit).toHaveBeenCalledWith(
      30,
      { reportedMemberId: 2, reasonCode: 'RUDE' },
      expect.any(AbortSignal),
    );
    expect(states.at(-1)?.submitting).toBe(true);

    pending.resolve(response);
    await expect(first).resolves.toBe(true);
    await expect(second).resolves.toBe(true);
    expect(states.at(-1)).toMatchObject({
      step: 'CLOSED',
      successMessage: '여행자B님에 대한 신고가 접수됐어요.',
    });
  });

  it('실패 시 기존 화면을 유지하고 재시도할 수 있다', async () => {
    const submit = vi.fn()
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(response);
    const states: MatchReportState[] = [];
    const session = createMatchReportSession(submit, (state) => states.push(state));
    session.open({ memberId: 2, nickname: '여행자B' });
    session.selectReason('SCAM');
    session.confirm();

    await expect(session.submit(30)).resolves.toBe(false);
    expect(states.at(-1)).toMatchObject({
      step: 'CONFIRM',
      reasonCode: 'SCAM',
      submitting: false,
    });
    expect(states.at(-1)?.error).toBeInstanceOf(Error);
    await expect(session.submit(30)).resolves.toBe(true);
    expect(submit).toHaveBeenCalledTimes(2);
  });

  it('취소하면 초기화하고 늦은 응답이 새 상대 상태를 덮어쓰지 않는다', async () => {
    const first = deferred<typeof response>();
    const submit = vi.fn().mockReturnValue(first.promise);
    const states: MatchReportState[] = [];
    const session = createMatchReportSession(submit, (state) => states.push(state));
    session.open({ memberId: 2, nickname: '여행자B' });
    session.selectReason('OTHER');
    session.confirm();
    const operation = session.submit(30);
    session.close();
    session.open({ memberId: 3, nickname: '여행자C' });

    first.resolve(response);
    await expect(operation).resolves.toBe(false);
    expect(states.at(-1)).toMatchObject({
      step: 'REASON',
      target: { memberId: 3, nickname: '여행자C' },
      reasonCode: null,
      successMessage: null,
    });
  });

  it('사유 미선택 상태에서는 확인이나 제출을 진행하지 않는다', async () => {
    const submit = vi.fn();
    const states: MatchReportState[] = [];
    const session = createMatchReportSession(submit, (state) => states.push(state));
    session.open({ memberId: 2, nickname: '여행자B' });
    session.confirm();
    await expect(session.submit(30)).resolves.toBe(false);
    expect(states.at(-1)?.step).toBe('REASON');
    expect(submit).not.toHaveBeenCalled();
  });
});
