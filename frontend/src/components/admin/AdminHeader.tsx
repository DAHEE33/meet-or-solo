import { useState } from 'react';
import { adminAuthApi } from '../../api/adminAuth';

/** 관리자 화면 공통 상단 타이틀 — "혼자왔니" 브랜드와 화면별 부제를 함께 보여준다. */
export default function AdminHeader({ title }: { title: string }) {
  const [loggingOut, setLoggingOut] = useState(false);

  /**
   * 로그아웃은 소셜·ID/PW 어느 경로로 들어왔든 같은 endpoint를 쓴다(docs/30).
   * 서버가 인증 여부와 무관하게 cookie 만료를 내려주므로 실패해도 로그인 화면으로 보낸다.
   */
  const logout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    try {
      await adminAuthApi.logout();
    } catch {
      // 무시한다. cookie가 남아 있어도 다음 요청이 401로 다시 로그인 화면으로 보낸다.
    }
    window.location.replace('/admin/login');
  };

  return (
    <header className="flex items-center justify-between border-b border-line bg-white px-6 py-4">
      <h1 className="text-lg font-bold text-ink">
        혼자<span className="text-coral">왔니</span> <span className="ml-2 text-sm font-medium text-ink/45">{title}</span>
      </h1>
      <button
        type="button"
        onClick={() => void logout()}
        disabled={loggingOut}
        className="rounded-xl border border-line px-3 py-1.5 text-sm font-semibold text-ink/70 disabled:opacity-50"
      >
        {loggingOut ? '로그아웃 중...' : '로그아웃'}
      </button>
    </header>
  );
}
