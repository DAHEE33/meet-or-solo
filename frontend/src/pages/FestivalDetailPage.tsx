import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Calendar, MapPin, Share2, Navigation, HeartHandshake, ChevronRight } from 'lucide-react';
import { getFestivalById } from '../data/mock/festivals';
import { getSpotById } from '../data/mock/tourSpots';
import {
  getFestivalStatusLabel,
  getFestivalStatusSolidClass,
  getFestivalStatusSoftClass,
} from '../utils/festival';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import ImagePlaceholder from '../components/common/ImagePlaceholder';
import PlaceScrollCard from '../components/common/PlaceScrollCard';

export default function FestivalDetailPage() {
  const { festivalId } = useParams<{ festivalId: string }>();
  const navigate = useNavigate();
  const [introOpen, setIntroOpen] = useState(false);
  const festival = getFestivalById(Number(festivalId));

  if (!festival) {
    return (
      <MobileLayout showTabBar={false}>
        <PageHeader title="축제 상세" />
        <p className="py-16 text-center text-[14px] text-ink/45">
          축제를 찾을 수 없어요. <Link to="/spots" className="text-coral">목록으로</Link>
        </p>
      </MobileLayout>
    );
  }

  const ended = festival.status === 'ended';
  const noMatch = !festival.matchSupported;
  const ctaDisabled = ended || noMatch;
  const ctaLabel = ended
    ? '종료된 축제입니다'
    : noMatch
      ? '현재 매칭을 지원하지 않는 축제입니다'
      : '이 축제에서 매칭하기';

  const nearbyPlaces = festival.nearbyPlaces
    .map((rel) => {
      const spot = getSpotById(rel.spotId);
      return spot ? { spot, distanceKm: rel.distanceKm } : null;
    })
    .filter((v): v is { spot: NonNullable<ReturnType<typeof getSpotById>>; distanceKm: number } => v !== null);

  return (
    <MobileLayout showTabBar={false}>
      <PageHeader
        title={festival.name}
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
          <ImagePlaceholder label={`${festival.name} 대표 사진`} className="h-60 w-full" />
          <span
            className={`absolute left-5 top-3 rounded-md px-2.5 py-[3px] text-xs font-bold tabular-nums ${getFestivalStatusSolidClass(festival.status)}`}
          >
            {getFestivalStatusLabel(festival)}
          </span>
          {festival.imageCount > 1 && (
            <span className="absolute bottom-3 right-5 rounded-full bg-ink/55 px-2.5 py-[3px] text-[11px] font-medium text-white tabular-nums">
              1 / {festival.imageCount}
            </span>
          )}
        </div>

        {/* 핵심 정보 */}
        <section className="flex flex-col gap-2">
          <div className="flex items-center gap-1.5">
            <span
              className={`rounded-full px-2.5 py-0.5 text-xs font-semibold tabular-nums ${getFestivalStatusSoftClass(festival.status)}`}
            >
              {getFestivalStatusLabel(festival)}
            </span>
            <span className="rounded-full border border-line bg-white px-2.5 py-0.5 text-xs font-medium text-ink/60">
              {festival.category}
            </span>
          </div>
          <h2 className="text-[22px] font-bold text-ink">{festival.name}</h2>
          <div className="flex flex-col gap-1 text-[13px] text-ink/60 tabular-nums">
            <span className="flex items-center gap-1">
              <Calendar size={14} />
              {festival.periodFull}
            </span>
            <span className="flex items-center gap-1">
              <MapPin size={14} />
              {festival.place} · 현재 위치에서 {festival.distanceKm}km
            </span>
          </div>
          <p className="text-[13px] text-ink/50">{festival.region}</p>
        </section>

        {/* 매칭 현황 요약 */}
        {festival.matchSupported ? (
          <div className="flex items-center gap-3 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-coral/10 text-coral">
              <HeartHandshake size={20} />
            </span>
            <div className="flex flex-col gap-0.5">
              <span className="text-sm font-semibold text-ink tabular-nums">
                현재 {festival.matchingCount}명이 이 축제에서 매칭 중이에요
              </span>
              <span className="text-xs text-ink/50">
                {ended ? '종료된 축제라 새 매칭은 시작할 수 없어요' : '지금 바로 매칭을 시작할 수 있어요'}
              </span>
            </div>
          </div>
        ) : (
          <div className="flex items-center gap-3 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-sand text-ink/40">
              <HeartHandshake size={20} />
            </span>
            <span className="text-sm font-medium text-ink/55">현재 매칭을 지원하지 않는 축제예요</span>
          </div>
        )}

        {/* 축제 소개 */}
        <section className="flex flex-col gap-2.5">
          <h3 className="text-[17px] font-bold text-ink">축제 소개</h3>
          <div className="rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
            <p className={`text-[14px] leading-relaxed text-ink/75 ${introOpen ? '' : 'line-clamp-3'}`}>
              {festival.intro}
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

        {/* 이용 정보 */}
        {festival.infoItems.length > 0 && (
          <section className="flex flex-col gap-2.5">
            <h3 className="text-[17px] font-bold text-ink">이용 정보</h3>
            <div className="flex flex-col gap-2.5 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
              {festival.infoItems
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

        {/* 주요 프로그램 */}
        {festival.programs.length > 0 && (
          <section className="flex flex-col gap-2.5">
            <h3 className="text-[17px] font-bold text-ink">주요 프로그램</h3>
            <div className="flex flex-col gap-2.5">
              {festival.programs.map((program) => (
                <div
                  key={program.name}
                  className="flex items-center gap-3 rounded-2xl bg-white px-3.5 py-3 shadow-[0_1px_8px_rgba(34,48,62,0.05)]"
                >
                  <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                    <span className="text-sm font-semibold text-ink">{program.name}</span>
                    <span className="text-xs text-ink/50">{program.desc}</span>
                  </div>
                  <span className="shrink-0 rounded-md bg-sand px-2 py-[3px] text-xs text-ink/60 tabular-nums">
                    {program.time}
                  </span>
                </div>
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
                <span className="text-[13px] text-ink/75">{festival.address}</span>
                <span className="text-xs text-ink/50 tabular-nums">현재 위치에서 {festival.distanceKm}km</span>
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

        {/* 축제와 함께 둘러보기 */}
        {nearbyPlaces.length > 0 && (
          <section className="flex flex-col gap-2.5">
            <h3 className="text-[17px] font-bold text-ink">축제와 함께 둘러보기</h3>
            <div className="hscroll -mx-5 flex gap-2.5 overflow-x-auto px-5 pb-1 [scrollbar-width:none]">
              {nearbyPlaces.map(({ spot, distanceKm }) => (
                <PlaceScrollCard
                  key={spot.id}
                  to={`/spots/${spot.id}`}
                  name={spot.name}
                  meta={`${spot.category} · 축제장에서 ${distanceKm}km`}
                />
              ))}
            </div>
          </section>
        )}

        {/* 혼자 즐기는 코스 */}
        <div className="flex flex-col gap-2.5 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
          <div className="flex flex-col gap-0.5">
            <span className="text-sm font-semibold text-ink">혼자 가도 괜찮아요</span>
            <span className="text-[13px] text-ink/55">이 축제를 중심으로 하루 코스를 확인해 보세요</span>
          </div>
          <button
            type="button"
            onClick={() => navigate('/solo-course')}
            className="flex w-fit items-center gap-1 rounded-full border border-teal bg-white px-3.5 py-2 text-[13px] font-semibold text-teal"
          >
            솔로 코스 보기
            <ChevronRight size={14} />
          </button>
        </div>
      </main>

      {/* 하단 고정 CTA */}
      <div className="fixed inset-x-0 bottom-0 z-30 mx-auto max-w-md border-t border-line bg-white px-5 pb-[calc(12px+env(safe-area-inset-bottom))] pt-3">
        <button
          type="button"
          disabled={ctaDisabled}
          onClick={() => !ctaDisabled && navigate('/matching')}
          className={`w-full rounded-2xl py-3.5 text-[15px] font-bold transition-transform active:scale-[0.99] disabled:active:scale-100 ${
            ctaDisabled ? 'bg-line text-ink/45' : 'bg-coral text-white'
          }`}
        >
          {ctaLabel}
        </button>
      </div>
    </MobileLayout>
  );
}
