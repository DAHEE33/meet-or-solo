import { AlertTriangle } from 'lucide-react';
import type { SanctionNotice } from '../../api/types';
import { formatSeoulDateTime } from '../../utils/dateTime';

type Props = {
  /** 제재 안내. 이용정지 중 탈퇴는 잔여 기간이 이어지므로 그 사실을 먼저 알린다. */
  sanction: SanctionNotice | null;
  submitting: boolean;
  errorMessage: string | null;
  onClose: () => void;
  onConfirm: () => void;
};

/**
 * 회원 탈퇴 확인 dialog(docs/19 4.4).
 *
 * 되돌릴 수 없는 조치라 버튼 한 번으로 실행하지 않고 결과를 먼저 나열한다.
 *
 * 이용정지 중인 회원에게는 잔여 정지 기간이 탈퇴로 사라지지 않는다는 것을 명시한다.
 * 이 안내가 없으면 사용자가 탈퇴를 제재 해제 수단으로 오해하고 누른다.
 */
export default function WithdrawalConfirmDialog({
  sanction,
  submitting,
  errorMessage,
  onClose,
  onConfirm,
}: Props) {
  const suspended = sanction?.status === 'SUSPENDED';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/60 p-5">
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="withdrawal-confirm-title"
        className="max-h-[85vh] w-full max-w-sm overflow-y-auto rounded-3xl bg-white p-6"
      >
        <div className="flex items-center gap-2">
          <AlertTriangle aria-hidden className="h-5 w-5 text-coral" />
          <h2 id="withdrawal-confirm-title" className="text-[16px] font-bold text-ink">
            정말 탈퇴하시겠어요?
          </h2>
        </div>

        <p className="mt-3 text-[13px] leading-5 text-ink/60">
          탈퇴하면 되돌릴 수 없어요. 아래 내용을 확인해 주세요.
        </p>

        <ul className="mt-4 flex flex-col gap-2 text-[13px] leading-5 text-ink/70">
          <li>· 닉네임, 이메일, 소개, 프로필 사진이 삭제돼요.</li>
          <li>· 작성한 댓글이 보이지 않게 되고, 찜과 취향 정보는 삭제돼요.</li>
          <li>· 진행 중인 체크인과 동행 매칭은 취소돼요.</li>
          <li>· 탈퇴 후 7일 동안은 같은 계정으로 다시 가입할 수 없어요.</li>
          <li>· 신고와 제재 기록은 안전을 위해 보관돼요.</li>
        </ul>

        {/*
          이용정지 중 탈퇴가 제재 회피로 쓰이지 않는다는 것을 먼저 알린다.
          잔여 기간은 members의 탈퇴 스냅샷에 남고 재가입 시 이어진다(Member.rejoin).
        */}
        {suspended && (
          <p
            role="note"
            className="mt-4 rounded-xl border border-coral bg-coral/5 px-4 py-3 text-[13px] leading-5 text-ink"
          >
            <span className="font-bold">남은 이용정지 기간은 탈퇴로 사라지지 않아요.</span>
            <br />
            지금 탈퇴해도 정지가 해제되지 않고, 다시 가입하면 남은 기간
            {sanction?.suspendedUntil
              ? ` (${formatSeoulDateTime(sanction.suspendedUntil)}까지)`
              : ''}
            이 이어서 적용돼요.
          </p>
        )}

        {errorMessage && (
          <p role="alert" aria-live="assertive" className="mt-4 text-[13px] font-semibold text-coral">
            {errorMessage}
          </p>
        )}

        <div className="mt-6 grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="rounded-2xl border border-line bg-white py-3.5 text-[14px] font-semibold text-ink disabled:opacity-60"
          >
            취소
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={submitting}
            className="rounded-2xl bg-coral py-3.5 text-[14px] font-bold text-white disabled:opacity-60"
          >
            {submitting ? '탈퇴 처리 중...' : '탈퇴하기'}
          </button>
        </div>
      </section>
    </div>
  );
}
