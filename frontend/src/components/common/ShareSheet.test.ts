import { describe, expect, it, vi } from 'vitest';
import { handleShareSheetKeyDown } from './ShareSheet';

describe('handleShareSheetKeyDown', () => {
  it('Escape는 시트를 닫는다', () => {
    const preventDefault = vi.fn();
    const onClose = vi.fn();

    handleShareSheetKeyDown({ key: 'Escape', shiftKey: false, preventDefault }, [], null, onClose);

    expect(preventDefault).toHaveBeenCalledOnce();
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('Tab은 focusable 목록 안에서 순환한다', () => {
    const preventDefault = vi.fn();
    const onClose = vi.fn();
    const first = { focus: vi.fn() } as unknown as HTMLElement;
    const last = { focus: vi.fn() } as unknown as HTMLElement;

    handleShareSheetKeyDown({ key: 'Tab', shiftKey: false, preventDefault }, [first, last], last, onClose);
    expect(first.focus).toHaveBeenCalledOnce();

    handleShareSheetKeyDown({ key: 'Tab', shiftKey: true, preventDefault }, [first, last], first, onClose);
    expect(last.focus).toHaveBeenCalledOnce();
  });

  it('focusable이 없으면 Tab을 무시한다', () => {
    const preventDefault = vi.fn();
    const onClose = vi.fn();

    handleShareSheetKeyDown({ key: 'Tab', shiftKey: false, preventDefault }, [], null, onClose);

    expect(preventDefault).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });
});
