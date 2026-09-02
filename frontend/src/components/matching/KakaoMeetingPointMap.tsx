import { useEffect, useRef, useState } from 'react';

/** 지도를 그리는 데 필요한 최소 정보. MatchGroupMeetingPoint(만남 장소)뿐 아니라
 * 축제/관광지 좌표(mapY=위도, mapX=경도)에서도 그대로 만들어 넘길 수 있다. */
export type KakaoMapPoint = {
  name: string;
  latitude: number;
  longitude: number;
};

export type KakaoLatLng = { getLat(): number; getLng(): number };
export type KakaoMap = { setCenter(position: unknown): void };
export type KakaoMarker = { setPosition(position: unknown): void };

/** Places.keywordSearch가 콜백으로 주는 결과 한 건. 관리자 만남 장소 검색이 쓴다. */
export type KakaoPlaceSearchItem = {
  id: string;
  place_name: string;
  address_name: string;
  road_address_name: string;
  x: string; // 경도(longitude), 문자열로 내려온다
  y: string; // 위도(latitude), 문자열로 내려온다
};

type KakaoPlacesService = {
  keywordSearch(
    keyword: string,
    callback: (data: KakaoPlaceSearchItem[], status: string) => void,
  ): void;
};

type KakaoMaps = {
  load(callback: () => void): void;
  LatLng: new (latitude: number, longitude: number) => KakaoLatLng;
  Map: new (container: HTMLElement, options: { center: unknown; level: number }) => KakaoMap;
  Marker: new (options: { map: unknown; position: unknown }) => KakaoMarker;
  event: {
    addListener(target: unknown, type: string, handler: (event: { latLng: KakaoLatLng }) => void): void;
  };
  services: {
    Places: new () => KakaoPlacesService;
    Status: { OK: string; ZERO_RESULT: string; ERROR: string };
  };
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
    // libraries=services — 좌표 선택기(components/admin/KakaoCoordinatePicker)의
    // 장소 검색(Places.keywordSearch)에 필요하다. 지도만 그리는 다른 화면에는 영향 없다.
    script.src =
      `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(appKey)}&autoload=false&libraries=services`;
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

export default function KakaoMeetingPointMap({ meetingPoint }: { meetingPoint: KakaoMapPoint }) {
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
