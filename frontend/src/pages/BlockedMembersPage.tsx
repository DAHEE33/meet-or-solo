import { useEffect, useRef, type RefObject } from 'react';
import { X } from 'lucide-react';
import MobileLayout from '../components/layout/MobileLayout';
import PageHeader from '../components/layout/PageHeader';
import { type MemberBlock } from '../api/memberBlocks';
import { useMemberBlocks, type MemberBlocksState } from '../hooks/useMemberBlocks';
import { formatSeoulDateTime } from '../utils/dateTime';

export default function BlockedMembersPage() {
  const { state, reload, open, close, clearSuccess, submit } = useMemberBlocks();
  const openerRef = useRef<HTMLButtonElement | null>(null);
  return (
    <MobileLayout>
      <PageHeader title="차단 회원 관리" />
      <main className="flex flex-col gap-3 px-5 pb-10" aria-busy={state.status === 'LOADING'}>
        <p className="text-[13px] leading-5 text-ink/55">내가 차단한 회원만 표시됩니다.</p>
        <div className="sr-only" role="status" aria-live="polite">
          {state.status === 'LOADING' ? '차단 회원 목록을 불러오는 중입니다.' : state.successMessage ?? ''}
        </div>
        {state.status === 'LOADING' && <p className="rounded-2xl bg-white p-5 text-sm text-ink/60">불러오는 중...</p>}
        {state.status === 'ERROR' && (
          <section role="alert" className="rounded-2xl bg-white p-5">
            <p className="text-sm text-coral">차단 회원 목록을 불러오지 못했어요.</p>
            <button type="button" onClick={() => void reload()} className="mt-3 rounded-xl border border-line px-4 py-2 text-sm font-semibold">다시 시도</button>
          </section>
        )}
        {state.status === 'READY' && state.blocks.length === 0 && (
          <p className="rounded-2xl bg-white p-5 text-center text-sm text-ink/60">차단한 회원이 없어요</p>
        )}
        {state.status === 'READY' && state.blocks.map((block) => (
          <article key={block.blockedMemberId} className="flex items-center gap-3 rounded-2xl bg-white p-4 shadow-sm">
            {block.profileImageUrl ? <img src={block.profileImageUrl} alt="" className="h-12 w-12 rounded-full object-cover" />
              : <div aria-hidden="true" className="flex h-12 w-12 items-center justify-center rounded-full bg-coral/10 font-bold text-coral">{block.nickname.slice(0, 1)}</div>}
            <div className="min-w-0 flex-1">
              <h2 className="truncate text-[15px] font-bold text-ink">{block.nickname}</h2>
              <time dateTime={block.blockedAt} className="text-[12px] text-ink/50">차단 {formatSeoulDateTime(block.blockedAt)}</time>
            </div>
            <button type="button" ref={state.target?.blockedMemberId === block.blockedMemberId ? openerRef : undefined}
              onClick={(event) => { openerRef.current = event.currentTarget; open(block); }}
              className="rounded-xl border border-line px-3 py-2 text-[13px] font-semibold text-ink/70"
              aria-label={`${block.nickname}님 차단 해제`}>해제</button>
          </article>
        ))}
        {state.successMessage && <div role="status" aria-live="polite" className="rounded-2xl bg-ink px-4 py-3 text-sm text-white">{state.successMessage}<button type="button" className="ml-2 underline" onClick={clearSuccess}>닫기</button></div>}
      </main>
      {state.target && <UnblockDialog state={state} onClose={() => { close(); queueMicrotask(() => openerRef.current?.focus()); }} onSubmit={() => void submit()} />}
    </MobileLayout>
  );
}

export function UnblockDialog({ state, onClose, onSubmit }: { state: MemberBlocksState; onClose: () => void; onSubmit: () => void }) {
  const dialogRef = useRef<HTMLElement>(null);
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    const focusables = () => Array.from(dialog.querySelectorAll<HTMLElement>('button:not(:disabled)'));
    focusables()[0]?.focus();
    const keydown = (event: KeyboardEvent) => {
      handleUnblockDialogKeyDown(event, focusables(), document.activeElement, state.submitting, onClose);
    };
    document.addEventListener('keydown', keydown);
    return () => document.removeEventListener('keydown', keydown);
  }, [onClose, state.submitting]);
  return <UnblockDialogContent state={state} onClose={onClose} onSubmit={onSubmit} dialogRef={dialogRef} />;
}

export function UnblockDialogContent({ state, onClose, onSubmit, dialogRef }: { state: MemberBlocksState; onClose: () => void; onSubmit: () => void; dialogRef?: RefObject<HTMLElement> }) {
  const target = state.target as MemberBlock;
  return <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink/45 sm:items-center sm:p-5">
    <section ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="unblock-title" aria-describedby="unblock-description" className="w-full max-w-[430px] rounded-t-3xl bg-white p-5 sm:rounded-3xl">
      <div className="flex justify-between gap-3"><div><h2 id="unblock-title" className="text-lg font-bold">{target.nickname}님의 차단을 해제할까요?</h2><p id="unblock-description" className="mt-1 text-sm text-ink/60">차단 해제 전에 아래 내용을 확인해주세요.</p></div><button type="button" aria-label="차단 해제 창 닫기" disabled={state.submitting} onClick={onClose}><X aria-hidden="true" /></button></div>
      <ul className="mt-5 list-disc space-y-2 rounded-2xl bg-sand/50 py-4 pl-9 pr-4 text-sm leading-6 text-ink/75"><li>차단을 해제하면 향후 다시 매칭될 수 있습니다.</li><li>현재 진행 중인 MatchRoom은 즉시 변경되지 않습니다.</li><li>해제 사실은 상대에게 알려지지 않습니다.</li></ul>
      {state.error && <p role="alert" aria-live="assertive" className="mt-3 rounded-2xl bg-coral/10 p-3 text-sm text-coral">차단을 해제하지 못했어요. 다시 시도해주세요.</p>}
      <div className="mt-5 grid grid-cols-2 gap-2"><button type="button" disabled={state.submitting} onClick={onClose} className="rounded-2xl border border-line py-3 disabled:opacity-50">취소</button><button type="button" disabled={state.submitting} onClick={onSubmit} className="rounded-2xl bg-coral py-3 font-bold text-white disabled:opacity-50">{state.submitting ? '해제 중...' : '차단 해제'}</button></div>
    </section>
  </div>;
}

export function handleUnblockDialogKeyDown(
  event: Pick<KeyboardEvent, 'key' | 'shiftKey' | 'preventDefault'>,
  focusables: HTMLElement[],
  activeElement: Element | null,
  submitting: boolean,
  onClose: () => void,
) {
  if (event.key === 'Escape' && !submitting) { event.preventDefault(); onClose(); return; }
  if (event.key !== 'Tab' || focusables.length === 0) return;
  const first = focusables[0]; const last = focusables[focusables.length - 1];
  if (event.shiftKey && activeElement === first) { event.preventDefault(); last.focus(); }
  else if (!event.shiftKey && activeElement === last) { event.preventDefault(); first.focus(); }
}
