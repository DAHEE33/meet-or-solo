import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { AdminMemberDetailDialog, AdminMemberForcedWithdrawalDialog } from './AdminMembersPage';
import type { AdminMemberDetail, AdminMemberForcedWithdrawalRequest } from '../api/adminMembers';
import { createAdminMembersSession, type AdminMembersState } from '../hooks/useAdminMembers';

const detail = (
  status: AdminMemberDetail['status'] = 'ACTIVE', id = 1,
): AdminMemberDetail => ({
  memberId: id, nickname: `회원${id}`, profileImageUrl: null, role: 'USER', status,
  penaltyScore: 0, mannerTemperature: 36.5, suspendedAt: null, suspendedUntil: null,
  createdAt: '2026-09-01T09:00:00+09:00', lastLoginAt: null, recentValidReportCount: 0,
  safetyReviewRequired: false, reports: [], actions: [],
});

const detailDialog = (status: AdminMemberDetail['status']) => renderToStaticMarkup(
  <AdminMemberDetailDialog
    detail={detail(status)} loading={false} error={null}
    onClose={() => undefined} onAction={() => undefined} onForceWithdraw={() => undefined}
  />,
);

const request = (
  overrides: Partial<AdminMemberForcedWithdrawalRequest> = {},
): AdminMemberForcedWithdrawalRequest => ({
  reasonCode: 'COMMUNITY_GUIDELINE', reasonNote: null, expectedStatus: 'ACTIVE',
  blockRejoin: true, ...overrides,
});

const withdrawalDialog = (overrides: Partial<AdminMemberForcedWithdrawalRequest> = {}) =>
  renderToStaticMarkup(
    <AdminMemberForcedWithdrawalDialog
      detail={detail()} initial={request(overrides)} submitting={false} error={null}
      onClose={() => undefined} onSubmit={() => undefined}
    />,
  );

const page = (items: AdminMemberDetail[] = [detail()]) => ({
  items, pagination: { size: 20, hasNext: false, nextCursor: null },
});

describe('관리자 강제 탈퇴 UI', () => {
  it('활성 회원 상세에 강제 탈퇴 버튼이 있다', () => {
    expect(detailDialog('ACTIVE')).toContain('강제 탈퇴');
  });

  /** 영구차단 회원은 로그인이 막혀 본인 탈퇴를 할 수 없다. 삭제 요청을 처리할 유일한 경로다. */
  it('영구차단 회원도 강제 탈퇴시킬 수 있다', () => {
    expect(detailDialog('BANNED')).toContain('강제 탈퇴');
  });

  it('이미 탈퇴한 회원에게는 강제 탈퇴 버튼을 노출하지 않는다', () => {
    expect(detailDialog('WITHDRAWN')).not.toContain('강제 탈퇴');
  });

  /**
   * 경고는 backend의 validateWarningStatus가 ACTIVE·PROFILE_REQUIRED·SUSPENDED만 허용한다.
   * 화면 조건을 그 목록과 맞추지 않으면 누르면 무조건 ADMIN_MEMBER_STATUS_CONFLICT가 나는
   * 버튼이 노출된다. 익명화된 회원에게는 통보할 대상 자체가 없다.
   */
  it.each(['ACTIVE', 'PROFILE_REQUIRED', 'SUSPENDED'] as const)(
    '%s 회원 상세에는 경고 버튼이 있다',
    (status) => {
      expect(detailDialog(status)).toContain('경고');
    },
  );

  it.each(['BANNED', 'WITHDRAWN', 'DELETED'] as const)(
    '%s 회원 상세에는 경고 버튼을 노출하지 않는다',
    (status) => {
      expect(detailDialog(status)).not.toContain('경고');
    },
  );

  /**
   * 관리자가 영구차단과 강제 탈퇴를 같은 조치로 오해하면 되돌릴 수 없는 처리를 가볍게 누른다.
   * 차이를 dialog 문구로 못 박는다.
   */
  it('되돌릴 수 없다는 차이를 영구차단과 비교해 명시한다', () => {
    const html = withdrawalDialog();

    expect(html).toContain('영구차단은 되돌릴 수 있지만 강제 탈퇴는 되돌릴 수 없습니다');
    expect(html).toContain('익명화');
    expect(html).toContain('신고와 제재 이력은 보존됩니다');
  });

  it('재가입 차단 체크박스가 기본으로 켜져 있다', () => {
    const html = withdrawalDialog({ blockRejoin: true });

    expect(html).toContain('재가입 영구 차단');
    expect(html).toContain('type="checkbox"');
    expect(html).toContain('checked=""');
  });

  it('탈퇴 대행이면 체크를 해제한다는 안내를 함께 둔다', () => {
    const html = withdrawalDialog({ blockRejoin: false });

    expect(html).toContain('탈퇴 대행이면 체크를 해제하세요');
    expect(html).not.toContain('checked=""');
  });

  it('감사 로그와 민감정보 입력 금지를 안내한다', () => {
    const html = withdrawalDialog();

    expect(html).toContain('감사 로그에 기록됩니다');
    expect(html).toContain('GPS 좌표를 입력하지 마세요');
  });
});

describe('createAdminMembersSession 강제 탈퇴', () => {
  it('강제 탈퇴 요청을 endpoint로 보내고 목록을 갱신한다', async () => {
    const forceWithdraw = vi.fn().mockResolvedValue(detail('WITHDRAWN'));
    let state!: AdminMembersState;
    const session = createAdminMembersSession({
      list: vi.fn().mockResolvedValue(page()),
      detail: vi.fn().mockResolvedValue(detail()),
      act: vi.fn(),
      forceWithdraw,
    }, (next) => { state = next; });
    await session.load();
    await session.openDetail(1);

    session.requestWithdrawal(request());
    await session.submitWithdrawal();

    expect(forceWithdraw).toHaveBeenCalledOnce();
    expect(forceWithdraw.mock.calls[0][1].blockRejoin).toBe(true);
    expect(state.items[0].status).toBe('WITHDRAWN');
    expect(state.pendingWithdrawal).toBeNull();
  });

  /** 되돌릴 수 없는 조치라 이중 제출이 두 번 나가면 안 된다. */
  it('이중 제출은 한 번만 호출한다', async () => {
    const forceWithdraw = vi.fn().mockResolvedValue(detail('WITHDRAWN'));
    const session = createAdminMembersSession({
      list: vi.fn().mockResolvedValue(page()),
      detail: vi.fn().mockResolvedValue(detail()),
      act: vi.fn(),
      forceWithdraw,
    }, () => undefined);
    await session.load();
    await session.openDetail(1);

    session.requestWithdrawal(request());
    const first = session.submitWithdrawal();
    const second = session.submitWithdrawal();

    expect(first).toBe(second);
    await first;
    expect(forceWithdraw).toHaveBeenCalledOnce();
  });

  it('실패하면 확인 요청을 유지하고 사유를 남긴다', async () => {
    let state!: AdminMembersState;
    const session = createAdminMembersSession({
      list: vi.fn().mockResolvedValue(page()),
      detail: vi.fn().mockResolvedValue(detail()),
      act: vi.fn(),
      forceWithdraw: vi.fn().mockRejectedValue(new Error('fail')),
    }, (next) => { state = next; });
    await session.load();
    await session.openDetail(1);

    session.requestWithdrawal(request());
    await session.submitWithdrawal();

    expect(state.detail?.status).toBe('ACTIVE');
    expect(state.pendingWithdrawal?.blockRejoin).toBe(true);
    expect(state.actionError).toBeInstanceOf(Error);
  });
});
