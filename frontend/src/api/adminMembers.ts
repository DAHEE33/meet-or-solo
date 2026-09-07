import { apiClient } from './apiClient';

export type AdminMemberStatus = 'ACTIVE' | 'PROFILE_REQUIRED' | 'SUSPENDED' | 'BANNED' | 'WITHDRAWN' | 'DELETED';
export type AdminMemberActionType = 'WARNING' | 'SUSPEND' | 'BAN' | 'UNBAN' | 'UNSUSPEND';
export type AdminMemberActionReasonCode = 'COMMUNITY_GUIDELINE' | 'HARASSMENT' | 'NO_SHOW_ABUSE' | 'FRAUD_OR_SCAM' | 'SAFETY_RISK' | 'ADMIN_CORRECTION' | 'OTHER';
export type AdminSuspensionDuration = 'ONE_DAY' | 'THREE_DAYS' | 'SEVEN_DAYS' | 'THIRTY_DAYS';

export type AdminMemberListItem = {
  memberId: number; nickname: string | null; profileImageUrl: string | null; role: 'USER' | 'ADMIN';
  status: AdminMemberStatus; penaltyScore: number; mannerTemperature: number;
  suspendedUntil: string | null; createdAt: string;
};
export type AdminMemberDetail = AdminMemberListItem & {
  suspendedAt: string | null; lastLoginAt: string | null;
  /** 최근 30일 누적 유효 신고 건수. 같은 만남의 사유별 중복은 1건으로 압축한다. */
  recentValidReportCount: number;
  /** 누적 유효 신고가 임계에 도달해 이용 제한을 검토해야 하는 회원인지. */
  safetyReviewRequired: boolean;
  reports: Array<{ reportId: number; reasonCode: string; status: string; createdAt: string; resolvedAt: string | null }>;
  actions: Array<{ actionId: number; actionType: AdminMemberActionType; reasonCode: AdminMemberActionReasonCode; reasonNote: string | null; reportId: number | null; createdAt: string }>;
};
export type AdminMemberFilters = { query: string; status: AdminMemberStatus | ''; role: 'USER' | 'ADMIN' | '' };
export type AdminMemberPage = { items: AdminMemberListItem[]; pagination: { size: number; hasNext: boolean; nextCursor: string | null } };
export type AdminMemberActionRequest = {
  action: AdminMemberActionType; reasonCode: AdminMemberActionReasonCode; reasonNote: string | null;
  suspensionDuration: AdminSuspensionDuration | null; reportId: number | null; expectedStatus: AdminMemberStatus;
};

function query(filters: AdminMemberFilters, cursor: string | null, size: number) {
  const value = new URLSearchParams();
  if (filters.query) value.set('query', filters.query);
  if (filters.status) value.set('status', filters.status);
  if (filters.role) value.set('role', filters.role);
  if (cursor) value.set('cursor', cursor);
  value.set('size', String(size));
  return value.toString();
}

export const adminMembersApi = {
  list: (filters: AdminMemberFilters, cursor: string | null, size = 20, signal?: AbortSignal) =>
    apiClient<AdminMemberPage>(`/api/admin/members?${query(filters, cursor, size)}`, { signal }),
  detail: (memberId: number, signal?: AbortSignal) =>
    apiClient<AdminMemberDetail>(`/api/admin/members/${memberId}`, { signal }),
  act: (memberId: number, request: AdminMemberActionRequest, idempotencyKey: string, signal?: AbortSignal) =>
    apiClient<AdminMemberDetail>(`/api/admin/members/${memberId}/actions`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify(request), signal,
    }),
};
