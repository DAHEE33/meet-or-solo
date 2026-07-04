import { apiClient } from './apiClient';

export type HealthResponse = {
  status: string;
  service: string;
};

export async function getHealth(): Promise<HealthResponse> {
  return apiClient<HealthResponse>('/api/health');
}
