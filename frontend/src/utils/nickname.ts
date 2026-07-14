export const NICKNAME_MIN_LENGTH = 2;
export const NICKNAME_MAX_LENGTH = 12;
export const NICKNAME_RULE_MESSAGE = '닉네임은 2~12자, 한글/영문/숫자만 사용할 수 있습니다.';

const NICKNAME_PATTERN = /^[가-힣A-Za-z0-9]+$/;

export function validateNickname(nickname: string): string | null {
  const trimmedNickname = nickname.trim();
  if (
    trimmedNickname.length < NICKNAME_MIN_LENGTH ||
    trimmedNickname.length > NICKNAME_MAX_LENGTH ||
    !NICKNAME_PATTERN.test(trimmedNickname)
  ) {
    return NICKNAME_RULE_MESSAGE;
  }
  return null;
}
