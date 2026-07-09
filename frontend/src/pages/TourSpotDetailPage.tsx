import { Link, useNavigate, useParams } from 'react-router-dom';
import { MapPin, Star } from 'lucide-react';
import { getSpotById } from '../data/mock/tourSpots';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import PrimaryButton from '../components/common/PrimaryButton';
import ImagePlaceholder from '../components/common/ImagePlaceholder';

export default function TourSpotDetailPage() {
  const { spotId } = useParams<{ spotId: string }>();
  const navigate = useNavigate();
  const spot = getSpotById(Number(spotId));

  if (!spot) {
    return (
      <MobileLayout showTabBar={false}>
        <PageHeader title="관광지 상세" />
        <p className="py-16 text-center text-[14px] text-ink/45">
          관광지를 찾을 수 없어요. <Link to="/spots" className="text-coral">목록으로</Link>
        </p>
      </MobileLayout>
    );
  }

  return (
    <MobileLayout showTabBar={false}>
      <PageHeader title={spot.name} />
      <main className="flex flex-col gap-5 px-5 pb-10 pt-1">
        <ImagePlaceholder label={`${spot.name} 사진`} className="h-52 w-full rounded-3xl" />

        <section className="flex flex-col gap-2">
          <div className="flex items-center gap-2">
            <span className="rounded-full bg-coral/10 px-2.5 py-0.5 text-xs font-semibold text-coral">
              {spot.category}
            </span>
          </div>
          <h2 className="text-[22px] font-bold text-ink">{spot.name}</h2>
          <div className="flex items-center gap-3 text-[13px] text-ink/60 tabular-nums">
            <span className="flex items-center gap-1">
              <Star size={14} className="fill-amber-400 text-amber-400" />
              {spot.rating} ({spot.reviewCount.toLocaleString()})
            </span>
            <span className="flex items-center gap-1">
              <MapPin size={14} />
              {spot.distanceKm}km
            </span>
          </div>
          <p className="text-[13px] text-ink/50">{spot.address}</p>
        </section>

        {spot.description && (
          <p className="rounded-2xl bg-white p-4 text-[14px] leading-relaxed text-ink/75">
            {spot.description}
          </p>
        )}

        <div className="flex flex-wrap gap-1.5">
          {spot.tags.map((tag) => (
            <span key={tag} className="rounded-md bg-white px-2 py-1 text-xs text-ink/60">
              #{tag}
            </span>
          ))}
        </div>

        <div className="mt-2 flex flex-col gap-3">
          <PrimaryButton onClick={() => navigate('/matching')}>
            이 근처에서 매칭하기
          </PrimaryButton>
          <PrimaryButton tone="teal" onClick={() => navigate('/solo-course')}>
            솔로 코스 보기
          </PrimaryButton>
        </div>
      </main>
    </MobileLayout>
  );
}
