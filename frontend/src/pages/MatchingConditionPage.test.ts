import { isValidElement, type ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { ApiClientError } from '../api/apiClient';
import type { CurrentMatchGroup, MatchingRestriction } from '../api/matching';
import {
  checkInNavigationTarget,
  consumeMatchRoomNotice,
  countdownSeconds,
  handleCancelCheckinDialogKeyDown,
  MatchBody,
  PreferenceGuideDialog,
  readMatchRoomNotice,
  resolveFestivalId,
  submitPoolEntry,
} from './MatchingConditionPage';

describe('match room 종료 안내 소비', () => {
  it('종료 안내를 한 번 읽고 festivalId 등 다른 route state는 보존한다', () => {
    const locationState = {
      festivalId: 144,
      matchRoomNotice: '참여 취소가 완료되어 그룹이 종료됐어요.',
    };

    expect(readMatchRoomNotice(locationState)).toBe('참여 취소가 완료되어 그룹이 종료됐어요.');
    expect(consumeMatchRoomNotice(locationState)).toEqual({ festivalId: 144 });
  });

  it('종료 안내만 있으면 history state를 비우고 잘못된 값은 표시하지 않는다', () => {
    expect(consumeMatchRoomNotice({ matchRoomNotice: '종료 안내' })).toBeNull();
    expect(readMatchRoomNotice({ matchRoomNotice: 123 })).toBeNull();
    expect(readMatchRoomNotice(null)).toBeNull();
  });
});

describe('resolveFestivalId', () => {
  it('location state 값을 개발 환경 fallback보다 우선한다', () => {
    expect(resolveFestivalId({ festivalId: 7 }, 8, true, '9')).toBe(7);
  });

  it('terminal pool festivalId를 개발 환경 fallback보다 우선한다', () => {
    expect(resolveFestivalId(null, 8, true, '9')).toBe(8);
  });

  it('location과 terminal pool이 없을 때 개발 환경에서만 VITE_DEV_FESTIVAL_ID를 사용한다', () => {
    expect(resolveFestivalId(null, null, true, '9')).toBe(9);
    expect(resolveFestivalId(null, null, false, '9')).toBeNull();
  });

  it('유효한 festivalId가 없으면 null을 반환하여 신청을 막는다', () => {
    expect(resolveFestivalId(undefined, null, true, '')).toBeNull();
    expect(resolveFestivalId({ festivalId: 0 }, null, false, undefined)).toBeNull();
  });
});

describe('checkInNavigationTarget', () => {
  it('festivalId를 알고 있으면 CheckInPage로 그 축제 id를 state에 실어 보낸다', () => {
    expect(checkInNavigationTarget(144)).toEqual({ to: '/check-in', state: { festivalId: 144 } });
  });

  it('festivalId를 모르면 체크인 화면을 거치지 않고 축제·관광 탐색으로 바로 보낸다', () => {
    // 어느 축제인지 모르면 CheckInPage가 할 수 있는 일이 없어 안내 화면이 한 단계 낭비였다.
    expect(checkInNavigationTarget(null)).toEqual({ to: '/spots' });
  });
});

const restriction = (active = false): MatchingRestriction => ({
  penaltyScore: 0,
  serverNow: '2026-07-27T12:00:00+09:00',
  cooldown: {
    active,
    reason: active ? 'REJECTED_PROPOSAL' : null,
    startsAt: active ? '2026-07-27T12:00:00' : null,
    expiresAt: active ? '2026-07-27T12:05:00' : null,
    remainingSeconds: active ? 300 : 0,
  },
  completionLock: {
    active: false,
    reason: null,
    groupId: null,
    startsAt: null,
    expiresAt: null,
    remainingSeconds: 0,
  },
});

describe('countdown 경계', () => {
  const deadline = '2026-08-10T13:00:00+09:00';
  const deadlineMs = new Date(deadline).getTime();

  it('마감 직전의 일부 초를 조기 만료시키지 않는다', () => {
    expect(countdownSeconds(deadline, deadlineMs - 1)).toBe(1);
  });

  it('정확한 마감과 마감 이후는 0이다', () => {
    expect(countdownSeconds(deadline, deadlineMs)).toBe(0);
    expect(countdownSeconds(deadline, deadlineMs + 1)).toBe(0);
  });
});

const completionLock = (active = true): MatchingRestriction['completionLock'] => ({
  active,
  reason: 'MATCH_VALIDITY',
  groupId: 24,
  startsAt: '2026-08-10T12:00:00+09:00',
  expiresAt: '2026-08-10T13:00:00+09:00',
  remainingSeconds: active ? 1_200 : 0,
});

const group: CurrentMatchGroup = {
  groupId: 30,
  festivalId: 2,
  status: 'CONFIRMED',
  confirmedMemberCount: 2,
  currentMemberCount: 2,
  confirmedAt: '2026-07-27T12:00:20',
  arrivalDeadlineAt: '2026-07-27T12:30:20',
  festival: {
    festivalId: 2,
    title: '테스트 축제',
    address: '강원특별자치도 춘천시',
    eventStartDate: '2026-07-27',
    eventEndDate: '2026-07-29',
  },
  members: [
    { memberId: 1, nickname: 'member-a', profileImageUrl: null, status: 'JOINED', arrivalMinutes: null, arrivalTimeSelectedAt: null },
    { memberId: 2, nickname: 'member-b', profileImageUrl: null, status: 'JOINED', arrivalMinutes: null, arrivalTimeSelectedAt: null },
  ],
};

function bodyProps(overrides: Partial<Parameters<typeof MatchBody>[0]> = {}): Parameters<typeof MatchBody>[0] {
  return {
    status: 'CANCELLED',
    error: null,
    isRetryFormOpen: false,
    group: null,
    groupSize: 3,
    allowMinimum: false,
    hasFestival: true,
    currentCheckin: null,
    festivalId: null,
    canApply: true,
    isSubmitting: false,
    searchRemaining: 0,
    responseRemaining: 0,
    cooldownRemaining: 0,
    cooldownActive: false,
    terminationReason: null,
    completionLock: null,
    completionRemaining: 0,
    setGroupSize: vi.fn(),
    setAllowMinimum: vi.fn(),
    onStart: vi.fn(),
    onAccept: vi.fn(),
    onDecline: vi.fn(),
    onStartWithCurrent: vi.fn(),
    onCancelProposal: vi.fn(),
    onCancelSearch: vi.fn(),
    onRetry: vi.fn(),
    onErrorRetry: vi.fn(),
    onGoCheckIn: vi.fn(),
    onEnterRoom: vi.fn(),
    onRequestCancelCheckin: vi.fn(),
    ...overrides,
  };
}

function renderNode(node: ReactNode): ReactNode {
  if (Array.isArray(node)) return node.map(renderNode);
  if (!isValidElement(node)) return node;
  const element = node;
  if (typeof element.type === 'function') {
    const Component = element.type as (props: typeof element.props) => ReactNode;
    return renderNode(Component(element.props));
  }
  return {
    ...element,
    props: {
      ...element.props,
      children: renderNode(element.props.children),
    },
  };
}

function elements(node: ReactNode): Array<{ type: unknown; props: Record<string, unknown> }> {
  if (Array.isArray(node)) return node.flatMap(elements);
  if (!isValidElement(node)) return [];
  return [node as never, ...elements(node.props.children)];
}

function text(node: ReactNode): string {
  if (Array.isArray(node)) return node.map(text).join('');
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (!isValidElement(node)) return '';
  return text(node.props.children);
}

describe('IDLE 상태의 현재 체크인 노출·취소', () => {
  it('체크인이 없으면 체크인하기 버튼만 표시하고 취소 버튼은 표시하지 않는다', () => {
    const tree = renderNode(MatchBody(bodyProps({ status: 'IDLE', hasFestival: false, currentCheckin: null })));
    expect(text(tree)).toContain('체크인하기');
    expect(text(tree)).not.toContain('체크인 취소');
  });

  it('체크인이 있으면 어느 축제인지·만료 시각과 취소 버튼을 표시하고 체크인하기는 표시하지 않는다', () => {
    const tree = renderNode(MatchBody(bodyProps({
      status: 'IDLE',
      hasFestival: true,
      currentCheckin: {
        checkinId: 1,
        festivalId: 10,
        festivalName: '춘천 마임축제',
        checkedInAt: '2026-07-27T10:00:00+09:00',
        expiresAt: '2026-07-27T11:00:00+09:00',
      },
    })));
    expect(text(tree)).toContain('춘천 마임축제에 체크인됨');
    expect(text(tree)).toContain('체크인 취소');
    expect(text(tree)).not.toContain('먼저 체크인을 해주세요');
  });

  it('체크인 취소 버튼 클릭은 onRequestCancelCheckin을 호출한다', () => {
    const onRequestCancelCheckin = vi.fn();
    const tree = renderNode(MatchBody(bodyProps({
      status: 'IDLE',
      hasFestival: true,
      currentCheckin: {
        checkinId: 1,
        festivalId: 10,
        festivalName: '춘천 마임축제',
        checkedInAt: '2026-07-27T10:00:00+09:00',
        expiresAt: '2026-07-27T11:00:00+09:00',
      },
      onRequestCancelCheckin,
    })));
    const cancelButton = elements(tree).find(
      (element) => element.type === 'button' && text(element as never) === '체크인 취소',
    );
    expect(cancelButton).toBeDefined();
    (cancelButton?.props.onClick as () => void)();
    expect(onRequestCancelCheckin).toHaveBeenCalledOnce();
  });
});

describe('handleCancelCheckinDialogKeyDown', () => {
  it('Escape는 제출 중이 아닐 때만 닫는다', () => {
    const preventDefault = vi.fn();
    const close = vi.fn();
    const first = { focus: vi.fn() } as unknown as HTMLElement;
    const last = { focus: vi.fn() } as unknown as HTMLElement;

    handleCancelCheckinDialogKeyDown({ key: 'Escape', shiftKey: false, preventDefault }, [first, last], first, false, close);
    expect(close).toHaveBeenCalledOnce();
    handleCancelCheckinDialogKeyDown({ key: 'Escape', shiftKey: false, preventDefault }, [first, last], first, true, close);
    expect(close).toHaveBeenCalledOnce();
  });

  it('Tab은 focusable 목록 안에서 순환한다', () => {
    const preventDefault = vi.fn();
    const close = vi.fn();
    const first = { focus: vi.fn() } as unknown as HTMLElement;
    const last = { focus: vi.fn() } as unknown as HTMLElement;

    handleCancelCheckinDialogKeyDown({ key: 'Tab', shiftKey: false, preventDefault }, [first, last], last, false, close);
    expect(first.focus).toHaveBeenCalledOnce();
    handleCancelCheckinDialogKeyDown({ key: 'Tab', shiftKey: true, preventDefault }, [first, last], first, false, close);
    expect(last.focus).toHaveBeenCalledOnce();
  });
});

describe('terminal retry form', () => {
  it.each(['CANCELLED', 'EXPIRED'] as const)('%s retry 모드에서 신청 form을 표시한다', (status) => {
    const tree = renderNode(MatchBody(bodyProps({ status, isRetryFormOpen: true })));
    expect(text(tree)).toContain('희망 인원');
    expect(text(tree)).toContain('자동 매칭 신청');
    expect(text(tree)).not.toContain('매칭이 종료됐어요');
  });

  it('terminal 카드의 다시 신청하기 클릭 handler를 호출한다', () => {
    const onRetry = vi.fn();
    const tree = renderNode(MatchBody(bodyProps({ onRetry })));
    const retryButton = elements(tree).find(
      (element) => element.type === 'button' && text(element as never) === '다시 신청하기',
    );
    expect(retryButton).toBeDefined();
    (retryButton?.props.onClick as () => void)();
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('옵션 변경과 신청 handler를 선택값 기준으로 호출할 수 있다', () => {
    const setGroupSize = vi.fn();
    const setAllowMinimum = vi.fn();
    const onStart = vi.fn();
    const tree = renderNode(MatchBody(bodyProps({
      isRetryFormOpen: true,
      groupSize: 3,
      allowMinimum: false,
      setGroupSize,
      setAllowMinimum,
      onStart,
    })));
    const all = elements(tree);
    (all.find((element) => text(element as never) === '4명')?.props.onClick as () => void)();
    (all.find((element) => element.props.role === 'switch')?.props.onClick as () => void)();
    (all.find((element) => text(element as never) === '자동 매칭 신청')?.props.onClick as () => void)();
    expect(setGroupSize).toHaveBeenCalledWith(4);
    expect(setAllowMinimum).toHaveBeenCalledWith(true);
    expect(onStart).toHaveBeenCalledOnce();
  });

  it('terminal pool festivalId와 변경한 옵션으로 enterPool을 호출한다', async () => {
    const enterPool = vi.fn().mockResolvedValue(true);
    const festivalId = resolveFestivalId(null, 8, false, undefined);
    await submitPoolEntry(enterPool, festivalId, 4, true);
    expect(enterPool).toHaveBeenCalledWith(8, 4, true);
  });

  it('festivalId가 없으면 pool 제출을 차단한다', () => {
    const enterPool = vi.fn();
    expect(submitPoolEntry(enterPool, null, 3, false)).toBeNull();
    expect(enterPool).not.toHaveBeenCalled();
  });

  it('POST 실패 후 retry form의 선택값을 그대로 렌더링한다', () => {
    const tree = renderNode(MatchBody(bodyProps({
      status: 'EXPIRED',
      isRetryFormOpen: true,
      groupSize: 4,
      allowMinimum: true,
    })));
    const all = elements(tree);
    const selectedSize = all.find(
      (element) => element.type === 'button' && text(element as never) === '4명',
    );
    const minimumSwitch = all.find((element) => element.props.role === 'switch');
    expect(selectedSize?.props.className).toContain('border-coral');
    expect(minimumSwitch?.props['aria-checked']).toBe(true);
  });

  it('MATCHED에서는 retry form과 재신청 UI를 표시하지 않는다', () => {
    const tree = renderNode(MatchBody(bodyProps({
      status: 'MATCHED',
      isRetryFormOpen: true,
      group,
    })));
    expect(text(tree)).toContain('매칭이 확정됐어요');
    expect(text(tree)).not.toContain('자동 매칭 신청');
    expect(text(tree)).not.toContain('다시 신청하기');
  });

  it('MATCHED는 자동 이동하지 않고 상태방 버튼 클릭으로만 이동 handler를 호출한다', () => {
    const onEnterRoom = vi.fn();
    const tree = renderNode(MatchBody(bodyProps({
      status: 'MATCHED',
      group,
      onEnterRoom,
    })));
    expect(onEnterRoom).not.toHaveBeenCalled();
    const enterButton = elements(tree).find(
      (element) => element.type === 'button' && text(element as never) === '상태방 들어가기',
    );
    expect(enterButton).toBeDefined();
    (enterButton?.props.onClick as () => void)();
    expect(onEnterRoom).toHaveBeenCalledOnce();
  });

  it('종료 카드는 festivalId가 있으면 코스 보러가기 링크를 함께 보여준다', () => {
    const tree = renderNode(MatchBody(bodyProps({ status: 'EXPIRED', festivalId: 144 })));
    const link = elements(tree).find(
      (element) => text(element as never) === '솔로 코스 보러가기',
    );
    expect(link).toBeDefined();
    expect(link?.props.to).toBe('/solo-course');
    expect(link?.props.state).toEqual({ festivalId: 144 });
  });

  it('festivalId가 없으면 코스 보러가기 링크를 보여주지 않는다', () => {
    const tree = renderNode(MatchBody(bodyProps({ status: 'EXPIRED', festivalId: null })));
    expect(text(tree)).not.toContain('솔로 코스 보러가기');
  });

  it('cooldown 카드에서는 retry button이 비활성화된다', () => {
    const tree = renderNode(MatchBody(bodyProps({
      status: 'COOLDOWN',
      cooldownActive: true,
      cooldownRemaining: restriction(true).cooldown.remainingSeconds,
    })));
    const retryButton = elements(tree).find(
      (element) => element.type === 'button' && text(element as never) === '다시 신청하기',
    );
    expect(retryButton?.props.disabled).toBe(true);
  });

  it.each([
    ['SELF_REJECTED', '매칭 제안을 거절했어요.'],
    ['NON_FAULT_TERMINATED', '이번 매칭을 진행할 수 없어 종료됐어요.'],
    ['SELF_TIMEOUT', '응답 시간이 지나 매칭이 종료됐어요.'],
    ['SYSTEM_TERMINATED', '이번 매칭을 진행할 수 없어요.'],
  ] as const)('%s 종료 문구를 개인정보 없이 표시한다', (terminationReason, message) => {
    const tree = renderNode(MatchBody(bodyProps({ terminationReason })));
    expect(text(tree)).toContain(message);
  });

  it('비귀책 종료는 cooldown을 표시하지 않고 즉시 재신청할 수 있다', () => {
    const tree = renderNode(MatchBody(bodyProps({ terminationReason: 'NON_FAULT_TERMINATED' })));
    const retryButton = elements(tree).find(
      (element) => element.type === 'button' && text(element as never) === '다시 신청하기',
    );
    expect(text(tree)).not.toContain('후 재신청 가능');
    expect(retryButton?.props.disabled).toBe(false);
  });

  it('직접 거절은 REJECT cooldown countdown을 표시한다', () => {
    const tree = renderNode(MatchBody(bodyProps({
      terminationReason: 'SELF_REJECTED',
      cooldownActive: true,
      cooldownRemaining: 30,
    })));
    expect(text(tree)).toContain('0:30 후 재신청 가능');
  });

  it('유효 체크인 오류는 일반 연결 오류 대신 체크인 안내를 표시한다', () => {
    const onGoCheckIn = vi.fn();
    const tree = renderNode(MatchBody(bodyProps({
      status: 'ERROR',
      error: new ApiClientError(
        '해당 축제의 유효한 체크인이 필요합니다.',
        400,
        'MATCHING_INVALID_REQUEST',
        [],
      ),
      onGoCheckIn,
    })));
    expect(text(tree)).toContain('축제 체크인이 필요해요');
    expect(text(tree)).toContain('해당 축제의 유효한 체크인이 필요합니다.');
    expect(text(tree)).not.toContain('연결이 잠시 끊겼어요');
    const checkInButton = elements(tree).find(
      (element) => element.type === 'button' && text(element as never) === '체크인하기',
    );
    (checkInButton?.props.onClick as () => void)();
    expect(onGoCheckIn).toHaveBeenCalledOnce();
  });

  it('활성 만남 장소가 없으면 일반 네트워크 오류가 아닌 준비 안내를 표시한다', () => {
    const tree = renderNode(MatchBody(bodyProps({
      status: 'ERROR',
      error: new ApiClientError(
        '선택한 축제의 만남 장소를 준비하고 있습니다.',
        409,
        'MATCHING_MEETING_POINT_NOT_READY',
        [],
      ),
    })));
    expect(text(tree)).toContain('만남 장소 준비 중이에요');
    expect(text(tree)).toContain('선택한 축제의 만남 장소를 준비하고 있습니다.');
    expect(text(tree)).not.toContain('다시 시도');
  });
});

describe('정상 완료 card', () => {
  it('완료 전용 문구와 종료 시각, countdown을 표시하고 취소 문구를 표시하지 않는다', () => {
    const tree = renderNode(MatchBody(bodyProps({
      status: 'COMPLETED',
      completionLock: completionLock(),
      completionRemaining: 1_200,
    })));

    expect(text(tree)).toContain('만남이 완료됐어요');
    expect(text(tree)).toContain('모든 참여자가 도착했어요.');
    expect(text(tree)).toContain('매칭 유효 종료 시각');
    expect(text(tree)).toContain('20분 후 신청할 수 있어요');
    expect(text(tree)).not.toContain('매칭이 취소됐어요');
    expect(text(tree)).not.toContain('매칭이 종료됐어요');
  });

  it('제한 중 다시 매칭 action을 비활성화한다', () => {
    const tree = renderNode(MatchBody(bodyProps({
      status: 'COMPLETED',
      completionLock: completionLock(),
      completionRemaining: 1,
    })));
    const button = elements(tree).find(
      (element) => element.type === 'button' && text(element as never) === '다시 매칭하기',
    );

    expect(text(tree)).toContain('1분 후 신청할 수 있어요');
    expect(button?.props.disabled).toBe(true);
  });

  it('제한 종료 후 retry action을 활성화한다', () => {
    const onRetry = vi.fn();
    const tree = renderNode(MatchBody(bodyProps({
      status: 'COMPLETED',
      completionLock: completionLock(false),
      completionRemaining: 0,
      onRetry,
    })));
    const button = elements(tree).find(
      (element) => element.type === 'button' && text(element as never) === '다시 매칭하기',
    );

    expect(button?.props.disabled).toBe(false);
    (button?.props.onClick as () => void)();
    expect(onRetry).toHaveBeenCalledOnce();
  });
});

describe('매칭 탐색 취소 버튼', () => {
  it('WAITING 상태에서 SearchingCard를 렌더링한다', () => {
    const node = MatchBody(bodyProps({ status: 'WAITING' }));
    expect(isValidElement(node)).toBe(true);
  });

  it('LOCKED 상태에서도 SearchingCard를 렌더링한다', () => {
    const node = MatchBody(bodyProps({ status: 'LOCKED' }));
    expect(isValidElement(node)).toBe(true);
  });

  it('WAITING/LOCKED가 아닌 IDLE에서는 SearchingCard를 렌더링하지 않는다', () => {
    const tree = renderNode(MatchBody(bodyProps({ status: 'IDLE' })));
    expect(text(tree)).toContain('희망 인원');
    expect(text(tree)).not.toContain('주변 여행자를 찾고 있어요');
  });
});

describe('매칭 신청 전 취향 안내', () => {
  it('취향 미입력자에게 정확도 안내와 건너뛰기를 함께 제공한다', () => {
    const tree = renderNode(PreferenceGuideDialog({
      state: 'NONE',
      onSkip: vi.fn(),
      onGoInput: vi.fn(),
      onDismiss: vi.fn(),
    }));
    expect(text(tree)).toContain('취향을 입력하면 매칭 정확도가 올라가요');
    expect(text(tree)).toContain('건너뛰고 신청');
    expect(text(tree)).toContain('지금 입력하기');
  });

  it('분석 실패는 재저장 경로를 안내한다', () => {
    const tree = renderNode(PreferenceGuideDialog({
      state: 'FAILED',
      onSkip: vi.fn(),
      onGoInput: vi.fn(),
      onDismiss: vi.fn(),
    }));
    expect(text(tree)).toContain('취향 분석에 실패했어요');
    expect(text(tree)).toContain('다시 저장하기');
    expect(text(tree)).toContain('건너뛰고 신청');
  });

  it('건너뛰기와 입력하기 handler를 각각 호출한다', () => {
    const onSkip = vi.fn();
    const onGoInput = vi.fn();
    const tree = renderNode(PreferenceGuideDialog({
      state: 'NONE',
      onSkip,
      onGoInput,
      onDismiss: vi.fn(),
    }));
    const all = elements(tree);
    (all.find((element) => text(element as never) === '건너뛰고 신청')?.props.onClick as () => void)();
    (all.find((element) => text(element as never) === '지금 입력하기')?.props.onClick as () => void)();
    expect(onSkip).toHaveBeenCalledOnce();
    expect(onGoInput).toHaveBeenCalledOnce();
  });

  it('안내 대상이 아닌 상태에서는 창을 그리지 않는다', () => {
    for (const state of ['COMPLETED', 'ANALYZING', 'LOADING', 'UNAVAILABLE'] as const) {
      expect(PreferenceGuideDialog({
        state,
        onSkip: vi.fn(),
        onGoInput: vi.fn(),
        onDismiss: vi.fn(),
      })).toBeNull();
    }
  });

  it('취향 상태와 무관하게 자동 매칭 신청 버튼을 비활성화하지 않는다', () => {
    // 취향 미입력자의 매칭 신청을 막지 않는다는 결정을 회귀로 고정한다.
    const tree = renderNode(MatchBody(bodyProps({ status: 'IDLE', canApply: true })));
    const applyButton = elements(tree).find(
      (element) => element.type === 'button' && text(element as never) === '자동 매칭 신청',
    );
    expect(applyButton).toBeDefined();
    expect(applyButton?.props.disabled).toBe(false);
  });
});
