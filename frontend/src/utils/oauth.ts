export type OAuthProvider = 'kakao' | 'naver';

export function getOAuthLoginPath(provider: OAuthProvider) {
  return `/api/auth/${provider}/login`;
}
