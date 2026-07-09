import type { ApiResponse } from './types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

type ApiClientOptions = Omit<RequestInit, 'headers'> & {
  headers?: HeadersInit;
};

function buildApiUrl(path: string): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const baseUrl = API_BASE_URL.replace(/\/$/, '');

  return baseUrl ? `${baseUrl}${normalizedPath}` : normalizedPath;
}

function getErrorMessage(response: ApiResponse<unknown>, fallbackMessage: string): string {
  return response.error?.message || fallbackMessage;
}

export async function apiClient<T>(path: string, options: ApiClientOptions = {}): Promise<T> {
  const response = await fetch(buildApiUrl(path), {
    ...options,
    headers: {
      Accept: 'application/json',
      ...options.headers,
    },
  });

  let body: ApiResponse<T> | null = null;

  try {
    body = (await response.json()) as ApiResponse<T>;
  } catch {
    throw new Error(`API 응답을 해석할 수 없습니다. HTTP ${response.status}`);
  }

  if (!response.ok) {
    throw new Error(getErrorMessage(body, `API 요청 실패: HTTP ${response.status}`));
  }

  if (!body.success) {
    throw new Error(getErrorMessage(body, 'API 요청 처리에 실패했습니다.'));
  }

  if (body.data === null) {
    throw new Error('API 응답 데이터가 비어 있습니다.');
  }

  return body.data;
}
