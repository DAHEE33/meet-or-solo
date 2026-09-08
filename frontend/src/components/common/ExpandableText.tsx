import { useState } from 'react';
import { ChevronDown, ChevronUp } from 'lucide-react';

/** 아코디언으로 펼치기 전 보여줄 최대 글자 수 */
export const EXPANDABLE_TEXT_LIMIT = 200;

/**
 * limit자를 넘는 텍스트를 자르고 말줄임표를 붙인다.
 * limit 이하이면 원문을 그대로 반환한다.
 */
export function truncateText(text: string, limit: number = EXPANDABLE_TEXT_LIMIT): string {
  if (text.length <= limit) return text;
  return `${text.slice(0, limit)}...`;
}

interface ExpandableTextProps {
  text: string;
  limit?: number;
  className?: string;
}

/**
 * 소개/프로그램 설명처럼 길어질 수 있는 본문 텍스트용 아코디언.
 * limit자를 넘으면 잘라서 보여주고, 버튼을 누르면 전체를 펼친다.
 */
export default function ExpandableText({ text, limit = EXPANDABLE_TEXT_LIMIT, className = '' }: ExpandableTextProps) {
  const [expanded, setExpanded] = useState(false);
  const isTruncatable = text.length > limit;

  return (
    <div className="flex flex-col gap-1.5">
      <p className={`whitespace-pre-line leading-relaxed ${className}`}>
        {expanded || !isTruncatable ? text : truncateText(text, limit)}
      </p>
      {isTruncatable && (
        <button
          type="button"
          onClick={() => setExpanded((prev) => !prev)}
          className="flex w-fit items-center gap-0.5 text-[13px] font-semibold text-ink/50"
        >
          {expanded ? '접기' : '더보기'}
          {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
        </button>
      )}
    </div>
  );
}
