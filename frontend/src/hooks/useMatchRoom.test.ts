import { describe, expect, it, vi } from 'vitest';
import type { CurrentMatchGroup } from '../api/matching';
import type {
  MatchingStateChangedNotification,
  MatchingWebSocketCallbacks,
} from '../api/matchingWebSocket';
import {
  ARRIVAL_CHANGE_NOTICE_MS,
  createMatchRoomSession,
  MATCH_ROOM_FALLBACK_POLL_MS,
  type MatchRoomState,
} from './useMatchRoom';

const group: CurrentMatchGroup = {
  groupId: 30,
  festivalId: 2,
  status: 'CONFIRMED',
  confirmedMemberCount: 2,
  confirmedAt: '2026-07-27T12:00:20+09:00',
  arrivalDeadlineAt: '2026-07-27T12:30:20+09:00',
  currentMemberId: 1,
  festival: {
    festivalId: 2,
    title: '테스트 축제',
    address: null,
    eventStartDate: '2026-07-27',
    eventEndDate: '2026-07-29',
  },
  members: [
    { memberId: 1, nickname: 'member-a', profileImageUrl: null, status: 'JOINED', arrivalMinutes: null, arrivalTimeSelectedAt: null },
    { memberId: 2, nickname: 'member-b', profileImageUrl: null, status: 'JOINED', arrivalMinutes: null, arrivalTimeSelectedAt: null },
  ],
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function harness(loadEvents = vi.fn().mockResolvedValue({ events: [] })) {
  let callbacks: MatchingWebSocketCallbacks | null = null;
  const loads: Array<ReturnType<typeof deferred<CurrentMatchGroup | null>>> = [];
  const signals: AbortSignal[] = [];
  const mutationSignals: AbortSignal[] = [];
  const mutations: Array<{
    minutes: 5 | 10 | 20 | 25;
    request: ReturnType<typeof deferred<CurrentMatchGroup>>;
  }> = [];
  const states: MatchRoomState[] = [];
  const scheduled = new Map<number, () => void>();
  const scheduledDelays = new Map<number, number>();
  let nextTimer = 1;
  const disconnect = vi.fn();
  const session = createMatchRoomSession({
    loadCurrentGroup: vi.fn((signal) => {
      signals.push(signal);
      const request = deferred<CurrentMatchGroup | null>();
      loads.push(request);
      return request.promise;
    }),
    loadCurrentGroupEvents: loadEvents,
    selectArrivalTime: vi.fn((minutes, signal) => {
      mutationSignals.push(signal);
      const request = deferred<CurrentMatchGroup>();
      mutations.push({ minutes, request });
      return request.promise;
    }),
    connect: (nextCallbacks) => {
      callbacks = nextCallbacks;
      return disconnect;
    },
    schedule: (callback, delay) => {
      expect([MATCH_ROOM_FALLBACK_POLL_MS, ARRIVAL_CHANGE_NOTICE_MS]).toContain(delay);
      const id = nextTimer++;
      scheduled.set(id, callback);
      scheduledDelays.set(id, delay);
      return id;
    },
    cancelSchedule: (id) => {
      scheduled.delete(id);
      scheduledDelays.delete(id);
    },
    onState: (state) => states.push(state),
  });
  return {
    get callbacks() {
      if (!callbacks) throw new Error('WebSocket callbacks가 등록되지 않았습니다.');
      return callbacks;
    },
    loads,
    signals,
    mutationSignals,
    mutations,
    states,
    scheduled,
    scheduledDelays,
    disconnect,
    loadEvents,
    session,
  };
}

const notification: MatchingStateChangedNotification = {
  type: 'MATCHING_STATE_CHANGED',
  reason: 'MATCH_CONFIRMED',
  occurredAt: '2026-07-29T12:00:00+09:00',
};

describe('createMatchRoomSession', () => {
  it('최초 mount REST 복원 후 연결·재연결과 정상 알림마다 REST를 refresh한다', async () => {
    const test = harness();
    expect(test.loads).toHaveLength(1);
    expect(test.loadEvents).toHaveBeenCalledTimes(1);
    test.loads[0].resolve(group);
    await test.session.refresh();
    expect(test.states.at(-1)).toMatchObject({ status: 'READY', group });

    test.callbacks.onConnected();
    expect(test.loads).toHaveLength(2);
    expect(test.loadEvents).toHaveBeenCalledTimes(2);
    test.loads[1].resolve(group);
    await test.session.refresh();

    test.callbacks.onDisconnected?.();
    test.callbacks.onConnected();
    expect(test.loads).toHaveLength(3);
    expect(test.loadEvents).toHaveBeenCalledTimes(3);
    test.loads[2].resolve(group);
    await test.session.refresh();

    test.callbacks.onStateChanged(notification);
    expect(test.loads).toHaveLength(4);
    expect(test.loadEvents).toHaveBeenCalledTimes(4);
  });

  it('WebSocket 장애 중 polling fallback으로 REST를 refresh한다', async () => {
    const test = harness();
    test.loads[0].resolve(group);
    await test.session.refresh();
    expect(test.scheduled.size).toBe(1);

    const poll = [...test.scheduled.values()][0];
    poll();
    expect(test.loads).toHaveLength(2);
  });

  it('data:null과 API 오류를 각각 EMPTY와 재시도 가능한 ERROR로 만든다', async () => {
    const empty = harness();
    empty.loads[0].resolve(null);
    await empty.session.refresh();
    expect(empty.states.at(-1)?.status).toBe('EMPTY');
    expect(empty.scheduled.size).toBe(0);

    const failed = harness();
    failed.loads[0].reject(new Error('network'));
    await failed.session.refresh();
    expect(failed.states.at(-1)?.status).toBe('ERROR');
    expect(failed.scheduled.size).toBe(1);
  });

  it('group 성공과 events 실패를 분리해 group snapshot을 유지한다', async () => {
    const test = harness(vi.fn().mockRejectedValue(new Error('events network')));
    test.loads[0].resolve(group);

    await test.session.refresh();

    expect(test.states.at(-1)).toMatchObject({
      status: 'READY',
      group,
      events: [],
    });
    expect(test.states.at(-1)?.eventsError).toBeInstanceOf(Error);
  });

  it('stop에서 timer, WebSocket과 진행 중 AbortController를 정리하고 후속 callback을 무시한다', () => {
    const test = harness();
    test.callbacks.onDisconnected?.();
    expect(test.scheduled.size).toBe(1);
    test.session.stop();
    expect(test.signals[0].aborted).toBe(true);
    expect(test.scheduled.size).toBe(0);
    expect(test.disconnect).toHaveBeenCalledOnce();
    test.callbacks.onConnected();
    test.callbacks.onStateChanged(notification);
    expect(test.loads).toHaveLength(1);
  });

  it('도착 시간 mutation 중 중복 제출을 합치고 성공 snapshot을 즉시 반영한다', async () => {
    const test = harness();
    test.loads[0].resolve(group);
    await test.session.refresh();
    const changed: CurrentMatchGroup = {
      ...group,
      members: [
        {
          ...group.members[0],
          status: 'ARRIVAL_TIME_SELECTED',
          arrivalMinutes: 10,
          arrivalTimeSelectedAt: '2026-07-27T12:05:00+09:00',
        },
        group.members[1],
      ],
    };

    const first = test.session.selectArrivalTime(10);
    const duplicate = test.session.selectArrivalTime(5);
    expect(test.mutations).toHaveLength(1);
    expect(test.mutations[0].minutes).toBe(10);
    expect(test.states.at(-1)?.isSubmitting).toBe(true);
    test.mutations[0].request.resolve(changed);

    await expect(first).resolves.toBe(true);
    await expect(duplicate).resolves.toBe(true);
    expect(test.states.at(-1)).toMatchObject({
      status: 'READY',
      group: changed,
      isSubmitting: false,
      actionError: null,
    });
  });

  it('mutation 실패 시 기존 snapshot을 유지하고 오류를 표시하며 재시도할 수 있다', async () => {
    const test = harness();
    test.loads[0].resolve(group);
    await test.session.refresh();

    const failed = test.session.selectArrivalTime(20);
    test.mutations[0].request.reject(new Error('network'));
    await expect(failed).resolves.toBe(false);
    expect(test.states.at(-1)?.group).toBe(group);
    expect(test.states.at(-1)?.actionError).toBeInstanceOf(Error);
    expect(test.states.at(-1)?.isSubmitting).toBe(false);

    const retry = test.session.selectArrivalTime(20);
    expect(test.mutations).toHaveLength(2);
    test.mutations[1].request.resolve(group);
    await expect(retry).resolves.toBe(true);
  });

  it('mutation 뒤 늦게 끝난 이전 refresh 응답이 최신 snapshot을 덮지 않는다', async () => {
    const test = harness();
    test.loads[0].resolve(group);
    await test.session.refresh();

    const poll = [...test.scheduled.values()][0];
    poll();
    const changed: CurrentMatchGroup = {
      ...group,
      status: 'IN_PROGRESS',
    };
    const mutation = test.session.selectArrivalTime(10);
    test.mutations[0].request.resolve(changed);
    await expect(mutation).resolves.toBe(true);

    test.loads[1].resolve(group);
    await test.session.refresh();

    expect(test.states.at(-1)?.group).toBe(changed);
  });

  it('stop에서 진행 중 mutation AbortController도 정리한다', async () => {
    const test = harness();
    test.loads[0].resolve(group);
    await test.session.refresh();
    void test.session.selectArrivalTime(25);
    test.session.stop();
    expect(test.mutationSignals[0].aborted).toBe(true);
  });

  it('최초 snapshot과 동일 refresh에는 상대 변경 알림을 만들지 않는다', async () => {
    const test = harness();
    test.loads[0].resolve(group);
    await test.session.refresh();
    expect(test.states.at(-1)?.arrivalChangeNotice).toBeNull();

    const refresh = test.session.refresh();
    test.loads[1].resolve({ ...group, members: group.members.map((member) => ({ ...member })) });
    await refresh;
    expect(test.states.at(-1)?.arrivalChangeNotice).toBeNull();
  });

  it('WebSocket REST refresh에서 상대 도착 시간이 실제 변경되면 nickname 알림을 자동 제거한다', async () => {
    const test = harness();
    test.loads[0].resolve(group);
    await test.session.refresh();

    test.callbacks.onStateChanged(notification);
    const changed = {
      ...group,
      members: [
        group.members[0],
        {
          ...group.members[1],
          status: 'ARRIVAL_TIME_SELECTED' as const,
          arrivalMinutes: 25 as const,
          arrivalTimeSelectedAt: '2026-07-27T12:05:00+09:00',
        },
      ],
    };
    test.loads[1].resolve(changed);
    await test.session.refresh();

    expect(test.states.at(-1)?.arrivalChangeNotice)
      .toBe('member-b님이 도착 시간을 변경하였어요.');
    const noticeTimerId = [...test.scheduledDelays.entries()]
      .find(([, delay]) => delay === ARRIVAL_CHANGE_NOTICE_MS)?.[0];
    expect(noticeTimerId).toBeDefined();
    test.scheduled.get(noticeTimerId!)?.();
    expect(test.states.at(-1)?.arrivalChangeNotice).toBeNull();
  });

  it('polling fallback에서도 상대 변경을 찾고 본인 변경에는 알림을 만들지 않는다', async () => {
    const test = harness();
    test.loads[0].resolve(group);
    await test.session.refresh();

    const pollTimerId = [...test.scheduledDelays.entries()]
      .find(([, delay]) => delay === MATCH_ROOM_FALLBACK_POLL_MS)?.[0];
    test.scheduled.get(pollTimerId!)?.();
    test.loads[1].resolve({
      ...group,
      members: [
        group.members[0],
        { ...group.members[1], arrivalMinutes: 10, arrivalTimeSelectedAt: '2026-07-27T12:01:00+09:00' },
      ],
    });
    await test.session.refresh();
    expect(test.states.at(-1)?.arrivalChangeNotice)
      .toBe('member-b님이 도착 시간을 변경하였어요.');
    const noticeTimerId = [...test.scheduledDelays.entries()]
      .find(([, delay]) => delay === ARRIVAL_CHANGE_NOTICE_MS)?.[0];
    test.scheduled.get(noticeTimerId!)?.();

    const ownOnly = {
      ...test.states.at(-1)!.group!,
      members: [
        { ...group.members[0], arrivalMinutes: 5 as const, arrivalTimeSelectedAt: '2026-07-27T12:02:00+09:00' },
        test.states.at(-1)!.group!.members[1],
      ],
    };
    const refresh = test.session.refresh();
    test.loads[2].resolve(ownOnly);
    await refresh;
    expect(test.states.at(-1)?.arrivalChangeNotice).toBeNull();
  });

  it('실패한 refresh에는 상대 변경 알림을 만들지 않는다', async () => {
    const test = harness();
    test.loads[0].resolve(group);
    await test.session.refresh();

    const refresh = test.session.refresh();
    test.loads[1].reject(new Error('network'));
    await refresh;

    expect(test.states.at(-1)?.arrivalChangeNotice).toBeNull();
  });

  it('stop은 상대 변경 snackbar timer도 정리한다', async () => {
    const test = harness();
    test.loads[0].resolve(group);
    await test.session.refresh();
    const refresh = test.session.refresh();
    test.loads[1].resolve({
      ...group,
      members: [
        group.members[0],
        { ...group.members[1], arrivalMinutes: 20, arrivalTimeSelectedAt: '2026-07-27T12:03:00+09:00' },
      ],
    });
    await refresh;
    expect([...test.scheduledDelays.values()]).toContain(ARRIVAL_CHANGE_NOTICE_MS);

    test.session.stop();

    expect(test.scheduled.size).toBe(0);
    expect(test.scheduledDelays.size).toBe(0);
  });
});
