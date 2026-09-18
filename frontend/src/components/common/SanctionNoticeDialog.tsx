import { useEffect, useState } from 'react';
import { X } from 'lucide-react';
import AccountRestrictionNotice from './AccountRestrictionNotice';
import { SANCTION_EVENT } from '../../api/apiClient';
import type { SanctionNotice } from '../../api/types';

/** 하루 동안 보지 않기 설정을 담는 key. 이 브라우저에만 남는다. */
const SNOOZE_KEY = 'sanctionNoticeSnoozedUntil';
const SNOOZE_MS = 24 * 60 * 60 * 1000;

/**
 * 저장소 접근은 시크릿 모드나 site data 차단 설정에서 던질 수 있으므로 항상 감싼다.
 * 읽지 못하면 "보지 않기 설정 없음"으로 본다. 안내를 한 번 더 보는 편이 놓치는 것보다 낫다.
 */
export function isSnoozed(now = Date.now()): boolean {
  try {
    const stored = window.localStorage.getItem(SNOOZE_KEY);
    if (!stored) return false;
    const until = Number(stored);
    return Number.isFinite(until) && now < until;
  } catch {
    return false;
  }
}

export function snooze(now = Date.now()): void {
  try {
    window.localStorage.setItem(SNOOZE_KEY, String(now + SNOOZE_MS));
  } catch {
    // 저장하지 못해도 이번 팝업은 닫힌다. 다음 활동 시도에서 다시 뜰 뿐이다.
  }
}

/**
 * 정지 회원이 활동을 시도했을 때 사유·기간을 알리는 팝업(docs/19 4.8).
 *
 * App 최상단에 한 번만 붙인다. `apiClient`가 모든 요청의 단일 통로이므로, 체크인·매칭·댓글
 * 어느 화면에서 403을 받아도 여기서 안내가 뜬다. 활동 화면마다 개별 처리를 붙이지 않는다.
 *
 * 마이페이지처럼 화면 자체가 제재 안내를 이미 보여주는 곳에서는 팝업이 중복이므로
 * `하루 동안 보지 않기`로 접을 수 있다. 조회는 계속 가능한 상태라 팝업을 강제할 이유가 없다.
 *
 * 영구정지는 로그인 자체가 막혀 이 팝업을 볼 수 없다. 그쪽은 로그인 화면의 안내 card가 맡는다.
 */
export default function SanctionNoticeDialog() {
  const [notice, setNotice] = useState<SanctionNotice | null>(null);

  useEffect(() => {
    const onSanction = (event: Event) => {
      const detail = (event as CustomEvent<SanctionNotice>).detail;
      if (detail && !isSnoozed()) setNotice(detail);
    };

    window.addEventListener(SANCTION_EVENT, onSanction);
    return () => window.removeEventListener(SANCTION_EVENT, onSanction);
  }, []);

  useEffect(() => {
    if (!notice) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setNotice(null);
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [notice]);

  if (!notice) return null;

  const close = () => setNotice(null);
  const closeForToday = () => {
    snooze();
    setNotice(null);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink/45 sm:items-center sm:p-5">
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="account-restriction-title"
        className="w-full max-w-[430px] rounded-t-3xl bg-white p-5 sm:rounded-3xl"
      >
        <div className="flex justify-end">
          <button type="button" aria-label="이용정지 안내 닫기" onClick={close}>
            <X aria-hidden="true" />
          </button>
        </div>
        <AccountRestrictionNotice notice={notice} />
        <div className="mt-4 grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={closeForToday}
            className="h-12 rounded-2xl border border-line text-[14px] font-semibold text-ink/60"
          >
            하루 동안 보지 않기
          </button>
          <button
            type="button"
            onClick={close}
            className="h-12 rounded-2xl bg-ink text-[14px] font-bold text-white"
          >
            닫기
          </button>
        </div>
      </section>
    </div>
  );
}
