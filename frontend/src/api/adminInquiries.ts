// 관리자 1:1 문의 관리용 데이터 접근 계층.
// 설계는 docs/29_MEMBER_INQUIRY_DESIGN.md 6장을 따른다.

import { apiClient } from './apiClient';
import type { InquiryCategory, InquiryMessage, InquiryStatus } from './inquiries';

export type { InquiryCategory, InquiryMessage, InquiryStatus };

export type InquiryPriority = 'NORMAL' | 'URGENT';

/** 관리자가 직접 지정할 수 있는 상태. `ANSWERED`는 답변 등록으로만 만들어진다. */
export type AdminInquiryTargetStatus = 'IN_PROGRESS' | 'CLOSED';

/** 제재 이의제기 판단에 회원 상태가 필요하다(docs/29 7절). */
export type AdminInquiryMember = {
  memberId: number;
  nickname: string;
  status: string;
};

export type AdminInquiryListItem = {
  inquiryId: number;
  category: InquiryCategory;
  title: string;
  status: InquiryStatus;
  priority: InquiryPriority;
  member: AdminInquiryMember;
  messageCount: number;
  lastMessageAt: string;
  createdAt: string;
};

export type AdminInquiryDetail = {
  inquiryId: number;
  category: InquiryCategory;
  title: string;
  status: InquiryStatus;
  priority: InquiryPriority;
  member: AdminInquiryMember;
  messages: InquiryMessage[];
  lastMessageAt: string;
  lastAnsweredAt: string | null;
  createdAt: string;
  closedAt: string | null;
};

export type AdminInquiryFilters = {
  status: InquiryStatus | '';
  category: InquiryCategory | '';
  priority: InquiryPriority | '';
  createdFrom: string;
  createdTo: string;
};

export type AdminInquiryPage = {
  items: AdminInquiryListItem[];
  /** 미처리(RECEIVED·IN_PROGRESS) 전체 건수. 관리자 메뉴 badge가 쓴다. filter와 무관하다. */
  openCount: number;
  pagination: { size: number; hasNext: boolean; nextCursor: string | null };
};

export const EMPTY_ADMIN_INQUIRY_FILTERS: AdminInquiryFilters = {
  status: '',
  category: '',
  priority: '',
  createdFrom: '',
  createdTo: '',
};

function query(filters: AdminInquiryFilters, cursor: string | null, size: number): string {
  const parameters = new URLSearchParams();
  if (filters.status) parameters.set('status', filters.status);
  if (filters.category) parameters.set('category', filters.category);
  if (filters.priority) parameters.set('priority', filters.priority);
  if (filters.createdFrom) parameters.set('createdFrom', filters.createdFrom);
  if (filters.createdTo) parameters.set('createdTo', filters.createdTo);
  if (cursor) parameters.set('cursor', cursor);
  parameters.set('size', String(size));
  return parameters.toString();
}

export const adminInquiriesApi = {
  list: (
    filters: AdminInquiryFilters,
    cursor: string | null,
    size = 20,
    signal?: AbortSignal,
  ) => apiClient<AdminInquiryPage>(`/api/admin/inquiries?${query(filters, cursor, size)}`, { signal }),

  detail: (inquiryId: number, signal?: AbortSignal) =>
    apiClient<AdminInquiryDetail>(`/api/admin/inquiries/${inquiryId}`, { signal }),

  answer: (inquiryId: number, body: string, signal?: AbortSignal) =>
    apiClient<AdminInquiryDetail>(`/api/admin/inquiries/${inquiryId}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ body }),
      signal,
    }),

  // 상태와 우선순위를 함께 받는다. 긴급 지정이 관리자 전용이라 별도 endpoint를 두지 않았다.
  update: (
    inquiryId: number,
    input: { status?: AdminInquiryTargetStatus; priority?: InquiryPriority },
    signal?: AbortSignal,
  ) =>
    apiClient<AdminInquiryDetail>(`/api/admin/inquiries/${inquiryId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
      signal,
    }),
};
