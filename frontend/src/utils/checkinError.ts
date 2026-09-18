// GPS 체크인 API(POST /api/festivals/{festivalId}/checkin) 에러코드를 사용자 문구로 매핑한다.
// 여기 없는 코드나 네트워크/geolocation 오류는 서버·브라우저가 준 message를 그대로 보여준다.

import { ApiClientError } from '../api/apiClient';

/**
 * 서버가 거리를 담아 보내는 코드.
 *
 * 반경 밖 거절은 서버만 "얼마나 떨어져 있는지"를 안다. 여기서 고정 문구로 덮으면 그 값이
 * 사라지고, 사용자는 GPS가 이상한 것인지 자기가 정말 먼 것인지 구분할 수 없다(docs/32 3.1).
 * 서버 message가 비어 있을 때를 대비해 아래 표에 기본 문구를 남겨 둔다.
 */
const SERVER_DETAILED_CODES = new Set(['CHECKIN_OUT_OF_RANGE']);

const CHECKIN_ERROR_MESSAGES: Record<string, string> = {
  CHECKIN_OUT_OF_RANGE: '축제 반경을 벗어난 위치예요. 축제 현장 안에서 다시 시도해주세요.',
  LOW_LOCATION_ACCURACY: '현재 위치를 정확히 확인하지 못했어요. 실내나 지하라면 밖에서 다시 시도해주세요.',
  FESTIVAL_LOCATION_UNAVAILABLE: '이 축제는 아직 위치 정보가 없어 체크인할 수 없어요.',
  NOT_FOUND: '축제 정보를 찾을 수 없어요.',
};

export function describeCheckinError(error: unknown): string {
  if (error instanceof ApiClientError) {
    if (error.code && SERVER_DETAILED_CODES.has(error.code) && error.message) {
      return error.message;
    }
    if (error.code && CHECKIN_ERROR_MESSAGES[error.code]) {
      return CHECKIN_ERROR_MESSAGES[error.code];
    }
    return error.message;
  }
  return error instanceof Error ? error.message : '체크인에 실패했어요.';
}
