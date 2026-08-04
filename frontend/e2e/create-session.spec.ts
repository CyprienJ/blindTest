import { expect, Page, test } from '@playwright/test';

const player = {
  id: '11111111-1111-4111-8111-111111111111',
  username: 'music-player',
  email: 'player@example.com',
  role: 'USER',
  avatarUrl: null,
};

const playlists = [
  {
    id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    name: 'Empty playlist',
    description: null,
    imageUrl: null,
    trackCount: 0,
  },
  {
    id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    name: 'Eighties essentials',
    description: 'Synth-pop classics',
    imageUrl: null,
    trackCount: 2,
  },
  {
    id: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    name: 'Rock anthems',
    description: 'Guitars and big choruses',
    imageUrl: null,
    trackCount: 1,
  },
];

const details = {
  [playlists[1].id]: {
    ...playlists[1],
    tracks: [
      track('dddddddd-dddd-4ddd-8ddd-dddddddddddd', 'Take On Me', 'a-ha', 225),
      track('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee', 'Africa', 'Toto', 295),
    ],
  },
  [playlists[2].id]: {
    ...playlists[2],
    tracks: [track('ffffffff-ffff-4fff-8fff-ffffffffffff', 'The Final Countdown', 'Europe', 310)],
  },
};

function track(id: string, title: string, artist: string, durationSeconds: number) {
  return {
    id,
    title,
    artist,
    album: null,
    composer: null,
    work: null,
    category: 'Pop',
    decade: 1980,
    durationSeconds,
    excerptStartSeconds: 0,
    imageUrl: null,
  };
}

async function installPlayerSession(page: Page): Promise<void> {
  await page.addInitScript((user) => {
    localStorage.setItem('access_token', 'player-access-token');
    localStorage.setItem('current_user', JSON.stringify(user));
  }, player);
}

test.describe('game configuration', () => {
  test('lets an authenticated player configure an eligible public playlist', async ({ page }) => {
    await installPlayerSession(page);
    await page.route('**/api/playlists', (route) => route.fulfill({ json: playlists }));
    await page.route('**/api/playlists/*', (route) => {
      const id = route.request().url().split('/').at(-1) ?? '';
      return route.fulfill({ json: details[id as keyof typeof details] });
    });

    await page.goto('/lobby/create');

    await expect(page.getByRole('heading', { name: 'Configure a new game' })).toBeVisible();
    await expect(page.getByRole('radio', { name: /Empty playlist/ })).toHaveCount(0);
    await expect(page.getByRole('radio', { name: /Eighties essentials/ })).toHaveAttribute(
      'aria-checked',
      'true',
    );
    await expect(page.locator('.track-preview > div').first()).toContainText('Take On Me');

    await page.getByRole('radio', { name: /Rock anthems/ }).click();
    await expect(
      page.getByRole('complementary').getByRole('heading', { name: 'Rock anthems' }),
    ).toBeVisible();
    await page.getByRole('slider', { name: 'Maximum answer time' }).fill('45');
    await page.getByRole('button', { name: 'Create session' }).click();

    await expect(page.locator('.notice.success')).toContainText('Configuration saved');
    const draft = await page.evaluate(() => sessionStorage.getItem('pending_game_configuration'));
    expect(JSON.parse(draft ?? '{}')).toEqual({
      playlistId: playlists[2].id,
      trackCount: 1,
      maxRoundSeconds: 45,
    });
  });

  test('requires authentication before opening game configuration', async ({ page }) => {
    await page.route('**/api/auth/refresh', (route) =>
      route.fulfill({ status: 401, json: { code: 'UNAUTHORIZED', message: 'Authentication required' } }),
    );

    await page.goto('/lobby/create');

    await expect(page).toHaveURL(/\/auth\/login\?returnUrl=%2Flobby%2Fcreate$/);
  });
});
