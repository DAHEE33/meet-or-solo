import { afterEach, describe, expect, it, vi } from 'vitest';
import { agreeAll, hasAllAiConsents, memberConsentApi, type MemberConsent } from './memberConsents';

afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

function jsonResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify({ success: status < 400, data, error: null }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function consent(overrides: Partial<MemberConsent> & Pick<MemberConsent, 'consentType'>): MemberConsent {
  return { agreed: false, version: '1.0', agreedAt: null, revokedAt: null, ...overrides };
}

describe('memberConsentApi', () => {
  it('동의 상태를 조회하면 항목 배열을 그대로 돌려준다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
      consents: [
        consent({ consentType: 'AI_PROCESSING', agreed: true, agreedAt: '2026-08-26T00:00:00Z' }),
        consent({ consentType: 'OVERSEAS_TRANSFER' }),
      ],
    }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(memberConsentApi.getAiConsents()).resolves.toHaveLength(2);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/members/me/consents',
      expect.objectContaining({ credentials: 'include' }),
    );
  });

  it('동의를 기록할 때 버전은 보내지 않는다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(
      consent({ consentType: 'AI_PROCESSING', agreed: true, agreedAt: '2026-08-26T00:00:00Z' }),
    ));
    vi.stubGlobal('fetch', fetchMock);

    await memberConsentApi.agree('AI_PROCESSING');

    const options = fetchMock.mock.calls[0][1] as RequestInit;
    expect(options.method).toBe('POST');
    expect(JSON.parse(options.body as string)).toEqual({ consentType: 'AI_PROCESSING' });
    // 고지 문구 버전은 서버가 정한다.
    expect(options.body).not.toContain('version');
  });

  it('철회는 유형을 경로에 담아 DELETE한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(
      consent({ consentType: 'OVERSEAS_TRANSFER', revokedAt: '2026-08-26T00:00:00Z' }),
    ));
    vi.stubGlobal('fetch', fetchMock);

    await memberConsentApi.revoke('OVERSEAS_TRANSFER');

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/members/me/consents/OVERSEAS_TRANSFER',
      expect.objectContaining({ method: 'DELETE', credentials: 'include' }),
    );
  });
});

describe('hasAllAiConsents', () => {
  it('두 동의가 모두 있어야 true다', () => {
    expect(hasAllAiConsents([
      consent({ consentType: 'AI_PROCESSING', agreed: true }),
      consent({ consentType: 'OVERSEAS_TRANSFER', agreed: true }),
    ])).toBe(true);
  });

  it('국외 이전 동의만 없어도 false다', () => {
    expect(hasAllAiConsents([
      consent({ consentType: 'AI_PROCESSING', agreed: true }),
      consent({ consentType: 'OVERSEAS_TRANSFER', agreed: false }),
    ])).toBe(false);
  });

  it('항목 자체가 없으면 false다', () => {
    expect(hasAllAiConsents([consent({ consentType: 'AI_PROCESSING', agreed: true })])).toBe(false);
    expect(hasAllAiConsents([])).toBe(false);
  });
});

describe('agreeAll', () => {
  it('요청한 순서대로 모두 기록한다', async () => {
    // Response body는 한 번만 읽을 수 있으므로 호출마다 새 객체를 만든다.
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(jsonResponse(
      consent({ consentType: 'TERMS', agreed: true, agreedAt: '2026-08-26T00:00:00Z' }),
    )));
    vi.stubGlobal('fetch', fetchMock);

    await agreeAll(['TERMS', 'PRIVACY']);

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(JSON.parse((fetchMock.mock.calls[0][1] as RequestInit).body as string).consentType)
      .toBe('TERMS');
    expect(JSON.parse((fetchMock.mock.calls[1][1] as RequestInit).body as string).consentType)
      .toBe('PRIVACY');
  });

  it('앞선 동의가 실패하면 뒤 동의를 시도하지 않고 예외를 전파한다', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(jsonResponse(null, 500)));
    vi.stubGlobal('fetch', fetchMock);

    await expect(agreeAll(['TERMS', 'PRIVACY'])).rejects.toThrow();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
