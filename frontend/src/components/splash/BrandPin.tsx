/**
 * meet·or·solo 핀 심볼.
 *
 * 로고 PNG(`frontend/logo.png`)를 그대로 쓰지 않고 SVG로 다시 그린 이유는 조각별 애니메이션이
 * 필요해서다. PNG는 핀·광선·워드마크가 한 장에 픽셀로 녹아 있어 광선만 따로 팝시킬 수 없다.
 *
 * 좌표는 원본 PNG를 격자로 스캔해 옮긴 값이다. 100x112 viewBox에서 안쪽 흰 원은 (50,44) r27.5,
 * 외곽 물방울은 반지름 38 원의 상단을 크게 돌아(`large-arc`) 아래 꼭짓점 (50,99)으로 접선
 * 연결한 경로다.
 *
 * 색은 원본 로고의 오렌지(`#F65F27`)가 아니라 앱 팔레트 `coral`(`#E8593A`)을 쓴다. 스플래시의
 * 워드마크가 `LoginPage`와 같은 텍스트 렌더(=`coral`)이므로, 핀만 원본색이면 같은 화면에서
 * 오렌지 두 개가 어긋난다. 원본색으로 되돌리려면 `fill-coral`/`stroke-coral`만 교체하면 된다.
 */

/**
 * 한 사람의 몸통과 치켜든 팔. 팔은 손끝으로 갈수록 좁아지는 쐐기이고, 어깨는 원 왼쪽 아래를
 * 채우며 흘러내린다. 아래로 길게 뻗은 구간은 안쪽 원 clip이 잘라낸다.
 */
const FIGURE_BODY = [
  'M48 35.2',
  'C46.9 37.3 45.4 40 44 42.6',
  'C43 44.8 41.6 46.6 39.6 47.6',
  'C35.4 49.6 30 50.6 26 53.2',
  'C22.5 55.4 19 58.4 16 61',
  'L16 78',
  'L39.4 78',
  'C38.4 71 37.8 65 38.6 60.4',
  'C39.4 56.4 41.8 54.4 44.8 52.6',
  'C47.2 51 48.6 46.6 49.6 42.2',
  'C50.2 38.6 49.8 36.2 48 35.2',
  'Z',
].join(' ');

const HEAD = { cx: 30.8, cy: 42.1, r: 6.3 };

export default function BrandPin({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 100 112"
      className={className}
      aria-hidden="true"
      focusable="false"
    >
      {/* 핀이 착지하며 옆으로 퍼지는 바닥 타원. 핀 그룹보다 먼저 그려 핀 끝이 위에 겹친다. */}
      <ellipse
        cx="50"
        cy="103.5"
        rx="21.3"
        ry="4"
        className="animate-pin-shadow fill-coral [transform-box:view-box] [transform-origin:50px_103.5px] motion-reduce:animate-none"
      />
      <g className="animate-pin-drop [transform-box:view-box] [transform-origin:50px_60px] motion-reduce:animate-none">
        <path d="M22.5 70.3 A38 38 0 1 1 77.5 70.3 L50 99 Z" className="fill-coral" />
        <circle cx="50" cy="44" r="27.5" className="fill-white" />
        {/*
          두 사람은 안쪽 원에 clip된다. 원본 로고도 몸통이 원 아래 경계에서 잘리고, 그 사이에
          남는 흰 여백이 로고의 일부다. 오른쪽 사람은 왼쪽을 좌우 반전해 그린다 — 원본도 두
          사람이 대칭이다.
        */}
        <g clipPath="url(#mors-pin-inner)">
          <g className="fill-ink">
            <circle cx={HEAD.cx} cy={HEAD.cy} r={HEAD.r} />
            <path d={FIGURE_BODY} />
          </g>
          <g className="fill-coral" transform="translate(100 0) scale(-1 1)">
            <circle cx={HEAD.cx} cy={HEAD.cy} r={HEAD.r} />
            <path d={FIGURE_BODY} />
          </g>
        </g>
        {/* 맞닿은 손 위로 튀는 하이파이브 광선 3줄. 60ms씩 늦게 팝한다. */}
        <g className="stroke-coral" strokeWidth="3.2" strokeLinecap="round">
          <path
            d="M50 23 L50 29"
            className="animate-ray-pop-1 [transform-box:view-box] [transform-origin:50px_26px] motion-reduce:animate-none"
          />
          <path
            d="M40.4 27.6 L44.1 31.5"
            className="animate-ray-pop-2 [transform-box:view-box] [transform-origin:42.25px_29.55px] motion-reduce:animate-none"
          />
          <path
            d="M59.6 27.6 L55.9 31.5"
            className="animate-ray-pop-3 [transform-box:view-box] [transform-origin:57.75px_29.55px] motion-reduce:animate-none"
          />
        </g>
      </g>
      <defs>
        <clipPath id="mors-pin-inner">
          <circle cx="50" cy="44" r="27.5" />
        </clipPath>
      </defs>
    </svg>
  );
}
