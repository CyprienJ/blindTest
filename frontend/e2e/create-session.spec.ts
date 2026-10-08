import { expect, Page, test } from '@playwright/test';

const player = {
  id: '11111111-1111-4111-8111-111111111111',
  username: 'music-player',
  email: 'player@example.com',
  role: 'USER',
  avatarUrl: null,
};

const SESSION_ID = '55555555-5555-4555-8555-555555555555';

const soloSessionResponse = {
  sessionId: SESSION_ID,
  participantId: '66666666-6666-4666-8666-666666666666',
  state: 'CREATED',
  version: 0,
  expiresAt: '2026-09-02T12:30:00Z',
  connection: {
    protocolVersion: 1,
    webSocketEndpoint: '/ws',
    sockJsEndpoint: '/ws-sockjs',
    commandDestination: `/app/sessions/${SESSION_ID}`,
    eventDestination: `/topic/sessions/${SESSION_ID}`,
    privateDestination: `/user/queue/sessions/${SESSION_ID}`,
  },
};

const playlists = [
  {
    id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    name: 'Too small playlist',
    description: null,
    imageUrl: null,
    trackCount: 4,
  },
  {
    id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    name: 'Eighties essentials',
    description: 'Synth-pop classics',
    imageUrl: null,
    trackCount: 8,
  },
  {
    id: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    name: 'Rock anthems',
    description: 'Guitars and big choruses',
    imageUrl: null,
    trackCount: 6,
  },
  {
    id: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
    name: 'Shrinking playlist',
    description: 'Fewer tracks than it claims',
    imageUrl: null,
    trackCount: 6,
  },
];

const details = {
  [playlists[1].id]: {
    ...playlists[1],
    tracks: [
      track('11111111-dddd-4ddd-8ddd-dddddddddddd', 'Take On Me', 'a-ha', 225),
      track('22222222-eeee-4eee-8eee-eeeeeeeeeeee', 'Africa', 'Toto', 295),
      ...fillerTracks('eighties', 6),
    ],
  },
  [playlists[2].id]: {
    ...playlists[2],
    tracks: [
      track('33333333-ffff-4fff-8fff-ffffffffffff', 'The Final Countdown', 'Europe', 310, 5),
      ...fillerTracks('rock', 5, 2),
    ],
  },
  [playlists[3].id]: {
    ...playlists[3],
    trackCount: 3,
    tracks: fillerTracks('shrinking', 3),
  },
};

function track(
  id: string,
  title: string,
  artist: string,
  durationSeconds: number,
  difficulty: number | null = null,
) {
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
    difficulty,
  };
}

function fillerTracks(prefix: string, count: number, difficulty: number | null = null) {
  return Array.from({ length: count }, (_, index) =>
    track(`${prefix}-${index}`, `${prefix} track ${index + 1}`, 'Test artist', 180, difficulty),
  );
}

async function installPlayerSession(page: Page): Promise<void> {
  await page.addInitScript((user) => {
    localStorage.setItem('access_token', 'player-access-token');
    localStorage.setItem('current_user', JSON.stringify(user));
  }, player);
}

async function mockCatalogue(page: Page): Promise<void> {
  await page.route('**/api/playlists', (route) => route.fulfill({ json: playlists }));
  await page.route('**/api/playlists/*', (route) => {
    const id = route.request().url().split('/').at(-1) ?? '';
    return route.fulfill({ json: details[id as keyof typeof details] });
  });
}

test.describe('game configuration', () => {
  test('configures a playlist and creates a solo session', async ({ page }) => {
    await installPlayerSession(page);
    await mockCatalogue(page);
    await page.route('**/api/sessions', (route) =>
      route.fulfill({ status: 201, json: soloSessionResponse }),
    );

    await page.goto('/lobby/create');

    await expect(page.getByRole('heading', { name: 'Configure a new game' })).toBeVisible();
    await expect(page.getByRole('radio', { name: /Too small playlist/ })).toHaveCount(0);
    await expect(page.getByRole('radio', { name: /Eighties essentials/ })).toHaveAttribute(
      'aria-checked',
      'true',
    );
    await expect(page.locator('.track-preview > div').first()).toContainText('Take On Me');

    const trackCount = page.getByRole('slider', { name: 'Number of tracks' });
    await expect(trackCount).toHaveAttribute('min', '5');
    await expect(trackCount).toHaveAttribute('max', '8');
    await expect(trackCount).toHaveAttribute('step', '1');
    await expect(trackCount).toHaveValue('8');
    const roundDuration = page.getByRole('slider', { name: 'Maximum answer time' });
    await expect(roundDuration).toHaveAttribute('min', '10');
    await expect(roundDuration).toHaveAttribute('max', '60');
    await expect(roundDuration).toHaveAttribute('step', '5');
    await expect(roundDuration).toHaveValue('30');

    await page.getByRole('radio', { name: /Rock anthems/ }).click();
    await expect(
      page.getByRole('complementary').getByRole('heading', { name: 'Rock anthems' }),
    ).toBeVisible();
    await expect(trackCount).toHaveAttribute('max', '6');
    await trackCount.fill('5');
    await roundDuration.fill('45');

    const createButton = page.getByRole('button', { name: 'Create session' });
    await page.getByRole('button', { name: '5 stars, 1 track' }).click();
    await expect(trackCount).toHaveAttribute('max', '5');
    await page.getByRole('button', { name: '2 stars, 5 tracks' }).click();
    await expect(page.getByText('Only 0 tracks match these levels')).toBeVisible();
    await expect(createButton).toBeDisabled();
    await page.getByRole('button', { name: '2 stars, 5 tracks' }).click();
    await expect(createButton).toBeEnabled();

    const [request] = await Promise.all([
      page.waitForRequest('**/api/sessions'),
      page.getByRole('button', { name: 'Create session' }).click(),
    ]);
    expect(request.method()).toBe('POST');
    expect(request.postDataJSON()).toEqual({
      playlistId: playlists[2].id,
      trackCount: 5,
      maxRoundSeconds: 45,
      extensions: {},
      difficultyLevels: [1, 2, 3, 4],
      includeUnrated: true,
      allTracks: false,
    });

    await expect(page).toHaveURL(new RegExp(`/game/${SESSION_ID}$`));
    await expect(page.getByRole('heading', { name: 'Setting up your game…' })).toBeVisible();
    await expect(page.getByText('Rock anthems')).toBeVisible();
    await expect(page.getByText('5 tracks · 45s per track')).toBeVisible();
  });

  test('surfaces an actionable error when the playlist lost eligible tracks', async ({ page }) => {
    await installPlayerSession(page);
    await mockCatalogue(page);
    await page.route('**/api/sessions', (route) =>
      route.fulfill({
        status: 409,
        json: { code: 'GAME_PLAYLIST_INSUFFICIENT_TRACKS', message: 'not enough tracks' },
      }),
    );

    await page.goto('/lobby/create');
    await expect(page.getByRole('radio', { name: /Eighties essentials/ })).toHaveAttribute(
      'aria-checked',
      'true',
    );
    await page.getByRole('button', { name: 'Create session' }).click();

    await expect(page.getByRole('alert')).toContainText('Pick fewer tracks or another playlist');
    await expect(page).toHaveURL(/\/lobby\/create$/);
  });

  test('cannot submit when the selected playlist no longer has enough tracks', async ({ page }) => {
    await installPlayerSession(page);
    await mockCatalogue(page);

    await page.goto('/lobby/create');
    await page.getByRole('radio', { name: /Shrinking playlist/ }).click();

    await expect(page.getByText('no longer contains enough published tracks')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Create session' })).toBeDisabled();
  });

  test('requires authentication before opening game configuration', async ({ page }) => {
    await page.route('**/api/auth/refresh', (route) =>
      route.fulfill({
        status: 401,
        json: { code: 'UNAUTHORIZED', message: 'Authentication required' },
      }),
    );

    await page.goto('/lobby/create');

    await expect(page).toHaveURL(/\/auth\/login\?returnUrl=%2Flobby%2Fcreate$/);
  });
});
