import { describe, expect, it } from 'vitest';
import AdminHeader from './AdminHeader';

describe('AdminHeader', () => {
  it('브랜드명과 화면별 부제를 함께 표시한다', () => {
    const tree = AdminHeader({ title: '회원 조회·제재' });
    const heading = tree.props.children;
    expect(heading.props.children[0]).toBe('meet·or·solo ');
    expect(heading.props.children[1].props.children).toBe('회원 조회·제재');
  });
});
