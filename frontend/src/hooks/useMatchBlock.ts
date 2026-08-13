import { useCallback, useEffect, useRef, useState } from 'react';
import { matchingApi, type MatchBlockResponse } from '../api/matching';

export type MatchBlockTarget = {
  memberId: number;
  nickname: string;
};

export type MatchBlockState = {
  target: MatchBlockTarget | null;
  open: boolean;
  submitting: boolean;
  error: Error | null;
  successMessage: string | null;
};

const INITIAL_STATE: MatchBlockState = {
  target: null,
  open: false,
  submitting: false,
  error: null,
  successMessage: null,
};

type SubmitBlock = (
  groupId: number,
  request: { blockedMemberId: number },
  signal: AbortSignal,
) => Promise<MatchBlockResponse>;

export function createMatchBlockSession(
  submitBlock: SubmitBlock,
  onState: (state: MatchBlockState) => void,
) {
  let state = INITIAL_STATE;
  let requestId = 0;
  let inFlight: Promise<boolean> | null = null;
  let controller: AbortController | null = null;
  let stopped = false;

  const publish = (next: MatchBlockState) => {
    state = next;
    if (!stopped) onState(next);
  };

  const cancelRequest = () => {
    requestId += 1;
    controller?.abort();
    controller = null;
    inFlight = null;
  };

  return {
    open: (target: MatchBlockTarget) => {
      cancelRequest();
      publish({ ...INITIAL_STATE, target, open: true });
    },
    close: () => {
      cancelRequest();
      publish({ ...INITIAL_STATE, successMessage: state.successMessage });
    },
    clearSuccess: () => publish({ ...state, successMessage: null }),
    submit: (groupId: number): Promise<boolean> => {
      if (inFlight) return inFlight;
      if (stopped || !state.open || !state.target) return Promise.resolve(false);

      const target = state.target;
      const currentRequestId = ++requestId;
      const requestController = new AbortController();
      controller = requestController;
      publish({ ...state, submitting: true, error: null });

      const operation = submitBlock(
        groupId,
        { blockedMemberId: target.memberId },
        requestController.signal,
      ).then(() => {
        if (stopped || requestController.signal.aborted || currentRequestId !== requestId) {
          return false;
        }
        publish({
          ...INITIAL_STATE,
          successMessage: '회원을 차단했어요. 앞으로 서로 매칭되지 않아요.',
        });
        return true;
      }).catch((error: unknown) => {
        if (stopped || requestController.signal.aborted || currentRequestId !== requestId) {
          return false;
        }
        publish({
          ...state,
          submitting: false,
          error: error instanceof Error ? error : new Error('회원을 차단하지 못했습니다.'),
        });
        return false;
      }).finally(() => {
        if (controller === requestController) controller = null;
        if (inFlight === operation) inFlight = null;
      });
      inFlight = operation;
      return operation;
    },
    stop: () => {
      stopped = true;
      cancelRequest();
    },
  };
}

export function useMatchBlock() {
  const [state, setState] = useState<MatchBlockState>(INITIAL_STATE);
  const sessionRef = useRef<ReturnType<typeof createMatchBlockSession> | null>(null);

  useEffect(() => {
    const session = createMatchBlockSession(
      (groupId, request, signal) => matchingApi.submitBlock(groupId, request, signal),
      setState,
    );
    sessionRef.current = session;
    return () => {
      sessionRef.current = null;
      session.stop();
    };
  }, []);

  return {
    state,
    open: useCallback((target: MatchBlockTarget) => sessionRef.current?.open(target), []),
    close: useCallback(() => sessionRef.current?.close(), []),
    clearSuccess: useCallback(() => sessionRef.current?.clearSuccess(), []),
    submit: useCallback((groupId: number) =>
      sessionRef.current?.submit(groupId) ?? Promise.resolve(false), []),
  };
}
