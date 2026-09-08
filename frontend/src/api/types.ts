export type FieldError = {
  field: string;
  message: string;
};

/**
 * 제재로 막힌 응답에만 담기는 사유·기간 안내.
 *
 * 신고자 보호를 위해 서버가 제재 시작 시각과 신고 관련 값을 담지 않는다.
 * 화면에서 사유 문구를 다시 만들지 않고 서버가 준 reasonMessage를 그대로 쓴다.
 */
export type SanctionNotice = {
  status: 'SUSPENDED' | 'BANNED';
  /** 정지 종료 시각. 영구 제한(BANNED)은 기간이 없어 null이다. */
  suspendedUntil: string | null;
  reasonCode: string;
  reasonMessage: string;
  /** 고객센터 이메일. 설정되지 않았으면 null이다. */
  contactEmail: string | null;
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
