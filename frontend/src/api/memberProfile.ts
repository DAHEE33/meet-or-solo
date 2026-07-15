import { apiClient, buildApiUrl } from './apiClient';

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
  getMine: () => apiClient<MemberProfile>('/api/members/me').then(resolveProfileImageUrl),
  complete: (request: UpdateMemberProfileRequest) =>
    apiClient<MemberProfile>('/api/members/me/profile', {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    }),
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
