import { describe, expect, it } from 'vitest';
import type {
  ActiveMatchProposal,
  CurrentMatchGroup,
  MatchPool,
  MatchingRestriction,
} from '../api/matching';
import { deriveMatchingState, isAbortError, pollingDelay } from './useMatchingSession';

const restriction = (active = false): MatchingRestriction => ({
  penaltyScore: 0,
  cooldown: {
    active,
    reason: active ? 'REJECTED_PROPOSAL' : null,
    startsAt: active ? '2026-07-27T12:00:00' : null,
    expiresAt: active ? '2026-07-27T12:05:00' : null,
    remainingSeconds: active ? 300 : 0,
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
  confirmedAt: '2026-07-27T12:00:20',
  members: [
    { memberId: 1, nickname: 'member-a', profileImageUrl: null },
    { memberId: 2, nickname: 'member-b', profileImageUrl: null },
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

  it('PROPOSED 또는 MATCHED pool에서 active proposal/group이 없으면 응답 대기로 본다', () => {
    expect(state({ pool: pool('PROPOSED') }).status).toBe('RESPONSE_PENDING');
    expect(state({ pool: pool('MATCHED') }).status).toBe('RESPONSE_PENDING');
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
    expect(pollingDelay('ERROR', 1)).toBe(2_000);
    expect(pollingDelay('ERROR', 10)).toBe(30_000);
  });

  it('AbortError를 구분한다', () => {
    expect(isAbortError(new DOMException('aborted', 'AbortError'))).toBe(true);
    expect(isAbortError(new Error('network'))).toBe(false);
  });
});
