import {
  FIGURE_BODY,
  GROUND_SHADOW,
  HEAD,
  INNER_CIRCLE,
  PIN_OUTLINE,
  RAYS,
  RAY_STROKE_WIDTH,
} from './brandPinGeometry';

/**
 * meet·or·solo 핀 심볼.
 *
 * 로고 PNG(`frontend/logo.png`)를 그대로 쓰지 않고 SVG로 다시 그린 이유는 조각별 애니메이션이
 * 필요해서다. PNG는 핀·광선·워드마크가 한 장에 픽셀로 녹아 있어 광선만 따로 팝시킬 수 없다.
 *
 * 도형 좌표는 `brandPinGeometry.ts`에 있다. favicon과 PWA 아이콘(`public/icons/icon.svg`)이
 * 같은 도형을 쓰고, 두 곳이 어긋나지 않는지는 `brandPinIcon.test.ts`가 확인한다.
 *
 * 색은 원본 로고의 오렌지(`#F65F27`)가 아니라 앱 팔레트 `coral`(`#E8593A`)을 쓴다. 스플래시의
 * 워드마크가 `LoginPage`와 같은 텍스트 렌더(=`coral`)이므로, 핀만 원본색이면 같은 화면에서
 * 오렌지 두 개가 어긋난다. 원본색으로 되돌리려면 `fill-coral`/`stroke-coral`만 교체하면 된다.
 */
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
        cx={GROUND_SHADOW.cx}
        cy={GROUND_SHADOW.cy}
        rx={GROUND_SHADOW.rx}
        ry={GROUND_SHADOW.ry}
        className="animate-pin-shadow fill-coral [transform-box:view-box] [transform-origin:50px_103.5px] motion-reduce:animate-none"
      />
      <g className="animate-pin-drop [transform-box:view-box] [transform-origin:50px_60px] motion-reduce:animate-none">
        <path d={PIN_OUTLINE} className="fill-coral" />
        <circle cx={INNER_CIRCLE.cx} cy={INNER_CIRCLE.cy} r={INNER_CIRCLE.r} className="fill-white" />
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
        <g className="stroke-coral" strokeWidth={RAY_STROKE_WIDTH} strokeLinecap="round">
          {RAYS.map((ray, index) => (
            <path
              key={ray.d}
              d={ray.d}
              className={`animate-ray-pop-${index + 1} [transform-box:view-box] motion-reduce:animate-none`}
              style={{ transformOrigin: `${ray.originX}px ${ray.originY}px` }}
            />
          ))}
        </g>
      </g>
      <defs>
        <clipPath id="mors-pin-inner">
          <circle cx={INNER_CIRCLE.cx} cy={INNER_CIRCLE.cy} r={INNER_CIRCLE.r} />
        </clipPath>
      </defs>
    </svg>
  );
}
