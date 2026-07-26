export type GeolocationResult = {
  latitude: number;
  longitude: number;
  accuracyMeters: number;
};

/** 브라우저 Geolocation API를 한 번만 호출해 현재 위치를 읽는다(연속 추적 없음). */
export function getCurrentPosition(): Promise<GeolocationResult> {
  return new Promise((resolve, reject) => {
    if (!('geolocation' in navigator)) {
      reject(new Error('이 브라우저에서는 위치 확인을 지원하지 않아요.'));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        resolve({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          accuracyMeters: Math.round(position.coords.accuracy),
        });
      },
      (error) => reject(new Error(geolocationErrorMessage(error))),
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 },
    );
  });
}

function geolocationErrorMessage(error: GeolocationPositionError): string {
  switch (error.code) {
    case error.PERMISSION_DENIED:
      return '위치 권한이 필요해요. 브라우저 설정에서 위치 권한을 허용해주세요.';
    case error.POSITION_UNAVAILABLE:
      return '위치 정보를 가져올 수 없어요.';
    case error.TIMEOUT:
      return '위치 확인이 시간 초과됐어요. 다시 시도해주세요.';
    default:
      return '위치를 확인하는 중 오류가 발생했어요.';
  }
}
