import { useEffect, useRef, useState, type RefObject } from 'react';
import { ChevronRight, X } from 'lucide-react';
import AdminHeader from '../components/admin/AdminHeader';
import AdminNav from '../components/admin/AdminNav';
import type { AdminMeetingPoint, AdminMeetingPointUpsertRequest } from '../api/adminMeetingPoints';
import { useAdminMeetingPoints } from '../hooks/useAdminMeetingPoints';

/** 등록된 장소가 하나뿐인 ACTIVE 장소인지 — 비활성화하면 그 축제의 매칭이 막힐 수 있다는 경고에 사용. */
export function isLastActivePoint(points: AdminMeetingPoint[], point: AdminMeetingPoint): boolean {
  return point.status === 'ACTIVE' && points.filter((item) => item.status === 'ACTIVE').length === 1;
}

export default function AdminMeetingPointsPage() {
  const {
    state, searchFestivals, selectFestival, reloadPoints,
    openCreateForm, openEditForm, closeForm, submitForm, toggleStatus, clearSuccess,
  } = useAdminMeetingPoints();
  const [keywordDraft, setKeywordDraft] = useState('');
  const formOpenerRef = useRef<HTMLButtonElement | null>(null);
  const editingPoint = state.points.find((point) => point.id === state.editingPointId) ?? null;

  return (
    <div className="min-h-screen bg-sand">
      <AdminHeader title="만남 장소 관리" />
      <AdminNav />
      <main className="mx-auto flex max-w-5xl flex-col gap-5 p-6">
        <section className="rounded-2xl bg-white p-4 shadow-sm">
          <form
            onSubmit={(event) => { event.preventDefault(); void searchFestivals(keywordDraft); }}
            className="flex gap-2"
          >
            <input
              aria-label="축제 검색"
              value={keywordDraft}
              onChange={(event) => setKeywordDraft(event.target.value)}
              placeholder="축제명으로 검색"
              className="flex-1 rounded-xl border border-line px-3 py-2"
            />
            <button type="submit" className="rounded-xl bg-ink px-4 py-2 font-bold text-white">검색</button>
          </form>
          {state.festivalSearchStatus === 'ERROR' && (
            <p role="alert" className="mt-3 text-sm text-coral">축제 목록을 불러오지 못했습니다.</p>
          )}
          {state.festivalResults.length > 0 && (
            <ul className="mt-3 flex flex-col gap-1">
              {state.festivalResults.map((festival) => (
                <li key={festival.id}>
                  <button
                    type="button"
                    onClick={() => void selectFestival(festival.id, festival.title)}
                    className={`flex w-full items-center justify-between rounded-xl px-3 py-2 text-left ${
                      state.selectedFestival?.id === festival.id ? 'bg-coral/10 font-bold text-coral' : 'hover:bg-sand'
                    }`}
                  >
                    {festival.title}
                    <ChevronRight size={16} />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        {state.selectedFestival && (
          <section className="rounded-2xl bg-white p-4 shadow-sm">
            <div className="flex items-center justify-between">
              <h2 className="text-[15px] font-bold text-ink">{state.selectedFestival.title}의 만남 장소</h2>
              <button
                type="button"
                ref={formOpenerRef}
                onClick={openCreateForm}
                className="rounded-xl border border-line px-3 py-2 text-sm font-semibold"
              >
                새 장소 등록
              </button>
            </div>

            <div className="sr-only" role="status" aria-live="polite">
              {state.successMessage ?? (state.pointsStatus === 'LOADING' ? '만남 장소를 불러오는 중입니다.' : '')}
            </div>

            {state.pointsStatus === 'LOADING' && <p role="status" className="mt-6 text-center text-ink/60">장소를 불러오는 중...</p>}
            {state.pointsStatus === 'ERROR' && (
              <div role="alert" className="mt-6 text-center">
                <p className="text-coral">장소 목록을 불러오지 못했습니다.</p>
                <button type="button" onClick={() => void reloadPoints()} className="mt-2 underline">다시 시도</button>
              </div>
            )}
            {state.pointsStatus === 'READY' && state.points.length === 0 && (
              <p className="mt-6 text-center text-ink/60">등록된 만남 장소가 없습니다.</p>
            )}
            {state.pointsStatus === 'READY' && state.points.length > 0 && (
              <ul className="mt-4 flex flex-col gap-3">
                {[...state.points]
                  .sort((a, b) => a.assignmentOrder - b.assignmentOrder || a.id - b.id)
                  .map((point) => (
                    <li key={point.id} className="flex items-center gap-3 rounded-xl border border-line p-3">
                      <div className="flex-1">
                        <div className="flex items-center gap-2">
                          <span className="font-bold text-ink">{point.name}</span>
                          <StatusBadge status={point.status} />
                          {point.kakaoPlaceId.startsWith('AUTO-') && (
                            <span className="rounded-full bg-ink/5 px-2 py-0.5 text-xs text-ink/50">자동 생성</span>
                          )}
                        </div>
                        <p className="mt-1 text-sm text-ink/60">{point.address}</p>
                        <p className="mt-1 text-xs text-ink/40 tabular-nums">
                          {point.longitude}, {point.latitude} · 순서 {point.assignmentOrder}
                        </p>
                      </div>
                      <button
                        type="button"
                        onClick={() => openEditForm(point.id)}
                        className="rounded-xl border border-line px-3 py-2 text-sm font-semibold"
                      >
                        수정
                      </button>
                      <button
                        type="button"
                        disabled={state.togglingPointId === point.id}
                        onClick={() => {
                          if (
                            isLastActivePoint(state.points, point)
                            && !window.confirm('마지막으로 활성화된 장소예요. 비활성화하면 이 축제의 매칭이 막힐 수 있어요. 계속할까요?')
                          ) {
                            return;
                          }
                          void toggleStatus(point);
                        }}
                        className={`rounded-xl px-3 py-2 text-sm font-semibold disabled:opacity-50 ${
                          point.status === 'ACTIVE' ? 'border border-coral text-coral' : 'bg-teal text-white'
                        }`}
                      >
                        {point.status === 'ACTIVE' ? '비활성화' : '활성화'}
                      </button>
                    </li>
                  ))}
              </ul>
            )}
            {state.toggleError && (
              <p role="alert" className="mt-3 text-sm text-coral">상태 변경에 실패했습니다. 다시 시도해주세요.</p>
            )}
            {state.successMessage && (
              <div role="status" aria-live="polite" className="mt-4 rounded-xl bg-ink px-4 py-3 text-white">
                {state.successMessage}
                <button type="button" onClick={clearSuccess} className="ml-3 underline">닫기</button>
              </div>
            )}
          </section>
        )}
      </main>

      {state.formOpen && state.selectedFestival && (
        <AdminMeetingPointFormDialog
          festivalTitle={state.selectedFestival.title}
          initial={editingPoint}
          submitting={state.submitting}
          error={state.formError}
          onClose={() => { closeForm(); queueMicrotask(() => formOpenerRef.current?.focus()); }}
          onSubmit={(request) => void submitForm(request)}
        />
      )}
    </div>
  );
}

function StatusBadge({ status }: { status: AdminMeetingPoint['status'] }) {
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
      status === 'ACTIVE' ? 'bg-teal/10 text-teal' : 'bg-ink/10 text-ink/50'
    }`}>
      {status === 'ACTIVE' ? '활성' : '비활성'}
    </span>
  );
}

function toFormState(initial: AdminMeetingPoint | null): AdminMeetingPointUpsertRequest {
  if (!initial) return { kakaoPlaceId: '', name: '', address: '', longitude: 0, latitude: 0, assignmentOrder: 10 };
  return {
    kakaoPlaceId: initial.kakaoPlaceId, name: initial.name, address: initial.address,
    longitude: initial.longitude, latitude: initial.latitude, assignmentOrder: initial.assignmentOrder,
  };
}

export function AdminMeetingPointFormDialog({ festivalTitle, initial, submitting, error, onClose, onSubmit }: {
  festivalTitle: string;
  initial: AdminMeetingPoint | null;
  submitting: boolean;
  error: Error | null;
  onClose: () => void;
  onSubmit: (request: AdminMeetingPointUpsertRequest) => void;
}) {
  const [form, setForm] = useState(toFormState(initial));
  const dialogRef = useRef<HTMLElement>(null);
  useDialogKeyboard(dialogRef, submitting, onClose);

  return (
    <AdminMeetingPointFormDialogContent
      festivalTitle={festivalTitle}
      isEdit={initial !== null}
      form={form}
      submitting={submitting}
      error={error}
      onChange={setForm}
      onClose={onClose}
      onSubmit={onSubmit}
      dialogRef={dialogRef}
    />
  );
}

export function AdminMeetingPointFormDialogContent({
  festivalTitle, isEdit, form, submitting, error, onChange, onClose, onSubmit, dialogRef,
}: {
  festivalTitle: string;
  isEdit: boolean;
  form: AdminMeetingPointUpsertRequest;
  submitting: boolean;
  error: Error | null;
  onChange: (form: AdminMeetingPointUpsertRequest) => void;
  onClose: () => void;
  onSubmit: (request: AdminMeetingPointUpsertRequest) => void;
  dialogRef?: RefObject<HTMLElement>;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/55 p-4">
      <section
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="meeting-point-form-title"
        className="w-full max-w-md rounded-3xl bg-white p-6"
      >
        <div className="flex items-center justify-between">
          <h2 id="meeting-point-form-title" className="text-lg font-bold">
            {isEdit ? '만남 장소 수정' : '만남 장소 등록'}
          </h2>
          <button type="button" disabled={submitting} onClick={onClose} aria-label="닫기"><X /></button>
        </div>
        <p className="mt-1 text-sm text-ink/50">{festivalTitle}</p>
        {!isEdit && (
          <p className="mt-3 rounded-xl bg-ink/5 p-3 text-sm text-ink/60">
            새로 등록한 장소는 비활성 상태로 저장됩니다. 등록 후 목록에서 활성화해주세요.
          </p>
        )}
        <form
          onSubmit={(event) => { event.preventDefault(); onSubmit(form); }}
          className="mt-4 flex flex-col gap-3"
        >
          <label className="text-sm font-semibold">이름
            <input required maxLength={255} value={form.name}
              onChange={(event) => onChange({ ...form, name: event.target.value })}
              className="mt-1 block w-full rounded-xl border border-line p-2 font-normal" />
          </label>
          <label className="text-sm font-semibold">주소
            <input required maxLength={500} value={form.address}
              onChange={(event) => onChange({ ...form, address: event.target.value })}
              className="mt-1 block w-full rounded-xl border border-line p-2 font-normal" />
          </label>
          <label className="text-sm font-semibold">카카오 장소 ID
            <input required maxLength={50} value={form.kakaoPlaceId}
              onChange={(event) => onChange({ ...form, kakaoPlaceId: event.target.value })}
              className="mt-1 block w-full rounded-xl border border-line p-2 font-normal" />
          </label>
          <div className="grid grid-cols-2 gap-3">
            <label className="text-sm font-semibold">경도
              <input required type="number" step="0.0000000001" min={-180} max={180} value={form.longitude}
                onChange={(event) => onChange({ ...form, longitude: Number(event.target.value) })}
                className="mt-1 block w-full rounded-xl border border-line p-2 font-normal" />
            </label>
            <label className="text-sm font-semibold">위도
              <input required type="number" step="0.0000000001" min={-90} max={90} value={form.latitude}
                onChange={(event) => onChange({ ...form, latitude: Number(event.target.value) })}
                className="mt-1 block w-full rounded-xl border border-line p-2 font-normal" />
            </label>
          </div>
          <label className="text-sm font-semibold">배정 순서
            <input required type="number" step="1" min={0} value={form.assignmentOrder}
              onChange={(event) => onChange({ ...form, assignmentOrder: Number(event.target.value) })}
              className="mt-1 block w-full rounded-xl border border-line p-2 font-normal" />
          </label>
          {error && (
            <p role="alert" aria-live="assertive" className="rounded-xl bg-coral/10 p-3 text-sm text-coral">
              저장하지 못했습니다. 다시 시도해주세요.
            </p>
          )}
          <div className="mt-2 grid grid-cols-2 gap-2">
            <button type="button" disabled={submitting} onClick={onClose} className="rounded-xl border border-line py-3 disabled:opacity-50">취소</button>
            <button type="submit" disabled={submitting} className="rounded-xl bg-coral py-3 font-bold text-white disabled:opacity-50">
              {submitting ? '저장 중...' : '저장'}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

function useDialogKeyboard(ref: RefObject<HTMLElement>, submitting: boolean, onClose: () => void) {
  useEffect(() => {
    const dialog = ref.current; if (!dialog) return;
    const focusables = () => Array.from(dialog.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled)'));
    focusables()[0]?.focus();
    const listener = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !submitting) { event.preventDefault(); onClose(); }
      if (event.key === 'Tab') {
        const values = focusables();
        const first = values[0]; const last = values[values.length - 1];
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
      }
    };
    document.addEventListener('keydown', listener);
    return () => document.removeEventListener('keydown', listener);
  }, [ref, submitting, onClose]);
}
