import type {
  AdminDashboardRecentInquiry,
  AdminDashboardRecentReport,
  AdminDashboardStats,
} from '../api/adminDashboard';
import { adminReportReasonLabel, adminReportStatusLabel } from '../api/adminReports';
import { inquiryCategoryLabel, inquiryStatusLabel } from '../api/inquiries';

/**
 * 대시보드 "신고 / 문의" 목록의 한 줄.
 *
 * 서버는 신고와 문의를 별개 목록으로 내린다(식별자도 상태 enum도 다르다).
 * 관리자에게는 "최근 들어온 것"이 하나의 시간순 흐름이므로 화면에서만 합친다.
 */
export type AdminDashboardIssue = {
  key: string;
  kind: '신고' | '문의';
  /** 무슨 건인지 한 줄로. 신고는 사유, 문의는 제목이다. */
  summary: string;
  statusLabel: string;
  createdAt: string;
  /** 상세를 볼 수 있는 관리자 화면. */
  href: string;
};

/** 화면에 남길 최대 줄 수. 카드 한 장 높이를 넘지 않게 한다. */
export const ADMIN_DASHBOARD_ISSUE_LIMIT = 6;

function fromReport(report: AdminDashboardRecentReport): AdminDashboardIssue {
  return {
    key: `report-${report.reportId}`,
    kind: '신고',
    // 신고 본문은 암호화돼 있어 서버가 내리지 않는다. 사유와 대상만으로 줄을 만든다.
    summary: `${adminReportReasonLabel(report.reasonCode)} · ${report.reportedNickname}`,
    statusLabel: adminReportStatusLabel(report.status),
    createdAt: report.createdAt,
    href: '/admin/reports',
  };
}

function fromInquiry(inquiry: AdminDashboardRecentInquiry): AdminDashboardIssue {
  return {
    key: `inquiry-${inquiry.inquiryId}`,
    kind: '문의',
    summary: `${inquiryCategoryLabel(inquiry.category)} · ${inquiry.title}`,
    statusLabel: inquiryStatusLabel(inquiry.status),
    createdAt: inquiry.createdAt,
    href: '/admin/inquiries',
  };
}

/**
 * 신고와 문의를 최신순 한 목록으로 합친다.
 *
 * 시각이 같으면 신고를 앞에 둔다. 두 목록의 id는 서로 비교할 수 없어 tiebreaker로 쓸 수 없고,
 * 순서가 호출마다 달라지면 화면이 이유 없이 흔들린다. 신고가 문의보다 급한 건이라 앞이다.
 */
export function mergeRecentIssues(stats: AdminDashboardStats): AdminDashboardIssue[] {
  const reports = stats.recentReports.map(fromReport);
  const inquiries = stats.recentInquiries.map(fromInquiry);
  return [...reports, ...inquiries]
    .sort((left, right) => {
      const gap = Date.parse(right.createdAt) - Date.parse(left.createdAt);
      if (gap !== 0) return gap;
      return left.kind === right.kind ? 0 : left.kind === '신고' ? -1 : 1;
    })
    .slice(0, ADMIN_DASHBOARD_ISSUE_LIMIT);
}

/**
 * 막대 그래프의 폭(%)이다. 최대값을 100%로 잡는다.
 *
 * 최대값이 0이면 0을 돌려준다. 나눗셈을 그대로 두면 `NaN`이 되어 style에 들어가고,
 * 막대가 폭 없이 렌더링되는 대신 콘솔 경고가 난다.
 */
export function barWidthPercent(count: number, maxCount: number): number {
  if (maxCount <= 0) return 0;
  return Math.round((count / maxCount) * 100);
}
