/**
 * 화면에 잠깐 뜨는 알림 한 자리.
 *
 * <p><b>알림은 화면 위에만 뜬다.</b> 예전에는 긴급 알림이 위, 나머지가 아래로 갈려 있었고
 * 매칭방은 자기 스낵바를 따로 아래에 띄웠다. 같은 성격의 알림이 화면 위아래에서 제각각
 * 나타나니 사용자에게는 종류가 나뉜 것처럼 보였다. 위치와 모양을 이 파일 하나로 모은다.
 *
 * <p>머무는 시간도 {@link NOTICE_DISMISS_MS} 하나로 통일한다. 자리마다 3초·5초·무제한으로
 * 달랐고, 신고·차단 완료 안내는 자동 해제가 아예 없어 닫기를 누르기 전까지 남았다.
 */

/** 알림이 화면에 머무는 시간. 모든 알림이 이 값을 쓴다. */
export const NOTICE_DISMISS_MS = 5_000;

/**
 * 알림 자리의 위치와 모양.
 *
 * <p>전역 알림(`NotificationCenter`)과 화면별 알림이 겹칠 수 있어 z-index를 나눈다. 전역이
 * `z-50`, 화면별이 `z-40`이다. 둘이 동시에 뜨면 매칭 상태 변화인 전역 알림이 위로 온다 —
 * 신고 접수 완료보다 매칭이 확정됐다는 사실이 먼저다.
 */
export const TOP_NOTICE_POSITION =
  'fixed left-1/2 top-4 w-[calc(100%-2.5rem)] max-w-[390px] -translate-x-1/2'
  + ' rounded-2xl px-4 py-3 shadow-lg';

/** 화면별 알림. 전역 알림은 `NotificationCenter`가 직접 그린다(레벨 색과 이동이 붙는다). */
export default function TopNotice({
  message,
  onClose,
}: {
  message?: string | null;
  onClose?: () => void;
}) {
  if (!message) return null;
  return (
    <div
      role="status"
      aria-live="polite"
      className={`${TOP_NOTICE_POSITION} z-40 bg-ink text-center text-[14px] font-semibold text-white`}
    >
      <span>{message}</span>
      {onClose && (
        <button type="button" onClick={onClose} className="ml-2 underline">
          닫기
        </button>
      )}
    </div>
  );
}
