import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { MapPin } from 'lucide-react';
import type { Festival, TourSpot } from '../types';
import type { MemberProfile } from '../api/memberProfile';
import { memberProfileApi } from '../api/memberProfile';
import { festivalsApi } from '../api/festivals';
import { checkinApi } from '../api/checkin';
import { mapFestivalDetailToFestival, mapFestivalListItemToFestival } from '../utils/festival';
import {
  pickFallbackFestival,
  pickNearestFestival,
  shouldReplaceHero,
  sigunguName,
  type HeroSource,
} from '../utils/homeFestival';
import { getCurrentPosition } from '../utils/geolocation';
import { mapNearbyTourPlaceToTourSpot, formatDistanceLabel, formatWalkMinutesLabel } from '../utils/tourSpot';
import MobileLayout from '../components/layout/MobileLayout';
import AppHeader from '../components/layout/AppHeader';
import FestivalHeroCard from '../components/home/FestivalHeroCard';
import FestivalMatchingBanner from '../components/home/FestivalMatchingBanner';
import UpcomingFestivalCard from '../components/home/UpcomingFestivalCard';
import CtaBanner from '../components/home/CtaBanner';
import FestivalNearbyPlaceItem from '../components/home/FestivalNearbyPlaceItem';

type NearbyPlace = { spot: TourSpot; distanceMeters: number };

export default function HomePage() {
  const [profile, setProfile] = useState<MemberProfile | null>(null);
  const [hotFestival, setHotFestival] = useState<Festival | null>(null);
  const [heroSource, setHeroSource] = useState<HeroSource | null>(null);
  const [heroDistanceMeters, setHeroDistanceMeters] = useState<number | null>(null);
  const [heroRegionName, setHeroRegionName] = useState<string | null>(null);
  const [upcomingFestivals, setUpcomingFestivals] = useState<Festival[]>([]);
  const [nearbyPlaces, setNearbyPlaces] = useState<NearbyPlace[]>([]);

  useEffect(() => {
    let mounted = true;
    // 체크인·GPS·목록이 각각 따로 도착하므로, 늦게 온 결과가 더 확실한 근거를 덮어쓰지 않도록
    // 지금 반영된 근거를 ref로 들고 비교한다(setState는 비동기라 값을 바로 읽을 수 없다).
    let appliedSource: HeroSource | null = null;

    const applyHero = (
      source: HeroSource,
      festival: Festival,
      distanceMeters: number | null,
      regionName: string | null,
    ) => {
      if (!mounted || !shouldReplaceHero(appliedSource, source)) return;
      appliedSource = source;
      setHeroSource(source);
      setHotFestival(festival);
      setHeroDistanceMeters(distanceMeters);
      setHeroRegionName(regionName);
    };

    memberProfileApi.getMine().then((memberProfile) => {
      if (!mounted) return;
      setProfile(memberProfile);
    });

    festivalsApi.getList(0, 20).then((festivalList) => {
      if (!mounted) return;
      const mapped = festivalList.items.map(mapFestivalListItemToFestival);
      setUpcomingFestivals(mapped.filter((festival) => festival.status === 'upcoming'));

      // 목록이 도착하면 먼저 폴백 기준으로 히어로를 그린다. 체크인·위치 조회가 화면을 막지 않게
      // 하고, 권한을 거부한 사용자는 위치 기능 도입 전과 완전히 같은 화면을 보게 된다.
      const fallback = pickFallbackFestival(mapped);
      if (fallback) applyHero('FALLBACK', fallback, null, null);

      // 내 위치에서 가장 가까운 축제로 히어로를 교체한다. 좌표는 이 브라우저 안에서만 쓰이고
      // 서버로 전송되지 않는다(docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md 4.1).
      getCurrentPosition()
        .then((position) => {
          const nearest = pickNearestFestival(
            position,
            festivalList.items,
            mapFestivalListItemToFestival,
          );
          if (nearest) {
            applyHero('NEAREST', nearest.festival, nearest.distanceMeters, nearest.sigunguName);
          }
        })
        // 권한 거부·타임아웃·미지원은 모두 폴백으로 조용히 처리한다. 홈 화면에 위치 오류
        // 메시지를 띄우는 것은 과하다(체크인처럼 위치가 필수인 기능이 아니다).
        .catch(() => undefined);
    });

    // 체크인한 축제가 있으면 그 축제를 최우선으로 보여준다. 사용자가 이미 "나 여기 있다"고
    // 선언한 축제이므로 GPS로 계산한 최근접 축제보다 확실하다 — 최근접 축제와 다른 곳에
    // 체크인했더라도 홈에는 체크인한 축제가 떠야 한다.
    checkinApi
      .getCurrent()
      .then((checkin) => {
        if (!mounted || !checkin) return;
        // 체크인 응답에는 축제 id/이름만 있어 카드에 필요한 기간·이미지를 상세로 채운다.
        // 목록 20건 안에 없을 수도 있어 목록에서 찾지 않고 상세를 조회한다.
        return festivalsApi.getDetail(checkin.festivalId).then((detail) => {
          applyHero(
            'CHECKIN',
            mapFestivalDetailToFestival(detail),
            null,
            sigunguName(detail.address),
          );
        });
      })
      .catch(() => undefined);

    return () => {
      mounted = false;
    };
  }, []);

  // 히어로가 바뀌면 그 축제 기준으로 주변 관광지를 다시 받는다. 이 API는 중심이 축제 좌표라
  // 사용자 좌표가 서버로 가지 않는다.
  useEffect(() => {
    if (!hotFestival) return;
    let mounted = true;
    setNearbyPlaces([]);
    festivalsApi.getNearbyTourPlaces(hotFestival.id).then((places) => {
      if (!mounted) return;
      setNearbyPlaces(
        places.map((place) => ({
          spot: mapNearbyTourPlaceToTourSpot(place),
          distanceMeters: place.distanceMeters,
        })),
      );
    });
    return () => {
      mounted = false;
    };
  }, [hotFestival]);

  return (
    <MobileLayout>
      <AppHeader />

      <main className="flex flex-col gap-6 px-5 pt-2">
        {/* 인사말 + 현재 기준 지역 */}
        <section className="flex flex-col gap-1.5">
          {/* 체크인했거나 위치로 고른 경우에만 기준을 노출한다. 폴백 상태에서는 기준 지역이
              없으므로 잘못된 지역명을 보여주지 않기 위해 숨긴다. */}
          {heroRegionName && (heroSource === 'CHECKIN' || heroSource === 'NEAREST') && (
            <span className="flex w-fit items-center gap-1 rounded-full bg-white px-3 py-1 text-xs font-medium text-ink/60 shadow-sm">
              <MapPin size={13} className="text-coral" />
              {heroSource === 'CHECKIN'
                ? `체크인한 ${heroRegionName}의 축제`
                : `내 위치에서 가까운 ${heroRegionName}의 축제`}
            </span>
          )}
          <h1 className="text-[22px] font-bold leading-snug text-ink">
            {profile?.nickname ? `${profile.nickname}님,` : '여행자님,'}
            <br />
            함께 즐길 축제를 찾아볼까요?
          </h1>
        </section>

        {/* 지금 가장 핫한 축제 */}
        {hotFestival && (
          <div className="flex flex-col gap-1.5">
            <FestivalHeroCard festival={hotFestival} />
            {heroDistanceMeters !== null && (
              <span className="px-1 text-xs text-ink/55">
                내 위치에서 {formatDistanceLabel(heroDistanceMeters)}
              </span>
            )}
          </div>
        )}

        {/* 선택한 축제에서 매칭 시작 */}
        {hotFestival && (
          <FestivalMatchingBanner festivalId={hotFestival.id} festivalName={hotFestival.name} />
        )}

        {/* 곧 시작하는 축제 */}
        <section className="flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <h2 className="text-[17px] font-bold text-ink">곧 시작하는 축제</h2>
            <Link to="/spots" className="text-[13px] font-medium text-coral">
              전체 보기
            </Link>
          </div>
          <div className="hscroll -mx-5 flex gap-2.5 overflow-x-auto px-5 pb-1 [scrollbar-width:none]">
            {upcomingFestivals.map((festival) => (
              <UpcomingFestivalCard key={festival.id} festival={festival} />
            ))}
          </div>
        </section>

        {/* 혼자 즐기는 주변 관광지 추천 */}
        <CtaBanner
          to="/solo-course"
          tone="teal"
          title="혼자 즐기는 주변 관광지 추천"
          description="선택한 축제 주변의 가볼 만한 곳을 찾아보세요"
          state={hotFestival ? { festivalId: hotFestival.id } : undefined}
        />

        {/* 축제와 함께 둘러보기 */}
        {nearbyPlaces.length > 0 && (
          <section className="flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <h2 className="text-[17px] font-bold text-ink">축제와 함께 둘러보기</h2>
              <Link to="/spots" className="text-[13px] font-medium text-coral">
                전체 보기
              </Link>
            </div>
            <div className="flex flex-col gap-2.5">
              {nearbyPlaces.map(({ spot, distanceMeters }) => (
                <FestivalNearbyPlaceItem
                  key={spot.id}
                  spot={spot}
                  distanceLabel={formatDistanceLabel(distanceMeters)}
                  walkLabel={formatWalkMinutesLabel(distanceMeters)}
                />
              ))}
            </div>
          </section>
        )}
      </main>
    </MobileLayout>
  );
}
