import { Link } from 'react-router-dom';
import { Users, HeartHandshake, MapPinCheck } from 'lucide-react';
import AdminHeader from '../components/admin/AdminHeader';
import AdminNav from '../components/admin/AdminNav';
import { LoadingState } from '../components/common/Spinner';
import { useAdminDashboard } from '../hooks/useAdminDashboard';
import type { AdminDashboardStats } from '../api/adminDashboard';
import { barWidthPercent, mergeRecentIssues } from '../utils/adminDashboard';
import { formatSeoulDateTime } from '../utils/dateTime';

/** 관리자 대시보드 — 유일하게 데스크톱 폭 레이아웃 사용 */
export default function AdminDashboardPage() {
  const { state, retry } = useAdminDashboard();

  return (
    <div className="min-h-screen bg-sand">
      <AdminHeader title="관리자 대시보드" />
      <AdminNav />

      <main className="mx-auto flex max-w-4xl flex-col gap-6 p-6">
        {state.status === 'loading' && <LoadingState message="집계를 불러오는 중이에요" />}
        {state.status === 'error' && (
          <div role="alert" className="rounded-2xl bg-white p-6 text-center shadow-sm">
            <p className="text-[15px] font-bold text-ink">집계를 불러오지 못했어요</p>
            <p className="mt-1 text-[13px] text-ink/55">
              잠시 후 다시 시도해주세요. 계속 실패하면 서버 상태를 확인해주세요.
            </p>
            <button
              type="button"
              onClick={retry}
              className="mt-4 rounded-xl bg-coral px-4 py-2 text-sm font-bold text-white"
            >
              다시 시도
            </button>
          </div>
        )}
        {state.status === 'loaded' && <DashboardContent stats={state.stats} />}
      </main>
    </div>
  );
}

export function DashboardContent({ stats }: { stats: AdminDashboardStats }) {
  const summaries = [
    { label: '총 사용자', value: stats.totalMemberCount, icon: Users, color: 'text-ink' },
    { label: '오늘 매칭', value: stats.todayMatchCount, icon: HeartHandshake, color: 'text-coral' },
    { label: '누적 체크인', value: stats.totalCheckinCount, icon: MapPinCheck, color: 'text-teal' },
  ];
  const issues = mergeRecentIssues(stats);
  // 1위 축제를 100%로 놓고 나머지를 상대 비교한다. 서버가 내림차순으로 내리지만
  // 순서에 기대지 않고 최대값을 직접 구한다.
  const maxCheckinCount = Math.max(0, ...stats.popularFestivals.map((f) => f.checkinCount));

  return (
    <>
      {/* 통계 카드 */}
      <section className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        {summaries.map(({ label, value, icon: Icon, color }) => (
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
        {/* 인기 축제 — 체크인은 축제에만 기록되므로 관광지가 아니라 축제를 센다 */}
        <section className="flex flex-col gap-4 rounded-2xl bg-white p-5 shadow-sm">
          <h2 className="text-[15px] font-bold text-ink">인기 축제 (체크인 기준)</h2>
          {stats.popularFestivals.length === 0 ? (
            <p className="py-4 text-[13px] text-ink/45">아직 체크인 기록이 없어요.</p>
          ) : (
            <div className="flex flex-col gap-3">
              {stats.popularFestivals.map((festival) => (
                <div key={festival.festivalId} className="flex items-center gap-3">
                  <span className="w-28 shrink-0 truncate text-[13px] text-ink/70" title={festival.title}>
                    {festival.title}
                  </span>
                  <div className="h-2 flex-1 overflow-hidden rounded-full bg-sand">
                    <div
                      className="h-full rounded-full bg-coral"
                      style={{ width: `${barWidthPercent(festival.checkinCount, maxCheckinCount)}%` }}
                    />
                  </div>
                  <span className="w-10 shrink-0 text-right text-[13px] text-ink/55 tabular-nums">
                    {festival.checkinCount}
                  </span>
                </div>
              ))}
            </div>
          )}
        </section>

        {/* 신고/문의 */}
        <section className="flex flex-col gap-4 rounded-2xl bg-white p-5 shadow-sm">
          <h2 className="text-[15px] font-bold text-ink">신고 / 문의</h2>
          {issues.length === 0 ? (
            <p className="py-4 text-[13px] text-ink/45">새로 들어온 신고나 문의가 없어요.</p>
          ) : (
            <ul className="flex flex-col gap-3">
              {issues.map((issue) => (
                <li
                  key={issue.key}
                  className="border-b border-line pb-3 last:border-0 last:pb-0"
                >
                  <Link to={issue.href} className="flex items-start gap-3">
                    <span
                      className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold ${
                        issue.kind === '신고' ? 'bg-coral/10 text-coral' : 'bg-teal/10 text-teal'
                      }`}
                    >
                      {issue.kind}
                    </span>
                    <span className="flex-1 text-[13px] leading-relaxed text-ink/70">
                      {issue.summary}
                      <span className="ml-1 text-ink/40">({issue.statusLabel})</span>
                    </span>
                    <span className="shrink-0 text-xs text-ink/40 tabular-nums">
                      {formatSeoulDateTime(issue.createdAt)}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  );
}
