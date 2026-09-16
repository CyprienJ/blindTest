import { expect, test } from './fixtures/test';
import { mockJson } from './support/auth';

test.describe('email verification', () => {
  test('shows the registration check-inbox state without an API call', async ({ page }) => {
    await page.goto('/auth/verify-email?mode=check-inbox&email=listener%40example.com');

    await expect(page.getByRole('heading', { name: 'Check your inbox' })).toBeVisible();
    await expect(page.getByText('listener@example.com')).toBeVisible();
  });

  test('verifies a token successfully', async ({ page }) => {
    let requestBody: unknown;
    await page.route('**/api/auth/verify-email', async (route) => {
      requestBody = route.request().postDataJSON();
      await route.fulfill({ status: 204 });
    });

    await page.goto('/verify-email?token=verification-token');

    await expect(page.getByRole('heading', { name: 'Email verified!' })).toBeVisible();
    expect(requestBody).toEqual({ token: 'verification-token' });
  });

  test('shows an invalid link when verification is rejected', async ({ page }) => {
    await mockJson(page, '/api/auth/verify-email', 400, { detail: 'Expired token' });

    await page.goto('/verify-email?token=expired-token');

    await expect(page.getByRole('heading', { name: 'Invalid link' })).toBeVisible();
    await expect(page.getByText('invalid or has expired')).toBeVisible();
  });
});

test.describe('forgot password', () => {
  test('validates keyboard submission', async ({ page }) => {
    await page.goto('/auth/forgot-password');
    await page.getByLabel('Email').fill('invalid');
    await page.getByLabel('Email').press('Enter');

    await expect(page.getByText('Enter a valid email address')).toBeVisible();
  });

  for (const scenario of [
    { name: 'accepted', status: 204 },
    { name: 'server failure', status: 500 },
  ]) {
    test(`keeps account existence private when the request is ${scenario.name}`, async ({
      page,
    }) => {
      await mockJson(page, '/api/auth/forgot-password', scenario.status);
      await page.goto('/auth/forgot-password');
      await page.getByLabel('Email').fill('listener@example.com');
      await page.getByLabel('Email').press('Enter');

      await expect(page.getByRole('heading', { name: 'Check your inbox' })).toBeVisible();
      await expect(page.getByText(/If an account exists/)).toBeVisible();
    });
  }
});

test.describe('reset password', () => {
  test('rejects a missing token before submission', async ({ page }) => {
    await page.goto('/reset-password');

    await expect(page.getByRole('heading', { name: 'Invalid link' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Request a new link' })).toBeVisible();
  });

  test('validates with Enter and updates the password', async ({ page }) => {
    let requestBody: unknown;
    await page.route('**/api/auth/reset-password', async (route) => {
      requestBody = route.request().postDataJSON();
      await route.fulfill({ status: 204 });
    });
    await page.goto('/reset-password?token=reset-token');

    await page.getByLabel('New password').fill('short');
    await page.getByLabel('New password').press('Enter');
    await expect(page.getByText('At least 8 characters required')).toBeVisible();

    await page.getByLabel('New password').fill('NewPassword!');
    await page.getByLabel('New password').press('Enter');

    await expect(page.getByRole('heading', { name: 'Password updated!' })).toBeVisible();
    expect(requestBody).toEqual({ token: 'reset-token', newPassword: 'NewPassword!' });
  });

  test('shows an invalid link for an expired reset token', async ({ page }) => {
    await mockJson(page, '/api/auth/reset-password', 400, { detail: 'Expired token' });
    await page.goto('/reset-password?token=expired-token');
    await page.getByLabel('New password').fill('NewPassword!');
    await page.getByRole('button', { name: 'Set new password' }).click();

    await expect(page.getByRole('heading', { name: 'Invalid link' })).toBeVisible();
  });

  test('keeps the form available after a server failure', async ({ page }) => {
    await mockJson(page, '/api/auth/reset-password', 500, { detail: 'Unavailable' });
    await page.goto('/reset-password?token=reset-token');
    await page.getByLabel('New password').fill('NewPassword!');
    await page.getByRole('button', { name: 'Set new password' }).click();

    await expect(page.getByRole('alert')).toHaveText('Something went wrong. Please try again.');
    await expect(page.getByRole('button', { name: 'Set new password' })).toBeEnabled();
  });
});
