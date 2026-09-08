import { useEffect, useRef, useState } from 'react';
import { loadKakaoMaps, type KakaoMap, type KakaoMarker } from '../matching/KakaoMeetingPointMap';

// 만남 장소 등록/수정 폼의 좌표 미세조정용 지도. 검색(KakaoPlaceSearch)으로 채운 좌표를
// 눈으로 확인하고, 정확한 지점이 아니면 지도를 클릭해 옮길 수 있다 — 위도/경도 숫자 입력은
// 그대로 남겨둔 fallback이라, 이 지도가 못 뜨거나 원하는 위치가 검색에 안 걸려도 등록엔 문제없다.

// 위경도가 아직 없는 값(0,0 — Null Island)일 때 쓰는 기본 중심점. 강원도 축제 서비스라
// 춘천을 기준으로 잡아, 신규 등록마다 태평양 한가운데서 지도를 옮겨야 하는 상황을 피한다.
const DEFAULT_CENTER = { latitude: 37.8813, longitude: 127.73 };

/** 폼에 값이 아직 없으면(0,0) 기본 중심점을, 있으면 그 값을 그대로 지도 중심으로 쓴다. */
export function resolveCenter(latitude: number, longitude: number): { latitude: number; longitude: number } {
  return latitude !== 0 || longitude !== 0 ? { latitude, longitude } : DEFAULT_CENTER;
}

export default function KakaoCoordinatePicker({
  latitude, longitude, onPick,
}: {
  latitude: number;
  longitude: number;
  onPick: (latitude: number, longitude: number) => void;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapsRef = useRef<Awaited<ReturnType<typeof loadKakaoMaps>> | null>(null);
  const mapRef = useRef<KakaoMap | null>(null);
  const markerRef = useRef<KakaoMarker | null>(null);
  const onPickRef = useRef(onPick);
  onPickRef.current = onPick;
  const [failed, setFailed] = useState(false);

  const hasCoordinates = latitude !== 0 || longitude !== 0;
  const center = resolveCenter(latitude, longitude);

  // 지도/마커는 한 번만 만든다 — 클릭할 때마다 다시 만들면 그 순간 이전 위치로 튕겨 보인다.
  useEffect(() => {
    const appKey = import.meta.env.VITE_KAKAO_MAPS_APP_KEY;
    if (!appKey) {
      setFailed(true);
      return;
    }
    let active = true;
    void loadKakaoMaps(appKey).then((maps) => {
      if (!active || !containerRef.current) return;
      mapsRef.current = maps;
      const position = new maps.LatLng(center.latitude, center.longitude);
      const map = new maps.Map(containerRef.current, { center: position, level: 4 });
      const marker = new maps.Marker({ map, position });
      mapRef.current = map;
      markerRef.current = marker;
      maps.event.addListener(map, 'click', (event) => {
        const lat = event.latLng.getLat();
        const lng = event.latLng.getLng();
        marker.setPosition(event.latLng);
        onPickRef.current(lat, lng);
      });
    }).catch(() => {
      if (active) setFailed(true);
    });
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 검색 결과 선택이나 숫자 필드 수동 입력으로 좌표가 바뀌면 지도/마커를 따라 옮긴다.
  useEffect(() => {
    const maps = mapsRef.current;
    const map = mapRef.current;
    const marker = markerRef.current;
    if (!maps || !map || !marker) return;
    const position = new maps.LatLng(center.latitude, center.longitude);
    map.setCenter(position);
    marker.setPosition(position);
  }, [center.latitude, center.longitude]);

  if (failed) {
    return (
      <p className="rounded-xl bg-sand/60 px-3 py-4 text-center text-xs text-ink/55">
        지도를 불러오지 못했어요. 위도/경도를 직접 입력해주세요.
      </p>
    );
  }
  return (
    <div>
      <div
        ref={containerRef}
        aria-label="좌표 선택 지도"
        className="h-40 w-full overflow-hidden rounded-xl bg-sand"
      />
      <p className="mt-1 text-xs text-ink/45">
        {hasCoordinates ? '지도를 클릭하면 그 위치로 좌표가 바뀌어요.' : '검색하거나 지도를 클릭해 위치를 지정하세요.'}
      </p>
    </div>
  );
}
