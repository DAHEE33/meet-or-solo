import type { SanctionNotice } from './types';
import { apiClient, apiClientVoid, buildApiUrl } from './apiClient';

export type Gender = 'MALE' | 'FEMALE' | 'OTHER';
export type AgeRange = '10S' | '20S' | '30S' | '40S' | '50S' | '60_PLUS';
export type TravelStyleCode = 'RELAXED' | 'ACTIVE' | 'FOOD' | 'PHOTO' | 'CULTURE';

export type TravelStyle = {
  code: TravelStyleCode;
  label: string;
};

export type MemberProfile = {
  memberId: number;
  nickname: string;
  email: string | null;
  intro: string | null;
  profileImageUrl: string | null;
  gender: Gender | null;
  ageRange: AgeRange | null;
  status: string;
  /**
   * 본인의 매너온도(docs/19 4.9). 다른 회원의 온도는 어디에도 내려오지 않는다.
   * 낮은 온도는 "신고를 받은 적이 있다"를 그대로 드러내기 때문이다.
   */
  mannerTemperature: number;
  /** 제재 중일 때만 채워진다. 화면이 활동 UI를 미리 막는 데 쓴다. */
  sanction: SanctionNotice | null;
  travelStyles: TravelStyle[];
};

export type UpdateMemberProfileRequest = {
  nickname: string;
  email?: string | null;
  intro?: string | null;
  gender: Gender;
  ageRange: AgeRange;
  travelStyles: TravelStyleCode[];
};

export const memberProfileApi = {
  getMine: (signal?: AbortSignal) =>
    apiClient<MemberProfile>('/api/members/me', { signal }).then(resolveProfileImageUrl),
  complete: (request: UpdateMemberProfileRequest) =>
    apiClient<MemberProfile>('/api/members/me/profile', {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    }),
  /**
   * 회원 탈퇴(docs/19 4.4). 서버가 세션을 폐기하고 cookie를 만료시킨다.
   * 응답 본문이 없어 apiClientVoid를 쓴다.
   */
  withdraw: () => apiClientVoid('/api/members/me', { method: 'DELETE' }),
  uploadImage: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return apiClient<MemberProfile>('/api/members/me/profile-image', {
      method: 'POST',
      body: formData,
    }).then(resolveProfileImageUrl);
  },
};

function resolveProfileImageUrl(profile: MemberProfile): MemberProfile {
  if (!profile.profileImageUrl?.startsWith('/')) return profile;
  return { ...profile, profileImageUrl: buildApiUrl(profile.profileImageUrl) };
}
