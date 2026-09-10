const SEOUL_TIME_ZONE = 'Asia/Seoul';

const seoulDateTimeFormatter = new Intl.DateTimeFormat('sv-SE', {
  timeZone: SEOUL_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

export function formatSeoulDateTime(value: string | null | undefined): string {
  if (!value) return '-';

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';

  return seoulDateTimeFormatter.format(date);
}

const seoulDateFormatter = new Intl.DateTimeFormat('sv-SE', {
  timeZone: SEOUL_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

/**
 * 서울 기준 날짜 키(`YYYY-MM-DD`)를 만든다. 값을 읽을 수 없으면 null이다.
 *
 * "같은 날인가"를 판단하는 곳에서 쓴다. 시각 차이(24시간)가 아니라 달력 날짜를 기준으로 해야
 * 하는 규칙이 있어서, 뺄셈이 아니라 키 비교로 다룬다.
 */
export function seoulDateKey(value: string | null | undefined): string | null {
  if (!value) return null;

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;

  return seoulDateFormatter.format(date);
}

/** 두 시각이 서울 기준 같은 날인지. 한쪽이라도 읽을 수 없으면 false다. */
export function isSameSeoulDate(
  left: string | null | undefined,
  right: string | null | undefined,
): boolean {
  const leftKey = seoulDateKey(left);
  const rightKey = seoulDateKey(right);
  return leftKey !== null && leftKey === rightKey;
}
