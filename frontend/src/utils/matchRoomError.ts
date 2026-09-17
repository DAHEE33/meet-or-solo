// 상태방(MatchRoomPage)에서 버튼을 눌렀다가 실패했을 때 보여 줄 문구를 만든다.
//
// 체크인에는 `checkinError.ts`가 있었지만 상태방에는 같은 자리가 없었다. 그래서 도착 인증이
// 반경 밖으로 거절돼도 화면에는 아무것도 뜨지 않거나, "도착 예정 시간을 저장하지 못했어요"라는
// 엉뚱한 문구가 떴다(docs/32 3.1).

import { ApiClientError } from '../api/apiClient';
import { GeolocationError } from './geolocation';

/** 어떤 버튼이 실패했는지. 같은 `actionError` 자리를 여러 버튼이 함께 쓰기 때문에 구분한다. */
export type MatchRoomAction = 'ARRIVAL_TIME' | 'ARRIVE' | 'CANCEL' | 'LEAVE';

/**
 * 서버가 거리를 담아 보내는 코드. 고정 문구로 덮으면 "얼마나 떨어져 있는지"가 사라진다.
 * 체크인의 `CHECKIN_OUT_OF_RANGE`와 같은 이유다.
 */
const SERVER_DETAILED_CODES = new Set(['MATCHING_ARRIVAL_OUT_OF_RANGE']);

const CODE_MESSAGES: Record<string, string> = {
  MATCHING_ARRIVAL_OUT_OF_RANGE: '만남 장소 근처에서 도착을 인증해주세요.',
  MATCHING_ARRIVAL_DEADLINE_EXCEEDED: '도착 마감 시간이 지났어요.',
  MATCHING_LEAVE_NOT_ALLOWED: '지금은 먼저 나갈 수 없어요.',
  MATCHING_MEETING_POINT_NOT_READY: '만남 장소가 아직 준비되지 않았어요.',
  MATCHING_COMPLETION_LOCKED: '방금 만남이 끝나 잠시 기다려야 해요.',
};

/** 코드가 없을 때 쓰는 행동별 기본 문구. */
const ACTION_FALLBACK: Record<MatchRoomAction, string> = {
  ARRIVAL_TIME: '도착 예정 시간을 저장하지 못했어요. 다시 선택해주세요.',
  ARRIVE: '도착을 인증하지 못했어요. 잠시 후 다시 시도해주세요.',
  CANCEL: '매칭을 취소하지 못했어요. 잠시 후 다시 시도해주세요.',
  LEAVE: '만남에서 나가지 못했어요. 잠시 후 다시 시도해주세요.',
};

export function describeMatchRoomError(error: unknown, action: MatchRoomAction): string {
  if (error instanceof ApiClientError) {
    if (error.code && SERVER_DETAILED_CODES.has(error.code) && error.message) {
      return error.message;
    }
    if (error.code && CODE_MESSAGES[error.code]) {
      return CODE_MESSAGES[error.code];
    }
    return error.message || ACTION_FALLBACK[action];
  }
  // 도착 인증은 API를 부르기 전에 브라우저 위치 조회가 먼저 실패할 수 있다. 그때는
  // geolocation이 만든 안내(권한·시간 초과)가 서버 오류보다 정확하다.
  //
  // 반대로 일반 Error의 message는 화면에 내보내지 않는다. fetch 실패는 `network` 같은 값이라
  // 사용자에게 아무 의미가 없다.
  if (error instanceof GeolocationError && error.message) return error.message;
  return ACTION_FALLBACK[action];
}
