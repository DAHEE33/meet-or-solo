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
    },
  },
  plugins: [],
} satisfies Config;
