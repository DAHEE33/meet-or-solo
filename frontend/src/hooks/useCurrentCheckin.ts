// /matching 화면이 "체크인하기" 버튼 대신 실제 체크인 상태(어느 축제인지, 언제 만료되는지)를
// 보여주고 매칭 신청 전(IDLE)에 취소할 수 있게 하는 훅.
// docs/21_CHECKIN_MATCH_POOL_INTEGRATION_DESIGN.md 참고.

import { useCallback, useEffect, useRef, useState } from 'react';
import { checkinApi, type CurrentCheckinResponse } from '../api/checkin';

export type CurrentCheckinState =
  | { status: 'loading' }
  | { status: 'loaded'; checkin: CurrentCheckinResponse | null }
  | { status: 'error' };

export function useCurrentCheckin() {
  const [state, setState] = useState<CurrentCheckinState>({ status: 'loading' });
  const [isCancelling, setIsCancelling] = useState(false);
  const mountedRef = useRef(true);

  /**
   * 체크인 상태를 다시 읽는다.
   *
   * 첫 조회가 아니면 `loading`으로 되돌리지 않는다. 되돌리면 갱신하는 순간 `currentCheckin`이
   * null이 되어 `MatchingConditionPage`의 `hasFestival`이 false로 떨어지고, "자동 매칭 신청"
   * 버튼이 잠깐 비활성화된다. 사용자에게는 버튼이 안 눌리는 것으로 보인다.
   */
  const refresh = useCallback(async () => {
    setState((previous) => (previous.status === 'loaded' ? previous : { status: 'loading' }));
    try {
      const checkin = await checkinApi.getCurrent();
      if (mountedRef.current) setState({ status: 'loaded', checkin });
    } catch {
      if (mountedRef.current) setState({ status: 'error' });
    }
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    void refresh();
    return () => { mountedRef.current = false; };
  }, [refresh]);

  /**
   * 탭이 다시 보일 때 체크인을 재조회한다.
   *
   * 체크인은 `checked_in_at + 1시간`이면 만료되는데, 이 훅이 mount 때 한 번만 조회하면 화면은
   * 계속 유효한 체크인이 있다고 믿는다. 그 상태에서 매칭을 신청하면 backend가 "해당 축제의
   * 유효한 체크인이 필요합니다"로 거절하고, 재신청 흐름에서는 그 실패가 화면에 드러나지 않아
   * 버튼이 먹통인 것처럼 보인다. `useMatchingSession`과 같은 신호를 쓴다.
   */
  useEffect(() => {
    const handleVisibility = () => {
      if (document.visibilityState !== 'hidden') void refresh();
    };
    document.addEventListener('visibilitychange', handleVisibility);
    return () => document.removeEventListener('visibilitychange', handleVisibility);
  }, [refresh]);

  const cancel = useCallback(async () => {
    if (isCancelling) return false;
    setIsCancelling(true);
    try {
      await checkinApi.cancelCurrent();
      setState({ status: 'loaded', checkin: null });
      return true;
    } catch {
      return false;
    } finally {
      setIsCancelling(false);
    }
  }, [isCancelling]);

  return { state, refresh, cancel, isCancelling };
}
