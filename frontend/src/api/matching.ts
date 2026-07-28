import { apiClient, apiClientNullable } from './apiClient';

export type MatchPoolStatus =
  | 'WAITING'
  | 'LOCKED'
  | 'PROPOSED'
  | 'MATCHED'
  | 'EXPIRED'
  | 'CANCELLED'
  | 'COOLDOWN';

export type MatchProposalType = 'INITIAL_MATCH' | 'INSUFFICIENT_MEMBERS_CONFIRMATION';
export type MatchProposalAction = 'ACCEPT' | 'REJECT' | 'CANCEL_CURRENT_MEMBERS';

export type MatchPoolEntryRequest = {
  festivalId: number;
  preferredGroupSize: 2 | 3 | 4;
  allowMinimumTwo: boolean;
  tags: [];
};

export type MatchPool = {
  poolId: number;
  festivalId: number;
  preferredGroupSize: 2 | 3 | 4;
  allowMinimumTwo: boolean;
  tags: string[];
  status: MatchPoolStatus;
  enteredAt: string;
  searchExpiresAt: string;
};

export type ActiveMatchProposal = {
  proposalId: number;
  attemptId: number;
  proposalType: MatchProposalType;
  proposalRound: number;
  status: 'SENT';
  targetGroupSize: 2 | 3 | 4;
  attemptStatus: string;
  expiresAt: string;
};

export type MatchProposalActionResponse = {
  attemptId: number;
  proposalId: number;
  action: MatchProposalAction;
  recordedResponse: string;
  attemptStatus: string;
};

export type MatchingRestriction = {
  penaltyScore: number;
  cooldown: {
    active: boolean;
    reason: string | null;
    startsAt: string | null;
    expiresAt: string | null;
    remainingSeconds: number;
  };
};

export type MatchGroupMember = {
  memberId: number;
  nickname: string;
  profileImageUrl: string | null;
};

export type CurrentMatchGroup = {
  groupId: number;
  festivalId: number;
  status: 'CONFIRMED' | 'IN_PROGRESS';
  confirmedMemberCount: number;
  confirmedAt: string;
  members: MatchGroupMember[];
};

export const matchingApi = {
  enterPool: (request: MatchPoolEntryRequest, signal?: AbortSignal) =>
    apiClient<MatchPool>('/api/matching/pools', {
      method: 'POST',
      signal,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    }),
  getCurrentPool: (signal?: AbortSignal) =>
    apiClientNullable<MatchPool>('/api/matching/pools/me/current', { signal }),
  getActiveProposal: (signal?: AbortSignal) =>
    apiClientNullable<ActiveMatchProposal>('/api/matching/proposals/me/active', { signal }),
  respond: (proposalId: number, action: MatchProposalAction, signal?: AbortSignal) =>
    apiClient<MatchProposalActionResponse>(
      `/api/matching/proposals/${proposalId}/responses`,
      {
        method: 'POST',
        signal,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action }),
      },
    ),
  getRestrictions: (signal?: AbortSignal) =>
    apiClient<MatchingRestriction>('/api/matching/me/restrictions', { signal }),
  getCurrentGroup: (signal?: AbortSignal) =>
    apiClientNullable<CurrentMatchGroup>('/api/matching/groups/me/current', { signal }),
};
