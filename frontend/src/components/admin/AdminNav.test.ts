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
    expect(links).toHaveLength(4);
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
});
