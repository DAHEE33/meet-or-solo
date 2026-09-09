import { isValidElement, type ReactNode } from 'react';
import { describe, expect, it } from 'vitest';
import { AdminNavContent } from './AdminNav';

function nodes(node: ReactNode): Array<{ type: unknown; props: Record<string, unknown> }> {
  if (Array.isArray(node)) return node.flatMap(nodes);
  if (!isValidElement(node)) return [];
  return [node as never, ...nodes(node.props.children)];
}

describe('AdminNavContent', () => {
  it('현재 경로와 일치하는 메뉴에만 aria-current를 표시한다', () => {
    const tree = AdminNavContent({ pathname: '/admin/reports' });
    const links = nodes(tree).filter((node) => (node.props as { to?: string }).to !== undefined);
    // 대시보드/신고/회원/만남 장소/문의 5개다.
    expect(links).toHaveLength(5);
    expect(links.find((link) => link.props.to === '/admin/reports')?.props['aria-current']).toBe('page');
    expect(links.filter((link) => link.props.to !== '/admin/reports')
      .every((link) => link.props['aria-current'] === undefined)).toBe(true);
  });

  it('만남 장소 관리 메뉴를 포함한다', () => {
    const tree = AdminNavContent({ pathname: '/admin' });
    const links = nodes(tree).filter((node) => (node.props as { to?: string }).to !== undefined);
    expect(links.map((link) => link.props.to)).toContain('/admin/meeting-points');
  });

  it('미확인 안전 알림이 있으면 신고 관리 메뉴에만 badge를 표시한다', () => {
    const tree = AdminNavContent({ pathname: '/admin', openSafetyAlertCount: 3 });
    const badges = nodes(tree).filter(
      (node) => typeof (node.props as { 'aria-label'?: string })['aria-label'] === 'string'
        && (node.props as { 'aria-label': string })['aria-label'].startsWith('미확인 안전 알림'));
    expect(badges).toHaveLength(1);
    expect(badges[0].props['aria-label']).toBe('미확인 안전 알림 3건');
    expect(badges[0].props.children).toBe(3);

    const reportsLink = nodes(tree).find(
      (node) => (node.props as { to?: string }).to === '/admin/reports');
    expect(nodes(reportsLink?.props.children as never)).toContainEqual(badges[0]);
  });

  it('미확인 안전 알림이 없으면 badge를 표시하지 않는다', () => {
    for (const props of [{ pathname: '/admin' }, { pathname: '/admin', openSafetyAlertCount: 0 }]) {
      const badges = nodes(AdminNavContent(props)).filter(
        (node) => typeof (node.props as { 'aria-label'?: string })['aria-label'] === 'string'
          && (node.props as { 'aria-label': string })['aria-label'].startsWith('미확인 안전 알림'));
      expect(badges).toHaveLength(0);
    }
  });

  it('문의 관리 메뉴를 포함한다', () => {
    const tree = AdminNavContent({ pathname: '/admin' });
    const links = nodes(tree).filter((node) => (node.props as { to?: string }).to !== undefined);
    expect(links.map((link) => link.props.to)).toContain('/admin/inquiries');
  });

  it('미처리 문의가 있으면 문의 관리 메뉴에만 badge를 표시한다', () => {
    const tree = AdminNavContent({ pathname: '/admin', openInquiryCount: 2 });
    const badges = nodes(tree).filter(
      (node) => typeof (node.props as { 'aria-label'?: string })['aria-label'] === 'string'
        && (node.props as { 'aria-label': string })['aria-label'].startsWith('미처리 문의'));
    expect(badges).toHaveLength(1);
    expect(badges[0].props['aria-label']).toBe('미처리 문의 2건');
    expect(badges[0].props.children).toBe(2);

    const inquiriesLink = nodes(tree).find(
      (node) => (node.props as { to?: string }).to === '/admin/inquiries');
    expect(nodes(inquiriesLink?.props.children as never)).toContainEqual(badges[0]);
  });

  it('두 badge는 서로 다른 메뉴에 붙는다', () => {
    // 하나의 count가 다른 메뉴로 새면 관리자가 잘못된 곳을 본다.
    const tree = AdminNavContent({
      pathname: '/admin',
      openSafetyAlertCount: 3,
      openInquiryCount: 2,
    });
    const badged = nodes(tree).filter((node) => {
      const label = (node.props as { 'aria-label'?: string })['aria-label'];
      // nav 자체의 aria-label("관리자 메뉴")은 badge가 아니다.
      return typeof label === 'string' && /^미(확인|처리)/.test(label);
    });
    expect(badged.map((node) => node.props['aria-label'])).toEqual([
      '미확인 안전 알림 3건',
      '미처리 문의 2건',
    ]);
  });
});
