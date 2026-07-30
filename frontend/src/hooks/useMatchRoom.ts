import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiClientError } from '../api/apiClient';
import {
  matchingApi,
  type ArrivalMinutesSelection,
  type CurrentMatchGroup,
  type MatchGroupEvent,
} from '../api/matching';
import { connectMatchingWebSocket } from '../api/matchingWebSocket';

export const MATCH_ROOM_FALLBACK_POLL_MS = 5_000;
export const ARRIVAL_CHANGE_NOTICE_MS = 3_000;

export type MatchRoomState = {
  status: 'LOADING' | 'READY' | 'EMPTY' | 'ERROR';
  group: CurrentMatchGroup | null;
  events: MatchGroupEvent[];
  error: ApiClientError | Error | null;
  eventsError: ApiClientError | Error | null;
  actionError: ApiClientError | Error | null;
  arrivalChangeNotice?: string | null;
  isSubmitting: boolean;
};

const INITIAL_STATE: MatchRoomState = {
  status: 'LOADING',
  group: null,
  events: [],
  error: null,
  eventsError: null,
  actionError: null,
  arrivalChangeNotice: null,
  isSubmitting: false,
};

type MatchRoomSessionDependencies = {
  loadCurrentGroup: (signal: AbortSignal) => Promise<CurrentMatchGroup | null>;
  loadCurrentGroupEvents: (
    signal: AbortSignal,
  ) => Promise<{ events: MatchGroupEvent[] } | null>;
  selectArrivalTime: (
    arrivalMinutes: ArrivalMinutesSelection,
    signal: AbortSignal,
  ) => Promise<CurrentMatchGroup>;
  arrive?: (signal: AbortSignal) => Promise<CurrentMatchGroup>;
  connect: typeof connectMatchingWebSocket;
  schedule: (callback: () => void, delay: number) => number;
  cancelSchedule: (timer: number) => void;
  onState: (state: MatchRoomState) => void;
};

export function createMatchRoomSession(dependencies: MatchRoomSessionDependencies) {
  let stopped = false;
  let connected = false;
  let inFlight: Promise<void> | null = null;
  let abortController: AbortController | null = null;
  let mutationAbortController: AbortController | null = null;
  let mutationInFlight: Promise<boolean> | null = null;
  let timer: number | null = null;
  let noticeTimer: number | null = null;
  let currentState = INITIAL_STATE;
  let generation = 0;
  let refreshQueued = false;

  const clearTimer = () => {
    if (timer !== null) dependencies.cancelSchedule(timer);
    timer = null;
  };

  const clearNoticeTimer = () => {
    if (noticeTimer !== null) dependencies.cancelSchedule(noticeTimer);
    noticeTimer = null;
  };

  const scheduleFallback = () => {
    clearTimer();
    if (stopped || connected || currentState.status === 'EMPTY') return;
    timer = dependencies.schedule(() => {
      timer = null;
      void refresh();
    }, MATCH_ROOM_FALLBACK_POLL_MS);
  };

  const publish = (state: MatchRoomState) => {
    currentState = state;
    dependencies.onState(state);
  };

  const refresh = (queueIfBusy = false): Promise<void> => {
    if (inFlight) {
      if (queueIfBusy) refreshQueued = true;
      return inFlight;
    }
    const controller = new AbortController();
    abortController = controller;
    const requestGeneration = ++generation;
    const operation = Promise.allSettled([
      dependencies.loadCurrentGroup(controller.signal),
      dependencies.loadCurrentGroupEvents(controller.signal),
    ])
      .then(([groupResult, eventsResult]) => {
        if (stopped || controller.signal.aborted || requestGeneration !== generation) return;
        if (groupResult.status === 'rejected') {
          if (isAbortError(groupResult.reason)) return;
          publish({
            ...currentState,
            status: 'ERROR',
            error: normalizeError(groupResult.reason),
            isSubmitting: false,
          });
          return;
        }
        const group = groupResult.value;
        if (!group) {
          publish({
            ...currentState,
            status: 'EMPTY',
            group: null,
            events: [],
            error: null,
            eventsError: null,
            isSubmitting: false,
          });
          return;
        }
        const eventsFailed = eventsResult.status === 'rejected'
          && !isAbortError(eventsResult.reason);
        const changedMember = findChangedOtherMember(currentState.group, group);
        if (changedMember) {
          clearNoticeTimer();
          noticeTimer = dependencies.schedule(() => {
            noticeTimer = null;
            if (!stopped) publish({ ...currentState, arrivalChangeNotice: null });
          }, ARRIVAL_CHANGE_NOTICE_MS);
        }
        publish({
          status: 'READY',
          group,
          events: eventsResult.status === 'fulfilled'
            ? (eventsResult.value?.events ?? [])
            : currentState.events,
          error: null,
          eventsError: eventsFailed ? normalizeError(eventsResult.reason) : null,
          actionError: null,
          arrivalChangeNotice: changedMember
            ? `${changedMember.nickname}님이 도착 시간을 변경하였어요.`
            : currentState.arrivalChangeNotice,
          isSubmitting: false,
        });
      })
      .finally(() => {
        if (abortController === controller) abortController = null;
        if (inFlight === operation) inFlight = null;
        if (refreshQueued && !stopped) {
          refreshQueued = false;
          void refresh();
          return;
        }
        scheduleFallback();
      });
    inFlight = operation;
    return operation;
  };

  const disconnect = dependencies.connect({
    onConnected: () => {
      if (stopped) return;
      connected = true;
      clearTimer();
      void refresh(true);
    },
    onDisconnected: () => {
      if (stopped) return;
      connected = false;
      scheduleFallback();
    },
    onStateChanged: () => {
      if (!stopped) void refresh(true);
    },
  });

  void refresh();

  const selectArrivalTime = (
    arrivalMinutes: ArrivalMinutesSelection,
  ): Promise<boolean> => {
    if (mutationInFlight || stopped || currentState.status !== 'READY') {
      return mutationInFlight ?? Promise.resolve(false);
    }
    const controller = new AbortController();
    mutationAbortController = controller;
    publish({ ...currentState, actionError: null, isSubmitting: true });
    const operation = dependencies.selectArrivalTime(arrivalMinutes, controller.signal)
      .then(async (group) => {
        if (stopped || controller.signal.aborted) return false;
        const mutationGeneration = ++generation;
        publish({
          ...currentState,
          group,
          error: null,
          actionError: null,
          isSubmitting: false,
        });
        await refreshEventsAfterMutation(controller, mutationGeneration);
        return true;
      })
      .catch((error: unknown) => {
        if (stopped || controller.signal.aborted || isAbortError(error)) return false;
        publish({
          ...currentState,
          actionError: normalizeError(error),
          isSubmitting: false,
        });
        return false;
      })
      .finally(() => {
        if (mutationAbortController === controller) mutationAbortController = null;
        if (mutationInFlight === operation) mutationInFlight = null;
      });
    mutationInFlight = operation;
    return operation;
  };

  const arrive = (): Promise<boolean> => {
    if (!dependencies.arrive || mutationInFlight || stopped || currentState.status !== 'READY') {
      return mutationInFlight ?? Promise.resolve(false);
    }
    const controller = new AbortController();
    mutationAbortController = controller;
    publish({ ...currentState, actionError: null, isSubmitting: true });
    const operation = dependencies.arrive(controller.signal)
      .then(async (group) => {
        if (stopped || controller.signal.aborted) return false;
        const mutationGeneration = ++generation;
        publish({
          ...currentState,
          group,
          error: null,
          actionError: null,
          isSubmitting: false,
        });
        await refreshEventsAfterMutation(controller, mutationGeneration);
        return true;
      })
      .catch((error: unknown) => {
        if (stopped || controller.signal.aborted || isAbortError(error)) return false;
        publish({ ...currentState, actionError: normalizeError(error), isSubmitting: false });
        return false;
      })
      .finally(() => {
        if (mutationAbortController === controller) mutationAbortController = null;
        if (mutationInFlight === operation) mutationInFlight = null;
      });
    mutationInFlight = operation;
    return operation;
  };

  const refreshEventsAfterMutation = async (
    controller: AbortController,
    mutationGeneration: number,
  ) => {
    try {
      const response = await dependencies.loadCurrentGroupEvents(controller.signal);
      if (stopped || controller.signal.aborted || mutationGeneration !== generation) return;
      publish({
        ...currentState,
        events: response?.events ?? [],
        eventsError: null,
      });
    } catch (error: unknown) {
      if (stopped || controller.signal.aborted || isAbortError(error)
          || mutationGeneration !== generation) return;
      publish({
        ...currentState,
        eventsError: normalizeError(error),
      });
    }
  };

  return {
    refresh,
    selectArrivalTime,
    arrive,
    stop: () => {
      stopped = true;
      clearTimer();
      clearNoticeTimer();
      abortController?.abort();
      mutationAbortController?.abort();
      disconnect();
    },
  };
}

export function useMatchRoom() {
  const [state, setState] = useState<MatchRoomState>(INITIAL_STATE);
  const sessionRef = useRef<ReturnType<typeof createMatchRoomSession> | null>(null);

  useEffect(() => {
    const session = createMatchRoomSession({
      loadCurrentGroup: (signal) => matchingApi.getCurrentGroup(signal),
      loadCurrentGroupEvents: (signal) => matchingApi.getCurrentGroupEvents(signal),
      selectArrivalTime: (arrivalMinutes, signal) =>
        matchingApi.selectArrivalTime(arrivalMinutes, signal),
      arrive: (signal) => matchingApi.arrive(signal),
      connect: connectMatchingWebSocket,
      schedule: (callback, delay) => window.setTimeout(callback, delay),
      cancelSchedule: (timer) => window.clearTimeout(timer),
      onState: setState,
    });
    sessionRef.current = session;
    return () => {
      sessionRef.current = null;
      session.stop();
    };
  }, []);

  const refresh = useCallback(
    () => sessionRef.current?.refresh() ?? Promise.resolve(),
    [],
  );

  const selectArrivalTime = useCallback(
    (arrivalMinutes: ArrivalMinutesSelection) =>
      sessionRef.current?.selectArrivalTime(arrivalMinutes) ?? Promise.resolve(false),
    [],
  );

  const arrive = useCallback(
    () => sessionRef.current?.arrive() ?? Promise.resolve(false),
    [],
  );

  return { state, refresh, selectArrivalTime, arrive };
}

function findChangedOtherMember(
  previous: CurrentMatchGroup | null,
  next: CurrentMatchGroup,
) {
  if (!previous || previous.groupId !== next.groupId || next.currentMemberId === undefined) {
    return null;
  }
  return next.members.find((member) => {
    if (member.memberId === next.currentMemberId) return false;
    const before = previous.members.find((candidate) => candidate.memberId === member.memberId);
    return before
      && (before.arrivalMinutes !== member.arrivalMinutes
        || before.arrivalTimeSelectedAt !== member.arrivalTimeSelectedAt);
  }) ?? null;
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError';
}

function normalizeError(error: unknown): ApiClientError | Error {
  return error instanceof Error ? error : new Error('매칭방 정보를 불러오지 못했습니다.');
}
