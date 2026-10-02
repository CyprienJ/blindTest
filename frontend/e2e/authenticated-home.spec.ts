import { expect, test } from './fixtures/test';
import { installSession, mockJson, testUser } from './support/auth';

test.describe('authenticated home', () => {
  test('is the default destination after login', async ({ page }) => {
    await mockJson(page, '/api/auth/login', 200, {
      accessToken: 'login-access-token',
      user: testUser,
    });
    await page.goto('/auth/login');

    await page.getByLabel('Email').fill('musicfan@example.com');
    await page.getByLabel('Password', { exact: true }).fill('correct-horse');
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page).toHaveURL(/\/home$/);
    await expect(
      page.getByRole('heading', { name: /Ready for the next track, musicfan/ }),
    ).toBeVisible();
  });

  test('shows player actions without privileged administration links', async ({ page }) => {
    await installSession(page);
    await page.goto('/home');

    await expect(
      page.getByRole('heading', { name: /Ready for the next track, musicfan/ }),
    ).toBeVisible();
    await expect(page.getByRole('link', { name: /Create a game/ })).toHaveAttribute(
      'href',
      '/lobby/create',
    );
    await expect(page.getByText('Join a game')).toBeVisible();
    await expect(page.getByRole('link', { name: /Join a game/ })).toHaveAttribute('href', '/lobby/join');
    await expect(page.getByRole('link', { name: /Edit playlists/ })).toHaveCount(0);
  });

  test('shows playlist administration only to an administrator', async ({ page }) => {
    await installSession(page, { ...testUser, role: 'ADMIN' });
    await page.goto('/home');

    await expect(page.getByRole('link', { name: /Edit playlists/ })).toHaveAttribute(
      'href',
      '/admin/playlists',
    );
  });

  test('keeps navigation clear of account controls and returns home from the logo', async ({
    page,
  }) => {
    await installSession(page, { ...testUser, role: 'ADMIN' });
    await page.route('**/api/playlists', (route) => route.fulfill({ json: [] }));
    await page.goto('/lobby/create');

    const navigation = page.getByRole('navigation', { name: 'Primary navigation' });
    const signOut = page.getByRole('button', { name: 'Sign out' });
    const [navigationBox, signOutBox] = await Promise.all([
      navigation.boundingBox(),
      signOut.boundingBox(),
    ]);
    expect(navigationBox).not.toBeNull();
    expect(signOutBox).not.toBeNull();
    expect((navigationBox?.x ?? 0) + (navigationBox?.width ?? 0)).toBeLessThanOrEqual(
      signOutBox?.x ?? 0,
    );

    await page.getByRole('link', { name: 'Blindtest home' }).click();
    await expect(page).toHaveURL(/\/home$/);
  });

  test('restores a missing access token before rendering the player home', async ({ page }) => {
    await page.addInitScript((user) => {
      localStorage.setItem('current_user', JSON.stringify(user));
      localStorage.removeItem('access_token');
    }, testUser);
    await mockJson(page, '/api/auth/refresh', 200, { accessToken: 'restored-access-token' });

    await page.goto('/home');

    await expect(
      page.getByRole('heading', { name: /Ready for the next track, musicfan/ }),
    ).toBeVisible();
    await expect
      .poll(() => page.evaluate(() => localStorage.getItem('access_token')))
      .toBe('restored-access-token');
  });

  test('preserves the home return URL when authentication cannot be restored', async ({ page }) => {
    await mockJson(page, '/api/auth/refresh', 401, { detail: 'No refresh session' });

    await page.goto('/home');

    await expect(page).toHaveURL(/\/auth\/login\?returnUrl=%2Fhome$/);
  });
});
