import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiClientError } from '../api/apiClient';
import {
  matchingApi,
  type ArrivalMinutesSelection,
  type CurrentMatchGroup,
  type MatchGroupEvent,
  type MatchCancellationReason,
  type MatchCancellationResult,
} from '../api/matching';
import { NOTICE_DISMISS_MS } from '../components/common/TopNotice';
import { connectMatchingWebSocket } from '../api/matchingWebSocket';
import { subscribeMatchingNotifications } from '../api/matchingNotificationHub';
import { getCurrentPosition } from '../utils/geolocation';
import type { MatchRoomAction } from '../utils/matchRoomError';

export const MATCH_ROOM_FALLBACK_POLL_MS = 5_000;

/**
 * 도착 시간 변경 안내가 화면에 머무는 시간.
 *
 * <p>다른 알림과 같은 값을 쓴다({@link NOTICE_DISMISS_MS}). 예전에는 이것만 3초여서, 같은
 * 화면의 다른 안내보다 먼저 사라졌다.
 */
export const ARRIVAL_CHANGE_NOTICE_MS = NOTICE_DISMISS_MS;

/**
 * 이 session이 거는 timer의 종류.
 *
 * <p>둘의 지연 시간이 같아졌기 때문에 필요하다. 예전에는 폴링 5초 / 안내 3초라 테스트가 지연
 * 시간만 보고 구분할 수 있었는데, 안내를 5초로 맞추면서 그 방법이 통하지 않는다.
 */
export type MatchRoomTimer = 'FALLBACK_POLL' | 'ARRIVAL_CHANGE_NOTICE';

export type MatchRoomState = {
  status: 'LOADING' | 'READY' | 'EMPTY' | 'ERROR';
  group: CurrentMatchGroup | null;
  events: MatchGroupEvent[];
  error: ApiClientError | Error | null;
  eventsError: ApiClientError | Error | null;
  actionError: ApiClientError | Error | null;
  /**
   * 어떤 버튼이 실패했는지. 여러 버튼이 `actionError` 한 자리를 함께 쓰므로 화면이 구분해야
   * "도착 예정 시간을 저장하지 못했어요"가 도착 인증 실패에 뜨는 일이 없다(docs/32 3.1).
   *
   * 선택 필드로 둔 이유는 `arrivalChangeNotice`와 같다 — 화면 조립 테스트가 상태를 직접
   * 만들어 쓰는데, 오류와 무관한 경우까지 이 값을 적게 하면 잡음만 늘어난다.
   */
  actionErrorSource?: MatchRoomAction | null;
  arrivalChangeNotice?: string | null;
  isSubmitting: boolean;
  cancellationResult?: MatchCancellationResult | null;
  terminationNotice?: string | null;
};

const INITIAL_STATE: MatchRoomState = {
  status: 'LOADING',
  group: null,
  events: [],
  error: null,
  eventsError: null,
  actionError: null,
  actionErrorSource: null,
  arrivalChangeNotice: null,
  isSubmitting: false,
  cancellationResult: null,
  terminationNotice: null,
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
  cancelParticipation?: (
    reason: MatchCancellationReason,
    signal: AbortSignal,
  ) => Promise<MatchCancellationResult>;
  leave?: (signal: AbortSignal) => Promise<MatchCancellationResult>;
  connect: typeof connectMatchingWebSocket;
  schedule: (callback: () => void, delay: number, purpose: MatchRoomTimer) => number;
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
  let completionSignalReceived = false;

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
    }, MATCH_ROOM_FALLBACK_POLL_MS, 'FALLBACK_POLL');
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
          const hadCurrentGroup = currentState.group !== null;
          publish({
            ...currentState,
            status: 'EMPTY',
            group: null,
            events: [],
            error: null,
            eventsError: null,
            terminationNotice: completionSignalReceived
              ? '만남이 끝났어요. 매너온도가 올랐어요.'
              : hadCurrentGroup
              // 알림 문구(notificationMessages의 MATCH_CANCELLED)와 같은 어휘를 쓴다.
              ? '남은 인원으로 만남을 이어갈 수 없어 만남이 종료됐어요.'
              : currentState.terminationNotice,
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
          }, ARRIVAL_CHANGE_NOTICE_MS, 'ARRIVAL_CHANGE_NOTICE');
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
          actionErrorSource: null,
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
    onStateChanged: (notification) => {
      if (notification.reason === 'MATCH_COMPLETED') completionSignalReceived = true;
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
    publish({ ...currentState, actionError: null, actionErrorSource: null, isSubmitting: true });
    const operation = dependencies.selectArrivalTime(arrivalMinutes, controller.signal)
      .then(async (group) => {
        if (stopped || controller.signal.aborted) return false;
        const mutationGeneration = ++generation;
        publish({
          ...currentState,
          group,
          error: null,
          actionError: null,
          actionErrorSource: null,
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
          actionErrorSource: 'ARRIVAL_TIME',
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
    publish({ ...currentState, actionError: null, actionErrorSource: null, isSubmitting: true });
    const operation = dependencies.arrive(controller.signal)
      .then(async (group) => {
        if (stopped || controller.signal.aborted) return false;
        if (group.status === 'COMPLETED') {
          ++generation;
          clearTimer();
          publish({
            ...currentState,
            status: 'EMPTY',
            group: null,
            events: [],
            error: null,
            actionError: null,
            actionErrorSource: null,
            // 도착 요청과 만남 종료가 겹친 경우다. 전원 도착만으로는 완료되지 않으므로
            // 여기 도달하는 것은 만남 시간이 끝나 서버가 방을 닫은 뒤다(docs/19 4.11.2).
            terminationNotice: '만남이 끝났어요. 매너온도가 올랐어요.',
            isSubmitting: false,
          });
          return true;
        }
        const mutationGeneration = ++generation;
        publish({
          ...currentState,
          group,
          error: null,
          actionError: null,
          actionErrorSource: null,
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
          actionErrorSource: 'ARRIVE',
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

  const cancelParticipation = (reason: MatchCancellationReason): Promise<boolean> => {
    if (!dependencies.cancelParticipation || mutationInFlight || stopped
        || currentState.status !== 'READY') {
      return mutationInFlight ?? Promise.resolve(false);
    }
    const controller = new AbortController();
    mutationAbortController = controller;
    publish({ ...currentState, actionError: null, actionErrorSource: null, isSubmitting: true });
    const operation = dependencies.cancelParticipation(reason, controller.signal)
      .then((result) => {
        if (stopped || controller.signal.aborted) return false;
        ++generation;
        publish({
          ...currentState,
          status: 'EMPTY',
          group: null,
          events: [],
          actionError: null,
          actionErrorSource: null,
          cancellationResult: result,
          terminationNotice: result.groupContinues
            ? '참여 취소가 완료됐어요. 남은 멤버는 만남을 계속해요.'
            : '참여 취소가 완료되어 그룹이 종료됐어요.',
          isSubmitting: false,
        });
        return true;
      })
      .catch((error: unknown) => {
        if (stopped || controller.signal.aborted || isAbortError(error)) return false;
        publish({
          ...currentState,
          actionError: normalizeError(error),
          actionErrorSource: 'CANCEL',
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

  /**
   * 도착한 사람이 먼저 나간다(docs/19 4.11.3). 페널티가 없고, 만남이 성립했다면 매너온도 보상도
   * 그대로 받는다. 취소와 달리 사유를 받지 않는다.
   */
  const leave = (): Promise<boolean> => {
    if (!dependencies.leave || mutationInFlight || stopped || currentState.status !== 'READY') {
      return mutationInFlight ?? Promise.resolve(false);
    }
    const controller = new AbortController();
    mutationAbortController = controller;
    publish({ ...currentState, actionError: null, actionErrorSource: null, isSubmitting: true });
    const operation = dependencies.leave(controller.signal)
      .then((result) => {
        if (stopped || controller.signal.aborted) return false;
        ++generation;
        clearTimer();
        publish({
          ...currentState,
          status: 'EMPTY',
          group: null,
          events: [],
          actionError: null,
          actionErrorSource: null,
          cancellationResult: result,
          // 이미 만남이 성립했던 방은 마지막 인원이 나가도 groupContinues가 true로 온다
          // (MatchGroupContinuationPolicy가 도착 이력으로 판정하기 때문에 취소로 보지 않는다).
          // 그래서 "남은 멤버가 계속한다"는 문구는 실제로 남은 인원이 있을 때만 맞는다.
          terminationNotice: result.groupContinues
            ? (result.currentMemberCount > 0
              ? '먼저 나왔어요. 남은 멤버는 만남을 계속해요.'
              : '먼저 나왔어요. 마지막 인원이라 만남이 끝났어요.')
            : '먼저 나와 만남이 종료됐어요.',
          isSubmitting: false,
        });
        return true;
      })
      .catch((error: unknown) => {
        if (stopped || controller.signal.aborted || isAbortError(error)) return false;
        publish({
          ...currentState,
          actionError: normalizeError(error),
          actionErrorSource: 'LEAVE',
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
    cancelParticipation,
    leave,
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
      // 도착 인증에는 현재 좌표가 필요하다. 서버가 만남 장소와의 거리를 재고 좌표는 버린다.
      arrive: async (signal) => {
        const position = await getCurrentPosition();
        return matchingApi.arrive(
          { latitude: position.latitude, longitude: position.longitude },
          signal,
        );
      },
      cancelParticipation: (reason, signal) =>
        matchingApi.cancelParticipation(reason, signal),
      leave: (signal) => matchingApi.leave(signal),
      // 소켓은 허브가 하나만 유지한다. 여기서 새로 연결하면 알림 센터와 소켓이 2개가 된다.
      connect: subscribeMatchingNotifications,
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

  const cancelParticipation = useCallback(
    (reason: MatchCancellationReason) =>
      sessionRef.current?.cancelParticipation(reason) ?? Promise.resolve(false),
    [],
  );

  const leave = useCallback(
    () => sessionRef.current?.leave() ?? Promise.resolve(false),
    [],
  );

  return { state, refresh, selectArrivalTime, arrive, cancelParticipation, leave };
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
