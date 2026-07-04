export type FieldError = {
  field: string;
  message: string;
};

export type ApiError = {
  code: string;
  message: string;
  fields?: FieldError[];
};

export type ApiResponse<T> = {
  success: boolean;
  data: T | null;
  error: ApiError | null;
};
