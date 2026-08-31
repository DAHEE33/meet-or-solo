import { describe, expect, it } from 'vitest';
import type { CurrentCheckinState } from '../hooks/useCurrentCheckin';
import { formatDurationLabel, resolveSoloCourseFestival } from './SoloCoursePage';

const loading: CurrentCheckinState = { status: 'loading' };
const loadedWith = (festivalId: number | null): CurrentCheckinState =>
  festivalId === null
    ? { status: 'loaded', checkin: null }
    : {
        status: 'loaded',
        checkin: {
          checkinId: 1,
          festivalId,
          festivalName: '테스트 축제',
          checkedInAt: '2026-08-18T10:00:00+09:00',
          expiresAt: '2026-08-18T11:00:00+09:00',
        },
      };
const error: CurrentCheckinState = { status: 'error' };

describe('resolveSoloCourseFestival', () => {
  it('location state의 festivalId를 체크인 조회보다 우선한다', () => {
    expect(resolveSoloCourseFestival({ festivalId: 7 }, loading)).toEqual({
      status: 'ready',
      festivalId: 7,
    });
    expect(resolveSoloCourseFestival({ festivalId: 7 }, loadedWith(9))).toEqual({
      status: 'ready',
      festivalId: 7,
    });
  });

  it('location state가 없으면 체크인 조회가 끝날 때까지 loading을 유지한다', () => {
    expect(resolveSoloCourseFestival(null, loading)).toEqual({ status: 'loading' });
  });

  it('체크인 조회가 끝나면 체크인된 축제를 사용한다', () => {
    expect(resolveSoloCourseFestival(null, loadedWith(3))).toEqual({
      status: 'ready',
      festivalId: 3,
    });
  });

  it('체크인이 없거나 조회에 실패하면 festivalId 없이 ready를 반환한다', () => {
    expect(resolveSoloCourseFestival(null, loadedWith(null))).toEqual({
      status: 'ready',
      festivalId: null,
    });
    expect(resolveSoloCourseFestival(null, error)).toEqual({ status: 'ready', festivalId: null });
  });

  it('location state의 잘못된 값(0 이하, 문자열 아님)은 무시한다', () => {
    expect(resolveSoloCourseFestival({ festivalId: 0 }, loadedWith(3))).toEqual({
      status: 'ready',
      festivalId: 3,
    });
  });
});

describe('formatDurationLabel', () => {
  it('시간과 분이 모두 있으면 함께 표시한다', () => {
    expect(formatDurationLabel(223)).toBe('3시간 43분');
  });

  it('정확히 시간 단위면 분은 생략한다', () => {
    expect(formatDurationLabel(240)).toBe('4시간');
  });

  it('한 시간 미만이면 분만 표시한다', () => {
    expect(formatDurationLabel(45)).toBe('45분');
  });

  it('0분은 0분으로 표시한다', () => {
    expect(formatDurationLabel(0)).toBe('0분');
  });
});
