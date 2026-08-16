import { isValidElement, type ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  KakaoMapFallback,
  loadKakaoMaps,
  resetKakaoMapsLoaderForTest,
} from './KakaoMeetingPointMap';

class FakeScript extends EventTarget {
  id = '';
  async = false;
  src = '';
  removed = false;
  remove() { this.removed = true; }
}

let scripts: FakeScript[];

beforeEach(() => {
  scripts = [];
  const fakeDocument = {
    getElementById: (id: string) => scripts.find((script) => script.id === id && !script.removed) ?? null,
    createElement: () => new FakeScript(),
    head: { appendChild: (script: FakeScript) => { scripts.push(script); return script; } },
  };
  vi.stubGlobal('document', fakeDocument);
  vi.stubGlobal('window', {});
  resetKakaoMapsLoaderForTest();
});

afterEach(() => {
  resetKakaoMapsLoaderForTest();
  vi.unstubAllGlobals();
});

function text(node: ReactNode): string {
  if (Array.isArray(node)) return node.map(text).join('');
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (!isValidElement(node)) return '';
  const element = node;
  if (typeof element.type === 'function') {
    const Component = element.type as (props: typeof element.props) => ReactNode;
    return text(Component(element.props));
  }
  return text(element.props.children);
}

describe('KakaoMeetingPointMap fallback', () => {
  it('SDK가 없거나 로딩에 실패해도 장소 텍스트 확인 안내를 제공한다', () => {
    expect(text(<KakaoMapFallback />))
      .toContain('지도를 불러오지 못했어요. 장소명과 주소를 확인해주세요.');
  });

  it('동시 호출은 하나의 Promise와 script를 공유한다', () => {
    const first = loadKakaoMaps('test-key');
    const second = loadKakaoMaps('test-key');
    expect(second).toBe(first);
    expect(scripts).toHaveLength(1);
  });

  it('script 실패 시 태그와 공유 상태를 정리해 다음 진입에서 재시도한다', async () => {
    const first = loadKakaoMaps('test-key');
    scripts[0].dispatchEvent(new Event('error'));
    await expect(first).rejects.toThrow('Kakao Maps SDK 로드 실패');
    expect(scripts[0].removed).toBe(true);

    const retry = loadKakaoMaps('test-key');
    expect(scripts).toHaveLength(2);
    expect(retry).not.toBe(first);

    const maps = { load: (callback: () => void) => callback() };
    window.kakao = { maps: maps as never };
    scripts[1].dispatchEvent(new Event('load'));
    await expect(retry).resolves.toBe(maps);
  });
});
