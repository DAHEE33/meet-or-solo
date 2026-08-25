import { useCallback, useEffect, useRef, useState } from 'react';
import { adminMembersApi, type AdminMemberActionRequest, type AdminMemberDetail, type AdminMemberFilters, type AdminMemberListItem, type AdminMemberPage } from '../api/adminMembers';

export const EMPTY_ADMIN_MEMBER_FILTERS: AdminMemberFilters = { query: '', status: '', role: 'USER' };
export type AdminMembersState = {
  status: 'LOADING' | 'READY' | 'ERROR'; items: AdminMemberListItem[]; filters: AdminMemberFilters;
  pageIndex: number; hasNext: boolean; detail: AdminMemberDetail | null; selectedMemberId: number | null;
  detailLoading: boolean; detailError: Error | null; pendingAction: AdminMemberActionRequest | null;
  submitting: boolean; actionError: Error | null; successMessage: string | null;
};
export const INITIAL_ADMIN_MEMBERS_STATE: AdminMembersState = {
  status: 'LOADING', items: [], filters: EMPTY_ADMIN_MEMBER_FILTERS, pageIndex: 0, hasNext: false,
  detail: null, selectedMemberId: null, detailLoading: false, detailError: null, pendingAction: null,
  submitting: false, actionError: null, successMessage: null,
};
type Dependencies = {
  list: (filters: AdminMemberFilters, cursor: string | null, size: number, signal: AbortSignal) => Promise<AdminMemberPage>;
  detail: (memberId: number, signal: AbortSignal) => Promise<AdminMemberDetail>;
  act: (memberId: number, request: AdminMemberActionRequest, key: string, signal: AbortSignal) => Promise<AdminMemberDetail>;
};

export function createAdminMembersSession(dependencies: Dependencies, onState: (state: AdminMembersState) => void) {
  let state = INITIAL_ADMIN_MEMBERS_STATE; let cursors: Array<string | null> = [null]; let nextCursor: string | null = null;
  let listId = 0; let detailId = 0; let actionId = 0; let listController: AbortController | null = null;
  let detailController: AbortController | null = null; let actionController: AbortController | null = null;
  let inFlight: Promise<boolean> | null = null; let stopped = false;
  const publish = (next: AdminMembersState) => { state = next; if (!stopped) onState(next); };
  const load = async (pageIndex = state.pageIndex) => {
    listController?.abort(); const controller = new AbortController(); listController = controller; const requestId = ++listId;
    publish({ ...state, status: 'LOADING' });
    try {
      const page = await dependencies.list(state.filters, cursors[pageIndex] ?? null, 20, controller.signal);
      if (stopped || controller.signal.aborted || requestId !== listId) return;
      nextCursor = page.pagination.nextCursor;
      publish({ ...state, status: 'READY', items: page.items, pageIndex, hasNext: page.pagination.hasNext });
    } catch { if (!stopped && !controller.signal.aborted && requestId === listId) publish({ ...state, status: 'ERROR' }); }
  };
  const openDetail = async (memberId: number) => {
    detailController?.abort(); const controller = new AbortController(); detailController = controller; const requestId = ++detailId;
    publish({ ...state, selectedMemberId: memberId, detail: null, detailLoading: true, detailError: null, pendingAction: null });
    try {
      const detail = await dependencies.detail(memberId, controller.signal);
      if (!stopped && !controller.signal.aborted && requestId === detailId) publish({ ...state, detail, detailLoading: false });
    } catch (error) { if (!stopped && !controller.signal.aborted && requestId === detailId) publish({ ...state, detailLoading: false, detailError: error instanceof Error ? error : new Error('상세 조회 실패') }); }
  };
  return {
    load,
    applyFilters: (filters: AdminMemberFilters) => { cursors = [null]; nextCursor = null; publish({ ...state, filters, pageIndex: 0, hasNext: false, detail: null, selectedMemberId: null }); return load(0); },
    next: () => { if (!state.hasNext || !nextCursor) return Promise.resolve(); const index = state.pageIndex + 1; cursors = [...cursors.slice(0, index), nextCursor]; return load(index); },
    previous: () => state.pageIndex > 0 ? load(state.pageIndex - 1) : Promise.resolve(), openDetail,
    closeDetail: () => { if (!state.submitting) { detailId++; detailController?.abort(); publish({ ...state, detail: null, selectedMemberId: null, pendingAction: null, actionError: null }); } },
    requestAction: (request: AdminMemberActionRequest) => publish({ ...state, pendingAction: request, actionError: null }),
    cancelAction: () => { if (!state.submitting) publish({ ...state, pendingAction: null, actionError: null }); },
    submitAction: () => {
      if (inFlight) return inFlight;
      if (!state.detail || !state.pendingAction || stopped) return Promise.resolve(false);
      const memberId = state.detail.memberId; const request = state.pendingAction; const controller = new AbortController(); actionController = controller; const requestId = ++actionId;
      publish({ ...state, submitting: true, actionError: null });
      const operation = dependencies.act(memberId, request, crypto.randomUUID(), controller.signal).then((detail) => {
        if (stopped || controller.signal.aborted || requestId !== actionId) return false;
        publish({ ...state, detail, items: state.items.map((item) => item.memberId === memberId ? detail : item), pendingAction: null, submitting: false, successMessage: '회원 조치를 처리했습니다.' }); return true;
      }).catch((error: unknown) => { if (stopped || controller.signal.aborted || requestId !== actionId) return false; publish({ ...state, submitting: false, actionError: error instanceof Error ? error : new Error('조치 실패') }); return false; }).finally(() => { if (actionController === controller) actionController = null; if (inFlight === operation) inFlight = null; });
      inFlight = operation; return operation;
    },
    stop: () => { stopped = true; listId++; detailId++; actionId++; listController?.abort(); detailController?.abort(); actionController?.abort(); inFlight = null; },
  };
}

export function useAdminMembers() {
  const [state, setState] = useState(INITIAL_ADMIN_MEMBERS_STATE); const sessionRef = useRef<ReturnType<typeof createAdminMembersSession> | null>(null);
  useEffect(() => { const session = createAdminMembersSession(adminMembersApi, setState); sessionRef.current = session; void session.load(); return () => { sessionRef.current = null; session.stop(); }; }, []);
  return {
    state, reload: useCallback(() => sessionRef.current?.load(), []), applyFilters: useCallback((filters: AdminMemberFilters) => sessionRef.current?.applyFilters(filters), []),
    next: useCallback(() => sessionRef.current?.next(), []), previous: useCallback(() => sessionRef.current?.previous(), []), openDetail: useCallback((id: number) => sessionRef.current?.openDetail(id), []),
    closeDetail: useCallback(() => sessionRef.current?.closeDetail(), []), requestAction: useCallback((request: AdminMemberActionRequest) => sessionRef.current?.requestAction(request), []),
    cancelAction: useCallback(() => sessionRef.current?.cancelAction(), []), submitAction: useCallback(() => sessionRef.current?.submitAction() ?? Promise.resolve(false), []),
  };
}
