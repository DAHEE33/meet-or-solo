import { isValidElement, type ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { handleUnblockDialogKeyDown, UnblockDialogContent } from './BlockedMembersPage';

function nodes(node: ReactNode): Array<{ type: unknown; props: Record<string, unknown> }> {
  if (Array.isArray(node)) return node.flatMap(nodes);
  if (!isValidElement(node)) return [];
  return [node as never, ...nodes(node.props.children)];
}
function content(node: ReactNode): string {
  if (Array.isArray(node)) return node.map(content).join('');
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (!isValidElement(node)) return '';
  return content(node.props.children);
}

describe('BlockedMembersPage', () => {
  const state = { status: 'READY' as const, blocks: [], target: { blockedMemberId: 27, nickname: '테스트', profileImageUrl: null, blockedAt: '2026-08-14T07:00:00Z' }, submitting: false, error: null, successMessage: null };
  it('최종 dialog에 대상과 세 정책 및 접근성 연결만 표시한다', () => {
    const tree = UnblockDialogContent({ state, onClose: vi.fn(), onSubmit: vi.fn() });
    const text = content(tree);
    expect(text).toContain('테스트님의 차단을 해제할까요?');
    expect(text).toContain('향후 다시 매칭될 수 있습니다');
    expect(text).toContain('현재 진행 중인 MatchRoom은 즉시 변경되지 않습니다');
    expect(text).toContain('해제 사실은 상대에게 알려지지 않습니다');
    expect(text).not.toMatch(/blockId|blockerMemberId|reason/);
    expect(nodes(tree).find((item) => item.props.role === 'dialog')?.props).toMatchObject({ 'aria-modal': 'true', 'aria-labelledby': 'unblock-title', 'aria-describedby': 'unblock-description' });
  });

  it('제출 중 action을 제한하고 실패 안내를 live alert로 제공한다', () => {
    const tree = UnblockDialogContent({ state: { ...state, submitting: true, error: new Error('fail') }, onClose: vi.fn(), onSubmit: vi.fn() });
    const buttons = nodes(tree).filter((item) => item.type === 'button');
    expect(buttons.every((button) => button.props.disabled === true)).toBe(true);
    expect(nodes(tree).find((item) => item.props.role === 'alert')?.props['aria-live']).toBe('assertive');
  });

  it('Escape와 Tab/Shift+Tab 순환을 처리하고 제출 중 Escape는 무시한다', () => {
    const first = { focus: vi.fn() } as unknown as HTMLElement;
    const last = { focus: vi.fn() } as unknown as HTMLElement;
    const close = vi.fn(); const preventDefault = vi.fn();
    handleUnblockDialogKeyDown({ key: 'Escape', shiftKey: false, preventDefault }, [first, last], first, false, close);
    expect(close).toHaveBeenCalledOnce();
    handleUnblockDialogKeyDown({ key: 'Escape', shiftKey: false, preventDefault }, [first, last], first, true, close);
    expect(close).toHaveBeenCalledOnce();
    handleUnblockDialogKeyDown({ key: 'Tab', shiftKey: false, preventDefault }, [first, last], last, false, close);
    expect(first.focus).toHaveBeenCalledOnce();
    handleUnblockDialogKeyDown({ key: 'Tab', shiftKey: true, preventDefault }, [first, last], first, false, close);
    expect(last.focus).toHaveBeenCalledOnce();
  });
});
