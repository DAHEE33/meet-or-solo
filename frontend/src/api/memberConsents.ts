import { apiClient, ApiClientError } from './apiClient';

/** 현재 화면에서 기록·철회할 수 있는 동의 유형. 서버 `MemberConsentType`과 값이 같아야 한다. */
export type ConsentType = 'TERMS' | 'PRIVACY' | 'AI_PROCESSING' | 'OVERSEAS_TRANSFER';

/** 취향 분석에 필요한 동의. 하나라도 없으면 취향 글을 외부로 보내지 않는다. */
export const AI_CONSENT_TYPES: ConsentType[] = ['AI_PROCESSING', 'OVERSEAS_TRANSFER'];

/** 가입 시 필수로 받는 동의. */
export const SIGNUP_CONSENT_TYPES: ConsentType[] = ['TERMS', 'PRIVACY'];

export interface MemberConsent {
  consentType: ConsentType;
  agreed: boolean;
  version: string;
  agreedAt: string | null;
  revokedAt: string | null;
}

interface MemberConsentsResponse {
  consents: MemberConsent[];
}

export const memberConsentApi = {
  /** 기록이 없는 유형도 `agreed: false` 항목으로 내려온다. */
  getAiConsents: () =>
    apiClient<MemberConsentsResponse>('/api/members/me/consents').then(
      (response) => response.consents,
    ),

  agree: (consentType: ConsentType) =>
    apiClient<MemberConsent>('/api/members/me/consents', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ consentType }),
    }),

  revoke: (consentType: ConsentType) =>
    apiClient<MemberConsent>(`/api/members/me/consents/${consentType}`, {
      method: 'DELETE',
    }),
};

/** 취향 분석에 필요한 동의를 모두 보유했는지 확인한다. */
export function hasAllAiConsents(consents: MemberConsent[]): boolean {
  return AI_CONSENT_TYPES.every(
    (type) => consents.find((consent) => consent.consentType === type)?.agreed === true,
  );
}

/** 가입 시 필수 동의를 순서대로 기록한다. 하나라도 실패하면 예외를 그대로 전파한다. */
export async function agreeAll(consentTypes: ConsentType[]): Promise<void> {
  for (const consentType of consentTypes) {
    await memberConsentApi.agree(consentType);
  }
}

export function isSignupConsentRequired(error: unknown): boolean {
  return error instanceof ApiClientError && error.code === 'SIGNUP_CONSENT_REQUIRED';
}
