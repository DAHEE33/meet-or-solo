import type { ApiResponse, FieldError } from './types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

type ApiClientOptions = Omit<RequestInit, 'headers'> & {
  headers?: HeadersInit;
};

export class ApiClientError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code: string | null,
    public readonly fields: FieldError[] | undefined,
  ) {
    super(message);
    this.name = 'ApiClientError';
  }
}

export function buildApiUrl(path: string): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const baseUrl = API_BASE_URL.replace(/\/$/, '');

  return baseUrl ? `${baseUrl}${normalizedPath}` : normalizedPath;
}

function getErrorMessage(response: ApiResponse<unknown>, fallbackMessage: string): string {
  return response.error?.message || fallbackMessage;
}

async function request<T>(path: string, options: ApiClientOptions): Promise<T | null> {
  const response = await fetch(buildApiUrl(path), {
    credentials: 'include',
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
    redirectToLoginIfUnauthorized(response.status);
    throw new ApiClientError(
      `API 응답을 해석할 수 없습니다. HTTP ${response.status}`,
      response.status,
      null,
      undefined,
    );
  }

  if (!response.ok) {
    redirectToLoginIfUnauthorized(response.status);
    throw new ApiClientError(
      getErrorMessage(body, `API 요청 실패: HTTP ${response.status}`),
      response.status,
      body.error?.code ?? null,
      body.error?.fields,
    );
  }

  if (!body.success) {
    throw new ApiClientError(
      getErrorMessage(body, 'API 요청 처리에 실패했습니다.'),
      response.status,
      body.error?.code ?? null,
      body.error?.fields,
    );
  }

  return body.data;
}

export async function apiClient<T>(path: string, options: ApiClientOptions = {}): Promise<T> {
  const data = await request<T>(path, options);
  if (data === null) {
    throw new ApiClientError('API 응답 데이터가 비어 있습니다.', 200, null, undefined);
  }
  return data;
}

export function apiClientNullable<T>(
  path: string,
  options: ApiClientOptions = {},
): Promise<T | null> {
  return request<T>(path, options);
}

export async function apiClientVoid(
  path: string,
  options: ApiClientOptions = {},
): Promise<void> {
  const response = await fetch(buildApiUrl(path), {
    credentials: 'include',
    ...options,
    headers: { Accept: 'application/json', ...options.headers },
  });
  if (response.ok && response.status === 204) return;

  let body: ApiResponse<unknown> | null = null;
  try {
    body = (await response.json()) as ApiResponse<unknown>;
  } catch {
    redirectToLoginIfUnauthorized(response.status);
    throw new ApiClientError(
      `API 응답을 해석할 수 없습니다. HTTP ${response.status}`,
      response.status,
      null,
      undefined,
    );
  }
  if (!response.ok || !body.success) {
    redirectToLoginIfUnauthorized(response.status);
    throw new ApiClientError(
      getErrorMessage(body, `API 요청 실패: HTTP ${response.status}`),
      response.status,
      body.error?.code ?? null,
      body.error?.fields,
    );
  }
}

function redirectToLoginIfUnauthorized(status: number): void {
  if (status !== 401 || window.location.pathname === '/login') return;
  window.location.replace('/login');
}
