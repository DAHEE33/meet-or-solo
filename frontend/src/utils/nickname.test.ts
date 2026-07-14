import { describe, expect, it } from 'vitest';
import { validateNickname } from './nickname';

describe('validateNickname', () => {
  it('2~12자 한글, 영문, 숫자 닉네임을 허용한다', () => {
    expect(validateNickname('여행자12')).toBeNull();
    expect(validateNickname('soloMate')).toBeNull();
  });

  it('길이가 맞지 않으면 거절한다', () => {
    expect(validateNickname('가')).not.toBeNull();
    expect(validateNickname('열두글자를넘는닉네임')).not.toBeNull();
  });

  it('공백이나 특수문자가 있으면 거절한다', () => {
    expect(validateNickname('여행 자')).not.toBeNull();
    expect(validateNickname('여행자!')).not.toBeNull();
  });
});
