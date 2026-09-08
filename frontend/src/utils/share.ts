// 공유 시트(ShareSheet)가 쓰는 순수 함수. 실제 클립보드 복사는 컴포넌트에서
// navigator.clipboard를 직접 호출하고, 여기는 테스트 가능한 판별/문자열 조립만 둔다.

/**
 * 문자(SMS) 공유 버튼을 보여줄지 판별한다. 데스크톱은 SMS 앱이 없어 `sms:` 링크를
 * 눌러도 반응이 없으므로, 모바일 브라우저에서만 노출한다.
 */
export function isMobileUserAgent(userAgent: string): boolean {
  return /Android|iPhone|iPad|iPod/i.test(userAgent);
}

/** 제목과 링크를 하나의 본문으로 합쳐 문자 공유 딥링크를 만든다. */
export function buildSmsShareUrl(title: string, url: string): string {
  return `sms:?body=${encodeURIComponent(`${title} ${url}`)}`;
}
