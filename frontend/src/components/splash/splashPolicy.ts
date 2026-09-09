import { BANNED_CODE, SANCTION_LOGIN_PATH } from '../../api/apiClient';
import type { SessionProbe } from '../../api/session';

/**
 * 로고 애니메이션이 온전히 한 번 보이는 최소 시간.
 *
 * "2초 고정 대기"로 두지 않는다. 이미 로그인된 회원이 진입할 때마다 2초를 기다리게 되기
 * 때문이다. 세션 확인과 병렬로 돌리고 둘 다 끝나면 해제하므로, 느린 네트워크에서만 이보다
 * 오래 머문다. 애니메이션 마지막 구간(워드마크 760ms + 380ms)이 끝나는 시점을 덮는 값이다.
 */
export const SPLASH_MIN_VISIBLE_MS = 1200;

/** 오버레이 페이드아웃 시간. `SplashScreen`의 transition 시간과 같아야 한다. */
export const SPLASH_FADE_OUT_MS = 260;

/** 탭 세션당 1회만 재생하기 위한 `sessionStorage` key. */
export const SPLASH_SEEN_KEY = 'mors:splash-seen';

/**
 * 스플래시를 건너뛰는 경로.
 *
 * - `/login`: 미로그인 회원이 도착하는 목적지다. 여기서 또 재생하면 로그인 화면이 늦게 뜬다.
 * - `/admin`: `AdminRoute`가 자체 권한 확인 화면을 갖고 있어 안내가 두 번 겹친다.
 */
const SKIP_PATHS = ['/login'];
const SKIP_PREFIXES = ['/admin'];

export function shouldSkipSplash({
  pathname,
  alreadyShown,
}: {
  pathname: string;
  alreadyShown: boolean;
}): boolean {
  if (alreadyShown) return true;
  if (SKIP_PATHS.includes(pathname)) return true;
  return SKIP_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}

/**
 * 세션 조회 결과를 이동할 경로로 바꾼다. `null`이면 원래 목적지를 그대로 유지한다.
 *
 * 401과 영구제한 403만 로그인 화면으로 보낸다. 네트워크 실패(`0`)나 서버 오류(5xx)에서는
 * 이동하지 않는다 — 로그인된 회원을 일시적 장애로 로그인 화면에 떨어뜨리면 안 되고, 각 화면이
 * 이미 자기 오류 상태를 갖고 있다. 이용정지(`MEMBER_SUSPENDED`)도 로그인 상태이므로 이동하지
 * 않고 `SanctionNoticeDialog`가 안내를 맡는다.
 */
export function resolveSplashTarget({ status, code }: SessionProbe): string | null {
  if (status >= 200 && status < 300) return null;
  if (status === 403 && code === BANNED_CODE) return SANCTION_LOGIN_PATH;
  if (status === 401) return '/login';
  return null;
}

/** `sessionStorage`는 시크릿 모드나 차단 설정에서 접근 자체가 throw할 수 있어 감싼다. */
export function readSplashSeen(): boolean {
  try {
    return window.sessionStorage.getItem(SPLASH_SEEN_KEY) === '1';
  } catch {
    return false;
  }
}

export function markSplashSeen(): void {
  try {
    window.sessionStorage.setItem(SPLASH_SEEN_KEY, '1');
  } catch {
    /* 저장에 실패하면 다음 진입에서 한 번 더 재생될 뿐이라 무시한다. */
  }
}
