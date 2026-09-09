import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import Spinner, { LoadingState } from '../components/common/Spinner';
import {
  INQUIRY_BODY_MAX_LENGTH,
  inquiriesApi,
  inquiryCategoryLabel,
  inquiryStatusClass,
  inquiryStatusLabel,
  type InquiryDetail,
} from '../api/inquiries';
import { ApiClientError } from '../api/apiClient';
import { formatSeoulDateTime } from '../utils/dateTime';

type DetailState =
  | { status: 'LOADING' }
  | { status: 'ERROR' }
  | { status: 'NOT_FOUND' }
  | { status: 'READY'; detail: InquiryDetail };

/**
 * 경로 파라미터를 문의 id로 해석한다. 숫자가 아니면 `null`이고 화면은 "찾을 수 없음"을 그린다.
 * 순수 함수로 분리한 이유는 이 저장소 vitest에 jsdom이 없어 렌더링 없이 검증해야 하기 때문이다.
 */
export function resolveInquiryId(raw: string | undefined): number | null {
  if (raw === undefined || raw.trim() === '') return null;
  const id = Number(raw);
  return Number.isInteger(id) && id > 0 ? id : null;
}

/** 추가 질문 실패 문구. 종결된 문의는 원인이 분명하므로 따로 안내한다. */
export function inquiryReplyErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError && error.code === 'INQUIRY_CLOSED') {
    return '종결된 문의예요. 새 문의로 등록해 주세요.';
  }
  return '메시지를 남기지 못했어요. 잠시 후 다시 시도해 주세요.';
}

/**
 * 문의 스레드.
 *
 * 이 화면을 여는 것만으로 서버가 열람 시각을 갱신해 미확인 배지가 꺼진다(docs/29 5.3).
 * 관리자 발화는 작성자를 특정하지 않고 "운영팀"으로만 표시한다(docs/29 7절).
 */
export default function InquiryDetailPage() {
  const { inquiryId } = useParams<{ inquiryId: string }>();
  const [state, setState] = useState<DetailState>({ status: 'LOADING' });
  const [reply, setReply] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const load = useCallback(
    (signal?: AbortSignal) => {
      const id = resolveInquiryId(inquiryId);
      if (id === null) {
        setState({ status: 'NOT_FOUND' });
        return Promise.resolve();
      }
      setState({ status: 'LOADING' });
      return inquiriesApi
        .getDetail(id, signal)
        .then((detail) => {
          if (signal?.aborted) return;
          setState({ status: 'READY', detail });
        })
        .catch((error: unknown) => {
          if (signal?.aborted) return;
          const notFound =
            error instanceof ApiClientError &&
            (error.code === 'INQUIRY_NOT_FOUND' || error.code === 'INQUIRY_FORBIDDEN');
          setState({ status: notFound ? 'NOT_FOUND' : 'ERROR' });
        });
    },
    [inquiryId],
  );

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);

  const handleReply = () => {
    if (state.status !== 'READY' || submitting || reply.trim().length === 0) return;
    setSubmitting(true);
    setErrorMessage(null);
    inquiriesApi
      .addMessage(state.detail.inquiryId, reply.trim())
      .then((detail) => {
        setState({ status: 'READY', detail });
        setReply('');
      })
      .catch((error: unknown) => setErrorMessage(inquiryReplyErrorMessage(error)))
      .finally(() => setSubmitting(false));
  };

  return (
    <MobileLayout showTabBar={false}>
      <PageHeader title="문의 상세" />
      <main className="flex flex-col gap-4 px-5 pb-10" aria-busy={state.status === 'LOADING'}>
        {state.status === 'LOADING' && (
          <div className="rounded-2xl bg-white p-5">
            <LoadingState className="py-2" message="문의를 불러오는 중이에요" />
          </div>
        )}

        {state.status === 'ERROR' && (
          <section role="alert" className="rounded-2xl bg-white p-5">
            <p className="text-sm text-coral">문의를 불러오지 못했어요.</p>
            <button
              type="button"
              onClick={() => void load()}
              className="mt-3 rounded-xl border border-line px-4 py-2 text-sm font-semibold"
            >
              다시 시도
            </button>
          </section>
        )}

        {state.status === 'NOT_FOUND' && (
          <p className="py-16 text-center text-[14px] text-ink/45">
            문의를 찾을 수 없어요.{' '}
            <Link to="/mypage/inquiries" className="text-coral">
              목록으로
            </Link>
          </p>
        )}

        {state.status === 'READY' && (
          <>
            <section className="flex flex-col gap-2">
              <div className="flex items-center gap-1.5">
                <span
                  className={`rounded-md px-[7px] py-0.5 text-[11px] font-bold ${inquiryStatusClass(state.detail.status)}`}
                >
                  {inquiryStatusLabel(state.detail.status)}
                </span>
                <span className="text-[11px] text-ink/50">
                  {inquiryCategoryLabel(state.detail.category)}
                </span>
              </div>
              <h2 className="text-[18px] font-bold text-ink">{state.detail.title}</h2>
              <time dateTime={state.detail.createdAt} className="text-[12px] text-ink/50">
                {formatSeoulDateTime(state.detail.createdAt)} 등록
              </time>
            </section>

            <section className="flex flex-col gap-2.5" aria-label="문의 내용">
              {state.detail.messages.map((message) => {
                const fromAdmin = message.authorType === 'ADMIN';
                return (
                  <article
                    key={message.messageId}
                    className={`flex max-w-[88%] flex-col gap-1 rounded-2xl px-4 py-3 ${
                      fromAdmin ? 'self-start bg-white shadow-sm' : 'self-end bg-coral/[0.08]'
                    }`}
                  >
                    <span className="text-[11px] font-bold text-ink/50">
                      {fromAdmin ? '운영팀' : '내 문의'}
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

            {state.detail.status === 'CLOSED' ? (
              <p className="rounded-2xl bg-sand/70 p-3.5 text-[13px] leading-5 text-ink/60">
                종결된 문의예요. 더 문의할 내용이 있으면{' '}
                <Link to="/mypage/inquiries/new" className="font-semibold text-coral">
                  새 문의
                </Link>
                를 등록해 주세요.
              </p>
            ) : (
              <section className="flex flex-col gap-2">
                <label className="flex flex-col gap-1.5">
                  <span className="text-[13px] font-semibold text-ink">추가 문의</span>
                  <textarea
                    value={reply}
                    maxLength={INQUIRY_BODY_MAX_LENGTH}
                    onChange={(event) => setReply(event.target.value)}
                    rows={4}
                    placeholder="더 알려주실 내용이 있으면 남겨주세요."
                    className="resize-none rounded-xl border border-line bg-white px-3 py-3 text-[14px] leading-6 text-ink placeholder:text-ink/35"
                  />
                </label>
                {errorMessage && (
                  <p role="alert" className="rounded-2xl bg-coral/10 p-3 text-[13px] text-coral">
                    {errorMessage}
                  </p>
                )}
                <button
                  type="button"
                  onClick={handleReply}
                  disabled={submitting || reply.trim().length === 0}
                  className="flex items-center justify-center gap-2 rounded-2xl bg-coral py-3.5 text-[14px] font-bold text-white disabled:opacity-50"
                >
                  {submitting && <Spinner size="sm" tone="white" />}
                  {submitting ? '등록 중...' : '보내기'}
                </button>
              </section>
            )}
          </>
        )}
      </main>
    </MobileLayout>
  );
}
