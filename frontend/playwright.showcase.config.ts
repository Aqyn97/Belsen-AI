import { defineConfig, devices } from '@playwright/test';
export default defineConfig({ testDir:'./e2e-showcase',outputDir:'./test-results/showcase',workers:1,
  use:{baseURL:'http://127.0.0.1:4175'},
  projects:[{name:'showcase-desktop',use:{...devices['Desktop Chrome']}},{name:'showcase-mobile',use:{...devices['iPhone 13'],defaultBrowserType:'chromium'}}],
  webServer:{command:'node ../showcase/build.mjs && node scripts/showcase-server.mjs',url:'http://127.0.0.1:4175',reuseExistingServer:false,env:{BELSEN_APP_URL:'',BELSEN_APP_VERIFIED:'',BELSEN_SOURCE_PUBLIC:''}},
});
