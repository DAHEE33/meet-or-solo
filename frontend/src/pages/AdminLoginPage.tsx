import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { adminAuthApi } from '../api/adminAuth';
import Wordmark from '../components/brand/Wordmark';

/**
 * 슈퍼관리자 ID/PW 로그인 화면(docs/30).
 *
 * 소셜 로그인을 대체하지 않는 두 번째 진입 경로다. 일반 `/login` 화면에는 이 경로로 가는
 * 링크를 두지 않는다.
 *
 * 실패 사유를 화면에서도 구분하지 않는다. 서버가 아이디 오류·비밀번호 오류·잠금을 같은
 * 응답으로 내려주므로, 화면이 추측해서 다르게 안내하면 그 구분이 되살아난다.
 */
const FAILURE_MESSAGE = '아이디 또는 비밀번호가 올바르지 않습니다.';

export default function AdminLoginPage() {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [failed, setFailed] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (submitting || !username.trim() || !password) return;
    setSubmitting(true);
    setFailed(false);
    try {
      await adminAuthApi.login(username.trim(), password);
      // replace로 이동한다. 뒤로 가기로 로그인 화면에 돌아오면 이미 session이 있어 혼란스럽다.
      navigate('/admin', { replace: true });
    } catch {
      setFailed(true);
      setPassword('');
      setSubmitting(false);
    }
  };

  return (
    <main className="flex min-h-screen items-center justify-center bg-sand p-6">
      <section className="w-full max-w-sm rounded-3xl bg-white p-8">
        <h1 className="flex items-baseline text-ink">
          <Wordmark className="text-2xl font-extrabold" />
          <span className="ml-1 text-sm font-medium text-ink/45">관리자</span>
        </h1>
        <p className="mt-2 text-sm text-ink/60">관리자 계정으로 로그인하세요.</p>

        <form onSubmit={(event) => void submit(event)} className="mt-6 grid gap-3">
          <label className="block text-sm font-semibold text-ink">
            아이디
            <input
              type="text"
              name="username"
              autoComplete="username"
              value={username}
              maxLength={50}
              onChange={(event) => setUsername(event.target.value)}
              className="mt-1 w-full rounded-xl border border-line px-3 py-2 font-normal"
            />
          </label>
          <label className="block text-sm font-semibold text-ink">
            비밀번호
            <input
              type="password"
              name="password"
              autoComplete="current-password"
              value={password}
              maxLength={200}
              onChange={(event) => setPassword(event.target.value)}
              className="mt-1 w-full rounded-xl border border-line px-3 py-2 font-normal"
            />
          </label>

          {failed && (
            <p role="alert" aria-live="assertive" className="text-sm text-coral">
              {FAILURE_MESSAGE}
            </p>
          )}

          <button
            type="submit"
            disabled={submitting || !username.trim() || !password}
            className="mt-2 rounded-xl bg-coral py-3 font-bold text-white disabled:opacity-50"
          >
            {submitting ? '로그인 중...' : '로그인'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-ink/50">
          소셜 계정에 관리자 권한이 있다면{' '}
          <a href="/login" className="font-semibold underline">
            일반 로그인
          </a>
          으로도 들어올 수 있습니다.
        </p>
      </section>
    </main>
  );
}
