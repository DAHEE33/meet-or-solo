// 홈 화면용 데이터 접근 계층.
// 지금은 mock을 반환하지만, Spring Boot 연동 시 이 파일의 함수 내부만
// fetch(`${API_BASE}/api/...`)로 교체하면 페이지 코드는 그대로 유지된다.

import type { Festival, UserProfile } from '../types';
import { hotFestival, upcomingFestivals, homeNearbyPlaces, type HomeNearbyPlace } from '../data/mock/festivals';
import { mockUser } from '../data/mock/user';

const delay = (ms: number) => new Promise((r) => setTimeout(r, ms));

export async function getCurrentUser(): Promise<UserProfile> {
  await delay(100);
  return mockUser;
}

export async function getHotFestival(): Promise<Festival> {
  await delay(150);
  return hotFestival;
}

export async function getUpcomingFestivals(): Promise<Festival[]> {
  await delay(200);
  return upcomingFestivals;
}

export async function getHomeNearbyPlaces(): Promise<HomeNearbyPlace[]> {
  await delay(200);
  return homeNearbyPlaces;
}
