import type { TourSpot } from '../../types';

export const todaySpot: TourSpot = {
  id: 1,
  name: '전주 한옥마을',
  category: '문화재',
  address: '전북 전주시 완산구 기린대로 99',
  distanceKm: 0.4,
  rating: 4.7,
  reviewCount: 1284,
  imageUrl: null,
  tags: ['한복체험', '야경맛집', '혼행추천'],
  description:
    '700여 채의 전통 한옥이 모여 있는 국내 최대 규모의 한옥촌. 한복 체험, 전통 찻집, 야경 명소가 밀집해 있어 혼자 걸어도, 동행과 함께여도 좋다.',
};

export const tourSpots: TourSpot[] = [
  todaySpot,
  {
    id: 2,
    name: '경기전',
    category: '문화재',
    address: '전북 전주시 완산구 태조로 44',
    distanceKm: 0.6,
    rating: 4.6,
    reviewCount: 892,
    imageUrl: null,
    tags: ['조선왕조', '사진명소'],
    description: '태조 이성계의 어진을 모신 조선왕조의 상징적 공간. 대나무숲 산책로가 유명하다.',
  },
  {
    id: 3,
    name: '자만벽화마을',
    category: '체험',
    address: '전북 전주시 완산구 자만동',
    distanceKm: 1.1,
    rating: 4.3,
    reviewCount: 445,
    imageUrl: null,
    tags: ['벽화', '골목산책'],
    description: '언덕 골목을 따라 벽화가 이어지는 마을. 한옥마을 전경이 내려다보인다.',
  },
  {
    id: 4,
    name: '전주향교',
    category: '문화재',
    address: '전북 전주시 완산구 향교길 139',
    distanceKm: 1.3,
    rating: 4.5,
    reviewCount: 367,
    imageUrl: null,
    tags: ['은행나무', '고즈넉'],
    description: '400년 된 은행나무가 있는 조선시대 지방 교육기관. 가을 단풍 명소.',
  },
  {
    id: 5,
    name: '남부시장 야시장',
    category: '맛집',
    address: '전북 전주시 완산구 풍남문2길 53',
    distanceKm: 0.9,
    rating: 4.4,
    reviewCount: 1023,
    imageUrl: null,
    tags: ['야시장', '먹거리'],
    description: '금·토 저녁마다 열리는 야시장. 청년몰과 먹거리 노점이 모여 있다.',
  },
  {
    id: 6,
    name: '오목대',
    category: '자연',
    address: '전북 전주시 완산구 기린대로 55',
    distanceKm: 0.7,
    rating: 4.2,
    reviewCount: 298,
    imageUrl: null,
    tags: ['전망', '노을'],
    description: '한옥마을 전경을 한눈에 담을 수 있는 언덕. 노을·야경 포인트.',
  },
  {
    id: 7,
    name: '전동성당',
    category: '문화재',
    address: '전북 전주시 완산구 태조로 51',
    distanceKm: 0.5,
    rating: 4.6,
    reviewCount: 764,
    imageUrl: null,
    tags: ['로마네스크', '사진명소'],
    description: '호남 최초의 로마네스크 양식 성당. 붉은 벽돌 외관이 아름답다.',
  },
];

export const nearbySpots: TourSpot[] = tourSpots.filter((s) => s.id !== 1).slice(0, 4);

export function getSpotById(id: number): TourSpot | undefined {
  return tourSpots.find((s) => s.id === id);
}
