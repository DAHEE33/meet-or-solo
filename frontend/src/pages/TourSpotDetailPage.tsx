import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { MapPin, Share2, Navigation } from 'lucide-react';
import { spotsApi, type TourPlaceDetail, type NearbyFestivalItem } from '../api/spots';
import { mapTourPlaceDetailToTourSpot, mapTourPlaceListItemToTourSpot } from '../utils/tourSpot';
import { mapNearbyFestivalToFestival } from '../utils/festival';
import { buildKakaoDirectionsUrl } from '../utils/geo';
import type { Festival, TourSpot } from '../types';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import ImagePlaceholder from '../components/common/ImagePlaceholder';
import PlaceScrollCard from '../components/common/PlaceScrollCard';
import FestivalListItem from '../components/festival/FestivalListItem';
import KakaoMeetingPointMap from '../components/matching/KakaoMeetingPointMap';

export default function TourSpotDetailPage() {
  const { spotId } = useParams<{ spotId: string }>();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<TourPlaceDetail | null>(null);
  const [nearbyFestivals, setNearbyFestivals] = useState<NearbyFestivalItem[]>([]);
  const [otherSpots, setOtherSpots] = useState<TourSpot[]>([]);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    const id = Number(spotId);
    if (!Number.isFinite(id)) {
      setLoading(false);
      setNotFound(true);
      return;
    }
    let mounted = true;
    setLoading(true);
    setNotFound(false);
    spotsApi
      .getDetail(id)
      .then((data) => {
        if (!mounted) return;
        setDetail(data);
        spotsApi.getNearbyFestivals(id).then((festivals) => {
          if (mounted) setNearbyFestivals(festivals);
        });
        spotsApi.getList(0, 5).then((list) => {
          if (!mounted) return;
          setOtherSpots(
            list.items
              .filter((item) => item.id !== id)
              .slice(0, 4)
              .map(mapTourPlaceListItemToTourSpot),
          );
        });
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
  }, [spotId]);

  if (loading) {
    return (
      <MobileLayout showTabBar={false}>
        <PageHeader title="관광지 상세" />
        <p className="py-16 text-center text-[14px] text-ink/45">불러오는 중이에요...</p>
      </MobileLayout>
    );
  }

  if (notFound || !detail) {
    return (
      <MobileLayout showTabBar={false}>
        <PageHeader title="관광지 상세" />
        <p className="py-16 text-center text-[14px] text-ink/45">
          관광지를 찾을 수 없어요. <Link to="/spots" className="text-coral">목록으로</Link>
        </p>
      </MobileLayout>
    );
  }

  const spot: TourSpot = mapTourPlaceDetailToTourSpot(detail);
  const nearbyFestivalCards: Festival[] = nearbyFestivals.map(mapNearbyFestivalToFestival);
  // 관광공사 동기화 데이터는 좌표(mapX=경도, mapY=위도)가 비어 있을 수 있다.
  const hasCoordinates = detail.mapX !== null && detail.mapY !== null;
  const directionsUrl = hasCoordinates ? buildKakaoDirectionsUrl(spot.name, detail.mapY!, detail.mapX!) : '';

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
          {detail.imageUrl ? (
            <img
              src={detail.imageUrl}
              alt={`${spot.name} 사진`}
              className="h-60 w-full object-cover"
            />
          ) : (
            <ImagePlaceholder label={`${spot.name} 사진`} className="h-60 w-full" />
          )}
        </div>

        {/* 핵심 정보 */}
        <section className="flex flex-col gap-2">
          <h2 className="text-[22px] font-bold text-ink">{spot.name}</h2>
          {spot.address && (
            <div className="flex items-center gap-3 text-[13px] text-ink/60">
              <span className="flex items-center gap-1">
                <MapPin size={14} />
                {spot.address}
              </span>
            </div>
          )}
        </section>

        {/* 오시는 길 — 좌표(mapX=경도, mapY=위도)가 있을 때만 실제 지도를 그린다.
            관광공사 동기화 데이터는 좌표가 비어 있을 수 있어, 그 경우 주소만 보여준다. */}
        <section className="flex flex-col gap-2.5">
          <h3 className="text-[17px] font-bold text-ink">오시는 길</h3>
          <div className="flex flex-col gap-3 rounded-2xl bg-white p-3 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
            {detail.mapX !== null && detail.mapY !== null ? (
              <KakaoMeetingPointMap
                meetingPoint={{ name: spot.name, latitude: detail.mapY, longitude: detail.mapX }}
              />
            ) : (
              <ImagePlaceholder label="지도 미리보기" className="h-32 w-full rounded-xl" />
            )}
            <div className="flex items-center justify-between gap-3 px-1 pb-1">
              <div className="flex min-w-0 flex-col gap-0.5">
                <span className="text-[13px] text-ink/75">{spot.address || '주소 정보 없음'}</span>
                {detail.tel && <span className="text-xs text-ink/50">문의 {detail.tel}</span>}
              </div>
              {hasCoordinates ? (
                <a
                  href={directionsUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="flex shrink-0 items-center gap-1 rounded-full border border-line bg-white px-3.5 py-2 text-[13px] font-semibold text-ink"
                >
                  <Navigation size={14} />
                  길찾기
                </a>
              ) : (
                <button
                  type="button"
                  disabled
                  className="flex shrink-0 items-center gap-1 rounded-full border border-line bg-white px-3.5 py-2 text-[13px] font-semibold text-ink opacity-40"
                >
                  <Navigation size={14} />
                  길찾기
                </button>
              )}
            </div>
          </div>
        </section>

        {/* 이 장소 주변에서 열리는 축제 */}
        {nearbyFestivalCards.length > 0 && (
          <section className="flex flex-col gap-2.5">
            <h3 className="text-[17px] font-bold text-ink">이 장소 주변에서 열리는 축제</h3>
            <div className="flex flex-col gap-2.5">
              {nearbyFestivalCards.map((festival) => (
                <FestivalListItem key={festival.id} festival={festival} />
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
                  meta={other.address || '관광지'}
                  imageUrl={other.imageUrl}
                />
              ))}
            </div>
          </section>
        )}
      </main>

      {/* 하단 고정 액션 */}
      <div className="fixed inset-x-0 bottom-0 z-30 mx-auto flex max-w-md gap-2.5 border-t border-line bg-white px-5 pb-[calc(12px+env(safe-area-inset-bottom))] pt-3">
        {hasCoordinates ? (
          <a
            href={directionsUrl}
            target="_blank"
            rel="noreferrer"
            className="flex flex-1 items-center justify-center gap-1.5 rounded-2xl border border-line bg-white py-3.5 text-[15px] font-bold text-ink active:scale-[0.99] transition-transform"
          >
            <Navigation size={16} />
            길찾기
          </a>
        ) : (
          <button
            type="button"
            disabled
            className="flex flex-1 items-center justify-center gap-1.5 rounded-2xl border border-line bg-white py-3.5 text-[15px] font-bold text-ink opacity-40"
          >
            <Navigation size={16} />
            길찾기
          </button>
        )}
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
