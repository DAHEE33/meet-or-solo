import { describe, expect, it, vi } from 'vitest';
import { createMemberBlocksSession, type MemberBlocksState } from './useMemberBlocks';
import type { MemberBlock } from '../api/memberBlocks';

const a: MemberBlock = { blockedMemberId: 27, nickname: '가', profileImageUrl: null, blockedAt: '2026-08-14T07:00:00Z' };
const b: MemberBlock = { blockedMemberId: 28, nickname: '나', profileImageUrl: 'https://img', blockedAt: '2026-08-14T08:00:00Z' };
const deferred = <T,>() => { let resolve!: (value: T) => void; let reject!: (error: unknown) => void; const promise = new Promise<T>((ok, no) => { resolve = ok; reject = no; }); return { promise, resolve, reject }; };

describe('createMemberBlocksSession', () => {
  it('loading, 목록, 빈 목록, 오류와 재시도를 상태로 제공한다', async () => {
    const states: MemberBlocksState[] = [];
    const load = vi.fn().mockRejectedValueOnce(new Error('fail')).mockResolvedValueOnce([]);
    const session = createMemberBlocksSession(load, vi.fn(), (state) => states.push(state));
    await session.reload();
    expect(states.map((state) => state.status)).toEqual(['LOADING', 'ERROR']);
    await session.reload();
    expect(states.at(-1)).toMatchObject({ status: 'READY', blocks: [] });
  });

  it('이중 제출을 한 요청으로 수렴하고 성공한 대상만 제거한다', async () => {
    const states: MemberBlocksState[] = [];
    const pending = deferred<void>(); const unblock = vi.fn().mockReturnValue(pending.promise);
    const session = createMemberBlocksSession(vi.fn().mockResolvedValue([a, b]), unblock, (state) => states.push(state));
    await session.reload(); session.open(a);
    const first = session.submit(); const second = session.submit();
    expect(unblock).toHaveBeenCalledOnce(); expect(unblock).toHaveBeenCalledWith(27, expect.any(AbortSignal));
    pending.resolve(); await Promise.all([first, second]);
    expect(states.at(-1)?.blocks).toEqual([b]);
  });

  it('실패는 dialog와 목록을 유지하고 재시도할 수 있다', async () => {
    const states: MemberBlocksState[] = [];
    const unblock = vi.fn().mockRejectedValueOnce(new Error('fail')).mockResolvedValueOnce(undefined);
    const session = createMemberBlocksSession(vi.fn().mockResolvedValue([a, b]), unblock, (state) => states.push(state));
    await session.reload(); session.open(a); await expect(session.submit()).resolves.toBe(false);
    expect(states.at(-1)).toMatchObject({ target: a, blocks: [a, b], submitting: false });
    await expect(session.submit()).resolves.toBe(true); expect(unblock).toHaveBeenCalledTimes(2);
  });

  it('대상 변경과 stop 뒤 늦은 성공과 실패를 무시한다', async () => {
    const states: MemberBlocksState[] = [];
    const late = deferred<void>();
    const session = createMemberBlocksSession(vi.fn().mockResolvedValue([a, b]), vi.fn().mockReturnValue(late.promise), (state) => states.push(state));
    await session.reload(); session.open(a); const result = session.submit(); session.open(b); late.resolve();
    await expect(result).resolves.toBe(false); expect(states.at(-1)?.target).toEqual(b); expect(states.at(-1)?.blocks).toEqual([a, b]);
    const lateFailure = deferred<MemberBlock[]>();
    const stoppedStates: MemberBlocksState[] = [];
    const stopped = createMemberBlocksSession(() => lateFailure.promise, vi.fn(), (state) => stoppedStates.push(state));
    const loading = stopped.reload(); stopped.stop(); lateFailure.reject(new Error('late')); await loading;
    expect(stoppedStates).toHaveLength(1);
  });
});
