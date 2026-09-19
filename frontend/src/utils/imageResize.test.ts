import { describe, expect, it } from 'vitest';
import {
  MAX_DIMENSION,
  calculateTargetSize,
  isSupportedImageType,
  resizeFailureMessage,
} from './imageResize';

/*
  canvas와 createImageBitmap은 jsdom에 없어서 resizeProfileImage 전체는 단위 테스트로
  다루지 않는다. 대신 판단이 들어가는 순수 함수를 검증한다. 실제 리사이즈 동작은
  브라우저에서 확인한다.
*/

describe('calculateTargetSize', () => {
  it('긴 변이 상한 이하면 그대로 둔다', () => {
    expect(calculateTargetSize(400, 300)).toEqual({ width: 400, height: 300 });
  });

  it('상한과 같으면 그대로 둔다', () => {
    expect(calculateTargetSize(MAX_DIMENSION, 100)).toEqual({ width: MAX_DIMENSION, height: 100 });
  });

  it('작은 이미지를 키우지 않는다', () => {
    // 키워봐야 화질은 그대로이고 파일만 커진다.
    const result = calculateTargetSize(64, 64);
    expect(result).toEqual({ width: 64, height: 64 });
  });

  it('가로가 긴 사진은 가로를 상한에 맞추고 비율을 지킨다', () => {
    expect(calculateTargetSize(4000, 3000)).toEqual({ width: 512, height: 384 });
  });

  it('세로가 긴 사진은 세로를 상한에 맞춘다', () => {
    // 아이폰 세로 사진의 일반적인 비율(3:4).
    expect(calculateTargetSize(3024, 4032)).toEqual({ width: 384, height: 512 });
  });

  it('정사각형은 양변이 모두 상한이 된다', () => {
    expect(calculateTargetSize(2000, 2000)).toEqual({ width: 512, height: 512 });
  });

  it('극단적으로 긴 이미지에서도 짧은 변이 0이 되지 않는다', () => {
    // 반올림 결과가 0이면 canvas 그리기가 실패한다.
    const result = calculateTargetSize(10000, 3);
    expect(result.width).toBe(512);
    expect(result.height).toBeGreaterThanOrEqual(1);
  });

  it('상한을 직접 지정할 수 있다', () => {
    expect(calculateTargetSize(1000, 500, 100)).toEqual({ width: 100, height: 50 });
  });
});

describe('isSupportedImageType', () => {
  it('JPEG, PNG, WEBP를 허용한다', () => {
    expect(isSupportedImageType('image/jpeg')).toBe(true);
    expect(isSupportedImageType('image/png')).toBe(true);
    expect(isSupportedImageType('image/webp')).toBe(true);
  });

  it('HEIC는 허용하지 않는다', () => {
    // iOS가 업로드 직전에 JPEG로 바꿔 주므로 실제로는 여기까지 오지 않는다.
    expect(isSupportedImageType('image/heic')).toBe(false);
  });

  it('이미지가 아닌 형식을 거른다', () => {
    expect(isSupportedImageType('application/pdf')).toBe(false);
    expect(isSupportedImageType('')).toBe(false);
  });
});

describe('resizeFailureMessage', () => {
  it('사유마다 다른 안내를 준다', () => {
    const messages = [
      resizeFailureMessage('UNSUPPORTED_TYPE'),
      resizeFailureMessage('TOO_LARGE'),
      resizeFailureMessage('DECODE_FAILED'),
    ];
    expect(new Set(messages).size).toBe(3);
  });

  it('무엇을 해야 하는지 알려준다', () => {
    expect(resizeFailureMessage('UNSUPPORTED_TYPE')).toContain('선택');
    expect(resizeFailureMessage('TOO_LARGE')).toContain('선택');
    expect(resizeFailureMessage('DECODE_FAILED')).toContain('선택');
  });
});
