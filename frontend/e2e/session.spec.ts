import { expect, test } from './fixtures/test';
import { installSession, mockJson } from './support/auth';

test.describe('authentication session', () => {
  test('refreshes an access token before entering a protected route', async ({ page }) => {
    await mockJson(page, '/api/auth/refresh', 200, { accessToken: 'refreshed-access-token' });

    await page.goto('/lobby');

    await expect(page).toHaveURL(/\/lobby\/join$/);
    await expect(page.getByText('JoinSession works')).toBeVisible();
    await expect
      .poll(() => page.evaluate(() => localStorage.getItem('access_token')))
      .toBe('refreshed-access-token');
  });

  test('redirects an unauthorized visitor and preserves the return URL', async ({ page }) => {
    await mockJson(page, '/api/auth/refresh', 401, { detail: 'No refresh session' });

    await page.goto('/profile');

    await expect(page).toHaveURL(/\/auth\/login\?returnUrl=%2Fprofile%2Fme$/);
  });

  test('uses an existing authenticated session without refreshing', async ({ page }) => {
    await installSession(page);

    await page.goto('/lobby');

    await expect(page).toHaveURL(/\/lobby\/join$/);
    await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible();
  });

  for (const scenario of [
    { name: 'accepted', status: 204 },
    { name: 'server failure', status: 500 },
  ]) {
    test(`clears the local session when logout is ${scenario.name}`, async ({ page }) => {
      await installSession(page);
      await mockJson(page, '/api/auth/logout', scenario.status);
      await page.goto('/lobby');

      await page.getByRole('button', { name: 'Sign out' }).click();

      await expect(page).toHaveURL(/\/auth\/login$/);
      await expect
        .poll(() =>
          page.evaluate(() => ({
            accessToken: localStorage.getItem('access_token'),
            currentUser: localStorage.getItem('current_user'),
          })),
        )
        .toEqual({ accessToken: null, currentUser: null });
    });
  }
});
