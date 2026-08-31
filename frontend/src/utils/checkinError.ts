// GPS 체크인 API(POST /api/festivals/{festivalId}/checkin) 에러코드를 사용자 문구로 매핑한다.
// 여기 없는 코드나 네트워크/geolocation 오류는 서버·브라우저가 준 message를 그대로 보여준다.

import { ApiClientError } from '../api/apiClient';

const CHECKIN_ERROR_MESSAGES: Record<string, string> = {
  CHECKIN_OUT_OF_RANGE: '축제 반경을 벗어난 위치예요. 축제 현장 안에서 다시 시도해주세요.',
  LOW_LOCATION_ACCURACY: '위치 정확도가 낮아요. GPS가 잘 잡히는 곳에서 다시 시도해주세요.',
  FESTIVAL_LOCATION_UNAVAILABLE: '이 축제는 아직 위치 정보가 없어 체크인할 수 없어요.',
  NOT_FOUND: '축제 정보를 찾을 수 없어요.',
};

export function describeCheckinError(error: unknown): string {
  if (error instanceof ApiClientError) {
    if (error.code && CHECKIN_ERROR_MESSAGES[error.code]) {
      return CHECKIN_ERROR_MESSAGES[error.code];
    }
    return error.message;
  }
  return error instanceof Error ? error.message : '체크인에 실패했어요.';
}
