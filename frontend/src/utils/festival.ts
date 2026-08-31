import type { Festival, FestivalStatus } from '../types';
import type { FestivalDetail, FestivalListItem } from '../api/festivals';
import type { NearbyFestivalItem } from '../api/spots';

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

/** backend가 내려주는 KST 날짜 문자열(yyyy-MM-dd) 기준 "오늘"을 계산한다. */
function todayKstDateString(now: Date): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(now);
}

/**
 * backend의 데이터 정합성 상태(ACTIVE/INACTIVE/ENDED/HIDDEN)와 행사 기간을 화면 표시용
 * status(ongoing/upcoming/ended)로 변환한다. 화면 상태는 backend에 별도로 두지 않고 항상 여기서 계산한다.
 */
export function resolveDisplayStatus(
  detail: Pick<FestivalDetail, 'status' | 'eventStartDate' | 'eventEndDate'>,
  now: Date = new Date(),
): FestivalStatus {
  if (detail.status === 'ENDED') return 'ended';
  const today = todayKstDateString(now);
  if (detail.eventEndDate && detail.eventEndDate < today) return 'ended';
  if (detail.eventStartDate && detail.eventStartDate > today) return 'upcoming';
  return 'ongoing';
}

/** 예정 축제의 D-day 뱃지 문구를 계산한다. 진행 중/종료 축제는 호출부에서 getFestivalStatusLabel로 대체된다. */
export function resolveDdayLabel(
  detail: Pick<FestivalDetail, 'eventStartDate'>,
  now: Date = new Date(),
): string {
  if (!detail.eventStartDate) return '';
  const today = todayKstDateString(now);
  const diffDays = Math.round(
    (new Date(`${detail.eventStartDate}T00:00:00+09:00`).getTime() -
      new Date(`${today}T00:00:00+09:00`).getTime()) /
      (1000 * 60 * 60 * 24),
  );
  return diffDays > 0 ? `D-${diffDays}` : '진행 중';
}

/** 행사 기간을 'yyyy.MM.dd – yyyy.MM.dd' 형식으로 표시한다. */
export function formatFestivalPeriod(
  detail: Pick<FestivalDetail, 'eventStartDate' | 'eventEndDate'>,
): string {
  if (!detail.eventStartDate) return '';
  const start = detail.eventStartDate.replaceAll('-', '.');
  if (!detail.eventEndDate || detail.eventEndDate === detail.eventStartDate) return start;
  return `${start} – ${detail.eventEndDate.replaceAll('-', '.')}`;
}

/** 행사 기간을 목록 카드용 'M.d – M.d' 축약 형식으로 표시한다. */
export function formatFestivalPeriodShort(
  detail: Pick<FestivalDetail, 'eventStartDate' | 'eventEndDate'>,
): string {
  const shorten = (isoDate: string) => isoDate.slice(5).replace(/^0?(\d+)-0?(\d+)$/, '$1.$2');
  if (!detail.eventStartDate) return '';
  const start = shorten(detail.eventStartDate);
  if (!detail.eventEndDate || detail.eventEndDate === detail.eventStartDate) return start;
  return `${start} – ${shorten(detail.eventEndDate)}`;
}

/**
 * 축제 목록 API 응답(`FestivalListItem`)을 화면 canonical 타입 `Festival`로 변환한다.
 * 실 API가 아직 제공하지 않는 카테고리/장소명/거리/매칭 정보는 optional 필드로 비워두고,
 * 화면 표시용 status/ddayLabel/기간은 여기서 계산한다.
 */
export function mapFestivalListItemToFestival(item: FestivalListItem): Festival {
  return {
    id: item.id,
    name: item.title,
    status: resolveDisplayStatus(item),
    ddayLabel: resolveDdayLabel(item),
    periodShort: formatFestivalPeriodShort(item),
    periodFull: formatFestivalPeriod(item),
    address: item.address ?? '',
    intro: '',
    thumbnailUrl: item.thumbnailUrl ?? item.originImageUrl ?? null,
    infoItems: [],
    programs: [],
    nearbyPlaces: [],
  };
}

/**
 * 축제 상세 응답(`FestivalDetail`)을 화면 canonical 타입 `Festival`로 변환한다.
 * 홈 화면이 체크인한 축제를 히어로 카드로 그릴 때 쓴다 — 체크인 응답에는 축제 id/이름만 있어
 * 카드에 필요한 기간·이미지를 상세에서 가져와야 한다.
 * 목록 매퍼와 같은 규칙으로 status/ddayLabel/기간을 계산한다.
 */
export function mapFestivalDetailToFestival(detail: FestivalDetail): Festival {
  return {
    id: detail.id,
    name: detail.title,
    status: resolveDisplayStatus(detail),
    ddayLabel: resolveDdayLabel(detail),
    periodShort: formatFestivalPeriodShort(detail),
    periodFull: formatFestivalPeriod(detail),
    address: detail.address ?? '',
    intro: detail.intro,
    thumbnailUrl: detail.thumbnailUrl ?? detail.originImageUrl ?? null,
    infoItems: detail.infoItems.map((item) => ({ label: item.label, value: item.value })),
    programs: detail.programs.map((program) => ({
      name: program.name,
      desc: program.description,
      time: program.time,
    })),
    nearbyPlaces: [],
  };
}

/** 관광지 상세 "이 장소 주변에서 열리는 축제"용 — 관광지 좌표 기준 거리(km)를 함께 계산한다. */
export function mapNearbyFestivalToFestival(item: NearbyFestivalItem): Festival {
  return {
    id: item.id,
    name: item.title,
    status: resolveDisplayStatus(item),
    ddayLabel: resolveDdayLabel(item),
    periodShort: formatFestivalPeriodShort(item),
    periodFull: formatFestivalPeriod(item),
    address: item.address ?? '',
    distanceKm: Math.round((item.distanceMeters / 1000) * 10) / 10,
    intro: '',
    thumbnailUrl: item.thumbnailUrl ?? null,
    infoItems: [],
    programs: [],
    nearbyPlaces: [],
  };
}
