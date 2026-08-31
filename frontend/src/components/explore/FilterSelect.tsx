import { useEffect, useRef, useState } from 'react';
import { Check, ChevronDown } from 'lucide-react';

// 탐색 화면의 필터 칩.
//
// 처음에는 네이티브 <select>를 투명하게 덮어 썼는데, 열었을 때 뜨는 목록이 OS 기본 스타일이라
// 앱 디자인(둥근 모서리, sand/coral 팔레트)과 따로 놀았다. 그래서 목록을 앱이 직접 그리는
// 바텀시트로 바꿨다 — 모바일 PWA에서 손이 닿기 쉬운 아래쪽에 뜨고, 선택지가 많은 지역 목록도
// 스크롤로 편하게 볼 수 있다.
//
// 접근성은 GPSPermissionModal과 같은 방식(Escape 닫기 + 바깥 클릭 닫기 + 열릴 때 포커스 이동)을 따른다.

export type FilterSelectOption = { value: string; label: string };

type Props = {
  label: string;
  value: string;
  options: FilterSelectOption[];
  onChange: (value: string) => void;
};

export default function FilterSelect({ label, value, options, onChange }: Props) {
  const [open, setOpen] = useState(false);
  const panelRef = useRef<HTMLDivElement | null>(null);
  const selected = options.find((option) => option.value === value);
  // 첫 번째 선택지를 "기본값"으로 본다(전체 지역 / 전체 기간 / 기본 정렬).
  const isDefault = value === (options[0]?.value ?? '');

  useEffect(() => {
    if (!open) return;
    panelRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open]);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-haspopup="listbox"
        aria-expanded={open}
        className={`flex shrink-0 items-center gap-1 rounded-full border px-3 py-1.5 text-[13px] font-medium transition-colors ${
          isDefault
            ? 'border-line bg-white text-ink/70'
            : 'border-coral bg-coral/10 text-coral'
        }`}
      >
        {selected?.label ?? label}
        <ChevronDown size={13} className={isDefault ? 'text-ink/40' : 'text-coral'} />
      </button>

      {open && (
        <div
          className="fixed inset-0 z-50 flex items-end justify-center bg-ink/40"
          onClick={() => setOpen(false)}
        >
          <div
            ref={panelRef}
            tabIndex={-1}
            role="listbox"
            aria-label={label}
            onClick={(event) => event.stopPropagation()}
            className="max-h-[70vh] w-full max-w-[430px] overflow-y-auto rounded-t-3xl bg-white pb-[env(safe-area-inset-bottom)] outline-none"
          >
            {/* 바텀시트 손잡이 — 아래로 열린 패널이라는 신호 */}
            <div className="sticky top-0 flex flex-col items-center gap-3 bg-white pb-2 pt-3">
              <span aria-hidden className="h-1 w-10 rounded-full bg-line" />
              <h2 className="text-[15px] font-bold text-ink">{label}</h2>
            </div>
            <ul className="flex flex-col px-2 pb-3">
              {options.map((option) => {
                const isSelected = option.value === value;
                return (
                  <li key={option.value}>
                    <button
                      type="button"
                      role="option"
                      aria-selected={isSelected}
                      onClick={() => {
                        onChange(option.value);
                        setOpen(false);
                      }}
                      className={`flex w-full items-center justify-between rounded-2xl px-4 py-3 text-left text-[15px] ${
                        isSelected ? 'bg-coral/10 font-bold text-coral' : 'text-ink/80'
                      }`}
                    >
                      {option.label}
                      {isSelected && <Check size={16} />}
                    </button>
                  </li>
                );
              })}
            </ul>
          </div>
        </div>
      )}
    </>
  );
}
