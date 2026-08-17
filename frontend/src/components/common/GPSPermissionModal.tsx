import { useEffect, useRef } from 'react';
import { MapPin, X } from 'lucide-react';

interface GPSPermissionModalProps {
  onConfirm: () => void;
  onCancel: () => void;
}

/** GPS 체크인 전 위치 권한이 왜 필요한지 안내하는 modal(docs/03_FRONTEND_GUIDE.md 컴포넌트 목록). */
export default function GPSPermissionModal({ onConfirm, onCancel }: GPSPermissionModalProps) {
  const dialogRef = useRef<HTMLElement>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    const focusables = () => Array.from(dialog.querySelectorAll<HTMLElement>('button:not(:disabled)'));
    focusables()[0]?.focus();
    const keydown = (event: KeyboardEvent) => {
      handleGPSPermissionModalKeyDown(event, focusables(), document.activeElement, onCancel);
    };
    dialog.addEventListener('keydown', keydown);
    return () => dialog.removeEventListener('keydown', keydown);
  }, [onCancel]);

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink/45 sm:items-center sm:p-5">
      <section
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="gps-permission-title"
        aria-describedby="gps-permission-description"
        className="w-full max-w-[430px] rounded-t-3xl bg-white p-5 sm:rounded-3xl"
      >
        <div className="flex items-start justify-between gap-3">
          <div className="flex flex-col gap-1">
            <span className="flex h-10 w-10 items-center justify-center rounded-full bg-coral/10 text-coral">
              <MapPin size={20} />
            </span>
            <h2 id="gps-permission-title" className="mt-2 text-lg font-bold text-ink">
              위치 정보가 필요해요
            </h2>
            <p id="gps-permission-description" className="text-sm leading-6 text-ink/60">
              축제 현장 안에 있는지 확인하기 위해 현재 위치를 1회만 확인해요. 위치는 거리 계산에만
              쓰이고 저장되지 않아요.
            </p>
          </div>
          <button type="button" aria-label="닫기" onClick={onCancel}>
            <X aria-hidden="true" size={18} />
          </button>
        </div>
        <div className="mt-5 flex gap-2.5">
          <button
            type="button"
            onClick={onCancel}
            className="flex-1 rounded-2xl border border-line bg-white py-3 text-[15px] font-bold text-ink/55 active:bg-sand"
          >
            취소
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="flex-1 rounded-2xl bg-coral py-3 text-[15px] font-bold text-white active:opacity-90"
          >
            위치 확인하기
          </button>
        </div>
      </section>
    </div>
  );
}

export function handleGPSPermissionModalKeyDown(
  event: KeyboardEvent,
  focusables: HTMLElement[],
  activeElement: Element | null,
  onCancel: () => void,
) {
  if (event.key === 'Escape') {
    event.preventDefault();
    onCancel();
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
