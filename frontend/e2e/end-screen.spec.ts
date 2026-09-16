import { expect, test } from './fixtures/test';
import { installSession, mockJson } from './support/auth';

const SESSION_ID = '55555555-5555-4555-8555-555555555555';

const summary = {
  sessionId: SESSION_ID,
  totalScore: 940,
  totalRounds: 2,
  titlesFound: 1,
  skippedRounds: 1,
  timedOutRounds: 0,
  startedAt: '2026-09-02T12:00:00.000Z',
  completedAt: '2026-09-02T12:02:30.000Z',
  rounds: [
    {
      roundIndex: 0,
      outcome: 'TITLE_FOUND',
      points: 120,
      title: 'Take On Me',
      artist: 'a-ha',
      composer: null,
      album: null,
      startedAt: '2026-09-02T12:00:00.000Z',
      revealedAt: '2026-09-02T12:00:04.500Z',
      completedAt: '2026-09-02T12:00:08.000Z',
    },
    {
      roundIndex: 1,
      outcome: 'SKIPPED',
      points: 0,
      title: 'Africa',
      artist: 'Toto',
      composer: null,
      album: null,
      startedAt: '2026-09-02T12:00:30.000Z',
      revealedAt: '2026-09-02T12:00:33.000Z',
      completedAt: '2026-09-02T12:00:36.000Z',
    },
  ],
};

test.describe('persisted end summary', () => {
  test.beforeEach(async ({ page }) => {
    await installSession(page);
  });

  test('loads and renders the durable summary from the server', async ({ page }) => {
    let resolveSummary = () => {};
    const pending = new Promise<void>((resolve) => (resolveSummary = resolve));
    await page.route(`**/api/sessions/${SESSION_ID}/summary`, async (route) => {
      await pending;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(summary),
      });
    });

    await page.goto(`/end-screen/${SESSION_ID}`);
    await expect(page.getByRole('heading', { name: 'Loading your summary…' })).toBeVisible();

    resolveSummary();

    await expect(page.getByRole('heading', { name: "That's a wrap." })).toBeVisible();
    await expect(page.getByText('2 tracks · 2m 30s')).toBeVisible();
    await expect(page.locator('.final-score')).toHaveText('940');
    await expect(page.getByText('1 found · 1 skipped · 0 timed out')).toBeVisible();

    await expect(page.locator('.rounds-list li')).toHaveCount(2);
    const firstRound = page.locator('.rounds-list li').nth(0);
    await expect(firstRound).toContainText('Take On Me');
    await expect(firstRound).toContainText('a-ha');
    await expect(firstRound).toContainText('Found');
    await expect(firstRound).toContainText('4.5s');
    await expect(firstRound).toContainText('+120');
    const secondRound = page.locator('.rounds-list li').nth(1);
    await expect(secondRound).toContainText('Africa');
    await expect(secondRound).toContainText('Skipped');

    await page.getByRole('button', { name: 'Play again' }).click();
    await expect(page).toHaveURL(/\/lobby\/create$/);
  });

  test('shows a not-found message when the summary is missing', async ({ page }) => {
    await mockJson(page, `/api/sessions/${SESSION_ID}/summary`, 404, { detail: 'Not found' });

    await page.goto(`/end-screen/${SESSION_ID}`);

    await expect(page.getByRole('heading', { name: 'Summary unavailable' })).toBeVisible();
    await expect(page.getByText("We couldn't find a summary for this game.")).toBeVisible();

    await page.getByRole('link', { name: 'Back to configuration' }).click();
    await expect(page).toHaveURL(/\/lobby\/create$/);
  });

  test('shows a generic error on an unexpected server failure', async ({ page }) => {
    await mockJson(page, `/api/sessions/${SESSION_ID}/summary`, 500, { detail: 'boom' });

    await page.goto(`/end-screen/${SESSION_ID}`);

    await expect(page.getByText('Something went wrong loading this summary.')).toBeVisible();
  });
});
