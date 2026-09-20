/**
 * 「혼자왔니」 워드마크.
 *
 * 로고 이미지(frontend/logo.png)의 글자 부분과 같은 구성이다. 「혼자」는 ink, 「왔니」는
 * coral이고, 마지막 「니」만 반시계로 8° 기울어져 있다. 각도는 로고 원본에서 잰 값이다
 * (ㄴ 세로획 -8.08°, ㅣ 세로획 -8.32°). 「혼」 -1.5°, 「왔」 +1.2°는 글자 모양에서 오는
 * 오차라 수직으로 둔다.
 *
 * 이미지가 아니라 텍스트로 그린다. 화면마다 크기가 다르고(스플래시 text-4xl ~ 헤더
 * text-xl), 검색엔진과 보조기술이 "혼자왔니"로 읽어야 하기 때문이다.
 *
 * ⚠ 기울인 「니」는 inline-block이어야 한다. transform은 inline 요소에 적용되지 않아
 *   span 기본값 그대로 두면 기울기가 조용히 사라진다.
 *
 * 크기·굵기는 `className`으로 주입한다. 색과 기울기만 이 컴포넌트가 고정한다.
 */
export default function Wordmark({ className = '' }: { className?: string }) {
  return (
    <span className={['inline-flex items-baseline tracking-tight', className].join(' ')}>
      <span className="text-ink">혼자</span>
      <span className="text-coral">왔</span>
      <span className="inline-block rotate-[-8deg] text-coral">니</span>
    </span>
  );
}
