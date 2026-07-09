import type { SoloCourse } from '../../types';

export const soloCourses: SoloCourse[] = [
  {
    id: 301,
    type: 'half',
    title: '한옥마을 감성 반나절',
    summary: '핵심 명소만 천천히 도는 4시간 코스',
    durationHours: 4,
    reason: '현재 위치에서 도보로만 이동 가능하고, 혼자 다니기 좋은 개방된 장소 위주예요.',
    tags: ['도보', '사진', '카페'],
    stops: [
      { order: 1, spotName: '경기전', stayMinutes: 60, note: '대나무숲 산책과 어진박물관' },
      { order: 2, spotName: '전동성당', stayMinutes: 30, note: '외관 사진 포인트' },
      { order: 3, spotName: '한옥마을 골목', stayMinutes: 90, note: '전통 찻집에서 휴식' },
      { order: 4, spotName: '오목대', stayMinutes: 40, note: '노을 시간에 맞춰 전망 감상' },
    ],
  },
  {
    id: 302,
    type: 'full',
    title: '전주 완전 정복 하루',
    summary: '낮의 문화답사부터 야시장까지 8시간 코스',
    durationHours: 8,
    reason: '혼자여도 심심할 틈 없이 체험·먹거리·야경이 골고루 섞인 구성입니다.',
    tags: ['체험', '야시장', '야경'],
    stops: [
      { order: 1, spotName: '전주향교', stayMinutes: 50, note: '은행나무와 고즈넉한 아침' },
      { order: 2, spotName: '자만벽화마을', stayMinutes: 60, note: '골목 산책과 전망' },
      { order: 3, spotName: '경기전', stayMinutes: 60, note: '해설 투어 추천' },
      { order: 4, spotName: '한옥마을 골목', stayMinutes: 120, note: '한복 체험 + 점심' },
      { order: 5, spotName: '오목대', stayMinutes: 40, note: '노을 전망' },
      { order: 6, spotName: '남부시장 야시장', stayMinutes: 90, note: '저녁 먹거리 (금·토)' },
    ],
  },
];
