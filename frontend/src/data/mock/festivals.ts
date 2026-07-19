import type { Festival } from '../../types';

export const festivals: Festival[] = [
  {
    id: 1,
    name: '전주비빔밥축제',
    category: '지역특산',
    status: 'ongoing',
    ddayLabel: '진행 중',
    periodShort: '7.24 – 7.28',
    periodFull: '2026.07.24 – 2026.07.28',
    place: '전주 한옥마을 일대',
    region: '전북 전주',
    distanceKm: 0.4,
    expectedAttendees: 1203,
    matchingCount: 87,
    matchSupported: true,
    address: '전북 전주시 완산구 기린대로 99 일대',
    imageCount: 4,
    intro:
      '전주의 대표 음식 비빔밥을 주제로 한옥마을 일대에서 열리는 미식 축제. 대형 비빔밥 퍼포먼스, 셰프와 함께하는 비빔밥 만들기 체험, 전통주 시음존이 운영되며 저녁에는 한옥마을 골목을 따라 야시장 먹거리존이 이어진다. 혼자 와도 체험 프로그램 대부분이 1인 참여형이라 부담이 없고, 축제 기간에는 동행 매칭 참여자가 가장 많이 모이는 축제이기도 하다.',
    infoItems: [
      { label: '개최 기간', value: '2026.07.24 – 2026.07.28' },
      { label: '운영 시간', value: '10:00 – 21:00 (야시장 ~23:00)' },
      { label: '개최 장소', value: '전주 한옥마을 일대' },
      { label: '이용 요금', value: '무료 (일부 체험 유료)' },
      { label: '주최·주관', value: '전주시 · 전주문화재단' },
      { label: '문의', value: '063-281-2114' },
    ],
    programs: [
      { name: '대형 비빔밥 퍼포먼스', desc: '개막 하이라이트 · 시식 포함', time: '매일 12:00' },
      { name: '비빔밥 만들기 체험', desc: '1인 참여형 · 현장 접수', time: '11:00–18:00' },
      { name: '한옥마을 야시장 먹거리존', desc: '골목형 야시장 · 전통주 시음', time: '18:00–23:00' },
    ],
    nearbyPlaces: [
      { spotId: 2, distanceKm: 0.5 },
      { spotId: 3, distanceKm: 1.1 },
      { spotId: 5, distanceKm: 0.5 },
      { spotId: 7, distanceKm: 0.3 },
    ],
  },
  {
    id: 2,
    name: '무주반딧불축제',
    category: '생태자연',
    status: 'upcoming',
    ddayLabel: 'D-3',
    periodShort: '7.21 – 7.26',
    periodFull: '2026.07.21 – 2026.07.26',
    place: '무주 반딧불랜드',
    region: '전북 무주',
    distanceKm: 62,
    matchingCount: 41,
    matchSupported: true,
    address: '전북 무주군 설천면 무주로 1758',
    imageCount: 1,
    intro:
      '청정 자연 속에서 반딧불이의 신비로운 빛을 관찰하는 생태 축제. 반딧불 탐사 나이트투어와 생태 전시관을 통해 무주의 깨끗한 자연을 체험할 수 있다.',
    infoItems: [
      { label: '개최 기간', value: '2026.07.21 – 2026.07.26' },
      { label: '운영 시간', value: '18:00 – 22:00 (반딧불 관찰 프로그램)' },
      { label: '개최 장소', value: '무주 반딧불랜드' },
      { label: '이용 요금', value: '무료 (반딧불 탐사선 유료)' },
      { label: '주최·주관', value: '무주군 · 무주문화관광재단' },
      { label: '문의', value: '063-320-2543' },
    ],
    programs: [
      { name: '반딧불 탐사 나이트투어', desc: '가이드 동행 · 사전예약', time: '19:30–21:00' },
      { name: '반딧불 생태 전시관', desc: '상설 전시 · 무료 관람', time: '09:00–18:00' },
    ],
    nearbyPlaces: [],
  },
  {
    id: 3,
    name: '임실치즈축제',
    category: '지역특산',
    status: 'upcoming',
    ddayLabel: 'D-7',
    periodShort: '7.25 – 7.27',
    periodFull: '2026.07.25 – 2026.07.27',
    place: '임실치즈테마파크',
    region: '전북 임실',
    distanceKm: 31,
    matchingCount: 23,
    matchSupported: true,
    address: '전북 임실군 성수면 도인2길 51',
    imageCount: 1,
    intro:
      '국내 최초 치즈 생산지 임실에서 열리는 체험형 축제. 직접 치즈를 만들어보는 체험 프로그램과 유럽풍 테마파크 포토존이 인기다.',
    infoItems: [
      { label: '개최 기간', value: '2026.07.25 – 2026.07.27' },
      { label: '운영 시간', value: '10:00 – 18:00' },
      { label: '개최 장소', value: '임실치즈테마파크' },
      { label: '이용 요금', value: '무료 (체험 프로그램 유료)' },
      { label: '주최·주관', value: '임실군 · 임실군축산업협동조합' },
      { label: '문의', value: '063-643-5200' },
    ],
    programs: [
      { name: '치즈 만들기 체험', desc: '1인 참여형 · 현장 접수', time: '10:30–17:00' },
      { name: '유럽풍 야외 포토존', desc: '테마파크 상설 전시', time: '상시' },
    ],
    nearbyPlaces: [],
  },
  {
    id: 4,
    name: '김제지평선축제',
    category: '문화관광',
    status: 'upcoming',
    ddayLabel: 'D-12',
    periodShort: '7.30 – 8.3',
    periodFull: '2026.07.30 – 2026.08.03',
    place: '벽골제 일원',
    region: '전북 김제',
    distanceKm: 28,
    matchingCount: 18,
    matchSupported: true,
    address: '전북 김제시 부량면 벽골제로 442',
    imageCount: 1,
    intro:
      '너른 김제 들녘에서 펼쳐지는 대한민국 유일의 지평선 축제. 전통 농경 문화 체험과 벽골제 일원의 야간 경관 조명이 볼거리다.',
    infoItems: [
      { label: '개최 기간', value: '2026.07.30 – 2026.08.03' },
      { label: '운영 시간', value: '10:00 – 22:00' },
      { label: '개최 장소', value: '벽골제 일원' },
      { label: '이용 요금', value: '무료' },
      { label: '주최·주관', value: '김제시 · 김제문화재단' },
      { label: '문의', value: '063-540-3090' },
    ],
    programs: [
      { name: '지평선 들녘 걷기', desc: '가이드 동행 · 사전예약', time: '매일 07:00' },
      { name: '전통 농경 체험마당', desc: '모내기·탈곡 체험', time: '10:00–17:00' },
    ],
    nearbyPlaces: [],
  },
  {
    id: 5,
    name: '남원춘향제',
    category: '전통역사',
    status: 'upcoming',
    ddayLabel: 'D-19',
    periodShort: '8.6 – 8.10',
    periodFull: '2026.08.06 – 2026.08.10',
    place: '광한루원 일원',
    region: '전북 남원',
    distanceKm: 47,
    matchingCount: 12,
    matchSupported: true,
    address: '전북 남원시 요천로 1447',
    imageCount: 1,
    intro:
      '춘향과 이몽룡의 사랑 이야기를 테마로 한 국내 최고(最古) 전통 문화축제. 광한루원 야간 개장과 전통 혼례 재현식이 대표 프로그램이다.',
    infoItems: [
      { label: '개최 기간', value: '2026.08.06 – 2026.08.10' },
      { label: '운영 시간', value: '10:00 – 21:00' },
      { label: '개최 장소', value: '광한루원 일원' },
      { label: '이용 요금', value: '무료 (광한루원 입장료 별도)' },
      { label: '주최·주관', value: '남원시 · 남원문화원' },
      { label: '문의', value: '063-620-6153' },
    ],
    programs: [
      { name: '춘향제 전통 혼례 재현', desc: '개막 하이라이트', time: '매일 15:00' },
      { name: '광한루원 야간 개장', desc: '조명 경관 관람', time: '19:00–22:00' },
    ],
    nearbyPlaces: [],
  },
  {
    id: 6,
    name: '전주 문화재 야행',
    category: '문화관광',
    status: 'upcoming',
    ddayLabel: 'D-9',
    periodShort: '7.27 – 7.29',
    periodFull: '2026.07.27 – 2026.07.29',
    place: '전주 한옥마을 일대',
    region: '전북 전주',
    distanceKm: 0.4,
    matchingCount: 15,
    matchSupported: true,
    address: '전북 전주시 완산구 태조로 44 일대',
    imageCount: 1,
    intro:
      '한옥마을 일대 문화재를 야간에 특별 개방하는 행사. 전동성당, 경기전 등 주요 문화재에서 야간 조명과 전통 공연이 펼쳐진다.',
    infoItems: [
      { label: '개최 기간', value: '2026.07.27 – 2026.07.29' },
      { label: '운영 시간', value: '19:00 – 22:00' },
      { label: '개최 장소', value: '전주 한옥마을 일대' },
      { label: '이용 요금', value: '무료' },
      { label: '주최·주관', value: '전주시 · 전주문화유산야행추진위원회' },
      { label: '문의', value: '063-281-2114' },
    ],
    programs: [
      { name: '문화재 야간 특별개방', desc: '경기전·전동성당 등', time: '19:00–22:00' },
      { name: '전통 공연 마당', desc: '국악 공연 · 무료 관람', time: '20:00, 21:00' },
    ],
    nearbyPlaces: [],
  },
];

export const hotFestival: Festival = festivals[0];

export const upcomingFestivals: Festival[] = festivals.filter(
  (f) => f.status === 'upcoming' && f.id !== 6,
);

export function getFestivalById(id: number): Festival | undefined {
  return festivals.find((f) => f.id === id);
}

/** 홈 "축제와 함께 둘러보기" 목록 — 선택된(대표) 축제 기준 도보 거리 */
export interface HomeNearbyPlace {
  spotId: number;
  distanceLabel: string; // 예: '200m'
  walkLabel: string; // 예: '도보 3분'
}

export const homeNearbyPlaces: HomeNearbyPlace[] = [
  { spotId: 2, distanceLabel: '200m', walkLabel: '도보 3분' },
  { spotId: 5, distanceLabel: '500m', walkLabel: '도보 8분' },
  { spotId: 7, distanceLabel: '300m', walkLabel: '도보 5분' },
  { spotId: 6, distanceLabel: '700m', walkLabel: '도보 11분' },
];
