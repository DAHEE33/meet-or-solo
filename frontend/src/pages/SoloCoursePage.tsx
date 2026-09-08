import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Clock } from 'lucide-react';
import { festivalsApi, type SoloCourseResponse, type SoloCourseType } from '../api/festivals';
import { useCurrentCheckin, type CurrentCheckinState } from '../hooks/useCurrentCheckin';
import { contentTypeLabel } from '../utils/tourSpot';
import { readNumberFromLocationState } from '../utils/positiveInteger';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import ImagePlaceholder from '../components/common/ImagePlaceholder';
import { LoadingState } from '../components/common/Spinner';

export type SoloCourseFestivalResolution =
  | { status: 'loading' }
  | { status: 'ready'; festivalId: number | null };

/**
 * "어느 축제 기준으로 코스를 짤지" 결정한다.
 * FestivalDetailPage에서 넘어온 경우(location.state.festivalId)를 최우선으로 쓰고,
 * 없으면 실제 체크인한 축제를 기준으로 삼는다(원본 GPS 좌표는 저장하지 않으므로
 * "내 위치 기반"은 곧 "체크인한 축제 좌표 기반"을 의미한다 — docs/23 참고).
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

/** 예: 223 → "3시간 43분", 45 → "45분", 240 → "4시간". */
export function formatDurationLabel(totalMinutes: number): string {
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (hours === 0) return `${minutes}분`;
  if (minutes === 0) return `${hours}시간`;
  return `${hours}시간 ${minutes}분`;
}

const COURSE_TYPES: { value: SoloCourseType; label: string }[] = [
  { value: 'HALF', label: '반나절 코스' },
  { value: 'FULL', label: '하루 코스' },
];

export default function SoloCoursePage() {
  const location = useLocation();
  const { state: checkinState } = useCurrentCheckin();
  const resolution = resolveSoloCourseFestival(location.state, checkinState);
  const festivalId = resolution.status === 'ready' ? resolution.festivalId : null;

  const [type, setType] = useState<SoloCourseType>('HALF');
  const [festivalTitle, setFestivalTitle] = useState<string | null>(null);
  const [course, setCourse] = useState<SoloCourseResponse | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);

  useEffect(() => {
    if (festivalId === null) {
      setFestivalTitle(null);
      setCourse(null);
      return;
    }
    let mounted = true;
    setCourse(null);
    setLoadFailed(false);
    Promise.all([festivalsApi.getDetail(festivalId), festivalsApi.getSoloCourse(festivalId, type)])
      .then(([festival, soloCourse]) => {
        if (!mounted) return;
        setFestivalTitle(festival.title);
        setCourse(soloCourse);
      })
      .catch(() => {
        if (mounted) setLoadFailed(true);
      });
    return () => {
      mounted = false;
    };
  }, [festivalId, type]);

  if (resolution.status === 'loading') {
    return (
      <MobileLayout>
        <PageHeader title="솔로 코스 추천" noBack />
        <LoadingState className="py-16" />
      </MobileLayout>
    );
  }

  if (festivalId === null) {
    return (
      <MobileLayout>
        <PageHeader title="솔로 코스 추천" noBack />
        <main className="flex flex-col items-center gap-3 px-5 py-16 text-center">
          <p className="text-[14px] text-ink/55">체크인한 축제가 있어야 코스를 추천할 수 있어요.</p>
          {/* 어느 축제로 체크인할지 아직 모르므로 체크인 화면이 아니라 축제·관광 탐색으로 보낸다.
              /check-in으로 보내면 그 화면이 다시 /spots로 튕겨 화면이 한 번 깜빡인다. */}
          <Link
            to="/spots"
            className="rounded-xl bg-ink px-4 py-2.5 text-[14px] font-semibold text-white"
          >
            체크인할 축제 고르기
          </Link>
        </main>
      </MobileLayout>
    );
  }

  return (
    <MobileLayout>
      <PageHeader title="솔로 코스 추천" noBack />
      <main className="flex flex-col gap-5 px-5 pb-10 pt-1">
        {/* 반나절/하루 선택 */}
        <div className="grid grid-cols-2 rounded-2xl bg-white p-1">
          {COURSE_TYPES.map((option) => (
            <button
              key={option.value}
              type="button"
              onClick={() => setType(option.value)}
              className={`rounded-xl py-2.5 text-[14px] font-bold transition-colors ${
                type === option.value ? 'bg-teal text-white' : 'text-ink/50'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>

        <p className="text-[13px] text-ink/55">
          {festivalTitle ?? '체크인한 축제'} 기준으로 걸을 수 있는 순서로 이어붙인 코스예요.
        </p>

        {course === null && !loadFailed && (
          <p className="py-10 text-center text-[14px] text-ink/45">불러오는 중이에요...</p>
        )}
        {loadFailed && (
          <p className="py-10 text-center text-[14px] text-coral">
            코스를 불러오지 못했어요. 잠시 후 다시 시도해주세요.
          </p>
        )}
        {course !== null && course.stops.length === 0 && (
          <p className="py-10 text-center text-[14px] text-ink/45">
            반경 내에서 추천할 코스를 만들지 못했어요.
          </p>
        )}

        {course !== null && course.stops.length > 0 && (
          <>
            <span className="flex w-fit items-center gap-1.5 rounded-full bg-teal/10 px-3.5 py-2 text-[13px] font-semibold text-teal tabular-nums">
              <Clock size={14} />
              예상 소요 약 {formatDurationLabel(course.totalDurationMinutes)} (도보 {course.totalWalkMinutes}분 포함)
            </span>

            {/* 타임라인 */}
            <section className="flex flex-col gap-0">
              {course.stops.map((stop, index) => {
                const previousTitle = index === 0 ? festivalTitle ?? '체크인한 축제' : course.stops[index - 1].title;
                return (
                  <div key={stop.id} className="flex gap-3">
                    {/* 타임라인 축 */}
                    <div className="flex flex-col items-center">
                      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-teal text-xs font-bold text-white tabular-nums">
                        {stop.order}
                      </span>
                      {index < course.stops.length - 1 && <span className="w-px flex-1 bg-line" />}
                    </div>
                    {/* 관광지 카드 */}
                    <Link
                      to={`/spots/${stop.id}`}
                      className="mb-3 flex flex-1 gap-3 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)] active:scale-[0.99] transition-transform"
                    >
                      {stop.imageUrl ? (
                        <img
                          src={stop.imageUrl}
                          alt={`${stop.title} 사진`}
                          className="h-14 w-14 shrink-0 rounded-xl object-cover"
                        />
                      ) : (
                        <ImagePlaceholder label="사진" className="h-14 w-14 shrink-0 rounded-xl" />
                      )}
                      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                        <div className="flex items-center justify-between gap-2">
                          <span className="truncate text-[15px] font-semibold text-ink">{stop.title}</span>
                          <span className="shrink-0 text-xs text-ink/50 tabular-nums">
                            약 {stop.estimatedStayMinutes}분
                          </span>
                        </div>
                        <span className="text-xs text-ink/50">{contentTypeLabel(stop.contentTypeId)}</span>
                        <span className="text-[13px] text-ink/60">
                          {previousTitle}에서 도보 {stop.walkMinutesFromPrevious}분
                        </span>
                      </div>
                    </Link>
                  </div>
                );
              })}
            </section>
          </>
        )}
      </main>
    </MobileLayout>
  );
}
