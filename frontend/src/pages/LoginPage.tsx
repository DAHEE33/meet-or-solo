import { useSearchParams } from 'react-router-dom';
import MobileLayout from '../components/layout/MobileLayout';

export default function LoginPage() {
  const [searchParams] = useSearchParams();
  const oauthError = searchParams.get('oauthError');

  const handleLogin = () => {
    window.location.href = '/api/auth/kakao/login';
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
              카카오 로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.
            </p>
          )}
          <button
            type="button"
            onClick={handleLogin}
            className="h-14 w-full rounded-2xl bg-[#FEE500] px-5 text-[15px] font-bold text-black transition-transform active:scale-[0.99]"
          >
            카카오로 시작하기
          </button>
        </div>

        <p className="mt-5 text-center text-xs leading-5 text-ink/45">
          계속 진행하면 meet·or·solo의 이용약관 및 개인정보처리방침에 동의하는 것으로 간주됩니다.
        </p>
      </main>
    </MobileLayout>
  );
}
