import { useCallback, useEffect, useRef, useState } from 'react';
import { Flag } from 'lucide-react';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import { LoadingMore, LoadingState } from '../components/common/Spinner';
import { matchHistoryApi, type MatchHistory, type MatchHistoryItem } from '../api/matchHistory';
import { useMatchReport } from '../hooks/useMatchReport';
import { ReportDialog } from './MatchRoomPage';
import { formatSeoulDateTime } from '../utils/dateTime';

type LoadStatus = 'LOADING' | 'READY' | 'ERROR';

/** 만남이 끝난 뒤에도 신고할 수 있는 진입점이다(docs/19 4.10). */
export default function MatchHistoryPage() {
  const [status, setStatus] = useState<LoadStatus>('LOADING');
  const [history, setHistory] = useState<MatchHistory | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const report = useMatchReport();
  const openerRef = useRef<HTMLButtonElement | null>(null);
  // 같은 상대와 여러 번 만났을 수 있으므로 dialog를 연 시점의 만남을 그대로 들고 간다.
  // 상대 ID로 목록을 되짚으면 다른 만남에 신고가 붙을 수 있다.
  const [reportGroupId, setReportGroupId] = useState<number | null>(null);
  // 신고 성공 뒤 목록을 다시 읽어 "신고됨" 표시를 반영한다.
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setStatus('LOADING');
    matchHistoryApi.getMine(null, controller.signal)
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
      const next = await matchHistoryApi.getMine(cursor);
      setHistory((current) => (current
        ? { items: [...current.items, ...next.items], pagination: next.pagination }
        : next));
    } catch {
      // 추가 로딩 실패는 이미 보여준 목록을 지우지 않는다. 버튼을 눌러 다시 시도하면 된다.
    } finally {
      setLoadingMore(false);
    }
  }, [history, loadingMore]);

  const submitReport = async (groupId: number) => {
    const submitted = await report.submit(groupId);
    if (submitted) setReloadToken((token) => token + 1);
  };

  return (
    <MobileLayout>
      <PageHeader title="매칭 기록" />
      <main className="flex flex-col gap-3 px-5 pb-10" aria-busy={status === 'LOADING'}>
        <p className="text-[13px] leading-5 text-ink/55">
          만남이 끝난 뒤에도 종료 후 14일 안에는 신고할 수 있어요.
        </p>

        {status === 'LOADING' && (
          <div className="rounded-2xl bg-white p-5">
            <LoadingState className="py-2" message="매칭 기록을 불러오는 중이에요" />
          </div>
        )}

        {status === 'ERROR' && (
          <section role="alert" className="rounded-2xl bg-white p-5">
            <p className="text-sm text-coral">매칭 기록을 불러오지 못했어요.</p>
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
            아직 완료된 매칭이 없어요
          </p>
        )}

        {status === 'READY' && history?.items.map((item) => (
          <MatchHistoryCard
            key={item.groupId}
            item={item}
            onOpenReport={(target, opener) => {
              openerRef.current = opener;
              setReportGroupId(item.groupId);
              report.open(target);
            }}
          />
        ))}

        {status === 'READY' && history?.pagination.hasNext && (
          loadingMore
            ? <LoadingMore message="매칭 기록을 더 불러오는 중" />
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

        {report.state.successMessage && (
          <div role="status" aria-live="polite" className="rounded-2xl bg-ink px-4 py-3 text-sm text-white">
            {report.state.successMessage}
            <button type="button" className="ml-2 underline" onClick={report.clearSuccess}>닫기</button>
          </div>
        )}
      </main>

      <ReportDialog
        state={report.state}
        onSelectReason={report.selectReason}
        onConfirm={report.confirm}
        onBack={report.back}
        onClose={() => {
          report.close();
          setReportGroupId(null);
          queueMicrotask(() => openerRef.current?.focus());
        }}
        onSubmit={() => {
          if (reportGroupId !== null) void submitReport(reportGroupId);
        }}
      />
    </MobileLayout>
  );
}

export function MatchHistoryCard({
  item,
  onOpenReport,
}: {
  item: MatchHistoryItem;
  onOpenReport: (
    target: { memberId: number; nickname: string },
    opener: HTMLButtonElement,
  ) => void;
}) {
  return (
    <article className="flex flex-col gap-3 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
      <header className="flex flex-col gap-1">
        <div className="flex items-center gap-2">
          <h2 className="min-w-0 flex-1 truncate text-[15px] font-bold text-ink">
            {item.festivalTitle}
          </h2>
          {item.status === 'CANCELLED' && (
            <span className="shrink-0 rounded-full bg-ink/10 px-2 py-0.5 text-[11px] font-semibold text-ink/60">
              취소됨
            </span>
          )}
        </div>
        <p className="text-[12px] text-ink/50">
          <time dateTime={item.endedAt}>{formatSeoulDateTime(item.endedAt)}</time>
          {' · '}
          {item.confirmedMemberCount}명
          {item.meetingPlaceName ? ` · ${item.meetingPlaceName}` : ''}
        </p>
        <p className="text-[12px] font-semibold text-ink/45">
          {item.reportable
            ? `신고 가능 (${formatSeoulDateTime(item.reportableUntil)}까지)`
            : '신고 기간 종료'}
        </p>
      </header>

      {item.members.length === 0
        ? <p className="text-[13px] text-ink/50">함께한 상대 정보가 없어요.</p>
        : item.members.map((member) => (
          <div key={member.memberId} className="flex items-center gap-3">
            {member.profileImageUrl ? (
              <img
                src={member.profileImageUrl}
                alt={`${member.nickname} 프로필`}
                className="h-10 w-10 rounded-full object-cover"
                referrerPolicy="no-referrer"
              />
            ) : (
              <div aria-hidden="true" className="flex h-10 w-10 items-center justify-center rounded-full bg-coral/15 text-[13px] font-bold text-coral">
                {member.nickname.slice(0, 1)}
              </div>
            )}
            <span className="min-w-0 flex-1 truncate text-[14px] font-semibold text-ink">
              {member.nickname}
            </span>
            {member.reported ? (
              <span className="shrink-0 rounded-xl bg-coral/10 px-3 py-2 text-[12px] font-semibold text-coral">
                신고됨
              </span>
            ) : (
              <button
                type="button"
                disabled={!item.reportable}
                onClick={(event) => onOpenReport(
                  { memberId: member.memberId, nickname: member.nickname },
                  event.currentTarget,
                )}
                className="shrink-0 rounded-xl px-3 py-2 text-[12px] font-semibold text-ink/55 hover:bg-coral/10 hover:text-coral disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-ink/55"
                aria-label={`${member.nickname}님 신고하기`}
              >
                <Flag aria-hidden="true" size={14} className="mr-1 inline" />
                신고하기
              </button>
            )}
          </div>
        ))}
    </article>
  );
}
