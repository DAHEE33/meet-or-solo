import { ChevronDown } from 'lucide-react';

// 탐색 화면의 필터 칩.
//
// 목록은 브라우저/OS 기본 <select> 드롭다운을 그대로 쓴다. 앱이 직접 그리는 바텀시트로
// 만들어 봤지만, 필터를 바꾸려는데 화면 아래에서 시트가 올라오는 흐름이 조회 동작치고
// 무거웠다. 대신 닫힌 상태의 컨트롤만 appearance-none으로 초기화하고 앱 디자인
// (둥근 모서리, sand/coral 팔레트, 카테고리 칩과 같은 13px)에 맞춰 다시 그린다.
//
// 이렇게 하면 키보드 조작, 스크롤, 긴 지역 목록 처리는 모두 네이티브가 담당한다.

export type FilterSelectOption = { value: string; label: string };

type Props = {
  label: string;
  value: string;
  options: FilterSelectOption[];
  onChange: (value: string) => void;
};

export default function FilterSelect({ label, value, options, onChange }: Props) {
  // 첫 번째 선택지를 "기본값"으로 본다(전체 지역 / 전체 기간 / 기본 정렬).
  const isDefault = value === (options[0]?.value ?? '');

  return (
    <div className="relative shrink-0">
      <select
        aria-label={label}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className={`appearance-none rounded-full border py-1.5 pl-3 pr-7 text-[13px] font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-coral/40 ${
          isDefault
            ? 'border-line bg-white text-ink/70'
            : 'border-coral bg-coral/10 text-coral'
        }`}
      >
        {options.map((option) => (
          // 열린 목록은 OS가 그리므로, 칩 색(coral)이 새지 않게 항목 색은 기본값으로 고정한다.
          <option key={option.value} value={option.value} className="bg-white text-ink">
            {option.label}
          </option>
        ))}
      </select>
      <ChevronDown
        size={13}
        aria-hidden
        className={`pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 ${
          isDefault ? 'text-ink/40' : 'text-coral'
        }`}
      />
    </div>
  );
}
