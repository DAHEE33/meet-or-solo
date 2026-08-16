import { apiClient } from './apiClient';

export type AdminSession = {
  memberId: number;
  nickname: string;
  role: 'ADMIN';
};

export type AdminReportStatus =
  | 'SUBMITTED'
  | 'REVIEWING'
  | 'RESOLVED'
  | 'REJECTED'
  | 'ACTION_TAKEN';

export type AdminReportTargetStatus = 'REVIEWING' | 'RESOLVED' | 'REJECTED';
export type AdminReportReasonCode =
  | 'RUDE'
  | 'SEXUAL_HARASSMENT'
  | 'NO_SHOW'
  | 'SCAM'
  | 'SAFETY'
  | 'OTHER';

export type AdminReportMember = {
  memberId: number;
  nickname: string;
  profileImageUrl: string | null;
  memberStatus: string;
};

export type AdminReportListItem = {
  reportId: number;
  groupId: number | null;
  reasonCode: AdminReportReasonCode;
  status: AdminReportStatus;
  reporter: AdminReportMember;
  reportedMember: AdminReportMember;
  createdAt: string;
  updatedAt: string;
};

export type AdminReportDetail = Omit<AdminReportListItem, 'groupId'> & {
  group: { groupId: number; status: string; confirmedAt: string } | null;
  resolvedAt: string | null;
};

export type AdminReportFilters = {
  status: AdminReportStatus | '';
  reason: AdminReportReasonCode | '';
  createdFrom: string;
  createdTo: string;
};

export type AdminReportPage = {
  items: AdminReportListItem[];
  pagination: { size: number; hasNext: boolean; nextCursor: string | null };
};

function query(filters: AdminReportFilters, cursor: string | null, size: number): string {
  const parameters = new URLSearchParams();
  if (filters.status) parameters.set('status', filters.status);
  if (filters.reason) parameters.set('reason', filters.reason);
  if (filters.createdFrom) parameters.set('createdFrom', filters.createdFrom);
  if (filters.createdTo) parameters.set('createdTo', filters.createdTo);
  if (cursor) parameters.set('cursor', cursor);
  parameters.set('size', String(size));
  return parameters.toString();
}

export const adminReportsApi = {
  getSession: (signal?: AbortSignal) => apiClient<AdminSession>('/api/admin/me', { signal }),
  list: (filters: AdminReportFilters, cursor: string | null, size = 20, signal?: AbortSignal) =>
    apiClient<AdminReportPage>(`/api/admin/reports?${query(filters, cursor, size)}`, { signal }),
  detail: (reportId: number, signal?: AbortSignal) =>
    apiClient<AdminReportDetail>(`/api/admin/reports/${reportId}`, { signal }),
  changeStatus: (
    reportId: number,
    targetStatus: AdminReportTargetStatus,
    signal?: AbortSignal,
  ) => apiClient<AdminReportDetail>(`/api/admin/reports/${reportId}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ targetStatus }),
    signal,
  }),
};
