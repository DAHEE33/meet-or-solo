import { buildApiUrl } from './apiClient';

/**
 * 세션 bootstrap 전용 조회 결과.
 *
 * 판정은 하지 않고 응답의 원자료만 담는다. 어떤 화면으로 보낼지는
 * `components/splash/splashPolicy.ts`의 순수 함수가 결정한다.
 */
export type SessionProbe = {
  /** HTTP status. 네트워크 자체가 실패했거나 요청이 취소되면 `0`. */
  status: number;
  /** 서버 error code. 없으면 `null`. */
  code: string | null;
};

/**
 * 로그인 여부만 확인한다.
 *
 * `apiClient`를 쓰지 않는 이유는 `apiClient`가 401·영구제한 응답에서
 * `window.location.replace('/login')`을 호출하기 때문이다(`apiClient.ts`의
 * `redirectToLoginIfUnauthorized`). 스플래시가 그 경로를 타면 전체 페이지 리로드가 끼어
 * 애니메이션이 중간에 끊긴다. 공유 클라이언트를 고치는 대신 부트스트랩 시점에만 쓰는
 * 조회를 따로 둔다.
 *
 * 이 함수는 예외를 던지지 않는다. 스플래시는 어떤 실패에서도 화면을 계속 진행해야 한다.
 */
export async function probeSession(signal?: AbortSignal): Promise<SessionProbe> {
  try {
    const response = await fetch(buildApiUrl('/api/members/me'), {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      signal,
    });

    if (response.ok) return { status: response.status, code: null };

    const body = (await response.json().catch(() => null)) as
      | { error?: { code?: string | null } | null }
      | null;

    return { status: response.status, code: body?.error?.code ?? null };
  } catch {
    return { status: 0, code: null };
  }
}
