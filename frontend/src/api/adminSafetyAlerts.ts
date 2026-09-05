import { apiClient } from './apiClient';

export type AdminSafetyAlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'CLOSED';
export type AdminSafetyAlertType = 'REPORT_THRESHOLD';

export type AdminSafetyAlert = {
  alertId: number;
  alertType: AdminSafetyAlertType;
  status: AdminSafetyAlertStatus;
  reportedMemberId: number;
  reportedMemberNickname: string | null;
  reportedMemberProfileImageUrl: string | null;
  reportedMemberStatus: string;
  triggerReportId: number;
  validReportCount: number;
  handledAt: string | null;
  createdAt: string;
};

export type AdminSafetyAlertPage = {
  alerts: AdminSafetyAlert[];
  pagination: { size: number; hasNext: boolean; nextCursor: string | null };
  openCount: number;
};

function query(status: AdminSafetyAlertStatus | '', cursor: string | null, size: number): string {
  const parameters = new URLSearchParams();
  if (status) parameters.set('status', status);
  if (cursor) parameters.set('cursor', cursor);
  parameters.set('size', String(size));
  return parameters.toString();
}

export const adminSafetyAlertsApi = {
  list: (
    status: AdminSafetyAlertStatus | '',
    cursor: string | null,
    size = 20,
    signal?: AbortSignal,
  ) => apiClient<AdminSafetyAlertPage>(
    `/api/admin/safety-alerts?${query(status, cursor, size)}`,
    { signal },
  ),
  acknowledge: (alertId: number, signal?: AbortSignal) =>
    apiClient<AdminSafetyAlert>(`/api/admin/safety-alerts/${alertId}/acknowledgement`, {
      method: 'PUT',
      signal,
    }),
};
