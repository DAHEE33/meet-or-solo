import { describe, expect, it } from 'vitest';
import { profileImageSrc } from './profileImage';

/**
 * 프로필 사진은 두 모양으로 온다 — 소셜 CDN의 절대 URL, 그리고 직접 올린 사진의 상대 경로
 * (`/api/members/{id}/profile-image`). 상대 경로를 그대로 `<img src>`에 넣으면 API base를
 * 따로 두는 배포에서 깨진다.
 */
describe('profileImageSrc', () => {
  it('업로드 사진의 상대 경로를 API 주소로 바꾼다', () => {
    expect(profileImageSrc('/api/members/7/profile-image'))
      .toContain('/api/members/7/profile-image');
  });

  it('소셜 사진의 절대 URL은 그대로 둔다', () => {
    expect(profileImageSrc('https://cdn.kakao.com/a.jpg')).toBe('https://cdn.kakao.com/a.jpg');
  });

  /** 사진이 없으면 undefined다. 부르는 쪽은 이때 이니셜 아바타를 그린다. */
  it('사진이 없으면 undefined다', () => {
    expect(profileImageSrc(null)).toBeUndefined();
    expect(profileImageSrc(undefined)).toBeUndefined();
    expect(profileImageSrc('')).toBeUndefined();
  });
});
