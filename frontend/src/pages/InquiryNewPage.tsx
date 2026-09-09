import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Info } from 'lucide-react';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import Spinner from '../components/common/Spinner';
import {
  INQUIRY_BODY_MAX_LENGTH,
  INQUIRY_CATEGORY_OPTIONS,
  INQUIRY_TITLE_MAX_LENGTH,
  inquiriesApi,
  inquiryCategoryLabel,
  type InquiryCategory,
} from '../api/inquiries';
import { ApiClientError } from '../api/apiClient';

/** query의 category가 실제 선택지인지 검증한다. 위조된 값을 그대로 select에 넣지 않는다. */
export function resolveInitialCategory(raw: string | null): InquiryCategory {
  const candidate = INQUIRY_CATEGORY_OPTIONS.find((option) => option === raw);
  return candidate ?? 'ETC';
}

/** 서버 오류 code를 화면 문구로 옮긴다. 문구를 한 곳에 모아 두면 노출 심사가 한 번이면 된다. */
export function inquiryCreateErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError) {
    if (error.code === 'INQUIRY_TOO_MANY_OPEN') {
      return '답변을 기다리는 문의가 많아요. 답변을 받은 뒤 다시 등록해 주세요.';
    }
    if (error.code === 'INQUIRY_INVALID_REQUEST' || error.code === 'VALIDATION_ERROR') {
      return '제목과 내용을 다시 확인해 주세요.';
    }
  }
  return '문의를 등록하지 못했어요. 잠시 후 다시 시도해 주세요.';
}

/**
 * 문의 작성 폼.
 *
 * 본문을 평문으로 저장하므로(docs/28 3.1) 개인정보 입력 자제 안내를 화면에 노출한다.
 * 긴급 여부는 입력받지 않는다 — 관리자만 지정한다(docs/28 확정 5번).
 */
export default function InquiryNewPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  // 제재 안내에서 /mypage/inquiries/new?category=SANCTION_APPEAL로 들어오는 경로가 있다.
  const [category, setCategory] = useState<InquiryCategory>(
    resolveInitialCategory(searchParams.get('category')),
  );
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const canSubmit = title.trim().length > 0 && body.trim().length > 0 && !submitting;

  const handleSubmit = () => {
    if (!canSubmit) return;
    setSubmitting(true);
    setErrorMessage(null);
    inquiriesApi
      .create({ category, title: title.trim(), body: body.trim() })
      .then((created) => navigate(`/mypage/inquiries/${created.inquiryId}`, { replace: true }))
      .catch((error: unknown) => {
        setErrorMessage(inquiryCreateErrorMessage(error));
        setSubmitting(false);
      });
  };

  return (
    <MobileLayout showTabBar={false}>
      <PageHeader title="문의하기" />
      <main className="flex flex-col gap-4 px-5 pb-10">
        <section className="flex gap-2 rounded-2xl bg-sand/70 p-3.5">
          <Info size={16} className="mt-0.5 shrink-0 text-ink/45" aria-hidden="true" />
          <div className="flex flex-col gap-1 text-[12px] leading-5 text-ink/65">
            <p>전화번호, 주소 같은 개인정보는 적지 말아 주세요.</p>
            <p>동행 중 문제는 문의가 아니라 매칭 기록의 신고 기능을 이용해 주세요.</p>
          </div>
        </section>

        <label className="flex flex-col gap-1.5">
          <span className="text-[13px] font-semibold text-ink">문의 유형</span>
          <select
            value={category}
            onChange={(event) => setCategory(event.target.value as InquiryCategory)}
            className="rounded-xl border border-line bg-white px-3 py-3 text-[14px] text-ink"
          >
            {INQUIRY_CATEGORY_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {inquiryCategoryLabel(option)}
              </option>
            ))}
          </select>
        </label>

        <label className="flex flex-col gap-1.5">
          <span className="text-[13px] font-semibold text-ink">제목</span>
          <input
            type="text"
            value={title}
            maxLength={INQUIRY_TITLE_MAX_LENGTH}
            onChange={(event) => setTitle(event.target.value)}
            placeholder="무엇을 도와드릴까요?"
            className="rounded-xl border border-line bg-white px-3 py-3 text-[14px] text-ink placeholder:text-ink/35"
          />
          <span className="self-end text-[11px] text-ink/40 tabular-nums">
            {title.length}/{INQUIRY_TITLE_MAX_LENGTH}
          </span>
        </label>

        <label className="flex flex-col gap-1.5">
          <span className="text-[13px] font-semibold text-ink">내용</span>
          <textarea
            value={body}
            maxLength={INQUIRY_BODY_MAX_LENGTH}
            onChange={(event) => setBody(event.target.value)}
            rows={9}
            placeholder="상황을 자세히 알려주시면 더 빠르게 확인할 수 있어요."
            className="resize-none rounded-xl border border-line bg-white px-3 py-3 text-[14px] leading-6 text-ink placeholder:text-ink/35"
          />
          <span className="self-end text-[11px] text-ink/40 tabular-nums">
            {body.length}/{INQUIRY_BODY_MAX_LENGTH}
          </span>
        </label>

        {errorMessage && (
          <p role="alert" className="rounded-2xl bg-coral/10 p-3 text-[13px] text-coral">
            {errorMessage}
          </p>
        )}

        <button
          type="button"
          onClick={handleSubmit}
          disabled={!canSubmit}
          className="flex items-center justify-center gap-2 rounded-2xl bg-coral py-3.5 text-[14px] font-bold text-white disabled:opacity-50"
        >
          {submitting && <Spinner size="sm" tone="white" />}
          {submitting ? '등록 중...' : '문의 등록'}
        </button>
      </main>
    </MobileLayout>
  );
}
