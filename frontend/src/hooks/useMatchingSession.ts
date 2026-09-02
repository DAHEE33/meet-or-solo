import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiClientError } from '../api/apiClient';
import {
  matchingApi,
  type ActiveMatchProposal,
  type CurrentMatchGroup,
  type MatchPool,
  type MatchProposalAction,
  type MatchingRestriction,
} from '../api/matching';
import { connectMatchingWebSocket } from '../api/matchingWebSocket';
import { calculateServerOffsetMs } from '../utils/serverClock';

const ACTIVE_POLL_MS = 2_000;
const COOLDOWN_POLL_MS = 5_000;
const MAX_BACKOFF_MS = 30_000;

export type MatchingUiStatus =
  | 'LOADING'
  | 'IDLE'
  | 'WAITING'
  | 'LOCKED'
  | 'INITIAL_PROPOSAL'
  | 'INSUFFICIENT_MEMBERS_PROPOSAL'
  | 'RESPONSE_PENDING'
  | 'MATCHED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'COOLDOWN'
  | 'ERROR';

export type MatchingSnapshot = {
  pool: MatchPool | null;
  proposal: ActiveMatchProposal | null;
  group: CurrentMatchGroup | null;
  restriction: MatchingRestriction;
};

export type MatchingSessionState = {
  status: MatchingUiStatus;
  pool: MatchPool | null;
  proposal: ActiveMatchProposal | null;
  group: CurrentMatchGroup | null;
  restriction: MatchingRestriction | null;
  error: ApiClientError | Error | null;
};

export type RetryFormState = {
  sourcePoolId: number | null;
};

const INITIAL_STATE: MatchingSessionState = {
  status: 'LOADING',
  pool: null,
  proposal: null,
  group: null,
  restriction: null,
  error: null,
};

const INITIAL_RETRY_FORM_STATE: RetryFormState = {
  sourcePoolId: null,
};

/**
 * 이 세션에서 "진행 중"인 상태를 실제로 관측한 pool id를 기억한다.
 *
 * 종료 카드를 보여줄지 판단하는 기준이다. 세션 중 실시간으로 종료된 pool은 사용자가 종료 사유와
 * 다음 선택지를 최소 한 번은 봐야 하고, 새 mount에서 발견한 과거 terminal pool은 곧바로 신청
 * 화면으로 돌려보내야 한다(`[10-A 후속 2]` 종료 화면 고착 수정).
 *
 * `MATCHED`는 group이 실제로 있을 때만 관측으로 기록한다. group 없이 남은 `MATCHED` pool은 이미
 * 취소된 과거 이력이라, 이것까지 관측으로 세면 새 mount에서 종료 카드가 다시 뜬다.
 */
export function observedActivePoolId(
  snapshot: MatchingSnapshot,
  current: number | null,
): number | null {
  const { pool, group } = snapshot;
  if (!pool) return current;
  if (pool.status === 'WAITING' || pool.status === 'LOCKED' || pool.status === 'PROPOSED') {
    return pool.poolId;
  }
  if (pool.status === 'MATCHED' && group) {
    return pool.poolId;
  }
  return current;
}

export function deriveMatchingState(
  snapshot: MatchingSnapshot,
  sessionObservedPoolId: number | null = null,
): MatchingSessionState {
  const { pool, proposal, group, restriction } = snapshot;

  if (group) {
    return { status: 'MATCHED', pool, proposal, group, restriction, error: null };
  }
  if (proposal) {
    return {
      status:
        proposal.proposalType === 'INSUFFICIENT_MEMBERS_CONFIRMATION'
          ? 'INSUFFICIENT_MEMBERS_PROPOSAL'
          : 'INITIAL_PROPOSAL',
      pool,
      proposal,
      group,
      restriction,
      error: null,
    };
  }
  if (pool?.status === 'WAITING' || pool?.status === 'LOCKED') {
    return { status: pool.status, pool, proposal, group, restriction, error: null };
  }
  if (pool?.status === 'PROPOSED') {
    return {
      status: 'RESPONSE_PENDING',
      pool,
      proposal,
      group,
      restriction,
      error: null,
    };
  }
  if (restriction.completionLock.groupId !== null) {
    return { status: 'COMPLETED', pool, proposal, group, restriction, error: null };
  }
  if (restriction.cooldown.active) {
    const terminalStatus = pool?.status === 'MATCHED' ? 'CANCELLED'
      : (pool?.status === 'CANCELLED' || pool?.status === 'EXPIRED') ? pool.status
      : 'COOLDOWN';
    return { status: terminalStatus, pool, proposal, group, restriction, error: null };
  }
  if (pool?.status === 'MATCHED' || pool?.status === 'CANCELLED' || pool?.status === 'EXPIRED') {
    // 이 세션에서 진행 상태를 관측한 pool이 종료된 경우에만 종료 카드를 보여준다.
    // 60초 탐색 만료는 cooldown을 만들지 않으므로, 이 분기가 없으면 가장 흔한 매칭 실패에서
    // 종료 안내와 솔로 코스 전환이 화면에 아예 뜨지 않는다(docs/26 3장).
    if (sessionObservedPoolId !== null && sessionObservedPoolId === pool.poolId) {
      return {
        status: pool.status === 'MATCHED' ? 'CANCELLED' : pool.status,
        pool,
        proposal,
        group,
        restriction,
        error: null,
      };
    }
    // 새 mount에서 발견한 과거 terminal pool → 새로 신청 가능 (`[10-A 후속 2]`)
    return { status: 'IDLE', pool, proposal, group, restriction, error: null };
  }
  return { status: 'IDLE', pool, proposal, group, restriction, error: null };
}

export function pollingDelay(status: MatchingUiStatus, consecutiveErrors: number): number | null {
  if (status === 'ERROR') {
    return Math.min(ACTIVE_POLL_MS * 2 ** Math.max(0, consecutiveErrors - 1), MAX_BACKOFF_MS);
  }
  if (status === 'COOLDOWN') return COOLDOWN_POLL_MS;
  if (
    status === 'WAITING'
    || status === 'LOCKED'
    || status === 'INITIAL_PROPOSAL'
    || status === 'INSUFFICIENT_MEMBERS_PROPOSAL'
    || status === 'RESPONSE_PENDING'
  ) {
    return ACTIVE_POLL_MS;
  }
  return null;
}

export function retrySourceAfterRefresh(
  retrySourcePoolId: number | null,
  nextState: MatchingSessionState,
): number | null {
  if (retrySourcePoolId === null) return null;
  const sameTerminalPool =
    (nextState.status === 'CANCELLED'
      || nextState.status === 'EXPIRED'
      || nextState.status === 'COMPLETED')
    && nextState.pool?.poolId === retrySourcePoolId;
  return sameTerminalPool
    && !nextState.restriction?.cooldown.active
    && !nextState.restriction?.completionLock.active
      ? retrySourcePoolId
      : null;
}

export function canBeginRetry(state: MatchingSessionState, isSubmitting: boolean): boolean {
  const isTerminal = state.status === 'CANCELLED'
    || state.status === 'EXPIRED'
    || state.status === 'COMPLETED';
  return isTerminal
    && state.pool !== null
    && !state.restriction?.cooldown.active
    && !state.restriction?.completionLock.active
    && !isSubmitting;
}

export function stateAfterPoolEntry(
  previous: MatchingSessionState,
  pool: MatchPool,
): MatchingSessionState {
  return {
    ...previous,
    status: pool.status === 'LOCKED' ? 'LOCKED' : 'WAITING',
    pool,
    error: null,
  };
}

export function stateAfterPoolEntryFailure(
  previous: MatchingSessionState,
  error: ApiClientError | Error,
  retrySourcePoolId: number | null,
): MatchingSessionState {
  return {
    ...previous,
    status: retrySourcePoolId === null ? 'ERROR' : previous.status,
    error,
  };
}

export function useMatchingSession() {
  const [state, setState] = useState<MatchingSessionState>(INITIAL_STATE);
  const [retryForm, setRetryForm] = useState<RetryFormState>(INITIAL_RETRY_FORM_STATE);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isVisible, setIsVisible] = useState(() => document.visibilityState !== 'hidden');
  const [serverOffsetMs, setServerOffsetMs] = useState(0);
  const mountedRef = useRef(true);
  const inFlightRef = useRef<Promise<void> | null>(null);
  const queryAbortRef = useRef<AbortController | null>(null);
  const mutationAbortRef = useRef<AbortController | null>(null);
  const consecutiveErrorsRef = useRef(0);
  const retrySourcePoolIdRef = useRef<number | null>(null);
  /** 이 mount에서 진행 상태를 관측한 pool id. 새 mount면 null에서 다시 시작한다. */
  const observedActivePoolIdRef = useRef<number | null>(null);

  const updateRetrySourcePoolId = useCallback((sourcePoolId: number | null) => {
    retrySourcePoolIdRef.current = sourcePoolId;
    setRetryForm({ sourcePoolId });
  }, []);

  const refresh = useCallback((): Promise<void> => {
    if (inFlightRef.current) return inFlightRef.current;

    const controller = new AbortController();
    const requestStartedAt = Date.now();
    queryAbortRef.current = controller;
    const operation = Promise.all([
      matchingApi.getCurrentPool(controller.signal),
      matchingApi.getActiveProposal(controller.signal),
      matchingApi.getCurrentGroup(controller.signal),
      matchingApi.getRestrictions(controller.signal),
    ])
      .then(([pool, proposal, group, restriction]) => {
        if (!mountedRef.current || controller.signal.aborted) return;
        consecutiveErrorsRef.current = 0;
        const responseReceivedAt = Date.now();
        setServerOffsetMs(calculateServerOffsetMs(
          restriction.serverNow,
          requestStartedAt + (responseReceivedAt - requestStartedAt) / 2,
        ));
        const snapshot = { pool, proposal, group, restriction };
        observedActivePoolIdRef.current = observedActivePoolId(
          snapshot,
          observedActivePoolIdRef.current,
        );
        const nextState = deriveMatchingState(snapshot, observedActivePoolIdRef.current);
        const nextRetrySourcePoolId = retrySourceAfterRefresh(retrySourcePoolIdRef.current, nextState);
        if (nextRetrySourcePoolId !== retrySourcePoolIdRef.current) {
          updateRetrySourcePoolId(nextRetrySourcePoolId);
        }
        setState(nextState);
      })
      .catch((error: unknown) => {
        if (!mountedRef.current || controller.signal.aborted || isAbortError(error)) return;
        consecutiveErrorsRef.current += 1;
        setState((previous) => ({
          ...previous,
          status: 'ERROR',
          error: normalizeError(error),
        }));
      })
      .finally(() => {
        if (queryAbortRef.current === controller) queryAbortRef.current = null;
        if (inFlightRef.current === operation) inFlightRef.current = null;
      });
    inFlightRef.current = operation;
    return operation;
  }, [updateRetrySourcePoolId]);

  useEffect(() => {
    mountedRef.current = true;
    void refresh();
    return () => {
      mountedRef.current = false;
      queryAbortRef.current?.abort();
      mutationAbortRef.current?.abort();
    };
  }, [refresh]);

  useEffect(() => connectMatchingWebSocket({
    onConnected: () => {
      if (mountedRef.current) void refresh();
    },
    onStateChanged: () => {
      if (mountedRef.current) void refresh();
    },
  }), [refresh]);

  useEffect(() => {
    const handleVisibility = () => {
      const visible = document.visibilityState !== 'hidden';
      setIsVisible(visible);
      if (visible) void refresh();
    };
    document.addEventListener('visibilitychange', handleVisibility);
    return () => document.removeEventListener('visibilitychange', handleVisibility);
  }, [refresh]);

  useEffect(() => {
    if (!isVisible) return;
    if (state.status === 'ERROR' && isUserActionableError(state.error)) return;
    const pollingStatus = state.restriction?.cooldown.active
      || state.restriction?.completionLock.active
      ? 'COOLDOWN'
      : state.status;
    const delay = pollingDelay(pollingStatus, consecutiveErrorsRef.current);
    if (delay === null) return;
    const timer = window.setTimeout(() => void refresh(), delay);
    return () => window.clearTimeout(timer);
  }, [isVisible, refresh, state]);

  const beginRetry = useCallback(async () => {
    if (!canBeginRetry(state, isSubmitting)) return false;
    const sourcePoolId = state.pool?.poolId;
    if (sourcePoolId === undefined) return false;
    await refresh();
    if (!mountedRef.current) return false;
    updateRetrySourcePoolId(sourcePoolId);
    return true;
  }, [isSubmitting, refresh, state, updateRetrySourcePoolId]);

  const enterPool = useCallback(
    async (
      festivalId: number,
      preferredGroupSize: 2 | 3 | 4,
      allowMinimumTwo: boolean,
    ) => {
      if (isSubmitting
        || state.restriction?.cooldown.active
        || state.restriction?.completionLock.active) return false;
      setIsSubmitting(true);
      const controller = new AbortController();
      mutationAbortRef.current?.abort();
      mutationAbortRef.current = controller;
      try {
        const pool = await matchingApi.enterPool(
          { festivalId, preferredGroupSize, allowMinimumTwo, tags: [] },
          controller.signal,
        );
        if (mountedRef.current && !controller.signal.aborted) {
          updateRetrySourcePoolId(null);
          setState((previous) => stateAfterPoolEntry(previous, pool));
          void refresh();
          return true;
        }
      } catch (error) {
        if (!isAbortError(error) && mountedRef.current) {
          setState((previous) => stateAfterPoolEntryFailure(
            previous,
            normalizeError(error),
            retrySourcePoolIdRef.current,
          ));
        }
      } finally {
        if (mutationAbortRef.current === controller) mutationAbortRef.current = null;
        if (mountedRef.current) setIsSubmitting(false);
      }
      return false;
    },
    [
      isSubmitting,
      refresh,
      state.restriction?.completionLock.active,
      state.restriction?.cooldown.active,
      updateRetrySourcePoolId,
    ],
  );

  const respond = useCallback(
    async (action: MatchProposalAction) => {
      if (isSubmitting || !state.proposal) return;
      setIsSubmitting(true);
      const controller = new AbortController();
      mutationAbortRef.current?.abort();
      mutationAbortRef.current = controller;
      try {
        await matchingApi.respond(state.proposal.proposalId, action, controller.signal);
        if (mountedRef.current && !controller.signal.aborted) {
          setState((previous) => ({
            ...previous,
            status: 'RESPONSE_PENDING',
            proposal: null,
            error: null,
          }));
          void refresh();
        }
      } catch (error) {
        if (!isAbortError(error) && mountedRef.current) {
          setState((previous) => ({ ...previous, status: 'ERROR', error: normalizeError(error) }));
        }
      } finally {
        if (mutationAbortRef.current === controller) mutationAbortRef.current = null;
        if (mountedRef.current) setIsSubmitting(false);
      }
    },
    [isSubmitting, refresh, state.proposal],
  );

  const cancelSearch = useCallback(
    async () => {
      if (isSubmitting) return false;
      if (state.status !== 'WAITING' && state.status !== 'LOCKED') return false;
      setIsSubmitting(true);
      const controller = new AbortController();
      mutationAbortRef.current?.abort();
      mutationAbortRef.current = controller;
      try {
        await matchingApi.cancelPool(controller.signal);
        if (mountedRef.current && !controller.signal.aborted) {
          void refresh();
        }
        return true;
      } catch (error) {
        if (!isAbortError(error) && mountedRef.current) {
          if (error instanceof ApiClientError && error.status === 409) {
            void refresh();
          } else {
            setState((previous) => ({ ...previous, status: 'ERROR', error: normalizeError(error) }));
          }
        }
        return false;
      } finally {
        if (mutationAbortRef.current === controller) mutationAbortRef.current = null;
        if (mountedRef.current) setIsSubmitting(false);
      }
    },
    [isSubmitting, refresh, state.status],
  );

  return {
    state,
    isSubmitting,
    isRetryFormOpen: retryForm.sourcePoolId !== null,
    retrySourcePoolId: retryForm.sourcePoolId,
    refresh,
    beginRetry,
    enterPool,
    respond,
    cancelSearch,
    serverOffsetMs,
  };
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException
    ? error.name === 'AbortError'
    : error instanceof Error && error.name === 'AbortError';
}

function normalizeError(error: unknown): ApiClientError | Error {
  return error instanceof Error ? error : new Error('네트워크 요청에 실패했습니다.');
}

export function isUserActionableError(error: ApiClientError | Error | null): boolean {
  if (!(error instanceof ApiClientError)) return false;
  return (error.code === 'MATCHING_INVALID_REQUEST' && error.message.includes('체크인'))
    || error.code === 'MATCHING_MEETING_POINT_NOT_READY';
}
