import { defineConfig, devices } from '@playwright/test';
export default defineConfig({ testDir: './e2e-coach', outputDir: './test-results/coach', workers: 1,
  use: { baseURL: 'http://localhost:4173' }, projects: [
    { name: 'coach-desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'coach-mobile', use: { ...devices['iPhone 13'], defaultBrowserType: 'chromium' } },
  ], webServer: { command: 'npm run preview -- --host localhost --port 4173 --strictPort', url: 'http://localhost:4173', reuseExistingServer: false },
});
