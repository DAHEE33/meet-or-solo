// 댓글 작성 시각처럼 "얼마나 지났는지"가 중요한 값을 상대 시간으로 표시한다.
// 절대 시각이 필요한 운영 화면은 기존 formatSeoulDateTime(utils/dateTime.ts)을 계속 쓴다.

const MINUTE_MS = 60 * 1000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;

/** 7일이 지나면 상대 표기가 의미를 잃으므로 KST 날짜로 바꾼다. */
const RELATIVE_LIMIT_MS = 7 * DAY_MS;

const seoulDateFormatter = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: 'long',
  day: 'numeric',
});

/**
 * `방금` / `N분 전` / `N시간 전` / `N일 전`을 계산하고, 7일 이상은 날짜로 표시한다.
 *
 * `now`를 주입받는 이유는 이 프로젝트의 vitest가 node 환경이라 렌더링 없이 경계값을 검증해야
 * 하기 때문이다. 화면은 기본값(현재 시각)을 그대로 쓴다.
 */
export function formatRelativeTime(value: string | null | undefined, now: Date = new Date()): string {
  if (!value) return '';

  const created = new Date(value);
  if (Number.isNaN(created.getTime())) return '';

  // 서버·클라이언트 시계 차이로 미래가 나올 수 있다. 음수 경과를 "방금"으로 접는다.
  const elapsedMs = now.getTime() - created.getTime();
  if (elapsedMs < MINUTE_MS) return '방금';
  if (elapsedMs < HOUR_MS) return `${Math.floor(elapsedMs / MINUTE_MS)}분 전`;
  if (elapsedMs < DAY_MS) return `${Math.floor(elapsedMs / HOUR_MS)}시간 전`;
  if (elapsedMs < RELATIVE_LIMIT_MS) return `${Math.floor(elapsedMs / DAY_MS)}일 전`;

  return seoulDateFormatter.format(created);
}
