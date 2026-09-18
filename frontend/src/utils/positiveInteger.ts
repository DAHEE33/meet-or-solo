// 양의 정수 파싱 공용 유틸 — router state, 개발용 fallback 등 여러 곳에서
// "문자열이거나 숫자인 값을 유효한 양의 정수 ID로 해석"하는 동일한 규칙을 재사용한다.

export function positiveInteger(value: unknown): number | null {
  const parsed = typeof value === 'number' ? value : typeof value === 'string' ? Number(value) : NaN;
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

/** react-router의 location.state에서 특정 key의 값을 양의 정수로 읽는다. */
export function readNumberFromLocationState(locationState: unknown, key: string): number | null {
  if (!locationState || typeof locationState !== 'object' || !(key in locationState)) {
    return null;
  }
  return positiveInteger((locationState as Record<string, unknown>)[key]);
}
