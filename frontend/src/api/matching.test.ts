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
});
