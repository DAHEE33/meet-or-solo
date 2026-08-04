import { isValidElement, type ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { CurrentMatchGroup } from '../api/matching';
import type { MatchRoomState } from '../hooks/useMatchRoom';
import {
  ArrivalChangeSnackbar,
  formatRemainingTime,
  formatFestivalPeriod,
  getEstimatedArrivalAt,
  MatchRoomContent,
  matchRoomRedirectPath,
  matchEventText,
  memberArrivalText,
  CANCELLATION_OPTIONS,
} from './MatchRoomPage';

const group = (status: CurrentMatchGroup['status'] = 'CONFIRMED'): CurrentMatchGroup => ({
  groupId: 30,
  festivalId: 2,
  status,
    confirmedMemberCount: 2,
    currentMemberCount: 2,
  confirmedAt: '2026-07-27T12:00:20+09:00',
  arrivalDeadlineAt: '2026-07-27T12:30:20+09:00',
  currentMemberId: 1,
  festival: {
    festivalId: 2,
    title: '춘천 여름 축제',
    address: '강원특별자치도 춘천시 중앙로',
    eventStartDate: '2026-07-27',
    eventEndDate: '2026-07-29',
  },
  members: [
    { memberId: 1, nickname: '여행자A', profileImageUrl: null, status: 'JOINED', arrivalMinutes: null, arrivalTimeSelectedAt: null },
    {
      memberId: 2,
      nickname: '여행자B',
      profileImageUrl: 'https://example.com/profile.png',
      status: 'ARRIVED',
      arrivalMinutes: 10,
      arrivalTimeSelectedAt: '2026-07-27T12:05:00+09:00',
      arrivedAt: '2026-07-27T12:12:00+09:00',
    },
  ],
});

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
    props: { ...element.props, children: renderNode(element.props.children) },
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

describe('MatchRoomContent', () => {
  it('deadline 전 active 회원에게 세 구조화 취소 사유만 제공하고 자유 입력은 없다', () => {
    const tree = renderNode(MatchRoomContent({
      state: {
        status: 'READY', group: group(), events: [], error: null, eventsError: null,
        actionError: null, isSubmitting: false,
      },
      onRetry: vi.fn(),
      onSelectArrivalTime: vi.fn(),
      onCancel: vi.fn(),
      nowEpochMs: Date.parse('2026-07-27T12:10:00+09:00'),
    }));
    const content = text(tree);
    expect(content).toContain('못 갈 것 같아요');
    CANCELLATION_OPTIONS.forEach((option) => expect(content).toContain(option.label));
    expect(elements(tree).some((element) =>
      element.type === 'input' || element.type === 'textarea')).toBe(false);
  });

  it('상대 도착 시간 변경 snackbar를 하단 navigation 위 접근 가능한 status로 표시한다', () => {
    const tree = renderNode(ArrivalChangeSnackbar({
      message: '테스트님이 도착 시간을 변경하였어요.',
    }));
    expect(text(tree)).toContain('테스트님이 도착 시간을 변경하였어요.');
    const snackbar = elements(tree).find((element) => element.props.role === 'status');
    expect(snackbar?.props['aria-live']).toBe('polite');
    expect(snackbar?.props.className).toContain('bottom-24');
    expect(ArrivalChangeSnackbar({ message: null })).toBeNull();
  });

  it('current group null 상태만 /matching replace 이동 대상으로 판정한다', () => {
    expect(matchRoomRedirectPath('EMPTY')).toBe('/matching');
    expect(matchRoomRedirectPath('LOADING')).toBeNull();
    expect(matchRoomRedirectPath('READY')).toBeNull();
    expect(matchRoomRedirectPath('ERROR')).toBeNull();
  });

  it('loading 상태를 명확히 표시한다', () => {
    const tree = renderNode(MatchRoomContent({
      state: {
        status: 'LOADING', group: null, events: [], error: null, eventsError: null,
        actionError: null, isSubmitting: false,
      },
      onRetry: vi.fn(),
      onSelectArrivalTime: vi.fn(),
    }));
    expect(text(tree)).toContain('매칭방 정보를 불러오고 있어요');
    expect(elements(tree).some((element) => element.props.role === 'status')).toBe(true);
  });

  it.each([
    ['CONFIRMED', '만남 준비 중'],
    ['IN_PROGRESS', '미팅 진행 중'],
  ] as const)('%s group, 축제와 멤버 공개 정보를 렌더링한다', (status, statusText) => {
    const tree = renderNode(MatchRoomContent({
      state: {
        status: 'READY', group: group(status), events: [], error: null, eventsError: null,
        actionError: null, isSubmitting: false,
      },
      onRetry: vi.fn(),
      onSelectArrivalTime: vi.fn(),
    }));
    const content = text(tree);
    expect(content).toContain('매칭이 확정됐어요');
    expect(content).toContain(statusText);
    expect(content).toContain('2명');
    expect(content).toContain('춘천 여름 축제');
    expect(content).toContain('강원특별자치도 춘천시 중앙로');
    expect(content).toContain('2026-07-27 ~ 2026-07-29');
    expect(content).toContain('여행자A');
    expect(content).toContain('도착 시간 미정');
    expect(content).toContain('여행자B');
    expect(content).toContain('도착 완료');
    expect(content).not.toContain('메시지');
    expect(content).not.toContain('전송');
    expect(elements(tree).some((element) => element.type === 'input' || element.type === 'textarea')).toBe(false);
  });

  it('API 오류에서 안내와 재시도 UI를 제공한다', () => {
    const onRetry = vi.fn();
    const state: MatchRoomState = {
      status: 'ERROR',
      group: null,
      events: [],
      error: new Error('network'),
      eventsError: null,
      actionError: null,
      isSubmitting: false,
    };
    const tree = renderNode(MatchRoomContent({ state, onRetry, onSelectArrivalTime: vi.fn() }));
    expect(text(tree)).toContain('매칭방 정보를 불러오지 못했어요');
    const retry = elements(tree).find((element) => text(element as never) === '다시 시도');
    (retry?.props.onClick as () => void)();
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('MATCH_CONFIRMED와 도착 상태 기록을 결정적 순서와 KST 시각으로 렌더링한다', () => {
    const state: MatchRoomState = {
      status: 'READY',
      group: { ...group(), currentMemberId: 1 },
      events: [
        {
          eventId: 1,
          type: 'MATCH_CONFIRMED',
          occurredAt: '2026-07-30T00:00:00Z',
          actor: null,
          arrivalMinutes: null,
        },
        {
          eventId: 2,
          type: 'ARRIVAL_TIME_SELECTED',
          occurredAt: '2026-07-30T00:01:00Z',
          actor: { memberId: 1, nickname: '여행자A' },
          arrivalMinutes: 10,
        },
        {
          eventId: 3,
          type: 'MEMBER_ARRIVED',
          occurredAt: '2026-07-30T00:02:00Z',
          actor: { memberId: 2, nickname: '여행자B' },
          arrivalMinutes: null,
        },
      ],
      error: null,
      eventsError: null,
      actionError: null,
      isSubmitting: false,
    };

    const tree = renderNode(MatchRoomContent({
      state,
      onRetry: vi.fn(),
      onSelectArrivalTime: vi.fn(),
    }));
    const content = text(tree);
    expect(content.indexOf('매칭이 확정됐어요.'))
      .toBeLessThan(content.indexOf('내가 10분 후 도착할 예정이에요.'));
    expect(content).toContain('여행자B님이 도착했어요.');
    expect(content).toContain('2026-07-30 09:01:00');
    expect(elements(tree).some((element) => element.type === 'time')).toBe(true);
  });

  it.each([0, 5, 10, 20, 25, 30] as const)(
    '%s분 도착 예정 문구를 안전하게 표시한다',
    (minutes) => {
      expect(matchEventText({
        eventId: minutes + 1,
        type: 'ARRIVAL_TIME_SELECTED',
        occurredAt: '2026-07-30T00:00:00Z',
        actor: { memberId: 2, nickname: '민수' },
        arrivalMinutes: minutes,
      }, 1)).toBe(minutes === 0
        ? '민수님이 곧 도착할 예정이에요.'
        : `민수님이 ${minutes}분 후 도착할 예정이에요.`);
    },
  );

  it('빈 timeline과 events 단독 오류에서 기존 group을 유지하고 재시도한다', () => {
    const onRetry = vi.fn();
    const emptyTree = renderNode(MatchRoomContent({
      state: {
        status: 'READY', group: group(), events: [], error: null, eventsError: null,
        actionError: null, isSubmitting: false,
      },
      onRetry,
      onSelectArrivalTime: vi.fn(),
    }));
    expect(text(emptyTree)).toContain('아직 표시할 상태 기록이 없어요.');

    const errorTree = renderNode(MatchRoomContent({
      state: {
        status: 'READY', group: group(), events: [], error: null,
        eventsError: new Error('events network'), actionError: null, isSubmitting: false,
      },
      onRetry,
      onSelectArrivalTime: vi.fn(),
    }));
    expect(text(errorTree)).toContain('춘천 여름 축제');
    const retry = elements(errorTree).find(
      (element) => element.type === 'button' && text(element as never) === '다시 시도',
    );
    (retry?.props.onClick as () => void)();
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('접근 가능한 선택 panel에서 5/10/20/25분만 선택할 수 있다', () => {
    const onSelectArrivalTime = vi.fn().mockResolvedValue(true);
    const tree = renderNode(MatchRoomContent({
      state: {
        status: 'READY',
        group: group(),
        events: [],
        error: null,
        eventsError: null,
        actionError: null,
        isSubmitting: false,
      },
      onRetry: vi.fn(),
      onSelectArrivalTime,
    }));
    expect(elements(tree).some((element) => element.type === 'details')).toBe(true);
    expect(elements(tree).some((element) => element.type === 'summary')).toBe(true);
    for (const [label, minutes] of [
      ['5분', 5],
      ['10분', 10],
      ['20분', 20],
      ['25분', 25],
    ] as const) {
      const option = elements(tree).find(
        (element) => element.type === 'button' && text(element as never) === label,
      );
      (option?.props.onClick as () => void)();
      expect(onSelectArrivalTime).toHaveBeenCalledWith(minutes);
    }
    expect(text(tree)).not.toContain('지금 도착');
    expect(text(tree)).not.toContain('30분');
  });

  it('남은 전체 시간보다 긴 선택지만 비활성화한다', () => {
    const tree = renderNode(MatchRoomContent({
      state: {
        status: 'READY',
        group: group(),
        events: [],
        error: null,
        eventsError: null,
        actionError: null,
        isSubmitting: false,
      },
      onRetry: vi.fn(),
      onSelectArrivalTime: vi.fn(),
      nowEpochMs: Date.parse('2026-07-27T12:20:20+09:00'),
    }));
    const options = Object.fromEntries(
      elements(tree)
        .filter((element) => element.type === 'button')
        .map((element) => [text(element as never), element.props.disabled]),
    );
    expect(options['5분']).toBe(false);
    expect(options['10분']).toBe(false);
    expect(options['20분']).toBe(true);
    expect(options['25분']).toBe(true);
  });

  it('25분 선택은 예상 도착 시각이 마감과 같을 때까지 허용한다', () => {
    const boundary = renderNode(MatchRoomContent({
      state: {
        status: 'READY', group: group(), events: [], error: null, eventsError: null,
        actionError: null, isSubmitting: false,
      },
      onRetry: vi.fn(),
      onSelectArrivalTime: vi.fn(),
      nowEpochMs: Date.parse('2026-07-27T12:05:20+09:00'),
    }));
    const boundaryOption = elements(boundary).find(
      (element) => element.type === 'button' && text(element as never) === '25분',
    );
    expect(boundaryOption?.props.disabled).toBe(false);

    const exceeded = renderNode(MatchRoomContent({
      state: {
        status: 'READY', group: group(), events: [], error: null, eventsError: null,
        actionError: null, isSubmitting: false,
      },
      onRetry: vi.fn(),
      onSelectArrivalTime: vi.fn(),
      nowEpochMs: Date.parse('2026-07-27T12:05:20.001+09:00'),
    }));
    const exceededOption = elements(exceeded).find(
      (element) => element.type === 'button' && text(element as never) === '25분',
    );
    expect(exceededOption?.props.disabled).toBe(true);
  });

  it('최종 마감과 countdown 및 실제 예상 도착 시각을 표시한다', () => {
    const selected = group();
    selected.members[0] = {
      ...selected.members[0],
      status: 'ARRIVAL_TIME_SELECTED',
      arrivalMinutes: 10,
      arrivalTimeSelectedAt: '2026-07-27T12:05:20+09:00',
    };
    const tree = renderNode(MatchRoomContent({
      state: {
        status: 'READY',
        group: selected,
        events: [],
        error: null,
        eventsError: null,
        actionError: null,
        isSubmitting: false,
      },
      onRetry: vi.fn(),
      onSelectArrivalTime: vi.fn(),
      nowEpochMs: Date.parse('2026-07-27T12:10:20+09:00'),
    }));

    expect(text(tree)).toContain('최종 도착 마감');
    expect(text(tree)).toContain('2026-07-27 12:30:20');
    expect(text(tree)).toContain('전체 남은 시간20:00');
    expect(text(tree)).toContain('선택한 도착 시간10분');
    expect(text(tree)).toContain('예상 도착 시각2026-07-27 12:15:20');
    expect(text(tree)).toContain('예상 도착까지05:00');
  });

  it('개별 예정 시각이 지나도 전체 마감 전에는 안내하고 다시 선택할 수 있다', () => {
    const selected = group();
    selected.members[0] = {
      ...selected.members[0],
      status: 'ARRIVAL_TIME_SELECTED',
      arrivalMinutes: 5,
      arrivalTimeSelectedAt: '2026-07-27T12:05:20+09:00',
    };
    const tree = renderNode(MatchRoomContent({
      state: {
        status: 'READY',
        group: selected,
        events: [],
        error: null,
        eventsError: null,
        actionError: null,
        isSubmitting: false,
      },
      onRetry: vi.fn(),
      onSelectArrivalTime: vi.fn(),
      nowEpochMs: Date.parse('2026-07-27T12:11:20+09:00'),
    }));

    expect(text(tree)).toContain('예정 시간이 지났어요');
    expect(text(tree)).toContain('같은 시간을 다시 선택해도 예정 시각은 연장되지 않아요.');
    expect(text(tree)).toContain('몇 분 후 도착하나요?');
    const selectedOption = elements(tree).find(
      (element) => element.type === 'button' && text(element as never) === '5분 · 현재 선택',
    );
    expect(selectedOption?.props.disabled).toBe(true);
    expect(selectedOption?.props['aria-pressed']).toBe(true);
  });

  it('전체 마감부터 시간 선택과 도착 완료 action을 차단하고 노쇼 처리 대기를 안내한다', () => {
    const tree = renderNode(MatchRoomContent({
      state: {
        status: 'READY',
        group: group(),
        events: [],
        error: null,
        eventsError: null,
        actionError: null,
        isSubmitting: false,
      },
      onRetry: vi.fn(),
      onSelectArrivalTime: vi.fn(),
      onArrive: vi.fn(),
      nowEpochMs: Date.parse('2026-07-27T12:30:20+09:00'),
    }));

    expect(text(tree)).toContain('최종 도착 마감이 지나 예정 시간을 변경할 수 없어요.');
    expect(text(tree)).not.toContain('몇 분 후 도착하나요?');
    expect(text(tree)).not.toContain('축제 만남 장소에 도착했나요?');
    expect(text(tree)).not.toContain('도착했어요');
    expect(text(tree)).toContain('노쇼 처리 결과를 확인하고 있어요.');
    expect(elements(tree).some((element) => element.props.role === 'status')).toBe(true);
  });

  it('제출 중 선택을 막고 실패하면 기존 snapshot과 오류 안내를 유지한다', () => {
    const snapshot = group();
    const tree = renderNode(MatchRoomContent({
      state: {
        status: 'READY',
        group: snapshot,
        events: [],
        error: null,
        eventsError: null,
        actionError: new Error('network'),
        isSubmitting: true,
      },
      onRetry: vi.fn(),
      onSelectArrivalTime: vi.fn(),
    }));
    expect(text(tree)).toContain('저장 중...');
    expect(text(tree)).toContain('도착 예정 시간을 저장하지 못했어요');
    expect(text(tree)).toContain('여행자A');
    const optionButtons = elements(tree).filter(
      (element) => element.type === 'button' && ['5분', '10분', '20분', '25분'].includes(text(element as never)),
    );
    expect(optionButtons.every((button) => button.props.disabled === true)).toBe(true);
  });

  it('참여 상태와 nullable 행사 기간을 안전하게 표시한다', () => {
    expect(memberArrivalText({
      memberId: 1, nickname: 'a', profileImageUrl: null, status: 'JOINED',
      arrivalMinutes: null, arrivalTimeSelectedAt: null,
    })).toBe('도착 시간 미정');
    expect(memberArrivalText({
      memberId: 1, nickname: 'a', profileImageUrl: null, status: 'ARRIVAL_TIME_SELECTED',
      arrivalMinutes: 0, arrivalTimeSelectedAt: '2026-07-27T12:00:00+09:00',
    })).toBe('선택한 도착 시간: 곧 도착');
    expect(memberArrivalText({
      memberId: 1, nickname: 'a', profileImageUrl: null, status: 'ARRIVAL_TIME_SELECTED',
      arrivalMinutes: 10, arrivalTimeSelectedAt: '2026-07-27T12:00:00+09:00',
    })).toBe('선택한 도착 시간: 10분');
    expect(memberArrivalText({
      memberId: 1, nickname: 'a', profileImageUrl: null, status: 'ARRIVED',
      arrivalMinutes: 10, arrivalTimeSelectedAt: '2026-07-27T12:00:00+09:00',
    })).toBe('도착 완료');
    expect(formatFestivalPeriod(null, null)).toBe('행사 기간 정보 없음');
    expect(formatFestivalPeriod('2026-07-27', null)).toBe('2026-07-27');
  });

  it('자유 텍스트 입력을 제공하지 않는다', () => {
    const tree = renderNode(MatchRoomContent({
      state: {
        status: 'READY',
        group: group(),
        events: [],
        error: null,
        eventsError: null,
        actionError: null,
        isSubmitting: false,
      },
      onRetry: vi.fn(),
      onSelectArrivalTime: vi.fn(),
    }));
    expect(elements(tree).some((element) => element.type === 'input' || element.type === 'textarea')).toBe(false);
  });

  it('본인이 JOINED이면 도착 확인 UI를 제공하고 ARRIVED이면 숨긴다', () => {
    const onArrive = vi.fn().mockResolvedValue(true);
    const joinedTree = renderNode(MatchRoomContent({
      state: {
        status: 'READY',
        group: { ...group(), currentMemberId: 1 },
        events: [],
        error: null,
        eventsError: null,
        actionError: null,
        isSubmitting: false,
      },
      onRetry: vi.fn(),
      onSelectArrivalTime: vi.fn(),
      onArrive,
    }));
    expect(text(joinedTree)).toContain('축제 만남 장소에 도착했나요?');
    const confirm = elements(joinedTree).find(
      (element) => element.type === 'button' && text(element as never) === '도착했어요',
    );
    (confirm?.props.onClick as (event: { currentTarget: { closest: () => null } }) => void)({
      currentTarget: { closest: () => null },
    });
    expect(onArrive).toHaveBeenCalledOnce();

    const arrivedTree = renderNode(MatchRoomContent({
      state: {
        status: 'READY',
        group: { ...group(), currentMemberId: 2 },
        events: [],
        error: null,
        eventsError: null,
        actionError: null,
        isSubmitting: false,
      },
      onRetry: vi.fn(),
      onSelectArrivalTime: vi.fn(),
      onArrive,
    }));
    expect(text(arrivedTree)).not.toContain('축제 만남 장소에 도착했나요?');
    expect(text(arrivedTree)).not.toContain('내 도착 예정 시간');
    expect(text(arrivedTree)).not.toContain('몇 분 후 도착하나요?');
    expect(text(arrivedTree)).toContain('2026-07-27 12:12:00');
  });

  it('countdown과 예상 도착 시각 계산은 절대 시각을 기준으로 한다', () => {
    expect(formatRemainingTime(
      '2026-07-27T12:30:20+09:00',
      Date.parse('2026-07-27T12:25:15+09:00'),
    )).toBe('05:05');
    expect(getEstimatedArrivalAt({
      memberId: 1,
      nickname: 'a',
      profileImageUrl: null,
      status: 'ARRIVAL_TIME_SELECTED',
      arrivalMinutes: 10,
      arrivalTimeSelectedAt: '2026-07-27T12:05:20+09:00',
    })).toBe('2026-07-27T03:15:20.000Z');
  });
});
