import { useCallback, useEffect, useRef, useState } from 'react';
import {
  adminMeetingPointsApi,
  type AdminMeetingPoint,
  type AdminMeetingPointStatus,
  type AdminMeetingPointUpsertRequest,
} from '../api/adminMeetingPoints';
import { adminFestivalsApi, type AdminFestivalSummary } from '../api/adminFestivals';

export type AdminMeetingPointsState = {
  keyword: string;
  festivalSearchStatus: 'IDLE' | 'LOADING' | 'READY' | 'ERROR';
  festivalResults: AdminFestivalSummary[];
  // mapX/mapY는 신규 장소 등록 폼의 카카오맵 좌표 선택기 초기 중심점으로 쓴다.
  selectedFestival: { id: number; title: string; mapX: number | null; mapY: number | null } | null;
  pointsStatus: 'IDLE' | 'LOADING' | 'READY' | 'ERROR';
  points: AdminMeetingPoint[];
  formOpen: boolean;
  editingPointId: number | null; // null이면 신규 등록
  submitting: boolean;
  formError: Error | null;
  togglingPointId: number | null;
  toggleError: Error | null;
  successMessage: string | null;
};

export const INITIAL_ADMIN_MEETING_POINTS_STATE: AdminMeetingPointsState = {
  keyword: '',
  festivalSearchStatus: 'IDLE',
  festivalResults: [],
  selectedFestival: null,
  pointsStatus: 'IDLE',
  points: [],
  formOpen: false,
  editingPointId: null,
  submitting: false,
  formError: null,
  togglingPointId: null,
  toggleError: null,
  successMessage: null,
};

type Dependencies = {
  searchFestivals: (keyword: string, signal: AbortSignal) => Promise<AdminFestivalSummary[]>;
  listPoints: (festivalId: number, signal: AbortSignal) => Promise<AdminMeetingPoint[]>;
  create: (festivalId: number, request: AdminMeetingPointUpsertRequest, signal: AbortSignal) =>
    Promise<AdminMeetingPoint>;
  update: (festivalId: number, pointId: number, request: AdminMeetingPointUpsertRequest, signal: AbortSignal) =>
    Promise<AdminMeetingPoint>;
  changeStatus: (festivalId: number, pointId: number, status: AdminMeetingPointStatus, signal: AbortSignal) =>
    Promise<AdminMeetingPoint>;
};

export function createAdminMeetingPointsSession(
  dependencies: Dependencies,
  onState: (state: AdminMeetingPointsState) => void,
) {
  let state = INITIAL_ADMIN_MEETING_POINTS_STATE;
  let festivalSearchRequestId = 0;
  let festivalSearchController: AbortController | null = null;
  let pointsRequestId = 0;
  let pointsController: AbortController | null = null;
  let submitRequestId = 0;
  let submitController: AbortController | null = null;
  let inFlightSubmit: Promise<boolean> | null = null;
  const inFlightToggle = new Map<number, Promise<boolean>>();
  let stopped = false;
  const publish = (next: AdminMeetingPointsState) => { state = next; if (!stopped) onState(next); };

  const loadPoints = async (festivalId: number) => {
    pointsController?.abort();
    const controller = new AbortController();
    pointsController = controller;
    const requestId = ++pointsRequestId;
    publish({ ...state, pointsStatus: 'LOADING' });
    try {
      const points = await dependencies.listPoints(festivalId, controller.signal);
      if (stopped || controller.signal.aborted || requestId !== pointsRequestId) return;
      publish({ ...state, pointsStatus: 'READY', points });
    } catch {
      if (stopped || controller.signal.aborted || requestId !== pointsRequestId) return;
      publish({ ...state, pointsStatus: 'ERROR' });
    }
  };

  return {
    searchFestivals: async (keyword: string) => {
      festivalSearchController?.abort();
      const controller = new AbortController();
      festivalSearchController = controller;
      const requestId = ++festivalSearchRequestId;
      publish({ ...state, keyword, festivalSearchStatus: 'LOADING' });
      try {
        const results = await dependencies.searchFestivals(keyword, controller.signal);
        if (stopped || controller.signal.aborted || requestId !== festivalSearchRequestId) return;
        publish({ ...state, festivalSearchStatus: 'READY', festivalResults: results });
      } catch {
        if (stopped || controller.signal.aborted || requestId !== festivalSearchRequestId) return;
        publish({ ...state, festivalSearchStatus: 'ERROR', festivalResults: [] });
      }
    },
    selectFestival: (id: number, title: string, mapX: number | null = null, mapY: number | null = null) => {
      publish({
        ...state, selectedFestival: { id, title, mapX, mapY }, points: [], pointsStatus: 'LOADING',
        formOpen: false, editingPointId: null, formError: null, successMessage: null,
      });
      return loadPoints(id);
    },
    reloadPoints: () => state.selectedFestival ? loadPoints(state.selectedFestival.id) : Promise.resolve(),
    openCreateForm: () => publish({ ...state, formOpen: true, editingPointId: null, formError: null }),
    openEditForm: (pointId: number) => publish({ ...state, formOpen: true, editingPointId: pointId, formError: null }),
    closeForm: () => {
      if (state.submitting) return;
      publish({ ...state, formOpen: false, editingPointId: null, formError: null });
    },
    submitForm: (request: AdminMeetingPointUpsertRequest): Promise<boolean> => {
      if (inFlightSubmit) return inFlightSubmit;
      if (!state.selectedFestival || stopped) return Promise.resolve(false);
      const festivalId = state.selectedFestival.id;
      const editingPointId = state.editingPointId;
      const controller = new AbortController();
      submitController = controller;
      const requestId = ++submitRequestId;
      publish({ ...state, submitting: true, formError: null });
      const call = editingPointId === null
        ? dependencies.create(festivalId, request, controller.signal)
        : dependencies.update(festivalId, editingPointId, request, controller.signal);
      const operation = call.then((saved) => {
        if (stopped || controller.signal.aborted || requestId !== submitRequestId) return false;
        const points = editingPointId === null
          ? [...state.points, saved]
          : state.points.map((point) => point.id === saved.id ? saved : point);
        publish({
          ...state, points, submitting: false, formOpen: false, editingPointId: null, formError: null,
          successMessage: editingPointId === null ? '장소를 등록했습니다. 목록에서 활성화해주세요.' : '장소를 수정했습니다.',
        });
        return true;
      }).catch((error: unknown) => {
        if (stopped || controller.signal.aborted || requestId !== submitRequestId) return false;
        publish({ ...state, submitting: false, formError: error instanceof Error ? error : new Error('저장 실패') });
        return false;
      }).finally(() => {
        if (submitController === controller) submitController = null;
        if (inFlightSubmit === operation) inFlightSubmit = null;
      });
      inFlightSubmit = operation;
      return operation;
    },
    toggleStatus: (point: AdminMeetingPoint): Promise<boolean> => {
      const existing = inFlightToggle.get(point.id);
      if (existing) return existing;
      if (!state.selectedFestival || stopped) return Promise.resolve(false);
      const festivalId = state.selectedFestival.id;
      const nextStatus: AdminMeetingPointStatus = point.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
      const controller = new AbortController();
      publish({ ...state, togglingPointId: point.id, toggleError: null });
      const operation = dependencies.changeStatus(festivalId, point.id, nextStatus, controller.signal)
        .then((saved) => {
          if (stopped || controller.signal.aborted) return false;
          publish({
            ...state, togglingPointId: null,
            points: state.points.map((existingPoint) => existingPoint.id === saved.id ? saved : existingPoint),
          });
          return true;
        }).catch((error: unknown) => {
          if (stopped || controller.signal.aborted) return false;
          publish({ ...state, togglingPointId: null, toggleError: error instanceof Error ? error : new Error('상태 변경 실패') });
          return false;
        }).finally(() => { inFlightToggle.delete(point.id); });
      inFlightToggle.set(point.id, operation);
      return operation;
    },
    clearSuccess: () => publish({ ...state, successMessage: null }),
    stop: () => {
      stopped = true;
      festivalSearchRequestId += 1; pointsRequestId += 1; submitRequestId += 1;
      festivalSearchController?.abort(); pointsController?.abort(); submitController?.abort();
      inFlightSubmit = null; inFlightToggle.clear();
    },
  };
}

export function useAdminMeetingPoints() {
  const [state, setState] = useState(INITIAL_ADMIN_MEETING_POINTS_STATE);
  const sessionRef = useRef<ReturnType<typeof createAdminMeetingPointsSession> | null>(null);
  useEffect(() => {
    const session = createAdminMeetingPointsSession(
      {
        searchFestivals: (keyword, signal) => adminFestivalsApi.search(keyword || undefined, signal),
        listPoints: adminMeetingPointsApi.list,
        create: adminMeetingPointsApi.create,
        update: adminMeetingPointsApi.update,
        changeStatus: adminMeetingPointsApi.changeStatus,
      },
      setState,
    );
    sessionRef.current = session;
    void session.searchFestivals('');
    return () => { sessionRef.current = null; session.stop(); };
  }, []);
  return {
    state,
    searchFestivals: useCallback((keyword: string) => sessionRef.current?.searchFestivals(keyword), []),
    selectFestival: useCallback(
      (id: number, title: string, mapX: number | null = null, mapY: number | null = null) =>
        sessionRef.current?.selectFestival(id, title, mapX, mapY),
      [],
    ),
    reloadPoints: useCallback(() => sessionRef.current?.reloadPoints(), []),
    openCreateForm: useCallback(() => sessionRef.current?.openCreateForm(), []),
    openEditForm: useCallback((pointId: number) => sessionRef.current?.openEditForm(pointId), []),
    closeForm: useCallback(() => sessionRef.current?.closeForm(), []),
    submitForm: useCallback((request: AdminMeetingPointUpsertRequest) =>
      sessionRef.current?.submitForm(request) ?? Promise.resolve(false), []),
    toggleStatus: useCallback((point: AdminMeetingPoint) =>
      sessionRef.current?.toggleStatus(point) ?? Promise.resolve(false), []),
    clearSuccess: useCallback(() => sessionRef.current?.clearSuccess(), []),
  };
}
