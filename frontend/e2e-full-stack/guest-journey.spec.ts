import { expect, test } from '@playwright/test';
import {
  FIXTURE_TITLE,
  promoteAndSignIn,
  publishPlaylist,
  registerFromInvitation,
} from './support/catalogue';

// MP-09 full-stack acceptance: a registered host and a guest without an account play one game
// over the real backend, REST, STOMP, and Turnstile test keys. The host keeps the music on its
// own device (ADR-0022), the guest's reload rejoins the lobby, and a new tab cannot reuse the
// guest's seat.

const TRACK_COUNT = 5;

test('a registered host and a guest play a game to the guest end screen', async ({
  page,
  browser,
  request,
  baseURL,
}) => {
  test.setTimeout(300_000);
  const stamp = Date.now();
  const hostName = `e2e-guest-host-${stamp}`;
  const guestName = `Guest ${String(stamp).slice(-6)}`;
  const playlistName = `Guest playlist ${stamp}`;

  await test.step('the host builds a playlist', async () => {
    await registerFromInvitation(page, request, baseURL!, hostName);
    await promoteAndSignIn(page, request, baseURL!, hostName);
    await publishPlaylist(page, playlistName, TRACK_COUNT);
  });

  let code = '';
  await test.step('the host opens a lobby that plays the music on its device only', async () => {
    await page.goto('/lobby/create');
    await page.getByRole('radio', { name: 'With friends' }).click();
    await page.getByRole('radio', { name: new RegExp(playlistName) }).click();
    await page.getByRole('slider', { name: 'Maximum answer time' }).fill('10');
    await page.getByRole('radio', { name: /Only my device/ }).check();
    await page.getByRole('button', { name: 'Open lobby →' }).click();
    await expect(page).toHaveURL(/\/lobby\/[A-Z2-9]{6}$/);
    code = (await page.getByLabel('Session code').textContent())!.trim();
    await expect(page.getByRole('radio', { name: 'Only my device plays it' })).toBeChecked();
  });

  const guestContext = await browser.newContext();
  await guestContext.addInitScript(() => localStorage.setItem('locale', 'en'));
  const guest = await guestContext.newPage();
  try {
    await test.step('a visitor joins as a guest from the invite link', async () => {
      await guest.goto(`/lobby/join?code=${code}`);
      await expect(
        guest.getByText("You're joining as a guest — no account required."),
      ).toBeVisible();
      await guest.getByLabel('Your nickname').fill(guestName);
      const join = guest.getByRole('button', { name: 'Join lobby →' });
      await expect(join).toBeEnabled({ timeout: 15_000 });
      await join.click();
      await expect(guest).toHaveURL(new RegExp(`/lobby/${code}$`));
      await expect(page.getByText(guestName)).toBeVisible();
      await expect(page.getByText('Guest · Not ready')).toBeVisible();
    });

    await test.step('a reload rejoins, and a new tab cannot take the seat', async () => {
      await guest.reload();
      await expect(guest.getByLabel('Session code')).toHaveText(code);
      const stranger = await guestContext.newPage();
      await stranger.goto(`/lobby/${code}`);
      await expect(stranger).toHaveURL(/\/auth\/login/);
      await stranger.close();
    });

    await test.step('the guest gets ready and the host starts', async () => {
      await guest.getByRole('button', { name: "I'm ready" }).click();
      await expect(page.getByText('2/2 ready')).toBeVisible();
      await page.getByRole('button', { name: '▶ Start game' }).click();
      await expect(page).toHaveURL(/\/game\/[0-9a-f-]{36}$/);
      await expect(guest).toHaveURL(new RegExp(`${new URL(page.url()).pathname}$`), {
        timeout: 15_000,
      });
    });

    await test.step('both find every title; only the host plays the music', async () => {
      for (let round = 1; round <= TRACK_COUNT; round++) {
        for (const player of [page, guest]) {
          await expect(player.getByLabel('Your answer')).toBeEnabled({ timeout: 20_000 });
          if (player === guest && round === 1) {
            await expect(guest.getByText("The music plays on the host's device.")).toBeVisible();
            await expect(page.getByText("The music plays on the host's device.")).toHaveCount(0);
          }
          await player.waitForTimeout(400);
          await player.getByLabel('Your answer').fill(FIXTURE_TITLE);
          await player.getByRole('button', { name: 'Submit' }).click();
          await expect(
            player.getByText(/You found the title! \+\d+ points|Everyone found the title/).first(),
          ).toBeVisible({ timeout: 15_000 });
        }
        await page
          .getByRole('button', { name: round === TRACK_COUNT ? 'Finish now' : 'Next now' })
          .click({ timeout: 3_000 })
          .catch(() => undefined);
      }
    });

    await test.step('the guest reads its summary with a guest notice', async () => {
      await expect(guest.getByRole('heading', { name: "That's a wrap." })).toBeVisible({
        timeout: 15_000,
      });
      await expect(guest.getByRole('table', { name: 'Final standings' })).toContainText(hostName);
      await guest.getByRole('link', { name: 'View summary' }).click();
      await expect(guest.getByRole('region', { name: 'Final standings' })).toContainText(guestName);
      await expect(guest.getByText('You played as a guest')).toBeVisible();
      await expect(guest.getByRole('button', { name: 'Join another game' })).toBeVisible();
    });
  } finally {
    await guestContext.close();
  }
});
