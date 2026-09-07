import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import MatchHistoryPage, { MatchHistoryCard } from './MatchHistoryPage';
import type { MatchHistoryItem } from '../api/matchHistory';

function item(overrides: Partial<MatchHistoryItem> = {}): MatchHistoryItem {
  return {
    groupId: 12,
    status: 'COMPLETED',
    festivalTitle: '춘천 마임축제',
    festivalAddress: '강원 춘천시',
    meetingPlaceName: '정문 앞',
    confirmedMemberCount: 3,
    endedAt: '2026-09-05T20:00:00+09:00',
    reportableUntil: '2026-09-19T20:00:00+09:00',
    reportable: true,
    members: [
      { memberId: 7, nickname: '여행자B', profileImageUrl: null, reported: false },
      { memberId: 8, nickname: '여행자C', profileImageUrl: null, reported: true },
    ],
    ...overrides,
  };
}

const card = (value: MatchHistoryItem) => renderToStaticMarkup(
  <MatchHistoryCard item={value} onOpenReport={() => undefined} />,
);

describe('MatchHistoryPage', () => {
  it('조회 전에는 로딩 상태를 보여준다', () => {
    // useEffect가 돌지 않는 SSR 마크업이라 초기 상태(LOADING)가 그대로 나온다.
    const html = renderToStaticMarkup(<MemoryRouter><MatchHistoryPage /></MemoryRouter>);
    expect(html).toContain('매칭 기록');
    expect(html).toContain('불러오는 중');
    expect(html).toContain('14일');
  });
});

describe('MatchHistoryCard', () => {
  it('아직 신고하지 않은 상대에게만 신고 버튼을 준다', () => {
    const html = card(item());
    expect(html).toContain('여행자B님 신고하기');
    expect(html).not.toContain('여행자C님 신고하기');
  });

  it('이미 신고한 상대는 신고됨으로 표시한다', () => {
    expect(card(item())).toContain('신고됨');
  });

  // Tailwind의 disabled: 클래스가 마크업에 항상 남으므로 속성으로 확인한다.
  it('신고 기간이 지나면 버튼을 비활성화한다', () => {
    const expired = card(item({ reportable: false }));
    expect(expired).toContain('신고 기간 종료');
    expect(expired).toContain('disabled=""');
  });

  it('신고 가능하면 버튼을 열어두고 만료 시각을 함께 알려준다', () => {
    const html = card(item());
    expect(html).toContain('신고 가능 (2026-09-19 20:00:00까지)');
    expect(html).not.toContain('disabled=""');
  });

  it('취소된 만남은 취소됨 배지로 구분한다', () => {
    expect(card(item({ status: 'CANCELLED' }))).toContain('취소됨');
    expect(card(item({ status: 'COMPLETED' }))).not.toContain('취소됨');
  });
});
