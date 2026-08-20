import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { CheckCircle2, Loader2, RefreshCw, Users, XCircle } from 'lucide-react';
import { ApiClientError } from '../api/apiClient';
import type { CurrentMatchGroup, MatchingRestriction, MatchTerminationReason } from '../api/matching';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import PrimaryButton from '../components/common/PrimaryButton';
import { useMatchingSession, type MatchingUiStatus } from '../hooks/useMatchingSession';
import { formatSeoulDateTime } from '../utils/dateTime';
import { remainingSeconds, stabilizeRemainingSeconds } from '../utils/serverClock';

function useCountdown(deadlineIso: string | null | undefined, serverOffsetMs: number, deadlineKey?: string) {
  const [remaining, setRemaining] = useState(0);
  const previousRef = useRef<{ deadlineKey: string; seconds: number } | null>(null);
  useEffect(() => {
    if (!deadlineIso) {
      previousRef.current = null;
      setRemaining(0);
      return;
    }
    const key = deadlineKey ?? deadlineIso;
    const tick = () => {
      const next = stabilizeRemainingSeconds(
        previousRef.current,
        key,
        remainingSeconds(deadlineIso, serverOffsetMs),
      );
      previousRef.current = { deadlineKey: key, seconds: next };
      setRemaining(next);
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [deadlineIso, deadlineKey, serverOffsetMs]);
  return remaining;
}

const fmt = (sec: number) => `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}`;

function positiveInteger(value: unknown): number | null {
  const parsed = typeof value === 'number' ? value : typeof value === 'string' ? Number(value) : NaN;
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

export function resolveFestivalId(
  locationState: unknown,
  terminalPoolFestivalId?: number | null,
  isDevelopment = import.meta.env.DEV,
  developmentFestivalId = import.meta.env.VITE_DEV_FESTIVAL_ID,
): number | null {
  if (locationState && typeof locationState === 'object' && 'festivalId' in locationState) {
    const fromLocation = positiveInteger(locationState.festivalId);
    if (fromLocation !== null) return fromLocation;
  }
  const fromTerminalPool = positiveInteger(terminalPoolFestivalId);
  if (fromTerminalPool !== null) return fromTerminalPool;
  return isDevelopment ? positiveInteger(developmentFestivalId) : null;
}

export function submitPoolEntry(
  enterPool: (
    festivalId: number,
    preferredGroupSize: 2 | 3 | 4,
    allowMinimumTwo: boolean,
  ) => Promise<boolean>,
  festivalId: number | null,
  preferredGroupSize: 2 | 3 | 4,
  allowMinimumTwo: boolean,
): Promise<boolean> | null {
  return festivalId === null
    ? null
    : enterPool(festivalId, preferredGroupSize, allowMinimumTwo);
}

export function readMatchRoomNotice(locationState: unknown): string | null {
  return locationState
    && typeof locationState === 'object'
    && 'matchRoomNotice' in locationState
    && typeof locationState.matchRoomNotice === 'string'
      ? locationState.matchRoomNotice
      : null;
}

export function consumeMatchRoomNotice(locationState: unknown): unknown {
  if (!locationState || typeof locationState !== 'object' || !('matchRoomNotice' in locationState)) {
    return locationState;
  }
  const { matchRoomNotice: _consumedNotice, ...remainingState } = locationState;
  return Object.keys(remainingState).length > 0 ? remainingState : null;
}

export default function MatchingConditionPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const [groupSize, setGroupSize] = useState<2 | 3 | 4>(3);
  const [allowMinimum, setAllowMinimum] = useState(false);
  const [matchRoomNotice, setMatchRoomNotice] = useState(() => readMatchRoomNotice(location.state));
  const {
    state,
    isSubmitting,
    isRetryFormOpen,
    refresh,
    beginRetry,
    enterPool,
    respond,
    cancelSearch,
    serverOffsetMs,
  } = useMatchingSession();
  const terminalPoolFestivalId =
    isRetryFormOpen
      && (state.status === 'CANCELLED' || state.status === 'EXPIRED' || state.status === 'COMPLETED')
      ? state.pool?.festivalId
      : null;
  const festivalId = resolveFestivalId(location.state, terminalPoolFestivalId);

  const searchDeadline = state.status === 'WAITING' ? state.pool?.searchExpiresAt : undefined;
  const proposalDeadline =
    state.status === 'INITIAL_PROPOSAL' || state.status === 'INSUFFICIENT_MEMBERS_PROPOSAL'
      ? state.proposal?.expiresAt
      : undefined;
  const cooldownDeadline = state.restriction?.cooldown.active ? state.restriction.cooldown.expiresAt : undefined;
  const completionDeadline = state.restriction?.completionLock.expiresAt;
  const searchRemaining = useCountdown(searchDeadline, serverOffsetMs, `search:${state.pool?.poolId ?? ''}:${searchDeadline ?? ''}`);
  const responseRemaining = useCountdown(
    proposalDeadline,
    serverOffsetMs,
    `proposal:${state.proposal?.attemptId ?? ''}:${state.proposal?.proposalRound ?? ''}:${proposalDeadline ?? ''}`,
  );
  const cooldownRemaining = useCountdown(cooldownDeadline, serverOffsetMs, `cooldown:${cooldownDeadline ?? ''}`);
  const completionRemaining = useCountdown(completionDeadline, serverOffsetMs, `completion:${completionDeadline ?? ''}`);

  useEffect(() => {
    if (readMatchRoomNotice(location.state) === null) return;
    navigate(`${location.pathname}${location.search}${location.hash}`, {
      replace: true,
      state: consumeMatchRoomNotice(location.state),
    });
  }, [location.hash, location.pathname, location.search, location.state, navigate]);

  useEffect(() => {
    const deadlineExpired =
      (searchDeadline && searchRemaining === 0) ||
      (proposalDeadline && responseRemaining === 0) ||
      (cooldownDeadline && cooldownRemaining === 0) ||
      (state.restriction?.completionLock.active && completionDeadline && completionRemaining === 0);
    if (deadlineExpired) void refresh();
  }, [
    cooldownDeadline,
    cooldownRemaining,
    completionDeadline,
    completionRemaining,
    proposalDeadline,
    refresh,
    responseRemaining,
    searchDeadline,
    searchRemaining,
  ]);

  const hasFestival = festivalId !== null;
  const cooldownActive = state.restriction?.cooldown.active === true;
  const completionLockActive = state.restriction?.completionLock.active === true;
  const canApply = hasFestival && !isSubmitting && !cooldownActive && !completionLockActive;
  const onStart = () => {
    setMatchRoomNotice(null);
    void submitPoolEntry(enterPool, festivalId, groupSize, allowMinimum);
  };
  const onRetry = () => {
    setMatchRoomNotice(null);
    beginRetry();
  };

  return (
    <MobileLayout>
      <PageHeader title="자동 매칭" noBack />
      <main className="flex flex-col gap-5 px-5 pb-10 pt-1">
        {matchRoomNotice && (
          <p role="status" className="rounded-2xl bg-coral/10 px-4 py-3 text-[14px] font-semibold text-coral">
            {matchRoomNotice}
          </p>
        )}
        <MatchBody
          status={state.status}
          error={state.error}
          isRetryFormOpen={isRetryFormOpen}
          group={state.group}
          groupSize={
            isRetryFormOpen
              ? groupSize
              : state.pool?.preferredGroupSize ?? state.proposal?.targetGroupSize ?? groupSize
          }
          allowMinimum={allowMinimum}
          hasFestival={hasFestival}
          canApply={canApply}
          isSubmitting={isSubmitting}
          searchRemaining={searchRemaining}
          responseRemaining={responseRemaining}
          cooldownRemaining={cooldownRemaining}
          cooldownActive={cooldownActive}
          terminationReason={state.pool?.terminationReason ?? null}
          completionLock={state.restriction?.completionLock ?? null}
          completionRemaining={completionRemaining}
          setGroupSize={setGroupSize}
          setAllowMinimum={setAllowMinimum}
          onStart={onStart}
          onAccept={() => void respond('ACCEPT')}
          onDecline={() => void respond('REJECT')}
          onStartWithCurrent={() => void respond('ACCEPT')}
          onCancelProposal={() => void respond('CANCEL_CURRENT_MEMBERS')}
          onCancelSearch={() => void cancelSearch()}
          onRetry={onRetry}
          onErrorRetry={() => void refresh()}
          onGoCheckIn={() => navigate('/check-in')}
          onEnterRoom={() => navigate('/match-room')}
        />
      </main>
    </MobileLayout>
  );
}

interface MatchBodyProps {
  status: MatchingUiStatus;
  error: ApiClientError | Error | null;
  isRetryFormOpen: boolean;
  group: CurrentMatchGroup | null;
  groupSize: number;
  allowMinimum: boolean;
  hasFestival: boolean;
  canApply: boolean;
  isSubmitting: boolean;
  searchRemaining: number;
  responseRemaining: number;
  cooldownRemaining: number;
  cooldownActive: boolean;
  terminationReason?: MatchTerminationReason | null;
  completionLock: MatchingRestriction['completionLock'] | null;
  completionRemaining: number;
  setGroupSize: (size: 2 | 3 | 4) => void;
  setAllowMinimum: (allow: boolean) => void;
  onStart: () => void;
  onAccept: () => void;
  onDecline: () => void;
  onStartWithCurrent: () => void;
  onCancelProposal: () => void;
  onCancelSearch: () => void;
  onRetry: () => void;
  onErrorRetry: () => void;
  onGoCheckIn: () => void;
  onEnterRoom: () => void;
}

export function MatchBody(props: MatchBodyProps) {
  const { status } = props;
  const retryableTerminal =
    props.isRetryFormOpen
    && (status === 'CANCELLED' || status === 'EXPIRED' || status === 'COMPLETED');
  if (status === 'IDLE' || retryableTerminal) {
    return (
      <IdleForm
        groupSize={props.groupSize}
        allowMinimum={props.allowMinimum}
        hasFestival={props.hasFestival}
        canApply={props.canApply}
        setGroupSize={props.setGroupSize}
        setAllowMinimum={props.setAllowMinimum}
        onStart={props.onStart}
        onGoCheckIn={props.onGoCheckIn}
      />
    );
  }
  if (status === 'WAITING' || status === 'LOCKED') {
    return (
      <SearchingCard
        locked={status === 'LOCKED'}
        remaining={props.searchRemaining}
        groupSize={props.groupSize}
        disabled={props.isSubmitting}
        onCancel={props.onCancelSearch}
      />
    );
  }
  if (status === 'INITIAL_PROPOSAL' || status === 'INSUFFICIENT_MEMBERS_PROPOSAL') {
    const partial = status === 'INSUFFICIENT_MEMBERS_PROPOSAL';
    return (
      <ProposalCard
        partial={partial}
        groupSize={props.groupSize}
        remaining={props.responseRemaining}
        disabled={props.isSubmitting}
        onAccept={partial ? props.onStartWithCurrent : props.onAccept}
        onCancel={partial ? props.onCancelProposal : props.onDecline}
      />
    );
  }
  if (status === 'RESPONSE_PENDING') return <ResponsePendingCard />;
  if (status === 'MATCHED' && props.group) {
    return <ConfirmedCard group={props.group} onEnterRoom={props.onEnterRoom} />;
  }
  if (status === 'COMPLETED' && props.completionLock) {
    return (
      <CompletedCard
        expiresAt={props.completionLock.expiresAt}
        active={props.completionLock.active}
        remaining={props.completionRemaining}
        onRetry={props.onRetry}
      />
    );
  }
  if (status === 'CANCELLED' || status === 'EXPIRED' || status === 'COOLDOWN') {
    const reason = terminationMessage(props.terminationReason ?? null)
      ?? (status === 'COOLDOWN'
        ? '잠시 후 다시 매칭을 신청할 수 있어요'
        : status === 'EXPIRED'
          ? '이번 매칭을 진행할 수 없어요.'
          : '이번 매칭을 진행할 수 없어요.');
    return (
      <CancelledCard
        reason={reason}
        cooldownActive={props.cooldownActive}
        cooldownRemaining={props.cooldownRemaining}
        onRetry={props.onRetry}
      />
    );
  }
  if (status === 'ERROR') {
    return (
      <ErrorCard
        error={props.error}
        onRetry={props.onErrorRetry}
        onGoCheckIn={props.onGoCheckIn}
      />
    );
  }
  return null;
}

export function countdownSeconds(deadlineIso: string, nowMs = Date.now()): number {
  return Math.max(0, Math.ceil((new Date(deadlineIso).getTime() - nowMs) / 1000));
}

export function terminationMessage(reason: MatchTerminationReason | null): string | null {
  switch (reason) {
    case 'SELF_REJECTED': return '매칭 제안을 거절했어요.';
    case 'NON_FAULT_TERMINATED': return '이번 매칭을 진행할 수 없어 종료됐어요.';
    case 'SELF_TIMEOUT': return '응답 시간이 지나 매칭이 종료됐어요.';
    case 'SYSTEM_TERMINATED': return '이번 매칭을 진행할 수 없어요.';
    default: return null;
  }
}

function CompletedCard({
  expiresAt,
  active,
  remaining,
  onRetry,
}: {
  expiresAt: string | null;
  active: boolean;
  remaining: number;
  onRetry: () => void;
}) {
  const remainingMinutes = Math.max(1, Math.ceil(remaining / 60));
  return (
    <section className="flex flex-col items-center gap-4 rounded-3xl bg-white p-8 text-center shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
      <div className="flex h-16 w-16 items-center justify-center rounded-full bg-teal/10">
        <CheckCircle2 size={30} className="text-teal" />
      </div>
      <div className="flex flex-col gap-1.5">
        <h2 className="text-[17px] font-bold text-ink">만남이 완료됐어요</h2>
        <p className="text-[13px] text-ink/55">모든 참여자가 도착했어요.</p>
      </div>
      <div className="w-full rounded-2xl bg-sand px-4 py-3 text-left">
        <p className="text-[12px] text-ink/50">매칭 유효 종료 시각</p>
        <p className="mt-1 text-[14px] font-semibold text-ink">{formatSeoulDateTime(expiresAt)}</p>
      </div>
      {active ? (
        <span className="rounded-full bg-teal/10 px-4 py-2 text-[13px] font-semibold text-teal tabular-nums">
          새로운 매칭은 {remainingMinutes}분 후 신청할 수 있어요. ({fmt(remaining)})
        </span>
      ) : (
        <p className="text-[13px] text-ink/55">새로운 매칭을 신청할 수 있어요.</p>
      )}
      <PrimaryButton disabled={active} onClick={onRetry} className="mt-1">
        다시 매칭하기
      </PrimaryButton>
    </section>
  );
}

// ── 1. 신청 전 ─────────────────────────────────────────
function IdleForm({
  groupSize,
  allowMinimum,
  hasFestival,
  canApply,
  setGroupSize,
  setAllowMinimum,
  onStart,
  onGoCheckIn,
}: {
  groupSize: number;
  allowMinimum: boolean;
  hasFestival: boolean;
  canApply: boolean;
  setGroupSize: (size: 2 | 3 | 4) => void;
  setAllowMinimum: (allow: boolean) => void;
  onStart: () => void;
  onGoCheckIn: () => void;
}) {
  return (
    <>
      {!hasFestival && (
        <div className="flex items-center justify-between gap-3 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
          <p className="text-[13px] leading-relaxed text-ink/65">
            매칭은 현재 축제/행사 현장에서만 가능해요. 먼저 체크인을 해주세요.
          </p>
          <button
            type="button"
            onClick={onGoCheckIn}
            className="shrink-0 rounded-xl bg-ink px-3 py-2 text-[13px] font-semibold text-white"
          >
            체크인하기
          </button>
        </div>
      )}
      <section className="flex flex-col gap-3">
        <h2 className="text-[17px] font-bold text-ink">희망 인원</h2>
        <div className="grid grid-cols-3 gap-2">
          {([2, 3, 4] as const).map((size) => (
            <button
              key={size}
              type="button"
              onClick={() => setGroupSize(size)}
              className={`rounded-2xl border-2 py-3 text-[15px] font-bold transition-colors ${
                groupSize === size ? 'border-coral bg-coral/10 text-coral' : 'border-line bg-white text-ink/60'
              }`}
            >
              {size}명
            </button>
          ))}
        </div>
      </section>
      <section className="flex items-center justify-between rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
        <div className="flex flex-col gap-0.5">
          <span className="text-[14px] font-semibold text-ink">2명만 모여도 진행</span>
          <span className="text-[12px] text-ink/50">목표 인원이 다 안 모여도 매칭을 시작해요</span>
        </div>
        <button
          type="button"
          role="switch"
          aria-checked={allowMinimum}
          onClick={() => setAllowMinimum(!allowMinimum)}
          className={`h-7 w-12 shrink-0 rounded-full transition-colors ${allowMinimum ? 'bg-coral' : 'bg-line'}`}
        >
          <span
            className={`block h-5 w-5 translate-y-1 rounded-full bg-white transition-transform ${
              allowMinimum ? 'translate-x-6' : 'translate-x-1'
            }`}
          />
        </button>
      </section>
      <PrimaryButton disabled={!canApply} onClick={onStart}>
        자동 매칭 신청
      </PrimaryButton>
    </>
  );
}

// ── 2·3. WAITING / LOCKED ─────────────────────────────
function SearchingCard({
  locked,
  remaining,
  groupSize,
  disabled,
  onCancel,
}: {
  locked: boolean;
  remaining: number;
  groupSize: number;
  disabled: boolean;
  onCancel: () => void;
}) {
  const [confirmOpen, setConfirmOpen] = useState(false);
  return (
    <>
      <section className="flex flex-col items-center gap-4 rounded-3xl bg-white p-8 text-center shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
        <div className={`flex h-16 w-16 items-center justify-center rounded-full ${locked ? 'bg-ink/10' : 'bg-coral/10'}`}>
          <Loader2 size={28} className={`animate-spin ${locked ? 'text-ink/50' : 'text-coral'}`} />
        </div>
        <div className="flex flex-col gap-1.5">
          <h2 className="text-[17px] font-bold text-ink">
            {locked ? '함께할 분을 확정하고 있어요' : '주변 여행자를 찾고 있어요'}
          </h2>
          <p className="text-[13px] text-ink/55">
            {locked ? '거의 다 됐어요, 잠시만 기다려주세요' : `목표 인원 ${groupSize}명 기준으로 탐색 중`}
          </p>
        </div>
        {!locked && (
          <span className="rounded-full bg-sand px-4 py-1.5 text-[13px] font-semibold text-ink/60 tabular-nums">
            남은 탐색 시간 {fmt(remaining)}
          </span>
        )}
        <button
          type="button"
          disabled={disabled}
          onClick={() => setConfirmOpen(true)}
          className="mt-1 text-[13px] font-semibold text-ink/40 underline underline-offset-2 disabled:opacity-40"
        >
          매칭 취소
        </button>
      </section>
      {confirmOpen && (
        <CancelConfirmDialog
          onConfirm={() => { setConfirmOpen(false); onCancel(); }}
          onDismiss={() => setConfirmOpen(false)}
        />
      )}
    </>
  );
}

function CancelConfirmDialog({ onConfirm, onDismiss }: { onConfirm: () => void; onDismiss: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-6" onClick={onDismiss}>
      <div
        className="flex w-full max-w-sm flex-col gap-4 rounded-3xl bg-white p-6"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="매칭 취소 확인"
      >
        <h3 className="text-center text-[16px] font-bold text-ink">매칭 탐색을 취소할까요?</h3>
        <p className="text-center text-[13px] text-ink/55">
          취소하면 잠시 후 다시 신청할 수 있어요.
        </p>
        <div className="flex gap-3">
          <button
            type="button"
            onClick={onDismiss}
            className="flex-1 rounded-2xl border border-line bg-white py-3 text-[15px] font-bold text-ink/55"
          >
            돌아가기
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="flex-1 rounded-2xl bg-coral py-3 text-[15px] font-bold text-white"
          >
            취소하기
          </button>
        </div>
      </div>
    </div>
  );
}

// ── 4·5. 매칭 제안 (정원 / 미달) ───────────────────────
function ProposalCard({
  partial,
  groupSize,
  remaining,
  disabled,
  onAccept,
  onCancel,
}: {
  partial: boolean;
  groupSize: number;
  remaining: number;
  disabled: boolean;
  onAccept: () => void;
  onCancel: () => void;
}) {
  return (
    <section className="flex flex-col gap-4 rounded-3xl bg-white p-5 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
      <div className="flex items-center gap-2">
        <Users size={18} className="text-coral" />
        <h2 className="text-[17px] font-bold text-ink">
          {partial ? '인원이 조금 부족해요' : '매칭 상대를 찾았어요'}
        </h2>
      </div>
      <p className="text-[13px] leading-relaxed text-ink/65">
        {partial
          ? '최소 인원 이상이 모였어요. 현재 인원으로 시작할까요?'
          : `목표 인원 ${groupSize}명이 모였어요. 함께 떠나볼까요?`}
      </p>
      <div className="flex items-center justify-between rounded-xl bg-sand px-3 py-2">
        <span className="text-[12px] text-ink/50">응답 제한시간</span>
        <span className="text-[13px] font-bold text-ink tabular-nums">{fmt(remaining)}</span>
      </div>
      <div className="flex gap-2.5">
        <button
          type="button"
          disabled={disabled}
          onClick={onCancel}
          className="flex-1 rounded-2xl border border-line bg-white py-3 text-[15px] font-bold text-ink/55 active:bg-sand disabled:opacity-50"
        >
          취소
        </button>
        <PrimaryButton className="flex-1" disabled={disabled} onClick={onAccept}>
          {partial ? '현재 인원으로 시작' : '수락'}
        </PrimaryButton>
      </div>
    </section>
  );
}

// ── 6. 응답 대기 ───────────────────────────────────────
function ResponsePendingCard() {
  return (
    <section className="flex flex-col items-center gap-4 rounded-3xl bg-white p-8 text-center shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
      <div className="flex h-16 w-16 items-center justify-center rounded-full bg-teal/10">
        <CheckCircle2 size={28} className="text-teal" />
      </div>
      <div className="flex flex-col gap-1.5">
        <h2 className="text-[17px] font-bold text-ink">내 응답을 보냈어요</h2>
        <p className="text-[13px] text-ink/55">다른 참여자의 응답을 기다리고 있어요. 곧 확정돼요.</p>
      </div>
      <Loader2 size={20} className="animate-spin text-ink/30" />
    </section>
  );
}

// ── 7. 매칭 확정 ───────────────────────────────────────
function ConfirmedCard({ group, onEnterRoom }: { group: CurrentMatchGroup; onEnterRoom: () => void }) {
  const time = group.confirmedAt
    ? new Date(group.confirmedAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
    : '';
  return (
    <section className="flex flex-col gap-4 rounded-3xl bg-white p-5 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
      <div className="flex items-center gap-2">
        <CheckCircle2 size={20} className="text-teal" />
        <h2 className="text-[17px] font-bold text-ink">매칭이 확정됐어요</h2>
      </div>
      <p className="text-[13px] text-ink/55">
        {group.confirmedMemberCount}명 확정 · {time}
      </p>
      <div className="flex flex-col gap-2.5">
        {group.members.map((member) => (
          <div key={member.memberId} className="flex items-center gap-3 rounded-2xl bg-sand p-3">
            {member.profileImageUrl ? (
              <img
                src={member.profileImageUrl}
                alt=""
                className="h-11 w-11 rounded-full object-cover"
                referrerPolicy="no-referrer"
              />
            ) : (
              <div className="flex h-11 w-11 items-center justify-center rounded-full bg-coral/15 text-[14px] font-bold text-coral">
                {member.nickname.slice(0, 1)}
              </div>
            )}
            <span className="text-[14px] font-semibold text-ink">{member.nickname}</span>
          </div>
        ))}
      </div>
      <PrimaryButton onClick={onEnterRoom}>상태방 들어가기</PrimaryButton>
    </section>
  );
}

// ── 8. CANCELLED / EXPIRED / cooldown ─────────────────
function CancelledCard({
  reason,
  cooldownActive,
  cooldownRemaining,
  onRetry,
}: {
  reason: string;
  cooldownActive: boolean;
  cooldownRemaining: number;
  onRetry: () => void;
}) {
  return (
    <section className="flex flex-col items-center gap-4 rounded-3xl bg-white p-8 text-center shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
      <div className="flex h-16 w-16 items-center justify-center rounded-full bg-ink/10">
        <XCircle size={28} className="text-ink/45" />
      </div>
      <div className="flex flex-col gap-1.5">
        <h2 className="text-[17px] font-bold text-ink">매칭이 종료됐어요</h2>
        <p className="text-[13px] text-ink/55">{reason}</p>
      </div>
      {cooldownActive && (
        <span className="rounded-full bg-sand px-4 py-1.5 text-[13px] font-semibold text-ink/50 tabular-nums">
          {fmt(cooldownRemaining)} 후 재신청 가능
        </span>
      )}
      <PrimaryButton disabled={cooldownActive} onClick={onRetry} className="mt-1">
        다시 신청하기
      </PrimaryButton>
    </section>
  );
}

// ── 9. 네트워크 오류 ───────────────────────────────────
function ErrorCard({
  error,
  onRetry,
  onGoCheckIn,
}: {
  error: ApiClientError | Error | null;
  onRetry: () => void;
  onGoCheckIn: () => void;
}) {
  const requiresCheckIn = error instanceof ApiClientError
    && error.code === 'MATCHING_INVALID_REQUEST'
    && error.message.includes('체크인');
  const meetingPointNotReady = error instanceof ApiClientError
    && error.code === 'MATCHING_MEETING_POINT_NOT_READY';
  return (
    <section className="flex flex-col items-center gap-4 rounded-3xl bg-white p-8 text-center shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
      <div className="flex h-16 w-16 items-center justify-center rounded-full bg-coral/10">
        <RefreshCw size={28} className="text-coral" />
      </div>
      <div className="flex flex-col gap-1.5">
        <h2 className="text-[17px] font-bold text-ink">
          {requiresCheckIn
            ? '축제 체크인이 필요해요'
            : meetingPointNotReady ? '만남 장소 준비 중이에요' : '요청을 처리하지 못했어요'}
        </h2>
        <p className="text-[13px] text-ink/55">
          {error?.message ?? '진행 중이던 매칭 정보는 유지돼요. 다시 시도해주세요.'}
        </p>
      </div>
      {!meetingPointNotReady && (
        <PrimaryButton onClick={requiresCheckIn ? onGoCheckIn : onRetry}>
          {requiresCheckIn ? '체크인하기' : '다시 시도'}
        </PrimaryButton>
      )}
    </section>
  );
}
