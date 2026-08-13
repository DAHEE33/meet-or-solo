import { describe, expect, it, vi } from 'vitest';
import { createMatchBlockSession, type MatchBlockState } from './useMatchBlock';

const response = {
  blockId: 1,
  blockedMemberId: 2,
  createdAt: '2026-08-14T12:00:00+09:00',
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

describe('createMatchBlockSession', () => {
  it('선택한 상대와 current group ID로 빠른 중복 제출을 한 번만 보낸다', async () => {
    const pending = deferred<typeof response>();
    const submit = vi.fn().mockReturnValue(pending.promise);
    const states: MatchBlockState[] = [];
    const session = createMatchBlockSession(submit, (state) => states.push(state));
    session.open({ memberId: 2, nickname: '여행자B' });

    const first = session.submit(30);
    const second = session.submit(30);
    expect(submit).toHaveBeenCalledOnce();
    expect(submit).toHaveBeenCalledWith(
      30,
      { blockedMemberId: 2 },
      expect.any(AbortSignal),
    );
    expect(states.at(-1)?.submitting).toBe(true);

    pending.resolve(response);
    await expect(first).resolves.toBe(true);
    await expect(second).resolves.toBe(true);
    expect(states.at(-1)).toMatchObject({
      open: false,
      target: null,
      successMessage: '회원을 차단했어요. 앞으로 서로 매칭되지 않아요.',
    });
  });

  it('실패 시 대상 dialog를 유지하고 재시도할 수 있다', async () => {
    const submit = vi.fn()
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(response);
    const states: MatchBlockState[] = [];
    const session = createMatchBlockSession(submit, (state) => states.push(state));
    session.open({ memberId: 2, nickname: '여행자B' });

    await expect(session.submit(30)).resolves.toBe(false);
    expect(states.at(-1)).toMatchObject({
      open: true,
      target: { memberId: 2, nickname: '여행자B' },
      submitting: false,
    });
    expect(states.at(-1)?.error).toBeInstanceOf(Error);
    await expect(session.submit(30)).resolves.toBe(true);
    expect(submit).toHaveBeenCalledTimes(2);
  });

  it('취소 후 다른 상대를 열면 이전 요청을 abort하고 늦은 응답을 무시한다', async () => {
    const first = deferred<typeof response>();
    const submit = vi.fn().mockReturnValue(first.promise);
    const states: MatchBlockState[] = [];
    const session = createMatchBlockSession(submit, (state) => states.push(state));
    session.open({ memberId: 2, nickname: '여행자B' });
    const operation = session.submit(30);
    const signal = submit.mock.calls[0][2] as AbortSignal;
    session.close();
    expect(signal.aborted).toBe(true);
    session.open({ memberId: 3, nickname: '여행자C' });

    first.resolve(response);
    await expect(operation).resolves.toBe(false);
    expect(states.at(-1)).toMatchObject({
      open: true,
      target: { memberId: 3, nickname: '여행자C' },
      successMessage: null,
    });
  });

  it('대상 없이 제출하지 않고 unmount 정리 뒤 응답도 반영하지 않는다', async () => {
    const pending = deferred<typeof response>();
    const submit = vi.fn().mockReturnValue(pending.promise);
    const states: MatchBlockState[] = [];
    const session = createMatchBlockSession(submit, (state) => states.push(state));
    await expect(session.submit(30)).resolves.toBe(false);
    expect(submit).not.toHaveBeenCalled();

    session.open({ memberId: 2, nickname: '여행자B' });
    const operation = session.submit(30);
    const stateCount = states.length;
    session.stop();
    pending.resolve(response);
    await expect(operation).resolves.toBe(false);
    expect(states).toHaveLength(stateCount);
  });
});
