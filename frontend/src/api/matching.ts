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
  terminationReason: MatchTerminationReason | null;
};

export type MatchTerminationReason =
  | 'SELF_REJECTED'
  | 'NON_FAULT_TERMINATED'
  | 'SELF_TIMEOUT'
  | 'SYSTEM_TERMINATED';

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
  serverNow: string;
  cooldown: {
    active: boolean;
    reason: string | null;
    startsAt: string | null;
    expiresAt: string | null;
    remainingSeconds: number;
  };
  completionLock: {
    active: boolean;
    reason: 'MATCH_VALIDITY' | null;
    groupId: number | null;
    startsAt: string | null;
    expiresAt: string | null;
    remainingSeconds: number;
  };
};

export type ArrivalMinutesSnapshot = 0 | 5 | 10 | 20 | 25 | 30;
export type ArrivalMinutesSelection = 5 | 10 | 20 | 25;

export type MatchGroupMember = {
  memberId: number;
  nickname: string;
  profileImageUrl: string | null;
  status: 'JOINED' | 'ARRIVAL_TIME_SELECTED' | 'ARRIVED' | 'COMPLETED';
  arrivalMinutes: ArrivalMinutesSnapshot | null;
  arrivalTimeSelectedAt: string | null;
  arrivedAt?: string | null;
};

export type MatchCancellationReason =
  | 'SCHEDULE_CHANGED'
  | 'TRANSPORTATION_ISSUE'
  | 'OTHER';

export type MatchCancellationResult = {
  groupId: number;
  memberStatus: 'CANCELLED';
  groupStatus: 'CONFIRMED' | 'IN_PROGRESS' | 'CANCELLED';
  groupContinues: boolean;
  currentMemberCount: number;
};

export type MatchReportReasonCode =
  | 'RUDE'
  | 'SEXUAL_HARASSMENT'
  | 'NO_SHOW'
  | 'SCAM'
  | 'SAFETY'
  | 'OTHER';

export type MatchReportRequest = {
  reportedMemberId: number;
  reasonCode: MatchReportReasonCode;
};

export type MatchReportResponse = {
  reportId: number;
  groupId: number;
  reportedMemberId: number;
  reasonCode: MatchReportReasonCode;
  status: 'SUBMITTED' | 'REVIEWING' | 'RESOLVED' | 'REJECTED' | 'ACTION_TAKEN';
  createdAt: string;
};

export type MatchBlockRequest = {
  blockedMemberId: number;
};

export type MatchBlockResponse = {
  blockId: number;
  blockedMemberId: number;
  createdAt: string;
};

export type MatchGroupFestival = {
  festivalId: number;
  title: string;
  address: string | null;
  eventStartDate: string | null;
  eventEndDate: string | null;
};

export type MatchGroupMeetingPoint = {
  name: string;
  address: string;
  contentId: string;
  longitude: number;
  latitude: number;
  candidateSearchRadiusMeters: number;
  arrivalRadiusMeters: number;
};

export type CurrentMatchGroup = {
  groupId: number;
  festivalId: number;
  status: 'CONFIRMED' | 'IN_PROGRESS' | 'COMPLETED';
  confirmedMemberCount: number;
  currentMemberCount: number;
  confirmedAt: string;
  arrivalDeadlineAt: string;
  startedAt?: string | null;
  completedAt?: string | null;
  currentMemberId?: number;
  festival: MatchGroupFestival;
  meetingPoint?: MatchGroupMeetingPoint | null;
  members: MatchGroupMember[];
};

export type MatchGroupEventType =
  | 'MATCH_CONFIRMED'
  | 'ARRIVAL_TIME_SELECTED'
  | 'MEMBER_ARRIVED'
  | 'MEMBER_CANCELLED'
  | 'MEMBER_NO_SHOW'
  | 'MATCH_CANCELLED';

export type MatchGroupEvent = {
  eventId: number;
  type: MatchGroupEventType;
  occurredAt: string;
  actor: {
    memberId: number;
    nickname: string;
  } | null;
  arrivalMinutes: ArrivalMinutesSnapshot | null;
};

export type CurrentMatchGroupEvents = {
  events: MatchGroupEvent[];
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
  getCurrentGroupEvents: (signal?: AbortSignal) =>
    apiClientNullable<CurrentMatchGroupEvents>(
      '/api/matching/groups/me/current/events',
      { signal },
    ),
  selectArrivalTime: (arrivalMinutes: ArrivalMinutesSelection, signal?: AbortSignal) =>
    apiClient<CurrentMatchGroup>('/api/matching/groups/me/current/arrival-time', {
      method: 'PUT',
      signal,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ arrivalMinutes }),
    }),
  arrive: (signal?: AbortSignal) =>
    apiClient<CurrentMatchGroup>('/api/matching/groups/me/current/arrival', {
      method: 'PUT',
      signal,
    }),
  cancelParticipation: (reason: MatchCancellationReason, signal?: AbortSignal) =>
    apiClient<MatchCancellationResult>(
      '/api/matching/groups/me/current/cancellation',
      {
        method: 'PUT',
        signal,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason }),
      },
    ),
  submitReport: (groupId: number, request: MatchReportRequest, signal?: AbortSignal) =>
    apiClient<MatchReportResponse>(`/api/match-groups/${groupId}/reports`, {
      method: 'POST',
      signal,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    }),
  submitBlock: (groupId: number, request: MatchBlockRequest, signal?: AbortSignal) =>
    apiClient<MatchBlockResponse>(`/api/match-groups/${groupId}/blocks`, {
      method: 'POST',
      signal,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    }),
};
