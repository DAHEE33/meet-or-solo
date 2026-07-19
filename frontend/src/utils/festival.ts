import type { Festival, FestivalStatus } from '../types';

/** 대표 이미지 위 오버레이 뱃지 등 "진한" 배지 톤 */
export function getFestivalStatusSolidClass(status: FestivalStatus): string {
  if (status === 'ongoing') return 'bg-coral text-white';
  if (status === 'ended') return 'bg-ink/45 text-white';
  return 'bg-ink text-white';
}

/** 목록 카드 등에 쓰이는 "부드러운" 배지 톤 (진행 중일 때만 coral tint) */
export function getFestivalStatusSoftClass(status: FestivalStatus): string {
  if (status === 'ongoing') return 'bg-coral/10 text-coral';
  if (status === 'ended') return 'bg-line text-ink/55';
  return 'bg-ink text-white';
}

export function getFestivalStatusLabel(festival: Pick<Festival, 'status' | 'ddayLabel'>): string {
  if (festival.status === 'ongoing') return '진행 중';
  if (festival.status === 'ended') return '종료';
  return festival.ddayLabel;
}
