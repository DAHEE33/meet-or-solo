import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { MapPin, ChevronDown } from 'lucide-react';
import type { Festival, TourSpot } from '../types';
import type { MemberProfile } from '../api/memberProfile';
import { memberProfileApi } from '../api/memberProfile';
import { getHotFestival, getUpcomingFestivals, getHomeNearbyPlaces } from '../api/home';
import type { HomeNearbyPlace } from '../data/mock/festivals';
import { getSpotById } from '../data/mock/tourSpots';
import MobileLayout from '../components/layout/MobileLayout';
import AppHeader from '../components/layout/AppHeader';
import FestivalHeroCard from '../components/home/FestivalHeroCard';
import FestivalMatchingBanner from '../components/home/FestivalMatchingBanner';
import UpcomingFestivalCard from '../components/home/UpcomingFestivalCard';
import CtaBanner from '../components/home/CtaBanner';
import FestivalNearbyPlaceItem from '../components/home/FestivalNearbyPlaceItem';

export default function HomePage() {
  const [profile, setProfile] = useState<MemberProfile | null>(null);
  const [hotFestival, setHotFestival] = useState<Festival | null>(null);
  const [upcomingFestivals, setUpcomingFestivals] = useState<Festival[]>([]);
  const [nearbyPlaces, setNearbyPlaces] = useState<HomeNearbyPlace[]>([]);

  useEffect(() => {
    let mounted = true;
    Promise.all([
      memberProfileApi.getMine(),
      getHotFestival(),
      getUpcomingFestivals(),
      getHomeNearbyPlaces(),
    ]).then(([memberProfile, festival, upcoming, places]) => {
      if (!mounted) return;
      setProfile(memberProfile);
      setHotFestival(festival);
      setUpcomingFestivals(upcoming);
      setNearbyPlaces(places);
    });
    return () => {
      mounted = false;
    };
  }, []);

  return (
    <MobileLayout>
      <AppHeader />

      <main className="flex flex-col gap-6 px-5 pt-2">
        {/* 인사말 + 위치 선택 */}
        <section className="flex flex-col gap-1.5">
          <button
            type="button"
            className="flex w-fit items-center gap-1 rounded-full bg-white px-3 py-1 text-xs font-medium text-ink/60 shadow-sm"
          >
            <MapPin size={13} className="text-coral" />
            전북 전주시의 축제
            <ChevronDown size={13} className="text-ink/45" />
          </button>
          <h1 className="text-[22px] font-bold leading-snug text-ink">
            {profile?.nickname ? `${profile.nickname}님,` : '여행자님,'}
            <br />
            함께 즐길 축제를 찾아볼까요?
          </h1>
        </section>

        {/* 지금 가장 핫한 축제 */}
        {hotFestival && <FestivalHeroCard festival={hotFestival} />}

        {/* 선택한 축제에서 매칭 시작 */}
        {hotFestival && <FestivalMatchingBanner festivalName={hotFestival.name} />}

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

        {/* 혼자 즐기는 추천 코스 */}
        <CtaBanner
          to="/solo-course"
          tone="teal"
          title="혼자 즐기는 추천 코스"
          description="선택한 축제에서 시작하는 반나절 솔로 코스"
        />

        {/* 축제와 함께 둘러보기 */}
        <section className="flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <h2 className="text-[17px] font-bold text-ink">축제와 함께 둘러보기</h2>
            <Link to="/spots" className="text-[13px] font-medium text-coral">
              전체 보기
            </Link>
          </div>
          <div className="flex flex-col gap-2.5">
            {nearbyPlaces.map((place) => {
              const spot: TourSpot | undefined = getSpotById(place.spotId);
              if (!spot) return null;
              return (
                <FestivalNearbyPlaceItem
                  key={spot.id}
                  spot={spot}
                  distanceLabel={place.distanceLabel}
                  walkLabel={place.walkLabel}
                />
              );
            })}
          </div>
        </section>
      </main>
    </MobileLayout>
  );
}
