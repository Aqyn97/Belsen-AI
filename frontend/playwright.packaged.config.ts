import { defineConfig, devices } from '@playwright/test';
export default defineConfig({ testDir:'./e2e-packaged',outputDir:'./test-results/packaged',workers:1,fullyParallel:false,
  use:{baseURL:'https://localhost:18443',ignoreHTTPSErrors:true,trace:'retain-on-failure'},
  projects:[{name:'packaged-desktop',use:{...devices['Desktop Chrome']}},{name:'packaged-mobile',use:{...devices['iPhone 13'],defaultBrowserType:'chromium'}}],
  webServer:{command:'node scripts/browser-backend.mjs --packaged',url:'https://localhost:18443/api/v1/health',ignoreHTTPSErrors:true,timeout:120_000,reuseExistingServer:false,gracefulShutdown:{signal:'SIGTERM',timeout:15_000}},
});
