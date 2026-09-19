import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import type { AdminDashboardStats } from '../api/adminDashboard';
import { DashboardContent } from './AdminDashboardPage';

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

const markup = (value: AdminDashboardStats) => renderToStaticMarkup(
  <MemoryRouter><DashboardContent stats={value} /></MemoryRouter>,
);

describe('AdminDashboardPage', () => {
  it('서버 집계를 숫자 카드에 그대로 쓴다', () => {
    const html = markup(stats({
      totalMemberCount: 1842,
      todayMatchCount: 37,
      totalCheckinCount: 5211,
    }));

    expect(html).toContain('1,842');
    expect(html).toContain('37');
    expect(html).toContain('5,211');
  });

  /**
   * 회귀 방지: 대시보드는 `data/mock/adminStats.ts`를 렌더링했다. 전북 관광지가 박혀 있어
   * 강원 서비스의 화면으로 보이지 않았다.
   */
  it('목업 데이터가 남아 있지 않다', () => {
    const html = markup(stats());

    expect(html).not.toContain('전주 한옥마을');
    expect(html).not.toContain('남부시장 야시장');
  });

  it('인기 목록은 축제다 — 체크인은 축제에만 기록된다', () => {
    const html = markup(stats({
      popularFestivals: [{ festivalId: 1, title: '강릉커피축제', checkinCount: 41 }],
    }));

    expect(html).toContain('인기 축제 (체크인 기준)');
    expect(html).toContain('강릉커피축제');
    expect(html).toContain('41');
  });

  it('체크인 수에 비례해 막대 폭을 잡는다', () => {
    const html = markup(stats({
      popularFestivals: [
        { festivalId: 1, title: '많은 축제', checkinCount: 40 },
        { festivalId: 2, title: '적은 축제', checkinCount: 10 },
      ],
    }));

    expect(html).toContain('width:100%');
    expect(html).toContain('width:25%');
  });

  it('신고와 문의를 한 목록으로 최신순 표시하고 각 관리 화면으로 잇는다', () => {
    const html = markup(stats({
      recentReports: [{
        reportId: 1, reasonCode: 'NO_SHOW', status: 'SUBMITTED',
        reportedNickname: '홍길동', createdAt: '2026-09-18T01:00:00Z',
      }],
      recentInquiries: [{
        inquiryId: 7, category: 'BUG', title: '체크인이 안 돼요', status: 'RECEIVED',
        createdAt: '2026-09-18T03:00:00Z',
      }],
    }));

    expect(html).toContain('오류 제보 · 체크인이 안 돼요');
    expect(html).toContain('나타나지 않음 · 홍길동');
    expect(html).toContain('href="/admin/reports"');
    expect(html).toContain('href="/admin/inquiries"');
    expect(html.indexOf('체크인이 안 돼요')).toBeLessThan(html.indexOf('홍길동'));
  });

  it('집계가 비어 있으면 0이 아니라 안내 문구를 보여준다', () => {
    const html = markup(stats());

    expect(html).toContain('아직 체크인 기록이 없어요.');
    expect(html).toContain('새로 들어온 신고나 문의가 없어요.');
  });
});
