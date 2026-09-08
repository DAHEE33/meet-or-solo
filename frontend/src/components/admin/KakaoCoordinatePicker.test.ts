import { describe, expect, it } from 'vitest';
import { resolveCenter } from './KakaoCoordinatePicker';

describe('resolveCenter', () => {
  it('좌표가 0/0(지정 안 됨)이면 기본 중심점(춘천)을 쓴다', () => {
    expect(resolveCenter(0, 0)).toEqual({ latitude: 37.8813, longitude: 127.73 });
  });

  it('좌표가 지정돼 있으면 그 값을 그대로 쓴다', () => {
    expect(resolveCenter(37.75, 128.9)).toEqual({ latitude: 37.75, longitude: 128.9 });
  });

  it('위도나 경도 중 하나만 0이어도 지정된 값으로 본다', () => {
    expect(resolveCenter(0, 128.9)).toEqual({ latitude: 0, longitude: 128.9 });
    expect(resolveCenter(37.75, 0)).toEqual({ latitude: 37.75, longitude: 0 });
  });
});
