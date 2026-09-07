import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import AdminReportsPage from './AdminReportsPage';

/**
 * 안전 알림 UI가 실제 사용자 경로에서 도달 가능한지 확인한다.
 *
 * 컴포넌트를 직접 호출하는 단위 테스트만으로는 화면에 실제로 붙어 있는지 알 수 없다.
 * useEffect가 돌지 않는 SSR 마크업이므로 신고 목록은 초기 LOADING 상태로 그려진다.
 */
describe('AdminReportsPage', () => {
  it('신고 관리 화면에 안전 알림 섹션이 함께 붙어 있다', () => {
    const html = renderToStaticMarkup(<MemoryRouter><AdminReportsPage /></MemoryRouter>);
    expect(html).toContain('신고 누적 안전 알림');
    expect(html).toContain('aria-label="신고 누적 안전 알림"');
  });

  it('신고 목록이 아직 로딩 중이어도 안전 알림 섹션은 함께 렌더된다', () => {
    const html = renderToStaticMarkup(<MemoryRouter><AdminReportsPage /></MemoryRouter>);
    // 신고 목록 상태에 종속되지 않아야 목록 조회가 실패해도 알림을 볼 수 있다.
    expect(html).toContain('신고 목록을 불러오는 중이에요');
    expect(html).toContain('안전 알림을 불러오는 중이에요');
  });

  it('안전 알림 상태 filter를 화면에서 바로 바꿀 수 있다', () => {
    const html = renderToStaticMarkup(<MemoryRouter><AdminReportsPage /></MemoryRouter>);
    expect(html).toContain('aria-label="안전 알림 상태"');
  });
});
