/**
 * 취향 입력 3칸(가이드 2문항 + 자유 입력)과 서버가 받는 단일 문자열 사이를 변환한다.
 *
 * 서버 `preference_text`는 단일 컬럼이므로, 가이드 문항 답변을 라벨 접두어가 붙은 줄로
 * 직렬화해 하나의 텍스트로 저장한다. 임베딩 입력으로도 라벨이 붙은 문장이 더 유리하다.
 */

export interface PreferenceDraft {
  /** 가이드 1문항: 축제에서 하고 싶은 것 */
  activity: string;
  /** 가이드 2문항: 함께면 편한 사람 */
  companion: string;
  /** 자유 입력 (선택) */
  free: string;
}

export const PREFERENCE_TEXT_MAX_LENGTH = 2000;

const ACTIVITY_LABEL = '하고 싶은 것:';
const COMPANION_LABEL = '편한 사람:';

export const EMPTY_PREFERENCE_DRAFT: PreferenceDraft = {
  activity: '',
  companion: '',
  free: '',
};

/** 3칸을 서버에 보낼 단일 문자열로 합친다. 빈 칸은 줄 자체를 만들지 않는다. */
export function buildPreferenceText(draft: PreferenceDraft): string {
  const lines: string[] = [];
  const activity = draft.activity.trim();
  const companion = draft.companion.trim();
  const free = draft.free.trim();

  if (activity) lines.push(`${ACTIVITY_LABEL} ${activity}`);
  if (companion) lines.push(`${COMPANION_LABEL} ${companion}`);
  if (free) lines.push(free);

  return lines.join('\n');
}

/**
 * 저장된 단일 문자열을 3칸으로 되돌린다.
 *
 * 라벨을 찾지 못하면 전체를 자유 입력으로 둔다. 사용자가 라벨과 같은 문구를 직접 입력했거나
 * 이전 버전에서 자유 입력만으로 저장한 값도 내용을 잃지 않고 화면에 표시된다.
 */
export function parsePreferenceText(text: string | null | undefined): PreferenceDraft {
  if (!text || !text.trim()) return { ...EMPTY_PREFERENCE_DRAFT };

  const activity: string[] = [];
  const companion: string[] = [];
  const free: string[] = [];
  let hasLabel = false;
  let current: string[] | null = null;

  for (const line of text.split('\n')) {
    if (line.startsWith(ACTIVITY_LABEL)) {
      hasLabel = true;
      current = activity;
      current.push(line.slice(ACTIVITY_LABEL.length).trim());
    } else if (line.startsWith(COMPANION_LABEL)) {
      hasLabel = true;
      current = companion;
      current.push(line.slice(COMPANION_LABEL.length).trim());
    } else {
      current = free;
      current.push(line);
    }
  }

  if (!hasLabel) {
    return { activity: '', companion: '', free: text.trim() };
  }
  return {
    activity: activity.join('\n').trim(),
    companion: companion.join('\n').trim(),
    free: free.join('\n').trim(),
  };
}

/** 매칭 신청·회원가입 흐름에서 재사용할 최소 입력 조건. 가이드 2문항은 모두 채워야 한다. */
export function isPreferenceDraftComplete(draft: PreferenceDraft): boolean {
  return draft.activity.trim().length > 0 && draft.companion.trim().length > 0;
}

/** 합쳐진 길이가 서버 제한을 넘는지 확인한다. */
export function preferenceTextLength(draft: PreferenceDraft): number {
  return buildPreferenceText(draft).length;
}
