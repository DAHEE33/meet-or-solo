import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import MyPage from './MyPage';

describe('MyPage', () => {
  it('기존 마이페이지 내용과 차단 회원 관리 진입 action을 함께 제공한다', () => {
    const html = renderToStaticMarkup(<MemoryRouter><MyPage /></MemoryRouter>);
    expect(html).toContain('마이페이지');
    expect(html).toContain('프로필 수정');
    expect(html).toContain('차단 회원 관리');
    expect(html).toContain('href="/mypage/blocks"');
  });
});
