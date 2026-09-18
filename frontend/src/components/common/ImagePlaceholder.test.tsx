import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import ImagePlaceholder from './ImagePlaceholder';
import MapPlaceholder from './MapPlaceholder';

describe('ImagePlaceholder 크기 단계', () => {
  it('sm은 아이콘만 그린다 — 56~72px 썸네일에 문구를 넣으면 답답하다', () => {
    const html = renderToStaticMarkup(<ImagePlaceholder kind="TOUR" seed="설악산" size="sm" />);
    expect(html).toContain('aria-label="관광지 사진 준비 중"');
    expect(html).not.toContain('>관광지<');
  });

  it('md는 분류 문구를 함께 그린다', () => {
    const html = renderToStaticMarkup(<ImagePlaceholder kind="FOOD" seed="닭갈비" size="md" />);
    expect(html).toContain('>맛집<');
  });

  it('lg는 분류 문구와 능선 라인아트를 함께 그린다', () => {
    const html = renderToStaticMarkup(
      <ImagePlaceholder kind="FESTIVAL" seed="화천산천어축제" size="lg" />,
    );
    expect(html).toContain('>축제<');
    expect(html).toContain('<svg');
    expect(html).toContain('aria-hidden="true"');
  });
});

describe('ImagePlaceholder 접근성', () => {
  it('문구가 없는 sm에서도 스크린리더가 읽을 수 있게 role과 aria-label을 준다', () => {
    const html = renderToStaticMarkup(<ImagePlaceholder size="sm" />);
    expect(html).toContain('role="img"');
    expect(html).toContain('aria-label="사진 준비 중"');
  });
});

describe('MapPlaceholder', () => {
  it('사진 없음과 구분되게 좌표가 없다는 이유를 밝힌다', () => {
    const html = renderToStaticMarkup(<MapPlaceholder className="h-32 w-full" />);
    expect(html).toContain('좌표 정보가 없어 지도를 표시할 수 없어요');
    expect(html).toContain('role="img"');
  });
});
