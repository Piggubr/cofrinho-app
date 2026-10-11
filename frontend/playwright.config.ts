import { defineConfig, devices } from '@playwright/test';

/**
 * Ponta a ponta contra o build "demo": o app inteiro no navegador, com a API falsa
 * em memoria (src/app/demo). Nao precisa de backend nem de conta Google.
 *
 *   npm run e2e
 */
export default defineConfig({
  testDir: 'e2e',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: 0,
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:4300',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'celular', use: { ...devices['Pixel 7'] } },
    { name: 'computador', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    command: 'npx ng serve --configuration demo --port 4300',
    url: 'http://localhost:4300',
    reuseExistingServer: !process.env['CI'],
    timeout: 180_000,
  },
});
