/**
 * 취향 전격 분석의 화면 표시 상태와, 매칭 신청 전 안내를 띄울지 판단하는 규칙을 모은다.
 *
 * 마이페이지·프로필 수정·매칭 신청 세 화면이 같은 배지 문구와 같은 판단을 써야 하므로 한 곳에
 * 모았다. jsdom이 없어 클릭 흐름을 테스트할 수 없기 때문에, 판단 로직을 순수 함수로 빼는 것이
 * 곧 테스트 수단이기도 하다.
 */

import type { EmbeddingStatus, PreferenceEmbedding } from '../../api/preferenceEmbedding';

export type PreferenceState =
  /** 아직 조회 중 */
  | 'LOADING'
  /** 조회에 실패해 상태를 알 수 없음 */
  | 'UNAVAILABLE'
  /** 취향을 입력한 적이 없음 */
  | 'NONE'
  /** 입력했고 분석 진행 중 */
  | 'ANALYZING'
  /** 분석 완료 */
  | 'COMPLETED'
  /** 분석 실패 */
  | 'FAILED';

/**
 * 조회 결과를 화면 상태로 바꾼다.
 *
 * "아직 없음"은 404가 아니라 `200 + null data`이므로 `null`이 정상 입력값이다.
 */
export function resolvePreferenceState(embedding: PreferenceEmbedding | null): PreferenceState {
  if (!embedding) return 'NONE';
  return embeddingStatusToState(embedding.embeddingStatus);
}

function embeddingStatusToState(status: EmbeddingStatus): PreferenceState {
  switch (status) {
    case 'COMPLETED': return 'COMPLETED';
    case 'FAILED': return 'FAILED';
    case 'PENDING': return 'ANALYZING';
    default: return 'ANALYZING';
  }
}

/** 프로필 수정 화면처럼 `EmbeddingStatus`를 직접 들고 있는 곳에서 쓴다. */
export function preferenceStateOf(status: EmbeddingStatus): PreferenceState {
  return embeddingStatusToState(status);
}

/**
 * 매칭 신청 직전에 취향 입력 안내를 띄울지 판단한다.
 *
 * 안내는 매칭 흐름을 방해하면 안 되므로 아래 경우에는 띄우지 않고 곧바로 신청한다.
 * - 이미 분석이 끝났거나 진행 중일 때 (이미 입력한 사람을 재촉하지 않는다)
 * - 조회가 아직 끝나지 않았을 때 (부가 정보 때문에 신청을 지연시키지 않는다)
 * - 조회에 실패했을 때 (부가 정보 조회 실패로 매칭을 막지 않는다)
 * - 이 화면에서 이미 한 번 안내했을 때 (재신청마다 반복해서 띄우지 않는다)
 *
 * 안내를 띄우더라도 신청을 막지는 않는다. 취향 입력은 선택 동의(AI 처리·국외 이전)에 딸린
 * 기능이고, 임베딩이 없어도 여행 스타일 태그만으로 매칭이 정상 동작하기 때문이다.
 */
export function shouldPromptBeforeApply(
  state: PreferenceState,
  alreadyPrompted: boolean,
): boolean {
  if (alreadyPrompted) return false;
  return state === 'NONE' || state === 'FAILED';
}

export interface PreferencePromptCopy {
  title: string;
  body: string;
  /** 안내를 무시하고 그대로 매칭을 신청하는 버튼. 항상 존재한다. */
  skipLabel: string;
  /** 취향 입력 화면으로 이동하는 버튼. */
  confirmLabel: string;
}

/** 안내 창 문구. 안내 대상이 아닌 상태면 null. */
export function preferencePromptCopy(state: PreferenceState): PreferencePromptCopy | null {
  if (state === 'NONE') {
    return {
      title: '취향을 입력하면 매칭 정확도가 올라가요',
      body: '두 문항만 답하면 돼요. 지금 건너뛰어도 매칭은 정상적으로 진행돼요.',
      skipLabel: '건너뛰고 신청',
      confirmLabel: '지금 입력하기',
    };
  }
  if (state === 'FAILED') {
    return {
      title: '취향 분석에 실패했어요',
      body: '프로필 수정에서 다시 저장하면 분석을 다시 시도해요. 지금 건너뛰어도 매칭은 정상적으로 진행돼요.',
      skipLabel: '건너뛰고 신청',
      confirmLabel: '다시 저장하기',
    };
  }
  return null;
}

/** 상태 배지 문구. 화면 세 곳이 같은 말을 쓰도록 여기서만 정한다. */
export function preferenceStatusLabel(state: PreferenceState): string {
  switch (state) {
    case 'COMPLETED': return '분석 완료';
    case 'FAILED': return '분석 실패';
    case 'ANALYZING': return '분석 중';
    case 'NONE': return '아직 입력하지 않았어요';
    default: return '';
  }
}

/** 상태 배지 색. Tailwind class 문자열. */
export function preferenceStatusTone(state: PreferenceState): string {
  switch (state) {
    case 'COMPLETED': return 'bg-teal/10 text-teal';
    case 'FAILED': return 'bg-coral/10 text-coral';
    default: return 'bg-sand text-ink/50';
  }
}

/** 마이페이지에서 배지 아래에 붙는 한 줄 안내. */
export function preferenceStatusDescription(state: PreferenceState): string {
  switch (state) {
    case 'COMPLETED': return '나와 잘 맞는 사람을 찾는 데 쓰이고 있어요.';
    case 'FAILED': return '다시 저장하면 분석을 다시 시도해요.';
    case 'ANALYZING': return '분석이 끝나면 매칭에 반영돼요.';
    case 'NONE': return '두 문항만 답하면 매칭 정확도가 올라가요.';
    default: return '';
  }
}

/** 마이페이지에서 프로필 수정으로 보내는 링크 문구. */
export function preferenceActionLabel(state: PreferenceState): string {
  return state === 'NONE' ? '입력하기' : state === 'FAILED' ? '다시 저장하기' : '수정하기';
}

/** 상태 섹션을 그릴 수 있는지. 조회 중이거나 실패했으면 조용히 숨긴다. */
export function isPreferenceStateKnown(state: PreferenceState): boolean {
  return state !== 'LOADING' && state !== 'UNAVAILABLE';
}

/**
 * 취향 입력을 마친 뒤 돌아갈 경로를 route state에서 읽는다.
 *
 * 매칭 신청 화면의 안내 창에서 프로필 수정으로 보낼 때만 실린다. 마이페이지에서 스스로 들어온
 * 경우에는 없으므로, 저장 후 그 화면에 머무는 기존 동작이 그대로 유지된다.
 *
 * 외부 URL이나 프로토콜 상대 경로(`//evil.com`)가 실려 오면 무시하고 앱 내부 경로만 허용한다.
 */
export function readPreferenceReturnTo(locationState: unknown): string | null {
  if (!locationState || typeof locationState !== 'object' || !('returnTo' in locationState)) {
    return null;
  }
  const value = (locationState as { returnTo: unknown }).returnTo;
  if (typeof value !== 'string') return null;
  if (!value.startsWith('/') || value.startsWith('//')) return null;
  return value;
}
