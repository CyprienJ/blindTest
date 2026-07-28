import { expect, test } from './fixtures/test';
import { mockJson, testUser } from './support/auth';

test.describe('login', () => {
  test('exposes the form semantics and validates keyboard submission', async ({ page }) => {
    await page.goto('/auth/login');

    await expect(page.locator('.bt-card')).toMatchAriaSnapshot(`
      - heading "Welcome back" [level=1]
      - paragraph: Sign in to your account
      - text: Email
      - textbox "Email"
      - text: Password
      - textbox "Password"
      - button "Show password": 👁
      - button "Sign in"
      - link "Forgot your password?"
    `);

    await page.getByLabel('Email').fill('not-an-email');
    await page.getByLabel('Password', { exact: true }).press('Enter');

    await expect(page.getByText('Enter a valid email address')).toBeVisible();
    await expect(page.getByText('Password is required')).toBeVisible();
  });

  test('signs in with Enter and returns to the requested protected route', async ({ page }) => {
    let requestBody: unknown;
    await page.route('**/api/auth/login', async (route) => {
      requestBody = route.request().postDataJSON();
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ accessToken: 'login-access-token', user: testUser }),
      });
    });
    await page.goto('/auth/login?returnUrl=%2Flobby%2Fcreate');

    await page.getByLabel('Email').fill('musicfan@example.com');
    await page.getByLabel('Password', { exact: true }).fill('correct-horse');
    await page.getByLabel('Password', { exact: true }).press('Enter');

    await expect(page).toHaveURL(/\/lobby\/create$/);
    await expect(page.getByText('CreateSession works')).toBeVisible();
    expect(requestBody).toEqual({ email: 'musicfan@example.com', password: 'correct-horse' });
    await expect
      .poll(() => page.evaluate(() => localStorage.getItem('access_token')))
      .toBe('login-access-token');
    await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible();
  });

  for (const scenario of [
    { status: 401, message: 'Invalid email or password.' },
    { status: 403, message: 'Please verify your email before signing in.' },
    { status: 500, message: 'Something went wrong. Please try again.' },
  ]) {
    test(`shows the ${scenario.status} server error`, async ({ page }) => {
      await mockJson(page, '/api/auth/login', scenario.status, { detail: 'Rejected' });
      await page.goto('/auth/login');

      await page.getByLabel('Email').fill('musicfan@example.com');
      await page.getByLabel('Password', { exact: true }).fill('wrong-password');
      await page.getByRole('button', { name: 'Sign in' }).click();

      await expect(page.getByRole('alert')).toHaveText(scenario.message);
      await expect(page.getByRole('button', { name: 'Sign in' })).toBeEnabled();
    });
  }
});
