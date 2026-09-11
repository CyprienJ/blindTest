import { expect, test } from './fixtures/test';
import { installSession, mockJson, testUser } from './support/auth';

test.describe('authenticated application shell', () => {
  test('shows the player navigation with disabled placeholders and no admin entry', async ({
    page,
  }) => {
    await installSession(page);
    await page.goto('/home');

    const nav = page.getByRole('navigation', { name: 'Primary navigation' });
    await expect(nav.getByRole('link', { name: 'Play', exact: true })).toHaveAttribute(
      'href',
      '/lobby/create',
    );
    await expect(nav.getByText('Friends')).toHaveAttribute('aria-disabled', 'true');
    await expect(nav.getByText('History')).toHaveAttribute('aria-disabled', 'true');
    await expect(nav.getByRole('link', { name: 'Friends' })).toHaveCount(0);
    await expect(nav.getByRole('link', { name: 'History' })).toHaveCount(0);
    await expect(nav.getByRole('link', { name: 'Admin' })).toHaveCount(0);

    const banner = page.getByRole('banner');
    await expect(banner.getByText(testUser.username)).toBeVisible();
    await expect(banner.getByRole('button', { name: 'Sign out' })).toBeVisible();
  });

  test('keeps the administrator navigation correct after a reload', async ({ page }) => {
    await installSession(page, { ...testUser, role: 'ADMIN' });
    await page.route('**/api/playlists', (route) => route.fulfill({ json: [] }));
    await page.goto('/lobby/create');

    const adminLink = page
      .getByRole('navigation', { name: 'Primary navigation' })
      .getByRole('link', { name: 'Admin' });
    await expect(adminLink).toHaveAttribute('href', '/admin');

    await page.reload();
    await expect(adminLink).toHaveAttribute('href', '/admin');

    await adminLink.click();
    await expect(page).toHaveURL(/\/admin\/tracks$/);
    await expect(page.getByRole('complementary', { name: 'Administration' })).toBeVisible();
  });

  test('redirects inaccessible routes safely', async ({ page }) => {
    await installSession(page);

    await page.goto('/admin/tracks');
    await expect(page).toHaveURL(/\/home$/);

    await page.goto('/nope/not/here');
    await expect(page).toHaveURL(/\/auth\/login$/);
  });

  test('opens public playlists from the shell after a player reload', async ({ page }) => {
    await installSession(page);
    await mockJson(page, '/api/playlists', 200, []);
    await page.goto('/home');
    await page.reload();
    const nav = page.getByRole('navigation', { name: 'Primary navigation' });
    await expect(nav.getByRole('link', { name: 'Admin' })).toHaveCount(0);
    await nav.getByRole('link', { name: 'Playlists', exact: true }).click();
    await expect(page).toHaveURL(/\/lobby\/create#public-playlists$/);
    await expect(page.getByRole('heading', { name: 'No playable playlist yet' })).toBeVisible();
  });

  test('recovers administrator navigation when the access token is missing', async ({ page }) => {
    await page.addInitScript((user) => {
      localStorage.setItem('current_user', JSON.stringify({ ...user, role: 'ADMIN' }));
      localStorage.removeItem('access_token');
    }, testUser);
    await mockJson(page, '/api/auth/refresh', 200, { accessToken: 'restored-token' });
    await page.goto('/admin/tracks');
    await expect(
      page
        .getByRole('navigation', { name: 'Primary navigation' })
        .getByRole('link', { name: 'Admin' }),
    ).toBeVisible();
    await expect(page.getByRole('complementary', { name: 'Administration' })).toBeVisible();
  });

  test('clears stale privileged identity when session recovery fails', async ({ page }) => {
    await page.addInitScript((user) => {
      localStorage.setItem('current_user', JSON.stringify({ ...user, role: 'ADMIN' }));
    }, testUser);
    await mockJson(page, '/api/auth/refresh', 401, {});
    await page.goto('/admin/tracks');
    await expect(page).toHaveURL(/\/auth\/login\?returnUrl=%2Fadmin%2Ftracks$/);
    expect(await page.evaluate(() => localStorage.getItem('current_user'))).toBeNull();
    await expect(page.getByRole('navigation', { name: 'Primary navigation' })).toHaveCount(0);
  });

  test('rejects a refresh token response without a restored account', async ({ page }) => {
    await mockJson(page, '/api/auth/refresh', 200, { accessToken: 'orphan-token' });
    await page.goto('/home');
    await expect(page).toHaveURL(/\/auth\/login\?returnUrl=%2Fhome$/);
    expect(await page.evaluate(() => localStorage.getItem('access_token'))).toBeNull();
  });

  test('checks authentication again when navigating within the shell', async ({ page }) => {
    await installSession(page);
    await mockJson(page, '/api/auth/refresh', 401, {});
    await page.goto('/home');
    await page.evaluate(() => localStorage.removeItem('access_token'));
    await page
      .getByRole('navigation', { name: 'Primary navigation' })
      .getByRole('link', { name: 'Play', exact: true })
      .click();
    await expect(page).toHaveURL(/\/auth\/login\?returnUrl=%2Flobby%2Fcreate$/);
  });

  for (const status of [204, 500]) {
    test(`signs out locally when the server returns ${status}`, async ({ page }) => {
      await installSession(page, { ...testUser, role: 'ADMIN' });
      await page.route('**/api/auth/logout', (route) => route.fulfill({ status }));
      await page.goto('/home');
      await page.getByRole('button', { name: 'Sign out' }).click();
      await expect(page).toHaveURL('/');
      await expect(page.getByRole('navigation', { name: 'Primary navigation' })).toHaveCount(0);
      expect(await page.evaluate(() => localStorage.getItem('current_user'))).toBeNull();
      expect(await page.evaluate(() => localStorage.getItem('access_token'))).toBeNull();
    });
  }

  test('keeps shell controls inside the header on a narrow screen', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await installSession(page, { ...testUser, role: 'ADMIN' });
    await page.goto('/home');
    const banner = page.getByRole('banner');
    const headerBox = await banner.boundingBox();
    const logoutBox = await banner.getByRole('button', { name: 'Sign out' }).boundingBox();
    expect(headerBox).not.toBeNull();
    expect(logoutBox).not.toBeNull();
    expect(logoutBox!.y + logoutBox!.height).toBeLessThanOrEqual(headerBox!.y + headerBox!.height);
    expect(await banner.evaluate((element) => element.scrollWidth)).toBeLessThanOrEqual(390);
  });
});
