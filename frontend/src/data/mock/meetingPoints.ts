import type { MeetingPoint } from '../../types';

export const meetingPoints: MeetingPoint[] = [
  {
    id: 201,
    name: '경기전 정문 앞',
    category: '랜드마크',
    description: '찾기 쉽고 유동인구가 많은 개방된 장소. 첫 만남에 가장 무난해요.',
    distanceKm: 0.6,
    walkMinutes: 8,
  },
  {
    id: 202,
    name: '전동성당 광장',
    category: '랜드마크',
    description: '사진 명소라 기다리는 시간도 지루하지 않은 곳.',
    distanceKm: 0.5,
    walkMinutes: 7,
  },
  {
    id: 203,
    name: '한옥마을 관광안내소',
    category: '안내소',
    description: '안내 직원이 상주해 안전하고, 지도를 받아 바로 출발하기 좋아요.',
    distanceKm: 0.4,
    walkMinutes: 5,
  },
  {
    id: 204,
    name: '남부시장 청년몰 입구',
    category: '시장',
    description: '먹거리 일정으로 바로 이어가기 좋은 만남 포인트.',
    distanceKm: 0.9,
    walkMinutes: 12,
  },
];
