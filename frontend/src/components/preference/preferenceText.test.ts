import { describe, expect, it } from 'vitest';
import {
  EMPTY_PREFERENCE_DRAFT,
  buildPreferenceText,
  isPreferenceDraftComplete,
  parsePreferenceText,
  preferenceTextLength,
} from './preferenceText';

describe('buildPreferenceText', () => {
  it('가이드 2문항과 자유 입력을 라벨이 붙은 한 문자열로 합친다', () => {
    const text = buildPreferenceText({
      activity: '사진 찍으며 천천히 돌기',
      companion: '조용한 사람',
      free: '매운 음식은 못 먹어요',
    });

    expect(text).toBe(
      '하고 싶은 것: 사진 찍으며 천천히 돌기\n편한 사람: 조용한 사람\n매운 음식은 못 먹어요',
    );
  });

  it('비어 있는 칸은 줄을 만들지 않는다', () => {
    expect(buildPreferenceText({ activity: '공연 관람', companion: '', free: '' }))
      .toBe('하고 싶은 것: 공연 관람');
  });

  it('빈 draft는 빈 문자열이 된다', () => {
    expect(buildPreferenceText(EMPTY_PREFERENCE_DRAFT)).toBe('');
  });

  it('앞뒤 공백은 제거한다', () => {
    expect(buildPreferenceText({ activity: '  공연  ', companion: ' 조용 ', free: '' }))
      .toBe('하고 싶은 것: 공연\n편한 사람: 조용');
  });
});

describe('parsePreferenceText', () => {
  it('저장한 문자열을 3칸으로 되돌린다', () => {
    const draft = {
      activity: '사진 찍으며 천천히 돌기',
      companion: '조용한 사람',
      free: '매운 음식은 못 먹어요',
    };

    expect(parsePreferenceText(buildPreferenceText(draft))).toEqual(draft);
  });

  it('라벨이 없으면 전체를 자유 입력으로 둔다', () => {
    expect(parsePreferenceText('그냥 편하게 다니고 싶어요')).toEqual({
      activity: '',
      companion: '',
      free: '그냥 편하게 다니고 싶어요',
    });
  });

  it('null과 빈 문자열은 빈 draft가 된다', () => {
    expect(parsePreferenceText(null)).toEqual(EMPTY_PREFERENCE_DRAFT);
    expect(parsePreferenceText('   ')).toEqual(EMPTY_PREFERENCE_DRAFT);
  });

  it('가이드 문항 하나만 저장된 값도 복원한다', () => {
    expect(parsePreferenceText('편한 사람: 말 적은 사람')).toEqual({
      activity: '',
      companion: '말 적은 사람',
      free: '',
    });
  });

  it('여러 줄로 이어진 답변도 내용을 잃지 않는다', () => {
    const text = '하고 싶은 것: 공연 보기\n그리고 사진도\n편한 사람: 조용한 사람';

    expect(parsePreferenceText(text)).toEqual({
      activity: '공연 보기',
      companion: '조용한 사람',
      free: '그리고 사진도',
    });
  });
});

describe('isPreferenceDraftComplete', () => {
  it('가이드 2문항이 모두 채워지면 완료다', () => {
    expect(isPreferenceDraftComplete({ activity: '공연', companion: '조용', free: '' })).toBe(true);
  });

  it('한 문항이라도 비면 완료가 아니다', () => {
    expect(isPreferenceDraftComplete({ activity: '공연', companion: '', free: '자유' })).toBe(false);
    expect(isPreferenceDraftComplete({ activity: '  ', companion: '조용', free: '' })).toBe(false);
  });
});

describe('preferenceTextLength', () => {
  it('라벨을 포함한 실제 저장 길이를 센다', () => {
    const draft = { activity: '가', companion: '나', free: '' };

    expect(preferenceTextLength(draft)).toBe(buildPreferenceText(draft).length);
  });
});
