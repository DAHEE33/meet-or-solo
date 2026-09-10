import { apiClient } from './apiClient';
// 정책 값은 회원 화면과 공유하므로 mannerTemperature 모듈이 단독으로 정의한다.
export { MANNER_TEMPERATURE_CEILING, MANNER_TEMPERATURE_FLOOR, MANNER_TEMPERATURE_INITIAL } from './mannerTemperature';

export type AdminMemberStatus = 'ACTIVE' | 'PROFILE_REQUIRED' | 'SUSPENDED' | 'BANNED' | 'WITHDRAWN' | 'DELETED';
export type AdminMemberActionType = 'WARNING' | 'SUSPEND' | 'BAN' | 'UNBAN' | 'UNSUSPEND';
export type AdminMemberActionReasonCode = 'COMMUNITY_GUIDELINE' | 'HARASSMENT' | 'NO_SHOW_ABUSE' | 'FRAUD_OR_SCAM' | 'SAFETY_RISK' | 'ADMIN_CORRECTION' | 'OTHER';
export type AdminSuspensionDuration = 'ONE_DAY' | 'THREE_DAYS' | 'SEVEN_DAYS' | 'THIRTY_DAYS';

export type AdminMemberListItem = {
  memberId: number; nickname: string | null; profileImageUrl: string | null; role: 'USER' | 'ADMIN';
  status: AdminMemberStatus; penaltyScore: number; mannerTemperature: number;
  suspendedUntil: string | null; createdAt: string;
};
export type AdminMemberDetail = AdminMemberListItem & {
  suspendedAt: string | null; lastLoginAt: string | null;
  /** 최근 30일 누적 유효 신고 건수. 같은 만남의 사유별 중복은 1건으로 압축한다. */
  recentValidReportCount: number;
  /** 누적 유효 신고가 임계에 도달해 이용 제한을 검토해야 하는 회원인지. */
  safetyReviewRequired: boolean;
  reports: Array<{ reportId: number; reasonCode: string; status: string; createdAt: string; resolvedAt: string | null }>;
  actions: Array<{ actionId: number; actionType: AdminMemberActionType; reasonCode: AdminMemberActionReasonCode; reasonNote: string | null; reportId: number | null; createdAt: string }>;
  /** 관리자 매너온도 수동 조정 이력. 제재 이력과 성격이 달라 목록을 나눠 둔다(docs/19 4.9). */
  mannerTemperatureAdjustments: Array<{ actionId: number; beforeTemperature: number; afterTemperature: number; reasonCode: AdminMemberActionReasonCode; reasonNote: string | null; createdAt: string }>;
};
export type AdminMemberFilters = { query: string; status: AdminMemberStatus | ''; role: 'USER' | 'ADMIN' | '' };
export type AdminMemberPage = { items: AdminMemberListItem[]; pagination: { size: number; hasNext: boolean; nextCursor: string | null } };
export type AdminMemberActionRequest = {
  action: AdminMemberActionType; reasonCode: AdminMemberActionReasonCode; reasonNote: string | null;
  suspensionDuration: AdminSuspensionDuration | null; reportId: number | null; expectedStatus: AdminMemberStatus;
};

/**
 * 관리자 강제 탈퇴 요청(docs/19 4.4).
 *
 * 제재 조치(AdminMemberActionRequest)와 타입을 분리한다. 영구차단은 되돌릴 수 있고
 * 강제 탈퇴는 익명화라 되돌릴 수 없어서, 같은 요청 타입으로 섞으면 화면에서 구분이 흐려진다.
 */
export type AdminMemberForcedWithdrawalRequest = {
  reasonCode: AdminMemberActionReasonCode; reasonNote: string | null;
  expectedStatus: AdminMemberStatus;
  /** 재가입 영구 거부 여부. 제재성 강제 탈퇴는 true, 로그인 못 하는 회원의 탈퇴 대행은 false. */
  blockRejoin: boolean;
};

/**
 * 관리자 매너온도 수동 조정 요청(docs/19 4.9).
 *
 * 제재 조치(AdminMemberActionRequest)와 타입을 분리한다. 제재는 회원 상태를 바꾸고 온도
 * 조정은 상태를 전혀 바꾸지 않아, 낙관적 잠금 대상 자체가 다르다.
 */
export type AdminMemberMannerTemperatureRequest = {
  /** 조정 후 목표값. 차감량이 아니다. */
  targetTemperature: number;
  /** 화면에서 본 현재 값. 서버 값과 다르면 409로 거절된다. */
  expectedTemperature: number;
  reasonCode: AdminMemberActionReasonCode; reasonNote: string | null;
};


function query(filters: AdminMemberFilters, cursor: string | null, size: number) {
  const value = new URLSearchParams();
  if (filters.query) value.set('query', filters.query);
  if (filters.status) value.set('status', filters.status);
  if (filters.role) value.set('role', filters.role);
  if (cursor) value.set('cursor', cursor);
  value.set('size', String(size));
  return value.toString();
}

export const adminMembersApi = {
  list: (filters: AdminMemberFilters, cursor: string | null, size = 20, signal?: AbortSignal) =>
    apiClient<AdminMemberPage>(`/api/admin/members?${query(filters, cursor, size)}`, { signal }),
  detail: (memberId: number, signal?: AbortSignal) =>
    apiClient<AdminMemberDetail>(`/api/admin/members/${memberId}`, { signal }),
  act: (memberId: number, request: AdminMemberActionRequest, idempotencyKey: string, signal?: AbortSignal) =>
    apiClient<AdminMemberDetail>(`/api/admin/members/${memberId}/actions`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify(request), signal,
    }),
  forceWithdraw: (
    memberId: number, request: AdminMemberForcedWithdrawalRequest,
    idempotencyKey: string, signal?: AbortSignal,
  ) =>
    apiClient<AdminMemberDetail>(`/api/admin/members/${memberId}/forced-withdrawal`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify(request), signal,
    }),
  adjustMannerTemperature: (
    memberId: number, request: AdminMemberMannerTemperatureRequest,
    idempotencyKey: string, signal?: AbortSignal,
  ) =>
    apiClient<AdminMemberDetail>(`/api/admin/members/${memberId}/manner-temperature`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify(request), signal,
    }),
};
