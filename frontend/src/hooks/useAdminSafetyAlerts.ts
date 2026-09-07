import { useCallback, useEffect, useRef, useState } from 'react';
import {
  adminSafetyAlertsApi,
  type AdminSafetyAlert,
  type AdminSafetyAlertPage,
  type AdminSafetyAlertStatus,
} from '../api/adminSafetyAlerts';

export type AdminSafetyAlertsState = {
  status: 'LOADING' | 'READY' | 'ERROR';
  alerts: AdminSafetyAlert[];
  filter: AdminSafetyAlertStatus | '';
  pageIndex: number;
  hasNext: boolean;
  openCount: number;
  acknowledgingId: number | null;
  actionError: Error | null;
  successMessage: string | null;
};

export const INITIAL_ADMIN_SAFETY_ALERTS_STATE: AdminSafetyAlertsState = {
  status: 'LOADING',
  alerts: [],
  filter: 'OPEN',
  pageIndex: 0,
  hasNext: false,
  openCount: 0,
  acknowledgingId: null,
  actionError: null,
  successMessage: null,
};

type Dependencies = {
  list: (
    status: AdminSafetyAlertStatus | '',
    cursor: string | null,
    size: number,
    signal: AbortSignal,
  ) => Promise<AdminSafetyAlertPage>;
  acknowledge: (alertId: number, signal: AbortSignal) => Promise<AdminSafetyAlert>;
};

export function createAdminSafetyAlertsSession(
  dependencies: Dependencies,
  onState: (state: AdminSafetyAlertsState) => void,
) {
  let state = INITIAL_ADMIN_SAFETY_ALERTS_STATE;
  let cursors: Array<string | null> = [null];
  let nextCursor: string | null = null;
  let listRequestId = 0;
  let actionRequestId = 0;
  let listController: AbortController | null = null;
  let actionController: AbortController | null = null;
  let inFlightAction: Promise<boolean> | null = null;
  let stopped = false;
  const publish = (next: AdminSafetyAlertsState) => {
    state = next;
    if (!stopped) onState(next);
  };

  const load = async (pageIndex = state.pageIndex) => {
    listController?.abort();
    const controller = new AbortController();
    listController = controller;
    const requestId = ++listRequestId;
    publish({ ...state, status: 'LOADING' });
    try {
      const page = await dependencies.list(
        state.filter, cursors[pageIndex] ?? null, 20, controller.signal);
      // filter를 바꾸는 사이 도착한 오래된 응답은 버린다.
      if (stopped || controller.signal.aborted || requestId !== listRequestId) return;
      nextCursor = page.pagination.nextCursor;
      publish({
        ...state,
        status: 'READY',
        alerts: page.alerts,
        pageIndex,
        hasNext: page.pagination.hasNext,
        openCount: page.openCount,
      });
    } catch {
      if (stopped || controller.signal.aborted || requestId !== listRequestId) return;
      publish({ ...state, status: 'ERROR' });
    }
  };

  return {
    load,
    applyFilter: (filter: AdminSafetyAlertStatus | '') => {
      cursors = [null];
      nextCursor = null;
      publish({ ...state, filter, pageIndex: 0, hasNext: false, actionError: null });
      return load(0);
    },
    next: () => {
      if (!state.hasNext || !nextCursor) return Promise.resolve();
      const index = state.pageIndex + 1;
      cursors = [...cursors.slice(0, index), nextCursor];
      return load(index);
    },
    previous: () =>
      state.pageIndex > 0 ? load(state.pageIndex - 1) : Promise.resolve(),
    acknowledge: (alertId: number): Promise<boolean> => {
      // 이중 제출 방지. 실패 전 목록 snapshot은 그대로 유지한다.
      if (inFlightAction) return inFlightAction;
      if (stopped) return Promise.resolve(false);
      const controller = new AbortController();
      actionController = controller;
      const requestId = ++actionRequestId;
      publish({ ...state, acknowledgingId: alertId, actionError: null });
      const operation = dependencies.acknowledge(alertId, controller.signal)
        .then((updated) => {
          if (stopped || controller.signal.aborted || requestId !== actionRequestId) return false;
          const acknowledgedNow = state.alerts.some(
            (alert) => alert.alertId === alertId && alert.status === 'OPEN');
          // 미확인 목록은 처리해야 할 큐이므로 확인한 항목을 즉시 제거해 badge와 어긋나지
          // 않게 한다. 다른 filter에서는 결과를 볼 수 있도록 제자리에서 갱신한다.
          const removeFromList = state.filter === 'OPEN' && updated.status !== 'OPEN';
          publish({
            ...state,
            alerts: removeFromList
              ? state.alerts.filter((alert) => alert.alertId !== alertId)
              : state.alerts.map((alert) => alert.alertId === alertId ? updated : alert),
            openCount: acknowledgedNow ? Math.max(0, state.openCount - 1) : state.openCount,
            acknowledgingId: null,
            actionError: null,
            successMessage: '알림을 확인 처리했습니다.',
          });
          return true;
        })
        .catch((error: unknown) => {
          if (stopped || controller.signal.aborted || requestId !== actionRequestId) return false;
          publish({
            ...state,
            acknowledgingId: null,
            actionError: error instanceof Error ? error : new Error('알림 확인 실패'),
          });
          return false;
        })
        .finally(() => {
          if (actionController === controller) actionController = null;
          if (inFlightAction === operation) inFlightAction = null;
        });
      inFlightAction = operation;
      return operation;
    },
    clearSuccess: () => publish({ ...state, successMessage: null }),
    stop: () => {
      stopped = true;
      listRequestId += 1;
      actionRequestId += 1;
      listController?.abort();
      actionController?.abort();
      inFlightAction = null;
    },
  };
}

export function useAdminSafetyAlerts() {
  const [state, setState] = useState(INITIAL_ADMIN_SAFETY_ALERTS_STATE);
  const sessionRef = useRef<ReturnType<typeof createAdminSafetyAlertsSession> | null>(null);
  useEffect(() => {
    const session = createAdminSafetyAlertsSession(adminSafetyAlertsApi, setState);
    sessionRef.current = session;
    void session.load();
    return () => {
      sessionRef.current = null;
      session.stop();
    };
  }, []);
  return {
    state,
    reload: useCallback(() => sessionRef.current?.load(), []),
    applyFilter: useCallback(
      (filter: AdminSafetyAlertStatus | '') => sessionRef.current?.applyFilter(filter), []),
    next: useCallback(() => sessionRef.current?.next(), []),
    previous: useCallback(() => sessionRef.current?.previous(), []),
    acknowledge: useCallback(
      (alertId: number) => sessionRef.current?.acknowledge(alertId) ?? Promise.resolve(false), []),
    clearSuccess: useCallback(() => sessionRef.current?.clearSuccess(), []),
  };
}
