import { X } from 'lucide-react';

type Props = {
  startDate: string;
  endDate: string;
  onChange: (startDate: string, endDate: string) => void;
};

/**
 * 축제 목록의 기간 선택. "전체 기간 / 진행 중 / 이번 주말 / 이번 달" 프리셋을 대체한다.
 *
 * 브라우저 기본 `<input type="date">`를 그대로 쓴다 — 달력을 직접 그리면 키보드 조작·연도
 * 이동·모바일 네이티브 피커를 전부 다시 만들어야 하고, `FilterSelect`가 네이티브 `<select>`를
 * 쓰기로 한 것과 같은 이유다.
 *
 * 시작일과 종료일은 각각 비워둘 수 있다. 한쪽만 넣으면 "그 날짜 이후에 끝나는 축제" 또는
 * "그 날짜 이전에 시작하는 축제"가 된다.
 */
export default function DateRangeFilter({ startDate, endDate, onChange }: Props) {
  const selected = Boolean(startDate || endDate);

  return (
    <div
      className={`flex shrink-0 items-center gap-1 rounded-full border py-1 pl-2.5 pr-1.5 text-[13px] font-medium ${
        selected ? 'border-coral bg-coral/10 text-coral' : 'border-line bg-white text-ink/70'
      }`}
    >
      <input
        type="date"
        aria-label="기간 시작일"
        value={startDate}
        // 종료일보다 뒤를 고르면 결과가 항상 비므로 브라우저가 먼저 막는다.
        max={endDate || undefined}
        onChange={(event) => onChange(event.target.value, endDate)}
        className="w-[105px] bg-transparent text-center tabular-nums focus:outline-none"
      />
      <span aria-hidden className={selected ? 'text-coral/60' : 'text-ink/35'}>
        –
      </span>
      <input
        type="date"
        aria-label="기간 종료일"
        value={endDate}
        min={startDate || undefined}
        onChange={(event) => onChange(startDate, event.target.value)}
        className="w-[105px] bg-transparent text-center tabular-nums focus:outline-none"
      />
      {selected && (
        <button
          type="button"
          aria-label="기간 선택 해제"
          onClick={() => onChange('', '')}
          className="rounded-full p-0.5 text-coral active:bg-coral/20"
        >
          <X size={13} />
        </button>
      )}
    </div>
  );
}
