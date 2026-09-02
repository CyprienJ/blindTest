import { expect, test } from './fixtures/test';
import { installSession, testUser } from './support/auth';

test.describe('authenticated application shell', () => {
  test('shows the player navigation with disabled placeholders and no admin entry', async ({
    page,
  }) => {
    await installSession(page);
    await page.goto('/home');

    const nav = page.getByRole('navigation', { name: 'Primary navigation' });
    await expect(nav.getByRole('link', { name: 'Play' })).toHaveAttribute('href', '/lobby/create');
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
    await expect(page).toHaveURL(/\/lobby\/join$/);

    await page.goto('/nope/not/here');
    await expect(page).toHaveURL(/\/auth\/login$/);
  });
});
