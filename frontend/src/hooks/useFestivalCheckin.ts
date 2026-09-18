// FestivalDetailPage(진입 겸 인라인 체크인)와 CheckInPage(매칭 흐름에서 온 체크인 전용 화면)가
// 공유하는 GPS 체크인 로직. 두 화면 다 "같은 축제, 같은 API, 같은 에러 처리"를 쓰므로 여기 하나로 모은다.
// docs/03_FRONTEND_GUIDE.md, docs/21_CHECKIN_MATCH_POOL_INTEGRATION_DESIGN.md 참고.

import { useCallback, useState } from 'react';
import { checkinApi, type CheckInResponse } from '../api/checkin';
import { getCurrentPosition } from '../utils/geolocation';
import { describeCheckinError } from '../utils/checkinError';

export type FestivalCheckinState =
  | { status: 'idle' }
  | { status: 'locating' }
  | { status: 'submitting' }
  | { status: 'success'; result: CheckInResponse }
  | { status: 'error'; message: string };

export function useFestivalCheckin(festivalId: number | null) {
  const [state, setState] = useState<FestivalCheckinState>({ status: 'idle' });

  const checkIn = useCallback(async () => {
    if (festivalId === null) return;
    setState({ status: 'locating' });
    try {
      const position = await getCurrentPosition();
      setState({ status: 'submitting' });
      const result = await checkinApi.checkIn(
        festivalId,
        position.latitude,
        position.longitude,
        position.accuracyMeters,
      );
      setState({ status: 'success', result });
    } catch (error) {
      setState({ status: 'error', message: describeCheckinError(error) });
    }
  }, [festivalId]);

  const reset = useCallback(() => setState({ status: 'idle' }), []);

  return { state, checkIn, reset };
}
