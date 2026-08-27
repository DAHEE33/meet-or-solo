import { ChevronDown } from 'lucide-react';

// 탐색 화면의 필터 칩. 네이티브 <select>를 칩 모양으로 감싼 형태다.
// 커스텀 드롭다운을 만들지 않은 이유: 모바일에서 OS 기본 선택 UI가 더 쓰기 좋고, 포커스·키보드
// 접근성을 직접 구현하지 않아도 되기 때문이다.

export type FilterSelectOption = { value: string; label: string };

type Props = {
  label: string;
  value: string;
  options: FilterSelectOption[];
  onChange: (value: string) => void;
};

export default function FilterSelect({ label, value, options, onChange }: Props) {
  const selected = options.find((option) => option.value === value);
  const isDefault = value === '' || value === options[0]?.value;

  return (
    <label
      className={`relative flex shrink-0 items-center gap-[3px] rounded-lg border px-2.5 py-[5px] text-xs font-medium ${
        isDefault ? 'border-line bg-sand text-ink/70' : 'border-coral bg-coral/10 text-coral'
      }`}
    >
      <span className="sr-only">{label}</span>
      <span aria-hidden>{selected ? selected.label : label}</span>
      <ChevronDown size={12} aria-hidden className={isDefault ? 'text-ink/45' : 'text-coral'} />
      <select
        aria-label={label}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}
