import { buildApiUrl } from '../api/apiClient';

/**
 * 프로필 사진을 `<img src>`에 넣을 수 있는 주소로 바꾼다.
 *
 * <p>서버는 두 가지 모양으로 준다.
 *
 * <ul>
 *   <li>카카오·네이버 사진 — 그쪽 CDN의 <b>절대 URL</b></li>
 *   <li>직접 올린 사진 — 우리 서버를 거치는 <b>상대 경로</b>
 *       (`/api/members/{id}/profile-image`). private bucket에 있어 중계가 필요하다.</li>
 * </ul>
 *
 * <p>상대 경로를 그대로 넣으면 API base를 따로 두는 배포에서 깨진다. 프로필 화면이 자기
 * 사진에 하는 처리와 같다(`memberProfile.ts`).
 *
 * <p>사진이 없으면 {@code undefined}를 돌려준다 — 부르는 쪽은 이때 이니셜 아바타를 그린다.
 */
export function profileImageSrc(url: string | null | undefined): string | undefined {
  if (!url) return undefined;
  return url.startsWith('/') ? buildApiUrl(url) : url;
}
