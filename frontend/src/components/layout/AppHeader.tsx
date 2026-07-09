import { Bell } from 'lucide-react';

export default function AppHeader() {
  return (
    <header className="sticky top-0 z-20 flex items-center justify-between bg-sand/90 px-5 pb-2 pt-4 backdrop-blur">
      <div className="flex items-baseline gap-1">
        <span className="text-lg font-extrabold tracking-tight text-ink">meet</span>
        <span className="text-lg font-extrabold text-coral">·or·</span>
        <span className="text-lg font-extrabold tracking-tight text-ink">solo</span>
      </div>
      <button
        type="button"
        aria-label="알림"
        className="flex h-11 w-11 items-center justify-center rounded-full text-ink active:bg-black/5"
      >
        <Bell size={22} strokeWidth={1.8} />
      </button>
    </header>
  );
}
