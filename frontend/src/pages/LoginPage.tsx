import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import MobileLayout from '../components/layout/MobileLayout';
import { getOAuthLoginPath, type OAuthProvider } from '../utils/oauth';

export default function LoginPage() {
  const [searchParams] = useSearchParams();
  const oauthError = searchParams.get('oauthError');
  const [loadingProvider, setLoadingProvider] = useState<OAuthProvider | null>(null);

  useEffect(() => {
    const resetLoadingProvider = () => setLoadingProvider(null);

    window.addEventListener('pageshow', resetLoadingProvider);
    return () => window.removeEventListener('pageshow', resetLoadingProvider);
  }, []);

  const handleLogin = (provider: OAuthProvider) => {
    if (loadingProvider) return;
    setLoadingProvider(provider);
    window.location.href = getOAuthLoginPath(provider);
  };

  return (
    <MobileLayout showTabBar={false}>
      <main className="flex min-h-screen flex-col justify-center px-6 pb-12 pt-10">
        <div className="flex flex-col gap-2">
          <div className="flex items-baseline gap-1 text-2xl font-extrabold tracking-tight">
            <span className="text-ink">meet</span>
            <span className="text-coral">·or·</span>
            <span className="text-ink">solo</span>
          </div>
          <p className="text-[15px] text-ink/60">혼자 온 여행, 함께가 될 수도 있으니까</p>
        </div>

        <div className="mt-10 flex flex-col gap-3">
          {oauthError && (
            <p role="alert" className="rounded-2xl bg-coral/10 px-4 py-3 text-sm text-coral">
              소셜 로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.
            </p>
          )}
          <button
            type="button"
            onClick={() => handleLogin('kakao')}
            disabled={loadingProvider !== null}
            aria-label="카카오로 로그인"
            className="h-14 w-full rounded-2xl bg-[#FEE500] px-5 text-[15px] font-bold text-black transition-transform active:scale-[0.99]"
          >
            {loadingProvider === 'kakao' ? '카카오로 이동 중...' : '카카오로 시작하기'}
          </button>
          <button
            type="button"
            onClick={() => handleLogin('naver')}
            disabled={loadingProvider !== null}
            aria-label="네이버로 로그인"
            className="h-14 w-full rounded-2xl bg-[#03C75A] px-5 text-[15px] font-bold text-white transition-transform active:scale-[0.99] disabled:opacity-70"
          >
            {loadingProvider === 'naver' ? '네이버로 이동 중...' : '네이버로 시작하기'}
          </button>
        </div>

        <p className="mt-5 text-center text-xs leading-5 text-ink/45">
          계속 진행하면 meet·or·solo의 이용약관 및 개인정보처리방침에 동의하는 것으로 간주됩니다.
        </p>
      </main>
    </MobileLayout>
  );
}
