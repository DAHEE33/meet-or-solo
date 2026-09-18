import { describe, expect, it } from 'vitest';
import { groupFestivalsByDisplayStatus } from './festival';

type Fixture = { id: number; status: 'ACTIVE' | 'ENDED'; eventStartDate: string | null; eventEndDate: string | null };

const festival = (id: number, overrides: Partial<Fixture> = {}): Fixture => ({
  id,
  status: 'ACTIVE',
  eventStartDate: null,
  eventEndDate: null,
  ...overrides,
});

describe('groupFestivalsByDisplayStatus', () => {
  // KST 기준 "오늘"로 고정 — resolveDisplayStatus가 Asia/Seoul 기준으로 날짜를 비교한다.
  const now = new Date('2026-08-31T12:00:00+09:00');

  it('진행 중/진행 예정/마감을 각 그룹으로 나눈다', () => {
    const ongoing = festival(1, { eventStartDate: '2026-08-30', eventEndDate: '2026-09-02' });
    const upcoming = festival(2, { eventStartDate: '2026-09-10', eventEndDate: '2026-09-12' });
    const endedByDate = festival(3, { eventStartDate: '2026-08-01', eventEndDate: '2026-08-10' });
    const endedByStatus = festival(4, { status: 'ENDED', eventStartDate: '2026-09-10', eventEndDate: '2026-09-12' });

    const groups = groupFestivalsByDisplayStatus([ongoing, upcoming, endedByDate, endedByStatus], now);

    expect(groups.ongoing).toEqual([ongoing]);
    expect(groups.upcoming).toEqual([upcoming]);
    expect(groups.ended).toEqual([endedByDate, endedByStatus]);
  });

  it('입력이 비어 있으면 세 그룹 모두 빈 배열이다', () => {
    expect(groupFestivalsByDisplayStatus([], now)).toEqual({ ongoing: [], upcoming: [], ended: [] });
  });

  it('원래 순서를 각 그룹 안에서 유지한다', () => {
    const first = festival(1, { eventStartDate: '2026-08-01', eventEndDate: '2026-08-05' });
    const second = festival(2, { eventStartDate: '2026-07-01', eventEndDate: '2026-07-05' });

    const groups = groupFestivalsByDisplayStatus([first, second], now);

    expect(groups.ended).toEqual([first, second]);
  });
});
