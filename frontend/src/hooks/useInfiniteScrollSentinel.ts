import { useEffect, useRef } from 'react';

// 목록 끝에 둔 빈 요소가 화면에 들어오면 다음 페이지를 요청한다.
// scroll 이벤트 대신 IntersectionObserver를 쓰는 이유는 스크롤마다 콜백이 쏟아지지 않고
// 브라우저가 교차 여부만 알려주기 때문이다.
//
// 중복 요청 방지는 여기서 하지 않고 useInfiniteList의 loadMore가 담당한다 — observer는
// 요소가 보이는 동안 여러 번 호출될 수 있다.

/**
 * @param onIntersect 화면에 들어왔을 때 호출할 콜백
 * @param enabled false면 관찰하지 않는다(더 받을 페이지가 없을 때 등)
 */
export function useInfiniteScrollSentinel(onIntersect: () => void, enabled: boolean) {
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const callbackRef = useRef(onIntersect);
  callbackRef.current = onIntersect;

  useEffect(() => {
    const element = sentinelRef.current;
    // IntersectionObserver가 없는 환경(테스트의 node 환경 등)에서는 조용히 아무것도 하지 않는다.
    if (!enabled || !element || typeof IntersectionObserver === 'undefined') return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) callbackRef.current();
      },
      // 목록 끝에 완전히 닿기 전에 미리 불러와 스크롤이 끊기지 않게 한다.
      { rootMargin: '200px' },
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, [enabled]);

  return sentinelRef;
}
