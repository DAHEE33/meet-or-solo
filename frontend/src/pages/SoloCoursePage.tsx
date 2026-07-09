import { useState } from 'react';
import { Clock } from 'lucide-react';
import { soloCourses } from '../data/mock/soloCourses';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';

export default function SoloCoursePage() {
  const [type, setType] = useState<'half' | 'full'>('half');
  const course = soloCourses.find((c) => c.type === type)!;

  return (
    <MobileLayout>
      <PageHeader title="솔로 코스 추천" noBack />
      <main className="flex flex-col gap-5 px-5 pb-10 pt-1">
        {/* 반나절/하루 선택 */}
        <div className="grid grid-cols-2 rounded-2xl bg-white p-1">
          {(['half', 'full'] as const).map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => setType(t)}
              className={`rounded-xl py-2.5 text-[14px] font-bold transition-colors ${
                type === t ? 'bg-teal text-white' : 'text-ink/50'
              }`}
            >
              {t === 'half' ? '반나절 코스' : '하루 코스'}
            </button>
          ))}
        </div>

        {/* 코스 요약 */}
        <section className="flex flex-col gap-2 rounded-3xl bg-white p-5 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
          <h2 className="text-lg font-bold text-ink">{course.title}</h2>
          <p className="text-[13px] text-ink/60">{course.summary}</p>
          <span className="flex items-center gap-1 text-[13px] text-ink/60 tabular-nums">
            <Clock size={14} /> 예상 소요 약 {course.durationHours}시간
          </span>
          <p className="mt-1 rounded-xl bg-teal/10 px-3 py-2.5 text-[13px] leading-relaxed text-teal">
            {course.reason}
          </p>
          <div className="flex flex-wrap gap-1.5">
            {course.tags.map((t) => (
              <span key={t} className="rounded-md bg-sand px-2 py-0.5 text-xs text-ink/60">
                #{t}
              </span>
            ))}
          </div>
        </section>

        {/* 타임라인 */}
        <section className="flex flex-col gap-0">
          {course.stops.map((stop, i) => (
            <div key={stop.order} className="flex gap-3">
              {/* 타임라인 축 */}
              <div className="flex flex-col items-center">
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-teal text-xs font-bold text-white tabular-nums">
                  {stop.order}
                </span>
                {i < course.stops.length - 1 && <span className="w-px flex-1 bg-line" />}
              </div>
              {/* 관광지 카드 */}
              <div className="mb-3 flex flex-1 flex-col gap-1 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
                <div className="flex items-center justify-between">
                  <span className="text-[15px] font-semibold text-ink">{stop.spotName}</span>
                  <span className="text-xs text-ink/50 tabular-nums">약 {stop.stayMinutes}분</span>
                </div>
                <p className="text-[13px] text-ink/60">{stop.note}</p>
              </div>
            </div>
          ))}
        </section>
      </main>
    </MobileLayout>
  );
}
