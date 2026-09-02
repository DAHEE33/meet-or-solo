import { describe, expect, it } from 'vitest';
import { buildSmsShareUrl, isMobileUserAgent } from './share';

describe('isMobileUserAgent', () => {
  it('Android User-Agent는 모바일로 판별한다', () => {
    expect(
      isMobileUserAgent(
        'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36',
      ),
    ).toBe(true);
  });

  it('iPhone User-Agent는 모바일로 판별한다', () => {
    expect(
      isMobileUserAgent('Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15'),
    ).toBe(true);
  });

  it('데스크톱 Chrome User-Agent는 모바일이 아니다', () => {
    expect(
      isMobileUserAgent(
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0 Safari/537.36',
      ),
    ).toBe(false);
  });

  it('데스크톱 macOS User-Agent는 모바일이 아니다', () => {
    expect(
      isMobileUserAgent('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15'),
    ).toBe(false);
  });
});

describe('buildSmsShareUrl', () => {
  it('제목과 링크를 본문에 담아 sms: 딥링크를 만든다', () => {
    const url = buildSmsShareUrl('춘천 마임축제', 'https://meet-or-solo.example/festivals/1');

    expect(url).toBe(
      'sms:?body=' + encodeURIComponent('춘천 마임축제 https://meet-or-solo.example/festivals/1'),
    );
  });
});
