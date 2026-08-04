import { expect, test } from './fixtures/test';
import { installSession } from './support/auth';

test.describe('public home', () => {
  test('presents the product and account actions to an anonymous visitor', async ({ page }) => {
    await page.goto('/');

    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByRole('heading', { name: /Hear it\. Name it\./ })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Sign in' })).toHaveAttribute(
      'href',
      '/auth/login',
    );
    await expect(page.getByRole('link', { name: 'Create account' })).toHaveAttribute(
      'href',
      '/auth/register',
    );
    await expect(page.getByRole('heading', { name: 'Trust your ears.' })).toBeVisible();
  });

  test('lets an authenticated visitor continue to the existing player experience', async ({
    page,
  }) => {
    await installSession(page);
    await page.route('**/api/playlists', (route) => route.fulfill({ json: [] }));
    await page.goto('/');

    const continueLink = page.getByRole('link', { name: 'Continue as musicfan' });
    await expect(continueLink).toHaveAttribute('href', '/home');
    await continueLink.click();

    await expect(page).toHaveURL(/\/lobby\/create$/);
    await expect(page.getByRole('heading', { name: 'Configure a new game' })).toBeVisible();
  });
});
