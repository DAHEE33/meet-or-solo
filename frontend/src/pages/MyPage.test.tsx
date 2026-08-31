import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import MyPage from './MyPage';
import {
  isPreferenceStateKnown,
  preferenceActionLabel,
  preferenceStatusDescription,
  preferenceStatusLabel,
} from '../components/preference/preferenceStatus';

describe('MyPage', () => {
  it('기존 마이페이지 내용과 차단 회원 관리 진입 action을 함께 제공한다', () => {
    const html = renderToStaticMarkup(<MemoryRouter><MyPage /></MemoryRouter>);
    expect(html).toContain('마이페이지');
    expect(html).toContain('프로필 수정');
    expect(html).toContain('차단 회원 관리');
    expect(html).toContain('href="/mypage/blocks"');
  });

  it('취향 상태를 조회하기 전에는 상태 섹션을 그리지 않는다', () => {
    // useEffect가 돌지 않는 SSR 마크업이므로 초기 상태(LOADING)가 그대로 나온다.
    const html = renderToStaticMarkup(<MemoryRouter><MyPage /></MemoryRouter>);
    expect(isPreferenceStateKnown('LOADING')).toBe(false);
    expect(html).not.toContain('취향 전격 분석');
  });
});

/**
 * 상태 섹션 자체는 useEffect 결과에 의존해 SSR 마크업으로 확인할 수 없다.
 * jsdom이 없어 클릭·비동기 갱신을 재현할 수 없으므로 표시 규칙을 순수 함수로 검증한다.
 */
describe('MyPage 취향 상태 표시 규칙', () => {
  it('상태를 알 수 있을 때만 섹션을 그린다', () => {
    expect(isPreferenceStateKnown('NONE')).toBe(true);
    expect(isPreferenceStateKnown('COMPLETED')).toBe(true);
    expect(isPreferenceStateKnown('FAILED')).toBe(true);
    expect(isPreferenceStateKnown('ANALYZING')).toBe(true);
    expect(isPreferenceStateKnown('UNAVAILABLE')).toBe(false);
  });

  it('분석 완료·실패·미입력을 각각 다른 문구로 노출한다', () => {
    const labels = (['COMPLETED', 'FAILED', 'ANALYZING', 'NONE'] as const).map(preferenceStatusLabel);
    expect(new Set(labels).size).toBe(labels.length);
    expect(preferenceStatusDescription('FAILED')).toContain('다시 저장');
    expect(preferenceActionLabel('NONE')).toBe('입력하기');
  });
});
