import { useCallback, useEffect, useRef, useState } from 'react';
import { memberBlocksApi, type MemberBlock } from '../api/memberBlocks';

export type MemberBlocksState = {
  status: 'LOADING' | 'READY' | 'ERROR';
  blocks: MemberBlock[];
  target: MemberBlock | null;
  submitting: boolean;
  error: Error | null;
  successMessage: string | null;
};

const initialState: MemberBlocksState = {
  status: 'LOADING', blocks: [], target: null, submitting: false,
  error: null, successMessage: null,
};

export function createMemberBlocksSession(
  load: (signal: AbortSignal) => Promise<MemberBlock[]>,
  unblock: (id: number, signal: AbortSignal) => Promise<void>,
  onState: (state: MemberBlocksState) => void,
) {
  let state = initialState;
  let loadId = 0;
  let submitId = 0;
  let loadController: AbortController | null = null;
  let submitController: AbortController | null = null;
  let inFlight: Promise<boolean> | null = null;
  let stopped = false;
  const publish = (next: MemberBlocksState) => { state = next; if (!stopped) onState(next); };

  const reload = async () => {
    loadController?.abort();
    const controller = new AbortController();
    loadController = controller;
    const id = ++loadId;
    publish({ ...state, status: 'LOADING', error: null });
    try {
      const blocks = await load(controller.signal);
      if (!stopped && !controller.signal.aborted && id === loadId) {
        publish({ ...state, status: 'READY', blocks, error: null });
      }
    } catch (error) {
      if (!stopped && !controller.signal.aborted && id === loadId) {
        publish({ ...state, status: 'ERROR', error: error instanceof Error ? error : new Error('조회 실패') });
      }
    }
  };

  return {
    reload,
    open: (target: MemberBlock) => {
      submitId += 1; submitController?.abort(); submitController = null; inFlight = null;
      publish({ ...state, target, submitting: false, error: null });
    },
    close: () => {
      if (state.submitting) return;
      submitId += 1; submitController?.abort(); submitController = null; inFlight = null;
      publish({ ...state, target: null, error: null });
    },
    clearSuccess: () => publish({ ...state, successMessage: null }),
    submit: (): Promise<boolean> => {
      if (inFlight) return inFlight;
      if (stopped || !state.target) return Promise.resolve(false);
      const target = state.target;
      const controller = new AbortController();
      submitController = controller;
      const id = ++submitId;
      publish({ ...state, submitting: true, error: null });
      const operation = unblock(target.blockedMemberId, controller.signal).then(() => {
        if (stopped || controller.signal.aborted || id !== submitId) return false;
        publish({ ...state, status: 'READY',
          blocks: state.blocks.filter((item) => item.blockedMemberId !== target.blockedMemberId),
          target: null, submitting: false, error: null,
          successMessage: `${target.nickname}님의 차단을 해제했어요.`,
        });
        return true;
      }).catch((error: unknown) => {
        if (stopped || controller.signal.aborted || id !== submitId) return false;
        publish({ ...state, submitting: false,
          error: error instanceof Error ? error : new Error('차단 해제 실패') });
        return false;
      }).finally(() => {
        if (submitController === controller) submitController = null;
        if (inFlight === operation) inFlight = null;
      });
      inFlight = operation;
      return operation;
    },
    stop: () => {
      stopped = true; loadId += 1; submitId += 1;
      loadController?.abort(); submitController?.abort(); inFlight = null;
    },
  };
}

export function useMemberBlocks() {
  const [state, setState] = useState(initialState);
  const sessionRef = useRef<ReturnType<typeof createMemberBlocksSession> | null>(null);
  useEffect(() => {
    const session = createMemberBlocksSession(memberBlocksApi.getMine, memberBlocksApi.unblock, setState);
    sessionRef.current = session;
    void session.reload();
    return () => { sessionRef.current = null; session.stop(); };
  }, []);
  return {
    state,
    reload: useCallback(() => sessionRef.current?.reload(), []),
    open: useCallback((target: MemberBlock) => sessionRef.current?.open(target), []),
    close: useCallback(() => sessionRef.current?.close(), []),
    clearSuccess: useCallback(() => sessionRef.current?.clearSuccess(), []),
    submit: useCallback(() => sessionRef.current?.submit() ?? Promise.resolve(false), []),
  };
}
