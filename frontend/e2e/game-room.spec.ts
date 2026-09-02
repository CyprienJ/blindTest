import { expect, test } from './fixtures/test';
import { installSession } from './support/auth';

const SESSION_ID = '55555555-5555-4555-8555-555555555555';

const activeSession = {
  sessionId: SESSION_ID,
  participantId: '66666666-6666-4666-8666-666666666666',
  state: 'CREATED',
  version: 0,
  expiresAt: '2026-09-02T23:00:00Z',
  connection: {
    protocolVersion: 1,
    webSocketEndpoint: '/ws',
    sockJsEndpoint: '/ws-sockjs',
    commandDestination: `/app/sessions/${SESSION_ID}`,
    eventDestination: `/topic/sessions/${SESSION_ID}`,
    privateDestination: `/user/queue/sessions/${SESSION_ID}`,
  },
  playlistName: 'Eighties essentials',
  trackCount: 12,
  maxRoundSeconds: 30,
};

test.describe('solo game room', () => {
  test.beforeEach(async ({ page }) => {
    await installSession(page);
    await page.addInitScript((session) => {
      sessionStorage.setItem('active_solo_session', JSON.stringify(session));
    }, activeSession);
    // The gameplay socket has no REST fallback; block it so the test is deterministic.
    await page.route('**/ws/**', (route) => route.abort());
    await page.route('**/ws', (route) => route.abort());
  });

  test('shows the connecting state for a stored session', async ({ page }) => {
    await page.goto(`/game/${SESSION_ID}`);

    await expect(page.getByRole('heading', { name: 'Setting up your game…' })).toBeVisible();
    await expect(page.getByText('Eighties essentials')).toBeVisible();
    await expect(page.getByText('12 tracks · 30s per track')).toBeVisible();
    await expect(page.getByText('Connecting you to the live game…')).toBeVisible();
  });

  test('returns to configuration and redirects unknown sessions', async ({ page }) => {
    await page.goto(`/game/${SESSION_ID}`);
    await page.getByRole('link', { name: 'Back to configuration' }).click();
    await expect(page).toHaveURL(/\/lobby\/create$/);

    await page.goto('/game/00000000-0000-4000-8000-000000000000');
    await expect(page).toHaveURL(/\/lobby\/create$/);
  });
});
