import { Link, useNavigate } from 'react-router-dom';
import { ChevronRight, Heart, MapPinCheck, HeartHandshake } from 'lucide-react';
import { mockUser } from '../data/mock/user';
import { checkInRecords } from '../data/mock/checkIns';
import { matchCandidates } from '../data/mock/matchCandidates';
import { tourSpots } from '../data/mock/tourSpots';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';

const favoriteSpots = tourSpots.slice(1, 4); // 찜한 관광지 mock

export default function MyPage() {
  const navigate = useNavigate();

  return (
    <MobileLayout>
      <PageHeader title="마이페이지" noBack />
      <main className="flex flex-col gap-5 px-5 pb-10 pt-1">
        {/* 프로필 카드 */}
        <section className="flex items-center gap-4 rounded-3xl bg-white p-5 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
          <div className="flex h-14 w-14 items-center justify-center rounded-full bg-coral/10 text-lg font-bold text-coral">
            {mockUser.nickname.slice(0, 1)}
          </div>
          <div className="flex min-w-0 flex-1 flex-col">
            <span className="text-[17px] font-bold text-ink">{mockUser.nickname}</span>
            <span className="text-[13px] text-ink/50">{mockUser.email}</span>
            {mockUser.intro && <span className="mt-1 text-[13px] text-ink/65">{mockUser.intro}</span>}
          </div>
        </section>

        {/* 나의 여행 스타일 */}
        <section className="flex flex-col gap-2">
          <h2 className="text-[15px] font-bold text-ink">나의 여행 스타일</h2>
          <div className="flex flex-wrap gap-1.5">
            {mockUser.travelStyles.map((s) => (
              <span key={s} className="rounded-full bg-white px-3 py-1.5 text-[13px] text-ink/70 shadow-sm">
                #{s}
              </span>
            ))}
          </div>
        </section>

        {/* 기록 요약 */}
        <section className="grid grid-cols-2 gap-3">
          <Link
            to="/matching/results"
            className="flex flex-col gap-1 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]"
          >
            <HeartHandshake size={18} className="text-coral" />
            <span className="text-lg font-bold text-ink tabular-nums">{matchCandidates.length}</span>
            <span className="text-xs text-ink/50">매칭 기록</span>
          </Link>
          <Link
            to="/check-in"
            className="flex flex-col gap-1 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]"
          >
            <MapPinCheck size={18} className="text-teal" />
            <span className="text-lg font-bold text-ink tabular-nums">{checkInRecords.length}</span>
            <span className="text-xs text-ink/50">체크인 기록</span>
          </Link>
        </section>

        {/* 찜한 관광지 */}
        <section className="flex flex-col gap-2">
          <h2 className="text-[15px] font-bold text-ink">찜한 관광지</h2>
          <div className="flex flex-col gap-2">
            {favoriteSpots.map((spot) => (
              <Link
                key={spot.id}
                to={`/spots/${spot.id}`}
                className="flex items-center gap-3 rounded-2xl bg-white px-4 py-3 shadow-[0_1px_8px_rgba(34,48,62,0.05)]"
              >
                <Heart size={16} className="shrink-0 fill-coral text-coral" />
                <span className="flex-1 text-[14px] font-medium text-ink">{spot.name}</span>
                <ChevronRight size={16} className="text-ink/30" />
              </Link>
            ))}
          </div>
        </section>

        <button
          type="button"
          onClick={() => navigate('/login')}
          className="mt-2 rounded-2xl border border-line bg-white py-3.5 text-[14px] font-semibold text-ink/60 active:bg-sand"
        >
          로그아웃
        </button>
      </main>
    </MobileLayout>
  );
}
