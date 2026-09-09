import { MapPin } from 'lucide-react';

interface MapPlaceholderProps {
  className?: string;
}

/**
 * 좌표(mapX/mapY)가 없어 실제 지도를 그릴 수 없을 때 쓰는 자리 표시.
 * "사진이 없는 것"과 "지도를 그릴 수 없는 것"은 원인이 달라 `ImagePlaceholder`와 분리한다.
 * 격자 배경으로 지도 자리임을 알리고, 사용자가 다음 행동을 정할 수 있게 이유를 문구로 밝힌다.
 */
export default function MapPlaceholder({ className = '' }: MapPlaceholderProps) {
  return (
    <div
      role="img"
      aria-label="좌표 정보가 없어 지도를 표시할 수 없음"
      className={`relative flex flex-col items-center justify-center gap-1 overflow-hidden ${className}`}
      style={{
        backgroundColor: '#F4F1EA',
        backgroundImage:
          'linear-gradient(#E4DED2 1px, transparent 1px), linear-gradient(90deg, #E4DED2 1px, transparent 1px)',
        backgroundSize: '20px 20px',
      }}
    >
      <MapPin size={24} strokeWidth={1.7} className="text-ink/35" />
      <span className="text-[11px] font-medium text-ink/70">
        좌표 정보가 없어 지도를 표시할 수 없어요
      </span>
    </div>
  );
}
