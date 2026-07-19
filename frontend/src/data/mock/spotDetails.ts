import type { InfoRow } from '../../types';

/** 관광지 상세 전용 부가 정보. TourSpot 자체 필드로 넣기엔 상세 페이지에서만 쓰이는 데이터라 분리했다. */
export interface SpotCourse {
  title: string;
  stops: string[];
  highlightIndex: number;
}

export interface SpotNearbyFestival {
  festivalId: number;
  distanceKm: number;
}

export interface SpotDetailExtra {
  visitInfo: InfoRow[];
  points: string[];
  course: SpotCourse | null;
  nearbyFestivals: SpotNearbyFestival[];
}

export const spotDetails: Record<number, SpotDetailExtra> = {
  1: {
    visitInfo: [
      { label: '이용 시간', value: '상시 개방 (체험시설 09:00–18:00)' },
      { label: '휴무일', value: '연중무휴' },
      { label: '이용 요금', value: '무료 (일부 체험 유료)' },
      { label: '주차', value: '공영주차장 이용 가능' },
      { label: '문의', value: '063-282-1330' },
    ],
    points: ['한복 체험 가능', '야간 방문 추천', '사진 촬영 명소', '도보 관광 가능'],
    course: {
      title: '전주 한옥마을 반나절 코스',
      stops: ['경기전', '전주 한옥마을', '자만벽화마을', '남부시장 야시장'],
      highlightIndex: 1,
    },
    nearbyFestivals: [
      { festivalId: 1, distanceKm: 0.2 },
      { festivalId: 6, distanceKm: 0.4 },
    ],
  },
  2: {
    visitInfo: [
      { label: '이용 시간', value: '09:00 – 18:00 (하절기 ~19:00)' },
      { label: '휴무일', value: '연중무휴' },
      { label: '이용 요금', value: '성인 3,000원' },
      { label: '문의', value: '063-281-2790' },
    ],
    points: ['조선왕조 유적', '대나무숲 산책', '사진 촬영 명소'],
    course: null,
    nearbyFestivals: [{ festivalId: 1, distanceKm: 0.3 }],
  },
  3: {
    visitInfo: [
      { label: '이용 시간', value: '상시 개방' },
      { label: '휴무일', value: '없음' },
      { label: '이용 요금', value: '무료' },
      { label: '문의', value: '063-281-2114' },
    ],
    points: ['벽화 포토존', '골목 산책', '전망 명소'],
    course: null,
    nearbyFestivals: [{ festivalId: 1, distanceKm: 0.9 }],
  },
  4: {
    visitInfo: [
      { label: '이용 시간', value: '09:00 – 18:00' },
      { label: '휴무일', value: '연중무휴' },
      { label: '이용 요금', value: '무료' },
      { label: '문의', value: '063-232-6428' },
    ],
    points: ['은행나무 명소', '가을 단풍', '고즈넉한 분위기'],
    course: null,
    nearbyFestivals: [{ festivalId: 1, distanceKm: 1.1 }],
  },
  5: {
    visitInfo: [
      { label: '영업 시간', value: '금·토 18:00 – 24:00' },
      { label: '대표 메뉴', value: '청년몰 먹거리 전반' },
      { label: '가격대', value: '5,000원대~' },
      { label: '예약', value: '불필요' },
      { label: '문의', value: '063-284-1344' },
    ],
    points: ['야시장 먹거리', '청년몰 쇼핑', '현지 분위기'],
    course: null,
    nearbyFestivals: [{ festivalId: 1, distanceKm: 0.6 }],
  },
  6: {
    visitInfo: [
      { label: '이용 시간', value: '상시 개방' },
      { label: '휴무일', value: '없음' },
      { label: '이용 요금', value: '무료' },
      { label: '문의', value: '063-281-2114' },
    ],
    points: ['노을 명소', '한옥마을 전망', '사진 촬영 명소'],
    course: null,
    nearbyFestivals: [{ festivalId: 1, distanceKm: 0.5 }],
  },
  7: {
    visitInfo: [
      { label: '이용 시간', value: '09:00 – 18:00 (미사 시간 제외)' },
      { label: '휴무일', value: '없음' },
      { label: '이용 요금', value: '무료' },
      { label: '문의', value: '063-284-3222' },
    ],
    points: ['로마네스크 건축', '사진 촬영 명소', '건축 투어'],
    course: null,
    nearbyFestivals: [{ festivalId: 1, distanceKm: 0.2 }],
  },
};

export function getSpotDetailExtra(spotId: number): SpotDetailExtra | undefined {
  return spotDetails[spotId];
}
