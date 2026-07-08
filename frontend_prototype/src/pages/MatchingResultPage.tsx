import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MapPin } from 'lucide-react';
import { matchCandidates } from '../data/mock/matchCandidates';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import PrimaryButton from '../components/common/PrimaryButton';

export default function MatchingResultPage() {
  const navigate = useNavigate();
  const [requested, setRequested] = useState<number[]>([]);

  const toggleRequest = (id: number) =>
    setRequested((prev) => (prev.includes(id) ? prev : [...prev, id]));

  return (
    <MobileLayout>
      <PageHeader title="매칭 결과" />
      <main className="flex flex-col gap-4 px-5 pb-10 pt-1">
        <p className="text-[13px] text-ink/55">
          조건에 맞는 여행자 {matchCandidates.length}명을 찾았어요.
        </p>

        <div className="flex flex-col gap-3">
          {matchCandidates.map((c) => (
            <article
              key={c.id}
              className="flex flex-col gap-3 rounded-3xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]"
            >
              <div className="flex items-center gap-3">
                <div className="flex h-12 w-12 items-center justify-center rounded-full bg-sand text-[15px] font-bold text-ink/60">
                  {c.nickname.slice(0, 1)}
                </div>
                <div className="flex min-w-0 flex-1 flex-col">
                  <span className="text-[15px] font-semibold text-ink">{c.nickname}</span>
                  <span className="flex items-center gap-1 text-xs text-ink/50 tabular-nums">
                    {c.ageBand} · <MapPin size={11} /> {c.distanceKm}km
                  </span>
                </div>
                <span className="rounded-full bg-coral/10 px-2.5 py-1 text-[13px] font-bold text-coral tabular-nums">
                  {c.matchRate}%
                </span>
              </div>
              <p className="text-[13px] leading-relaxed text-ink/70">{c.intro}</p>
              <div className="flex flex-wrap gap-1.5">
                {[...c.travelStyles, ...c.interests].map((t) => (
                  <span key={t} className="rounded-md bg-sand px-2 py-0.5 text-xs text-ink/60">
                    #{t}
                  </span>
                ))}
              </div>
              <button
                type="button"
                onClick={() => toggleRequest(c.id)}
                disabled={requested.includes(c.id)}
                className={`rounded-xl py-2.5 text-[14px] font-bold transition-colors ${
                  requested.includes(c.id)
                    ? 'bg-sand text-ink/40'
                    : 'bg-ink text-white active:scale-[0.99]'
                }`}
              >
                {requested.includes(c.id) ? '요청 보냄 ✓' : '함께 가기 요청'}
              </button>
            </article>
          ))}
        </div>

        <PrimaryButton onClick={() => navigate('/meeting-point')}>
          만남 포인트 추천 받기
        </PrimaryButton>
      </main>
    </MobileLayout>
  );
}
