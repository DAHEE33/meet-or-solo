import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#22303E', // 텍스트/탭바
        sand: '#FAF7F1', // 앱 배경
        line: '#EDE7DD', // 보더
        coral: '#E8593A', // Primary — 매칭/주 CTA
        teal: '#2F8C85', // Secondary — 솔로 코스
      },
      fontFamily: {
        sans: ['Pretendard', 'system-ui', 'sans-serif'],
      },
      // 스플래시(`components/splash`) 로고 연출. 핀 낙하 → 그림자 → 광선 3줄 → 워드마크 순서다.
      keyframes: {
        'pin-drop': {
          '0%': { transform: 'translateY(-56px) scale(0.96)', opacity: '0' },
          '60%': { transform: 'translateY(4px) scale(1.01)', opacity: '1' },
          '100%': { transform: 'translateY(0) scale(1)', opacity: '1' },
        },
        'pin-shadow': {
          '0%': { transform: 'scaleX(0.3)', opacity: '0' },
          '100%': { transform: 'scaleX(1)', opacity: '1' },
        },
        'ray-pop': {
          '0%': { transform: 'scale(0)', opacity: '0' },
          '70%': { transform: 'scale(1.15)', opacity: '1' },
          '100%': { transform: 'scale(1)', opacity: '1' },
        },
        'wordmark-rise': {
          '0%': { transform: 'translateY(10px)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' },
        },
      },
      // 광선은 같은 keyframe을 60ms씩 늦게 재생한다. delay를 별도 utility로 덧붙이면 animation
      // 축약형이 delay를 0으로 되돌릴 수 있어, 지연까지 포함한 utility를 3개로 나눠 정의한다.
      animation: {
        'pin-drop': 'pin-drop 460ms cubic-bezier(0.22, 1.2, 0.36, 1) both',
        'pin-shadow': 'pin-shadow 320ms ease-out 300ms both',
        'ray-pop-1': 'ray-pop 260ms cubic-bezier(0.34, 1.56, 0.64, 1) 520ms both',
        'ray-pop-2': 'ray-pop 260ms cubic-bezier(0.34, 1.56, 0.64, 1) 580ms both',
        'ray-pop-3': 'ray-pop 260ms cubic-bezier(0.34, 1.56, 0.64, 1) 640ms both',
        'wordmark-rise': 'wordmark-rise 380ms ease-out 760ms both',
      },
    },
  },
  plugins: [],
} satisfies Config;
