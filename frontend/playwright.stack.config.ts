import { defineConfig, devices } from '@playwright/test';

/**
 * Smoke e regressao contra a stack de verdade: os servicos do backend no Docker e o
 * front servido com o proxy de /api para o gateway. Sobe tudo e roda com:
 *
 *   bash scripts/smoke.sh
 *
 * Com a stack ja de pe:  npm run smoke  ou  npm run regressao
 */
export default defineConfig({
  testDir: 'stack',
  // Em sequencia: as contas criadas dividem o limite de chamadas de login por IP.
  workers: 1,
  forbidOnly: !!process.env['CI'],
  retries: 0,
  timeout: 60_000,
  reporter: process.env['CI']
    ? [['list'], ['html', { open: 'never', outputFolder: 'playwright-report-stack' }]]
    : 'list',
  outputDir: 'test-results-stack',
  use: {
    baseURL: 'http://localhost:4400',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'computador', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    // Build de desenvolvimento com o proxy.conf.json: /api vai para o gateway em 8080.
    command: 'npx ng serve --port 4400',
    url: 'http://localhost:4400',
    reuseExistingServer: !process.env['CI'],
    timeout: 180_000,
  },
});
