import { describe, expect, it } from 'vitest';
import { resolveFestivalId } from './MatchingConditionPage';

describe('resolveFestivalId', () => {
  it('location state 값을 개발 환경 fallback보다 우선한다', () => {
    expect(resolveFestivalId({ festivalId: 7 }, true, '9')).toBe(7);
  });

  it('개발 환경에서만 VITE_DEV_FESTIVAL_ID를 fallback으로 사용한다', () => {
    expect(resolveFestivalId(null, true, '9')).toBe(9);
    expect(resolveFestivalId(null, false, '9')).toBeNull();
  });

  it('유효한 festivalId가 없으면 null을 반환하여 신청을 막는다', () => {
    expect(resolveFestivalId(undefined, true, '')).toBeNull();
    expect(resolveFestivalId({ festivalId: 0 }, false, undefined)).toBeNull();
  });
});
