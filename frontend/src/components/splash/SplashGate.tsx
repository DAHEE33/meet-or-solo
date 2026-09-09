import { useEffect, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { probeSession } from '../../api/session';
import SplashScreen from './SplashScreen';
import {
  SPLASH_FADE_OUT_MS,
  SPLASH_MIN_VISIBLE_MS,
  markSplashSeen,
  readSplashSeen,
  resolveSplashTarget,
  shouldSkipSplash,
} from './splashPolicy';

type SplashPhase =
  /** 스플래시만 보이는 구간. 아래 화면은 아직 마운트하지 않는다. */
  | 'PLAYING'
  /** 목적지가 정해져 아래 화면을 마운트하고 오버레이를 걷어내는 구간. */
  | 'FADING'
  /** 스플래시가 완전히 사라진 상태. */
  | 'DONE';

const sleep = (ms: number) => new Promise<void>((resolve) => { setTimeout(resolve, ms); });

/**
 * 앱 진입 시 로고 스플래시를 재생하고 세션을 확인해 목적지를 정한다.
 *
 * `PLAYING` 구간에서 children을 마운트하지 않는 것이 중요하다. 마운트하면 `HomePage`가
 * 오버레이 뒤에서 GPS 권한 팝업을 띄우고 축제·체크인 API를 쏘기 시작한다. 대신 목적지가
 * 정해진 `FADING` 구간에서 children과 오버레이를 함께 렌더해 크로스페이드를 만든다.
 *
 * 최소 노출 시간과 세션 확인은 병렬로 돌린다. 이미 로그인된 회원이 진입마다 고정 지연을
 * 겪지 않으면서도 로고는 항상 한 번 온전히 보인다.
 */
export default function SplashGate({ children }: { children: ReactNode }) {
  const location = useLocation();
  const navigate = useNavigate();
  // 게이트 판정은 mount 시점의 경로로 한 번만 한다. 이후 화면 이동에서 다시 켜지면 안 된다.
  const [phase, setPhase] = useState<SplashPhase>(() => (
    shouldSkipSplash({ pathname: location.pathname, alreadyShown: readSplashSeen() })
      ? 'DONE'
      : 'PLAYING'
  ));

  useEffect(() => {
    if (phase !== 'PLAYING') return;

    let cancelled = false;
    const controller = new AbortController();

    Promise.all([probeSession(controller.signal), sleep(SPLASH_MIN_VISIBLE_MS)])
      .then(([probe]) => {
        if (cancelled) return;
        markSplashSeen();
        const target = resolveSplashTarget(probe);
        // 오버레이가 아직 덮고 있는 동안 이동시켜야 로그인 화면이 튀어나오지 않는다.
        if (target) navigate(target, { replace: true });
        setPhase('FADING');
      });

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [phase, navigate]);

  useEffect(() => {
    if (phase !== 'FADING') return;
    const timer = setTimeout(() => setPhase('DONE'), SPLASH_FADE_OUT_MS);
    return () => clearTimeout(timer);
  }, [phase]);

  return (
    <>
      {phase !== 'PLAYING' && children}
      {phase !== 'DONE' && <SplashScreen fadingOut={phase === 'FADING'} />}
    </>
  );
}
