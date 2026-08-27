import { useEffect, useMemo, useRef, useState } from 'react';
import { Search, SlidersHorizontal, RotateCw } from 'lucide-react';
import type { Festival } from '../types';
import {
  festivalsApi,
  type FestivalListItem,
  type FestivalListSort,
  type FestivalScheduleFilter,
  type RegionOption,
} from '../api/festivals';
import { spotsApi, type TourPlaceListItem, type TourPlaceListSort } from '../api/spots';
import { mapFestivalListItemToFestival } from '../utils/festival';
import { mapTourPlaceListItemToTourSpot } from '../utils/tourSpot';
import { useInfiniteList } from '../hooks/useInfiniteList';
import { useInfiniteScrollSentinel } from '../hooks/useInfiniteScrollSentinel';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import FestivalListItemCard from '../components/festival/FestivalListItem';
import ExploreSpotItem from '../components/explore/ExploreSpotItem';
import FilterSelect from '../components/explore/FilterSelect';

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

const FESTIVAL_SORTS: { value: FestivalListSort; label: string }[] = [
  { value: 'START_DATE_ASC', label: '시작일 빠른순' },
  { value: 'END_DATE_ASC', label: '종료 임박순' },
  { value: 'RECENTLY_ADDED', label: '최근 등록순' },
];

const FESTIVAL_SCHEDULES: { value: FestivalScheduleFilter; label: string }[] = [
  { value: 'ALL', label: '전체 기간' },
  { value: 'ONGOING', label: '진행 중' },
  { value: 'THIS_WEEKEND', label: '이번 주말' },
  { value: 'THIS_MONTH', label: '이번 달' },
];

// 관광지에는 거리 정렬(가까운순/먼순)이 없다. 사용자 좌표를 서버로 보내지 않아 서버에
// 기준점이 없기 때문이다(docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md 3.1, 6.2).
const SPOT_SORTS: { value: TourPlaceListSort; label: string }[] = [
  { value: 'TITLE_ASC', label: '이름순' },
  { value: 'RECENTLY_ADDED', label: '최근 등록순' },
];

const festivalListDependencies = {
  fetchPage: async (
    query: {
      keyword: string;
      sigunguCode: string;
      sort: FestivalListSort;
      schedule: FestivalScheduleFilter;
      matchableOnly: boolean;
    },
    page: number,
    size: number,
  ) => {
    const response = await festivalsApi.getList(page, size, query.keyword || undefined, {
      sigunguCode: query.sigunguCode || undefined,
      sort: query.sort,
      schedule: query.schedule,
      matchableOnly: query.matchableOnly,
    });
    return { items: response.items, page: response.page, hasNext: response.hasNext };
  },
};

const spotListDependencies = {
  fetchPage: async (
    query: { keyword: string; contentTypeId: string; sigunguCode: string; sort: TourPlaceListSort },
    page: number,
    size: number,
  ) => {
    const response = await spotsApi.getList(
      page,
      size,
      query.contentTypeId || undefined,
      query.keyword || undefined,
      { sigunguCode: query.sigunguCode || undefined, sort: query.sort },
    );
    return { items: response.items, page: response.page, hasNext: response.hasNext };
  },
};

export default function ExploreListPage() {
  const [segment, setSegment] = useState<Segment>('festival');
  const [category, setCategory] = useState<string>('전체');
  const [keyword, setKeyword] = useState('');
  const [debouncedKeyword, setDebouncedKeyword] = useState('');
  const [festivalSigunguCode, setFestivalSigunguCode] = useState('');
  const [festivalSort, setFestivalSort] = useState<FestivalListSort>('START_DATE_ASC');
  const [festivalSchedule, setFestivalSchedule] = useState<FestivalScheduleFilter>('ALL');
  const [matchableOnly, setMatchableOnly] = useState(false);
  const [spotSigunguCode, setSpotSigunguCode] = useState('');
  const [spotSort, setSpotSort] = useState<TourPlaceListSort>('TITLE_ASC');
  const [festivalRegions, setFestivalRegions] = useState<RegionOption[]>([]);
  const [spotRegions, setSpotRegions] = useState<RegionOption[]>([]);
  const isFirstKeyword = useRef(true);

  const isFestival = segment === 'festival';
  const contentTypeId = SPOT_CATEGORIES.find((c) => c.label === category)?.contentTypeId ?? '';

  // 최초 진입은 즉시 조회하고 이후 타이핑만 디바운스한다(기존 동작 유지).
  useEffect(() => {
    const delay = isFirstKeyword.current ? 0 : 300;
    isFirstKeyword.current = false;
    const timer = setTimeout(() => setDebouncedKeyword(keyword.trim()), delay);
    return () => clearTimeout(timer);
  }, [keyword]);

  // 지역 목록은 서버가 "실제로 데이터가 있는 시군구"만 주므로, 선택했을 때 항상 빈 결과가
  // 나오는 지역이 노출되지 않는다.
  useEffect(() => {
    let mounted = true;
    festivalsApi
      .getRegions()
      .then((regions) => mounted && setFestivalRegions(regions))
      .catch(() => undefined);
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    spotsApi
      .getRegions(contentTypeId || undefined)
      .then((regions) => {
        if (!mounted) return;
        setSpotRegions(regions);
        // 카테고리를 바꿔 선택 중인 지역이 사라졌다면 지역 선택을 풀어야 빈 화면에 갇히지 않는다.
        setSpotSigunguCode((current) =>
          current && !regions.some((region) => region.sigunguCode === current) ? '' : current,
        );
      })
      .catch(() => undefined);
    return () => {
      mounted = false;
    };
  }, [contentTypeId]);

  const festivalQuery = useMemo(
    () => ({
      keyword: debouncedKeyword,
      sigunguCode: festivalSigunguCode,
      sort: festivalSort,
      schedule: festivalSchedule,
      matchableOnly,
    }),
    [debouncedKeyword, festivalSigunguCode, festivalSort, festivalSchedule, matchableOnly],
  );

  const spotQuery = useMemo(
    () => ({
      keyword: debouncedKeyword,
      contentTypeId,
      sigunguCode: spotSigunguCode,
      sort: spotSort,
    }),
    [debouncedKeyword, contentTypeId, spotSigunguCode, spotSort],
  );

  const festivals = useInfiniteList<FestivalListItem, typeof festivalQuery>(
    festivalListDependencies,
    festivalQuery,
  );
  const spots = useInfiniteList<TourPlaceListItem, typeof spotQuery>(
    spotListDependencies,
    spotQuery,
  );

  const active = isFestival ? festivals : spots;
  const sentinelRef = useInfiniteScrollSentinel(
    () => void active.loadMore(),
    active.state.hasNext && active.state.status !== 'ERROR',
  );

  const festivalCards: Festival[] = festivals.state.items.map(mapFestivalListItemToFestival);
  const visibleSpots = spots.state.items.map(mapTourPlaceListItemToTourSpot);

  const isInitialLoading = active.state.status === 'LOADING';
  const isError = active.state.status === 'ERROR';
  const resultCount = isFestival ? festivalCards.length : visibleSpots.length;
  const isEmpty = !isInitialLoading && !isError && resultCount === 0;

  function handleSegmentChange(next: Segment) {
    setSegment(next);
    setCategory('전체');
    setKeyword('');
    setDebouncedKeyword('');
  }

  function resetFilters() {
    setCategory('전체');
    setKeyword('');
    setDebouncedKeyword('');
    if (isFestival) {
      setFestivalSigunguCode('');
      setFestivalSort('START_DATE_ASC');
      setFestivalSchedule('ALL');
      setMatchableOnly(false);
    } else {
      setSpotSigunguCode('');
      setSpotSort('TITLE_ASC');
    }
  }

  const regionOptions = (regions: RegionOption[]) => [
    { value: '', label: '전체 지역' },
    ...regions.map((region) => ({
      value: region.sigunguCode,
      label: `${region.name} (${region.count})`,
    })),
  ];

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

        {/* 2Depth 카테고리 칩 (축제는 카테고리 데이터가 없어 숨김 — raw_data의 cat1~3이 전부 null) */}
        {!isFestival && (
          <div className="hscroll -mx-5 flex gap-2 overflow-x-auto px-5 pb-1 [scrollbar-width:none]">
            {SPOT_CATEGORIES.map((c) => (
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

        {/* 조건 필터 행 */}
        <div className="-mt-1 flex flex-wrap items-center gap-1.5">
          {isFestival ? (
            <>
              <FilterSelect
                label="지역"
                value={festivalSigunguCode}
                options={regionOptions(festivalRegions)}
                onChange={setFestivalSigunguCode}
              />
              <FilterSelect
                label="일정"
                value={festivalSchedule}
                options={FESTIVAL_SCHEDULES.map((s) => ({ value: s.value, label: s.label }))}
                onChange={(value) => setFestivalSchedule(value as FestivalScheduleFilter)}
              />
              <FilterSelect
                label="정렬"
                value={festivalSort}
                options={FESTIVAL_SORTS.map((s) => ({ value: s.value, label: s.label }))}
                onChange={(value) => setFestivalSort(value as FestivalListSort)}
              />
              <button
                type="button"
                onClick={() => setMatchableOnly((current) => !current)}
                className={`flex shrink-0 items-center gap-1 rounded-lg border px-2.5 py-[5px] text-xs font-medium ${
                  matchableOnly
                    ? 'border-coral bg-coral/10 text-coral'
                    : 'border-line bg-sand text-ink/70'
                }`}
              >
                <SlidersHorizontal size={12} />
                매칭 가능
              </button>
            </>
          ) : (
            <>
              <FilterSelect
                label="지역"
                value={spotSigunguCode}
                options={regionOptions(spotRegions)}
                onChange={setSpotSigunguCode}
              />
              <FilterSelect
                label="정렬"
                value={spotSort}
                options={SPOT_SORTS.map((s) => ({ value: s.value, label: s.label }))}
                onChange={(value) => setSpotSort(value as TourPlaceListSort)}
              />
            </>
          )}
        </div>

        {/* 첫 페이지 로딩 */}
        {isInitialLoading && (
          <p className="py-12 text-center text-[13px] text-ink/45">불러오는 중이에요...</p>
        )}

        {/* 조회 실패 */}
        {isError && (
          <div className="flex flex-col items-center gap-3 py-12">
            <p className="text-sm text-ink/45">목록을 불러오지 못했어요.</p>
            <button
              type="button"
              onClick={() => void active.reload()}
              className="flex items-center gap-1.5 rounded-full border border-line bg-white px-5 py-2.5 text-[13px] font-bold text-ink"
            >
              <RotateCw size={14} />
              다시 시도
            </button>
          </div>
        )}

        {/* 결과 요약 */}
        {!isEmpty && !isInitialLoading && !isError && (
          <div className="-mt-1 flex items-center justify-between">
            <span className="text-[13px] text-ink/60">
              {isFestival ? `축제 ${resultCount}개` : `관광지 ${resultCount}개`}
            </span>
          </div>
        )}

        {/* 결과 목록 */}
        {!isEmpty && !isInitialLoading && !isError && (
          <div className="flex flex-col gap-2.5">
            {isFestival
              ? festivalCards.map((f) => <FestivalListItemCard key={f.id} festival={f} />)
              : visibleSpots.map((s) => <ExploreSpotItem key={s.id} spot={s} />)}
          </div>
        )}

        {/* 무한스크롤 sentinel — 화면에 들어오면 다음 20개를 요청한다 */}
        {!isInitialLoading && !isError && active.state.hasNext && (
          <div ref={sentinelRef} className="py-4 text-center text-xs text-ink/40">
            {active.state.loadingMore ? '더 불러오는 중...' : ' '}
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
