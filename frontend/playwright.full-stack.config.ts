import { defineConfig, devices } from '@playwright/test';

// Targets the isolated Docker Compose stack from docker-compose.test.yml (E2E-01): a real
// frontend, backend, PostgreSQL, and MinIO, with Resend/yt-dlp faked out server-side and
// Turnstile faked via Cloudflare's test key pair. Unlike playwright.config.ts, this suite
// makes no API mocks and starts no webServer — docker-compose.test.yml owns the stack
// lifecycle; run it with `./scripts/e2e-test.sh` from the repository root.
const baseURL = process.env['PLAYWRIGHT_BASE_URL'] ?? 'http://127.0.0.1:8091';

export default defineConfig({
  testDir: './e2e-full-stack',
  outputDir: 'test-results-full-stack',
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env['CI']),
  retries: process.env['CI'] ? 1 : 0,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report-full-stack', open: 'never' }]],
  use: {
    baseURL,
    // The UI defaults to French; specs read the English source text.
    storageState: {
      cookies: [],
      origins: [
        { origin: new URL(baseURL).origin, localStorage: [{ name: 'locale', value: 'en' }] },
      ],
    },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
