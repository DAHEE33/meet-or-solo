export type FieldError = {
  field: string;
  message: string;
};

/**
 * 로그인이나 활동이 막힌 응답에 담기는 사유·기간 안내.
 *
 * 신고자 보호를 위해 서버가 제재 시작 시각과 신고 관련 값을 담지 않는다.
 * 화면에서 사유 문구를 다시 만들지 않고 서버가 준 reasonMessage를 그대로 쓴다.
 *
 * 제재(docs/19 4.8)와 탈퇴 재가입 제한(docs/19 4.4)을 함께 담는다. 로그인 화면이 안내를
 * 읽는 경로를 하나로 유지하기 위한 것이고, WITHDRAWN이면 rejoinAvailableAt으로 구분한다.
 */
export type SanctionNotice = {
  status: 'SUSPENDED' | 'BANNED' | 'WITHDRAWN';
  /** 정지 종료 시각. 영구 제한(BANNED)과 탈퇴(WITHDRAWN)는 기간이 없어 null이다. */
  suspendedUntil: string | null;
  reasonCode: string;
  reasonMessage: string;
  /** 고객센터 이메일. 설정되지 않았으면 null이다. */
  contactEmail: string | null;
  /** 재가입 가능 시각. 탈퇴 재가입 제한일 때만 값이 있고, 영구 거부이면 null이다. */
  rejoinAvailableAt: string | null;
};

export type ApiError = {
  code: string;
  message: string;
  fields?: FieldError[];
  sanction?: SanctionNotice | null;
};

export type ApiResponse<T> = {
  success: boolean;
  data: T | null;
  error: ApiError | null;
};
