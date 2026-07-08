import { useMemo, useState } from 'react';
import { Search } from 'lucide-react';
import type { SpotCategory } from '../types';
import { tourSpots } from '../data/mock/tourSpots';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import NearbySpotItem from '../components/home/NearbySpotItem';

const CATEGORIES: (SpotCategory | '전체')[] = ['전체', '문화재', '자연', '체험', '맛집', '카페', '야경'];

export default function TourSpotListPage() {
  const [keyword, setKeyword] = useState('');
  const [category, setCategory] = useState<(typeof CATEGORIES)[number]>('전체');

  const filtered = useMemo(
    () =>
      tourSpots.filter(
        (s) =>
          (category === '전체' || s.category === category) &&
          (keyword.trim() === '' || s.name.includes(keyword.trim())),
      ),
    [keyword, category],
  );

  return (
    <MobileLayout>
      <PageHeader title="관광지 탐색" noBack />
      <main className="flex flex-col gap-4 px-5 pt-1">
        {/* 검색바 */}
        <label className="flex items-center gap-2 rounded-2xl border border-line bg-white px-4 py-3">
          <Search size={18} className="text-ink/40" />
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="관광지 이름 검색"
            className="w-full bg-transparent text-[15px] text-ink outline-none placeholder:text-ink/35"
          />
        </label>

        {/* 카테고리 필터 */}
        <div className="-mx-5 flex gap-2 overflow-x-auto px-5 pb-1 [scrollbar-width:none]">
          {CATEGORIES.map((c) => (
            <button
              key={c}
              type="button"
              onClick={() => setCategory(c)}
              className={`shrink-0 rounded-full border px-4 py-1.5 text-[13px] font-medium ${
                category === c
                  ? 'border-ink bg-ink text-white'
                  : 'border-line bg-white text-ink/60'
              }`}
            >
              {c}
            </button>
          ))}
        </div>

        {/* 결과 리스트 */}
        <div className="flex flex-col gap-2.5">
          {filtered.map((spot) => (
            <NearbySpotItem key={spot.id} spot={spot} />
          ))}
          {filtered.length === 0 && (
            <p className="py-16 text-center text-[14px] text-ink/45">검색 결과가 없어요.</p>
          )}
        </div>
      </main>
    </MobileLayout>
  );
}
