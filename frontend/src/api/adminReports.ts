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

/**
 * 신고 상태·사유의 한국어 라벨. 신고 목록과 대시보드가 같은 문구를 쓰도록 여기에 둔다.
 * 문의 쪽 `inquiryCategoryLabel`(api/inquiries.ts)과 같은 자리다.
 */
const REPORT_STATUS_LABELS: Record<AdminReportStatus, string> = {
  SUBMITTED: '접수됨',
  REVIEWING: '검토 중',
  RESOLVED: '유효 신고',
  REJECTED: '기각',
  ACTION_TAKEN: '제재 완료',
};

const REPORT_REASON_LABELS: Record<AdminReportReasonCode, string> = {
  RUDE: '무례한 행동',
  SEXUAL_HARASSMENT: '성희롱',
  NO_SHOW: '나타나지 않음',
  SCAM: '사기 의심',
  SAFETY: '안전 문제',
  OTHER: '기타',
};

/** 라벨을 모르는 code는 code 자체를 보여준다. 값이 늘었을 때 빈 칸이 되지 않게 한다. */
export function adminReportStatusLabel(status: AdminReportStatus): string {
  return REPORT_STATUS_LABELS[status] ?? status;
}

export function adminReportReasonLabel(reason: AdminReportReasonCode): string {
  return REPORT_REASON_LABELS[reason] ?? reason;
}

/** 필터 select용 선택지. 맨 앞의 빈 값은 "전체"다. */
export const ADMIN_REPORT_STATUS_OPTIONS: Array<{ value: AdminReportStatus | ''; label: string }> = [
  { value: '', label: '전체 상태' },
  ...(Object.keys(REPORT_STATUS_LABELS) as AdminReportStatus[])
    .map((status) => ({ value: status, label: REPORT_STATUS_LABELS[status] })),
];

export const ADMIN_REPORT_REASON_OPTIONS: Array<{ value: AdminReportReasonCode | ''; label: string }> = [
  { value: '', label: '전체 사유' },
  ...(Object.keys(REPORT_REASON_LABELS) as AdminReportReasonCode[])
    .map((reason) => ({ value: reason, label: REPORT_REASON_LABELS[reason] })),
];

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
