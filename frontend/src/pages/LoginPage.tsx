import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import MobileLayout from '../components/layout/MobileLayout';
import AccountRestrictionNotice from '../components/common/AccountRestrictionNotice';
import { sanctionNoticeApi } from '../api/sanctionNotice';
import type { SanctionNotice } from '../api/types';
import { getOAuthLoginPath, type OAuthProvider } from '../utils/oauth';

/** 제재로 접근이 막혔을 때 서버가 붙이는 값. 사유·기간은 URL이 아니라 notice cookie로 온다. */
const RESTRICTED_ERROR = 'account_restricted';

export default function LoginPage() {
  const [searchParams] = useSearchParams();
  const oauthError = searchParams.get('oauthError');
  const restricted = oauthError === RESTRICTED_ERROR;
  const [loadingProvider, setLoadingProvider] = useState<OAuthProvider | null>(null);
  const [sanction, setSanction] = useState<SanctionNotice | null>(null);

  useEffect(() => {
    const resetLoadingProvider = () => setLoadingProvider(null);

    window.addEventListener('pageshow', resetLoadingProvider);
    return () => window.removeEventListener('pageshow', resetLoadingProvider);
  }, []);

  // 제재 안내는 서버가 내려준 단기 cookie로만 조회된다. 조회에 실패하면 아래 포괄 안내가 남는다.
  useEffect(() => {
    if (!restricted) return;
    const controller = new AbortController();
    sanctionNoticeApi.getMine(controller.signal)
      .then((loaded) => {
        if (!controller.signal.aborted) setSanction(loaded);
      })
      .catch(() => { /* 안내를 못 읽어도 로그인 화면은 그대로 쓸 수 있어야 한다. */ });
    return () => controller.abort();
  }, [restricted]);

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
          {restricted
            ? (
              sanction
                ? <AccountRestrictionNotice notice={sanction} />
                : (
                  /*
                    안내 조회 전이거나 notice cookie가 만료된 경우의 포괄 문구.
                    제재와 탈퇴가 모두 이 화면으로 오므로 "제재"라고 단정하지 않는다.
                    탈퇴한 사용자에게 "계정이 제재되어"가 뜨는 것은 틀린 안내다.
                  */
                  <p role="alert" className="rounded-2xl bg-coral/10 px-4 py-3 text-sm text-coral">
                    이 계정으로는 지금 로그인할 수 없습니다. 자세한 사유는 고객센터로 문의해 주세요.
                  </p>
                )
            )
            : (
              <>
                {oauthError && (
                  <p role="alert" className="rounded-2xl bg-coral/10 px-4 py-3 text-sm text-coral">
                    소셜 로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.
                  </p>
                )}
                {/*
                  제재된 계정은 다시 로그인해도 같은 화면으로 돌아온다. 버튼을 남겨두면 사용자가
                  재시도만 반복하므로 제재 안내에서는 로그인 버튼을 감춘다.
                */}
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
              </>
            )}
        </div>

        {!restricted && (
          <p className="mt-5 text-center text-xs leading-5 text-ink/45">
            로그인 후 프로필 설정 단계에서 meet·or·solo의 이용약관과 개인정보 수집·이용에 동의하게 됩니다.
          </p>
        )}
      </main>
    </MobileLayout>
  );
}
