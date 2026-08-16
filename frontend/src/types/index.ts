// 도메인 타입 정의 — Spring Boot API 스펙 확정 시 이 파일만 맞추면 됨

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

export type SpotCategory = '역사' | '자연' | '체험' | '문화' | '액티비티' | '맛집' | '카페' | '쇼핑';

export type TravelStyle = '느긋하게' | '액티브' | '맛집탐방' | '사진위주' | '문화답사';

export interface TourSpot {
  id: number;
  name: string;
  /** 관광공사 동기화 데이터에는 아직 세부 카테고리가 없어 mock 전용으로 optional */
  category?: SpotCategory;
  address: string;
  /** GPS 체크인 기능 도입 전까지는 실 API에서 제공하지 않는 optional 값 */
  distanceKm?: number;
  /** 리뷰 도메인 구현 전까지는 실 API에서 제공하지 않는 optional 값 */
  rating?: number;
  reviewCount?: number;
  imageUrl: string | null; // null이면 플레이스홀더 렌더링
  tags?: string[];
  description?: string;
}

export interface UserProfile {
  id: number;
  nickname: string;
  email: string;
  currentAreaName: string; // 예: "전주 한옥마을"
  travelStyles: TravelStyle[];
  intro?: string;
}

export interface CourseStop {
  order: number;
  spotName: string;
  stayMinutes: number;
  note: string;
}

export interface SoloCourse {
  id: number;
  type: 'half' | 'full'; // 반나절 | 하루
  title: string;
  summary: string;
  durationHours: number;
  reason: string; // 추천 이유
  tags: string[];
  stops: CourseStop[];
}

export type FestivalCategory =
  | '문화관광'
  | '문화예술'
  | '지역특산'
  | '전통역사'
  | '생태자연'
  | '기타';

export type FestivalStatus = 'ongoing' | 'upcoming' | 'ended';

export interface InfoRow {
  label: string;
  value: string;
}

export interface FestivalProgram {
  name: string;
  desc: string;
  time: string;
}

/** 축제 상세 "축제와 함께 둘러보기"용 근접 관광지 관계 (관광지 id + 축제 기준 거리) */
export interface FestivalNearbyPlace {
  spotId: number;
  distanceKm: number;
}

export interface Festival {
  id: number;
  name: string;
  /** 관광공사 동기화 데이터에는 아직 세부 카테고리가 없어 mock 전용으로 optional */
  category?: FestivalCategory;
  status: FestivalStatus;
  /** status가 'upcoming'일 때 노출되는 D-day 뱃지 문구 (예: 'D-3') */
  ddayLabel: string;
  periodShort: string; // 예: '7.24 – 7.28'
  periodFull: string; // 예: '2026.07.24 – 2026.07.28'
  /** mock 전용 장소명. 실 API는 address만 제공하므로 optional */
  place?: string;
  /** mock 전용 지역명. 실 API는 regionCode만 제공하므로 optional */
  region?: string;
  /** GPS 체크인 기능 도입 전까지는 실 API에서 제공하지 않는 optional 값 */
  distanceKm?: number;
  expectedAttendees?: number;
  /** 매칭 도메인 구현 전까지는 실 API에서 제공하지 않는 optional 값 */
  matchingCount?: number;
  matchSupported?: boolean;
  intro: string;
  address: string;
  thumbnailUrl: string | null; // null이면 플레이스홀더 렌더링
  infoItems: InfoRow[];
  programs: FestivalProgram[];
  nearbyPlaces: FestivalNearbyPlace[];
}

export interface CheckInRecord {
  id: number;
  spotName: string;
  checkedInAt: string; // ISO
  memo?: string;
}

export interface AdminReport {
  id: number;
  type: '신고' | '문의';
  content: string;
  createdAt: string;
}

export interface AdminStats {
  totalUsers: number;
  todayMatches: number;
  totalCheckIns: number;
  popularSpots: { name: string; count: number }[];
  reports: AdminReport[];
}
