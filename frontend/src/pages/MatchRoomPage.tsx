import { CheckCircle2, Clock3, Loader2, RefreshCw, Users } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type {
  ArrivalMinutesSelection,
  CurrentMatchGroup,
  MatchCancellationReason,
} from '../api/matching';
import PrimaryButton from '../components/common/PrimaryButton';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import KakaoMeetingPointMap from '../components/matching/KakaoMeetingPointMap';
import { useMatchRoom, type MatchRoomState } from '../hooks/useMatchRoom';
import { formatSeoulDateTime } from '../utils/dateTime';

export default function MatchRoomPage() {
  const navigate = useNavigate();
  const { state, refresh, selectArrivalTime, arrive, cancelParticipation } = useMatchRoom();
  const [nowEpochMs, setNowEpochMs] = useState(() => Date.now());

  useEffect(() => {
    const redirectPath = matchRoomRedirectPath(state.status);
    if (redirectPath) {
      navigate(redirectPath, {
        replace: true,
        state: state.terminationNotice
          ? { matchRoomNotice: state.terminationNotice }
          : undefined,
      });
    }
  }, [navigate, state.status, state.terminationNotice]);

  useEffect(() => {
    const timer = window.setInterval(() => setNowEpochMs(Date.now()), 1_000);
    return () => window.clearInterval(timer);
  }, []);

  return (
    <MobileLayout>
      <PageHeader title="매칭 상태방" />
      <main className="flex flex-col gap-5 px-5 pb-10 pt-1">
        <MatchRoomContent
          state={state}
          onRetry={() => void refresh()}
          onSelectArrivalTime={selectArrivalTime}
          onArrive={arrive}
          onCancel={cancelParticipation}
          nowEpochMs={nowEpochMs}
        />
      </main>
      <ArrivalChangeSnackbar message={state.arrivalChangeNotice} />
    </MobileLayout>
  );
}

export function matchRoomRedirectPath(status: MatchRoomState['status']): string | null {
  return status === 'EMPTY' ? '/matching' : null;
}

export function MatchRoomContent({
  state,
  onRetry,
  onSelectArrivalTime,
  onArrive,
  onCancel,
  nowEpochMs,
}: {
  state: MatchRoomState;
  onRetry: () => void;
  onSelectArrivalTime: (minutes: ArrivalMinutes) => Promise<boolean>;
  onArrive?: () => Promise<boolean>;
  onCancel?: (reason: MatchCancellationReason) => Promise<boolean>;
  nowEpochMs?: number;
}) {
  if (state.status === 'LOADING' || state.status === 'EMPTY') {
    return (
      <section role="status" className="flex flex-col items-center gap-3 rounded-3xl bg-white p-8 text-center">
        <Loader2 size={28} className="animate-spin text-coral" />
        <p className="text-[14px] text-ink/60">매칭방 정보를 불러오고 있어요</p>
      </section>
    );
  }
  if (state.status === 'ERROR' || !state.group) {
    return (
      <section role="alert" className="flex flex-col items-center gap-4 rounded-3xl bg-white p-8 text-center">
        <RefreshCw size={28} className="text-coral" />
        <div>
          <h2 className="text-[17px] font-bold text-ink">매칭방 정보를 불러오지 못했어요</h2>
          <p className="mt-1 text-[13px] text-ink/55">확정된 매칭은 유지돼요. 다시 시도해주세요.</p>
        </div>
        <PrimaryButton onClick={onRetry}>다시 시도</PrimaryButton>
      </section>
    );
  }
  return (
    <CurrentGroupRoom
      group={state.group}
      events={state.events}
      eventsError={state.eventsError}
      isSubmitting={state.isSubmitting}
      actionError={state.actionError}
      onRetry={onRetry}
      onSelectArrivalTime={onSelectArrivalTime}
      onArrive={onArrive ?? (() => Promise.resolve(false))}
      onCancel={onCancel ?? (() => Promise.resolve(false))}
      nowEpochMs={nowEpochMs}
    />
  );
}

export function ArrivalChangeSnackbar({ message }: { message?: string | null }) {
  if (!message) return null;
  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed bottom-24 left-1/2 z-50 w-[calc(100%-2.5rem)] max-w-[390px] -translate-x-1/2 rounded-2xl bg-ink px-4 py-3 text-center text-[14px] font-semibold text-white shadow-lg"
    >
      {message}
    </div>
  );
}

export type ArrivalMinutes = ArrivalMinutesSelection;

export function CurrentGroupRoom({
  group,
  events,
  eventsError,
  isSubmitting,
  actionError,
  onRetry,
  onSelectArrivalTime,
  onArrive,
  onCancel,
  nowEpochMs,
}: {
  group: CurrentMatchGroup;
  events: MatchRoomState['events'];
  eventsError: Error | null;
  isSubmitting: boolean;
  actionError: Error | null;
  onRetry: () => void;
  onSelectArrivalTime: (minutes: ArrivalMinutes) => Promise<boolean>;
  onArrive: () => Promise<boolean>;
  onCancel: (reason: MatchCancellationReason) => Promise<boolean>;
  nowEpochMs?: number;
}) {
  const effectiveNowEpochMs = nowEpochMs ?? Date.parse(group.confirmedAt);
  const statusText = group.status === 'IN_PROGRESS' ? '미팅 진행 중' : '만남 준비 중';
  const period = formatFestivalPeriod(group.festival.eventStartDate, group.festival.eventEndDate);
  const currentMember = group.members.find((member) => member.memberId === group.currentMemberId);
  const canArrive = currentMember?.status === 'JOINED'
    || currentMember?.status === 'ARRIVAL_TIME_SELECTED';
  const arrivalDeadlineReached = effectiveNowEpochMs >= Date.parse(group.arrivalDeadlineAt);
  const canSelectArrivalTime = canArrive && !arrivalDeadlineReached;
  const estimatedArrivalAt = getEstimatedArrivalAt(currentMember);
  const estimatedArrivalEpochMs = estimatedArrivalAt ? Date.parse(estimatedArrivalAt) : null;
  const estimatedArrivalPassed = estimatedArrivalEpochMs !== null
    && effectiveNowEpochMs > estimatedArrivalEpochMs;
  return (
    <>
      <section className="flex flex-col gap-3 rounded-3xl bg-white p-5 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
        <div className="flex items-center gap-2">
          <CheckCircle2 size={20} className="text-teal" />
          <h2 className="text-[17px] font-bold text-ink">매칭이 확정됐어요</h2>
        </div>
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-[13px]">
          <dt className="text-ink/50">확정 시각</dt><dd className="text-right font-semibold text-ink">{formatSeoulDateTime(group.confirmedAt)}</dd>
          <dt className="text-ink/50">최종 도착 마감</dt><dd className="text-right font-semibold text-ink">{formatSeoulDateTime(group.arrivalDeadlineAt)}</dd>
          <dt className="text-ink/50">전체 남은 시간</dt><dd className="text-right font-semibold text-coral">{formatRemainingTime(group.arrivalDeadlineAt, effectiveNowEpochMs)}</dd>
          <dt className="text-ink/50">확정 인원</dt><dd className="text-right font-semibold text-ink">{group.confirmedMemberCount}명</dd>
          <dt className="text-ink/50">현재 참여 인원</dt><dd className="text-right font-semibold text-ink">{group.currentMemberCount}명</dd>
          <dt className="text-ink/50">현재 상태</dt><dd className="text-right font-semibold text-teal">{statusText}</dd>
        </dl>
      </section>

      {group.meetingPoint && (
        <section aria-labelledby="meeting-point-title" className="flex flex-col gap-3 rounded-3xl bg-white p-5 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
          <div>
            <h2 id="meeting-point-title" className="text-[16px] font-bold text-ink">만남 장소</h2>
            <p className="mt-1 text-[12px] text-teal">운영자가 확인한 공개 장소예요.</p>
          </div>
          <div>
            <p className="text-[15px] font-semibold text-ink">{group.meetingPoint.name}</p>
            <p className="mt-1 text-[13px] text-ink/60">{group.meetingPoint.address}</p>
          </div>
          <KakaoMeetingPointMap meetingPoint={group.meetingPoint} />
          <p className="rounded-xl bg-sand/60 px-3 py-2 text-[12px] text-ink/60">
            도착 인정 반경은 장소 핀 기준 {group.meetingPoint.arrivalRadiusMeters}m예요.
          </p>
        </section>
      )}

      {canArrive && !arrivalDeadlineReached && (
        <section className="flex flex-col gap-3 rounded-3xl bg-white p-5 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
          <details>
            <summary className={`cursor-pointer list-none rounded-2xl bg-coral px-4 py-3 text-center text-[15px] font-bold text-white ${isSubmitting ? 'pointer-events-none opacity-50' : ''}`}>
              도착했어요
            </summary>
            <div role="dialog" aria-modal="true" aria-labelledby="arrival-confirm-title" className="rounded-2xl border border-line bg-sand/40 p-4">
              <h2 id="arrival-confirm-title" className="text-[16px] font-bold text-ink">
                축제 만남 장소에 도착했나요?
              </h2>
              <div className="mt-4 grid grid-cols-2 gap-2">
                <button type="button" disabled={isSubmitting} onClick={(event) => {
                  const details = event.currentTarget.closest('details');
                  if (details) details.open = false;
                }} className="rounded-2xl border border-line px-3 py-3 font-semibold">
                  아직이에요
                </button>
                <button
                  type="button"
                  disabled={isSubmitting}
                  onClick={(event) => {
                    const details = event.currentTarget.closest('details');
                    void onArrive().then((success) => {
                    if (success && details) details.open = false;
                    });
                  }}
                  className="rounded-2xl bg-coral px-3 py-3 font-bold text-white disabled:opacity-50"
                >
                  {isSubmitting ? '처리 중...' : '도착했어요'}
                </button>
              </div>
            </div>
          </details>
        </section>
      )}

      {canSelectArrivalTime && (
        <section className="flex flex-col gap-3 rounded-3xl bg-white p-5 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
          <details>
            <summary className={`cursor-pointer list-none rounded-2xl border border-coral px-4 py-3 text-center text-[15px] font-bold text-coral ${isSubmitting ? 'pointer-events-none opacity-50' : ''}`}>
              못 갈 것 같아요
            </summary>
            <div role="dialog" aria-modal="true" aria-labelledby="cancellation-title" className="mt-3 rounded-2xl border border-line bg-sand/40 p-4">
              <h2 id="cancellation-title" className="text-[16px] font-bold text-ink">
                정말 참여를 취소할까요?
              </h2>
              <p className="mt-1 text-[13px] text-ink/55">취소 사유는 다른 멤버에게 공개되지 않아요.</p>
              <div className="mt-4 grid gap-2">
                {CANCELLATION_OPTIONS.map((option) => (
                  <button
                    key={option.reason}
                    type="button"
                    disabled={isSubmitting}
                    onClick={() => void onCancel(option.reason)}
                    className="rounded-2xl border border-line bg-white px-3 py-3 text-left text-[14px] font-semibold disabled:opacity-50"
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </div>
          </details>
        </section>
      )}

      {canArrive && arrivalDeadlineReached && (
        <section
          role="status"
          aria-live="polite"
          className="rounded-3xl bg-white p-5 shadow-[0_1px_8px_rgba(34,48,62,0.05)]"
        >
          <p className="text-[15px] font-bold text-ink">최종 도착 마감이 지났어요</p>
          <p className="mt-1 text-[13px] text-ink/60">
            노쇼 처리 결과를 확인하고 있어요. 잠시 후 화면이 자동으로 변경됩니다.
          </p>
        </section>
      )}

      {canArrive && (
        <section className="flex flex-col gap-3 rounded-3xl bg-white p-5 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
          <h2 className="text-[16px] font-bold text-ink">내 도착 예정 시간</h2>
          <p className="text-[13px] text-ink/55">내 예상 도착 시간만 선택하거나 변경할 수 있어요.</p>
          {estimatedArrivalAt && (
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 rounded-2xl bg-sand/50 p-3 text-[13px]">
              <dt className="text-ink/50">선택한 도착 시간</dt>
              <dd className="text-right font-semibold text-ink">{currentMember?.arrivalMinutes}분</dd>
              <dt className="text-ink/50">예상 도착 시각</dt>
              <dd className="text-right font-semibold text-ink">{formatSeoulDateTime(estimatedArrivalAt)}</dd>
              <dt className="text-ink/50">예상 도착까지</dt>
              <dd className="text-right font-semibold text-teal">
                {estimatedArrivalPassed
                  ? '예정 시간이 지났어요'
                  : formatRemainingTime(estimatedArrivalAt, effectiveNowEpochMs)}
              </dd>
            </dl>
          )}
          {estimatedArrivalPassed && canSelectArrivalTime && (
            <p role="status" className="rounded-xl bg-coral/10 px-3 py-2 text-[13px] text-coral">
              다른 시간을 선택하거나 도착 완료를 눌러주세요. 같은 시간을 다시 선택해도 예정 시각은 연장되지 않아요.
            </p>
          )}
          {actionError && (
            <p role="alert" className="rounded-xl bg-coral/10 px-3 py-2 text-[13px] text-coral">
              도착 예정 시간을 저장하지 못했어요. 다시 선택해주세요.
            </p>
          )}
          {canSelectArrivalTime ? (
            <details>
              <summary className={`cursor-pointer list-none rounded-2xl bg-coral px-4 py-3 text-center text-[15px] font-bold text-white ${isSubmitting ? 'pointer-events-none opacity-50' : ''}`}>
                {isSubmitting ? '저장 중...' : '몇 분 후 도착하나요?'}
              </summary>
              <ArrivalTimePanel
                deadlineAt={group.arrivalDeadlineAt}
                nowEpochMs={effectiveNowEpochMs}
                isSubmitting={isSubmitting}
                selectedMinutes={currentMember?.arrivalMinutes ?? null}
                onSelect={(minutes) => void onSelectArrivalTime(minutes)}
              />
            </details>
          ) : (
            <p role="status" className="rounded-2xl bg-ink/5 px-4 py-3 text-[13px] text-ink/60">
              최종 도착 마감이 지나 예정 시간을 변경할 수 없어요.
            </p>
          )}
        </section>
      )}

      <section className="flex flex-col gap-2 rounded-3xl bg-white p-5 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
        <h2 className="text-[16px] font-bold text-ink">축제 정보</h2>
        <p className="text-[15px] font-semibold text-ink">{group.festival.title}</p>
        <p className="text-[13px] text-ink/60">{group.festival.address ?? '주소 정보 없음'}</p>
        <p className="text-[13px] text-ink/60">{period}</p>
      </section>

      <section className="flex flex-col gap-3">
        <div className="flex items-center gap-2">
          <Users size={18} className="text-coral" />
          <h2 className="text-[16px] font-bold text-ink">확정 멤버</h2>
        </div>
        {group.members.map((member) => (
          <article key={member.memberId} className="flex items-center gap-3 rounded-2xl bg-white p-4 shadow-[0_1px_8px_rgba(34,48,62,0.05)]">
            {member.profileImageUrl ? (
              <img src={member.profileImageUrl} alt={`${member.nickname} 프로필`} className="h-12 w-12 rounded-full object-cover" referrerPolicy="no-referrer" />
            ) : (
              <div aria-hidden="true" className="flex h-12 w-12 items-center justify-center rounded-full bg-coral/15 font-bold text-coral">
                {member.nickname.slice(0, 1)}
              </div>
            )}
            <div className="flex flex-col gap-0.5">
              <span className="text-[14px] font-semibold text-ink">{member.nickname}</span>
              <span className="text-[12px] text-teal">{memberArrivalText(member)}</span>
              {member.arrivedAt && (
                <span className="text-[11px] text-ink/50">{formatSeoulDateTime(member.arrivedAt)}</span>
              )}
            </div>
          </article>
        ))}
      </section>

      <section
        aria-labelledby="match-room-timeline-title"
        className="flex flex-col gap-3 rounded-3xl bg-white p-5 shadow-[0_1px_8px_rgba(34,48,62,0.05)]"
      >
        <div className="flex items-center gap-2">
          <Clock3 size={18} className="text-coral" />
          <h2 id="match-room-timeline-title" className="text-[16px] font-bold text-ink">
            함께 만나는 과정
          </h2>
        </div>
        {eventsError && (
          <div role="alert" className="rounded-2xl bg-coral/10 px-4 py-3 text-[13px] text-coral">
            <p>상태 기록을 불러오지 못했어요.</p>
            <button type="button" onClick={onRetry} className="mt-2 font-bold underline">
              다시 시도
            </button>
          </div>
        )}
        {!eventsError && events.length === 0 && (
          <p className="rounded-2xl bg-sand/50 px-4 py-4 text-[13px] text-ink/55">
            아직 표시할 상태 기록이 없어요.
          </p>
        )}
        <ol className="flex flex-col gap-3">
          {events.map((event) => (
            <li
              key={event.eventId}
              className="relative rounded-2xl border border-line bg-sand/40 px-4 py-3"
            >
              <p className="text-[14px] font-semibold text-ink">
                {matchEventText(event, group.currentMemberId)}
              </p>
              <time dateTime={event.occurredAt} className="mt-1 block text-[11px] text-ink/45">
                {formatSeoulDateTime(event.occurredAt)}
              </time>
            </li>
          ))}
        </ol>
      </section>

    </>
  );
}

export function matchEventText(
  event: MatchRoomState['events'][number],
  currentMemberId?: number,
): string {
  if (event.type === 'MATCH_CONFIRMED') return '매칭이 확정됐어요.';
  const actor = event.actor
    ? (event.actor.memberId === currentMemberId ? '내가' : `${event.actor.nickname}님이`)
    : '참여자가';
  if (event.type === 'MEMBER_ARRIVED') return `${actor} 도착했어요.`;
  if (event.type === 'MEMBER_CANCELLED') return `${actor} 참여를 취소했어요.`;
  if (event.type === 'MEMBER_NO_SHOW') {
    return `${actor} 도착 마감까지 도착하지 않았어요.`;
  }
  if (event.type === 'MATCH_CANCELLED') {
    return '남은 인원으로 만남을 계속할 수 없어 그룹이 종료됐어요.';
  }
  if (event.arrivalMinutes === 0) return `${actor} 곧 도착할 예정이에요.`;
  return `${actor} ${event.arrivalMinutes}분 후 도착할 예정이에요.`;
}

export const CANCELLATION_OPTIONS: Array<{
  reason: MatchCancellationReason;
  label: string;
}> = [
  { reason: 'SCHEDULE_CHANGED', label: '갑자기 일정이 생겼어요' },
  { reason: 'TRANSPORTATION_ISSUE', label: '이동이 어려워졌어요' },
  { reason: 'OTHER', label: '다른 이유가 있어요' },
];

export function ArrivalTimePanel({
  deadlineAt,
  nowEpochMs,
  isSubmitting,
  selectedMinutes = null,
  onSelect,
}: {
  deadlineAt: string;
  nowEpochMs: number;
  isSubmitting: boolean;
  selectedMinutes?: CurrentMatchGroup['members'][number]['arrivalMinutes'];
  onSelect: (minutes: ArrivalMinutes) => void;
}) {
  const options: Array<{ minutes: ArrivalMinutes; label: string }> = [
    { minutes: 5, label: '5분' },
    { minutes: 10, label: '10분' },
    { minutes: 20, label: '20분' },
    { minutes: 25, label: '25분' },
  ];
  return (
    <div role="region" aria-labelledby="arrival-time-title" className="mt-3 rounded-2xl border border-line bg-sand/40 p-3">
      <section>
        <div className="mb-3">
          <h2 id="arrival-time-title" className="text-[17px] font-bold text-ink">몇 분 후 도착하나요?</h2>
        </div>
        <div className="grid grid-cols-1 gap-2">
          {options.map((option) => {
            const exceedsDeadline = nowEpochMs >= Date.parse(deadlineAt)
              || nowEpochMs + option.minutes * 60_000 > Date.parse(deadlineAt);
            const selected = selectedMinutes === option.minutes;
            return (
            <button
              key={option.minutes}
              type="button"
              disabled={isSubmitting || exceedsDeadline || selected}
              aria-pressed={selected}
              onClick={() => onSelect(option.minutes)}
              className={`rounded-2xl border px-4 py-3 text-left text-[14px] font-semibold disabled:opacity-50 ${
                selected ? 'border-coral bg-coral/10 text-coral' : 'border-line text-ink'
              }`}
            >
              {option.label}{selected ? ' · 현재 선택' : ''}
            </button>
            );
          })}
        </div>
      </section>
    </div>
  );
}

export function getEstimatedArrivalAt(
  member: CurrentMatchGroup['members'][number] | undefined,
): string | null {
  if (!member?.arrivalTimeSelectedAt || member.arrivalMinutes === null) return null;
  const selectedAt = Date.parse(member.arrivalTimeSelectedAt);
  if (Number.isNaN(selectedAt)) return null;
  return new Date(selectedAt + member.arrivalMinutes * 60_000).toISOString();
}

export function formatRemainingTime(targetAt: string, nowEpochMs: number): string {
  const targetEpochMs = Date.parse(targetAt);
  if (Number.isNaN(targetEpochMs) || targetEpochMs <= nowEpochMs) return '00:00';
  const totalSeconds = Math.ceil((targetEpochMs - nowEpochMs) / 1_000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

export function memberArrivalText(member: CurrentMatchGroup['members'][number]): string {
  if (member.status === 'ARRIVED') return '도착 완료';
  if (member.status === 'ARRIVAL_TIME_SELECTED') {
    return member.arrivalMinutes === 0
      ? '선택한 도착 시간: 곧 도착'
      : `선택한 도착 시간: ${member.arrivalMinutes ?? '-'}분`;
  }
  return '도착 시간 미정';
}

export function formatFestivalPeriod(start: string | null, end: string | null): string {
  if (!start && !end) return '행사 기간 정보 없음';
  if (start && end) return `${start} ~ ${end}`;
  return start ?? end ?? '행사 기간 정보 없음';
}
