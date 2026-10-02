import { expect, test } from '@playwright/test';
import {
  FIXTURE_TITLE,
  promoteAndSignIn,
  publishPlaylist,
  registerFromInvitation,
} from './support/catalogue';

// MP-05 and MP-06 full-stack acceptance: two real browser contexts share one lobby over the real
// backend, REST, and STOMP topic. The host sees the friend join and get ready live, then both
// browsers play the same game to a ranked end screen. Every fake import has the same title, so
// both players can find every round.

const TRACK_COUNT = 5;

test('two players meet in a live lobby and play a game to the ranked end screen', async ({
  page,
  browser,
  request,
  baseURL,
}) => {
  test.setTimeout(300_000);
  const stamp = Date.now();
  const hostName = `e2e-mp-host-${stamp}`;
  const friendName = `e2e-mp-friend-${stamp}`;
  const playlistName = `Multiplayer playlist ${stamp}`;

  await test.step('the host builds a playlist', async () => {
    await registerFromInvitation(page, request, baseURL!, hostName);
    await promoteAndSignIn(page, request, baseURL!, hostName);
    await publishPlaylist(page, playlistName, TRACK_COUNT);
  });

  let code = '';
  await test.step('the host opens a lobby', async () => {
    await page.goto('/lobby/create');
    await page.getByRole('radio', { name: 'With friends' }).click();
    await page.getByRole('radio', { name: new RegExp(playlistName) }).click();
    // Ten seconds per round is the configuration minimum, which keeps this real-timer game fast.
    await page.getByRole('slider', { name: 'Maximum answer time' }).fill('10');
    await page.getByRole('button', { name: 'Open lobby →' }).click();
    await expect(page).toHaveURL(/\/lobby\/[A-Z2-9]{6}$/);
    code = (await page.getByLabel('Session code').textContent())!.trim();
    await expect(page.getByRole('button', { name: '▶ Start game' })).toBeDisabled();
  });

  const friendContext = await browser.newContext();
  const friend = await friendContext.newPage();
  try {
    await test.step('a friend joins by code and the host sees it live', async () => {
      await registerFromInvitation(friend, request, baseURL!, friendName);
      await friend.goto(`/lobby/join?code=${code}`);
      await friend.getByRole('button', { name: 'Join lobby →' }).click();
      await expect(friend).toHaveURL(new RegExp(`/lobby/${code}$`));

      // No reload: the host's list changes through the LOBBY_UPDATED event alone.
      await expect(page.getByText(friendName)).toBeVisible();
      await expect(page.getByText('1/2 ready')).toBeVisible();
    });

    await test.step('the friend gets ready and the host starts', async () => {
      await friend.getByRole('button', { name: "I'm ready" }).click();
      await expect(page.getByText('2/2 ready')).toBeVisible();
      const start = page.getByRole('button', { name: '▶ Start game' });
      await expect(start).toBeEnabled();
      await start.click();
    });

    await test.step('both browsers enter the same game', async () => {
      await expect(page).toHaveURL(/\/game\/[0-9a-f-]{36}$/);
      await expect(friend).toHaveURL(new RegExp(`${new URL(page.url()).pathname}$`), {
        timeout: 15_000,
      });
      await expect(page.getByLabel('Your answer')).toBeVisible({ timeout: 20_000 });
      await expect(friend.getByLabel('Your answer')).toBeVisible({ timeout: 20_000 });
      await expect(page.getByRole('heading', { name: 'Scoreboard' })).toBeVisible();
    });

    await test.step('both players find every title and the host advances', async () => {
      for (let round = 1; round <= TRACK_COUNT; round++) {
        for (const player of [page, friend]) {
          await expect(player.getByLabel('Your answer')).toBeEnabled({ timeout: 20_000 });
          // Stay clear of the per-player answer cooldown between back-to-back rounds.
          await player.waitForTimeout(400);
          await player.getByLabel('Your answer').fill(FIXTURE_TITLE);
          await player.getByRole('button', { name: 'Submit' }).click();
          // The last finder's message gives way at once to the reveal, so accept either.
          await expect(
            player.getByText(/You found the title! \+\d+ points|Everyone found the title/).first(),
          ).toBeVisible({ timeout: 15_000 });
        }
        for (const player of [page, friend]) {
          await expect(player.getByText('Everyone found the title', { exact: true })).toBeVisible();
        }
        await expect(friend.getByText('The host can skip ahead')).toBeVisible();
        // The four-second automatic advance may win the race with the host's click.
        await page
          .getByRole('button', { name: round === TRACK_COUNT ? 'Finish now' : 'Next now' })
          .click({ timeout: 3_000 })
          .catch(() => undefined);
      }
    });

    await test.step('both players see the ranked standings', async () => {
      for (const player of [page, friend]) {
        await expect(player.getByRole('heading', { name: "That's a wrap." })).toBeVisible({
          timeout: 15_000,
        });
        const standings = player.getByRole('table', { name: 'Final standings' });
        await expect(standings).toContainText(hostName);
        await expect(standings).toContainText(friendName);
        await expect(standings).toContainText(`${TRACK_COUNT}/${TRACK_COUNT}`);
      }
      // The host answered first every round, so they rank first.
      await expect(
        page.getByRole('table', { name: 'Final standings' }).locator('tbody tr').first(),
      ).toContainText(hostName);

      await friend.getByRole('link', { name: 'View summary' }).click();
      await expect(friend.getByRole('region', { name: 'Final standings' })).toContainText(hostName);
      await expect(friend.getByText('Your score')).toBeVisible();
    });
  } finally {
    await friendContext.close();
  }
});
