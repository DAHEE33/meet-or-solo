import type { ApiError, ApiResponse, FieldError, SanctionNotice } from './types';

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
    public readonly sanction: SanctionNotice | null = null,
  ) {
    super(message);
    this.name = 'ApiClientError';
  }
}

/** 제재로 접근이 막힌 응답의 error code. */
const SUSPENDED_CODE = 'MEMBER_SUSPENDED';
/** 영구제한 error code. 스플래시의 세션 판정(`splashPolicy.ts`)도 같은 값을 쓴다. */
export const BANNED_CODE = 'MEMBER_BANNED';

export const SANCTION_LOGIN_PATH = '/login?oauthError=account_restricted';

/**
 * 정지 회원이 활동을 시도해 403을 받았을 때 발생시키는 이벤트.
 *
 * 정지 회원은 로그인 상태로 조회를 계속하므로 로그인 화면으로 보내면 안 된다. 대신 이
 * 이벤트를 App 최상단의 `SanctionNoticeDialog`가 받아 안내를 띄운다. apiClient가 모든
 * 요청의 단일 통로이므로, 활동 화면마다 개별로 붙이지 않아도 전부 덮인다.
 */
export const SANCTION_EVENT = 'member-sanction';

function announceSuspension(sanction: SanctionNotice): void {
  // 테스트는 window를 최소 객체로 stub하므로 존재 여부를 확인한다.
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function') return;
  if (typeof CustomEvent !== 'function') return;
  window.dispatchEvent(new CustomEvent<SanctionNotice>(SANCTION_EVENT, { detail: sanction }));
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
    handleSanction(response.status, body.error);
    throw new ApiClientError(
      getErrorMessage(body, `API 요청 실패: HTTP ${response.status}`),
      response.status,
      body.error?.code ?? null,
      body.error?.fields,
      body.error?.sanction ?? null,
    );
  }

  if (!body.success) {
    throw new ApiClientError(
      getErrorMessage(body, 'API 요청 처리에 실패했습니다.'),
      response.status,
      body.error?.code ?? null,
      body.error?.fields,
      body.error?.sanction ?? null,
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
    handleSanction(response.status, body.error);
    throw new ApiClientError(
      getErrorMessage(body, `API 요청 실패: HTTP ${response.status}`),
      response.status,
      body.error?.code ?? null,
      body.error?.fields,
      body.error?.sanction ?? null,
    );
  }
}

/**
 * 인증이 끊기거나 제재로 막힌 응답을 로그인 화면으로 보낸다.
 *
 * 401은 session이 없어진 경우다. 403 제재는 화면마다 "불러오지 못했습니다"로 흘리면
 * 사용자가 이유를 알 수 없으므로 안내 화면으로 보낸다. 이때 서버가 같은 응답에 실어준
 * notice cookie로 로그인 화면이 사유·기간을 다시 조회한다.
 */
function redirectToLoginIfUnauthorized(status: number, code?: string | null): void {
  // 영구 제한은 로그인 자체가 막히는 상태이므로 로그인 화면의 안내로 보낸다.
  // 정지는 로그인 상태로 조회를 계속하므로 화면을 이동시키지 않는다(handleSanction이 처리).
  const banned = status === 403 && code === BANNED_CODE;
  // window는 이동이 필요할 때만 읽는다. 먼저 읽으면 다른 status에서도 window에 의존하게 된다.
  if (status !== 401 && !banned) return;
  if (window.location.pathname === '/login') return;

  window.location.replace(banned ? SANCTION_LOGIN_PATH : '/login');
}

/**
 * 제재 응답을 처리한다. 영구 제한은 로그인 화면으로, 정지는 안내 이벤트로 보낸다.
 * 어느 쪽이든 error는 그대로 던져지므로 호출한 화면이 실패를 알 수 있다.
 */
function handleSanction(status: number, error: ApiError | null | undefined): void {
  redirectToLoginIfUnauthorized(status, error?.code);
  if (status === 403 && error?.code === SUSPENDED_CODE && error.sanction) {
    announceSuspension(error.sanction);
  }
}
