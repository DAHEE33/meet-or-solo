import type { LucideIcon } from 'lucide-react';
import {
  Image as ImageIcon,
  Landmark,
  Mountain,
  PartyPopper,
  UtensilsCrossed,
  Waves,
} from 'lucide-react';
import {
  placeholderAccent,
  placeholderAriaLabel,
  placeholderBackground,
  placeholderLabel,
  type PlaceholderKind,
  type PlaceholderSize,
} from './imagePlaceholderPresets';

const KIND_ICONS: Record<PlaceholderKind, LucideIcon> = {
  FESTIVAL: PartyPopper,
  TOUR: Mountain,
  CULTURE: Landmark,
  ACTIVITY: Waves,
  FOOD: UtensilsCrossed,
  DEFAULT: ImageIcon,
};

const ICON_SIZES: Record<PlaceholderSize, number> = { sm: 22, md: 26, lg: 34 };

interface ImagePlaceholderProps {
  /** 콘텐츠 분류. 관광지는 `placeholderKindFromContentType(contentTypeId)`로 구한다. */
  kind?: PlaceholderKind;
  /** 톤 변형 seed. 보통 콘텐츠 제목을 넘긴다. 같은 콘텐츠는 항상 같은 톤이 나온다. */
  seed?: string;
  size?: PlaceholderSize;
  className?: string;
}

/**
 * 관광공사 API가 이미지를 주지 않을 때 쓰는 기본 이미지.
 * 분류별 아이콘과 옅은 accent 그라데이션으로 채운다. 로딩 스켈레톤과 혼동되지 않도록
 * 스트라이프·펄스 같은 "곧 채워질 것" 신호는 쓰지 않는다.
 * 실제 지도를 못 그리는 경우는 의미가 달라 `MapPlaceholder`를 쓴다.
 */
export default function ImagePlaceholder({
  kind = 'DEFAULT',
  seed = '',
  size = 'md',
  className = '',
}: ImagePlaceholderProps) {
  const Icon = KIND_ICONS[kind];
  const accent = placeholderAccent(kind);

  return (
    <div
      role="img"
      aria-label={placeholderAriaLabel(kind)}
      className={`relative flex flex-col items-center justify-center gap-1 overflow-hidden ${className}`}
      style={{ background: placeholderBackground(kind, seed) }}
    >
      {size === 'lg' && <RidgeArt accent={accent} />}
      <Icon
        size={ICON_SIZES[size]}
        strokeWidth={1.6}
        color={accent}
        className="relative opacity-70"
      />
      {size !== 'sm' && (
        <span className="relative text-[11px] font-medium text-ink/70">
          {placeholderLabel(kind)}
        </span>
      )}
    </div>
  );
}

/** 큰 카드 하단에 깔리는 강원도 능선 라인아트. 작은 썸네일에서는 선이 뭉개져 쓰지 않는다. */
function RidgeArt({ accent }: { accent: string }) {
  return (
    <svg
      viewBox="0 0 320 80"
      preserveAspectRatio="none"
      aria-hidden="true"
      className="pointer-events-none absolute inset-x-0 bottom-0 h-1/2 w-full"
    >
      <path
        d="M0 66 L54 40 L96 58 L150 24 L206 56 L258 34 L320 62 L320 80 L0 80 Z"
        fill={accent}
        fillOpacity="0.07"
      />
      <path
        d="M0 66 L54 40 L96 58 L150 24 L206 56 L258 34 L320 62"
        fill="none"
        stroke={accent}
        strokeOpacity="0.2"
        strokeWidth="2"
        strokeLinejoin="round"
      />
    </svg>
  );
}
