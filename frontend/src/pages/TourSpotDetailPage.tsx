import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { MapPin, Share2, Navigation, ChevronRight } from 'lucide-react';
import { getSpotById, tourSpots } from '../data/mock/tourSpots';
import { getSpotDetailExtra } from '../data/mock/spotDetails';
import { getFestivalById } from '../data/mock/festivals';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import ImagePlaceholder from '../components/common/ImagePlaceholder';
import PlaceScrollCard from '../components/common/PlaceScrollCard';
import FestivalListItem from '../components/festival/FestivalListItem';

export default function TourSpotDetailPage() {
  const { spotId } = useParams<{ spotId: string }>();
  const navigate = useNavigate();
  const [introOpen, setIntroOpen] = useState(false);
  const spot = getSpotById(Number(spotId));
  const extra = spot ? getSpotDetailExtra(spot.id) : undefined;

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

  const nearbyFestivals = (extra?.nearbyFestivals ?? [])
    .map((rel) => {
      const festival = getFestivalById(rel.festivalId);
      return festival ? { festival, distanceKm: rel.distanceKm } : null;
    })
    .filter((v): v is { festival: NonNullable<ReturnType<typeof getFestivalById>>; distanceKm: number } => v !== null);

  const otherSpots = tourSpots.filter((s) => s.id !== spot.id).slice(0, 4);

  return (
    <MobileLayout showTabBar={false}>
      <PageHeader
        title={spot.name}
        rightAction={
          <button
            type="button"
            aria-label="공유"
            className="flex h-11 w-11 items-center justify-center rounded-full text-ink active:bg-black/5"
          >
            <Share2 size={20} strokeWidth={1.8} />
          </button>
        }
      />
      <main className="flex flex-col gap-5 px-5 pb-[120px] pt-1">
        <div className="relative -mx-5">
          <ImagePlaceholder label={`${spot.name} 사진`} className="h-60 w-full" />
          <span className="absolute bottom-3 right-5 rounded-full bg-ink/55 px-2.5 py-[3px] text-[11px] font-medium text-white tabular-nums">
            1 / 6
          </span>
        </div>

        {/* 핵심 정보 */}
        <section className="flex flex-col gap-2">
          <span className="w-fit rounded-full bg-coral/10 px-2.5 py-0.5 text-xs font-semibold text-coral">
            {spot.category}
          </span>
          <h2 className="text-[22px] font-bold text-ink">{spot.name}</h2>
          <div className="flex items-center gap-3 text-[13px] text-ink/60 tabular-nums">
            <span className="flex items-center gap-1">
              <MapPin size={14} />
              현재 위치에서 {spot.distanceKm}km
            </span>
            <span>전주시 완산구</span>
          </div>
        </section>

        {/* 장소 소개 */}
        {spot.description && (
          <section className="flex flex-col gap-2.5">
            <h3 className="text-[17px] font-bold text-ink">장소 소개</h3>
            <div className="rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
              <p className={`text-[14px] leading-relaxed text-ink/75 ${introOpen ? '' : 'line-clamp-3'}`}>
                {spot.description}
              </p>
              <button
                type="button"
                onClick={() => setIntroOpen((v) => !v)}
                className="mt-2 text-[13px] font-medium text-ink/45"
              >
                {introOpen ? '접기' : '더 보기'}
              </button>
            </div>
          </section>
        )}

        {/* 방문 정보 */}
        {extra && extra.visitInfo.length > 0 && (
          <section className="flex flex-col gap-2.5">
            <h3 className="text-[17px] font-bold text-ink">방문 정보</h3>
            <div className="flex flex-col gap-2.5 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
              {extra.visitInfo
                .filter((row) => row.value)
                .map((row) => (
                  <div key={row.label} className="grid grid-cols-[84px_1fr] gap-3 text-[13px]">
                    <span className="text-ink/45">{row.label}</span>
                    <span className="text-ink/75 tabular-nums">{row.value}</span>
                  </div>
                ))}
            </div>
          </section>
        )}

        {/* 추천 포인트 */}
        {extra && extra.points.length > 0 && (
          <section className="flex flex-col gap-2.5">
            <h3 className="text-[17px] font-bold text-ink">추천 포인트</h3>
            <div className="flex flex-wrap gap-1.5">
              {extra.points.map((point) => (
                <span
                  key={point}
                  className="rounded-md bg-white px-2.5 py-1 text-xs text-ink/60 shadow-[0_1px_2px_rgba(0,0,0,0.04)]"
                >
                  {point}
                </span>
              ))}
            </div>
          </section>
        )}

        {/* 오시는 길 */}
        <section className="flex flex-col gap-2.5">
          <h3 className="text-[17px] font-bold text-ink">오시는 길</h3>
          <div className="flex flex-col gap-3 rounded-2xl bg-white p-3 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
            <ImagePlaceholder label="지도 미리보기" className="h-32 w-full rounded-xl" />
            <div className="flex items-center justify-between gap-3 px-1 pb-1">
              <div className="flex min-w-0 flex-col gap-0.5">
                <span className="text-[13px] text-ink/75">{spot.address}</span>
                <span className="text-xs text-ink/50 tabular-nums">현재 위치에서 {spot.distanceKm}km</span>
              </div>
              <button
                type="button"
                className="flex shrink-0 items-center gap-1 rounded-full border border-line bg-white px-3.5 py-2 text-[13px] font-semibold text-ink"
              >
                <Navigation size={14} />
                길찾기
              </button>
            </div>
          </div>
        </section>

        {/* 이 장소 주변에서 열리는 축제 */}
        {nearbyFestivals.length > 0 && (
          <section className="flex flex-col gap-2.5">
            <h3 className="text-[17px] font-bold text-ink">이 장소 주변에서 열리는 축제</h3>
            <div className="flex flex-col gap-2.5">
              {nearbyFestivals.map(({ festival, distanceKm }) => (
                <FestivalListItem
                  key={festival.id}
                  festival={festival}
                  distanceLabel={`이 장소에서 ${distanceKm}km`}
                />
              ))}
            </div>
          </section>
        )}

        {/* 함께 둘러볼 장소 */}
        {otherSpots.length > 0 && (
          <section className="flex flex-col gap-2.5">
            <h3 className="text-[17px] font-bold text-ink">함께 둘러볼 장소</h3>
            <div className="hscroll -mx-5 flex gap-2.5 overflow-x-auto px-5 pb-1 [scrollbar-width:none]">
              {otherSpots.map((other) => (
                <PlaceScrollCard
                  key={other.id}
                  to={`/spots/${other.id}`}
                  name={other.name}
                  meta={`${other.category} · ${other.distanceKm}km`}
                />
              ))}
            </div>
          </section>
        )}

        {/* 이 장소가 포함된 추천 코스 */}
        {extra?.course && (
          <section className="flex flex-col gap-2.5">
            <h3 className="text-[17px] font-bold text-ink">이 장소가 포함된 추천 코스</h3>
            <div className="flex flex-col gap-3 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
              <span className="text-sm font-semibold text-ink">{extra.course.title}</span>
              <div className="flex flex-wrap items-center gap-1.5 text-[13px] text-ink/60">
                {extra.course.stops.map((stop, index) => (
                  <span key={stop} className="flex items-center gap-1.5">
                    <span
                      className={`rounded-md px-2 py-[3px] ${
                        index === extra.course!.highlightIndex
                          ? 'bg-teal/10 font-semibold text-teal'
                          : 'bg-sand'
                      }`}
                    >
                      {stop}
                    </span>
                    {index < extra.course!.stops.length - 1 && (
                      <ChevronRight size={12} className="text-ink/35" />
                    )}
                  </span>
                ))}
              </div>
              <button
                type="button"
                onClick={() => navigate('/solo-course')}
                className="flex w-fit items-center gap-1 rounded-full border border-teal bg-white px-3.5 py-2 text-[13px] font-semibold text-teal"
              >
                코스 보기
                <ChevronRight size={14} />
              </button>
            </div>
          </section>
        )}
      </main>

      {/* 하단 고정 액션 */}
      <div className="fixed inset-x-0 bottom-0 z-30 mx-auto flex max-w-md gap-2.5 border-t border-line bg-white px-5 pb-[calc(12px+env(safe-area-inset-bottom))] pt-3">
        <button
          type="button"
          className="flex flex-1 items-center justify-center gap-1.5 rounded-2xl border border-line bg-white py-3.5 text-[15px] font-bold text-ink active:scale-[0.99] transition-transform"
        >
          <Navigation size={16} />
          길찾기
        </button>
        <button
          type="button"
          onClick={() => navigate('/spots')}
          className="flex-[1.4] rounded-2xl bg-coral py-3.5 text-[15px] font-bold text-white active:scale-[0.99] transition-transform"
        >
          주변 축제 보기
        </button>
      </div>
    </MobileLayout>
  );
}
