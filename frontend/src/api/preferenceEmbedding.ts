import { apiClient, apiClientNullable, apiClientVoid, ApiClientError } from './apiClient';

export type EmbeddingStatus = 'PENDING' | 'COMPLETED' | 'FAILED';

export interface PreferenceEmbedding {
  memberId: number;
  preferenceText: string;
  embeddingStatus: EmbeddingStatus;
  embeddingModel: string | null;
  createdAt: string;
  updatedAt: string;
}

export const preferenceEmbeddingApi = {
  /** 저장된 취향이 없으면 null. "아직 입력하지 않음"은 오류가 아니다. */
  get: () =>
    apiClientNullable<PreferenceEmbedding>('/api/members/me/preference-embedding'),

  createOrUpdate: (preferenceText: string) =>
    apiClient<PreferenceEmbedding>('/api/members/me/preference-embedding', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ preferenceText }),
    }),

  delete: () =>
    apiClientVoid('/api/members/me/preference-embedding', { method: 'DELETE' }),
};

export function isConsentRequired(error: unknown): boolean {
  return error instanceof ApiClientError && error.code === 'AI_CONSENT_REQUIRED';
}
