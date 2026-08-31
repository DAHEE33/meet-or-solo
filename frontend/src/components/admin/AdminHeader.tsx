/** 관리자 화면 공통 상단 타이틀 — "meet·or·solo" 브랜드와 화면별 부제를 함께 보여준다. */
export default function AdminHeader({ title }: { title: string }) {
  return (
    <header className="border-b border-line bg-white px-6 py-4">
      <h1 className="text-lg font-bold text-ink">
        meet·or·solo <span className="ml-2 text-sm font-medium text-ink/45">{title}</span>
      </h1>
    </header>
  );
}
