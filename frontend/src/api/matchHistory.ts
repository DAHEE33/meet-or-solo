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
  /**
   * 만남 장소에 실제로 도착한 사람이 있었는지. 확정 직후 깨져 아무도 만나지 못한 매칭은
   * 신고 대상이 아니다(docs/19 4.11.1). `reportable`이 false일 때 기간 만료와 구분해
   * 안내하려고 따로 받는다.
   */
  meetingHeld: boolean;
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
