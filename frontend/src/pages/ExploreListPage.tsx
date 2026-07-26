import { useEffect, useMemo, useState } from 'react';
import { Search, ChevronDown, SlidersHorizontal, RotateCw } from 'lucide-react';
import type { Festival } from '../types';
import { festivalsApi } from '../api/festivals';
import { spotsApi, type TourPlaceListItem } from '../api/spots';
import { mapFestivalListItemToFestival } from '../utils/festival';
import { mapTourPlaceListItemToTourSpot } from '../utils/tourSpot';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import FestivalListItem from '../components/festival/FestivalListItem';
import ExploreSpotItem from '../components/explore/ExploreSpotItem';

type Segment = 'festival' | 'spot';

/** 관광공사 contentTypeId 기준 카테고리. mock 시절의 SpotCategory(역사/자연/체험 등)는
 * 실 동기화 데이터와 매핑할 근거가 없어 폐기하고, 실제로 구분 가능한 카테고리만 노출한다. */
const SPOT_CATEGORIES: { label: string; contentTypeId?: string }[] = [
  { label: '전체' },
  { label: '관광지', contentTypeId: '12' },
  { label: '문화시설', contentTypeId: '14' },
  { label: '액티비티', contentTypeId: '28' },
  { label: '맛집', contentTypeId: '39' },
];

const FESTIVAL_FILTERS = ['지역', '일정', '정렬'];
const SPOT_FILTERS = ['현재 위치', '거리', '정렬'];

export default function ExploreListPage() {
  const [segment, setSegment] = useState<Segment>('festival');
  const [category, setCategory] = useState<string>('전체');
  const [keyword, setKeyword] = useState('');
  const [festivals, setFestivals] = useState<Festival[]>([]);
  const [festivalsLoading, setFestivalsLoading] = useState(true);
  const [spots, setSpots] = useState<TourPlaceListItem[]>([]);
  const [spotsLoading, setSpotsLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    festivalsApi.getList(0, 100).then((list) => {
      if (!mounted) return;
      setFestivals(list.items.map(mapFestivalListItemToFestival));
      setFestivalsLoading(false);
    });
    spotsApi.getList(0, 100).then((list) => {
      if (!mounted) return;
      setSpots(list.items);
      setSpotsLoading(false);
    });
    return () => {
      mounted = false;
    };
  }, []);

  const isFestival = segment === 'festival';
  // 관광공사 동기화 데이터에는 아직 세부 카테고리가 없어 축제 세그먼트는 카테고리 칩을 노출하지 않는다.
  const categoryChips = isFestival ? [] : SPOT_CATEGORIES;
  const filterLabels = isFestival ? FESTIVAL_FILTERS : SPOT_FILTERS;

  const filteredFestivals = useMemo(() => {
    const kw = keyword.trim();
    return festivals.filter((f) => kw === '' || f.name.includes(kw));
  }, [festivals, keyword]);

  const filteredSpots = useMemo(() => {
    const kw = keyword.trim();
    const contentTypeId = SPOT_CATEGORIES.find((c) => c.label === category)?.contentTypeId;
    return spots
      .filter(
        (s) =>
          (contentTypeId === undefined || s.contentTypeId === contentTypeId) &&
          (kw === '' || s.title.includes(kw)),
      )
      .map(mapTourPlaceListItemToTourSpot);
  }, [spots, category, keyword]);

  const isLoadingCurrentSegment = isFestival ? festivalsLoading : spotsLoading;
  const resultCount = isFestival ? filteredFestivals.length : filteredSpots.length;
  const isEmpty = !isLoadingCurrentSegment && resultCount === 0;

  function handleSegmentChange(next: Segment) {
    setSegment(next);
    setCategory('전체');
    setKeyword('');
  }

  function resetFilters() {
    setCategory('전체');
    setKeyword('');
  }

  return (
    <MobileLayout>
      <PageHeader title="축제·관광 탐색" noBack />
      <main className="flex flex-col gap-4 px-5 pt-1">
        {/* 검색바 */}
        <label className="flex items-center gap-2 rounded-2xl border border-line bg-white px-4 py-3">
          <Search size={18} className="text-ink/40" />
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder={isFestival ? '축제 이름 검색' : '관광지·맛집 이름 검색'}
            className="w-full bg-transparent text-[15px] text-ink outline-none placeholder:text-ink/35"
          />
        </label>

        {/* 1Depth 세그먼트 */}
        <div className="grid grid-cols-2 gap-1 rounded-2xl border border-line bg-white p-1">
          {(['festival', 'spot'] as Segment[]).map((seg) => (
            <button
              key={seg}
              type="button"
              onClick={() => handleSegmentChange(seg)}
              className={`rounded-xl py-2.5 text-sm font-bold ${
                segment === seg ? 'bg-ink text-white' : 'bg-transparent text-ink/60'
              }`}
            >
              {seg === 'festival' ? '축제' : '관광지'}
            </button>
          ))}
        </div>

        {/* 2Depth 카테고리 칩 (축제 세그먼트는 아직 카테고리 데이터가 없어 숨김) */}
        {categoryChips.length > 0 && (
          <div className="hscroll -mx-5 flex gap-2 overflow-x-auto px-5 pb-1 [scrollbar-width:none]">
            {categoryChips.map((c) => (
              <button
                key={c.label}
                type="button"
                onClick={() => setCategory(c.label)}
                className={`shrink-0 rounded-full border px-4 py-1.5 text-[13px] font-medium ${
                  category === c.label ? 'border-ink bg-ink text-white' : 'border-line bg-white text-ink/60'
                }`}
              >
                {c.label}
              </button>
            ))}
          </div>
        )}

        {/* 조건 필터 행 (연동 예정 — 현재는 표시만) */}
        <div className="-mt-1.5 flex items-center gap-1.5">
          {filterLabels.map((label) => (
            <button
              key={label}
              type="button"
              className="flex shrink-0 items-center gap-[3px] rounded-lg border border-line bg-sand px-2.5 py-[5px] text-xs font-medium text-ink/70"
            >
              {label}
              <ChevronDown size={12} className="text-ink/45" />
            </button>
          ))}
          <button
            type="button"
            className="ml-auto flex shrink-0 items-center gap-1 rounded-lg border border-line bg-sand px-2.5 py-[5px] text-xs font-medium text-ink/70"
          >
            <SlidersHorizontal size={12} />
            필터
          </button>
        </div>

        {/* 로딩 */}
        {isLoadingCurrentSegment && (
          <p className="py-12 text-center text-[13px] text-ink/45">불러오는 중이에요...</p>
        )}

        {/* 결과 요약 */}
        {!isEmpty && !isLoadingCurrentSegment && (
          <div className="-mt-1 flex items-center justify-between">
            <span className="text-[13px] text-ink/60">
              {isFestival ? `현재 진행 중인 축제 ${resultCount}개` : `주변 장소 ${resultCount}개`}
            </span>
            <span className="flex items-center gap-0.5 text-xs text-ink/45">
              가까운 순
              <ChevronDown size={12} />
            </span>
          </div>
        )}

        {/* 결과 목록 */}
        {!isEmpty && !isLoadingCurrentSegment && (
          <div className="flex flex-col gap-2.5">
            {isFestival
              ? filteredFestivals.map((f) => <FestivalListItem key={f.id} festival={f} />)
              : filteredSpots.map((s) => <ExploreSpotItem key={s.id} spot={s} />)}
          </div>
        )}

        {/* 결과 없음 */}
        {isEmpty && (
          <div className="flex flex-col items-center gap-3 py-12">
            <p className="text-sm text-ink/45">
              {isFestival ? '조건에 맞는 축제가 없어요.' : '조건에 맞는 장소가 없어요.'}
            </p>
            <button
              type="button"
              onClick={resetFilters}
              className="flex items-center gap-1.5 rounded-full border border-line bg-white px-5 py-2.5 text-[13px] font-bold text-ink"
            >
              <RotateCw size={14} />
              필터 초기화
            </button>
          </div>
        )}
      </main>
    </MobileLayout>
  );
}
