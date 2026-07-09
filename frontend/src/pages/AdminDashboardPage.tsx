import { Users, HeartHandshake, MapPinCheck } from 'lucide-react';
import { adminStats } from '../data/mock/adminStats';

function formatDate(iso: string) {
  const d = new Date(iso);
  return `${d.getMonth() + 1}.${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(
    d.getMinutes(),
  ).padStart(2, '0')}`;
}

/** 관리자 대시보드 — 유일하게 데스크톱 폭 레이아웃 사용 */
export default function AdminDashboardPage() {
  const maxCount = Math.max(...adminStats.popularSpots.map((s) => s.count));

  const stats = [
    { label: '총 사용자', value: adminStats.totalUsers, icon: Users, color: 'text-ink' },
    { label: '오늘 매칭', value: adminStats.todayMatches, icon: HeartHandshake, color: 'text-coral' },
    { label: '누적 체크인', value: adminStats.totalCheckIns, icon: MapPinCheck, color: 'text-teal' },
  ];

  return (
    <div className="min-h-screen bg-sand">
      <header className="border-b border-line bg-white px-6 py-4">
        <h1 className="text-lg font-bold text-ink">
          meet·or·solo <span className="ml-2 text-sm font-medium text-ink/45">관리자 대시보드</span>
        </h1>
      </header>

      <main className="mx-auto flex max-w-4xl flex-col gap-6 p-6">
        {/* 통계 카드 */}
        <section className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          {stats.map(({ label, value, icon: Icon, color }) => (
            <div key={label} className="flex flex-col gap-2 rounded-2xl bg-white p-5 shadow-sm">
              <Icon size={20} className={color} />
              <span className="text-2xl font-bold text-ink tabular-nums">
                {value.toLocaleString()}
              </span>
              <span className="text-[13px] text-ink/50">{label}</span>
            </div>
          ))}
        </section>

        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          {/* 인기 관광지 */}
          <section className="flex flex-col gap-4 rounded-2xl bg-white p-5 shadow-sm">
            <h2 className="text-[15px] font-bold text-ink">인기 관광지 (체크인 기준)</h2>
            <div className="flex flex-col gap-3">
              {adminStats.popularSpots.map((s) => (
                <div key={s.name} className="flex items-center gap-3">
                  <span className="w-28 shrink-0 truncate text-[13px] text-ink/70">{s.name}</span>
                  <div className="h-2 flex-1 overflow-hidden rounded-full bg-sand">
                    <div
                      className="h-full rounded-full bg-coral"
                      style={{ width: `${(s.count / maxCount) * 100}%` }}
                    />
                  </div>
                  <span className="w-10 shrink-0 text-right text-[13px] text-ink/55 tabular-nums">
                    {s.count}
                  </span>
                </div>
              ))}
            </div>
          </section>

          {/* 신고/문의 */}
          <section className="flex flex-col gap-4 rounded-2xl bg-white p-5 shadow-sm">
            <h2 className="text-[15px] font-bold text-ink">신고 / 문의</h2>
            <div className="flex flex-col gap-3">
              {adminStats.reports.map((r) => (
                <div key={r.id} className="flex items-start gap-3 border-b border-line pb-3 last:border-0 last:pb-0">
                  <span
                    className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold ${
                      r.type === '신고' ? 'bg-coral/10 text-coral' : 'bg-teal/10 text-teal'
                    }`}
                  >
                    {r.type}
                  </span>
                  <p className="flex-1 text-[13px] leading-relaxed text-ink/70">{r.content}</p>
                  <span className="shrink-0 text-xs text-ink/40 tabular-nums">{formatDate(r.createdAt)}</span>
                </div>
              ))}
            </div>
          </section>
        </div>
      </main>
    </div>
  );
}
