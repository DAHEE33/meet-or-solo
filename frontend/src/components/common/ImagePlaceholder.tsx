interface ImagePlaceholderProps {
  label: string;
  className?: string;
}

/** 실제 관광지 이미지가 없을 때 쓰는 스트라이프 플레이스홀더 */
export default function ImagePlaceholder({ label, className = '' }: ImagePlaceholderProps) {
  return (
    <div
      className={`flex items-center justify-center ${className}`}
      style={{
        background:
          'repeating-linear-gradient(45deg, #E9E2D6 0px, #E9E2D6 10px, #F1EBE0 10px, #F1EBE0 20px)',
      }}
    >
      <span className="rounded bg-white/70 px-2 py-0.5 font-mono text-[11px] text-ink/60">
        {label}
      </span>
    </div>
  );
}
