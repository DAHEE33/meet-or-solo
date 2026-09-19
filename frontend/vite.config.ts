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
      /*
        generateSW가 아니라 injectManifest다(docs/32 3.4). 생성된 service worker에는 우리 코드를
        넣을 수 없어 push 핸들러를 붙일 자리가 없었고, 그래서 앱이 꺼져 있으면 알림이 닿지
        않았다. 실제 service worker는 src/sw.ts다.
      */
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      registerType: 'autoUpdate',
      /*
        기본값은 dev 서버(`npm run dev`)에서 service worker를 등록하지 않는다. 그래서 Web
        Push(docs/32 3.4)는 `npm run build` 없이는 로컬에서 확인할 방법이 없었다. `type: 'module'`은
        injectManifest 전략의 dev 모드 요구사항이다. precache는 dev에서 빈 배열로 대체되고
        push·notificationclick 핸들러만 그대로 동작한다 — 캐싱 동작 자체는 바뀌지 않는다.
      */
      devOptions: {
        enabled: true,
        type: 'module',
      },
      includeAssets: ['icons/icon.svg', 'icons/icon-maskable.svg'],
      manifest: {
        name: 'meet-or-solo',
        short_name: 'meet-or-solo',
        description: '강원도 축제 현장 매칭 PWA',
        // 없으면 'en'으로 나간다. 화면이 전부 한국어고 index.html도 lang="ko"다.
        lang: 'ko',
        /*
          index.html의 <meta name="theme-color">와 같은 값이어야 한다. 예전에는 여기만
          '#0f172a'(남색)여서, 설치한 앱의 상단 바 색과 브라우저에서 연 색이 달랐다.
          배경색도 앱 배경(sand)으로 맞춘다 — 설치 앱이 뜨는 동안 흰 판이 한 번 깜빡였다.
        */
        theme_color: '#FAF7F1',
        background_color: '#FAF7F1',
        display: 'standalone',
        start_url: '/',
        scope: '/',
        /*
          'any'와 'maskable'을 한 항목에 같이 두지 않는다. maskable은 바깥 20%가 잘려도
          되도록 여백을 크게 준 그림이라, 그걸 그대로 탭 아이콘('any')으로 쓰면 로고가
          작게 박혀 보인다. 파일을 나눠 각자 맞는 그림을 준다.
        */
        icons: [
          {
            src: '/icons/icon.svg',
            sizes: 'any',
            type: 'image/svg+xml',
            purpose: 'any',
          },
          {
            src: '/icons/icon-maskable.svg',
            sizes: 'any',
            type: 'image/svg+xml',
            purpose: 'maskable',
          },
        ],
      },
      /*
        navigateFallback과 denylist는 이제 src/sw.ts 안에 있다(NavigationRoute).
        injectManifest에서는 workbox 옵션이 아니라 우리 코드가 라우팅을 정한다.

        기존 주석을 옮겨 둔다 — navigateFallback은 navigation request 전체를 index.html로
        돌린다. OAuth 로그인은 window.location.href 이동이라 navigation request이므로,
        denylist가 없으면 OAuth 로그인 시작과 callback 요청을 Service Worker가 가로채
        302 대신 index.html(200)을 돌려주고 로그인이 조용히 실패한다.
      */
      injectManifest: {
        globPatterns: ['**/*.{js,css,html,svg,png,ico,woff2}'],
      },
    }),
  ],
});
