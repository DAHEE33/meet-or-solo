/**
 * 동의 화면 문구.
 *
 * 회원가입, 프로필 수정, 매칭 유도 화면이 같은 문구를 쓰도록 한 곳에 모은다. 문구를 개정하면
 * 서버 `MemberConsentType.currentVersion()`도 함께 올려야 한다.
 *
 * 사용자에게는 "임베딩" 같은 개발 용어를 쓰지 않고 "취향 전격 분석"으로 통일한다.
 */

export interface ConsentItemNotice {
  title: string;
  summary: string;
  details: { label: string; value: string }[];
}

export const AI_PROCESSING_NOTICE: ConsentItemNotice = {
  title: '취향 글을 분석해 잘 맞는 사람 찾기',
  summary: '직접 쓴 취향 글을 AI가 읽고 비슷한 사람을 추천하는 데 사용해요.',
  details: [
    { label: '이용하는 정보', value: '내가 직접 쓴 취향 글' },
    { label: '이용 목적', value: '취향이 비슷한 사람 추천' },
    { label: '보관 기간', value: '동의를 철회하거나 취향을 삭제할 때까지' },
    {
      label: '동의하지 않으면',
      value: '매칭은 그대로 이용할 수 있고, 여행 스타일 태그만으로 추천해요.',
    },
  ],
};

export const OVERSEAS_TRANSFER_NOTICE: ConsentItemNotice = {
  title: '분석을 위해 취향 글을 해외로 보내기',
  summary: '분석은 미국에 있는 회사의 서비스를 이용해요. 취향 글이 국외로 전송돼요.',
  details: [
    { label: '받는 곳', value: 'OpenAI, L.L.C.' },
    { label: '보내는 국가', value: '미국' },
    { label: '보내는 정보', value: '내가 직접 쓴 취향 글' },
    { label: '보내는 시점과 방법', value: '취향을 저장할 때 암호화된 연결(HTTPS)로 전송' },
    { label: '보내는 목적', value: '취향 글의 의미를 수치로 바꿔 비슷한 사람을 찾기 위해' },
    { label: '보관 기간', value: '동의를 철회하거나 취향을 삭제할 때까지' },
    {
      label: '동의하지 않으면',
      value: '취향 글 분석만 이용할 수 없고, 매칭과 다른 기능은 그대로 이용할 수 있어요.',
    },
  ],
};

/** 닉네임, 성별, 연령대 등은 보내지 않는다는 사실을 함께 알린다. */
export const AI_CONSENT_FOOTNOTE =
  '닉네임, 성별, 연령대, 로그인 정보는 보내지 않아요. 동의는 언제든 프로필 수정에서 철회할 수 있고, 철회하면 저장한 취향 글도 함께 삭제돼요.';

export const SIGNUP_TERMS_LABEL = '이용약관에 동의합니다. (필수)';
export const SIGNUP_PRIVACY_LABEL = '개인정보 수집·이용에 동의합니다. (필수)';
