import { useEffect, useRef, useState, type RefObject } from 'react';
import { ChevronLeft, ChevronRight, X } from 'lucide-react';
import AdminHeader from '../components/admin/AdminHeader';
import AdminNav from '../components/admin/AdminNav';
import {
  type AdminReportDetail,
  type AdminReportFilters,
  type AdminReportReasonCode,
  type AdminReportStatus,
  type AdminReportTargetStatus,
} from '../api/adminReports';
import { EMPTY_ADMIN_REPORT_FILTERS, useAdminReports } from '../hooks/useAdminReports';
import { formatSeoulDateTime } from '../utils/dateTime';

const STATUS_OPTIONS: Array<{ value: AdminReportStatus | ''; label: string }> = [
  { value: '', label: '전체 상태' }, { value: 'SUBMITTED', label: '접수됨' },
  { value: 'REVIEWING', label: '검토 중' }, { value: 'RESOLVED', label: '유효 신고' },
  { value: 'REJECTED', label: '기각' }, { value: 'ACTION_TAKEN', label: '제재 완료' },
];
const REASON_OPTIONS: Array<{ value: AdminReportReasonCode | ''; label: string }> = [
  { value: '', label: '전체 사유' }, { value: 'RUDE', label: '무례한 행동' },
  { value: 'SEXUAL_HARASSMENT', label: '성희롱' }, { value: 'NO_SHOW', label: '나타나지 않음' },
  { value: 'SCAM', label: '사기 의심' }, { value: 'SAFETY', label: '안전 문제' },
  { value: 'OTHER', label: '기타' },
];

export default function AdminReportsPage() {
  const actions = useAdminReports();
  const { state } = actions;
  const [draft, setDraft] = useState(EMPTY_ADMIN_REPORT_FILTERS);
  const detailOpenerRef = useRef<HTMLButtonElement | null>(null);
  const actionOpenerRef = useRef<HTMLButtonElement | null>(null);

  return (
    <div className="min-h-screen bg-sand">
      <AdminHeader title="관리자 신고 검토" />
      <AdminNav />
      <main className="mx-auto flex max-w-6xl flex-col gap-5 p-6" aria-busy={state.status === 'LOADING'}>
        <form onSubmit={(event) => { event.preventDefault(); void actions.applyFilters(toApiFilters(draft)); }} className="grid gap-3 rounded-2xl bg-white p-4 shadow-sm md:grid-cols-5">
          <label className="text-sm font-semibold text-ink">상태<select aria-label="신고 상태" value={draft.status} onChange={(event) => setDraft({ ...draft, status: event.target.value as AdminReportStatus | '' })} className="mt-1 block w-full rounded-xl border border-line p-2 font-normal">{STATUS_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
          <label className="text-sm font-semibold text-ink">사유<select aria-label="신고 사유" value={draft.reason} onChange={(event) => setDraft({ ...draft, reason: event.target.value as AdminReportReasonCode | '' })} className="mt-1 block w-full rounded-xl border border-line p-2 font-normal">{REASON_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
          <label className="text-sm font-semibold text-ink">시작 시각<input aria-label="생성 시작 시각" type="datetime-local" value={draft.createdFrom} onChange={(event) => setDraft({ ...draft, createdFrom: event.target.value })} className="mt-1 block w-full rounded-xl border border-line p-2 font-normal" /></label>
          <label className="text-sm font-semibold text-ink">종료 시각<input aria-label="생성 종료 시각" type="datetime-local" value={draft.createdTo} onChange={(event) => setDraft({ ...draft, createdTo: event.target.value })} className="mt-1 block w-full rounded-xl border border-line p-2 font-normal" /></label>
          <button type="submit" className="self-end rounded-xl bg-ink px-4 py-2.5 font-bold text-white">필터 적용</button>
        </form>

        <div className="sr-only" role="status" aria-live="polite">{state.successMessage ?? (state.status === 'LOADING' ? '신고 목록을 불러오는 중입니다.' : '')}</div>
        {state.status === 'LOADING' && <p role="status" className="rounded-2xl bg-white p-8 text-center text-ink/60">신고 목록을 불러오는 중...</p>}
        {state.status === 'ERROR' && <section role="alert" className="rounded-2xl bg-white p-8 text-center"><p className="text-coral">신고 목록을 불러오지 못했습니다.</p><button type="button" onClick={() => void actions.reload()} className="mt-3 rounded-xl border border-line px-4 py-2 font-semibold">다시 시도</button></section>}
        {state.status === 'READY' && state.items.length === 0 && <p className="rounded-2xl bg-white p-8 text-center text-ink/60">조건에 맞는 신고가 없습니다.</p>}
        {state.status === 'READY' && state.items.length > 0 && (
          <section className="overflow-x-auto rounded-2xl bg-white shadow-sm" aria-label="관리자 신고 목록">
            <table className="w-full min-w-[760px] text-left text-sm"><thead className="bg-ink/5 text-ink/60"><tr><th className="p-4">상태</th><th className="p-4">사유</th><th className="p-4">신고자</th><th className="p-4">피신고자</th><th className="p-4">접수 시각</th><th className="p-4"><span className="sr-only">상세</span></th></tr></thead><tbody>{state.items.map((report) => <tr key={report.reportId} className="border-t border-line"><td className="p-4 font-semibold">{statusLabel(report.status)}</td><td className="p-4">{reasonLabel(report.reasonCode)}</td><td className="p-4">{report.reporter.nickname}</td><td className="p-4">{report.reportedMember.nickname}</td><td className="p-4 tabular-nums text-ink/60">{formatSeoulDateTime(report.createdAt)}</td><td className="p-4 text-right"><button type="button" onClick={(event) => { detailOpenerRef.current = event.currentTarget; void actions.openDetail(report.reportId); }} className="rounded-xl border border-line px-3 py-2 font-semibold">검토</button></td></tr>)}</tbody></table>
          </section>
        )}
        {state.status === 'READY' && <nav aria-label="신고 목록 페이지" className="flex items-center justify-center gap-3"><button type="button" disabled={state.pageIndex === 0} onClick={() => void actions.previous()} className="rounded-xl border border-line bg-white p-2 disabled:opacity-40" aria-label="이전 페이지"><ChevronLeft /></button><span className="text-sm font-semibold">{state.pageIndex + 1}페이지</span><button type="button" disabled={!state.hasNext} onClick={() => void actions.next()} className="rounded-xl border border-line bg-white p-2 disabled:opacity-40" aria-label="다음 페이지"><ChevronRight /></button></nav>}
        {state.successMessage && <div role="status" aria-live="polite" className="rounded-xl bg-ink px-4 py-3 text-white">{state.successMessage}<button type="button" onClick={actions.clearSuccess} className="ml-3 underline">닫기</button></div>}
      </main>
      {(state.detailLoading || state.detailError || state.detail) && <AdminReportDetailDialog detail={state.detail} loading={state.detailLoading} error={state.detailError} onRetry={() => state.selectedReportId && void actions.openDetail(state.selectedReportId)} onClose={() => { actions.closeDetail(); queueMicrotask(() => detailOpenerRef.current?.focus()); }} onAction={(target, opener) => { actionOpenerRef.current = opener; actions.requestAction(target); }} />}
      {state.detail && state.targetStatus && <AdminReportActionDialog detail={state.detail} targetStatus={state.targetStatus} submitting={state.submitting} error={state.actionError} onClose={() => { actions.cancelAction(); queueMicrotask(() => actionOpenerRef.current?.focus()); }} onSubmit={() => void actions.submitAction()} />}
    </div>
  );
}

export function toApiFilters(filters: AdminReportFilters): AdminReportFilters {
  return { ...filters, createdFrom: toSeoulOffset(filters.createdFrom), createdTo: toSeoulOffset(filters.createdTo) };
}
function toSeoulOffset(value: string): string { return value ? `${value}:00+09:00` : ''; }
function statusLabel(value: AdminReportStatus): string { return STATUS_OPTIONS.find((option) => option.value === value)?.label ?? value; }
function reasonLabel(value: AdminReportReasonCode): string { return REASON_OPTIONS.find((option) => option.value === value)?.label ?? value; }

export function AdminReportDetailDialog({ detail, loading, error, onRetry, onClose, onAction }: { detail: AdminReportDetail | null; loading: boolean; error: Error | null; onRetry: () => void; onClose: () => void; onAction: (target: AdminReportTargetStatus, opener: HTMLButtonElement) => void }) {
  const dialogRef = useRef<HTMLElement>(null);
  useDialogKeyboard(dialogRef, false, onClose);
  return <AdminReportDetailDialogContent detail={detail} loading={loading} error={error} onRetry={onRetry} onClose={onClose} onAction={onAction} dialogRef={dialogRef} />;
}

export function AdminReportDetailDialogContent({ detail, loading, error, onRetry, onClose, onAction, dialogRef }: { detail: AdminReportDetail | null; loading: boolean; error: Error | null; onRetry: () => void; onClose: () => void; onAction: (target: AdminReportTargetStatus, opener: HTMLButtonElement) => void; dialogRef?: RefObject<HTMLElement> }) {
  return <div className="fixed inset-0 z-40 flex items-center justify-center bg-ink/45 p-5"><section ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="report-detail-title" aria-describedby="report-detail-description" className="max-h-[90vh] w-full max-w-xl overflow-y-auto rounded-3xl bg-white p-6 shadow-xl"><div className="flex justify-between gap-3"><div><h2 id="report-detail-title" className="text-lg font-bold">신고 검토 상세</h2><p id="report-detail-description" className="mt-1 text-sm text-ink/55">구조화된 신고 사유와 검토에 필요한 최소 회원 정보입니다.</p></div><button type="button" onClick={onClose} aria-label="신고 상세 닫기"><X /></button></div>{loading && <p role="status" className="mt-6">상세 정보를 불러오는 중...</p>}{error && <div role="alert" className="mt-6 text-coral"><p>상세 정보를 불러오지 못했습니다.</p><button type="button" onClick={onRetry} className="mt-2 underline">다시 시도</button></div>}{detail && <><dl className="mt-6 grid grid-cols-[auto_1fr] gap-3 rounded-2xl bg-sand/50 p-4 text-sm"><dt>상태</dt><dd className="text-right font-bold">{statusLabel(detail.status)}</dd><dt>사유</dt><dd className="text-right font-bold">{reasonLabel(detail.reasonCode)}</dd><dt>신고자</dt><dd className="text-right">{detail.reporter.nickname}</dd><dt>피신고자</dt><dd className="text-right">{detail.reportedMember.nickname}</dd><dt>접수 시각</dt><dd className="text-right">{formatSeoulDateTime(detail.createdAt)}</dd><dt>최종 갱신</dt><dd className="text-right">{formatSeoulDateTime(detail.updatedAt)}</dd></dl><p className="mt-4 rounded-xl bg-coral/10 p-3 text-sm text-ink/70">유효 신고 확정은 제재를 적용하지 않습니다. 회원 제재는 후속 단계에서 별도로 처리합니다.</p><div className="mt-5 flex flex-wrap justify-end gap-2">{detail.status === 'SUBMITTED' && <button type="button" onClick={(event) => onAction('REVIEWING', event.currentTarget)} className="rounded-xl border border-line px-4 py-2 font-semibold">검토 시작</button>}{(detail.status === 'SUBMITTED' || detail.status === 'REVIEWING') && <><button type="button" onClick={(event) => onAction('REJECTED', event.currentTarget)} className="rounded-xl border border-coral px-4 py-2 font-semibold text-coral">신고 기각</button><button type="button" onClick={(event) => onAction('RESOLVED', event.currentTarget)} className="rounded-xl bg-coral px-4 py-2 font-bold text-white">유효 신고 확정</button></>}</div></>}</section></div>;
}

export function AdminReportActionDialog({ detail, targetStatus, submitting, error, onClose, onSubmit }: { detail: AdminReportDetail; targetStatus: AdminReportTargetStatus; submitting: boolean; error: Error | null; onClose: () => void; onSubmit: () => void }) {
  const dialogRef = useRef<HTMLElement>(null);
  useDialogKeyboard(dialogRef, submitting, onClose);
  return <AdminReportActionDialogContent detail={detail} targetStatus={targetStatus} submitting={submitting} error={error} onClose={onClose} onSubmit={onSubmit} dialogRef={dialogRef} />;
}

export function AdminReportActionDialogContent({ detail, targetStatus, submitting, error, onClose, onSubmit, dialogRef }: { detail: AdminReportDetail; targetStatus: AdminReportTargetStatus; submitting: boolean; error: Error | null; onClose: () => void; onSubmit: () => void; dialogRef?: RefObject<HTMLElement> }) {
  const title = targetStatus === 'REVIEWING' ? '신고 검토를 시작할까요?' : targetStatus === 'RESOLVED' ? '유효 신고로 확정할까요?' : '신고를 기각할까요?';
  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/55 p-5"><section ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="report-action-title" aria-describedby="report-action-description" className="w-full max-w-md rounded-3xl bg-white p-6 shadow-xl"><h2 id="report-action-title" className="text-lg font-bold">{title}</h2><p id="report-action-description" className="mt-2 text-sm text-ink/60">피신고자 {detail.reportedMember.nickname}님의 신고 상태만 변경합니다. 제재나 알림은 생성하지 않습니다.</p>{error && <p role="alert" aria-live="assertive" className="mt-4 rounded-xl bg-coral/10 p-3 text-sm text-coral">처리하지 못했습니다. 기존 상태는 유지되며 다시 시도할 수 있습니다.</p>}<div className="mt-6 grid grid-cols-2 gap-2"><button type="button" disabled={submitting} onClick={onClose} className="rounded-xl border border-line py-3 disabled:opacity-50">취소</button><button type="button" disabled={submitting} onClick={onSubmit} className="rounded-xl bg-coral py-3 font-bold text-white disabled:opacity-50">{submitting ? '처리 중...' : '확인'}</button></div></section></div>;
}

function useDialogKeyboard(ref: RefObject<HTMLElement>, submitting: boolean, onClose: () => void) {
  useEffect(() => {
    const dialog = ref.current; if (!dialog) return;
    const focusables = () => Array.from(dialog.querySelectorAll<HTMLElement>('button:not(:disabled),select:not(:disabled),input:not(:disabled)'));
    focusables()[0]?.focus();
    const listener = (event: KeyboardEvent) => handleAdminDialogKeyDown(event, focusables(), document.activeElement, submitting, onClose);
    document.addEventListener('keydown', listener);
    return () => document.removeEventListener('keydown', listener);
  }, [ref, submitting, onClose]);
}

export function handleAdminDialogKeyDown(event: Pick<KeyboardEvent, 'key' | 'shiftKey' | 'preventDefault'>, focusables: HTMLElement[], active: Element | null, submitting: boolean, onClose: () => void) {
  if (event.key === 'Escape' && !submitting) { event.preventDefault(); onClose(); return; }
  if (event.key !== 'Tab' || focusables.length === 0) return;
  const first = focusables[0]; const last = focusables[focusables.length - 1];
  if (event.shiftKey && active === first) { event.preventDefault(); last.focus(); }
  else if (!event.shiftKey && active === last) { event.preventDefault(); first.focus(); }
}
