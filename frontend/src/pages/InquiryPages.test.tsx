import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import MyInquiriesPage from './MyInquiriesPage';
import InquiryNewPage, {
  inquiryCreateErrorMessage,
  resolveInitialCategory,
} from './InquiryNewPage';
import InquiryDetailPage, {
  inquiryReplyErrorMessage,
  resolveInquiryId,
} from './InquiryDetailPage';
import AdminInquiriesPage, { toApiFilters, toSeoulOffset } from './AdminInquiriesPage';
import { EMPTY_ADMIN_INQUIRY_FILTERS } from '../api/adminInquiries';
import {
  inquiryCategoryLabel,
  inquiryStatusClass,
  inquiryStatusLabel,
} from '../api/inquiries';
import { ApiClientError } from '../api/apiClient';

// useEffect가 돌지 않는 SSR 마크업이라 초기 상태(LOADING)가 그대로 나온다.
const render = (element: React.ReactElement) =>
  renderToStaticMarkup(<MemoryRouter>{element}</MemoryRouter>);

describe('MyInquiriesPage', () => {
  it('문의하기 진입점과 로딩 상태를 그린다', () => {
    const html = render(<MyInquiriesPage />);
    expect(html).toContain('1:1 문의');
    expect(html).toContain('문의하기');
    expect(html).toContain('/mypage/inquiries/new');
    expect(html).toContain('문의를 불러오는 중이에요');
    expect(html).toContain('aria-busy="true"');
  });
});

describe('InquiryNewPage', () => {
  it('본문을 평문 저장하므로 개인정보 입력 자제를 안내한다', () => {
    // docs/28 3.1 — 암호화하지 않는 대신 입력 단계에서 안내한다.
    const html = render(<InquiryNewPage />);
    expect(html).toContain('개인정보는 적지 말아 주세요');
  });

  it('동행 중 문제는 신고 기능으로 안내한다', () => {
    // 안전 카테고리를 두지 않는 결정의 화면 쪽 대응이다(docs/28 3.4).
    const html = render(<InquiryNewPage />);
    expect(html).toContain('신고 기능을 이용해 주세요');
  });

  it('긴급 선택 입력을 노출하지 않는다', () => {
    // 긴급 지정은 관리자만 한다(docs/28 확정 5번).
    const html = render(<InquiryNewPage />);
    expect(html).not.toContain('긴급');
  });

  it('모든 문의 유형을 선택지로 제공한다', () => {
    const html = render(<InquiryNewPage />);
    expect(html).toContain('이용정지 이의제기');
    expect(html).toContain('계정·로그인');
    expect(html).toContain('오류 제보');
  });
});

describe('resolveInitialCategory', () => {
  it('제재 안내에서 넘어온 카테고리를 그대로 선택한다', () => {
    expect(resolveInitialCategory('SANCTION_APPEAL')).toBe('SANCTION_APPEAL');
  });

  it('없는 값이나 위조된 값은 기타로 떨어진다', () => {
    expect(resolveInitialCategory(null)).toBe('ETC');
    expect(resolveInitialCategory('')).toBe('ETC');
    expect(resolveInitialCategory('SAFETY')).toBe('ETC');
    expect(resolveInitialCategory('<script>')).toBe('ETC');
  });
});

describe('inquiryCreateErrorMessage', () => {
  it('미답변 누적 제한은 원인을 구분해 안내한다', () => {
    const error = new ApiClientError('too many', 429, 'INQUIRY_TOO_MANY_OPEN', undefined);
    expect(inquiryCreateErrorMessage(error)).toContain('답변을 기다리는 문의가 많아요');
  });

  it('검증 실패는 입력 확인을 안내한다', () => {
    const error = new ApiClientError('invalid', 400, 'INQUIRY_INVALID_REQUEST', undefined);
    expect(inquiryCreateErrorMessage(error)).toContain('제목과 내용을 다시 확인해');
  });

  it('알 수 없는 오류는 재시도 안내로 떨어진다', () => {
    expect(inquiryCreateErrorMessage(new Error('boom'))).toContain('잠시 후 다시 시도해 주세요');
  });
});

describe('inquiryReplyErrorMessage', () => {
  it('종결된 문의는 새 문의 등록을 안내한다', () => {
    const error = new ApiClientError('closed', 409, 'INQUIRY_CLOSED', undefined);
    expect(inquiryReplyErrorMessage(error)).toContain('새 문의로 등록해 주세요');
  });

  it('그 외 오류는 재시도 안내로 떨어진다', () => {
    expect(inquiryReplyErrorMessage(new Error('boom'))).toContain('잠시 후 다시 시도해 주세요');
  });
});

describe('InquiryDetailPage', () => {
  it('초기 로딩 상태를 그린다', () => {
    const html = render(<InquiryDetailPage />);
    expect(html).toContain('문의 상세');
    expect(html).toContain('문의를 불러오는 중이에요');
    expect(html).toContain('aria-busy="true"');
  });
});

describe('resolveInquiryId', () => {
  it('양의 정수만 문의 id로 받는다', () => {
    expect(resolveInquiryId('12')).toBe(12);
  });

  it('숫자가 아니거나 범위를 벗어난 값은 null이다 — 화면이 찾을 수 없음으로 떨어진다', () => {
    expect(resolveInquiryId(undefined)).toBeNull();
    expect(resolveInquiryId('')).toBeNull();
    expect(resolveInquiryId('  ')).toBeNull();
    expect(resolveInquiryId('abc')).toBeNull();
    expect(resolveInquiryId('0')).toBeNull();
    expect(resolveInquiryId('-3')).toBeNull();
    expect(resolveInquiryId('1.5')).toBeNull();
  });
});

describe('AdminInquiriesPage', () => {
  it('관리자 헤더와 필터를 그린다', () => {
    const html = render(<AdminInquiriesPage />);
    expect(html).toContain('관리자 문의 관리');
    expect(html).toContain('aria-label="문의 상태"');
    expect(html).toContain('aria-label="문의 유형"');
    expect(html).toContain('aria-label="문의 우선순위"');
  });
});

describe('toSeoulOffset / toApiFilters', () => {
  it('datetime-local 값에 KST offset을 붙인다', () => {
    expect(toSeoulOffset('2026-09-09T12:30')).toBe('2026-09-09T12:30:00+09:00');
  });

  it('빈 값은 빈 문자열로 남긴다 — filter 미적용이 되어야 한다', () => {
    expect(toSeoulOffset('')).toBe('');
    expect(toApiFilters(EMPTY_ADMIN_INQUIRY_FILTERS)).toEqual(EMPTY_ADMIN_INQUIRY_FILTERS);
  });

  it('다른 filter 값은 그대로 통과시킨다', () => {
    expect(toApiFilters({
      status: 'RECEIVED',
      category: 'BUG',
      priority: 'URGENT',
      createdFrom: '2026-09-01T00:00',
      createdTo: '',
    })).toEqual({
      status: 'RECEIVED',
      category: 'BUG',
      priority: 'URGENT',
      createdFrom: '2026-09-01T00:00:00+09:00',
      createdTo: '',
    });
  });
});

describe('문의 라벨', () => {
  it('카테고리 라벨은 안전 분류를 포함하지 않는다', () => {
    expect(inquiryCategoryLabel('SANCTION_APPEAL')).toBe('이용정지 이의제기');
    expect(inquiryCategoryLabel('MATCHING')).toBe('동행 매칭');
  });

  it('상태 라벨은 4종을 모두 옮긴다', () => {
    expect(inquiryStatusLabel('RECEIVED')).toBe('접수');
    expect(inquiryStatusLabel('IN_PROGRESS')).toBe('확인 중');
    expect(inquiryStatusLabel('ANSWERED')).toBe('답변 완료');
    expect(inquiryStatusLabel('CLOSED')).toBe('종결');
  });

  it('답변 완료와 종결의 배지 색을 구분한다', () => {
    // 사용자가 확인해야 하는 상태(답변 완료)만 강조한다.
    expect(inquiryStatusClass('ANSWERED')).not.toBe(inquiryStatusClass('CLOSED'));
    expect(inquiryStatusClass('RECEIVED')).toBe(inquiryStatusClass('IN_PROGRESS'));
  });
});
