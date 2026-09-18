import BrandPin from './BrandPin';

type SplashScreenProps = {
  /** 페이드아웃 중이면 `true`. 이때 아래 화면이 이미 마운트돼 있어 클릭을 통과시켜야 한다. */
  fadingOut: boolean;
};

/**
 * 앱 진입 첫 화면. 로고 애니메이션을 재생하는 동안 `SplashGate`가 세션을 확인한다.
 *
 * 워드마크는 이미지가 아니라 `LoginPage`와 같은 텍스트 구성으로 그린다. 미로그인 회원은 이
 * 화면 다음에 바로 로그인 화면을 보게 되는데, 같은 폰트·같은 색으로 워드마크가 이어지면
 * 페이드아웃이 화면 교체가 아니라 한 화면의 연속처럼 보인다.
 */
export default function SplashScreen({ fadingOut }: SplashScreenProps) {
  return (
    <div
      role="status"
      className={[
        'fixed inset-0 z-50 flex flex-col items-center justify-center gap-7 bg-sand',
        'transition-opacity duration-[260ms] ease-out motion-reduce:transition-none',
        fadingOut ? 'pointer-events-none opacity-0' : 'opacity-100',
      ].join(' ')}
    >
      <BrandPin className="h-56 w-auto" />
      <div className="flex animate-wordmark-rise items-baseline gap-1 text-3xl font-extrabold tracking-tight motion-reduce:animate-none">
        <span className="text-ink">meet</span>
        <span className="text-coral">·or·</span>
        <span className="text-ink">solo</span>
      </div>
    </div>
  );
}
