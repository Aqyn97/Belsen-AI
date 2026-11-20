import { defineConfig, devices } from '@playwright/test';
export default defineConfig({ testDir: './e2e-coach-real', outputDir: './test-results/coach-real', workers: 1,
  use: { baseURL: 'https://localhost:18443', ignoreHTTPSErrors: true, trace: 'retain-on-failure' },
  projects: [{name:'coach-real-desktop',use:{...devices['Desktop Chrome']}},{name:'coach-real-mobile',use:{...devices['iPhone 13'],defaultBrowserType:'chromium'}}],
  webServer: { command: 'node scripts/browser-backend.mjs --packaged --coach-fixture', url: 'https://localhost:18443/api/v1/health', ignoreHTTPSErrors: true, timeout: 120_000, reuseExistingServer: false, gracefulShutdown: {signal:'SIGTERM',timeout:15_000} },
});
