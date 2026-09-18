import { describe, expect, it } from 'vitest';
import {
  AI_CONSENT_FOOTNOTE,
  AI_PROCESSING_NOTICE,
  OVERSEAS_TRANSFER_NOTICE,
} from './consentNotice';

/**
 * 국외 이전 고지에는 빠뜨리면 안 되는 항목이 있다. 문구를 손볼 때 항목이 사라지는 것을
 * 막기 위해 테스트로 고정한다.
 */
describe('OVERSEAS_TRANSFER_NOTICE', () => {
  const labels = OVERSEAS_TRANSFER_NOTICE.details.map((detail) => detail.label);

  it('국외 이전 고지에 필요한 항목을 모두 담는다', () => {
    expect(labels).toEqual(expect.arrayContaining([
      '받는 곳',
      '보내는 국가',
      '보내는 정보',
      '보내는 시점과 방법',
      '보내는 목적',
      '보관 기간',
      '동의하지 않으면',
    ]));
  });

  it('이전받는 사업자와 국가를 구체적으로 밝힌다', () => {
    const values = Object.fromEntries(
      OVERSEAS_TRANSFER_NOTICE.details.map((detail) => [detail.label, detail.value]),
    );
    expect(values['받는 곳']).toContain('OpenAI');
    expect(values['보내는 국가']).toContain('미국');
  });

  it('동의하지 않아도 매칭을 이용할 수 있다고 알린다', () => {
    const refusal = OVERSEAS_TRANSFER_NOTICE.details
      .find((detail) => detail.label === '동의하지 않으면')?.value ?? '';
    expect(refusal).toContain('매칭');
  });
});

describe('AI_PROCESSING_NOTICE', () => {
  it('이용 목적과 보관 기간, 거부 시 안내를 담는다', () => {
    const labels = AI_PROCESSING_NOTICE.details.map((detail) => detail.label);
    expect(labels).toEqual(expect.arrayContaining([
      '이용하는 정보',
      '이용 목적',
      '보관 기간',
      '동의하지 않으면',
    ]));
  });
});

describe('동의 화면 문구', () => {
  const allText = [
    AI_PROCESSING_NOTICE.title,
    AI_PROCESSING_NOTICE.summary,
    ...AI_PROCESSING_NOTICE.details.flatMap((detail) => [detail.label, detail.value]),
    OVERSEAS_TRANSFER_NOTICE.title,
    OVERSEAS_TRANSFER_NOTICE.summary,
    ...OVERSEAS_TRANSFER_NOTICE.details.flatMap((detail) => [detail.label, detail.value]),
    AI_CONSENT_FOOTNOTE,
  ].join(' ');

  it('개발 용어를 화면 문구에 쓰지 않는다', () => {
    for (const term of ['임베딩', '벡터', 'embedding', 'API', '코사인']) {
      expect(allText).not.toContain(term);
    }
  });

  it('보내지 않는 정보와 철회 시 삭제를 함께 알린다', () => {
    expect(AI_CONSENT_FOOTNOTE).toContain('닉네임');
    expect(AI_CONSENT_FOOTNOTE).toContain('철회');
    expect(AI_CONSENT_FOOTNOTE).toContain('삭제');
  });
});
