import { describe, expect, it } from 'vitest';
import type {
  ActiveMatchProposal,
  CurrentMatchGroup,
  MatchPool,
  MatchingRestriction,
} from '../api/matching';
import {
  canBeginRetry,
  deriveMatchingState,
  isAbortError,
  pollingDelay,
  retrySourceAfterRefresh,
  stateAfterPoolEntry,
  stateAfterPoolEntryFailure,
} from './useMatchingSession';

const restriction = (active = false): MatchingRestriction => ({
  penaltyScore: 0,
  cooldown: {
    active,
    reason: active ? 'REJECTED_PROPOSAL' : null,
    startsAt: active ? '2026-07-27T12:00:00' : null,
    expiresAt: active ? '2026-07-27T12:05:00' : null,
    remainingSeconds: active ? 300 : 0,
  },
  completionLock: {
    active: false,
    reason: null,
    groupId: null,
    startsAt: null,
    expiresAt: null,
    remainingSeconds: 0,
  },
});

const completionRestriction = (active = true): MatchingRestriction => ({
  ...restriction(),
  completionLock: {
    active,
    reason: 'MATCH_VALIDITY',
    groupId: 24,
    startsAt: '2026-08-10T12:00:00+09:00',
    expiresAt: '2026-08-10T13:00:00+09:00',
    remainingSeconds: active ? 1_200 : 0,
  },
});

const pool = (status: MatchPool['status']): MatchPool => ({
  poolId: 1,
  festivalId: 2,
  preferredGroupSize: 3,
  allowMinimumTwo: true,
  tags: [],
  status,
  enteredAt: '2026-07-27T12:00:00',
  searchExpiresAt: '2026-07-27T12:01:00',
});

const proposal = (proposalType: ActiveMatchProposal['proposalType']): ActiveMatchProposal => ({
  proposalId: 10,
  attemptId: 20,
  proposalType,
  proposalRound: proposalType === 'INITIAL_MATCH' ? 1 : 2,
  status: 'SENT',
  targetGroupSize: 3,
  attemptStatus: 'PROPOSED',
  expiresAt: '2026-07-27T12:00:30',
});

const group: CurrentMatchGroup = {
  groupId: 30,
  festivalId: 2,
  status: 'CONFIRMED',
  confirmedMemberCount: 2,
  currentMemberCount: 2,
  confirmedAt: '2026-07-27T12:00:20',
  arrivalDeadlineAt: '2026-07-27T12:30:20',
  festival: {
    festivalId: 2,
    title: '테스트 축제',
    address: '강원특별자치도 춘천시',
    eventStartDate: '2026-07-27',
    eventEndDate: '2026-07-29',
  },
  members: [
    { memberId: 1, nickname: 'member-a', profileImageUrl: null, status: 'JOINED', arrivalMinutes: null, arrivalTimeSelectedAt: null },
    { memberId: 2, nickname: 'member-b', profileImageUrl: null, status: 'JOINED', arrivalMinutes: null, arrivalTimeSelectedAt: null },
  ],
};

const state = (
  overrides: Partial<{
    pool: MatchPool | null;
    proposal: ActiveMatchProposal | null;
    group: CurrentMatchGroup | null;
    restriction: MatchingRestriction;
  }> = {},
) =>
  deriveMatchingState({
    pool: null,
    proposal: null,
    group: null,
    restriction: restriction(),
    ...overrides,
  });

describe('deriveMatchingState', () => {
  it('group을 다른 서버 상태보다 우선하여 MATCHED로 복원한다', () => {
    expect(
      state({ group, proposal: proposal('INITIAL_MATCH'), pool: pool('WAITING'), restriction: restriction(true) }).status,
    ).toBe('MATCHED');
  });

  it('proposal round 1과 round 2를 구분한다', () => {
    expect(state({ proposal: proposal('INITIAL_MATCH') }).status).toBe('INITIAL_PROPOSAL');
    expect(state({ proposal: proposal('INSUFFICIENT_MEMBERS_CONFIRMATION') }).status).toBe(
      'INSUFFICIENT_MEMBERS_PROPOSAL',
    );
  });

  it.each(['WAITING', 'LOCKED', 'CANCELLED', 'EXPIRED'] as const)('pool %s를 복원한다', (status) => {
    expect(state({ pool: pool(status) }).status).toBe(status);
  });

  it('PROPOSED pool에서 active proposal이 없으면 응답 대기로 본다', () => {
    expect(state({ pool: pool('PROPOSED') }).status).toBe('RESPONSE_PENDING');
  });

  it('MATCHED pool만 남고 active group이 없으면 종료 상태로 복원한다', () => {
    expect(state({ pool: pool('MATCHED') }).status).toBe('CANCELLED');
    expect(state({ pool: pool('MATCHED'), restriction: restriction(true) }).status).toBe('CANCELLED');
  });

  it('current group이 null이고 최신 pool이 MATCHED여도 완료 이력을 우선한다', () => {
    expect(state({ pool: pool('MATCHED'), restriction: completionRestriction() }).status)
      .toBe('COMPLETED');
  });

  it('완료 제한이 만료돼도 완료 이력은 CANCELLED로 오인하지 않는다', () => {
    expect(state({ pool: pool('MATCHED'), restriction: completionRestriction(false) }).status)
      .toBe('COMPLETED');
  });

  it('active 서버 상태가 없으면 cooldown, 그마저 없으면 IDLE을 사용한다', () => {
    expect(state({ restriction: restriction(true) }).status).toBe('COOLDOWN');
    expect(state().status).toBe('IDLE');
  });
});

describe('polling policy', () => {
  it('active 상태는 2초, cooldown은 5초로 polling한다', () => {
    expect(pollingDelay('WAITING', 0)).toBe(2_000);
    expect(pollingDelay('LOCKED', 0)).toBe(2_000);
    expect(pollingDelay('RESPONSE_PENDING', 0)).toBe(2_000);
    expect(pollingDelay('COOLDOWN', 0)).toBe(5_000);
  });

  it('terminal 상태는 polling하지 않고 오류는 최대 30초까지 backoff한다', () => {
    expect(pollingDelay('MATCHED', 0)).toBeNull();
    expect(pollingDelay('CANCELLED', 0)).toBeNull();
    expect(pollingDelay('EXPIRED', 0)).toBeNull();
    expect(pollingDelay('COMPLETED', 0)).toBeNull();
    expect(pollingDelay('ERROR', 1)).toBe(2_000);
    expect(pollingDelay('ERROR', 10)).toBe(30_000);
  });

  it('AbortError를 구분한다', () => {
    expect(isAbortError(new DOMException('aborted', 'AbortError'))).toBe(true);
    expect(isAbortError(new Error('network'))).toBe(false);
  });
});

describe('retry form reconciliation', () => {
  it.each(['CANCELLED', 'EXPIRED'] as const)('같은 terminal pool %s에서는 retry form을 유지한다', (status) => {
    expect(retrySourceAfterRefresh(1, state({ pool: pool(status) }))).toBe(1);
  });

  it('다른 최신 pool이나 활성 서버 상태가 확인되면 retry form을 해제한다', () => {
    expect(retrySourceAfterRefresh(99, state({ pool: pool('CANCELLED') }))).toBeNull();
    expect(retrySourceAfterRefresh(1, state({ pool: pool('WAITING') }))).toBeNull();
    expect(retrySourceAfterRefresh(1, state({ pool: pool('LOCKED') }))).toBeNull();
    expect(retrySourceAfterRefresh(1, state({ pool: pool('PROPOSED') }))).toBeNull();
    expect(retrySourceAfterRefresh(1, state({ proposal: proposal('INITIAL_MATCH'), pool: pool('CANCELLED') }))).toBeNull();
    expect(retrySourceAfterRefresh(1, state({ group, pool: pool('CANCELLED') }))).toBeNull();
  });

  it('cooldown이 활성화되면 같은 terminal pool의 retry form도 해제한다', () => {
    expect(
      retrySourceAfterRefresh(1, state({ pool: pool('CANCELLED'), restriction: restriction(true) })),
    ).toBeNull();
  });

  it('cooldown과 제출 중에는 retry form 진입을 차단한다', () => {
    expect(canBeginRetry(state({ pool: pool('CANCELLED') }), false)).toBe(true);
    expect(canBeginRetry(state({ pool: pool('EXPIRED') }), false)).toBe(true);
    expect(canBeginRetry(state({ pool: pool('CANCELLED'), restriction: restriction(true) }), false)).toBe(false);
    expect(canBeginRetry(state({ pool: pool('CANCELLED') }), true)).toBe(false);
    expect(canBeginRetry(state({ pool: pool('WAITING') }), false)).toBe(false);
    expect(canBeginRetry(state({ pool: pool('MATCHED'), restriction: completionRestriction() }), false)).toBe(false);
    expect(canBeginRetry(state({ pool: pool('MATCHED'), restriction: completionRestriction(false) }), false)).toBe(true);
  });

  it.each(['WAITING', 'LOCKED'] as const)('POST 성공 pool %s를 즉시 화면 상태에 반영한다', (status) => {
    const enteredPool = pool(status);
    enteredPool.poolId = 2;
    const nextState = stateAfterPoolEntry(state({ pool: pool('CANCELLED') }), enteredPool);
    expect(nextState.status).toBe(status);
    expect(nextState.pool?.poolId).toBe(2);
    expect(nextState.error).toBeNull();
  });

  it('retry POST 실패 시 terminal 상태와 pool을 보존한다', () => {
    const previous = state({ pool: pool('CANCELLED') });
    const error = new Error('network');
    const nextState = stateAfterPoolEntryFailure(previous, error, 1);
    expect(nextState.status).toBe('CANCELLED');
    expect(nextState.pool?.poolId).toBe(1);
    expect(nextState.error).toBe(error);
    expect(retrySourceAfterRefresh(1, nextState)).toBe(1);
  });

  it('일반 POST 실패는 기존 ERROR 처리로 전환한다', () => {
    expect(stateAfterPoolEntryFailure(state(), new Error('network'), null).status).toBe('ERROR');
  });

  it('새 mount의 초기 retry source는 없으므로 terminal 서버 상태만 복원한다', () => {
    const terminalState = state({ pool: pool('EXPIRED') });
    expect(terminalState.status).toBe('EXPIRED');
    expect(retrySourceAfterRefresh(null, terminalState)).toBeNull();
  });
});
