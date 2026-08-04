import { expect, test } from './fixtures/test';

test('renders the public home page', async ({ page }) => {
  await page.goto('/');

  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole('heading', { name: /Hear it\. Name it\./ })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Start playing' })).toBeVisible();
});
