import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Calendar, MapPin, Share2, Navigation, ChevronRight } from 'lucide-react';
import { festivalsApi, type FestivalDetail } from '../api/festivals';
import {
  resolveDisplayStatus,
  resolveDdayLabel,
  formatFestivalPeriod,
  getFestivalStatusLabel,
  getFestivalStatusSolidClass,
  getFestivalStatusSoftClass,
} from '../utils/festival';
import { formatDistanceLabel } from '../utils/tourSpot';
import { buildKakaoDirectionsUrl } from '../utils/geo';
import { useFestivalCheckin } from '../hooks/useFestivalCheckin';
import { useCurrentCheckin } from '../hooks/useCurrentCheckin';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import ImagePlaceholder from '../components/common/ImagePlaceholder';
import GPSPermissionModal from '../components/common/GPSPermissionModal';
import Spinner, { LoadingState } from '../components/common/Spinner';
import KakaoMeetingPointMap from '../components/matching/KakaoMeetingPointMap';

export default function FestivalDetailPage() {
  const { festivalId } = useParams<{ festivalId: string }>();
  const navigate = useNavigate();
  const [festival, setFestival] = useState<FestivalDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [showPermissionModal, setShowPermissionModal] = useState(false);
  const { state: checkinState, checkIn } = useFestivalCheckin(festival?.id ?? null);
  // 이 화면에서 방금 체크인한 결과(checkinState)뿐 아니라, 이전에 체크인해두고 다시
  // 들어온 경우도 매칭 시작 버튼이 활성화돼야 하므로 실제 체크인 상태를 함께 조회한다.
  const { state: currentCheckinState, refresh: refreshCurrentCheckin } = useCurrentCheckin();
  useEffect(() => {
    if (checkinState.status === 'success') void refreshCurrentCheckin();
  }, [checkinState.status, refreshCurrentCheckin]);
  const currentCheckin = currentCheckinState.status === 'loaded' ? currentCheckinState.checkin : null;
  const isCheckedIntoThisFestival = currentCheckin !== null && currentCheckin.festivalId === festival?.id;

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
        <LoadingState className="py-16" />
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

        {/* GPS 체크인 — 완료하면 아래 하단 CTA로 매칭을 시작할 수 있다 */}
        <div className="flex flex-col gap-3 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-sand text-ink/40">
              <MapPin size={20} />
            </span>
            <div className="flex flex-col gap-0.5">
              <span className="text-sm font-semibold text-ink">
                {isCheckedIntoThisFestival ? '체크인 완료' : '이 축제에 체크인하기'}
              </span>
              <span className="text-[13px] text-ink/55">
                {checkinState.status === 'success'
                  ? `현재 위치에서 ${formatDistanceLabel(checkinState.result.distanceMeters)} 떨어진 곳에서 체크인했어요. 아래 버튼으로 매칭을 시작해보세요.`
                  : isCheckedIntoThisFestival
                    ? '이미 체크인되어 있어요. 아래 버튼으로 매칭을 시작해보세요.'
                    : '축제 반경 안에 있으면 체크인할 수 있어요.'}
              </span>
            </div>
          </div>
          {checkinState.status === 'error' && (
            <p className="text-[13px] text-coral">{checkinState.message}</p>
          )}
          {!isCheckedIntoThisFestival && (
            <button
              type="button"
              onClick={() => setShowPermissionModal(true)}
              disabled={checkinState.status === 'locating' || checkinState.status === 'submitting'}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-ink py-2.5 text-[14px] font-bold text-white disabled:opacity-50"
            >
              {(checkinState.status === 'locating' || checkinState.status === 'submitting') && (
                <Spinner size="sm" tone="white" />
              )}
              {checkinState.status === 'locating'
                ? '위치 확인 중...'
                : checkinState.status === 'submitting'
                  ? '체크인 처리 중...'
                  : '체크인하기'}
            </button>
          )}
        </div>

        {/* 소개 — 관광공사 detailCommon2 온디맨드 조회, 실패/미제공 시 섹션 자체를 숨긴다 */}
        {festival.intro && (
          <section className="flex flex-col gap-2.5">
            <h3 className="text-[17px] font-bold text-ink">소개</h3>
            <p className="whitespace-pre-line text-[14px] leading-relaxed text-ink/75">{festival.intro}</p>
          </section>
        )}

        {/* 이용정보 — 관광공사 detailIntro2 온디맨드 조회 */}
        {festival.infoItems.length > 0 && (
          <section className="flex flex-col gap-2.5">
            <h3 className="text-[17px] font-bold text-ink">이용정보</h3>
            <div className="flex flex-col gap-2 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
              {festival.infoItems.map((item) => (
                <div key={item.label} className="flex items-start justify-between gap-3 text-[13px]">
                  <span className="shrink-0 text-ink/50">{item.label}</span>
                  <span className="text-right text-ink/80">{item.value}</span>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* 프로그램 — 관광공사 detailInfo2 온디맨드 조회 */}
        {festival.programs.length > 0 && (
          <section className="flex flex-col gap-2.5">
            <h3 className="text-[17px] font-bold text-ink">프로그램</h3>
            <div className="flex flex-col gap-3 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
              {festival.programs.map((program, index) => (
                <div
                  key={`${program.name}-${index}`}
                  className="flex flex-col gap-0.5 border-b border-line pb-3 last:border-none last:pb-0"
                >
                  <span className="text-sm font-semibold text-ink">{program.name}</span>
                  {program.time && (
                    <span className="text-xs text-ink/50 tabular-nums">{program.time}</span>
                  )}
                  {program.description && (
                    <span className="text-[13px] text-ink/65">{program.description}</span>
                  )}
                </div>
              ))}
            </div>
          </section>
        )}

        {/* 오시는 길 — 좌표(mapX=경도, mapY=위도)가 있을 때만 실제 지도를 그린다.
            관광공사 동기화 데이터는 좌표가 비어 있을 수 있어, 그 경우 주소만 보여준다. */}
        {festival.address && (
          <section className="flex flex-col gap-2.5">
            <h3 className="text-[17px] font-bold text-ink">오시는 길</h3>
            <div className="flex flex-col gap-3 rounded-2xl bg-white p-3 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
              {festival.mapX !== null && festival.mapY !== null ? (
                <KakaoMeetingPointMap
                  meetingPoint={{ name: festival.title, latitude: festival.mapY, longitude: festival.mapX }}
                />
              ) : (
                <ImagePlaceholder label="지도 미리보기" className="h-32 w-full rounded-xl" />
              )}
              <div className="flex items-center justify-between gap-3 px-1 pb-1">
                <span className="text-[13px] text-ink/75">{festival.address}</span>
                {festival.mapX !== null && festival.mapY !== null && (
                  <a
                    href={buildKakaoDirectionsUrl(festival.title, festival.mapY, festival.mapX)}
                    target="_blank"
                    rel="noreferrer"
                    className="flex shrink-0 items-center gap-1 rounded-full border border-line bg-white px-3.5 py-2 text-[13px] font-semibold text-ink"
                  >
                    <Navigation size={14} />
                    길찾기
                  </a>
                )}
              </div>
            </div>
          </section>
        )}

        {/* 혼자 즐기는 주변 관광지 추천 */}
        <div className="flex flex-col gap-2.5 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
          <div className="flex flex-col gap-0.5">
            <span className="text-sm font-semibold text-ink">혼자 가도 괜찮아요</span>
            <span className="text-[13px] text-ink/55">이 축제 주변의 가볼 만한 관광지를 확인해 보세요</span>
          </div>
          <button
            type="button"
            onClick={() => navigate('/solo-course', { state: { festivalId: festival.id } })}
            className="flex w-fit items-center gap-1 rounded-full border border-teal bg-white px-3.5 py-2 text-[13px] font-semibold text-teal"
          >
            주변 관광지 보기
            <ChevronRight size={14} />
          </button>
        </div>
      </main>

      {/* 하단 고정 CTA — 이 축제에 체크인 완료해야 활성화된다 */}
      <div className="fixed inset-x-0 bottom-0 z-30 mx-auto max-w-md border-t border-line bg-white px-5 pb-[calc(12px+env(safe-area-inset-bottom))] pt-3">
        <button
          type="button"
          disabled={!isCheckedIntoThisFestival}
          onClick={() => navigate('/matching', { state: { festivalId: festival.id } })}
          className={`w-full rounded-2xl py-3.5 text-[15px] font-bold ${
            isCheckedIntoThisFestival ? 'bg-coral text-white' : 'bg-line text-ink/45'
          }`}
        >
          {isCheckedIntoThisFestival ? '매칭 시작하기' : '체크인 후 매칭을 시작할 수 있어요'}
        </button>
      </div>

      {showPermissionModal && (
        <GPSPermissionModal
          onConfirm={() => {
            setShowPermissionModal(false);
            void checkIn();
          }}
          onCancel={() => setShowPermissionModal(false)}
        />
      )}
    </MobileLayout>
  );
}
