import { ButtonHTMLAttributes } from 'react';
import Spinner from './Spinner';

interface PrimaryButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  tone?: 'coral' | 'teal' | 'ink';
  /**
   * 처리 중이면 라벨 앞에 스피너를 붙인다. 라벨만 "저장 중..."으로 바뀌면 눌린 건지
   * 멈춘 건지 구분이 안 되므로 움직이는 표시를 함께 준다.
   */
  pending?: boolean;
}

const TONE: Record<NonNullable<PrimaryButtonProps['tone']>, string> = {
  coral: 'bg-coral text-white',
  teal: 'bg-teal text-white',
  ink: 'bg-ink text-white',
};

export default function PrimaryButton({
  tone = 'coral',
  pending = false,
  className = '',
  children,
  ...rest
}: PrimaryButtonProps) {
  return (
    <button
      type="button"
      aria-busy={pending || undefined}
      className={`flex w-full items-center justify-center gap-2 rounded-2xl py-3.5 text-[15px] font-bold active:scale-[0.99] transition-transform disabled:opacity-40 ${TONE[tone]} ${className}`}
      {...rest}
    >
      {pending && <Spinner size="sm" tone="white" />}
      {children}
    </button>
  );
}
