import { describe, expect, it } from 'vitest';
import { truncateText, EXPANDABLE_TEXT_LIMIT } from './ExpandableText';

describe('truncateText', () => {
  it('limit 이하 텍스트는 그대로 반환한다', () => {
    const text = 'a'.repeat(EXPANDABLE_TEXT_LIMIT);
    expect(truncateText(text)).toBe(text);
  });

  it('limit을 넘으면 limit자까지 자르고 말줄임표를 붙인다', () => {
    const text = 'a'.repeat(EXPANDABLE_TEXT_LIMIT + 50);
    const result = truncateText(text);
    expect(result).toBe(`${'a'.repeat(EXPANDABLE_TEXT_LIMIT)}...`);
  });

  it('커스텀 limit을 지정할 수 있다', () => {
    expect(truncateText('12345678', 5)).toBe('12345...');
  });

  it('빈 문자열은 그대로 반환한다', () => {
    expect(truncateText('')).toBe('');
  });
});
