interface ChipProps {
  label: string;
  selected: boolean;
  onClick: () => void;
}

/** 선택형 칩 (여행 스타일, 테마, 필터 등) */
export default function Chip({ label, selected, onClick }: ChipProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-full border px-4 py-2 text-[13px] font-medium transition-colors ${
        selected
          ? 'border-coral bg-coral text-white'
          : 'border-line bg-white text-ink/60 active:bg-sand'
      }`}
    >
      {label}
    </button>
  );
}
