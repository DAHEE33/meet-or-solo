import { useEffect, useRef, useState } from 'react';
import type { MatchGroupMeetingPoint } from '../../api/matching';

type KakaoMaps = {
  load(callback: () => void): void;
  LatLng: new (latitude: number, longitude: number) => unknown;
  Map: new (container: HTMLElement, options: { center: unknown; level: number }) => unknown;
  Marker: new (options: { map: unknown; position: unknown }) => unknown;
};

declare global {
  interface Window {
    kakao?: { maps: KakaoMaps };
  }
}

const SCRIPT_ID = 'kakao-maps-sdk';
let sharedSdkPromise: Promise<KakaoMaps> | null = null;

export function loadKakaoMaps(appKey: string): Promise<KakaoMaps> {
  if (window.kakao?.maps) {
    return new Promise((resolve) => window.kakao!.maps.load(() => resolve(window.kakao!.maps)));
  }
  if (sharedSdkPromise) return sharedSdkPromise;

  const staleScript = document.getElementById(SCRIPT_ID);
  staleScript?.remove();

  const loadingPromise = new Promise<KakaoMaps>((resolve, reject) => {
    const script = document.createElement('script');
    const fail = () => {
      script.remove();
      reject(new Error('Kakao Maps SDK 로드 실패'));
    };
    const ready = () => {
      if (!window.kakao?.maps) {
        fail();
        return;
      }
      window.kakao.maps.load(() => resolve(window.kakao!.maps));
    };
    script.id = SCRIPT_ID;
    script.async = true;
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(appKey)}&autoload=false`;
    script.addEventListener('load', ready, { once: true });
    script.addEventListener('error', fail, { once: true });
    document.head.appendChild(script);
  });

  sharedSdkPromise = loadingPromise.catch((error: unknown) => {
    sharedSdkPromise = null;
    throw error;
  });
  return sharedSdkPromise;
}

export function resetKakaoMapsLoaderForTest() {
  sharedSdkPromise = null;
  document.getElementById(SCRIPT_ID)?.remove();
}

export default function KakaoMeetingPointMap({ meetingPoint }: { meetingPoint: MatchGroupMeetingPoint }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const appKey = import.meta.env.VITE_KAKAO_MAPS_APP_KEY;
    if (!appKey) {
      setFailed(true);
      return;
    }
    let active = true;
    void loadKakaoMaps(appKey).then((maps) => {
      if (!active || !containerRef.current) return;
      const position = new maps.LatLng(meetingPoint.latitude, meetingPoint.longitude);
      const map = new maps.Map(containerRef.current, { center: position, level: 3 });
      new maps.Marker({ map, position });
    }).catch(() => {
      if (active) setFailed(true);
    });
    return () => { active = false; };
  }, [meetingPoint.latitude, meetingPoint.longitude]);

  if (failed) {
    return <KakaoMapFallback />;
  }
  return <div ref={containerRef} aria-label={`${meetingPoint.name} 지도`} className="h-48 overflow-hidden rounded-2xl bg-sand" />;
}

export function KakaoMapFallback() {
  return <p role="status" className="rounded-2xl bg-sand/60 px-4 py-6 text-center text-[13px] text-ink/55">지도를 불러오지 못했어요. 장소명과 주소를 확인해주세요.</p>;
}
