import { apiClient, apiClientVoid, ApiClientError } from './apiClient';

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
  get: () =>
    apiClient<PreferenceEmbedding>('/api/members/me/preference-embedding'),

  createOrUpdate: (preferenceText: string) =>
    apiClient<PreferenceEmbedding>('/api/members/me/preference-embedding', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ preferenceText }),
    }),

  delete: () =>
    apiClientVoid('/api/members/me/preference-embedding', { method: 'DELETE' }),
};

export function isEmbeddingNotFound(error: unknown): boolean {
  return error instanceof ApiClientError && error.code === 'EMBEDDING_NOT_FOUND';
}

export function isConsentRequired(error: unknown): boolean {
  return error instanceof ApiClientError && error.code === 'AI_CONSENT_REQUIRED';
}
