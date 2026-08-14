import { useCallback, useEffect, useRef, useState } from 'react';
import {
  matchingApi,
  type MatchReportReasonCode,
  type MatchReportResponse,
} from '../api/matching';

export type MatchReportTarget = {
  memberId: number;
  nickname: string;
};

export type MatchReportState = {
  target: MatchReportTarget | null;
  reasonCode: MatchReportReasonCode | null;
  step: 'CLOSED' | 'REASON' | 'CONFIRM';
  submitting: boolean;
  error: Error | null;
  successMessage: string | null;
};

const INITIAL_STATE: MatchReportState = {
  target: null,
  reasonCode: null,
  step: 'CLOSED',
  submitting: false,
  error: null,
  successMessage: null,
};

type SubmitReport = (
  groupId: number,
  request: { reportedMemberId: number; reasonCode: MatchReportReasonCode },
  signal: AbortSignal,
) => Promise<MatchReportResponse>;

export function createMatchReportSession(
  submitReport: SubmitReport,
  onState: (state: MatchReportState) => void,
) {
  let state = INITIAL_STATE;
  let requestId = 0;
  let inFlight: Promise<boolean> | null = null;
  let controller: AbortController | null = null;
  let stopped = false;

  const publish = (next: MatchReportState) => {
    state = next;
    if (!stopped) onState(next);
  };

  const close = () => {
    requestId += 1;
    controller?.abort();
    controller = null;
    inFlight = null;
    publish({ ...INITIAL_STATE, successMessage: state.successMessage });
  };

  return {
    open: (target: MatchReportTarget) => {
      requestId += 1;
      controller?.abort();
      controller = null;
      inFlight = null;
      publish({ ...INITIAL_STATE, target, step: 'REASON' });
    },
    selectReason: (reasonCode: MatchReportReasonCode) => {
      if (state.step !== 'REASON' || state.submitting) return;
      publish({ ...state, reasonCode, error: null });
    },
    confirm: () => {
      if (state.step !== 'REASON' || !state.reasonCode || state.submitting) return;
      publish({ ...state, step: 'CONFIRM', error: null });
    },
    back: () => {
      if (state.step === 'CONFIRM' && !state.submitting) {
        publish({ ...state, step: 'REASON', error: null });
      }
    },
    close,
    clearSuccess: () => publish({ ...state, successMessage: null }),
    submit: (groupId: number): Promise<boolean> => {
      if (inFlight) return inFlight;
      if (stopped || state.step !== 'CONFIRM' || !state.target || !state.reasonCode) {
        return Promise.resolve(false);
      }
      const target = state.target;
      const reasonCode = state.reasonCode;
      const currentRequestId = ++requestId;
      controller = new AbortController();
      const requestController = controller;
      publish({ ...state, submitting: true, error: null });
      const operation = submitReport(
        groupId,
        { reportedMemberId: target.memberId, reasonCode },
        requestController.signal,
      ).then(() => {
        if (stopped || requestController.signal.aborted || currentRequestId !== requestId) {
          return false;
        }
        publish({
          ...INITIAL_STATE,
          successMessage: `${target.nickname}님에 대한 신고가 접수됐어요.`,
        });
        return true;
      }).catch((error: unknown) => {
        if (stopped || requestController.signal.aborted || currentRequestId !== requestId) {
          return false;
        }
        publish({
          ...state,
          submitting: false,
          error: error instanceof Error ? error : new Error('신고를 접수하지 못했습니다.'),
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
      requestId += 1;
      controller?.abort();
      controller = null;
      inFlight = null;
    },
  };
}

export function useMatchReport() {
  const [state, setState] = useState<MatchReportState>(INITIAL_STATE);
  const sessionRef = useRef<ReturnType<typeof createMatchReportSession> | null>(null);

  useEffect(() => {
    const session = createMatchReportSession(
      (groupId, request, signal) => matchingApi.submitReport(groupId, request, signal),
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
    open: useCallback((target: MatchReportTarget) => sessionRef.current?.open(target), []),
    selectReason: useCallback((reason: MatchReportReasonCode) =>
      sessionRef.current?.selectReason(reason), []),
    confirm: useCallback(() => sessionRef.current?.confirm(), []),
    back: useCallback(() => sessionRef.current?.back(), []),
    close: useCallback(() => sessionRef.current?.close(), []),
    clearSuccess: useCallback(() => sessionRef.current?.clearSuccess(), []),
    submit: useCallback((groupId: number) =>
      sessionRef.current?.submit(groupId) ?? Promise.resolve(false), []),
  };
}
