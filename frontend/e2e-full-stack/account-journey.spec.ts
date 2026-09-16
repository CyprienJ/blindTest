import { APIRequestContext, expect, test } from '@playwright/test';

// Drives the real account lifecycle against the isolated Compose stack from
// docker-compose.test.yml (E2E-01): a real Angular build, a real backend, and real
// PostgreSQL — no API mocking. Cloudflare Turnstile solves for real using its
// documented always-pass test key pair (see docker-compose.test.yml); email is
// captured server-side by FakeEmailSender and read back through the e2e-only
// /api/e2e/emails endpoint instead of a real mailbox (E2E-02).
//
// One continuous browser session carries state (the access token in localStorage,
// the HttpOnly refresh cookie) across every step, the way a real user session
// would, so the whole journey — registration, captured verification, login, access
// refresh, password reset, logout — runs as a single test.

interface CapturedEmail {
  type: 'VERIFICATION' | 'PASSWORD_RESET' | 'WELCOME';
  toEmail: string;
  username: string;
  token: string | null;
}

async function capturedEmail(
  request: APIRequestContext,
  baseURL: string,
  to: string,
  type: CapturedEmail['type'],
): Promise<CapturedEmail> {
  let match: CapturedEmail | undefined;
  await expect
    .poll(
      async () => {
        const response = await request.get(`${baseURL}/api/e2e/emails`, { params: { to } });
        expect(response.ok()).toBe(true);
        const emails = (await response.json()) as CapturedEmail[];
        const matching = emails.filter((email) => email.type === type);
        match = matching[matching.length - 1];
        return match !== undefined;
      },
      { message: `waiting for a captured ${type} email sent to ${to}`, timeout: 10_000 },
    )
    .toBe(true);
  return match!;
}

test('completes the account journey: register, verify, log in, refresh, reset password, log out', async ({
  page,
  request,
  baseURL,
}) => {
  const stamp = Date.now();
  const username = `e2e-journey-${stamp}`;
  const email = `${username}@example.test`;
  const password = 'StrongPassword!1';
  const newPassword = 'EvenStrongerPassword!2';

  await test.step('registration', async () => {
    await page.goto('/auth/register');
    await page.getByLabel('Username').fill(username);
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password', { exact: true }).fill(password);

    // The real Cloudflare widget (dummy test sitekey) solves itself without interaction.
    await expect(page.getByRole('button', { name: 'Create account' })).toBeEnabled({
      timeout: 15_000,
    });
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page).toHaveURL(/\/auth\/verify-email\?.*mode=check-inbox/);
    await expect(page.getByRole('heading', { name: 'Check your inbox' })).toBeVisible();
  });

  let verificationToken = '';
  await test.step('captured verification', async () => {
    const captured = await capturedEmail(request, baseURL!, email, 'VERIFICATION');
    expect(captured.username).toBe(username);
    expect(captured.token).toBeTruthy();
    verificationToken = captured.token!;

    await page.goto(`/verify-email?token=${encodeURIComponent(verificationToken)}`);
    await expect(page.getByRole('heading', { name: 'Email verified!' })).toBeVisible();
  });

  await test.step('login', async () => {
    await page.goto('/auth/login');
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password', { exact: true }).fill(password);
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page).toHaveURL(/\/home$/);
    await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible();
  });

  await test.step('access refresh', async () => {
    const loggedInUser = await page.evaluate(() => {
      const raw = window.localStorage.getItem('current_user');
      return raw ? (JSON.parse(raw) as { id: string }) : null;
    });
    expect(loggedInUser?.id).toBeTruthy();

    // page.request shares the browser context's cookie jar, so this exercises the
    // real HttpOnly refresh cookie the login step above received from the backend.
    const refreshResponse = await page.request.post('/api/auth/refresh');
    expect(refreshResponse.ok()).toBe(true);
    const { accessToken: rotatedAccessToken } = (await refreshResponse.json()) as {
      accessToken: string;
    };

    // A byte-for-byte comparison against the pre-refresh token would be flaky: the
    // JWT `iat`/`exp` claims have one-second granularity, so a refresh within the
    // same wall-clock second as login legitimately yields an identical token.
    // Decoding it instead proves the backend actually issued a real signed token
    // bound to this account from the httpOnly cookie, not just any 200 response.
    const [, payloadSegment] = rotatedAccessToken.split('.');
    expect(payloadSegment).toBeTruthy();
    const payload = JSON.parse(Buffer.from(payloadSegment, 'base64url').toString('utf8')) as {
      sub: string;
    };
    expect(payload.sub).toBe(loggedInUser!.id);
  });

  await test.step('password reset', async () => {
    await page.goto('/auth/forgot-password');
    await page.getByLabel('Email').fill(email);
    await page.getByRole('button', { name: 'Send reset link' }).click();
    await expect(page.getByRole('heading', { name: 'Check your inbox' })).toBeVisible();

    const captured = await capturedEmail(request, baseURL!, email, 'PASSWORD_RESET');
    expect(captured.token).toBeTruthy();

    await page.goto(`/reset-password?token=${encodeURIComponent(captured.token!)}`);
    await page.getByLabel('New password').fill(newPassword);
    await page.getByRole('button', { name: 'Set new password' }).click();
    await expect(page.getByRole('heading', { name: 'Password updated!' })).toBeVisible();

    // Prove the password actually changed server-side, not just that the UI moved on.
    await page.goto('/auth/login');
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password', { exact: true }).fill(newPassword);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/home$/);
  });

  await test.step('logout', async () => {
    await page.getByRole('button', { name: 'Sign out' }).click();
    await expect(page).toHaveURL(/\/$/);

    // Prove the server actually revoked the session: the refresh cookie no longer works.
    const refreshAfterLogout = await page.request.post('/api/auth/refresh');
    expect(refreshAfterLogout.status()).toBe(401);
  });
});
