import { ButtonHTMLAttributes } from 'react';

interface PrimaryButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  tone?: 'coral' | 'teal' | 'ink';
}

const TONE: Record<NonNullable<PrimaryButtonProps['tone']>, string> = {
  coral: 'bg-coral text-white',
  teal: 'bg-teal text-white',
  ink: 'bg-ink text-white',
};

export default function PrimaryButton({
  tone = 'coral',
  className = '',
  children,
  ...rest
}: PrimaryButtonProps) {
  return (
    <button
      type="button"
      className={`w-full rounded-2xl py-3.5 text-[15px] font-bold active:scale-[0.99] transition-transform disabled:opacity-40 ${TONE[tone]} ${className}`}
      {...rest}
    >
      {children}
    </button>
  );
}
