import { describe, expect, it } from 'vitest';
import {
  hashSeed,
  mixWithBase,
  placeholderAriaLabel,
  placeholderBackground,
  placeholderKindFromContentType,
  placeholderLabel,
} from './imagePlaceholderPresets';

describe('placeholderKindFromContentType', () => {
  it('동기화 대상 contentTypeId를 프리셋으로 매핑한다', () => {
    expect(placeholderKindFromContentType('12')).toBe('TOUR');
    expect(placeholderKindFromContentType('14')).toBe('CULTURE');
    expect(placeholderKindFromContentType('28')).toBe('ACTIVITY');
    expect(placeholderKindFromContentType('39')).toBe('FOOD');
  });

  it('값이 없거나 동기화 대상이 아니면 중립 프리셋으로 떨어진다', () => {
    // 관광공사 동기화 대상은 12/14/28/39뿐이지만, 대상이 늘어나거나 mock 데이터처럼
    // contentTypeId가 없는 경우에도 화면이 깨지지 않아야 한다.
    expect(placeholderKindFromContentType(undefined)).toBe('DEFAULT');
    expect(placeholderKindFromContentType(null)).toBe('DEFAULT');
    expect(placeholderKindFromContentType('')).toBe('DEFAULT');
    expect(placeholderKindFromContentType('32')).toBe('DEFAULT');
  });
});

describe('placeholderLabel / placeholderAriaLabel', () => {
  it('분류 문구는 contentTypeLabel과 같은 어휘를 쓴다', () => {
    expect(placeholderLabel('TOUR')).toBe('관광지');
    expect(placeholderLabel('CULTURE')).toBe('문화시설');
    expect(placeholderLabel('ACTIVITY')).toBe('액티비티');
    expect(placeholderLabel('FOOD')).toBe('맛집');
    expect(placeholderLabel('FESTIVAL')).toBe('축제');
  });

  it('중립 프리셋은 분류 문구를 두 번 읽지 않는다', () => {
    expect(placeholderAriaLabel('FESTIVAL')).toBe('축제 사진 준비 중');
    expect(placeholderAriaLabel('DEFAULT')).toBe('사진 준비 중');
  });
});

describe('hashSeed', () => {
  it('같은 문자열은 항상 같은 값을 낸다', () => {
    expect(hashSeed('화천산천어축제')).toBe(hashSeed('화천산천어축제'));
  });

  it('빈 문자열도 32bit 부호 없는 정수를 낸다', () => {
    const hash = hashSeed('');
    expect(Number.isInteger(hash)).toBe(true);
    expect(hash).toBeGreaterThanOrEqual(0);
    expect(hash).toBeLessThanOrEqual(0xffffffff);
  });
});

describe('mixWithBase', () => {
  it('ratio 0은 배경색, ratio 1은 accent 원색이다', () => {
    expect(mixWithBase('#E8593A', 0)).toBe('#faf7f1');
    expect(mixWithBase('#E8593A', 1)).toBe('#e8593a');
  });

  it('범위를 벗어난 ratio는 0~1로 잘라낸다', () => {
    expect(mixWithBase('#E8593A', -3)).toBe(mixWithBase('#E8593A', 0));
    expect(mixWithBase('#E8593A', 9)).toBe(mixWithBase('#E8593A', 1));
  });

  it('항상 6자리 hex를 만든다 — 한 자리 채널이 나와도 CSS가 깨지지 않아야 한다', () => {
    expect(mixWithBase('#000000', 1)).toBe('#000000');
    expect(mixWithBase('#020304', 1)).toBe('#020304');
  });
});

describe('placeholderBackground', () => {
  it('같은 콘텐츠는 항상 같은 톤을 낸다 — 목록을 다시 열어도 색이 바뀌면 안 된다', () => {
    expect(placeholderBackground('TOUR', '설악산')).toBe(placeholderBackground('TOUR', '설악산'));
  });

  it('같은 분류라도 콘텐츠가 다르면 톤이 갈린다 — 목록에서 항목이 구분돼야 한다', () => {
    const names = ['설악산', '오죽헌', '남이섬', '경포해변', '레고랜드', '청령포'];
    const backgrounds = new Set(names.map((name) => placeholderBackground('TOUR', name)));
    expect(backgrounds.size).toBeGreaterThan(1);
  });

  it('분류가 다르면 accent도 달라진다', () => {
    expect(placeholderBackground('FESTIVAL', '같은이름')).not.toBe(
      placeholderBackground('FOOD', '같은이름'),
    );
  });

  it('CSS linear-gradient 문법을 만든다', () => {
    expect(placeholderBackground('FESTIVAL', '화천산천어축제')).toMatch(
      /^linear-gradient\(\d+deg, #[0-9a-f]{6} 0%, #[0-9a-f]{6} 100%\)$/,
    );
  });
});
