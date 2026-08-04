import { afterEach, describe, expect, it, vi } from 'vitest';
import { matchingApi, type MatchProposalAction } from './matching';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('matchingApi', () => {
  it.each<MatchProposalAction>(['ACCEPT', 'REJECT', 'CANCEL_CURRENT_MEMBERS'])(
    'proposal 응답 action %s를 그대로 전송한다',
    async (action) => {
      const fetchMock = vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            success: true,
            data: { attemptId: 1, proposalId: 10, action, recordedResponse: action, attemptStatus: 'PROPOSED' },
            error: null,
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      );
      vi.stubGlobal('fetch', fetchMock);

      await matchingApi.respond(10, action);

      expect(fetchMock).toHaveBeenCalledWith(
        '/api/matching/proposals/10/responses',
        expect.objectContaining({
          credentials: 'include',
          method: 'POST',
          body: JSON.stringify({ action }),
        }),
      );
    },
  );

  it.each([
    ['pool', () => matchingApi.getCurrentPool()],
    ['proposal', () => matchingApi.getActiveProposal()],
    ['group', () => matchingApi.getCurrentGroup()],
    ['group events', () => matchingApi.getCurrentGroupEvents()],
  ])('%s 조회의 data:null을 정상 처리한다', async (_name, request) => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ success: true, data: null, error: null }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    await expect(request()).resolves.toBeNull();
  });

  it('current 로그인 회원의 group events를 식별자 없이 조회한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: { events: [] }, error: null }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await matchingApi.getCurrentGroupEvents();

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/matching/groups/me/current/events',
      expect.objectContaining({ credentials: 'include' }),
    );
  });

  it.each([5, 10, 20, 25] as const)(
    '도착 예정 시간 %s분을 current 로그인 회원 endpoint로 전송한다',
    async (arrivalMinutes) => {
      const response = {
        groupId: 1,
        festivalId: 2,
        status: 'CONFIRMED',
        confirmedMemberCount: 2,
        confirmedAt: '2026-07-29T12:00:00+09:00',
        arrivalDeadlineAt: '2026-07-29T12:30:00+09:00',
        festival: {
          festivalId: 2,
          title: '테스트 축제',
          address: null,
          eventStartDate: null,
          eventEndDate: null,
        },
        members: [],
      };
      const fetchMock = vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ success: true, data: response, error: null }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      );
      vi.stubGlobal('fetch', fetchMock);

      await matchingApi.selectArrivalTime(arrivalMinutes);

      expect(fetchMock).toHaveBeenCalledWith(
        '/api/matching/groups/me/current/arrival-time',
        expect.objectContaining({
          credentials: 'include',
          method: 'PUT',
          body: JSON.stringify({ arrivalMinutes }),
        }),
      );
    },
  );

  it('도착 완료를 body와 식별자 없이 current 회원 endpoint로 전송한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: {}, error: null }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await matchingApi.arrive();

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/matching/groups/me/current/arrival',
      expect.objectContaining({
        credentials: 'include',
        method: 'PUT',
      }),
    );
    expect(fetchMock.mock.calls[0][1]).not.toHaveProperty('body');
  });

  it.each(['SCHEDULE_CHANGED', 'TRANSPORTATION_ISSUE', 'OTHER'] as const)(
    '구조화된 취소 사유 %s만 current 회원 endpoint로 전송한다',
    async (reason) => {
      const fetchMock = vi.fn().mockResolvedValue(
        new Response(JSON.stringify({
          success: true,
          data: {
            groupId: 1,
            memberStatus: 'CANCELLED',
            groupStatus: 'CANCELLED',
            groupContinues: false,
            currentMemberCount: 0,
          },
          error: null,
        }), { status: 200, headers: { 'Content-Type': 'application/json' } }),
      );
      vi.stubGlobal('fetch', fetchMock);

      await matchingApi.cancelParticipation(reason);

      expect(fetchMock).toHaveBeenCalledWith(
        '/api/matching/groups/me/current/cancellation',
        expect.objectContaining({
          credentials: 'include',
          method: 'PUT',
          body: JSON.stringify({ reason }),
        }),
      );
    },
  );
});
