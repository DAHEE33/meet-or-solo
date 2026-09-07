import { apiClient } from './apiClient';

export type MatchHistoryMember = {
  memberId: number;
  nickname: string;
  profileImageUrl: string | null;
  /** 내가 이 만남에서 이 상대를 신고한 적이 있는지. 신고자 본인에게만 보인다. */
  reported: boolean;
};

export type MatchHistoryItem = {
  groupId: number;
  status: 'COMPLETED' | 'CANCELLED';
  festivalTitle: string;
  festivalAddress: string | null;
  meetingPlaceName: string | null;
  confirmedMemberCount: number;
  endedAt: string;
  /** 신고 가능 기간과 만료 시각은 서버가 판정한다. 화면에서 날짜를 다시 계산하지 않는다. */
  reportableUntil: string;
  reportable: boolean;
  members: MatchHistoryMember[];
};

export type MatchHistory = {
  items: MatchHistoryItem[];
  pagination: { size: number; hasNext: boolean; nextCursor: string | null };
};

export const matchHistoryApi = {
  getMine: (cursor?: string | null, signal?: AbortSignal) =>
    apiClient<MatchHistory>(
      cursor ? `/api/members/me/match-history?cursor=${encodeURIComponent(cursor)}`
        : '/api/members/me/match-history',
      { signal },
    ),
};
