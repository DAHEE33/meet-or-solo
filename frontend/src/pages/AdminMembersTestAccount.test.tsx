import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { AdminMemberDetailDialog } from './AdminMembersPage';
import type { AdminMemberDetail, AdminMemberPage } from '../api/adminMembers';
import { createAdminMembersSession, type AdminMembersState } from '../hooks/useAdminMembers';

const detail = (
  overrides: Partial<AdminMemberDetail> = {},
): AdminMemberDetail => ({
  memberId: 1, nickname: '회원1', profileImageUrl: null, role: 'USER', status: 'ACTIVE',
  penaltyScore: 0, mannerTemperature: 36.5, suspendedAt: null, suspendedUntil: null,
  createdAt: '2026-09-01T09:00:00+09:00', lastLoginAt: null, testAccount: false,
  recentValidReportCount: 0, safetyReviewRequired: false,
  reports: [], actions: [], mannerTemperatureAdjustments: [], ...overrides,
});

const page = (items: AdminMemberDetail[] = [detail()]): AdminMemberPage =>
  ({ items, pagination: { size: 20, hasNext: false, nextCursor: null } });

const dialog = (overrides: Partial<AdminMemberDetail> = {}, withHandler = true) =>
  renderToStaticMarkup(
    <AdminMemberDetailDialog
      detail={detail(overrides)} loading={false} error={null}
      onClose={() => undefined} onAction={() => undefined} onForceWithdraw={() => undefined}
      onAdjustTemperature={() => undefined}
      onToggleTestAccount={withHandler ? () => undefined : undefined}
    />,
  );

describe('테스트 계정 상세 UI', () => {
  it('지정되지 않은 회원에게는 지정 버튼을 보여준다', () => {
    const markup = dialog();
    expect(markup).toContain('>지정</button>');
    expect(markup).not.toContain('>해제</button>');
  });

  it('이미 지정된 회원에게는 해제 버튼을 보여준다', () => {
    const markup = dialog({ testAccount: true });
    expect(markup).toContain('>해제</button>');
    expect(markup).toContain('지정됨');
  });

  /**
   * 서버의 허용 목록(ACTIVE·PROFILE_REQUIRED)과 같아야 한다. 어긋나면 눌러도 409만 돌아온다.
   */
  it('제재·탈퇴 상태에는 지정 구역을 노출하지 않는다', () => {
    for (const status of ['SUSPENDED', 'BANNED', 'WITHDRAWN', 'DELETED'] as const) {
      expect(dialog({ status })).not.toContain('GPS 반경');
    }
    expect(dialog({ status: 'PROFILE_REQUIRED' })).toContain('GPS 반경');
  });
});

describe('테스트 계정 변경 세션', () => {
  it('성공하면 상세와 목록의 표시를 함께 갱신한다', async () => {
    const updateTestAccount = vi.fn().mockResolvedValue(detail({ testAccount: true }));
    let state!: AdminMembersState;
    const session = createAdminMembersSession({
      list: vi.fn().mockResolvedValue(page()),
      detail: vi.fn().mockResolvedValue(detail()),
      act: vi.fn(), forceWithdraw: vi.fn(), adjustMannerTemperature: vi.fn(),
      updateTestAccount,
    }, (next) => { state = next; });
    await session.load();
    await session.openDetail(1);
    await session.updateTestAccount(true);

    expect(updateTestAccount).toHaveBeenCalledWith(
      1, { enabled: true, reasonNote: null }, expect.anything());
    expect(state.detail?.testAccount).toBe(true);
    expect(state.items[0].testAccount).toBe(true);
    expect(state.submitting).toBe(false);
  });

  it('실패하면 기존 표시를 유지하고 오류만 남긴다', async () => {
    let state!: AdminMembersState;
    const session = createAdminMembersSession({
      list: vi.fn().mockResolvedValue(page()),
      detail: vi.fn().mockResolvedValue(detail()),
      act: vi.fn(), forceWithdraw: vi.fn(), adjustMannerTemperature: vi.fn(),
      updateTestAccount: vi.fn().mockRejectedValue(new Error('conflict')),
    }, (next) => { state = next; });
    await session.load();
    await session.openDetail(1);
    await session.updateTestAccount(true);

    expect(state.detail?.testAccount).toBe(false);
    expect(state.items[0].testAccount).toBe(false);
    expect(state.actionError).not.toBeNull();
    expect(state.submitting).toBe(false);
  });

  it('이중 제출은 한 번만 호출한다', async () => {
    const updateTestAccount = vi.fn().mockResolvedValue(detail({ testAccount: true }));
    const session = createAdminMembersSession({
      list: vi.fn().mockResolvedValue(page()),
      detail: vi.fn().mockResolvedValue(detail()),
      act: vi.fn(), forceWithdraw: vi.fn(), adjustMannerTemperature: vi.fn(),
      updateTestAccount,
    }, () => undefined);
    await session.load();
    await session.openDetail(1);
    await Promise.all([session.updateTestAccount(true), session.updateTestAccount(true)]);

    expect(updateTestAccount).toHaveBeenCalledTimes(1);
  });
});
