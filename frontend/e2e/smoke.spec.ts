import { expect, test } from './fixtures/test';

test('renders the login page', async ({ page }) => {
  await page.goto('/');

  await expect(page).toHaveURL(/\/auth\/login$/);
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
});
