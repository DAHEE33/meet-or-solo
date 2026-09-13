import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import CheckInHistoryPage, { CheckInHistoryCard } from './CheckInHistoryPage';
import type { CheckinHistoryItem } from '../api/checkin';

function item(overrides: Partial<CheckinHistoryItem> = {}): CheckinHistoryItem {
  return {
    checkinId: 401,
    festivalId: 12,
    festivalTitle: '춘천 마임축제',
    festivalAddress: '강원 춘천시 어딘가',
    distanceMeters: 137,
    status: 'ACTIVE',
    checkedInAt: '2026-09-05T20:00:00+09:00',
    expiresAt: '2026-09-05T21:00:00+09:00',
    ...overrides,
  };
}

const card = (value: CheckinHistoryItem) => renderToStaticMarkup(
  <MemoryRouter><CheckInHistoryCard item={value} /></MemoryRouter>,
);

describe('CheckInHistoryPage', () => {
  it('조회 전에는 로딩 상태를 보여준다', () => {
    // useEffect가 돌지 않는 SSR 마크업이라 초기 상태(LOADING)가 그대로 나온다.
    const html = renderToStaticMarkup(<MemoryRouter><CheckInHistoryPage /></MemoryRouter>);

    expect(html).toContain('체크인 기록');
    expect(html).toContain('불러오는 중');
  });
});

describe('CheckInHistoryCard', () => {
  it('축제 상세로 이동하는 링크로 그린다', () => {
    expect(card(item())).toContain('href="/festivals/12"');
  });

  it('체크인 시각과 축제로부터의 거리를 함께 보여준다', () => {
    const html = card(item());

    expect(html).toContain('2026-09-05 20:00:00');
    expect(html).toContain('137m');
    expect(html).toContain('강원 춘천시 어딘가');
  });

  // 상태는 서버가 판정해서 내려준다. 화면이 expiresAt으로 다시 계산하면 유효기간 정책이 갈라진다.
  it('서버가 내려준 상태를 그대로 배지로 그린다', () => {
    expect(card(item({ status: 'ACTIVE' }))).toContain('체크인 중');
    expect(card(item({ status: 'EXPIRED' }))).toContain('만료됨');
    expect(card(item({ status: 'CANCELLED' }))).toContain('취소됨');
  });

  it('만료된 기록에 체크인 중 배지를 붙이지 않는다', () => {
    expect(card(item({ status: 'EXPIRED' }))).not.toContain('체크인 중');
  });

  it('주소가 없는 축제도 그린다', () => {
    const html = card(item({ festivalAddress: null }));

    expect(html).toContain('춘천 마임축제');
    expect(html).not.toContain('강원');
  });
});
