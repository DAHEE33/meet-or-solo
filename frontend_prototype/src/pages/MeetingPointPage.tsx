import { useState } from 'react';
import { Footprints, MapPin } from 'lucide-react';
import { meetingPoints } from '../data/mock/meetingPoints';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import PrimaryButton from '../components/common/PrimaryButton';

export default function MeetingPointPage() {
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [confirmed, setConfirmed] = useState(false);
  const selected = meetingPoints.find((p) => p.id === selectedId);

  return (
    <MobileLayout>
      <PageHeader title="만남 포인트 추천" />
      <main className="flex flex-col gap-4 px-5 pb-10 pt-1">
        <p className="text-[13px] text-ink/55">
          두 사람의 현재 위치 사이, 찾기 쉬운 장소를 추천해요.
        </p>

        <div className="flex flex-col gap-3">
          {meetingPoints.map((p) => (
            <button
              key={p.id}
              type="button"
              onClick={() => {
                setSelectedId(p.id);
                setConfirmed(false);
              }}
              className={`flex flex-col gap-2 rounded-3xl border-2 bg-white p-4 text-left transition-colors ${
                selectedId === p.id ? 'border-coral' : 'border-transparent shadow-[0_1px_8px_rgba(34,48,62,0.05)]'
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="text-[15px] font-semibold text-ink">{p.name}</span>
                <span className="rounded-full bg-sand px-2 py-0.5 text-xs text-ink/55">
                  {p.category}
                </span>
              </div>
              <p className="text-[13px] leading-relaxed text-ink/65">{p.description}</p>
              <div className="flex items-center gap-3 text-xs text-ink/55 tabular-nums">
                <span className="flex items-center gap-1">
                  <MapPin size={12} /> {p.distanceKm}km
                </span>
                <span className="flex items-center gap-1">
                  <Footprints size={12} /> 도보 약 {p.walkMinutes}분
                </span>
              </div>
            </button>
          ))}
        </div>

        <PrimaryButton disabled={!selected} onClick={() => setConfirmed(true)}>
          {confirmed && selected ? `'${selected.name}' 만남 확정 ✓` : '이 장소로 만남 확정'}
        </PrimaryButton>
      </main>
    </MobileLayout>
  );
}
