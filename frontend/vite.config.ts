import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icons/placeholder.svg'],
      manifest: {
        name: 'meet-or-solo',
        short_name: 'meet-or-solo',
        description: '강원도 축제 현장 매칭 PWA',
        theme_color: '#0f172a',
        background_color: '#ffffff',
        display: 'standalone',
        start_url: '/',
        scope: '/',
        icons: [
          {
            src: '/icons/placeholder.svg',
            sizes: 'any',
            type: 'image/svg+xml',
            purpose: 'any maskable',
          },
        ],
      },
      workbox: {
        navigateFallback: '/index.html',
        // navigateFallback은 navigation request 전체를 index.html로 돌린다.
        // OAuth 로그인은 window.location.href 이동이라 navigation request이므로,
        // denylist가 없으면 /api/auth/*/login과 callback을 Service Worker가 가로채
        // 302 대신 index.html(200)을 돌려주고 로그인이 조용히 실패한다.
        // fetch로 호출하는 /api/members/me 같은 요청은 navigation이 아니라서
        // 영향을 받지 않기 때문에 로그인만 깨지는 형태로 나타난다.
        navigateFallbackDenylist: [/^\/api(\/|$)/, /^\/ws(\/|$)/],
      },
    }),
  ],
});
