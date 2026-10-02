import { APIRequestContext, expect, test } from '@playwright/test';

// Drives the real solo blindtest lifecycle against the isolated Compose stack from
// docker-compose.test.yml (E2E-01): a real Angular build, a real backend, real PostgreSQL and
// MinIO, and the real WebSocket gameplay protocol — no API or socket mocking. yt-dlp/FFmpeg are
// faked server-side by FakeProcessFactory (E2eAdaptersConfig), which always produces the same
// fixture title/artist regardless of the source URL; this spec relies on that determinism to
// know the correct answer for every round without knowing which of the five imported tracks the
// backend shuffles into it.
//
// Full-stack spec files share one Postgres instance for the whole suite run, and the
// first-administrator business rule only allows one bootstrap promotion per stack (E2E-03).
// Since this file can run after another spec has already promoted an admin, it resets that
// state through the e2e-only DELETE /api/e2e/admin-provisioning backdoor before promoting its
// own account, so the journey works regardless of file execution order.
//
// One browser session builds the catalogue as an administrator; a second, independent session
// then plays the game as a regular player — the way a real player would use music an admin
// already published, not an account that imported it themselves.

async function issueInvitation(
  request: APIRequestContext,
  baseURL: string,
  email: string,
): Promise<string> {
  // Registration is invitation-only; the e2e backdoor issues one without an admin session.
  const response = await request.post(`${baseURL}/api/e2e/invitations`, { params: { email } });
  expect(response.ok()).toBe(true);
  return ((await response.json()) as { token: string }).token;
}

const FIXTURE_TITLE = 'Deterministic e2e track title';
const TRACK_COUNT = 5;

test('completes the solo game journey: build a playlist and play a full game', async ({
  page,
  request,
  baseURL,
}) => {
  // Five imports, five game rounds, and two independent account journeys take meaningfully
  // longer than the single-track admin catalogue journey this reuses steps from.
  test.setTimeout(120_000);

  const stamp = Date.now();
  const adminUsername = `e2e-solo-admin-${stamp}`;
  const adminEmail = `${adminUsername}@example.test`;
  const playerUsername = `e2e-solo-player-${stamp}`;
  const playerEmail = `${playerUsername}@example.test`;
  const password = 'StrongPassword!1';
  const playlistName = `Solo playlist ${stamp}`;

  await test.step('reset any administrator left by another spec file', async () => {
    const response = await request.delete(`${baseURL}/api/e2e/admin-provisioning`);
    expect(response.ok()).toBe(true);
  });

  await test.step('register the administrator account from an invitation', async () => {
    const inviteToken = await issueInvitation(request, baseURL!, adminEmail);
    await page.goto(`/auth/register?invite=${encodeURIComponent(inviteToken)}`);
    await expect(page.getByLabel('Email')).toHaveValue(adminEmail);
    await page.getByLabel('Username').fill(adminUsername);
    await page.getByLabel('Password', { exact: true }).fill(password);

    await expect(page.getByRole('button', { name: 'Create account' })).toBeEnabled({
      timeout: 15_000,
    });
    await page.getByRole('button', { name: 'Create account' }).click();
    // The invitation proved the address, so the account is verified and signed in at once.
    await expect(page).toHaveURL(/\/home$/);
  });

  await test.step('promote to administrator', async () => {
    const response = await request.post(`${baseURL}/api/e2e/admin-provisioning`, {
      params: { email: adminEmail },
    });
    expect(response.ok()).toBe(true);
    expect((await response.json()).outcome).toBe('PROMOTED');
  });

  await test.step('log in as administrator', async () => {
    await page.goto('/auth/login');
    await page.getByLabel('Email').fill(adminEmail);
    await page.getByLabel('Password', { exact: true }).fill(password);
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page).toHaveURL(/\/home$/);
    await page.getByRole('link', { name: 'Admin', exact: true }).click();
    await expect(page).toHaveURL(/\/admin\/tracks$/);
    await page.getByRole('link', { name: '＋ Import track' }).click();
    await expect(page).toHaveURL(/\/admin\/tracks\/import$/);
  });

  await test.step('import and publish enough tracks for a solo game', async () => {
    for (let index = 1; index <= TRACK_COUNT; index++) {
      await page.getByRole('button', { name: 'YouTube link' }).click();
      await page
        .getByLabel('YouTube URL *')
        .fill(`https://www.youtube.com/watch?v=e2e-track-${index}`);
      await page.getByRole('button', { name: '↓ Download music' }).click();

      await expect(page.getByRole('heading', { name: 'Choose the excerpt start' })).toBeVisible({
        timeout: 30_000,
      });
      await expect(page.getByLabel('Title *')).toHaveValue(FIXTURE_TITLE);

      await page.getByRole('button', { name: '✓ Validate selection' }).click();
      await expect(page.getByRole('heading', { name: 'Preview the result' })).toBeVisible({
        timeout: 15_000,
      });

      const publishButton = page.getByRole('button', { name: '✓ Publish track' });
      await expect(publishButton).toBeEnabled({ timeout: 15_000 });
      await publishButton.click();
      await expect(page.getByText('Track published', { exact: true })).toBeVisible();

      if (index < TRACK_COUNT) {
        await page.getByRole('button', { name: 'New import' }).click();
      }
    }
  });

  await test.step('build a public playlist from the five published tracks', async () => {
    await page.goto('/admin/playlists');
    await page.getByRole('button', { name: '＋ New playlist' }).first().click();
    await page.getByLabel('Playlist name').fill(playlistName);
    await page.getByRole('button', { name: 'Create playlist' }).click();
    await expect(page.getByRole('heading', { name: playlistName })).toBeVisible();

    const addButton = page.getByRole('button', { name: `Add ${FIXTURE_TITLE}` }).first();
    for (let index = 1; index <= TRACK_COUNT; index++) {
      await addButton.click();
      await expect(page.locator('.track-row')).toHaveCount(index);
    }
    await expect(page.getByText(`${TRACK_COUNT} tracks`).first()).toBeVisible();
  });

  await test.step('sign out the administrator', async () => {
    await page.getByRole('button', { name: 'Sign out' }).click();
    await expect(page).toHaveURL(/\/$/);
  });

  await test.step('register a regular player account from an invitation', async () => {
    const inviteToken = await issueInvitation(request, baseURL!, playerEmail);
    await page.goto(`/auth/register?invite=${encodeURIComponent(inviteToken)}`);
    await expect(page.getByLabel('Email')).toHaveValue(playerEmail);
    await page.getByLabel('Username').fill(playerUsername);
    await page.getByLabel('Password', { exact: true }).fill(password);

    await expect(page.getByRole('button', { name: 'Create account' })).toBeEnabled({
      timeout: 15_000,
    });
    await page.getByRole('button', { name: 'Create account' }).click();
    // The invitation proved the address, so the account is verified and signed in at once.
    await expect(page).toHaveURL(/\/home$/);
  });

  let sessionId = '';
  await test.step('configure a solo session from the published playlist', async () => {
    await page.goto('/lobby/create');
    await expect(page.getByRole('heading', { name: 'Configure a new game' })).toBeVisible();

    await page.getByRole('radio', { name: new RegExp(playlistName) }).click();
    await expect(page.getByRole('slider', { name: 'Number of tracks' })).toHaveValue(
      String(TRACK_COUNT),
    );
    // Ten seconds per round is the configuration minimum, which keeps this real-timer game fast.
    await page.getByRole('slider', { name: 'Maximum answer time' }).fill('10');

    const [response] = await Promise.all([
      page.waitForResponse('**/api/sessions'),
      page.getByRole('button', { name: 'Create session →' }).click(),
    ]);
    sessionId = ((await response.json()) as { sessionId: string }).sessionId;

    await expect(page).toHaveURL(new RegExp(`/game/${sessionId}$`));
    await expect(page.getByRole('heading', { name: 'Setting up your game…' })).toBeVisible();
  });

  await test.step('answer every round correctly and finish the game', async () => {
    for (let round = 1; round <= TRACK_COUNT; round++) {
      await expect(page.getByLabel('Your answer')).toBeEnabled({ timeout: 20_000 });
      // The backend enforces a short per-player answer cooldown after each accepted guess
      // (GameplayCommandRateLimiter.PLAYER_ANSWER_COOLDOWN). Correct answers resolve instantly
      // and rounds advance immediately, so back-to-back rounds can otherwise submit faster than
      // a human ever would and land inside that cooldown.
      await page.waitForTimeout(400);
      await page.getByLabel('Your answer').fill(FIXTURE_TITLE);
      await page.getByRole('button', { name: 'Submit' }).click();

      await expect(page.getByText('You found the title', { exact: true })).toBeVisible({
        timeout: 15_000,
      });

      const isLastRound = round === TRACK_COUNT;
      const advanceButton = page.getByRole('button', {
        name: isLastRound ? 'Finish now' : 'Next now',
      });
      await expect(advanceButton).toBeEnabled();
      await advanceButton.click();
    }

    await expect(page.getByRole('heading', { name: "That's a wrap." })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByText(`${TRACK_COUNT} found · 0 skipped · 0 timed out`)).toBeVisible();
  });

  await test.step('review the persisted summary', async () => {
    await page.getByRole('link', { name: 'View summary' }).click();

    await expect(page).toHaveURL(new RegExp(`/end-screen/${sessionId}$`));
    await expect(page.getByRole('heading', { name: "That's a wrap." })).toBeVisible();
    await expect(page.locator('.rounds-list li')).toHaveCount(TRACK_COUNT);
    for (const row of await page.locator('.rounds-list li').all()) {
      await expect(row).toContainText(FIXTURE_TITLE);
      await expect(row).toContainText('Found');
    }
  });
});
