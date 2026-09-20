import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import AdminHeader from './AdminHeader';

const markup = () => renderToStaticMarkup(<AdminHeader title="회원 조회·제재" />);

describe('AdminHeader', () => {
  it('브랜드명과 화면별 부제를 함께 표시한다', () => {
    expect(markup()).toContain('>혼자<');
    expect(markup()).toContain('>니<');
    expect(markup()).toContain('회원 조회·제재');
  });

  /** 로그아웃 경로가 없으면 ID/PW로 들어온 관리자가 session을 끊을 방법이 없다(docs/30). */
  it('로그아웃 버튼을 제공한다', () => {
    expect(markup()).toContain('>로그아웃</button>');
  });
});
