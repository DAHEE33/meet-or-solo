import { useCallback, useEffect, useRef, useState } from 'react';
import { adminDashboardApi, type AdminDashboardStats } from '../api/adminDashboard';

export type AdminDashboardState =
  | { status: 'loading' }
  | { status: 'loaded'; stats: AdminDashboardStats }
  | { status: 'error' };

/**
 * 관리자 대시보드 집계를 읽는다. 조회 전용이라 `useCurrentCheckin`과 같은 단순 구조다.
 *
 * <p>재조회는 이전 값을 지우지 않는다. `loading`으로 되돌리면 갱신할 때마다 숫자 카드가
 * 0으로 깜빡였다가 돌아온다.
 */
export function useAdminDashboard() {
  const [state, setState] = useState<AdminDashboardState>({ status: 'loading' });
  const mountedRef = useRef(true);

  const refresh = useCallback(async () => {
    try {
      const stats = await adminDashboardApi.stats();
      if (mountedRef.current) setState({ status: 'loaded', stats });
    } catch {
      // 첫 조회가 실패했을 때만 에러 화면으로 간다. 이미 보여 주고 있는 집계가 있으면
      // 재조회 실패로 화면을 비우지 않고 직전 값을 유지한다.
      if (mountedRef.current) {
        setState((previous) => (previous.status === 'loaded' ? previous : { status: 'error' }));
      }
    }
  }, []);

  const retry = useCallback(() => {
    setState({ status: 'loading' });
    void refresh();
  }, [refresh]);

  useEffect(() => {
    mountedRef.current = true;
    void refresh();
    return () => { mountedRef.current = false; };
  }, [refresh]);

  return { state, retry };
}
