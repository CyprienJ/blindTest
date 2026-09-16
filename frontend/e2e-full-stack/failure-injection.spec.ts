import {
  APIRequestContext,
  BrowserContext,
  Page,
  WebSocketRoute,
  expect,
  test,
} from '@playwright/test';

// Failure-injection coverage for Lot 7 (E2E-06) against the real isolated Compose stack from
// docker-compose.test.yml: a real Angular build, a real backend, real PostgreSQL and MinIO, and
// the real WebSocket gameplay protocol. Two of the seven documented scenarios are covered here at
// the browser level, where they are observable to a real player: a browser network disconnect and
// reconnect mid-game, and a backend restart's effect on an active in-memory game. A third —
// import-process failure — is covered here as the one browser-observable angle of a path already
// exhaustively unit-tested in ImportJobRunnerTest. The remaining scenarios (MinIO unavailability,
// PostgreSQL transient failure, expired pre-signed URLs, and the timer/answer race) are backend
// concerns without a meaningful browser-level angle and are covered by backend integration tests
// instead — see docs/operations/failure-injection.md for the complete picture and results.
//
// The backend has no real restart lever reachable from this isolated stack's browser-driven test
// runner (it would need docker socket access from inside the "tests" container, which this stack
// deliberately doesn't grant). The e2e-only DELETE /api/e2e/active-sessions backdoor reproduces
// the one externally observable effect a real restart has on gameplay: every active in-memory
// session is gone, the same as InMemorySessionStore starting empty after a process restart.
//
// The network-disconnect scenario uses context.routeWebSocket to sever the real WebSocket
// connection rather than context.setOffline: Chromium's offline emulation does not interrupt a
// WebSocket connection already established (confirmed empirically — the STOMP heartbeat kept
// working through 40s of "offline" with no detected drop), so it cannot exercise this path at
// all. routeWebSocket is not a mock here — every message still flows to and from the real
// backend via connectToServer()'s full pass-through; the only deliberate action is closing the
// real socket at a chosen moment, which is the one thing needed to make a network blip
// deterministic instead of dependent on Chromium/heartbeat timing.

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

async function registerAndVerify(
  page: Page,
  request: APIRequestContext,
  baseURL: string,
  username: string,
  email: string,
  password: string,
): Promise<void> {
  await page.goto('/auth/register');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);

  await expect(page.getByRole('button', { name: 'Create account' })).toBeEnabled({
    timeout: 15_000,
  });
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page).toHaveURL(/\/auth\/verify-email\?.*mode=check-inbox/);

  const captured = await capturedEmail(request, baseURL, email, 'VERIFICATION');
  await page.goto(`/verify-email?token=${encodeURIComponent(captured.token!)}`);
  await expect(page.getByRole('heading', { name: 'Email verified!' })).toBeVisible();
}

async function login(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/auth/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/home$/);
}

const FIXTURE_TITLE = 'Deterministic e2e track title';
const TRACK_COUNT = 5;

/** Imports and publishes {@link TRACK_COUNT} fixture tracks, then builds a public playlist. */
async function buildFixturePlaylist(
  page: Page,
  request: APIRequestContext,
  baseURL: string,
  stamp: number,
): Promise<string> {
  const adminUsername = `e2e-fail-admin-${stamp}`;
  const adminEmail = `${adminUsername}@example.test`;
  const password = 'StrongPassword!1';
  const playlistName = `Failure-injection playlist ${stamp}`;

  expect((await request.delete(`${baseURL}/api/e2e/admin-provisioning`)).ok()).toBe(true);
  await registerAndVerify(page, request, baseURL, adminUsername, adminEmail, password);
  const promoted = await request.post(`${baseURL}/api/e2e/admin-provisioning`, {
    params: { email: adminEmail },
  });
  expect(promoted.ok()).toBe(true);
  await login(page, adminEmail, password);

  await page.getByRole('link', { name: 'Admin', exact: true }).click();
  await expect(page).toHaveURL(/\/admin\/tracks$/);
  await page.getByRole('link', { name: '＋ Import from YouTube' }).click();
  await expect(page).toHaveURL(/\/admin\/tracks\/import$/);

  for (let index = 1; index <= TRACK_COUNT; index++) {
    await page.getByLabel('YouTube URL *').fill(`https://www.youtube.com/watch?v=e2e-track-${index}`);
    await page.getByRole('button', { name: '↓ Download music' }).click();

    await expect(page.getByRole('heading', { name: 'Choose the excerpt start' })).toBeVisible({
      timeout: 30_000,
    });
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

  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/$/);
  return playlistName;
}

/**
 * Tracks the page-side route for the game's current real WebSocket connection: full pass-through
 * to the real backend via connectToServer() until the test deliberately closes it.
 */
function trackGameSocket(context: BrowserContext): { current: WebSocketRoute | null } {
  const tracked: { current: WebSocketRoute | null } = { current: null };
  void context.routeWebSocket(/\/ws$/, (ws) => {
    ws.connectToServer();
    tracked.current = ws;
  });
  return tracked;
}

test('a mid-game network blip recovers and a backend restart is surfaced cleanly', async ({
  page,
  context,
  request,
  baseURL,
}) => {
  // Two failure-injection scenarios chained onto one game: a full playlist build plus two
  // network-level interventions take longer than the golden-path journeys this reuses steps from.
  test.setTimeout(120_000);

  const gameSocket = trackGameSocket(context);

  const stamp = Date.now();
  const playlistName = await buildFixturePlaylist(page, request, baseURL!, stamp);

  const playerUsername = `e2e-fail-player-${stamp}`;
  const playerEmail = `${playerUsername}@example.test`;
  const password = 'StrongPassword!1';

  await registerAndVerify(page, request, baseURL!, playerUsername, playerEmail, password);
  await login(page, playerEmail, password);

  let sessionId = '';
  await test.step('configure a solo session from the published playlist', async () => {
    await page.goto('/lobby/create');
    await page.getByRole('radio', { name: new RegExp(playlistName) }).click();
    await page.getByRole('slider', { name: 'Maximum answer time' }).fill('10');

    const [response] = await Promise.all([
      page.waitForResponse('**/api/sessions'),
      page.getByRole('button', { name: 'Create session →' }).click(),
    ]);
    sessionId = ((await response.json()) as { sessionId: string }).sessionId;
    await expect(page).toHaveURL(new RegExp(`/game/${sessionId}$`));
  });

  await test.step('round 1 plays normally, establishing the baseline', async () => {
    await expect(page.getByLabel('Your answer')).toBeEnabled({ timeout: 20_000 });
    await page.waitForTimeout(400);
    await page.getByLabel('Your answer').fill(FIXTURE_TITLE);
    await page.getByRole('button', { name: 'Submit' }).click();
    await expect(page.getByText('You found the title', { exact: true })).toBeVisible({
      timeout: 15_000,
    });
    await page.getByRole('button', { name: 'Next now' }).click();
  });

  await test.step('round 2: a network disconnect mid-round recovers automatically', async () => {
    await expect(page.getByLabel('Your answer')).toBeEnabled({ timeout: 20_000 });

    // Severs the real WebSocket connection to simulate the network dropping mid-game. RxStomp
    // sees the native close event immediately (no heartbeat-timeout wait needed) and starts
    // reconnecting on its own.
    await gameSocket.current?.close({ code: 1006, reason: 'e2e-simulated-network-blip' });
    await expect(page.getByText('Connection lost — reconnecting…')).toBeVisible({
      timeout: 10_000,
    });

    // RxStomp reconnects (a fresh WebSocket, again routed and connected to the real backend
    // above) and the client re-requests a snapshot; the round remains fully playable afterward
    // with no manual recovery action from the player.
    await expect(page.getByText('Connection lost — reconnecting…')).toBeHidden({
      timeout: 30_000,
    });
    await expect(page.getByLabel('Your answer')).toBeEnabled({ timeout: 15_000 });

    await page.waitForTimeout(400);
    await page.getByLabel('Your answer').fill(FIXTURE_TITLE);
    await page.getByRole('button', { name: 'Submit' }).click();
    await expect(page.getByText('You found the title', { exact: true })).toBeVisible({
      timeout: 15_000,
    });
    await page.getByRole('button', { name: 'Next now' }).click();
  });

  await test.step('round 3: a simulated backend restart surfaces a clean, terminal error', async () => {
    await expect(page.getByLabel('Your answer')).toBeEnabled({ timeout: 20_000 });

    // Discards every active in-memory session, reproducing exactly what InMemorySessionStore
    // loses on a real process restart (E2eTestSupportController#discardActiveSessions).
    const discarded = await request.delete(`${baseURL}/api/e2e/active-sessions`);
    expect(discarded.status()).toBe(204);

    // The client doesn't yet know the session is gone — it only learns on its next command.
    await page.getByRole('button', { name: 'Skip track' }).click();

    await expect(page.getByRole('heading', { name: 'Something went wrong' })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByText(/session is no longer available|unavailable/i)).toBeVisible();
  });

  await test.step('no partial or duplicate history was written for the interrupted game', async () => {
    // The app keeps its access token in localStorage, not a cookie, so the bare `request`
    // fixture needs it passed explicitly to authenticate as the player who owns this session.
    const accessToken = await page.evaluate(() => localStorage.getItem('access_token'));
    const summary = await request.get(`${baseURL}/api/sessions/${sessionId}/summary`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(summary.status()).toBe(404);
  });
});

test('an import process failure surfaces a clean error and the admin can recover', async ({
  page,
  request,
  baseURL,
}) => {
  const stamp = Date.now();
  const username = `e2e-fail-import-${stamp}`;
  const email = `${username}@example.test`;
  const password = 'StrongPassword!1';

  expect((await request.delete(`${baseURL}/api/e2e/admin-provisioning`)).ok()).toBe(true);
  await registerAndVerify(page, request, baseURL!, username, email, password);
  const promoted = await request.post(`${baseURL}/api/e2e/admin-provisioning`, {
    params: { email },
  });
  expect(promoted.ok()).toBe(true);
  await login(page, email, password);

  await page.getByRole('link', { name: 'Admin', exact: true }).click();
  await page.getByRole('link', { name: '＋ Import from YouTube' }).click();
  await expect(page).toHaveURL(/\/admin\/tracks\/import$/);

  await test.step('a provider failure fails the job cleanly instead of publishing a track', async () => {
    // FakeProcessFactory.FORCED_FAILURE_VIDEO_ID deterministically fails yt-dlp with a nonzero
    // exit code, the same path ImportJobRunnerTest already covers at the unit level for every
    // failure reason — this is that path's one browser-observable angle.
    await page.getByLabel('YouTube URL *').fill('https://www.youtube.com/watch?v=e2e-fail-01');
    await page.getByRole('button', { name: '↓ Download music' }).click();

    // Scoped to the failure alert: the job-history badge also reads "Import failed", and the
    // pipeline activity log also echoes the error message with a timestamp prefix.
    const failureAlert = page.getByRole('alert');
    await expect(failureAlert.getByText('Import failed', { exact: true })).toBeVisible({
      timeout: 30_000,
    });
    await expect(
      failureAlert.getByText('The media provider could not complete the import'),
    ).toBeVisible();
  });

  await test.step('the admin can start a fresh import in the same session afterward', async () => {
    await page.getByRole('button', { name: 'Start another import' }).click();
    await page.getByLabel('YouTube URL *').fill('https://www.youtube.com/watch?v=e2e-track-1');
    await page.getByRole('button', { name: '↓ Download music' }).click();

    await expect(page.getByRole('heading', { name: 'Choose the excerpt start' })).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.getByLabel('Title *')).toHaveValue(FIXTURE_TITLE);
  });
});
