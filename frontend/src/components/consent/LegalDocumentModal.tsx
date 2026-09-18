import { X } from 'lucide-react';
import type { LegalDocument } from './legalDocuments';

interface LegalDocumentModalProps {
  document: LegalDocument;
  /** 닫기(X)와 확인 버튼의 기본 동작. 열람만 하고 닫는 경로다. */
  onClose: () => void;
  /**
   * 확인 버튼을 눌렀을 때의 동작. 넘기지 않으면 `onClose`와 같다.
   *
   * 동의 체크박스를 눌러서 연 경우에는 "확인"이 곧 동의로 이어지므로, 그 처리를 호출부가
   * 맡을 수 있게 분리했다.
   */
  onConfirm?: () => void;
  /** 확인 버튼 문구. 동의로 이어지는 경우와 열람만 하는 경우를 구분해 보여준다. */
  confirmLabel?: string;
}

/**
 * 약관·개인정보처리방침 전문을 띄우는 모달.
 *
 * 페이지 이동이 아니라 모달인 이유가 있다. 이 문서를 여는 곳은 회원가입 마지막 단계이고,
 * 그 화면에는 닉네임·성별·연령대·여행 스타일·취향 글이 이미 입력돼 있다. 다른 페이지로
 * 보내면 그 입력이 전부 사라진다.
 */
export default function LegalDocumentModal({
  document,
  onClose,
  onConfirm,
  confirmLabel = '확인했어요',
}: LegalDocumentModalProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/60 p-5">
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="legal-document-title"
        className="flex max-h-[85vh] w-full max-w-md flex-col rounded-3xl bg-white"
      >
        <header className="flex items-start justify-between gap-3 border-b border-line px-6 py-5">
          <div className="flex flex-col gap-1">
            <h2 id="legal-document-title" className="text-[16px] font-bold text-ink">
              {document.title}
            </h2>
            <p className="text-[12px] text-ink/45">
              버전 {document.version} · 시행일 {document.effectiveDate}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="shrink-0 rounded-full p-1 text-ink/50 active:text-coral"
          >
            <X aria-hidden className="h-5 w-5" />
          </button>
        </header>

        <div className="flex flex-col gap-5 overflow-y-auto px-6 py-5">
          {document.sections.map((section) => (
            <article key={section.heading} className="flex flex-col gap-2">
              <h3 className="text-[13px] font-bold text-ink">{section.heading}</h3>
              {section.paragraphs.map((paragraph) => (
                <p key={paragraph} className="text-[13px] leading-6 text-ink/70">
                  {paragraph}
                </p>
              ))}
            </article>
          ))}
        </div>

        <footer className="border-t border-line px-6 py-4">
          <button
            type="button"
            onClick={onConfirm ?? onClose}
            className="w-full rounded-2xl bg-ink py-3.5 text-[14px] font-bold text-white"
          >
            {confirmLabel}
          </button>
        </footer>
      </section>
    </div>
  );
}
