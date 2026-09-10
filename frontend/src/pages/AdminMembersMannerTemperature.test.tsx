import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { AdminMemberDetailDialog, AdminMemberMannerTemperatureDialog } from './AdminMembersPage';
import type { AdminMemberDetail, AdminMemberMannerTemperatureRequest } from '../api/adminMembers';
import { createAdminMembersSession, type AdminMembersState } from '../hooks/useAdminMembers';

const detail = (
  status: AdminMemberDetail['status'] = 'ACTIVE', mannerTemperature = 26.5, id = 1,
): AdminMemberDetail => ({
  memberId: id, nickname: `회원${id}`, profileImageUrl: null, role: 'USER', status,
  penaltyScore: 0, mannerTemperature, suspendedAt: null, suspendedUntil: null,
  createdAt: '2026-09-01T09:00:00+09:00', lastLoginAt: null, recentValidReportCount: 0,
  safetyReviewRequired: false, reports: [], actions: [], mannerTemperatureAdjustments: [],
});

/**
 * 버튼 라벨과 이력 구역 제목이 모두 "매너온도 조정"으로 시작한다. 문자열 포함만 보면
 * 버튼이 숨어 있어도 제목 때문에 통과하므로 닫는 태그까지 붙여 버튼만 집는다.
 */
const TEMPERATURE_BUTTON = '>매너온도 조정</button>';

const detailDialog = (status: AdminMemberDetail['status']) => renderToStaticMarkup(
  <AdminMemberDetailDialog
    detail={detail(status)} loading={false} error={null}
    onClose={() => undefined} onAction={() => undefined} onForceWithdraw={() => undefined}
    onAdjustTemperature={() => undefined}
  />,
);

const request = (
  overrides: Partial<AdminMemberMannerTemperatureRequest> = {},
): AdminMemberMannerTemperatureRequest => ({
  targetTemperature: 36.5, expectedTemperature: 26.5,
  reasonCode: 'ADMIN_CORRECTION', reasonNote: null, ...overrides,
});

const temperatureDialog = (overrides: Partial<AdminMemberMannerTemperatureRequest> = {}) =>
  renderToStaticMarkup(
    <AdminMemberMannerTemperatureDialog
      detail={detail()} initial={request(overrides)} submitting={false} error={null}
      onClose={() => undefined} onSubmit={() => undefined}
    />,
  );

const page = (items: AdminMemberDetail[] = [detail()]) => ({
  items, pagination: { size: 20, hasNext: false, nextCursor: null },
});

describe('관리자 매너온도 조정 UI', () => {
  /**
   * 상태 판정은 허용 목록으로 한다. `status !== 'WITHDRAWN'` 같은 부정 조건으로 쓰면 새
   * 상태가 추가될 때마다 조용히 새어 나간다. 탈퇴 회원에게 경고 버튼이 노출되던 결함이
   * 그 형태였다.
   */
  it.each(['ACTIVE', 'PROFILE_REQUIRED', 'SUSPENDED', 'BANNED'] as const)(
    '%s 회원 상세에는 매너온도 조정 버튼이 있다',
    (status) => {
      expect(detailDialog(status)).toContain(TEMPERATURE_BUTTON);
    },
  );

  /** 익명화된 회원은 다시 매칭에 들어올 일이 없고 온도를 조정할 대상 자체가 없다. */
  it.each(['WITHDRAWN', 'DELETED'] as const)(
    '%s 회원에게는 매너온도 조정 버튼을 노출하지 않는다',
    (status) => {
      expect(detailDialog(status)).not.toContain(TEMPERATURE_BUTTON);
    },
  );

  it('상세에 매너온도 조정 이력 구역이 있다', () => {
    expect(detailDialog('ACTIVE')).toContain('매너온도 조정 이력');
  });

  it('조정 이력이 있으면 변경 전후 값을 보여준다', () => {
    const markup = renderToStaticMarkup(
      <AdminMemberDetailDialog
        detail={{
          ...detail('ACTIVE'),
          mannerTemperatureAdjustments: [{
            actionId: 7, beforeTemperature: 26.5, afterTemperature: 36.5,
            reasonCode: 'ADMIN_CORRECTION', reasonNote: null,
            createdAt: '2026-09-10T09:00:00+09:00',
          }],
        }}
        loading={false} error={null} onClose={() => undefined} onAction={() => undefined}
        onForceWithdraw={() => undefined} onAdjustTemperature={() => undefined}
      />,
    );
    expect(markup).toContain('26.50');
    expect(markup).toContain('36.50');
  });

  it('현재 온도와 차감량이 아니라 조정 후 값임을 안내한다', () => {
    const markup = temperatureDialog();
    expect(markup).toContain('26.50');
    expect(markup).toContain('차감량이 아니라 조정 후 값입니다');
  });

  /**
   * 허용 범위는 backend MannerTemperaturePolicy와 같아야 한다. 화면이 넓게 열어 두면
   * 관리자가 입력한 뒤에야 400을 받는다.
   */
  it('허용 범위를 벗어난 값이면 경고하고 확인 버튼을 막는다', () => {
    const markup = temperatureDialog({ targetTemperature: 42.5 });
    expect(markup).toContain('허용 범위를 벗어난 값입니다');
    // Tailwind의 disabled: 클래스 때문에 문자열 포함 검사는 거짓 양성이다. 속성으로 본다.
    expect(markup).toContain('disabled=""');
  });

  it('범위 안의 값이면 확인 버튼이 열린다', () => {
    expect(temperatureDialog({ targetTemperature: 36.5 })).not.toContain('disabled=""');
  });
});

describe('매너온도 조정 세션', () => {
  it('조정에 성공하면 상세와 목록을 함께 갱신한다', async () => {
    const adjusted = detail('ACTIVE', 36.5);
    const adjustMannerTemperature = vi.fn().mockResolvedValue(adjusted);
    let state!: AdminMembersState;
    const session = createAdminMembersSession({
      list: vi.fn().mockResolvedValue(page()),
      detail: vi.fn().mockResolvedValue(detail()),
      act: vi.fn(),
      forceWithdraw: vi.fn(),
      adjustMannerTemperature,
    }, (next) => { state = next; });
    await session.load();
    await session.openDetail(1);

    session.requestTemperature(request());
    await session.submitTemperature();

    expect(adjustMannerTemperature).toHaveBeenCalledOnce();
    expect(adjustMannerTemperature.mock.calls[0][1]).toEqual(request());
    expect(state.detail?.mannerTemperature).toBe(36.5);
    expect(state.items[0].mannerTemperature).toBe(36.5);
    expect(state.pendingTemperature).toBeNull();
  });

  it('이중 제출은 한 번만 호출한다', async () => {
    const adjustMannerTemperature = vi.fn().mockResolvedValue(detail('ACTIVE', 36.5));
    const session = createAdminMembersSession({
      list: vi.fn().mockResolvedValue(page()),
      detail: vi.fn().mockResolvedValue(detail()),
      act: vi.fn(),
      forceWithdraw: vi.fn(),
      adjustMannerTemperature,
    }, () => undefined);
    await session.load();
    await session.openDetail(1);

    session.requestTemperature(request());
    const one = session.submitTemperature();
    const two = session.submitTemperature();

    expect(one).toBe(two);
    await one;
    expect(adjustMannerTemperature).toHaveBeenCalledOnce();
  });

  /** 실패해도 기존 온도를 유지하고 확인 요청을 남겨 관리자가 다시 시도할 수 있어야 한다. */
  it('실패하면 기존 온도와 확인 요청을 유지한다', async () => {
    let state!: AdminMembersState;
    const session = createAdminMembersSession({
      list: vi.fn().mockResolvedValue(page()),
      detail: vi.fn().mockResolvedValue(detail()),
      act: vi.fn(),
      forceWithdraw: vi.fn(),
      adjustMannerTemperature: vi.fn().mockRejectedValue(new Error('conflict')),
    }, (next) => { state = next; });
    await session.load();
    await session.openDetail(1);

    session.requestTemperature(request());
    await session.submitTemperature();

    expect(state.detail?.mannerTemperature).toBe(26.5);
    expect(state.pendingTemperature).not.toBeNull();
    expect(state.actionError).toBeInstanceOf(Error);
  });
});
