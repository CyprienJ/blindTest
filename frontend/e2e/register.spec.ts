import { expect, test } from './fixtures/test';
import {
  expireTurnstile,
  failTurnstile,
  mockJson,
  solveTurnstile,
  testUser,
  turnstileResetCount,
} from './support/auth';

test.describe('registration and Turnstile', () => {
  test('exposes form semantics, validates with Enter, and tracks Turnstile expiry', async ({
    page,
  }) => {
    await page.goto('/auth/register');

    await expect(page.locator('.bt-card')).toMatchAriaSnapshot(`
      - heading "Create an account" [level=1]
      - paragraph: Join the game
      - text: Username
      - textbox "Username"
      - text: Email
      - textbox "Email"
      - text: Password
      - textbox "Password"
      - button "Show password": 👁
      - button "Create account" [disabled]
    `);
    await solveTurnstile(page);
    await expect(page.getByRole('button', { name: 'Create account' })).toBeEnabled();

    await page.getByLabel('Username').fill('ab');
    await page.getByLabel('Email').fill('bad-email');
    await page.getByLabel('Password', { exact: true }).fill('short');
    await page.getByLabel('Password', { exact: true }).press('Enter');

    await expect(page.getByText('3–32 characters required')).toBeVisible();
    await expect(page.getByText('Enter a valid email address')).toBeVisible();
    await expect(page.getByText('At least 8 characters required')).toBeVisible();

    await expireTurnstile(page);
    await expect(page.getByRole('button', { name: 'Create account' })).toBeDisabled();
    await expect(page.getByRole('alert')).toHaveText(
      'Security check expired. Please complete it again.',
    );

    await solveTurnstile(page, 'replacement-token');
    await expect(page.getByRole('button', { name: 'Create account' })).toBeEnabled();
    await expect(page.getByRole('alert')).toBeHidden();

    expect(await failTurnstile(page)).toBe(true);
    await expect(page.getByRole('button', { name: 'Create account' })).toBeDisabled();
    await expect(page.getByRole('alert')).toHaveText(
      'Security check could not be completed. Please try again.',
    );
  });

  test('registers without Cloudflare and shows the check-inbox state', async ({ page }) => {
    let requestBody: unknown;
    await page.route('**/api/auth/register', async (route) => {
      requestBody = route.request().postDataJSON();
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(testUser),
      });
    });
    await page.goto('/auth/register');
    await solveTurnstile(page, 'deterministic-turnstile-token');

    await page.getByLabel('Username').fill('musicfan');
    await page.getByLabel('Email').fill('musicfan@example.com');
    await page.getByLabel('Password', { exact: true }).fill('StrongPassword!');
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page).toHaveURL(/\/auth\/verify-email\?.*mode=check-inbox/);
    await expect(page.getByRole('heading', { name: 'Check your inbox' })).toBeVisible();
    await expect(page.getByText('musicfan@example.com')).toBeVisible();
    expect(requestBody).toEqual({
      username: 'musicfan',
      email: 'musicfan@example.com',
      password: 'StrongPassword!',
      turnstileToken: 'deterministic-turnstile-token',
    });
  });

  test('sends a single-use token only once when the form is submitted twice', async ({ page }) => {
    let requestCount = 0;
    let releaseRequest!: () => void;
    const pendingResponse = new Promise<void>((resolve) => {
      releaseRequest = resolve;
    });
    await page.route('**/api/auth/register', async (route) => {
      requestCount += 1;
      await pendingResponse;
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(testUser),
      });
    });
    await page.goto('/auth/register');
    await solveTurnstile(page, 'single-use-token');
    await page.getByLabel('Username').fill('musicfan');
    await page.getByLabel('Email').fill('musicfan@example.com');
    await page.getByLabel('Password', { exact: true }).fill('StrongPassword!');

    await page.locator('form').evaluate((form) => {
      form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    });

    await expect.poll(() => requestCount).toBe(1);
    await expect(page.getByRole('button', { name: 'Creating account…' })).toBeDisabled();
    releaseRequest();
    await expect(page).toHaveURL(/\/auth\/verify-email\?.*mode=check-inbox/);
    expect(requestCount).toBe(1);
  });

  for (const scenario of [
    { status: 409, detail: 'EMAIL_ALREADY_EXISTS', message: 'Email already in use.' },
    {
      status: 403,
      detail: 'TURNSTILE_REJECTED',
      message: 'Security check failed. Please try again.',
    },
    {
      status: 500,
      detail: 'Unavailable',
      message: 'Something went wrong. Please try again.',
    },
  ]) {
    test(`shows the ${scenario.status} registration error and resets Turnstile`, async ({
      page,
    }) => {
      await mockJson(page, '/api/auth/register', scenario.status, { detail: scenario.detail });
      await page.goto('/auth/register');
      await solveTurnstile(page);
      await page.getByLabel('Username').fill('musicfan');
      await page.getByLabel('Email').fill('musicfan@example.com');
      await page.getByLabel('Password', { exact: true }).fill('StrongPassword!');

      await page.getByRole('button', { name: 'Create account' }).click();

      await expect(page.getByRole('alert')).toHaveText(scenario.message);
      await expect(page.getByRole('button', { name: 'Create account' })).toBeDisabled();
      await expect.poll(() => turnstileResetCount(page)).toBe(1);
    });
  }
});
