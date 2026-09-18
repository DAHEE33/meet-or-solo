import { Link } from 'react-router-dom';
import ImagePlaceholder from './ImagePlaceholder';
import { placeholderKindFromContentType } from './imagePlaceholderPresets';

interface PlaceScrollCardProps {
  to: string;
  name: string;
  meta: string;
  imageUrl?: string | null;
  /** 이미지가 없을 때 기본 이미지 프리셋을 고르는 데 쓴다 */
  contentTypeId?: string;
}

/** 가로 스크롤 140px 장소 카드 (축제 상세 "축제와 함께 둘러보기", 관광지 상세 "함께 둘러볼 장소") */
export default function PlaceScrollCard({
  to,
  name,
  meta,
  imageUrl,
  contentTypeId,
}: PlaceScrollCardProps) {
  return (
    <Link
      to={to}
      className="flex w-[140px] shrink-0 flex-col overflow-hidden rounded-2xl bg-white shadow-[0_1px_8px_rgba(34,48,62,0.05)] active:scale-[0.99] transition-transform"
    >
      {imageUrl ? (
        <img src={imageUrl} alt={`${name} 사진`} className="h-[84px] w-full object-cover" />
      ) : (
        <ImagePlaceholder
          kind={placeholderKindFromContentType(contentTypeId)}
          seed={name}
          size="md"
          className="h-[84px] w-full"
        />
      )}
      <div className="flex flex-col gap-0.5 px-3 pb-3 pt-2.5">
        <span className="truncate text-sm font-semibold text-ink">{name}</span>
        <span className="text-xs text-ink/50 tabular-nums">{meta}</span>
      </div>
    </Link>
  );
}
