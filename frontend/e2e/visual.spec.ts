import { expect, test } from '@playwright/test';

/**
 * A deliberately short list: only screens with no timers, animation, or
 * server-driven content are snapshotted, so a failure means an actual visual
 * change rather than environment noise.
 */
test.describe('stable visual snapshots', () => {
  // Pixel baselines are Chromium/Linux-specific; cross-browser coverage for
  // these screens is already exercised functionally in accessibility.spec.ts
  // and public-home.spec.ts / login.spec.ts.
  test.skip(({ browserName }) => browserName !== 'chromium', 'chromium-only baseline');

  test('public home', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { name: /Hear it\. Name it\./ })).toBeVisible();
    await expect(page).toHaveScreenshot('public-home.png', { fullPage: true });
  });

  test('sign-in form', async ({ page }) => {
    await page.goto('/auth/login');
    await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
    await expect(page).toHaveScreenshot('login.png', { fullPage: true });
  });
});
