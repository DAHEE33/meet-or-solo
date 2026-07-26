import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Calendar, MapPin, Share2, Navigation, HeartHandshake, ChevronRight } from 'lucide-react';
import { festivalsApi, type FestivalDetail } from '../api/festivals';
import {
  resolveDisplayStatus,
  resolveDdayLabel,
  formatFestivalPeriod,
  getFestivalStatusLabel,
  getFestivalStatusSolidClass,
  getFestivalStatusSoftClass,
} from '../utils/festival';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import ImagePlaceholder from '../components/common/ImagePlaceholder';

export default function FestivalDetailPage() {
  const { festivalId } = useParams<{ festivalId: string }>();
  const navigate = useNavigate();
  const [festival, setFestival] = useState<FestivalDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    const id = Number(festivalId);
    if (!Number.isFinite(id)) {
      setLoading(false);
      setNotFound(true);
      return;
    }
    let mounted = true;
    setLoading(true);
    setNotFound(false);
    festivalsApi
      .getDetail(id)
      .then((data) => {
        if (mounted) setFestival(data);
      })
      .catch(() => {
        if (mounted) setNotFound(true);
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });
    return () => {
      mounted = false;
    };
  }, [festivalId]);

  if (loading) {
    return (
      <MobileLayout showTabBar={false}>
        <PageHeader title="축제 상세" />
        <p className="py-16 text-center text-[14px] text-ink/45">불러오는 중이에요...</p>
      </MobileLayout>
    );
  }

  if (notFound || !festival) {
    return (
      <MobileLayout showTabBar={false}>
        <PageHeader title="축제 상세" />
        <p className="py-16 text-center text-[14px] text-ink/45">
          축제를 찾을 수 없어요. <Link to="/spots" className="text-coral">목록으로</Link>
        </p>
      </MobileLayout>
    );
  }

  const displayStatus = resolveDisplayStatus(festival);
  const statusView = { status: displayStatus, ddayLabel: resolveDdayLabel(festival) };
  const periodFull = formatFestivalPeriod(festival);

  return (
    <MobileLayout showTabBar={false}>
      <PageHeader
        title={festival.title}
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
          {festival.originImageUrl ? (
            <img
              src={festival.originImageUrl}
              alt={`${festival.title} 대표 사진`}
              className="h-60 w-full object-cover"
            />
          ) : (
            <ImagePlaceholder label={`${festival.title} 대표 사진`} className="h-60 w-full" />
          )}
          <span
            className={`absolute left-5 top-3 rounded-md px-2.5 py-[3px] text-xs font-bold tabular-nums ${getFestivalStatusSolidClass(displayStatus)}`}
          >
            {getFestivalStatusLabel(statusView)}
          </span>
        </div>

        {/* 핵심 정보 */}
        <section className="flex flex-col gap-2">
          <span
            className={`w-fit rounded-full px-2.5 py-0.5 text-xs font-semibold tabular-nums ${getFestivalStatusSoftClass(displayStatus)}`}
          >
            {getFestivalStatusLabel(statusView)}
          </span>
          <h2 className="text-[22px] font-bold text-ink">{festival.title}</h2>
          <div className="flex flex-col gap-1 text-[13px] text-ink/60 tabular-nums">
            {periodFull && (
              <span className="flex items-center gap-1">
                <Calendar size={14} />
                {periodFull}
              </span>
            )}
            {festival.address && (
              <span className="flex items-center gap-1">
                <MapPin size={14} />
                {festival.address}
              </span>
            )}
          </div>
        </section>

        {/* 매칭 안내 — 매칭 기능 구현 전까지는 상태만 안내한다 */}
        <div className="flex items-center gap-3 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-sand text-ink/40">
            <HeartHandshake size={20} />
          </span>
          <span className="text-sm font-medium text-ink/55">매칭 기능은 준비 중이에요</span>
        </div>

        {/* 오시는 길 */}
        {festival.address && (
          <section className="flex flex-col gap-2.5">
            <h3 className="text-[17px] font-bold text-ink">오시는 길</h3>
            <div className="flex flex-col gap-3 rounded-2xl bg-white p-3 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
              <ImagePlaceholder label="지도 미리보기" className="h-32 w-full rounded-xl" />
              <div className="flex items-center justify-between gap-3 px-1 pb-1">
                <span className="text-[13px] text-ink/75">{festival.address}</span>
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

      {/* 하단 고정 CTA — 매칭 기능 구현 전까지 비활성 상태로 고정 */}
      <div className="fixed inset-x-0 bottom-0 z-30 mx-auto max-w-md border-t border-line bg-white px-5 pb-[calc(12px+env(safe-area-inset-bottom))] pt-3">
        <button type="button" disabled className="w-full rounded-2xl bg-line py-3.5 text-[15px] font-bold text-ink/45">
          매칭 기능은 준비 중이에요
        </button>
      </div>
    </MobileLayout>
  );
}
