import { describe, expect, it } from 'vitest';
import type { AdminDashboardStats } from '../api/adminDashboard';
import {
  ADMIN_DASHBOARD_ISSUE_LIMIT,
  barWidthPercent,
  mergeRecentIssues,
} from './adminDashboard';

function stats(overrides: Partial<AdminDashboardStats> = {}): AdminDashboardStats {
  return {
    totalMemberCount: 0,
    todayMatchCount: 0,
    totalCheckinCount: 0,
    popularFestivals: [],
    recentReports: [],
    recentInquiries: [],
    ...overrides,
  };
}

describe('mergeRecentIssues', () => {
  it('신고와 문의를 최신순 한 목록으로 합친다', () => {
    const merged = mergeRecentIssues(stats({
      recentReports: [
        { reportId: 1, reasonCode: 'NO_SHOW', status: 'SUBMITTED', reportedNickname: '홍길동',
          createdAt: '2026-09-18T01:00:00Z' },
      ],
      recentInquiries: [
        { inquiryId: 7, category: 'BUG', title: '체크인이 안 돼요', status: 'RECEIVED',
          createdAt: '2026-09-18T03:00:00Z' },
        { inquiryId: 8, category: 'MATCHING', title: '매칭 실패', status: 'ANSWERED',
          createdAt: '2026-09-17T23:00:00Z' },
      ],
    }));

    expect(merged.map((issue) => issue.key))
      .toEqual(['inquiry-7', 'report-1', 'inquiry-8']);
  });

  it('시각이 같으면 신고를 문의보다 앞에 둔다', () => {
    const sameMoment = '2026-09-18T01:00:00Z';
    const merged = mergeRecentIssues(stats({
      recentReports: [
        { reportId: 1, reasonCode: 'RUDE', status: 'SUBMITTED', reportedNickname: '홍길동',
          createdAt: sameMoment },
      ],
      recentInquiries: [
        { inquiryId: 7, category: 'BUG', title: '오류', status: 'RECEIVED', createdAt: sameMoment },
      ],
    }));

    expect(merged.map((issue) => issue.kind)).toEqual(['신고', '문의']);
  });

  it('신고는 사유와 대상 닉네임으로 줄을 만든다 — 본문은 서버가 내리지 않는다', () => {
    const [issue] = mergeRecentIssues(stats({
      recentReports: [
        { reportId: 1, reasonCode: 'NO_SHOW', status: 'REVIEWING', reportedNickname: '탈퇴한 회원',
          createdAt: '2026-09-18T01:00:00Z' },
      ],
    }));

    expect(issue.summary).toBe('나타나지 않음 · 탈퇴한 회원');
    expect(issue.statusLabel).toBe('검토 중');
    expect(issue.href).toBe('/admin/reports');
  });

  it('문의는 분류와 제목으로 줄을 만든다', () => {
    const [issue] = mergeRecentIssues(stats({
      recentInquiries: [
        { inquiryId: 7, category: 'FESTIVAL_DATA', title: '축제 정보가 달라요', status: 'CLOSED',
          createdAt: '2026-09-18T01:00:00Z' },
      ],
    }));

    expect(issue.summary).toBe('축제·관광지 정보 · 축제 정보가 달라요');
    expect(issue.statusLabel).toBe('종결');
    expect(issue.href).toBe('/admin/inquiries');
  });

  it('표시 한도를 넘는 건은 잘라낸다', () => {
    const many = Array.from({ length: 5 }, (_, index) => ({
      reportId: index + 1,
      reasonCode: 'RUDE' as const,
      status: 'SUBMITTED' as const,
      reportedNickname: `회원${index}`,
      createdAt: `2026-09-18T0${index}:00:00Z`,
    }));
    const merged = mergeRecentIssues(stats({
      recentReports: many,
      recentInquiries: many.map((report) => ({
        inquiryId: report.reportId,
        category: 'ETC' as const,
        title: `문의${report.reportId}`,
        status: 'RECEIVED' as const,
        createdAt: report.createdAt,
      })),
    }));

    expect(merged).toHaveLength(ADMIN_DASHBOARD_ISSUE_LIMIT);
  });

  it('둘 다 비어 있으면 빈 목록이다', () => {
    expect(mergeRecentIssues(stats())).toEqual([]);
  });
});

describe('barWidthPercent', () => {
  it('최대값을 100%로 잡는다', () => {
    expect(barWidthPercent(40, 40)).toBe(100);
    expect(barWidthPercent(10, 40)).toBe(25);
  });

  it('최대값이 0이면 0을 돌려준다 — NaN이 style에 들어가지 않게 한다', () => {
    expect(barWidthPercent(0, 0)).toBe(0);
  });
});
