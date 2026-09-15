import { describe, expect, it } from 'vitest';
import { ApiClientError } from '../api/apiClient';
import { GeolocationError } from './geolocation';
import { describeMatchRoomError } from './matchRoomError';

describe('describeMatchRoomError', () => {
  // 거리는 서버만 안다. 고정 문구로 덮으면 "얼마나 떨어져 있는지"가 사라진다(docs/32 3.1).
  it('도착 반경 밖 거절은 거리가 담긴 서버 message를 그대로 쓴다', () => {
    const error = new ApiClientError(
      '만남 장소에서 약 1.2km 떨어져 있어요. 150m 안에서 도착을 인증할 수 있어요.',
      409,
      'MATCHING_ARRIVAL_OUT_OF_RANGE',
      undefined,
    );
    expect(describeMatchRoomError(error, 'ARRIVE')).toBe(
      '만남 장소에서 약 1.2km 떨어져 있어요. 150m 안에서 도착을 인증할 수 있어요.',
    );
  });

  it('서버 message가 비면 코드별 기본 문구로 돌아간다', () => {
    expect(
      describeMatchRoomError(new ApiClientError('', 409, 'MATCHING_ARRIVAL_OUT_OF_RANGE', undefined), 'ARRIVE'),
    ).toBe('만남 장소 근처에서 도착을 인증해주세요.');
    expect(
      describeMatchRoomError(new ApiClientError('원본', 409, 'MATCHING_ARRIVAL_DEADLINE_EXCEEDED', undefined), 'ARRIVE'),
    ).toBe('도착 마감 시간이 지났어요.');
  });

  it('알 수 없는 코드는 서버 message를, 그것도 없으면 행동별 기본 문구를 쓴다', () => {
    expect(
      describeMatchRoomError(new ApiClientError('서버가 준 메시지', 409, 'MATCHING_CONFLICT', undefined), 'CANCEL'),
    ).toBe('서버가 준 메시지');
    expect(describeMatchRoomError(new ApiClientError('', 500, null, undefined), 'LEAVE')).toBe(
      '만남에서 나가지 못했어요. 잠시 후 다시 시도해주세요.',
    );
  });

  // 도착은 API 호출 전에 브라우저 위치 조회가 먼저 실패할 수 있다.
  it('geolocation 오류는 브라우저 안내를 그대로 쓴다', () => {
    expect(
      describeMatchRoomError(
        new GeolocationError('위치 권한이 필요해요. 브라우저 설정에서 위치 권한을 허용해주세요.'),
        'ARRIVE',
      ),
    ).toBe('위치 권한이 필요해요. 브라우저 설정에서 위치 권한을 허용해주세요.');
  });

  // fetch 실패의 `network` 같은 값은 사용자에게 아무 의미가 없다.
  it('일반 Error의 message는 화면에 내보내지 않는다', () => {
    expect(describeMatchRoomError(new Error('network'), 'ARRIVAL_TIME')).toBe(
      '도착 예정 시간을 저장하지 못했어요. 다시 선택해주세요.',
    );
  });

  it('Error가 아닌 값은 행동별 기본 문구를 쓴다', () => {
    expect(describeMatchRoomError(null, 'ARRIVAL_TIME')).toBe(
      '도착 예정 시간을 저장하지 못했어요. 다시 선택해주세요.',
    );
    expect(describeMatchRoomError('문자열', 'ARRIVE')).toBe(
      '도착을 인증하지 못했어요. 잠시 후 다시 시도해주세요.',
    );
  });
});
