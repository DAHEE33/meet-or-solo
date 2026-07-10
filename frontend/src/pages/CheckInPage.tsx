import { useState } from 'react';
import { MapPin, CheckCircle2 } from 'lucide-react';
import type { CheckInRecord } from '../types';
import { checkInRecords } from '../data/mock/checkIns';
import { mockUser } from '../data/mock/user';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import PrimaryButton from '../components/common/PrimaryButton';
import { formatSeoulDateTime } from '../utils/dateTime';

export default function CheckInPage() {
  const [records, setRecords] = useState<CheckInRecord[]>(checkInRecords);
  const [checkedIn, setCheckedIn] = useState(false);

  const handleCheckIn = () => {
    if (checkedIn) return;
    setRecords((prev) => [
      {
        id: Date.now(),
        spotName: mockUser.currentAreaName,
        checkedInAt: new Date().toISOString(),
        memo: '앱에서 체크인',
      },
      ...prev,
    ]);
    setCheckedIn(true);
  };

  return (
    <MobileLayout>
      <PageHeader title="체크인" />
      <main className="flex flex-col gap-5 px-5 pb-10 pt-1">
        {/* 현재 장소 */}
        <section className="flex flex-col items-center gap-3 rounded-3xl bg-white p-6 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
          <span className="flex items-center gap-1 text-xs text-ink/50">
            <MapPin size={13} className="text-coral" /> 현재 위치 (mock)
          </span>
          <h2 className="text-xl font-bold text-ink">{mockUser.currentAreaName}</h2>
          <PrimaryButton onClick={handleCheckIn} tone={checkedIn ? 'ink' : 'coral'} disabled={checkedIn}>
            {checkedIn ? '체크인 완료 ✓' : '여기에 체크인'}
          </PrimaryButton>
        </section>

        {/* 체크인 기록 */}
        <section className="flex flex-col gap-3">
          <h2 className="text-[17px] font-bold text-ink">체크인 기록</h2>
          <div className="flex flex-col gap-2.5">
            {records.map((r) => (
              <div
                key={r.id}
                className="flex items-center gap-3 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]"
              >
                <CheckCircle2 size={20} className="shrink-0 text-teal" />
                <div className="flex min-w-0 flex-1 flex-col">
                  <span className="text-[14px] font-semibold text-ink">{r.spotName}</span>
                  {r.memo && <span className="text-xs text-ink/50">{r.memo}</span>}
                </div>
                <span className="shrink-0 text-xs text-ink/45 tabular-nums">
                  {formatSeoulDateTime(r.checkedInAt)}
                </span>
              </div>
            ))}
          </div>
        </section>
      </main>
    </MobileLayout>
  );
}
