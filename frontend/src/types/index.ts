// 도메인 타입 정의 — Spring Boot API 스펙 확정 시 이 파일만 맞추면 됨

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

export type SpotCategory = '문화재' | '자연' | '체험' | '맛집' | '카페' | '야경';

export type TravelStyle = '느긋하게' | '액티브' | '맛집탐방' | '사진위주' | '문화답사';

export interface TourSpot {
  id: number;
  name: string;
  category: SpotCategory;
  address: string;
  distanceKm: number;
  rating: number;
  reviewCount: number;
  imageUrl: string | null; // null이면 플레이스홀더 렌더링
  tags: string[];
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

export interface MatchCandidate {
  id: number;
  nickname: string;
  ageBand: string; // 예: "20대"
  travelStyles: TravelStyle[];
  interests: string[];
  distanceKm: number;
  matchRate: number; // 0~100
  intro: string;
}

export interface MeetingPoint {
  id: number;
  name: string;
  category: string;
  description: string;
  distanceKm: number;
  walkMinutes: number;
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
