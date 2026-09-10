import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import MannerTemperatureBadge, {
  mannerTemperatureFillPercent,
  mannerTemperatureLabel,
  mannerTemperatureTone,
} from './MannerTemperatureBadge';
import {
  MANNER_TEMPERATURE_CEILING,
  MANNER_TEMPERATURE_FLOOR,
  MANNER_TEMPERATURE_INITIAL,
} from '../../api/mannerTemperature';

describe('매너온도 구간 판정', () => {
  it('시작값을 기준으로 세 구간으로 나눈다', () => {
    expect(mannerTemperatureTone(30)).toBe('LOW');
    expect(mannerTemperatureTone(MANNER_TEMPERATURE_INITIAL)).toBe('NEUTRAL');
    expect(mannerTemperatureTone(38)).toBe('HIGH');
  });

  /** 낮은 구간에서는 "왜 낮은가"가 아니라 "어떻게 올리는가"를 알려줘야 한다. */
  it('낮은 구간에서는 회복 방법을 안내한다', () => {
    expect(mannerTemperatureLabel(30)).toContain('만남을 끝까지 마치면');
  });

  it('게이지는 하한에서 0, 상한에서 100이다', () => {
    expect(mannerTemperatureFillPercent(MANNER_TEMPERATURE_FLOOR)).toBe(0);
    expect(mannerTemperatureFillPercent(MANNER_TEMPERATURE_CEILING)).toBe(100);
  });

  /** 하한 아래·상한 위 값이 서버에서 오더라도 게이지가 넘치지 않아야 한다. */
  it('범위를 벗어난 값도 0~100으로 자른다', () => {
    expect(mannerTemperatureFillPercent(0)).toBe(0);
    expect(mannerTemperatureFillPercent(100)).toBe(100);
  });
});

describe('매너온도 표시', () => {
  it('소수점 한 자리로 보여준다', () => {
    expect(renderToStaticMarkup(<MannerTemperatureBadge temperature={34.5} />)).toContain('34.5°');
  });

  /** 프로필 로딩 중에는 값이 없다. 0도로 그리면 최하점으로 오해한다. */
  it.each([null, undefined])('값이 %s이면 아무것도 그리지 않는다', (value) => {
    expect(renderToStaticMarkup(<MannerTemperatureBadge temperature={value} />)).toBe('');
  });

  it('compact는 게이지 없이 한 줄로 보여준다', () => {
    const markup = renderToStaticMarkup(<MannerTemperatureBadge temperature={36.5} compact />);
    expect(markup).toContain('36.5°');
    expect(markup).not.toContain('role="img"');
  });

  /** 숫자만으로는 좋은 값인지 알 수 없어 스크린리더에 단위를 붙인다. */
  it('접근성 라벨에 단위를 붙인다', () => {
    expect(renderToStaticMarkup(<MannerTemperatureBadge temperature={36.5} />))
      .toContain('매너온도 36.5도');
  });

  /**
   * 패널티 점수는 어떤 형태로도 회원 화면에 나오면 안 된다. 노쇼 쿨타임을 거는 내부
   * 운영 값이다.
   */
  it('패널티라는 말을 쓰지 않는다', () => {
    expect(renderToStaticMarkup(<MannerTemperatureBadge temperature={22} />)).not.toContain('패널티');
  });
});
