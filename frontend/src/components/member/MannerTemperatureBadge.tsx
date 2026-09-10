import { MANNER_TEMPERATURE_CEILING, MANNER_TEMPERATURE_FLOOR, MANNER_TEMPERATURE_INITIAL } from '../../api/mannerTemperature';

/**
 * 본인 매너온도 표시(docs/19 4.9).
 *
 * **본인 것만 보여준다.** 다른 회원의 온도는 어디에도 노출하지 않는다 — 낮은 온도는
 * "신고를 받은 적이 있다"는 사실을 그대로 드러내고, 같은 만남에 있던 사람이 보면 누가
 * 신고했는지 좁힐 수 있다. backend도 본인 응답에만 담는다.
 *
 * 패널티 점수는 표시하지 않는다. 노쇼 누적으로 쿨타임을 거는 내부 운영 값이다.
 */

/** 시작값(36.5)을 기준으로 세 구간으로 나눈다. 숫자만으로는 좋은 값인지 알 수 없다. */
export function mannerTemperatureTone(temperature: number): 'LOW' | 'NEUTRAL' | 'HIGH' {
  if (temperature < MANNER_TEMPERATURE_INITIAL) return 'LOW';
  if (temperature > MANNER_TEMPERATURE_INITIAL) return 'HIGH';
  return 'NEUTRAL';
}

export function mannerTemperatureLabel(temperature: number): string {
  switch (mannerTemperatureTone(temperature)) {
    case 'HIGH':
      return '좋은 매너를 유지하고 있어요';
    case 'LOW':
      return '만남을 끝까지 마치면 올라가요';
    default:
      return '기본 온도예요';
  }
}

/** 게이지 채움 비율(0~100). 하한~상한 구간을 그대로 매핑한다. */
export function mannerTemperatureFillPercent(temperature: number): number {
  const span = MANNER_TEMPERATURE_CEILING - MANNER_TEMPERATURE_FLOOR;
  const ratio = (temperature - MANNER_TEMPERATURE_FLOOR) / span;
  return Math.round(Math.min(1, Math.max(0, ratio)) * 100);
}

type Props = {
  temperature: number | null | undefined;
  /** 매칭 화면처럼 좁은 자리에서는 게이지 없이 한 줄로 보여준다. */
  compact?: boolean;
  className?: string;
};

export default function MannerTemperatureBadge({ temperature, compact = false, className }: Props) {
  if (temperature === null || temperature === undefined || Number.isNaN(temperature)) {
    return null;
  }
  const tone = mannerTemperatureTone(temperature);
  const valueColor = tone === 'LOW' ? 'text-coral' : tone === 'HIGH' ? 'text-teal' : 'text-ink';
  const barColor = tone === 'LOW' ? 'bg-coral' : tone === 'HIGH' ? 'bg-teal' : 'bg-ink/40';
  const reading = `매너온도 ${temperature.toFixed(1)}도`;

  if (compact) {
    return (
      <span className={`inline-flex items-center gap-1 text-[13px] ${className ?? ''}`}>
        <span className="text-ink/55">매너온도</span>
        <span className={`font-bold ${valueColor}`} aria-label={reading}>{temperature.toFixed(1)}°</span>
      </span>
    );
  }

  return (
    <section className={`flex flex-col gap-2 ${className ?? ''}`}>
      <div className="flex items-baseline justify-between">
        <h2 className="text-[15px] font-bold text-ink">매너온도</h2>
        <span className={`text-[17px] font-bold ${valueColor}`} aria-label={reading}>
          {temperature.toFixed(1)}°
        </span>
      </div>
      <div
        className="h-2 w-full overflow-hidden rounded-full bg-line"
        role="img"
        aria-label={reading}
      >
        <div
          className={`h-full rounded-full ${barColor}`}
          style={{ width: `${mannerTemperatureFillPercent(temperature)}%` }}
        />
      </div>
      <p className="text-[13px] text-ink/55">{mannerTemperatureLabel(temperature)}</p>
    </section>
  );
}
