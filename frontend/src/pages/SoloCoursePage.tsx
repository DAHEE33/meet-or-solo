import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { MapPin } from 'lucide-react';
import { festivalsApi } from '../api/festivals';
import { useCurrentCheckin, type CurrentCheckinState } from '../hooks/useCurrentCheckin';
import { mapNearbyTourPlaceToTourSpot, formatDistanceLabel, formatWalkMinutesLabel } from '../utils/tourSpot';
import { readNumberFromLocationState } from '../utils/positiveInteger';
import type { TourSpot } from '../types';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import FestivalNearbyPlaceItem from '../components/home/FestivalNearbyPlaceItem';

type NearbyPlace = { spot: TourSpot; distanceMeters: number };

export type SoloCourseFestivalResolution =
  | { status: 'loading' }
  | { status: 'ready'; festivalId: number | null };

/**
 * "어느 축제 기준으로 주변 관광지를 추천할지" 결정한다.
 * FestivalDetailPage에서 넘어온 경우(location.state.festivalId)를 최우선으로 쓰고,
 * 없으면 실제 체크인한 축제를 기준으로 삼는다(원본 GPS 좌표는 저장하지 않으므로
 * "내 위치 기반"은 곧 "체크인한 축제 좌표 기반"을 의미한다 — docs/22 3장 참고).
 */
export function resolveSoloCourseFestival(
  locationState: unknown,
  checkinState: CurrentCheckinState,
): SoloCourseFestivalResolution {
  const fromLocation = readNumberFromLocationState(locationState, 'festivalId');
  if (fromLocation !== null) return { status: 'ready', festivalId: fromLocation };
  if (checkinState.status === 'loading') return { status: 'loading' };
  if (checkinState.status === 'loaded') {
    return { status: 'ready', festivalId: checkinState.checkin?.festivalId ?? null };
  }
  return { status: 'ready', festivalId: null };
}

export default function SoloCoursePage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { state: checkinState } = useCurrentCheckin();
  const resolution = resolveSoloCourseFestival(location.state, checkinState);
  const festivalId = resolution.status === 'ready' ? resolution.festivalId : null;

  const [festivalTitle, setFestivalTitle] = useState<string | null>(null);
  const [places, setPlaces] = useState<NearbyPlace[] | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);

  useEffect(() => {
    if (festivalId === null) {
      setFestivalTitle(null);
      setPlaces(null);
      return;
    }
    let mounted = true;
    setPlaces(null);
    setLoadFailed(false);
    Promise.all([festivalsApi.getDetail(festivalId), festivalsApi.getNearbyTourPlaces(festivalId)])
      .then(([festival, nearby]) => {
        if (!mounted) return;
        setFestivalTitle(festival.title);
        setPlaces(
          nearby.map((item) => ({
            spot: mapNearbyTourPlaceToTourSpot(item),
            distanceMeters: item.distanceMeters,
          })),
        );
      })
      .catch(() => {
        if (mounted) setLoadFailed(true);
      });
    return () => {
      mounted = false;
    };
  }, [festivalId]);

  if (resolution.status === 'loading') {
    return (
      <MobileLayout>
        <PageHeader title="주변 관광지 추천" noBack />
        <p className="py-16 text-center text-[14px] text-ink/45">불러오는 중이에요...</p>
      </MobileLayout>
    );
  }

  if (festivalId === null) {
    return (
      <MobileLayout>
        <PageHeader title="주변 관광지 추천" noBack />
        <main className="flex flex-col items-center gap-3 px-5 py-16 text-center">
          <p className="text-[14px] text-ink/55">
            체크인한 축제가 있어야 주변 관광지를 추천할 수 있어요.
          </p>
          <button
            type="button"
            onClick={() => navigate('/check-in')}
            className="rounded-xl bg-ink px-4 py-2.5 text-[14px] font-semibold text-white"
          >
            체크인하러 가기
          </button>
        </main>
      </MobileLayout>
    );
  }

  return (
    <MobileLayout>
      <PageHeader title="주변 관광지 추천" noBack />
      <main className="flex flex-col gap-5 px-5 pb-10 pt-1">
        <section className="flex items-center gap-1.5 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
          <MapPin size={16} className="shrink-0 text-teal" />
          <span className="text-[14px] font-semibold text-ink">
            {festivalTitle ?? '체크인한 축제'} 기준으로 가까운 곳을 추천해요
          </span>
        </section>

        {places === null && !loadFailed && (
          <p className="py-10 text-center text-[14px] text-ink/45">불러오는 중이에요...</p>
        )}
        {loadFailed && (
          <p className="py-10 text-center text-[14px] text-coral">
            주변 관광지를 불러오지 못했어요. 잠시 후 다시 시도해주세요.
          </p>
        )}
        {places !== null && places.length === 0 && (
          <p className="py-10 text-center text-[14px] text-ink/45">
            주변에 추천할 만한 관광지를 찾지 못했어요.
          </p>
        )}
        {places !== null && places.length > 0 && (
          <div className="flex flex-col gap-2.5">
            {places.map(({ spot, distanceMeters }) => (
              <FestivalNearbyPlaceItem
                key={spot.id}
                spot={spot}
                distanceLabel={formatDistanceLabel(distanceMeters)}
                walkLabel={formatWalkMinutesLabel(distanceMeters)}
              />
            ))}
          </div>
        )}
      </main>
    </MobileLayout>
  );
}
