import { useEffect, useState } from 'react';
import { memberProfileApi } from '../api/memberProfile';
import type { SanctionNotice } from '../api/types';

/**
 * 로그인한 회원의 제재 상태를 읽는다(docs/19 4.8).
 *
 * 정지 회원은 로그인해서 조회를 계속하므로, 화면이 활동 UI를 내주기 전에 제재 여부를 알아야
 * 한다. 활동을 시도해 `403`을 받은 뒤 dialog로 알리는 것만으로는 부족하다. 예를 들어 매칭
 * 화면은 지난 완료 매칭 카드를 띄우고 "다시 매칭하기"만 제공하는데, 정지 회원은 새 매칭을
 * 신청할 수 없어 그 화면에서 빠져나갈 수 없다.
 *
 * 조회에 실패하면 제재가 없는 것으로 본다. 제재 안내를 못 읽는 것 때문에 정상 회원의 화면이
 * 막히는 편이 더 나쁘다.
 */
export function useMemberSanction() {
  const [notice, setNotice] = useState<SanctionNotice | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const controller = new AbortController();
    memberProfileApi.getMine(controller.signal)
      .then((profile) => {
        if (controller.signal.aborted) return;
        setNotice(profile.sanction ?? null);
        setLoading(false);
      })
      .catch(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, []);

  return { notice, loading, suspended: notice?.status === 'SUSPENDED' };
}
