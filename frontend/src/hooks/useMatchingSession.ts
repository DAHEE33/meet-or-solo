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

const ACTIVE_POLL_MS = 2_000;
const COOLDOWN_POLL_MS = 5_000;
const MAX_BACKOFF_MS = 30_000;

export type MatchingUiStatus =
  | 'IDLE'
  | 'WAITING'
  | 'LOCKED'
  | 'INITIAL_PROPOSAL'
  | 'INSUFFICIENT_MEMBERS_PROPOSAL'
  | 'RESPONSE_PENDING'
  | 'MATCHED'
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

const INITIAL_STATE: MatchingSessionState = {
  status: 'IDLE',
  pool: null,
  proposal: null,
  group: null,
  restriction: null,
  error: null,
};

export function deriveMatchingState(snapshot: MatchingSnapshot): MatchingSessionState {
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
  if (pool?.status === 'PROPOSED' || pool?.status === 'MATCHED') {
    return {
      status: 'RESPONSE_PENDING',
      pool,
      proposal,
      group,
      restriction,
      error: null,
    };
  }
  if (pool?.status === 'CANCELLED' || pool?.status === 'EXPIRED') {
    return { status: pool.status, pool, proposal, group, restriction, error: null };
  }
  if (restriction.cooldown.active) {
    return { status: 'COOLDOWN', pool, proposal, group, restriction, error: null };
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

export function useMatchingSession() {
  const [state, setState] = useState<MatchingSessionState>(INITIAL_STATE);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isVisible, setIsVisible] = useState(() => document.visibilityState !== 'hidden');
  const mountedRef = useRef(true);
  const inFlightRef = useRef<Promise<void> | null>(null);
  const queryAbortRef = useRef<AbortController | null>(null);
  const mutationAbortRef = useRef<AbortController | null>(null);
  const consecutiveErrorsRef = useRef(0);

  const refresh = useCallback((): Promise<void> => {
    if (inFlightRef.current) return inFlightRef.current;

    const controller = new AbortController();
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
        setState(deriveMatchingState({ pool, proposal, group, restriction }));
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
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    void refresh();
    return () => {
      mountedRef.current = false;
      queryAbortRef.current?.abort();
      mutationAbortRef.current?.abort();
    };
  }, [refresh]);

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
    const pollingStatus = state.restriction?.cooldown.active ? 'COOLDOWN' : state.status;
    const delay = pollingDelay(pollingStatus, consecutiveErrorsRef.current);
    if (delay === null) return;
    const timer = window.setTimeout(() => void refresh(), delay);
    return () => window.clearTimeout(timer);
  }, [isVisible, refresh, state]);

  const enterPool = useCallback(
    async (
      festivalId: number,
      preferredGroupSize: 2 | 3 | 4,
      allowMinimumTwo: boolean,
    ) => {
      if (isSubmitting) return;
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
          setState((previous) => ({
            ...previous,
            status: pool.status === 'LOCKED' ? 'LOCKED' : 'WAITING',
            pool,
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
    [isSubmitting, refresh],
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

  return { state, isSubmitting, refresh, enterPool, respond };
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException
    ? error.name === 'AbortError'
    : error instanceof Error && error.name === 'AbortError';
}

function normalizeError(error: unknown): ApiClientError | Error {
  return error instanceof Error ? error : new Error('네트워크 요청에 실패했습니다.');
}
