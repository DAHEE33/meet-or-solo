/**
 * 매너온도 정책 값(docs/19 4.9).
 *
 * backend `MannerTemperaturePolicy`와 같은 값이어야 한다. 화면이 더 넓게 열어 두면 관리자가
 * 입력한 뒤에야 400을 받고, 더 좁게 두면 서버가 허용하는 값을 화면이 막는다.
 *
 * 관리자 화면과 회원 화면이 함께 쓰므로 어느 한쪽 모듈에 두지 않는다.
 */

/** 신규 회원의 시작 온도. */
export const MANNER_TEMPERATURE_INITIAL = 36.5;

/** 하한. 어떤 경로로도 이 아래로 내려가지 않는다. */
export const MANNER_TEMPERATURE_FLOOR = 20;

/** 상한. 어떤 경로로도 이 위로 올라가지 않는다. */
export const MANNER_TEMPERATURE_CEILING = 42;
