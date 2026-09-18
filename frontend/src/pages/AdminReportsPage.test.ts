import { isValidElement, type ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { AdminReportActionDialogContent, AdminReportDetailDialogContent, handleAdminDialogKeyDown, toApiFilters } from './AdminReportsPage';
import type { AdminReportDetail } from '../api/adminReports';

function nodes(node: ReactNode): Array<{ type: unknown; props: Record<string, unknown> }> {
  if (Array.isArray(node)) return node.flatMap(nodes);
  if (!isValidElement(node)) return [];
  return [node as never, ...nodes(node.props.children)];
}
function text(node: ReactNode): string {
  if (Array.isArray(node)) return node.map(text).join('');
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (!isValidElement(node)) return '';
  return text(node.props.children);
}
const detail: AdminReportDetail = {
  reportId: 31, group: { groupId: 41, status: 'COMPLETED', confirmedAt: '2026-08-15T09:00:00+09:00' },
  reasonCode: 'SAFETY', status: 'SUBMITTED',
  reporter: { memberId: 21, nickname: '신고자', profileImageUrl: null, memberStatus: 'ACTIVE' },
  reportedMember: { memberId: 22, nickname: '피신고자', profileImageUrl: null, memberStatus: 'ACTIVE' },
  createdAt: '2026-08-15T10:00:00+09:00', updatedAt: '2026-08-15T10:00:00+09:00', resolvedAt: null,
};

describe('AdminReportsPage', () => {
  it('datetime-local 값을 KST offset API filter로 바꾼다', () => {
    expect(toApiFilters({ status: 'SUBMITTED', reason: '', createdFrom: '2026-08-01T10:30', createdTo: '' }))
      .toMatchObject({ createdFrom: '2026-08-01T10:30:00+09:00', createdTo: '' });
  });

  it('상세 dialog는 최소 정보와 접근성 연결만 표시하고 내부 ID·민감정보를 표시하지 않는다', () => {
    const tree = AdminReportDetailDialogContent({ detail, loading: false, error: null, onRetry: vi.fn(), onClose: vi.fn(), onAction: vi.fn() });
    const content = text(tree);
    expect(content).toContain('안전 문제'); expect(content).toContain('신고자'); expect(content).toContain('피신고자');
    expect(content).not.toMatch(/31|41|memberId|reportId|groupId|email|provider|token|암호화/);
    expect(nodes(tree).find((node) => node.props.role === 'dialog')?.props).toMatchObject({
      'aria-modal': 'true', 'aria-labelledby': 'report-detail-title', 'aria-describedby': 'report-detail-description',
    });
  });

  it('위험 action 확인 dialog는 제재 없음 안내와 제출 중 제한을 제공한다', () => {
    const tree = AdminReportActionDialogContent({ detail, targetStatus: 'RESOLVED', submitting: true, error: new Error('fail'), onClose: vi.fn(), onSubmit: vi.fn() });
    expect(text(tree)).toContain('제재나 알림은 생성하지 않습니다');
    expect(nodes(tree).filter((node) => node.type === 'button').every((button) => button.props.disabled === true)).toBe(true);
    expect(nodes(tree).find((node) => node.props.role === 'alert')?.props['aria-live']).toBe('assertive');
  });

  it('Escape와 Tab 순환 및 제출 중 Escape 차단을 처리한다', () => {
    const first = { focus: vi.fn() } as unknown as HTMLElement; const last = { focus: vi.fn() } as unknown as HTMLElement;
    const close = vi.fn(); const preventDefault = vi.fn();
    handleAdminDialogKeyDown({ key: 'Escape', shiftKey: false, preventDefault }, [first, last], first, false, close);
    expect(close).toHaveBeenCalledOnce();
    handleAdminDialogKeyDown({ key: 'Escape', shiftKey: false, preventDefault }, [first, last], first, true, close);
    expect(close).toHaveBeenCalledOnce();
    handleAdminDialogKeyDown({ key: 'Tab', shiftKey: false, preventDefault }, [first, last], last, false, close);
    handleAdminDialogKeyDown({ key: 'Tab', shiftKey: true, preventDefault }, [first, last], first, false, close);
    expect(first.focus).toHaveBeenCalledOnce(); expect(last.focus).toHaveBeenCalledOnce();
  });
});
