import { ChevronLeft, ChevronRight, TriangleAlert } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { AdminSafetyAlertStatus } from '../../api/adminSafetyAlerts';
import { useAdminSafetyAlerts } from '../../hooks/useAdminSafetyAlerts';
import { formatSeoulDateTime } from '../../utils/dateTime';
import { LoadingState } from '../common/Spinner';

const FILTER_OPTIONS: Array<{ value: AdminSafetyAlertStatus | ''; label: string }> = [
  { value: 'OPEN', label: '미확인' },
  { value: 'ACKNOWLEDGED', label: '확인' },
  { value: 'CLOSED', label: '조치 완료' },
  { value: '', label: '전체' },
];

const STATUS_LABEL: Record<AdminSafetyAlertStatus, string> = {
  OPEN: '미확인',
  ACKNOWLEDGED: '확인',
  CLOSED: '조치 완료',
};

/**
 * 신고 누적 안전 알림 목록.
 *
 * 자동 처리는 회원 상태를 바꾸지 않으므로, 관리자는 이 목록에서 회원 관리로 이동해
 * 제재 여부를 직접 판단한다.
 */
export default function AdminSafetyAlertSection() {
  const actions = useAdminSafetyAlerts();
  const { state } = actions;

  return (
    <section
      aria-label="신고 누적 안전 알림"
      className="rounded-2xl bg-white p-4 shadow-sm"
      aria-busy={state.status === 'LOADING'}
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 font-bold text-ink">
          <TriangleAlert size={18} className="text-coral" aria-hidden="true" />
          신고 누적 안전 알림
          {state.openCount > 0 && (
            <span className="rounded-full bg-coral px-2 py-0.5 text-xs font-bold text-white tabular-nums">
              미확인 {state.openCount}
            </span>
          )}
        </h2>
        <label className="text-sm font-semibold text-ink">
          상태
          <select
            aria-label="안전 알림 상태"
            value={state.filter}
            onChange={(event) =>
              void actions.applyFilter(event.target.value as AdminSafetyAlertStatus | '')}
            className="ml-2 rounded-xl border border-line p-2 font-normal"
          >
            {FILTER_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </label>
      </div>

      <div className="sr-only" role="status" aria-live="polite">
        {state.successMessage ?? (state.status === 'LOADING' ? '안전 알림을 불러오는 중입니다.' : '')}
      </div>

      {state.status === 'LOADING' && <LoadingState message="안전 알림을 불러오는 중이에요" />}
      {state.status === 'ERROR' && (
        <div role="alert" className="mt-3 text-center">
          <p className="text-coral">안전 알림을 불러오지 못했습니다.</p>
          <button
            type="button"
            onClick={() => void actions.reload()}
            className="mt-3 rounded-xl border border-line px-4 py-2 font-semibold"
          >
            다시 시도
          </button>
        </div>
      )}
      {state.status === 'READY' && state.alerts.length === 0 && (
        <p className="mt-3 text-center text-sm text-ink/60">해당 상태의 안전 알림이 없습니다.</p>
      )}
      {state.status === 'READY' && state.alerts.length > 0 && (
        <ul className="mt-3 flex flex-col gap-2">
          {state.alerts.map((alert) => (
            <li
              key={alert.alertId}
              className="flex flex-wrap items-center gap-3 rounded-xl border border-line p-3"
            >
              <div className="flex-1">
                <p className="font-bold">
                  {alert.reportedMemberNickname ?? '닉네임 없음'}
                  <span className="ml-2 text-sm font-normal text-ink/60">
                    30일 유효 신고 {alert.validReportCount}건 · {STATUS_LABEL[alert.status]}
                  </span>
                </p>
                <p className="text-sm text-ink/50">
                  {formatSeoulDateTime(alert.createdAt)} · 신고 #{alert.triggerReportId}
                  {alert.handledAt && ` · 처리 ${formatSeoulDateTime(alert.handledAt)}`}
                </p>
              </div>
              {alert.status === 'OPEN' && (
                <button
                  type="button"
                  disabled={state.acknowledgingId === alert.alertId}
                  onClick={() => void actions.acknowledge(alert.alertId)}
                  className="rounded-xl border border-line px-3 py-2 font-semibold disabled:opacity-40"
                >
                  {state.acknowledgingId === alert.alertId ? '처리 중' : '확인'}
                </button>
              )}
              <Link
                to="/admin/members"
                className="rounded-xl bg-ink px-3 py-2 font-semibold text-white"
              >
                회원 관리로 이동
              </Link>
            </li>
          ))}
        </ul>
      )}

      {state.status === 'READY' && (state.pageIndex > 0 || state.hasNext) && (
        <nav aria-label="안전 알림 페이지" className="mt-3 flex items-center justify-center gap-3">
          <button
            type="button"
            disabled={state.pageIndex === 0}
            onClick={() => void actions.previous()}
            className="rounded-xl border border-line p-2 disabled:opacity-40"
            aria-label="이전 안전 알림 페이지"
          >
            <ChevronLeft />
          </button>
          <span className="text-sm font-semibold">{state.pageIndex + 1}페이지</span>
          <button
            type="button"
            disabled={!state.hasNext}
            onClick={() => void actions.next()}
            className="rounded-xl border border-line p-2 disabled:opacity-40"
            aria-label="다음 안전 알림 페이지"
          >
            <ChevronRight />
          </button>
        </nav>
      )}

      {state.actionError && (
        <p role="alert" className="mt-3 text-sm text-coral">알림을 확인 처리하지 못했습니다.</p>
      )}
      {state.successMessage && (
        <p className="mt-3 text-sm text-ink/60">
          {state.successMessage}
          <button type="button" onClick={actions.clearSuccess} className="ml-2 underline">
            닫기
          </button>
        </p>
      )}
    </section>
  );
}
