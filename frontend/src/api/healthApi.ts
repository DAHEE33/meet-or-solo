const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

export type HealthResponse = {
  status: string;
  service: string;
};

export async function getHealth(): Promise<HealthResponse> {
  const baseUrl = API_BASE_URL.replace(/\/$/, '');
  const healthUrl = baseUrl ? `${baseUrl}/api/health` : '/api/health';
  const response = await fetch(healthUrl, {
    headers: {
      Accept: 'application/json'
    }
  });

  if (!response.ok) {
    throw new Error(`Health API 요청 실패: ${response.status}`);
  }

  return response.json() as Promise<HealthResponse>;
}
