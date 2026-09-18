// 관광공사 API가 이미지를 내려주지 않는 콘텐츠에 쓰는 기본 이미지 규칙.
// 렌더링(ImagePlaceholder.tsx)과 분리해 둔다 — 이 저장소 vitest는 node 환경이고 jsdom이 없어
// 렌더링 없이 검증할 수 있어야 한다(docs/03 "상태 관리와 방어 규칙"과 같은 이유).

/**
 * 기본 이미지 프리셋 종류.
 * 관광지 동기화 대상 contentTypeId 4종(12/14/28/39) + 축제 + 중립 fallback으로 구성한다.
 */
export type PlaceholderKind = 'FESTIVAL' | 'TOUR' | 'CULTURE' | 'ACTIVITY' | 'FOOD' | 'DEFAULT';

/**
 * 노출 크기 단계. 56px 썸네일에 문구까지 넣으면 답답하고, 240px 히어로에 아이콘만 두면
 * 미완성처럼 보인다. 그래서 크기에 따라 노출 요소를 다르게 한다.
 * - `sm`: 아이콘만 (약 80px 이하)
 * - `md`: 아이콘 + 분류 문구 (약 80~150px)
 * - `lg`: 아이콘 + 분류 문구 + 능선 라인아트 (약 150px 이상)
 */
export type PlaceholderSize = 'sm' | 'md' | 'lg';

/** 관광공사 contentTypeId → 프리셋. 동기화 대상 외 타입은 중립 fallback으로 보낸다. */
const CONTENT_TYPE_KINDS: Record<string, PlaceholderKind> = {
  '12': 'TOUR',
  '14': 'CULTURE',
  '28': 'ACTIVITY',
  '39': 'FOOD',
};

export function placeholderKindFromContentType(contentTypeId?: string | null): PlaceholderKind {
  if (!contentTypeId) return 'DEFAULT';
  return CONTENT_TYPE_KINDS[contentTypeId] ?? 'DEFAULT';
}

/**
 * 프리셋별 분류 문구와 accent 색.
 * 문구는 `utils/tourSpot.ts`의 `contentTypeLabel`과 같은 어휘를 쓴다(액티비티/맛집).
 */
const KIND_PRESETS: Record<PlaceholderKind, { label: string; accent: string }> = {
  FESTIVAL: { label: '축제', accent: '#E8593A' }, // coral — 축제/매칭 계열
  TOUR: { label: '관광지', accent: '#2F8C85' }, // teal — 솔로 코스 계열
  CULTURE: { label: '문화시설', accent: '#4A6B8A' },
  ACTIVITY: { label: '액티비티', accent: '#2E8FA6' },
  FOOD: { label: '맛집', accent: '#C9772F' },
  DEFAULT: { label: '사진 준비 중', accent: '#7C8794' },
};

export function placeholderLabel(kind: PlaceholderKind): string {
  return KIND_PRESETS[kind].label;
}

export function placeholderAccent(kind: PlaceholderKind): string {
  return KIND_PRESETS[kind].accent;
}

/** 앱 배경색(tailwind `sand`). accent를 이 색에 섞어 아주 옅은 톤을 만든다. */
const BASE_COLOR = '#FAF7F1';

/**
 * seed 문자열을 안정적인 32bit 정수로 바꾼다(FNV-1a).
 * 같은 콘텐츠는 항상 같은 톤이 나와야 목록에서 항목 구분이 되고 재방문 시에도 같아 보인다.
 */
export function hashSeed(seed: string): number {
  let hash = 0x811c9dc5;
  for (let i = 0; i < seed.length; i += 1) {
    hash ^= seed.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash >>> 0;
}

function parseHex(hex: string): [number, number, number] {
  const value = hex.replace('#', '');
  return [
    parseInt(value.slice(0, 2), 16),
    parseInt(value.slice(2, 4), 16),
    parseInt(value.slice(4, 6), 16),
  ];
}

/** accent를 배경색에 `ratio`(0~1)만큼 섞은 hex를 만든다. ratio가 클수록 accent에 가깝다. */
export function mixWithBase(accent: string, ratio: number): string {
  const clamped = Math.min(1, Math.max(0, ratio));
  const from = parseHex(accent);
  const to = parseHex(BASE_COLOR);
  const channels = from.map((channel, index) =>
    Math.round(to[index] + (channel - to[index]) * clamped),
  );
  return `#${channels.map((channel) => channel.toString(16).padStart(2, '0')).join('')}`;
}

/** seed로 고르는 톤 변형. 같은 분류의 카드가 여러 개 나와도 서로 구분되게 하는 장치다. */
const TONE_VARIANTS = [
  { angle: 155, from: 0.14, to: 0.03 },
  { angle: 130, from: 0.1, to: 0.05 },
  { angle: 195, from: 0.17, to: 0.04 },
];

/** CSS `background` 값(그라데이션)을 만든다. seed가 같으면 결과도 항상 같다. */
export function placeholderBackground(kind: PlaceholderKind, seed: string): string {
  const accent = placeholderAccent(kind);
  const tone = TONE_VARIANTS[hashSeed(seed) % TONE_VARIANTS.length];
  return `linear-gradient(${tone.angle}deg, ${mixWithBase(accent, tone.from)} 0%, ${mixWithBase(accent, tone.to)} 100%)`;
}

/** 스크린리더용 설명. 크기 단계와 무관하게 항상 같은 문구를 읽어 준다. */
export function placeholderAriaLabel(kind: PlaceholderKind): string {
  return kind === 'DEFAULT' ? '사진 준비 중' : `${placeholderLabel(kind)} 사진 준비 중`;
}
