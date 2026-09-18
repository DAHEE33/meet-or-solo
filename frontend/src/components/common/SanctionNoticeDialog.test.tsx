import { renderToStaticMarkup } from 'react-dom/server';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import SanctionNoticeDialog, { isSnoozed, snooze } from './SanctionNoticeDialog';
import { SANCTION_EVENT } from '../../api/apiClient';

/**
 * jsdom이 없어 이벤트 수신 후의 재렌더는 재현할 수 없다. 그래서 SSR 마크업으로 "이벤트가
 * 오기 전에는 아무것도 그리지 않는다"만 확인하고, dialog 본문은 AccountRestrictionNotice
 * 테스트(LoginPage.test.tsx)가 덮는다. 이벤트 이름 계약은 아래에서 확인한다.
 */
describe('SanctionNoticeDialog', () => {
  it('제재 안내를 받기 전에는 아무것도 렌더하지 않는다', () => {
    expect(renderToStaticMarkup(<SanctionNoticeDialog />)).toBe('');
  });

  it('apiClient와 같은 이벤트 이름을 쓴다', () => {
    // 이름이 갈라지면 dialog가 조용히 안 뜬다. 상수를 공유하는지 못 박는다.
    expect(SANCTION_EVENT).toBe('member-sanction');
  });
});

describe('하루 동안 보지 않기', () => {
  const store = new Map<string, string>();

  beforeEach(() => {
    store.clear();
    vi.stubGlobal('window', {
      localStorage: {
        getItem: (key: string) => store.get(key) ?? null,
        setItem: (key: string, value: string) => { store.set(key, value); },
      },
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('설정 전에는 팝업을 막지 않는다', () => {
    expect(isSnoozed(1_000)).toBe(false);
  });

  it('설정 후 24시간 동안만 막는다', () => {
    const now = 1_000_000;
    snooze(now);

    expect(isSnoozed(now)).toBe(true);
    expect(isSnoozed(now + 23 * 60 * 60 * 1000)).toBe(true);
    expect(isSnoozed(now + 24 * 60 * 60 * 1000)).toBe(false);
    expect(isSnoozed(now + 25 * 60 * 60 * 1000)).toBe(false);
  });

  it('저장소를 못 읽으면 팝업을 막지 않는다', () => {
    // 시크릿 모드나 site data 차단에서 던진다. 안내를 놓치는 것보다 한 번 더 보는 게 낫다.
    vi.stubGlobal('window', {
      localStorage: {
        getItem: () => { throw new Error('blocked'); },
        setItem: () => { throw new Error('blocked'); },
      },
    });

    expect(isSnoozed()).toBe(false);
    expect(() => snooze()).not.toThrow();
  });

  it('깨진 값은 무시한다', () => {
    store.set('sanctionNoticeSnoozedUntil', 'not-a-number');
    expect(isSnoozed()).toBe(false);
  });
});
