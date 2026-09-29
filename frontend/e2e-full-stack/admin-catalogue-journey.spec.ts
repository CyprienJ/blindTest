import { APIRequestContext, expect, test } from '@playwright/test';

// Drives the real admin catalogue lifecycle against the isolated Compose stack from
// docker-compose.test.yml (E2E-01): a real Angular build, a real backend, real PostgreSQL and
// MinIO — no API mocking. yt-dlp/FFmpeg are faked server-side by FakeProcessFactory (E2eAdaptersConfig),
// which writes a deterministic MP3 fixture and metadata instead of reaching YouTube, so the import
// pipeline exercises the real job orchestration, WebSocket/polling recovery, and object storage
// without any outbound network dependency on a real video.
//
// There is no seeded administrator in this stack, and the real promotion path is an interactive
// CLI attached to the backend container's stdin — unreachable from a browser spec. The e2e-only
// POST /api/e2e/admin-provisioning endpoint (E2E-03) reuses the same FirstAdminProvisioningService
// the CLI calls, so this test proves the real promotion business rule runs, just without a terminal.
//
// One continuous browser session carries the admin's access token across every step — import,
// review, publish, catalogue listing, and playlist assembly — the way a real admin session would.

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

test('completes the admin catalogue journey: import, publish, and build a playlist', async ({
  page,
  request,
  baseURL,
}) => {
  const stamp = Date.now();
  const username = `e2e-admin-${stamp}`;
  const email = `${username}@example.test`;
  const password = 'StrongPassword!1';
  const playlistName = `E2E playlist ${stamp}`;
  const fixtureTitle = 'Deterministic e2e track title';

  await test.step('registration and verification', async () => {
    await page.goto('/auth/register');
    await page.getByLabel('Username').fill(username);
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password', { exact: true }).fill(password);

    await expect(page.getByRole('button', { name: 'Create account' })).toBeEnabled({
      timeout: 15_000,
    });
    await page.getByRole('button', { name: 'Create account' }).click();
    await expect(page).toHaveURL(/\/auth\/verify-email\?.*mode=check-inbox/);

    const captured = await capturedEmail(request, baseURL!, email, 'VERIFICATION');
    await page.goto(`/verify-email?token=${encodeURIComponent(captured.token!)}`);
    await expect(page.getByRole('heading', { name: 'Email verified!' })).toBeVisible();
  });

  await test.step('promote to administrator', async () => {
    // No admin exists yet in this fresh stack, so the safe first-administrator rule promotes
    // this freshly verified account — the same outcome the operator CLI would report.
    const response = await request.post(`${baseURL}/api/e2e/admin-provisioning`, {
      params: { email },
    });
    expect(response.ok()).toBe(true);
    expect((await response.json()).outcome).toBe('PROMOTED');
  });

  await test.step('login as administrator', async () => {
    await page.goto('/auth/login');
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password', { exact: true }).fill(password);
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page).toHaveURL(/\/home$/);
    await page.getByRole('link', { name: 'Admin', exact: true }).click();
    await expect(page).toHaveURL(/\/admin\/tracks$/);
    await expect(page.getByRole('heading', { name: 'Track catalogue' })).toBeVisible();
  });

  await test.step('import a track from YouTube', async () => {
    await page.getByRole('link', { name: '＋ Import track' }).click();
    await expect(page).toHaveURL(/\/admin\/tracks\/import$/);

    await page.getByRole('button', { name: 'YouTube link' }).click();
    await page.getByLabel('YouTube URL *').fill('https://www.youtube.com/watch?v=dQw4w9WgXcQ');
    await page.getByRole('button', { name: '↓ Download music' }).click();

    // FakeProcessFactory completes the fake yt-dlp/FFmpeg pipeline almost instantly, but the
    // UI still learns about it through the real job polling/WebSocket path, so give it room.
    await expect(page.getByRole('heading', { name: 'Choose the excerpt start' })).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.getByLabel('Title *')).toHaveValue(fixtureTitle);
    await expect(page.getByLabel('Artist *')).toHaveValue('Deterministic e2e channel');
  });

  await test.step('validate the selection and publish', async () => {
    await page.getByRole('button', { name: '✓ Validate selection' }).click();
    await expect(page.getByRole('heading', { name: 'Preview the result' })).toBeVisible({
      timeout: 15_000,
    });

    const publishButton = page.getByRole('button', { name: '✓ Publish track' });
    await expect(publishButton).toBeEnabled({ timeout: 15_000 });
    await publishButton.click();

    await expect(page.getByText('Track published', { exact: true })).toBeVisible();
    await page.getByRole('link', { name: 'Open catalogue' }).click();
    await expect(page).toHaveURL(/\/admin\/tracks$/);
  });

  await test.step('the track appears published in the catalogue', async () => {
    await expect(page.getByText(fixtureTitle)).toBeVisible();
    await page.getByRole('button', { name: /^Published/ }).click();
    await expect(page.getByText(fixtureTitle)).toBeVisible();
  });

  await test.step('build a public playlist from the catalogue', async () => {
    await page.getByRole('link', { name: '☷ Playlists' }).click();
    await expect(page).toHaveURL(/\/admin\/playlists$/);

    await page.getByRole('button', { name: '＋ New playlist' }).first().click();
    await page.getByLabel('Playlist name').fill(playlistName);
    await page.getByRole('button', { name: 'Create playlist' }).click();
    await expect(page.getByRole('heading', { name: playlistName })).toBeVisible();

    await page.getByRole('button', { name: `Add ${fixtureTitle}` }).click();
    await expect(page.getByRole('article').filter({ hasText: fixtureTitle })).toBeVisible();
    await expect(page.getByText('1 tracks').first()).toBeVisible();
  });
});
