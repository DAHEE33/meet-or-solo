import { describe, expect, it } from 'vitest';
// `?raw`로 읽는다. @types/node를 들이지 않으려는 것도 있지만, 번들러가 경로를 확인해 주므로
// 파일 이름이 바뀌면 테스트가 "없는 파일"로 바로 깨진다.
import maskableSvg from '../../../public/icons/icon-maskable.svg?raw';
import anySvg from '../../../public/icons/icon.svg?raw';
import {
  FIGURE_BODY,
  GROUND_SHADOW,
  HEAD,
  INNER_CIRCLE,
  PIN_OUTLINE,
  RAYS,
  RAY_STROKE_WIDTH,
} from './brandPinGeometry';

/**
 * 탭 아이콘·PWA 아이콘이 스플래시의 핀과 같은 그림인지 확인한다.
 *
 * <p>같은 도형이 세 곳에 있다 — `BrandPin.tsx`(애니메이션), `icon.svg`(favicon),
 * `icon-maskable.svg`(설치 아이콘). 정적 파일은 `public/`에 있어 좌표 모듈을 import할 수
 * 없으므로, <b>파일을 읽어 좌표가 그대로 들어 있는지</b> 본다.
 *
 * <p>이 테스트가 없으면 핀을 고칠 때 한 곳만 바뀌고, 탭 아이콘과 앱 첫 화면의 로고가 다른
 * 그림이 된다. 눈으로 보기 전까지 아무도 모른다.
 */
const ANY = anySvg;
const MASKABLE = maskableSvg;

describe.each([
  ['icon.svg', ANY],
  ['icon-maskable.svg', MASKABLE],
])('%s는 스플래시 핀과 같은 도형을 쓴다', (_name, svg) => {
  it('외곽 물방울과 안쪽 원이 같다', () => {
    expect(svg).toContain(PIN_OUTLINE);
    expect(svg).toContain(`cx="${INNER_CIRCLE.cx}" cy="${INNER_CIRCLE.cy}" r="${INNER_CIRCLE.r}"`);
  });

  it('두 사람의 머리와 몸통이 같다', () => {
    expect(svg).toContain(`cx="${HEAD.cx}" cy="${HEAD.cy}" r="${HEAD.r}"`);
    expect(svg).toContain(FIGURE_BODY);
    // 오른쪽 사람은 좌우 반전이다. 하나만 있으면 한 사람짜리 로고가 된다.
    expect(svg.split(FIGURE_BODY)).toHaveLength(3);
    expect(svg).toContain('translate(100 0) scale(-1 1)');
  });

  it('하이파이브 광선 3줄이 같다', () => {
    RAYS.forEach((ray) => expect(svg).toContain(ray.d));
    expect(svg).toContain(`stroke-width="${RAY_STROKE_WIDTH}"`);
  });

  /** 앱 팔레트 밖의 색이 섞이면 탭 아이콘만 다른 오렌지로 보인다. */
  it('앱 팔레트 색만 쓴다', () => {
    expect(svg.match(/#[0-9A-Fa-f]{6}/g) ?? []).toSatisfy((colors: string[]) =>
      colors.every((color) => ['#E8593A', '#22303E', '#FFFFFF', '#FAF7F1'].includes(color)));
  });

  /**
   * 착지 그림자는 뺀다. 동작을 설명하는 조각이라 멈춰 있으면 의미가 없고, 16px 탭
   * 아이콘에서는 핀 아래 얼룩으로만 보인다.
   */
  it('바닥 그림자는 넣지 않는다', () => {
    expect(svg).not.toContain(`ry="${GROUND_SHADOW.ry}"`);
  });

  /** 정사각이 아니면 브라우저와 OS가 제멋대로 여백을 붙인다. */
  it('정사각 viewBox다', () => {
    expect(svg).toContain('viewBox="0 0 112 112"');
  });
});

describe('maskable 아이콘', () => {
  /**
   * maskable은 바깥 20%가 잘려 나갈 수 있다. `any`와 같은 크기로 두면 핀 끝과 광선이 잘린다.
   */
  it('핀을 줄이고 배경을 꽉 채운다', () => {
    expect(MASKABLE).toContain('scale(0.71)');
    expect(MASKABLE).toContain('<rect width="112" height="112" fill="#FAF7F1"/>');
  });

  /** 반대로 `any`에 여백을 주면 탭에서 로고가 작게 박혀 보인다. */
  it('any 아이콘은 줄이지도 배경을 깔지도 않는다', () => {
    expect(ANY).not.toContain('scale(0.71)');
    expect(ANY).not.toContain('<rect');
  });
});
