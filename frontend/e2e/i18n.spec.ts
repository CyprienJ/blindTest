import { expect, test } from './fixtures/test';

// The application defaults to French; every other spec pins English through storage state.
test.use({ storageState: { cookies: [], origins: [] } });

test.describe('language', () => {
  test('renders in French by default and switches to English', async ({ page }) => {
    await page.goto('/');

    await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
    await expect(page.getByRole('heading', { name: /Trouvez/ })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Se connecter', exact: true })).toBeVisible();

    await page.getByRole('button', { name: 'EN' }).click();

    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(page.getByRole('heading', { name: /Name it/ })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Sign in', exact: true })).toBeVisible();
    expect(await page.evaluate(() => localStorage.getItem('locale'))).toBe('en');
  });

  test('sends the chosen language to the backend', async ({ page }) => {
    let language: string | undefined;
    await page.route('**/api/auth/login', async (route) => {
      language = route.request().headers()['accept-language'];
      await route.fulfill({ status: 401, json: { code: 'INVALID_CREDENTIALS' } });
    });

    await page.goto('/auth/login');
    await page.getByLabel('Email').fill('player@example.com');
    await page.getByLabel('Mot de passe', { exact: true }).fill('password');
    await page.getByRole('button', { name: 'Se connecter' }).click();

    await expect(page.getByRole('alert')).toHaveText('Email ou mot de passe incorrect.');
    expect(language).toBe('fr');
  });
});
