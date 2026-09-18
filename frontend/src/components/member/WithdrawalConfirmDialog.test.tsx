import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import WithdrawalConfirmDialog from './WithdrawalConfirmDialog';
import type { SanctionNotice } from '../../api/types';

const render = (sanction: SanctionNotice | null) => renderToStaticMarkup(
  <WithdrawalConfirmDialog
    sanction={sanction}
    submitting={false}
    errorMessage={null}
    onClose={() => undefined}
    onConfirm={() => undefined}
  />,
);

function suspended(overrides: Partial<SanctionNotice> = {}): SanctionNotice {
  return {
    status: 'SUSPENDED',
    suspendedUntil: '2026-09-30T10:00:00+09:00',
    reasonCode: 'HARASSMENT',
    reasonMessage: '다른 이용자에 대한 부적절한 언행',
    contactEmail: null,
    rejoinAvailableAt: null,
    ...overrides,
  };
}

describe('WithdrawalConfirmDialog', () => {
  it('되돌릴 수 없다는 것과 7일 재가입 제한을 안내한다', () => {
    const html = render(null);

    expect(html).toContain('정말 탈퇴하시겠어요?');
    expect(html).toContain('되돌릴 수 없어요');
    expect(html).toContain('7일');
  });

  it('삭제되는 데이터와 보존되는 이력을 함께 알린다', () => {
    const html = render(null);

    expect(html).toContain('프로필 사진이 삭제돼요');
    expect(html).toContain('진행 중인 체크인과 동행 매칭은 취소돼요');
    expect(html).toContain('신고와 제재 기록은 안전을 위해 보관돼요');
  });

  /**
   * 이용정지 중 탈퇴가 제재 해제 수단으로 오해되면 안 된다.
   * 잔여 기간이 재가입 시 이어진다는 사실을 실행 전에 알려야 한다.
   */
  it('이용정지 중이면 잔여 정지 기간이 재가입 시 이어진다고 알린다', () => {
    const html = render(suspended());

    expect(html).toContain('남은 이용정지 기간은 탈퇴로 사라지지 않아요');
    expect(html).toContain('남은 기간');
    expect(html).toContain('이어서 적용돼요');
    // 종료 시각을 함께 보여줘야 사용자가 얼마가 남았는지 판단할 수 있다.
    expect(html).toContain('2026');
  });

  it('제재가 없으면 정지 기간 안내를 띄우지 않는다', () => {
    const html = render(null);

    expect(html).not.toContain('남은 이용정지 기간');
  });

  /** 영구정지 회원은 로그인이 막혀 이 dialog에 닿지 않지만, 문구가 새는지 함께 확인한다. */
  it('영구정지 안내에는 정지 기간 이어받기 문구를 넣지 않는다', () => {
    const html = render(suspended({ status: 'BANNED', suspendedUntil: null }));

    expect(html).not.toContain('남은 이용정지 기간');
  });

  it('처리 중에는 두 버튼을 모두 잠근다', () => {
    const html = renderToStaticMarkup(
      <WithdrawalConfirmDialog
        sanction={null}
        submitting
        errorMessage={null}
        onClose={() => undefined}
        onConfirm={() => undefined}
      />,
    );

    expect(html).toContain('탈퇴 처리 중...');
    // Tailwind의 disabled: 클래스 때문에 클래스명 검사로는 거짓 양성이 난다. 속성으로 본다.
    expect(html.match(/disabled=""/g)).toHaveLength(2);
  });

  it('실패하면 사유를 alert로 알리고 dialog를 닫지 않는다', () => {
    const html = renderToStaticMarkup(
      <WithdrawalConfirmDialog
        sanction={null}
        submitting={false}
        errorMessage="탈퇴 처리에 실패했습니다. 잠시 후 다시 시도해 주세요."
        onClose={() => undefined}
        onConfirm={() => undefined}
      />,
    );

    expect(html).toContain('role="alert"');
    expect(html).toContain('탈퇴 처리에 실패했습니다');
    expect(html).toContain('탈퇴하기');
  });
});
