import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { MapPinCheck } from 'lucide-react';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import { LoadingMore, LoadingState } from '../components/common/Spinner';
import { checkinApi, type CheckinHistory, type CheckinHistoryItem } from '../api/checkin';
import { formatSeoulDateTime } from '../utils/dateTime';
import { formatDistanceLabel } from '../utils/tourSpot';

type LoadStatus = 'LOADING' | 'READY' | 'ERROR';

/** 어느 축제에 다녀왔는지 돌아보는 화면이다. 체크인을 하는 `/check-in`과는 목적이 다르다. */
export default function CheckInHistoryPage() {
  const [status, setStatus] = useState<LoadStatus>('LOADING');
  const [history, setHistory] = useState<CheckinHistory | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setStatus('LOADING');
    checkinApi.getMyHistory(null, controller.signal)
      .then((loaded) => {
        if (controller.signal.aborted) return;
        setHistory(loaded);
        setStatus('READY');
      })
      .catch(() => {
        if (!controller.signal.aborted) setStatus('ERROR');
      });
    return () => controller.abort();
  }, [reloadToken]);

  const loadMore = useCallback(async () => {
    const cursor = history?.pagination.nextCursor;
    if (!cursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const next = await checkinApi.getMyHistory(cursor);
      setHistory((current) => (current
        ? { items: [...current.items, ...next.items], pagination: next.pagination }
        : next));
    } catch {
      // 추가 로딩 실패는 이미 보여준 목록을 지우지 않는다. 버튼을 눌러 다시 시도하면 된다.
    } finally {
      setLoadingMore(false);
    }
  }, [history, loadingMore]);

  return (
    <MobileLayout>
      <PageHeader title="체크인 기록" />
      <main className="flex flex-col gap-3 px-5 pb-10" aria-busy={status === 'LOADING'}>
        <p className="text-[13px] leading-5 text-ink/55">
          축제 현장에서 GPS로 인증한 기록이에요. 체크인은 1시간 동안 유효해요.
        </p>

        {status === 'LOADING' && (
          <div className="rounded-2xl bg-white p-5">
            <LoadingState className="py-2" message="체크인 기록을 불러오는 중이에요" />
          </div>
        )}

        {status === 'ERROR' && (
          <section role="alert" className="rounded-2xl bg-white p-5">
            <p className="text-sm text-coral">체크인 기록을 불러오지 못했어요.</p>
            <button
              type="button"
              onClick={() => setReloadToken((token) => token + 1)}
              className="mt-3 rounded-xl border border-line px-4 py-2 text-sm font-semibold"
            >
              다시 시도
            </button>
          </section>
        )}

        {status === 'READY' && history?.items.length === 0 && (
          <p className="rounded-2xl bg-white p-5 text-center text-sm text-ink/60">
            아직 체크인한 축제가 없어요
          </p>
        )}

        {status === 'READY' && history?.items.map((item) => (
          <CheckInHistoryCard key={item.checkinId} item={item} />
        ))}

        {status === 'READY' && history?.pagination.hasNext && (
          loadingMore
            ? <LoadingMore message="체크인 기록을 더 불러오는 중" />
            : (
              <button
                type="button"
                onClick={() => void loadMore()}
                className="rounded-2xl border border-line bg-white py-3 text-[14px] font-semibold text-ink/70"
              >
                더 보기
              </button>
            )
        )}
      </main>
    </MobileLayout>
  );
}

/** 상태별 배지. 유효한 체크인만 강조하고 나머지는 회색으로 남긴다. */
const STATUS_BADGES = {
  ACTIVE: { label: '체크인 중', className: 'bg-teal/15 text-teal' },
  EXPIRED: { label: '만료됨', className: 'bg-ink/10 text-ink/60' },
  CANCELLED: { label: '취소됨', className: 'bg-ink/10 text-ink/60' },
} as const;

export function CheckInHistoryCard({ item }: { item: CheckinHistoryItem }) {
  const badge = STATUS_BADGES[item.status];

  return (
    <Link
      to={`/festivals/${item.festivalId}`}
      className="flex flex-col gap-2 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]"
    >
      <div className="flex items-center gap-2">
        <h2 className="min-w-0 flex-1 truncate text-[15px] font-bold text-ink">
          {item.festivalTitle}
        </h2>
        {badge && (
          <span className={`shrink-0 rounded-full px-2 py-0.5 text-[11px] font-semibold ${badge.className}`}>
            {badge.label}
          </span>
        )}
      </div>

      <p className="text-[12px] text-ink/50">
        <time dateTime={item.checkedInAt}>{formatSeoulDateTime(item.checkedInAt)}</time>
        {' · 축제에서 '}
        {formatDistanceLabel(item.distanceMeters)}
      </p>

      {item.festivalAddress && (
        <p className="flex items-center gap-1 text-[12px] text-ink/45">
          <MapPinCheck aria-hidden="true" size={13} className="shrink-0" />
          <span className="min-w-0 truncate">{item.festivalAddress}</span>
        </p>
      )}
    </Link>
  );
}
