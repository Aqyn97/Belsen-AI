import { defineConfig, devices } from '@playwright/test';
export default defineConfig({ testDir: './e2e-real', outputDir:'./test-results/real', workers: 1, fullyParallel: false,
  use: { baseURL: 'http://localhost:5183', trace:'retain-on-failure' },
  projects: [{name:'real-desktop',use:{...devices['Desktop Chrome']}},{name:'real-mobile',use:{...devices['iPhone 13'],defaultBrowserType:'chromium'}}],
  webServer: [
    {command:'node scripts/browser-backend.mjs',url:'http://127.0.0.1:18081/api/v1/health',timeout:120_000,reuseExistingServer:false,gracefulShutdown:{signal:'SIGTERM',timeout:15_000}},
    {command:'npm run dev -- --port 5183',url:'http://localhost:5183',env:{BELSEN_DEV_BACKEND:'http://127.0.0.1:18081', VITE_GOOGLE_ENABLED:'false'},reuseExistingServer:false},
  ],
});
