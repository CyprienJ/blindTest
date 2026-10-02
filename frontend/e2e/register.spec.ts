import { Page } from '@playwright/test';
import { expect, test } from './fixtures/test';
import {
  expireTurnstile,
  failTurnstile,
  mockJson,
  solveTurnstile,
  testUser,
  turnstileResetCount,
} from './support/auth';

const INVITE_URL = '/auth/register?invite=invite-token';

async function mockValidInvitation(page: Page): Promise<void> {
  await mockJson(page, '/api/auth/invitations/lookup', 200, {
    email: 'musicfan@example.com',
    expiresAt: '2026-10-04T08:00:00Z',
  });
}

test.describe('invitation-only registration and Turnstile', () => {
  test('explains invitation-only access when the link carries no invitation', async ({ page }) => {
    await page.goto('/auth/register');

    await expect(page.getByRole('heading', { name: 'Invitation only' })).toBeVisible();
    await expect(page.locator('form')).toHaveCount(0);
  });

  test('explains that an unusable invitation must be renewed', async ({ page }) => {
    await mockJson(page, '/api/auth/invitations/lookup', 400, { code: 'INVITATION_INVALID' });
    await page.goto(INVITE_URL);

    await expect(page.getByRole('heading', { name: 'Invitation unavailable' })).toBeVisible();
    await expect(page.getByText('Ask an administrator for a new one.')).toBeVisible();
  });

  test('exposes form semantics, validates with Enter, and tracks Turnstile expiry', async ({
    page,
  }) => {
    await mockValidInvitation(page);
    await page.goto(INVITE_URL);

    await expect(page.locator('.bt-card')).toMatchAriaSnapshot(`
      - heading "Create an account" [level=1]
      - paragraph: You're invited to join the game
      - text: Email
      - textbox "Email": musicfan@example.com
      - text: Username
      - textbox "Username"
      - text: Password
      - textbox "Password"
      - button "Show password": 👁
      - button "Create account" [disabled]
    `);
    await solveTurnstile(page);
    await expect(page.getByRole('button', { name: 'Create account' })).toBeEnabled();

    await page.getByLabel('Username').fill('ab');
    await page.getByLabel('Password', { exact: true }).fill('short');
    await page.getByLabel('Password', { exact: true }).press('Enter');

    await expect(page.getByText('3–32 characters required')).toBeVisible();
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

  test('registers from the invitation and signs the new player in', async ({ page }) => {
    let requestBody: unknown;
    await mockValidInvitation(page);
    await page.route('**/api/auth/register', async (route) => {
      requestBody = route.request().postDataJSON();
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(testUser),
      });
    });
    await mockJson(page, '/api/auth/login', 200, {
      accessToken: 'new-access-token',
      user: testUser,
    });
    await mockJson(page, '/api/playlists', 200, []);
    await page.goto(INVITE_URL);
    await solveTurnstile(page, 'deterministic-turnstile-token');

    await page.getByLabel('Username').fill('musicfan');
    await page.getByLabel('Password', { exact: true }).fill('StrongPassword!');
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page).toHaveURL(/\/home$/);
    expect(requestBody).toEqual({
      username: 'musicfan',
      inviteToken: 'invite-token',
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
    await mockValidInvitation(page);
    await page.route('**/api/auth/register', async (route) => {
      requestCount += 1;
      await pendingResponse;
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(testUser),
      });
    });
    await mockJson(page, '/api/auth/login', 401, { code: 'INVALID_CREDENTIALS' });
    await page.goto(INVITE_URL);
    await solveTurnstile(page, 'single-use-token');
    await page.getByLabel('Username').fill('musicfan');
    await page.getByLabel('Password', { exact: true }).fill('StrongPassword!');

    await page.locator('form').evaluate((form) => {
      form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    });

    await expect.poll(() => requestCount).toBe(1);
    await expect(page.getByRole('button', { name: 'Creating account…' })).toBeDisabled();
    releaseRequest();
    await expect(page).toHaveURL(/\/auth\/login$/);
    expect(requestCount).toBe(1);
  });

  for (const scenario of [
    { status: 409, code: 'USERNAME_TAKEN', message: 'Username already taken.' },
    {
      status: 403,
      code: 'TURNSTILE_FAILED',
      message: 'Security check failed. Please try again.',
    },
    {
      status: 500,
      code: 'INTERNAL_ERROR',
      message: 'Something went wrong. Please try again.',
    },
  ]) {
    test(`shows the ${scenario.status} registration error and resets Turnstile`, async ({
      page,
    }) => {
      await mockValidInvitation(page);
      await mockJson(page, '/api/auth/register', scenario.status, { code: scenario.code });
      await page.goto(INVITE_URL);
      await solveTurnstile(page);
      await page.getByLabel('Username').fill('musicfan');
      await page.getByLabel('Password', { exact: true }).fill('StrongPassword!');

      await page.getByRole('button', { name: 'Create account' }).click();

      await expect(page.getByRole('alert')).toHaveText(scenario.message);
      await expect(page.getByRole('button', { name: 'Create account' })).toBeDisabled();
      await expect.poll(() => turnstileResetCount(page)).toBe(1);
    });
  }
});
