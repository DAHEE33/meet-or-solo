import { useCallback, useEffect, useRef, useState } from 'react';
import {
  adminReportsApi,
  type AdminReportDetail,
  type AdminReportFilters,
  type AdminReportListItem,
  type AdminReportPage,
  type AdminReportTargetStatus,
} from '../api/adminReports';

export type AdminReportsState = {
  status: 'LOADING' | 'READY' | 'ERROR';
  items: AdminReportListItem[];
  filters: AdminReportFilters;
  pageIndex: number;
  hasNext: boolean;
  detail: AdminReportDetail | null;
  selectedReportId: number | null;
  detailLoading: boolean;
  detailError: Error | null;
  targetStatus: AdminReportTargetStatus | null;
  submitting: boolean;
  actionError: Error | null;
  successMessage: string | null;
};

export const EMPTY_ADMIN_REPORT_FILTERS: AdminReportFilters = {
  status: '', reason: '', createdFrom: '', createdTo: '',
};

export const INITIAL_ADMIN_REPORTS_STATE: AdminReportsState = {
  status: 'LOADING', items: [], filters: EMPTY_ADMIN_REPORT_FILTERS,
  pageIndex: 0, hasNext: false, detail: null, selectedReportId: null, detailLoading: false,
  detailError: null, targetStatus: null, submitting: false,
  actionError: null, successMessage: null,
};

type Dependencies = {
  list: (filters: AdminReportFilters, cursor: string | null, size: number, signal: AbortSignal) => Promise<AdminReportPage>;
  detail: (reportId: number, signal: AbortSignal) => Promise<AdminReportDetail>;
  changeStatus: (reportId: number, target: AdminReportTargetStatus, signal: AbortSignal) => Promise<AdminReportDetail>;
};

export function createAdminReportsSession(
  dependencies: Dependencies,
  onState: (state: AdminReportsState) => void,
) {
  let state = INITIAL_ADMIN_REPORTS_STATE;
  let cursors: Array<string | null> = [null];
  let nextCursor: string | null = null;
  let listRequestId = 0;
  let detailRequestId = 0;
  let actionRequestId = 0;
  let listController: AbortController | null = null;
  let detailController: AbortController | null = null;
  let actionController: AbortController | null = null;
  let inFlightAction: Promise<boolean> | null = null;
  let stopped = false;
  const publish = (next: AdminReportsState) => { state = next; if (!stopped) onState(next); };

  const load = async (pageIndex = state.pageIndex) => {
    listController?.abort();
    const controller = new AbortController();
    listController = controller;
    const requestId = ++listRequestId;
    publish({ ...state, status: 'LOADING' });
    try {
      const page = await dependencies.list(state.filters, cursors[pageIndex] ?? null, 20, controller.signal);
      if (stopped || controller.signal.aborted || requestId !== listRequestId) return;
      nextCursor = page.pagination.nextCursor;
      publish({ ...state, status: 'READY', items: page.items, pageIndex,
        hasNext: page.pagination.hasNext });
    } catch (error) {
      if (stopped || controller.signal.aborted || requestId !== listRequestId) return;
      publish({ ...state, status: 'ERROR' });
    }
  };

  return {
    load,
    applyFilters: (filters: AdminReportFilters) => {
      cursors = [null]; nextCursor = null;
      publish({ ...state, filters, pageIndex: 0, hasNext: false, detail: null, selectedReportId: null,
        targetStatus: null, actionError: null });
      return load(0);
    },
    next: () => {
      if (!state.hasNext || !nextCursor) return Promise.resolve();
      const index = state.pageIndex + 1;
      cursors = [...cursors.slice(0, index), nextCursor];
      return load(index);
    },
    previous: () => state.pageIndex > 0 ? load(state.pageIndex - 1) : Promise.resolve(),
    openDetail: async (reportId: number) => {
      detailController?.abort();
      const controller = new AbortController();
      detailController = controller;
      const requestId = ++detailRequestId;
      publish({ ...state, detail: null, selectedReportId: reportId, detailLoading: true, detailError: null,
        targetStatus: null, actionError: null });
      try {
        const detail = await dependencies.detail(reportId, controller.signal);
        if (!stopped && !controller.signal.aborted && requestId === detailRequestId) {
          publish({ ...state, detail, detailLoading: false, detailError: null });
        }
      } catch (error) {
        if (!stopped && !controller.signal.aborted && requestId === detailRequestId) {
          publish({ ...state, detailLoading: false,
            detailError: error instanceof Error ? error : new Error('상세 조회 실패') });
        }
      }
    },
    closeDetail: () => {
      if (state.submitting) return;
      detailRequestId += 1; detailController?.abort();
      publish({ ...state, detail: null, selectedReportId: null, detailLoading: false, detailError: null,
        targetStatus: null, actionError: null });
    },
    requestAction: (targetStatus: AdminReportTargetStatus) =>
      publish({ ...state, targetStatus, actionError: null }),
    cancelAction: () => {
      if (!state.submitting) publish({ ...state, targetStatus: null, actionError: null });
    },
    submitAction: (): Promise<boolean> => {
      if (inFlightAction) return inFlightAction;
      if (!state.detail || !state.targetStatus || stopped) return Promise.resolve(false);
      const reportId = state.detail.reportId;
      const targetStatus = state.targetStatus;
      const controller = new AbortController();
      actionController = controller;
      const requestId = ++actionRequestId;
      publish({ ...state, submitting: true, actionError: null });
      const operation = dependencies.changeStatus(reportId, targetStatus, controller.signal)
        .then((detail) => {
          if (stopped || controller.signal.aborted || requestId !== actionRequestId) return false;
          publish({ ...state, detail, items: state.items.map((item) => item.reportId === reportId
            ? toListItem(detail) : item), targetStatus: null, submitting: false,
          actionError: null, successMessage: actionSuccessMessage(targetStatus) });
          return true;
        }).catch((error: unknown) => {
          if (stopped || controller.signal.aborted || requestId !== actionRequestId) return false;
          publish({ ...state, submitting: false,
            actionError: error instanceof Error ? error : new Error('상태 변경 실패') });
          return false;
        }).finally(() => {
          if (actionController === controller) actionController = null;
          if (inFlightAction === operation) inFlightAction = null;
        });
      inFlightAction = operation;
      return operation;
    },
    clearSuccess: () => publish({ ...state, successMessage: null }),
    stop: () => {
      stopped = true; listRequestId += 1; detailRequestId += 1; actionRequestId += 1;
      listController?.abort(); detailController?.abort(); actionController?.abort();
      inFlightAction = null;
    },
  };
}

function toListItem(detail: AdminReportDetail): AdminReportListItem {
  return {
    reportId: detail.reportId,
    groupId: detail.group?.groupId ?? null,
    reasonCode: detail.reasonCode,
    status: detail.status,
    reporter: detail.reporter,
    reportedMember: detail.reportedMember,
    createdAt: detail.createdAt,
    updatedAt: detail.updatedAt,
  };
}

function actionSuccessMessage(target: AdminReportTargetStatus): string {
  if (target === 'REVIEWING') return '신고 검토를 시작했습니다.';
  if (target === 'RESOLVED') return '유효 신고로 검토를 마쳤습니다.';
  return '신고를 기각했습니다.';
}

export function useAdminReports() {
  const [state, setState] = useState(INITIAL_ADMIN_REPORTS_STATE);
  const sessionRef = useRef<ReturnType<typeof createAdminReportsSession> | null>(null);
  useEffect(() => {
    const session = createAdminReportsSession(adminReportsApi, setState);
    sessionRef.current = session;
    void session.load();
    return () => { sessionRef.current = null; session.stop(); };
  }, []);
  return {
    state,
    reload: useCallback(() => sessionRef.current?.load(), []),
    applyFilters: useCallback((filters: AdminReportFilters) => sessionRef.current?.applyFilters(filters), []),
    next: useCallback(() => sessionRef.current?.next(), []),
    previous: useCallback(() => sessionRef.current?.previous(), []),
    openDetail: useCallback((id: number) => sessionRef.current?.openDetail(id), []),
    closeDetail: useCallback(() => sessionRef.current?.closeDetail(), []),
    requestAction: useCallback((target: AdminReportTargetStatus) => sessionRef.current?.requestAction(target), []),
    cancelAction: useCallback(() => sessionRef.current?.cancelAction(), []),
    submitAction: useCallback(() => sessionRef.current?.submitAction() ?? Promise.resolve(false), []),
    clearSuccess: useCallback(() => sessionRef.current?.clearSuccess(), []),
  };
}
