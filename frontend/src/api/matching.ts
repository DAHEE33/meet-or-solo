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
  /** 내부 운영 값이라 화면에 표시하지 않는다. */
  penaltyScore: number;
  /** 본인의 매너온도(docs/19 4.9). 매칭 화면에 표시한다. */
  mannerTemperature: number;
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
  /**
   * 매너온도 매칭 제한(docs/19 4.9 PR C).
   *
   * 사유(신고)도 해제 예정 시각도 담기지 않는다. 낮은 온도는 곧 "신고를 받았다"이고,
   * 회복은 만남 완료와 시간 경과 두 경로에 달려 있어 확정된 해제 시각이 없다.
   */
  temperatureLimit: {
    active: boolean;
    minimumTemperature: number;
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

export type MatchPoolCancellationResult = {
  poolId: number;
  status: 'CANCELLED';
};

export type MatchCancellationReason =
  | 'SCHEDULE_CHANGED'
  | 'TRANSPORTATION_ISSUE'
  | 'OTHER';

export type MatchCancellationResult = {
  groupId: number;
  /** 참여 취소는 'CANCELLED', 먼저 나가기(도착 뒤 이탈)는 'LEFT'를 보낸다. */
  memberStatus: 'CANCELLED' | 'LEFT';
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
  /** 만남이 끝났다고 보는 시각. 이 시각에 상태방이 닫히고 매너온도 보상이 지급된다. */
  meetingEndsAt: string;
  startedAt?: string | null;
  completedAt?: string | null;
  currentMemberId?: number;
  festival: MatchGroupFestival;
  meetingPoint?: MatchGroupMeetingPoint | null;
  members: MatchGroupMember[];
  /**
   * 도착자 2명 이상으로 만남이 성립했었는지. 이탈해 `members`(활성 참여자)에서 빠진 사람도
   * 포함해서 서버가 판정한 값이다. 화면에서 `members`만으로 다시 세면, 혼자 남은 사람은
   * 상대가 나가자마자 "만남 성립 전"으로 잘못 보인다.
   */
  meetingHeld: boolean;
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
  /**
   * 도착 인증. 서버가 만남 장소와의 거리를 재서 반경 안인지 확인한다(docs/19 4.11.3).
   * 좌표는 거리 계산에만 쓰이고 저장되지 않는다.
   */
  arrive: (position: { latitude: number; longitude: number }, signal?: AbortSignal) =>
    apiClient<CurrentMatchGroup>('/api/matching/groups/me/current/arrival', {
      method: 'PUT',
      signal,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(position),
    }),
  /** 도착한 사람이 만남에서 먼저 나간다. 페널티는 없다(docs/19 4.11.3). */
  leave: (signal?: AbortSignal) =>
    apiClient<MatchCancellationResult>('/api/matching/groups/me/current/leave', {
      method: 'PUT',
      signal,
    }),
  cancelPool: (signal?: AbortSignal) =>
    apiClient<MatchPoolCancellationResult>(
      '/api/matching/pools/me/current/cancellation',
      { method: 'PUT', signal },
    ),
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
