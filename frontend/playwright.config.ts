import { defineConfig, devices } from '@playwright/test';

const baseURL = 'http://127.0.0.1:4200';
const chromiumExecutablePath = process.env['PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH'];

export default defineConfig({
  testDir: './e2e',
  outputDir: 'test-results',
  fullyParallel: true,
  forbidOnly: Boolean(process.env['CI']),
  retries: process.env['CI'] ? 2 : 0,
  workers: process.env['CI'] ? 1 : undefined,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
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
  // Visual snapshots are taken on Linux (local and CI both run Ubuntu), but font
  // hinting still drifts a little between machines; tolerate a small pixel diff
  // instead of chasing per-machine baselines.
  expect: {
    toHaveScreenshot: { maxDiffPixelRatio: 0.02 },
  },
  projects: [
    {
      // The only browser required to pass on every push; see e2e/README.md.
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        launchOptions: chromiumExecutablePath
          ? { executablePath: chromiumExecutablePath }
          : undefined,
      },
    },
    {
      // Scheduled coverage only (nightly workflow), not required on every push.
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      // Scheduled coverage only (nightly workflow), not required on every push.
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
  ],
  webServer: {
    command: 'npm start -- --host 127.0.0.1 --port 4200',
    url: baseURL,
    reuseExistingServer: !process.env['CI'],
    timeout: 120_000,
  },
});
