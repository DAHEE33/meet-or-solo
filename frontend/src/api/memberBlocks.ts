import { apiClient, apiClientVoid } from './apiClient';

export type MemberBlock = {
  blockedMemberId: number;
  nickname: string;
  profileImageUrl: string | null;
  blockedAt: string;
};

export const memberBlocksApi = {
  getMine: (signal?: AbortSignal) =>
    apiClient<MemberBlock[]>('/api/members/me/blocks', { signal }),
  unblock: (blockedMemberId: number, signal?: AbortSignal) =>
    apiClientVoid(`/api/members/me/blocks/${blockedMemberId}`, {
      method: 'DELETE',
      signal,
    }),
};
