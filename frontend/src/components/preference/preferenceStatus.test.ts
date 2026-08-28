import { describe, expect, it } from 'vitest';
import type { PreferenceEmbedding } from '../../api/preferenceEmbedding';
import {
  isPreferenceStateKnown,
  preferenceActionLabel,
  preferencePromptCopy,
  preferenceStateOf,
  preferenceStatusDescription,
  preferenceStatusLabel,
  preferenceStatusTone,
  readPreferenceReturnTo,
  resolvePreferenceState,
  shouldPromptBeforeApply,
  type PreferenceState,
} from './preferenceStatus';

const embedding = (status: PreferenceEmbedding['embeddingStatus']): PreferenceEmbedding => ({
  memberId: 1,
  preferenceText: '하고 싶은 것: 공연 보기',
  embeddingStatus: status,
  embeddingModel: 'text-embedding-3-small',
  createdAt: '2026-08-27T10:00:00',
  updatedAt: '2026-08-27T10:00:00',
});

describe('resolvePreferenceState', () => {
  it('"아직 없음"은 오류가 아니라 미입력 상태다', () => {
    // 서버가 404가 아니라 200 + null data로 응답한다.
    expect(resolvePreferenceState(null)).toBe('NONE');
  });

  it('저장된 분석 상태를 화면 상태로 옮긴다', () => {
    expect(resolvePreferenceState(embedding('COMPLETED'))).toBe('COMPLETED');
    expect(resolvePreferenceState(embedding('FAILED'))).toBe('FAILED');
    expect(resolvePreferenceState(embedding('PENDING'))).toBe('ANALYZING');
  });

  it('preferenceStateOf는 같은 매핑을 상태값만으로 제공한다', () => {
    expect(preferenceStateOf('COMPLETED')).toBe('COMPLETED');
    expect(preferenceStateOf('FAILED')).toBe('FAILED');
    expect(preferenceStateOf('PENDING')).toBe('ANALYZING');
  });
});

describe('shouldPromptBeforeApply', () => {
  it('취향이 없거나 분석에 실패했을 때만 안내한다', () => {
    expect(shouldPromptBeforeApply('NONE', false)).toBe(true);
    expect(shouldPromptBeforeApply('FAILED', false)).toBe(true);
  });

  it('이미 입력한 사람은 재촉하지 않는다', () => {
    expect(shouldPromptBeforeApply('COMPLETED', false)).toBe(false);
    expect(shouldPromptBeforeApply('ANALYZING', false)).toBe(false);
  });

  it('조회 중이거나 조회에 실패했으면 신청을 지연시키지 않는다', () => {
    expect(shouldPromptBeforeApply('LOADING', false)).toBe(false);
    expect(shouldPromptBeforeApply('UNAVAILABLE', false)).toBe(false);
  });

  it('한 화면에서 이미 안내했으면 재신청마다 다시 띄우지 않는다', () => {
    expect(shouldPromptBeforeApply('NONE', true)).toBe(false);
    expect(shouldPromptBeforeApply('FAILED', true)).toBe(false);
  });
});

describe('preferencePromptCopy', () => {
  it('안내 대상 상태에는 항상 건너뛰는 선택지를 준다', () => {
    // 취향 미입력자의 매칭 신청을 막지 않는다는 결정을 회귀로 고정한다.
    for (const state of ['NONE', 'FAILED'] as const) {
      const copy = preferencePromptCopy(state);
      expect(copy).not.toBeNull();
      expect(copy?.skipLabel).toBe('건너뛰고 신청');
      expect(copy?.confirmLabel).toBeTruthy();
    }
  });

  it('건너뛰어도 매칭이 정상 진행된다고 알린다', () => {
    expect(preferencePromptCopy('NONE')?.body).toContain('정상적으로 진행');
    expect(preferencePromptCopy('FAILED')?.body).toContain('정상적으로 진행');
  });

  it('안내 대상이 아닌 상태에는 창을 만들지 않는다', () => {
    for (const state of ['COMPLETED', 'ANALYZING', 'LOADING', 'UNAVAILABLE'] as const) {
      expect(preferencePromptCopy(state)).toBeNull();
    }
  });
});

describe('상태 표시', () => {
  it('분석 완료/실패/중과 미입력을 구분해 보여준다', () => {
    expect(preferenceStatusLabel('COMPLETED')).toBe('분석 완료');
    expect(preferenceStatusLabel('FAILED')).toBe('분석 실패');
    expect(preferenceStatusLabel('ANALYZING')).toBe('분석 중');
    expect(preferenceStatusLabel('NONE')).toBe('아직 입력하지 않았어요');
  });

  it('완료와 실패는 서로 다른 색으로 구분한다', () => {
    expect(preferenceStatusTone('COMPLETED')).not.toBe(preferenceStatusTone('FAILED'));
    expect(preferenceStatusTone('COMPLETED')).toContain('teal');
    expect(preferenceStatusTone('FAILED')).toContain('coral');
  });

  it('상태마다 다음에 할 일을 알려준다', () => {
    expect(preferenceActionLabel('NONE')).toBe('입력하기');
    expect(preferenceActionLabel('FAILED')).toBe('다시 저장하기');
    expect(preferenceActionLabel('COMPLETED')).toBe('수정하기');
  });

  it('조회 중이거나 조회에 실패하면 상태 섹션을 숨긴다', () => {
    expect(isPreferenceStateKnown('LOADING')).toBe(false);
    expect(isPreferenceStateKnown('UNAVAILABLE')).toBe(false);
    expect(isPreferenceStateKnown('NONE')).toBe(true);
    expect(isPreferenceStateKnown('COMPLETED')).toBe(true);
  });
});

describe('화면 문구', () => {
  const states: PreferenceState[] = ['LOADING', 'UNAVAILABLE', 'NONE', 'ANALYZING', 'COMPLETED', 'FAILED'];
  const allText = states.flatMap((state) => {
    const copy = preferencePromptCopy(state);
    return [
      preferenceStatusLabel(state),
      preferenceStatusDescription(state),
      preferenceActionLabel(state),
      copy?.title ?? '',
      copy?.body ?? '',
      copy?.skipLabel ?? '',
      copy?.confirmLabel ?? '',
    ];
  }).join(' ');

  it('개발 용어를 화면 문구에 쓰지 않는다', () => {
    for (const term of ['임베딩', '벡터', 'embedding', 'API', '코사인', 'AI 매칭']) {
      expect(allText).not.toContain(term);
    }
  });
});

describe('readPreferenceReturnTo', () => {
  it('매칭 화면에서 넘겨준 복귀 경로를 읽는다', () => {
    expect(readPreferenceReturnTo({ returnTo: '/matching' })).toBe('/matching');
  });

  it('직접 들어온 경우에는 복귀 경로가 없다', () => {
    // 마이페이지에서 스스로 들어오면 저장 후 그 화면에 머무는 기존 동작이 유지된다.
    expect(readPreferenceReturnTo(null)).toBeNull();
    expect(readPreferenceReturnTo(undefined)).toBeNull();
    expect(readPreferenceReturnTo({})).toBeNull();
  });

  it('앱 내부 경로가 아니면 무시한다', () => {
    expect(readPreferenceReturnTo({ returnTo: 'https://evil.example.com' })).toBeNull();
    expect(readPreferenceReturnTo({ returnTo: '//evil.example.com' })).toBeNull();
    expect(readPreferenceReturnTo({ returnTo: 'matching' })).toBeNull();
    expect(readPreferenceReturnTo({ returnTo: 123 })).toBeNull();
  });
});
