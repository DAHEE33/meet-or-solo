// 로딩 표시 공통 컴포넌트.
// 문구만 보여주면 멈춘 화면과 구분되지 않아, 회전 애니메이션으로 "진행 중"을 눈에 보이게 한다.
//
// 접근성: 애니메이션은 장식이므로 aria-hidden으로 감추고, 실제 안내는 문구가 담당한다.
// 문구가 없는 곳(버튼 내부 등)은 호출부가 aria-label이나 sr-only 텍스트를 제공한다.

type SpinnerSize = 'sm' | 'md' | 'lg';

const SIZE: Record<SpinnerSize, string> = {
  sm: 'h-4 w-4 border-2',
  md: 'h-6 w-6 border-2',
  lg: 'h-9 w-9 border-[3px]',
};

type SpinnerProps = {
  size?: SpinnerSize;
  /** 배경이 어두운 곳(주 버튼 내부 등)에서는 흰색 계열로 뒤집는다. */
  tone?: 'ink' | 'white' | 'coral';
  className?: string;
};

const TONE: Record<NonNullable<SpinnerProps['tone']>, string> = {
  // 한쪽 테두리만 진하게 두면 회전할 때 진행 중이라는 인상이 분명해진다.
  ink: 'border-ink/15 border-t-ink/60',
  white: 'border-white/30 border-t-white',
  coral: 'border-coral/20 border-t-coral',
};

export default function Spinner({ size = 'md', tone = 'ink', className = '' }: SpinnerProps) {
  return (
    <span
      aria-hidden
      className={`inline-block shrink-0 animate-spin rounded-full ${SIZE[size]} ${TONE[tone]} ${className}`}
    />
  );
}

type LoadingStateProps = {
  /** 화면에 보여줄 안내 문구. 스크린리더도 이 문구를 읽는다. */
  message?: string;
  className?: string;
};

/**
 * 목록·상세가 처음 로딩될 때 영역 전체를 차지하는 표시.
 * `role="status"`로 감싸 로딩이 끝나면 스크린리더가 변화를 알 수 있게 한다.
 */
export function LoadingState({ message = '불러오는 중이에요', className = '' }: LoadingStateProps) {
  return (
    <div
      role="status"
      className={`flex flex-col items-center justify-center gap-3 py-12 ${className}`}
    >
      <Spinner size="lg" />
      <p className="text-[13px] text-ink/45">{message}</p>
    </div>
  );
}

/** 무한스크롤 하단처럼 이미 목록이 보이는 상태에서 다음 페이지를 기다릴 때 쓰는 작은 표시. */
export function LoadingMore({ message = '더 불러오는 중' }: { message?: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-2 py-4">
      <Spinner size="sm" />
      <span className="text-xs text-ink/45">{message}</span>
    </div>
  );
}
