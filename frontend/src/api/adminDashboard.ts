import { apiClient } from './apiClient';
import type { AdminReportReasonCode, AdminReportStatus } from './adminReports';
import type { InquiryCategory, InquiryStatus } from './inquiries';

/** 체크인 수 기준 인기 축제. 관광지가 아니라 축제다 — 체크인은 축제에만 존재한다. */
export type AdminDashboardPopularFestival = {
  festivalId: number;
  title: string;
  checkinCount: number;
};

export type AdminDashboardRecentReport = {
  reportId: number;
  reasonCode: AdminReportReasonCode;
  status: AdminReportStatus;
  /** 신고 대상. 탈퇴 회원은 서버가 '탈퇴한 회원'으로 바꿔 내린다. */
  reportedNickname: string;
  createdAt: string;
};

export type AdminDashboardRecentInquiry = {
  inquiryId: number;
  category: InquiryCategory;
  title: string;
  status: InquiryStatus;
  createdAt: string;
};

export type AdminDashboardStats = {
  totalMemberCount: number;
  todayMatchCount: number;
  totalCheckinCount: number;
  popularFestivals: AdminDashboardPopularFestival[];
  recentReports: AdminDashboardRecentReport[];
  recentInquiries: AdminDashboardRecentInquiry[];
};

export const adminDashboardApi = {
  stats: (signal?: AbortSignal) =>
    apiClient<AdminDashboardStats>('/api/admin/dashboard/stats', { signal }),
};
