import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  LEGAL_DOCUMENT_VERSION,
  legalDocument,
  privacyDocument,
  supportContactEmail,
  termsDocument,
} from './legalDocuments';

function allText(document: ReturnType<typeof termsDocument>): string {
  return document.sections
    .map((section) => `${section.heading}\n${section.paragraphs.join('\n')}`)
    .join('\n');
}

afterEach(() => {
  vi.unstubAllEnvs();
});

describe('문서 버전', () => {
  /**
   * 서버 `MemberConsentType.TERMS/PRIVACY.currentVersion()`이 "1.0"이다. 원문을 개정하면서
   * 이 값만 올리면 "무엇에 동의했는지"를 특정할 수 없게 되므로 양쪽을 함께 올려야 한다.
   */
  it('두 문서가 서버 동의 기록과 같은 버전을 쓴다', () => {
    expect(LEGAL_DOCUMENT_VERSION).toBe('1.0');
    expect(termsDocument().version).toBe(LEGAL_DOCUMENT_VERSION);
    expect(privacyDocument().version).toBe(LEGAL_DOCUMENT_VERSION);
  });

  it('시행일을 함께 표시한다', () => {
    expect(termsDocument().effectiveDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(privacyDocument().effectiveDate).toBe(termsDocument().effectiveDate);
  });

  it('id로 같은 문서를 돌려준다', () => {
    expect(legalDocument('TERMS').title).toBe(termsDocument().title);
    expect(legalDocument('PRIVACY').title).toBe(privacyDocument().title);
  });
});

describe('문의 이메일', () => {
  it('환경변수 값을 원문에 넣는다', () => {
    vi.stubEnv('VITE_SUPPORT_CONTACT_EMAIL', 'help@example.com');
    expect(supportContactEmail()).toBe('help@example.com');
    expect(allText(privacyDocument())).toContain('help@example.com');
    expect(allText(termsDocument())).toContain('help@example.com');
  });

  it('앞뒤 공백을 제거한다', () => {
    vi.stubEnv('VITE_SUPPORT_CONTACT_EMAIL', '  help@example.com \n');
    expect(supportContactEmail()).toBe('help@example.com');
  });

  /**
   * 값이 없을 때 문구를 조용히 지우면 문의처 없는 처리방침이 배포된다. 제재 안내에서 환경변수
   * 누락이 문구를 소리 없이 없앴던 것과 같은 실수를 반복하지 않기 위해 눈에 보이게 남긴다.
   */
  it('환경변수가 비면 설정되지 않았다는 사실을 그대로 노출한다', () => {
    vi.stubEnv('VITE_SUPPORT_CONTACT_EMAIL', '');
    expect(supportContactEmail()).toContain('설정되지 않았습니다');
    expect(allText(privacyDocument())).toContain('VITE_SUPPORT_CONTACT_EMAIL');
  });
});

describe('이용약관', () => {
  it('오프라인 만남의 책임 범위를 별도 조항으로 둔다', () => {
    const text = allText(termsDocument());
    expect(text).toContain('제8조');
    expect(text).toContain('만남 당사자가 아니며');
    // 고의·중과실까지 면책하는 조항은 무효이므로 예외를 함께 적는다.
    expect(text).toContain('고의 또는 중대한 과실');
  });

  it('안전 사고 시 대응 방법을 안내한다', () => {
    expect(allText(termsDocument())).toContain('112');
  });

  it('탈퇴가 제재 회피 수단이 아니라는 것을 명시한다', () => {
    const text = allText(termsDocument());
    expect(text).toContain('7일');
    expect(text).toContain('탈퇴는 제재를 해제하는 수단이 아닙니다');
  });

  it('구현에 없는 자유 대화 기능을 약속하지 않는다', () => {
    expect(allText(termsDocument())).toContain('자유 대화 기능을 제공하지 않습니다');
  });
});

describe('개인정보처리방침', () => {
  it('법정 기재 항목을 모두 절로 둔다', () => {
    const headings = privacyDocument().sections.map((section) => section.heading);
    const required = [
      '수집하는 개인정보 항목',
      '처리 목적',
      '보유·이용 기간',
      '제3자 제공',
      '위탁',
      '국외 이전',
      '정보주체의 권리',
      '안전성 확보 조치',
      '쿠키',
      '개인정보 보호책임자',
      '권익침해 구제',
      '변경',
    ];
    required.forEach((keyword) => {
      expect(headings.some((heading) => heading.includes(keyword))).toBe(true);
    });
  });

  /** 좌표를 저장하지 않는 것은 실제 구현이다. festival_checkins에는 거리만 저장한다. */
  it('위치 좌표를 저장하지 않는다는 사실을 명시한다', () => {
    const text = allText(privacyDocument());
    expect(text).toContain('좌표를 저장하지 않습니다');
    expect(text).toContain('거리');
  });

  it('국외 이전 대상과 보내지 않는 항목을 함께 적는다', () => {
    const text = allText(privacyDocument());
    expect(text).toContain('OpenAI, L.L.C.');
    expect(text).toContain('미국');
    expect(text).toContain('취향 글');
    expect(text).toContain('로그인 정보는 이전되지 않습니다');
  });

  it('탈퇴 시 삭제 항목과 보관 항목을 구분해 적는다', () => {
    const text = allText(privacyDocument());
    expect(text).toContain('지체 없이 삭제합니다');
    expect(text).toContain('신고·제재 기록과 매칭 이력');
    expect(text).toContain('식별할 수 없는 형태');
  });

  it('매칭 상대에게 보이는 범위를 밝힌다', () => {
    const text = allText(privacyDocument());
    expect(text).toContain('닉네임과 프로필 이미지가 표시됩니다');
    expect(text).toContain('취향 글 원문은 상대 회원에게 표시되지 않습니다');
  });
});
