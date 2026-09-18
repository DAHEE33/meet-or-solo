import { useState } from 'react';
import type { ConsentItemNotice } from './consentNotice';

interface ConsentCheckboxProps {
  id: string;
  notice: ConsentItemNotice;
  checked: boolean;
  disabled: boolean;
  onChange: (checked: boolean) => void;
  /**
   * 원문 전문을 띄우는 버튼의 문구. 넘기지 않으면 버튼을 그리지 않는다.
   *
   * 약관·개인정보처리방침은 요약만으로 동의 근거가 되지 않아 전문을 함께 보여준다. AI 관련
   * 동의 2종은 `notice.details`에 목적·항목·보관기간이 다 들어가므로 전문이 따로 없다.
   */
  documentLabel?: string;
  onOpenDocument?: () => void;
}

/**
 * 동의 항목 하나를 그리는 체크박스.
 *
 * `AiConsentSection`이 쓰던 내부 컴포넌트를 회원가입의 약관·개인정보 동의에서도 쓰기 위해
 * 파일로 분리했다. 마크업은 옮기기 전과 같다.
 */
export default function ConsentCheckbox({
  id,
  notice,
  checked,
  disabled,
  onChange,
  documentLabel,
  onOpenDocument,
}: ConsentCheckboxProps) {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-start gap-2.5">
        <input
          id={id}
          type="checkbox"
          checked={checked}
          disabled={disabled}
          onChange={(event) => onChange(event.target.checked)}
          className="mt-0.5 h-5 w-5 shrink-0 accent-coral disabled:opacity-50"
        />
        <label htmlFor={id} className="flex flex-col gap-0.5">
          <span className="text-[14px] font-semibold text-ink">{notice.title}</span>
          <span className="text-[12px] leading-5 text-ink/50">{notice.summary}</span>
        </label>
      </div>
      <div className="flex items-center gap-3 pl-[30px]">
        <button
          type="button"
          onClick={() => setIsOpen((prev) => !prev)}
          aria-expanded={isOpen}
          className="text-[12px] text-ink/40 underline active:text-coral"
        >
          {isOpen ? '접기' : '자세히'}
        </button>
        {documentLabel && onOpenDocument && (
          <button
            type="button"
            onClick={onOpenDocument}
            className="text-[12px] font-semibold text-coral underline"
          >
            {documentLabel}
          </button>
        )}
      </div>
      {isOpen && (
        <dl className="ml-[30px] flex flex-col gap-1 rounded-xl bg-white px-3 py-2.5">
          {notice.details.map((detail) => (
            <div key={detail.label} className="flex gap-2 text-[12px] leading-5">
              <dt className="w-[92px] shrink-0 font-semibold text-ink/60">{detail.label}</dt>
              <dd className="flex-1 text-ink/60">{detail.value}</dd>
            </div>
          ))}
        </dl>
      )}
    </div>
  );
}
