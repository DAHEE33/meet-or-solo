import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight, MessageSquarePlus } from 'lucide-react';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import { LoadingState } from '../components/common/Spinner';
import {
  inquiriesApi,
  inquiryCategoryLabel,
  inquiryStatusClass,
  inquiryStatusLabel,
  type InquiryListItem,
} from '../api/inquiries';
import { formatSeoulDateTime } from '../utils/dateTime';

type ListState =
  | { status: 'LOADING' }
  | { status: 'ERROR' }
  | { status: 'READY'; items: InquiryListItem[] };

/**
 * 내 1:1 문의 목록.
 *
 * `BlockedMembersPage`의 상태 분기 패턴(loading / 빈 상태 / 오류·재시도)을 따른다.
 * 답변을 밀어줄 채널이 없으므로 `hasUnreadAnswer` 배지가 유일한 도달 신호다(docs/28 2.2).
 */
export default function MyInquiriesPage() {
  const [state, setState] = useState<ListState>({ status: 'LOADING' });

  const load = useCallback((signal?: AbortSignal) => {
    setState({ status: 'LOADING' });
    return inquiriesApi
      .getMine(0, 20, signal)
      .then((page) => {
        if (signal?.aborted) return;
        setState({ status: 'READY', items: page.items });
      })
      .catch(() => {
        if (signal?.aborted) return;
        setState({ status: 'ERROR' });
      });
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);

  return (
    <MobileLayout>
      <PageHeader title="1:1 문의" />
      <main className="flex flex-col gap-3 px-5 pb-10" aria-busy={state.status === 'LOADING'}>
        <p className="text-[13px] leading-5 text-ink/55">
          문의를 남기면 운영팀이 확인 후 답변합니다. 답변은 이 목록에서 확인할 수 있어요.
        </p>

        <Link
          to="/mypage/inquiries/new"
          className="flex items-center justify-center gap-1.5 rounded-2xl bg-coral py-3.5 text-[14px] font-bold text-white active:opacity-90"
        >
          <MessageSquarePlus size={18} aria-hidden="true" />
          문의하기
        </Link>

        <div className="sr-only" role="status" aria-live="polite">
          {state.status === 'LOADING' ? '문의 목록을 불러오는 중입니다.' : ''}
        </div>

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
              onClick={() => void load()}
              className="mt-3 rounded-xl border border-line px-4 py-2 text-sm font-semibold"
            >
              다시 시도
            </button>
          </section>
        )}

        {state.status === 'READY' && state.items.length === 0 && (
          <p className="rounded-2xl bg-white p-5 text-center text-sm text-ink/60">
            등록한 문의가 없어요
          </p>
        )}

        {state.status === 'READY' &&
          state.items.map((item) => (
            <Link
              key={item.inquiryId}
              to={`/mypage/inquiries/${item.inquiryId}`}
              className="flex items-center gap-3 rounded-2xl bg-white p-4 shadow-sm active:scale-[0.99] transition-transform"
            >
              <div className="flex min-w-0 flex-1 flex-col gap-1">
                <div className="flex items-center gap-1.5">
                  <span
                    className={`rounded-md px-[7px] py-0.5 text-[11px] font-bold ${inquiryStatusClass(item.status)}`}
                  >
                    {inquiryStatusLabel(item.status)}
                  </span>
                  {item.hasUnreadAnswer && (
                    <span
                      className="rounded-full bg-coral px-1.5 py-0.5 text-[10px] font-bold text-white"
                      aria-label="확인하지 않은 답변이 있습니다"
                    >
                      NEW
                    </span>
                  )}
                  <span className="text-[11px] text-ink/50">
                    {inquiryCategoryLabel(item.category)}
                  </span>
                </div>
                <h2 className="truncate text-[15px] font-semibold text-ink">{item.title}</h2>
                <time dateTime={item.lastMessageAt} className="text-[12px] text-ink/50">
                  {formatSeoulDateTime(item.lastMessageAt)}
                </time>
              </div>
              <ChevronRight size={16} className="text-ink/30" aria-hidden="true" />
            </Link>
          ))}
      </main>
    </MobileLayout>
  );
}
