import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
const proxy = Object.fromEntries(['/api', '/oauth2', '/login/oauth2'].map(path => [path, {
  target: process.env.BELSEN_DEV_BACKEND ?? 'http://127.0.0.1:8081', changeOrigin: false,
}]));
export default defineConfig({ plugins: [react()], server: { host: 'localhost', port: 5173, strictPort: true, proxy },
  test: { environment: 'jsdom', setupFiles: ['./src/test/setup.ts'], include: ['src/**/*.test.{ts,tsx}'], restoreMocks: true },
});
