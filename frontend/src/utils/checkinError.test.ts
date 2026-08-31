import { describe, expect, it } from 'vitest';
import { ApiClientError } from '../api/apiClient';
import { describeCheckinError } from './checkinError';

describe('describeCheckinError', () => {
  it('알려진 체크인 에러코드는 사용자 문구로 바꾼다', () => {
    expect(
      describeCheckinError(new ApiClientError('원본 메시지', 400, 'CHECKIN_OUT_OF_RANGE', undefined)),
    ).toBe('축제 반경을 벗어난 위치예요. 축제 현장 안에서 다시 시도해주세요.');
    expect(
      describeCheckinError(new ApiClientError('원본 메시지', 400, 'LOW_LOCATION_ACCURACY', undefined)),
    ).toBe('위치 정확도가 낮아요. GPS가 잘 잡히는 곳에서 다시 시도해주세요.');
    expect(
      describeCheckinError(new ApiClientError('원본 메시지', 400, 'FESTIVAL_LOCATION_UNAVAILABLE', undefined)),
    ).toBe('이 축제는 아직 위치 정보가 없어 체크인할 수 없어요.');
    expect(describeCheckinError(new ApiClientError('원본 메시지', 404, 'NOT_FOUND', undefined))).toBe(
      '축제 정보를 찾을 수 없어요.',
    );
  });

  it('알려지지 않은 에러코드는 서버 message를 그대로 쓴다', () => {
    expect(
      describeCheckinError(new ApiClientError('서버가 준 메시지', 409, 'MATCHING_CONFLICT', undefined)),
    ).toBe('서버가 준 메시지');
    expect(describeCheckinError(new ApiClientError('코드 없음', 500, null, undefined))).toBe('코드 없음');
  });

  it('ApiClientError가 아닌 일반 Error는 message를 쓰고, 그 외는 기본 문구를 쓴다', () => {
    expect(describeCheckinError(new Error('위치 권한이 필요해요.'))).toBe('위치 권한이 필요해요.');
    expect(describeCheckinError('그냥 문자열')).toBe('체크인에 실패했어요.');
    expect(describeCheckinError(null)).toBe('체크인에 실패했어요.');
  });
});
