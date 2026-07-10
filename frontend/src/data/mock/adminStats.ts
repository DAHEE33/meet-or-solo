import type { AdminStats } from '../../types';

export const adminStats: AdminStats = {
  totalUsers: 1842,
  todayMatches: 37,
  totalCheckIns: 5211,
  popularSpots: [
    { name: '전주 한옥마을', count: 412 },
    { name: '남부시장 야시장', count: 288 },
    { name: '경기전', count: 245 },
    { name: '전동성당', count: 190 },
  ],
  reports: [
    { id: 501, type: '신고', content: '매칭 상대가 약속 장소에 나타나지 않았어요.', createdAt: '2026-07-06T00:12:00Z' },
    { id: 502, type: '문의', content: '체크인 위치 인식이 안 됩니다.', createdAt: '2026-07-05T12:44:00Z' },
    { id: 503, type: '문의', content: '솔로 코스에 다른 지역도 추가되나요?', createdAt: '2026-07-05T05:03:00Z' },
  ],
};
