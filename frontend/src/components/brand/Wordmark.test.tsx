import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import Wordmark from './Wordmark';

const markup = () => renderToStaticMarkup(<Wordmark className="text-xl font-extrabold" />);

describe('Wordmark', () => {
  it('「혼자」는 ink, 「왔니」는 coral로 그린다', () => {
    const html = markup();
    expect(html).toContain('class="text-ink">혼자<');
    expect(html).toContain('>왔<');
    expect(html).toContain('>니<');
  });

  /**
   * transform은 inline 요소에 적용되지 않는다. inline-block이 빠지면 기울기가 조용히
   * 사라져 로고 이미지와 다른 워드마크가 되는데, 화면을 직접 보지 않으면 눈치채기 어렵다.
   */
  it('마지막 「니」만 8° 기울이고, transform이 먹도록 inline-block을 준다', () => {
    const html = markup();
    expect(html).toMatch(/<span class="[^"]*inline-block[^"]*rotate-\[-8deg\][^"]*">니<\/span>/);
    expect(html).not.toMatch(/rotate-\[-8deg\][^>]*>혼자</);
    expect(html).not.toMatch(/rotate-\[-8deg\][^>]*>왔</);
  });

  it('크기·굵기는 호출한 화면이 정한다', () => {
    expect(markup()).toContain('text-xl');
    expect(markup()).toContain('font-extrabold');
  });
});
