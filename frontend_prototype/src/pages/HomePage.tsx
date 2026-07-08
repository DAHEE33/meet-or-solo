import { useEffect, useState } from 'react';
import { MapPin } from 'lucide-react';
import type { TourSpot, UserSummary } from '../types';
import { getCurrentUser, getTodaySpot, getNearbySpots } from '../api/home';
import MobileLayout from '../components/layout/MobileLayout';
import AppHeader from '../components/layout/AppHeader';
import TodaySpotCard from '../components/home/TodaySpotCard';
import CtaBanner from '../components/home/CtaBanner';
import NearbySpotItem from '../components/home/NearbySpotItem';

export default function HomePage() {
  const [user, setUser] = useState<UserSummary | null>(null);
  const [todaySpot, setTodaySpot] = useState<TourSpot | null>(null);
  const [nearbySpots, setNearbySpots] = useState<TourSpot[]>([]);

  useEffect(() => {
    let mounted = true;
    Promise.all([getCurrentUser(), getTodaySpot(), getNearbySpots()]).then(
      ([u, spot, spots]) => {
        if (!mounted) return;
        setUser(u);
        setTodaySpot(spot);
        setNearbySpots(spots);
      },
    );
    return () => {
      mounted = false;
    };
  }, []);

  return (
    <MobileLayout>
      <AppHeader />

      <main className="flex flex-col gap-6 px-5 pt-2">
        {/* 현재 위치 기반 인사말 */}
        <section className="flex flex-col gap-1.5">
          <span className="flex w-fit items-center gap-1 rounded-full bg-white px-3 py-1 text-xs font-medium text-ink/60 shadow-sm">
            <MapPin size={13} className="text-coral" />
            {user ? user.currentAreaName : '위치 확인 중…'}
          </span>
          <h1 className="text-[22px] font-bold leading-snug text-ink">
            {user ? `${user.nickname}님,` : '여행자님,'}
            <br />
            오늘은 어디로 떠나볼까요?
          </h1>
        </section>

        {/* 오늘의 추천 관광지 */}
        {todaySpot && <TodaySpotCard spot={todaySpot} />}

        {/* CTA 영역 */}
        <section className="flex flex-col gap-3">
          <CtaBanner
            to="/matching"
            tone="coral"
            title="혼자 왔나요? 매칭 시작하기"
            description="같은 코스를 도는 여행자와 만나보세요"
          />
          <CtaBanner
            to="/solo-course"
            tone="teal"
            title="혼자 즐기는 추천 코스"
            description="지금 위치에서 시작하는 반나절 솔로 코스"
          />
        </section>

        {/* 주변 인기 관광지 */}
        <section className="flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <h2 className="text-[17px] font-bold text-ink">주변 인기 관광지</h2>
            <a href="/spots" className="text-[13px] font-medium text-coral">
              전체 보기
            </a>
          </div>
          <div className="flex flex-col gap-2.5">
            {nearbySpots.map((spot) => (
              <NearbySpotItem key={spot.id} spot={spot} />
            ))}
          </div>
        </section>
      </main>
    </MobileLayout>
  );
}
