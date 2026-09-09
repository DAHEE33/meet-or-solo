import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import SplashScreen from './SplashScreen';

describe('SplashScreen', () => {
  it('워드마크를 LoginPage와 같은 세 조각 텍스트로 그린다', () => {
    const html = renderToStaticMarkup(<SplashScreen fadingOut={false} />);
    expect(html).toContain('>meet<');
    expect(html).toContain('>·or·<');
    expect(html).toContain('>solo<');
  });

  it('핀 심볼은 장식이므로 보조기술에서 감춘다', () => {
    const html = renderToStaticMarkup(<SplashScreen fadingOut={false} />);
    expect(html).toContain('<svg');
    expect(html).toContain('aria-hidden="true"');
  });

  it('광선 3줄을 각각 다른 지연으로 재생한다', () => {
    const html = renderToStaticMarkup(<SplashScreen fadingOut={false} />);
    expect(html).toContain('animate-ray-pop-1');
    expect(html).toContain('animate-ray-pop-2');
    expect(html).toContain('animate-ray-pop-3');
  });

  it('애니메이션을 줄이는 설정에서는 연출을 끈다', () => {
    const html = renderToStaticMarkup(<SplashScreen fadingOut={false} />);
    expect(html).toContain('motion-reduce:animate-none');
  });

  it('페이드아웃 중에는 투명해지고 아래 화면의 클릭을 통과시킨다', () => {
    const html = renderToStaticMarkup(<SplashScreen fadingOut />);
    expect(html).toContain('opacity-0');
    expect(html).toContain('pointer-events-none');
  });

  it('재생 중에는 오버레이가 화면을 덮는다', () => {
    const html = renderToStaticMarkup(<SplashScreen fadingOut={false} />);
    expect(html).toContain('opacity-100');
    expect(html).not.toContain('pointer-events-none');
  });
});
