// /matching 화면이 "체크인하기" 버튼 대신 실제 체크인 상태(어느 축제인지, 언제 만료되는지)를
// 보여주고 매칭 신청 전(IDLE)에 취소할 수 있게 하는 훅.
// docs/21_CHECKIN_MATCH_POOL_INTEGRATION_DESIGN.md 참고.

import { useCallback, useEffect, useState } from 'react';
import { checkinApi, type CurrentCheckinResponse } from '../api/checkin';

export type CurrentCheckinState =
  | { status: 'loading' }
  | { status: 'loaded'; checkin: CurrentCheckinResponse | null }
  | { status: 'error' };

export function useCurrentCheckin() {
  const [state, setState] = useState<CurrentCheckinState>({ status: 'loading' });
  const [isCancelling, setIsCancelling] = useState(false);

  const refresh = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      const checkin = await checkinApi.getCurrent();
      setState({ status: 'loaded', checkin });
    } catch {
      setState({ status: 'error' });
    }
  }, []);

  useEffect(() => {
    void refresh();
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
