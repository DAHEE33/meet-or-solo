import { useCallback, useEffect, useState } from 'react';
import AdminHeader from '../components/admin/AdminHeader';
import AdminNav from '../components/admin/AdminNav';
import { LoadingState } from '../components/common/Spinner';
import Spinner from '../components/common/Spinner';
import {
  EMPTY_ADMIN_INQUIRY_FILTERS,
  adminInquiriesApi,
  type AdminInquiryDetail,
  type AdminInquiryFilters,
  type AdminInquiryListItem,
  type AdminInquiryTargetStatus,
  type InquiryPriority,
} from '../api/adminInquiries';
import {
  INQUIRY_BODY_MAX_LENGTH,
  INQUIRY_CATEGORY_OPTIONS,
  inquiryCategoryLabel,
  inquiryStatusClass,
  inquiryStatusLabel,
  type InquiryCategory,
  type InquiryStatus,
} from '../api/inquiries';
import { formatSeoulDateTime } from '../utils/dateTime';

const STATUS_OPTIONS: Array<{ value: InquiryStatus | ''; label: string }> = [
  { value: '', label: '전체' },
  { value: 'RECEIVED', label: '접수' },
  { value: 'IN_PROGRESS', label: '확인 중' },
  { value: 'ANSWERED', label: '답변 완료' },
  { value: 'CLOSED', label: '종결' },
];

const PRIORITY_OPTIONS: Array<{ value: InquiryPriority | ''; label: string }> = [
  { value: '', label: '전체' },
  { value: 'URGENT', label: '긴급' },
  { value: 'NORMAL', label: '보통' },
];

/** `AdminReportsPage.toSeoulOffset`과 같은 규칙. datetime-local 값에 KST offset을 붙인다. */
export function toSeoulOffset(value: string): string {
  return value ? `${value}:00+09:00` : '';
}

export function toApiFilters(filters: AdminInquiryFilters): AdminInquiryFilters {
  return {
    ...filters,
    createdFrom: toSeoulOffset(filters.createdFrom),
    createdTo: toSeoulOffset(filters.createdTo),
  };
}

type ListState =
  | { status: 'LOADING' }
  | { status: 'ERROR' }
  | { status: 'READY'; items: AdminInquiryListItem[]; openCount: number };

/**
 * 관리자 1:1 문의 관리. `AdminReportsPage` 구조(필터 + 목록 + 상세 패널)를 따른다.
 *
 * 긴급 우선 정렬은 제공하지 않는다 — 정렬 키와 cursor 키가 어긋나면 페이지 경계에서 항목이
 * 중복·누락된다. 대신 긴급 filter를 둔다(docs/29 5.7).
 */
export default function AdminInquiriesPage() {
  const [draft, setDraft] = useState(EMPTY_ADMIN_INQUIRY_FILTERS);
  const [applied, setApplied] = useState(EMPTY_ADMIN_INQUIRY_FILTERS);
  const [state, setState] = useState<ListState>({ status: 'LOADING' });
  const [selected, setSelected] = useState<AdminInquiryDetail | null>(null);

  const load = useCallback((filters: AdminInquiryFilters, signal?: AbortSignal) => {
    setState({ status: 'LOADING' });
    return adminInquiriesApi
      .list(toApiFilters(filters), null, 20, signal)
      .then((page) => {
        if (signal?.aborted) return;
        setState({ status: 'READY', items: page.items, openCount: page.openCount });
      })
      .catch(() => {
        if (signal?.aborted) return;
        setState({ status: 'ERROR' });
      });
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void load(applied, controller.signal);
    return () => controller.abort();
  }, [applied, load]);

  const refresh = () => setApplied({ ...applied });

  return (
    <div className="min-h-screen bg-sand">
      <AdminHeader title="관리자 문의 관리" />
      <AdminNav />
      <main
        className="mx-auto flex max-w-6xl flex-col gap-4 px-6 py-6"
        aria-busy={state.status === 'LOADING'}
      >
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-lg font-bold text-ink">1:1 문의</h2>
          {state.status === 'READY' && (
            <span className="text-sm text-ink/55 tabular-nums">
              미처리 {state.openCount}건
            </span>
          )}
        </div>

        <section className="grid grid-cols-1 gap-3 rounded-2xl bg-white p-4 sm:grid-cols-5">
          <label className="text-sm font-semibold text-ink">
            상태
            <select
              aria-label="문의 상태"
              value={draft.status}
              onChange={(event) =>
                setDraft({ ...draft, status: event.target.value as InquiryStatus | '' })
              }
              className="mt-1 block w-full rounded-xl border border-line p-2 font-normal"
            >
              {STATUS_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <label className="text-sm font-semibold text-ink">
            유형
            <select
              aria-label="문의 유형"
              value={draft.category}
              onChange={(event) =>
                setDraft({ ...draft, category: event.target.value as InquiryCategory | '' })
              }
              className="mt-1 block w-full rounded-xl border border-line p-2 font-normal"
            >
              <option value="">전체</option>
              {INQUIRY_CATEGORY_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {inquiryCategoryLabel(option)}
                </option>
              ))}
            </select>
          </label>
          <label className="text-sm font-semibold text-ink">
            우선순위
            <select
              aria-label="문의 우선순위"
              value={draft.priority}
              onChange={(event) =>
                setDraft({ ...draft, priority: event.target.value as InquiryPriority | '' })
              }
              className="mt-1 block w-full rounded-xl border border-line p-2 font-normal"
            >
              {PRIORITY_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <label className="text-sm font-semibold text-ink">
            등록 시작
            <input
              type="datetime-local"
              aria-label="등록 시작"
              value={draft.createdFrom}
              onChange={(event) => setDraft({ ...draft, createdFrom: event.target.value })}
              className="mt-1 block w-full rounded-xl border border-line p-2 font-normal"
            />
          </label>
          <label className="text-sm font-semibold text-ink">
            등록 종료
            <input
              type="datetime-local"
              aria-label="등록 종료"
              value={draft.createdTo}
              onChange={(event) => setDraft({ ...draft, createdTo: event.target.value })}
              className="mt-1 block w-full rounded-xl border border-line p-2 font-normal"
            />
          </label>
          <div className="flex items-end gap-2 sm:col-span-5">
            <button
              type="button"
              onClick={() => setApplied(draft)}
              className="rounded-xl bg-ink px-4 py-2 text-sm font-semibold text-white"
            >
              검색
            </button>
            <button
              type="button"
              onClick={() => {
                setDraft(EMPTY_ADMIN_INQUIRY_FILTERS);
                setApplied(EMPTY_ADMIN_INQUIRY_FILTERS);
              }}
              className="rounded-xl border border-line px-4 py-2 text-sm font-semibold text-ink/70"
            >
              초기화
            </button>
          </div>
        </section>

        {state.status === 'LOADING' && (
          <div className="rounded-2xl bg-white p-5">
            <LoadingState className="py-2" message="문의를 불러오는 중이에요" />
          </div>
        )}

        {state.status === 'ERROR' && (
          <section role="alert" className="rounded-2xl bg-white p-5">
            <p className="text-sm text-coral">문의 목록을 불러오지 못했어요.</p>
            <button
              type="button"
              onClick={() => void load(applied)}
              className="mt-3 rounded-xl border border-line px-4 py-2 text-sm font-semibold"
            >
              다시 시도
            </button>
          </section>
        )}

        {state.status === 'READY' && state.items.length === 0 && (
          <p className="rounded-2xl bg-white p-8 text-center text-sm text-ink/60">
            조건에 맞는 문의가 없어요
          </p>
        )}

        {state.status === 'READY' &&
          state.items.map((item) => (
            <article
              key={item.inquiryId}
              className="flex flex-col gap-2 rounded-2xl bg-white p-4 sm:flex-row sm:items-center"
            >
              <div className="flex min-w-0 flex-1 flex-col gap-1">
                <div className="flex flex-wrap items-center gap-1.5">
                  <span
                    className={`rounded-md px-[7px] py-0.5 text-[11px] font-bold ${inquiryStatusClass(item.status)}`}
                  >
                    {inquiryStatusLabel(item.status)}
                  </span>
                  {item.priority === 'URGENT' && (
                    <span className="rounded-md bg-coral px-[7px] py-0.5 text-[11px] font-bold text-white">
                      긴급
                    </span>
                  )}
                  <span className="text-[11px] text-ink/55">
                    {inquiryCategoryLabel(item.category)}
                  </span>
                  <span className="text-[11px] text-ink/40">·</span>
                  <span className="text-[11px] text-ink/55">
                    {item.member.nickname} ({item.member.status})
                  </span>
                </div>
                <h2 className="truncate text-[15px] font-semibold text-ink">{item.title}</h2>
                <span className="text-[12px] text-ink/50 tabular-nums">
                  {formatSeoulDateTime(item.createdAt)} 등록 · 메시지 {item.messageCount}건
                </span>
              </div>
              <button
                type="button"
                onClick={() => {
                  void adminInquiriesApi.detail(item.inquiryId).then(setSelected);
                }}
                className="shrink-0 rounded-xl border border-line px-4 py-2 text-sm font-semibold text-ink/70"
              >
                열기
              </button>
            </article>
          ))}
      </main>
      {selected && (
        <AdminInquiryPanel
          detail={selected}
          onClose={() => setSelected(null)}
          onChanged={(next) => {
            setSelected(next);
            refresh();
          }}
        />
      )}
    </div>
  );
}

/** 상세 + 답변 패널. `AdminReportDetailDialog`와 같은 역할이다. */
export function AdminInquiryPanel({
  detail,
  onClose,
  onChanged,
}: {
  detail: AdminInquiryDetail;
  onClose: () => void;
  onChanged: (next: AdminInquiryDetail) => void;
}) {
  const [answer, setAnswer] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const closed = detail.status === 'CLOSED';

  const run = (task: Promise<AdminInquiryDetail>) => {
    setSubmitting(true);
    setErrorMessage(null);
    task
      .then(onChanged)
      .catch(() => setErrorMessage('처리하지 못했어요. 잠시 후 다시 시도해 주세요.'))
      .finally(() => setSubmitting(false));
  };

  const update = (input: { status?: AdminInquiryTargetStatus; priority?: InquiryPriority }) =>
    run(adminInquiriesApi.update(detail.inquiryId, input));

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 p-5">
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="admin-inquiry-title"
        className="flex max-h-[85vh] w-full max-w-[640px] flex-col gap-4 overflow-y-auto rounded-3xl bg-white p-6"
      >
        <div className="flex items-start justify-between gap-3">
          <div className="flex min-w-0 flex-col gap-1">
            <h2 id="admin-inquiry-title" className="text-lg font-bold text-ink">
              {detail.title}
            </h2>
            <p className="text-[12px] text-ink/55">
              {inquiryCategoryLabel(detail.category)} · {detail.member.nickname} (
              {detail.member.status}) · {formatSeoulDateTime(detail.createdAt)}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="shrink-0 rounded-xl border border-line px-3 py-2 text-sm font-semibold"
          >
            닫기
          </button>
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            disabled={submitting || detail.priority === 'URGENT'}
            onClick={() => update({ priority: 'URGENT' })}
            className="rounded-xl border border-line px-3 py-2 text-[13px] font-semibold disabled:opacity-40"
          >
            긴급으로 표시
          </button>
          <button
            type="button"
            disabled={submitting || detail.priority === 'NORMAL'}
            onClick={() => update({ priority: 'NORMAL' })}
            className="rounded-xl border border-line px-3 py-2 text-[13px] font-semibold disabled:opacity-40"
          >
            긴급 해제
          </button>
          <button
            type="button"
            disabled={submitting || detail.status !== 'RECEIVED'}
            onClick={() => update({ status: 'IN_PROGRESS' })}
            className="rounded-xl border border-line px-3 py-2 text-[13px] font-semibold disabled:opacity-40"
          >
            확인 중으로
          </button>
          <button
            type="button"
            disabled={submitting || closed}
            onClick={() => update({ status: 'CLOSED' })}
            className="rounded-xl border border-line px-3 py-2 text-[13px] font-semibold disabled:opacity-40"
          >
            종결
          </button>
        </div>

        <section className="flex flex-col gap-2.5" aria-label="문의 스레드">
          {detail.messages.map((message) => {
            const fromAdmin = message.authorType === 'ADMIN';
            return (
              <article
                key={message.messageId}
                className={`flex max-w-[88%] flex-col gap-1 rounded-2xl px-4 py-3 ${
                  fromAdmin ? 'self-end bg-teal/[0.08]' : 'self-start bg-sand'
                }`}
              >
                <span className="text-[11px] font-bold text-ink/50">
                  {fromAdmin ? '운영팀' : detail.member.nickname}
                </span>
                <p className="whitespace-pre-wrap text-[14px] leading-6 text-ink">
                  {message.body}
                </p>
                <time dateTime={message.createdAt} className="text-[11px] text-ink/40">
                  {formatSeoulDateTime(message.createdAt)}
                </time>
              </article>
            );
          })}
        </section>

        {closed ? (
          <p className="rounded-2xl bg-sand p-3.5 text-[13px] text-ink/60">
            종결된 문의에는 답변을 남길 수 없어요.
          </p>
        ) : (
          <section className="flex flex-col gap-2">
            <label className="flex flex-col gap-1.5">
              <span className="text-[13px] font-semibold text-ink">답변</span>
              <textarea
                value={answer}
                maxLength={INQUIRY_BODY_MAX_LENGTH}
                onChange={(event) => setAnswer(event.target.value)}
                rows={5}
                className="resize-none rounded-xl border border-line px-3 py-3 text-[14px] leading-6"
              />
            </label>
            {errorMessage && (
              <p role="alert" className="rounded-2xl bg-coral/10 p-3 text-[13px] text-coral">
                {errorMessage}
              </p>
            )}
            <button
              type="button"
              disabled={submitting || answer.trim().length === 0}
              onClick={() => {
                run(adminInquiriesApi.answer(detail.inquiryId, answer.trim()));
                setAnswer('');
              }}
              className="flex items-center justify-center gap-2 rounded-2xl bg-coral py-3 text-[14px] font-bold text-white disabled:opacity-50"
            >
              {submitting && <Spinner size="sm" tone="white" />}
              {submitting ? '처리 중...' : '답변 등록'}
            </button>
          </section>
        )}
      </section>
    </div>
  );
}
