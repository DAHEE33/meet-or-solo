import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import AdminLoginPage from './AdminLoginPage';

const markup = () => renderToStaticMarkup(
  <MemoryRouter><AdminLoginPage /></MemoryRouter>,
);

describe('AdminLoginPage', () => {
  it('아이디와 비밀번호 입력을 제공한다', () => {
    expect(markup()).toContain('name="username"');
    expect(markup()).toContain('type="password"');
  });

  /** 빈 값으로 눌러도 요청이 나가면 실패 횟수만 쌓여 계정이 잠긴다(docs/30). */
  it('입력 전에는 제출 버튼이 비활성이다', () => {
    expect(markup()).toContain('disabled=""');
  });

  /**
   * 비밀번호 관리자가 채워 넣을 수 있어야 한다.
   *
   * HTML 속성 이름은 대소문자를 구분하지 않고 SSR이 내보내는 표기도 그대로이므로,
   * 소문자로 낮춘 뒤 비교한다.
   */
  it('자동완성 힌트를 준다', () => {
    const html = markup().toLowerCase();
    expect(html).toContain('autocomplete="username"');
    expect(html).toContain('autocomplete="current-password"');
  });

  it('소셜 로그인으로 돌아갈 길을 남긴다', () => {
    expect(markup()).toContain('href="/login"');
  });
});
