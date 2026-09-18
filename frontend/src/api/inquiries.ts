// 사용자 1:1 문의용 데이터 접근 계층.
// 설계는 docs/29_MEMBER_INQUIRY_DESIGN.md 6장을 따른다.

import { apiClient } from './apiClient';

export type InquiryCategory =
  | 'SANCTION_APPEAL'
  | 'ACCOUNT'
  | 'MATCHING'
  | 'FESTIVAL_DATA'
  | 'BUG'
  | 'ETC';

export type InquiryStatus = 'RECEIVED' | 'IN_PROGRESS' | 'ANSWERED' | 'CLOSED';

export type InquiryMessageAuthorType = 'USER' | 'ADMIN';

/**
 * 스레드 발화 1건.
 *
 * 관리자 발화에도 작성자 `memberId`·닉네임이 없다. 화면은 `authorType`만 보고 "운영팀"으로
 * 표시하며, 관리자 개인을 특정할 이유가 없다(docs/29 7절).
 */
export type InquiryMessage = {
  messageId: number;
  authorType: InquiryMessageAuthorType;
  body: string;
  createdAt: string;
};

/**
 * 목록 항목.
 *
 * `hasUnreadAnswer`가 유일한 답변 도달 신호다. 관리자 답변을 밀어줄 채널(STOMP·Web Push·메일)이
 * 없기 때문이다(docs/29 2.2).
 */
export type InquiryListItem = {
  inquiryId: number;
  category: InquiryCategory;
  title: string;
  status: InquiryStatus;
  hasUnreadAnswer: boolean;
  lastMessageAt: string;
  createdAt: string;
};

export type InquiryPage = {
  items: InquiryListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
};

export type InquiryDetail = {
  inquiryId: number;
  category: InquiryCategory;
  title: string;
  status: InquiryStatus;
  messages: InquiryMessage[];
  createdAt: string;
  closedAt: string | null;
};

export const INQUIRY_BODY_MAX_LENGTH = 2000;
export const INQUIRY_TITLE_MAX_LENGTH = 100;

export const inquiriesApi = {
  getMine: (page = 0, size = 20, signal?: AbortSignal) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    return apiClient<InquiryPage>(`/api/members/me/inquiries?${params.toString()}`, { signal });
  },

  // MyPage badge 전용. 목록 전체를 불러오지 않는다.
  getUnreadCount: (signal?: AbortSignal) =>
    apiClient<{ count: number }>('/api/members/me/inquiries/unread-count', { signal }),

  // 조회가 열람 시각을 갱신하므로 badge가 이 호출로 꺼진다(docs/29 5.3).
  getDetail: (inquiryId: number, signal?: AbortSignal) =>
    apiClient<InquiryDetail>(`/api/members/me/inquiries/${inquiryId}`, { signal }),

  // priority를 보내지 않는다. 긴급 지정은 관리자만 한다(docs/29 확정 5번).
  create: (
    input: { category: InquiryCategory; title: string; body: string },
    signal?: AbortSignal,
  ) =>
    apiClient<InquiryDetail>('/api/members/me/inquiries', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
      signal,
    }),

  addMessage: (inquiryId: number, body: string, signal?: AbortSignal) =>
    apiClient<InquiryDetail>(`/api/members/me/inquiries/${inquiryId}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ body }),
      signal,
    }),
};

const CATEGORY_LABELS: Record<InquiryCategory, string> = {
  SANCTION_APPEAL: '이용정지 이의제기',
  ACCOUNT: '계정·로그인',
  MATCHING: '동행 매칭',
  FESTIVAL_DATA: '축제·관광지 정보',
  BUG: '오류 제보',
  ETC: '기타',
};

const STATUS_LABELS: Record<InquiryStatus, string> = {
  RECEIVED: '접수',
  IN_PROGRESS: '확인 중',
  ANSWERED: '답변 완료',
  CLOSED: '종결',
};

export function inquiryCategoryLabel(category: InquiryCategory): string {
  return CATEGORY_LABELS[category] ?? '기타';
}

export function inquiryStatusLabel(status: InquiryStatus): string {
  return STATUS_LABELS[status] ?? status;
}

/** 상태 배지 색. 답변 완료만 강조한다 — 사용자가 확인해야 하는 상태다. */
export function inquiryStatusClass(status: InquiryStatus): string {
  if (status === 'ANSWERED') return 'bg-teal/10 text-teal';
  if (status === 'CLOSED') return 'bg-ink/[0.06] text-ink/50';
  return 'bg-coral/10 text-coral';
}

/** 사용자가 선택할 수 있는 카테고리. 안전(신고 성격) 분류는 두지 않는다(docs/29 3.4). */
export const INQUIRY_CATEGORY_OPTIONS: InquiryCategory[] = [
  'SANCTION_APPEAL',
  'ACCOUNT',
  'MATCHING',
  'FESTIVAL_DATA',
  'BUG',
  'ETC',
];
