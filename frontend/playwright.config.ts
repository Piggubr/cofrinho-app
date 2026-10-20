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
    // Cookies ja escolhidos, para o banner nao cobrir as abas; o proprio banner tem
    // teste em e2e/cookies.spec.ts. A versao e a de VERSAO_DOS_COOKIES.
    storageState: {
      cookies: [],
      origins: [
        {
          origin: 'http://localhost:4300',
          localStorage: [
            { name: 'piggu.cookies', value: '{"versao":"2026-10-19","analise":false,"em":"2026-10-19"}' },
          ],
        },
      ],
    },
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
