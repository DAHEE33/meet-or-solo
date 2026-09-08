import { useEffect, useRef, useState } from 'react';
import { Check, Link2, MessageCircle, X } from 'lucide-react';
import { buildSmsShareUrl, isMobileUserAgent } from '../../utils/share';

interface ShareSheetProps {
  /** 공유할 페이지 제목(축제명/관광지명). 문자 공유 본문에 붙는다. */
  title: string;
  /** 공유할 링크. 보통 현재 페이지 URL(window.location.href). */
  url: string;
  onClose: () => void;
}

/**
 * 커스텀 공유 바텀시트. `navigator.share`(OS 네이티브 공유 시트)는 브라우저마다 모양과
 * 지원 여부가 달라 데스크톱/모바일에서 다르게 보이므로 쓰지 않는다. 대신 이 컴포넌트는
 * 순수 React 모달이라 모바일/데스크톱 어디서 열어도 같은 UI가 뜬다("링크 복사"는
 * 항상 노출). 문자(SMS) 공유는 데스크톱에 SMS 앱이 없어 눌러도 반응이 없으므로
 * 모바일에서만 노출한다.
 */
export default function ShareSheet({ title, url, onClose }: ShareSheetProps) {
  const dialogRef = useRef<HTMLElement>(null);
  const [copied, setCopied] = useState(false);
  const showSmsButton = isMobileUserAgent(navigator.userAgent);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    const focusables = () => Array.from(dialog.querySelectorAll<HTMLElement>('button:not(:disabled),a'));
    focusables()[0]?.focus();
    const keydown = (event: KeyboardEvent) => {
      handleShareSheetKeyDown(event, focusables(), document.activeElement, onClose);
    };
    dialog.addEventListener('keydown', keydown);
    return () => dialog.removeEventListener('keydown', keydown);
  }, [onClose]);

  const handleCopyLink = async () => {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // 클립보드 권한이 없는 등 실패해도 시트를 닫지 않는다. 사용자가 직접 주소창에서
      // 복사할 수 있도록 링크를 그대로 보여준다.
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink/45 sm:items-center sm:p-5">
      <section
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="share-sheet-title"
        className="w-full max-w-[430px] rounded-t-3xl bg-white p-5 sm:rounded-3xl"
      >
        <div className="flex items-center justify-between gap-3">
          <h2 id="share-sheet-title" className="text-lg font-bold text-ink">
            공유하기
          </h2>
          <button type="button" aria-label="닫기" onClick={onClose}>
            <X aria-hidden="true" size={18} />
          </button>
        </div>
        <p className="mt-1 truncate text-sm text-ink/50">{url}</p>
        <div className="mt-5 flex gap-2.5">
          <button
            type="button"
            onClick={handleCopyLink}
            className="flex flex-1 flex-col items-center gap-2 rounded-2xl border border-line py-4 text-sm font-bold text-ink active:bg-sand"
          >
            <span className="flex h-11 w-11 items-center justify-center rounded-full bg-sand text-ink">
              {copied ? <Check size={20} /> : <Link2 size={20} />}
            </span>
            {copied ? '복사됨' : '링크 복사'}
          </button>
          {showSmsButton && (
            <a
              href={buildSmsShareUrl(title, url)}
              className="flex flex-1 flex-col items-center gap-2 rounded-2xl border border-line py-4 text-sm font-bold text-ink active:bg-sand"
            >
              <span className="flex h-11 w-11 items-center justify-center rounded-full bg-sand text-ink">
                <MessageCircle size={20} />
              </span>
              문자 보내기
            </a>
          )}
        </div>
      </section>
    </div>
  );
}

export function handleShareSheetKeyDown(
  event: Pick<KeyboardEvent, 'key' | 'shiftKey' | 'preventDefault'>,
  focusables: HTMLElement[],
  activeElement: Element | null,
  onClose: () => void,
) {
  if (event.key === 'Escape') {
    event.preventDefault();
    onClose();
    return;
  }
  if (event.key !== 'Tab' || focusables.length === 0) return;
  const first = focusables[0];
  const last = focusables[focusables.length - 1];
  if (event.shiftKey && activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}
