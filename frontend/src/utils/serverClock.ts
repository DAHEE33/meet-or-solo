export function calculateServerOffsetMs(serverNowIso: string, clientNowMs = Date.now()): number {
  return new Date(serverNowIso).getTime() - clientNowMs;
}

export function correctedNowMs(offsetMs: number, clientNowMs = Date.now()): number {
  return clientNowMs + offsetMs;
}

export function remainingSeconds(
  deadlineIso: string,
  offsetMs: number,
  clientNowMs = Date.now(),
): number {
  return Math.max(
    0,
    Math.ceil((new Date(deadlineIso).getTime() - correctedNowMs(offsetMs, clientNowMs)) / 1000),
  );
}

export function stabilizeRemainingSeconds(
  previous: { deadlineKey: string; seconds: number } | null,
  deadlineKey: string,
  nextSeconds: number,
): number {
  if (previous === null || previous.deadlineKey !== deadlineKey) return nextSeconds;
  return Math.min(nextSeconds, previous.seconds);
}
