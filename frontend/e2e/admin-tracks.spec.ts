import { expect, Page, test } from '@playwright/test';

const admin = {
  id: '22222222-2222-4222-8222-222222222222',
  username: 'catalogue-admin',
  email: 'admin@example.com',
  role: 'ADMIN',
  avatarUrl: null,
};

const pendingTrack = {
  id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  title: 'Midnight Echo',
  artist: 'The Satellites',
  album: 'After Dark',
  composer: null,
  work: null,
  category: 'Pop',
  decade: 1980,
  durationSeconds: 214,
  excerptStartSeconds: 12,
  excerptDurationSeconds: 60,
  imageUrl: null,
  audioUrl: '/media/midnight.mp3',
  audioChecksum: 'a'.repeat(64),
  audioSizeBytes: 1234,
  youtubeUrl: 'https://youtu.be/example',
  status: 'PENDING',
  createdAt: '2026-07-30T12:00:00',
};

const publishedTrack = {
  ...pendingTrack,
  id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
  title: 'Neon Hearts',
  status: 'PUBLISHED',
};

function pageResponse(content: unknown[], totalElements = content.length) {
  return {
    content,
    totalElements,
    totalPages: totalElements ? Math.ceil(totalElements / 10) : 0,
    number: 0,
    size: 10,
    first: true,
    last: totalElements <= 10,
  };
}

async function installAdminSession(page: Page): Promise<void> {
  await page.addInitScript((user) => {
    localStorage.setItem('access_token', 'admin-access-token');
    localStorage.setItem('current_user', JSON.stringify(user));
  }, admin);
}

async function mockCatalogue(page: Page): Promise<void> {
  await page.route('**/api/admin/tracks?*', async (route) => {
    const url = new URL(route.request().url());
    const status = url.searchParams.get('status');
    const search = url.searchParams.get('search');
    const size = Number(url.searchParams.get('size'));
    let tracks = [pendingTrack, publishedTrack];
    if (status) {
      tracks = tracks.filter((track) => track.status === status);
    }
    if (search) {
      tracks = tracks.filter((track) =>
        `${track.title} ${track.artist} ${track.album} ${track.category}`
          .toLowerCase()
          .includes(search.toLowerCase()),
      );
    }
    const response = pageResponse(size === 1 ? tracks.slice(0, 1) : tracks, tracks.length);
    await route.fulfill({ json: response });
  });
}

test.describe('admin track catalogue', () => {
  test.beforeEach(async ({ page }) => {
    await installAdminSession(page);
    await mockCatalogue(page);
  });

  test('lists, searches and filters every workflow status', async ({ page }) => {
    await page.goto('/admin/tracks');

    await expect(page.getByRole('heading', { name: 'Track catalogue' })).toBeVisible();
    await expect(page.getByText('Midnight Echo')).toBeVisible();
    await expect(page.getByText('Neon Hearts')).toBeVisible();
    await expect(page.getByRole('button', { name: /Published 1/ })).toBeVisible();

    await page.getByRole('button', { name: /Pending review 1/ }).click();
    await expect(page.getByText('Midnight Echo')).toBeVisible();
    await expect(page.getByText('Neon Hearts')).toBeHidden();

    await page.getByLabel('Search tracks').fill('nothing matches');
    await expect(page.getByRole('heading', { name: 'No tracks found' })).toBeVisible();
  });

  test('suggests covers from the metadata and saves the chosen one', async ({ page }) => {
    const pixel = Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
      'base64',
    );
    let searchBody: Record<string, unknown> = {};
    let updateBody: Record<string, unknown> = {};
    await page.route('**/api/admin/cover-suggestions', async (route) => {
      searchBody = route.request().postDataJSON();
      await route.fulfill({
        json: [
          { candidateId: 'c1', source: 'DEEZER', album: 'After Dark', artist: 'The Satellites' },
          { candidateId: 'c2', source: 'ITUNES', album: 'Night Drive', artist: 'The Satellites' },
        ],
      });
    });
    await page.route('**/api/admin/cover-suggestions/*/thumbnail', (route) =>
      route.fulfill({ status: 200, contentType: 'image/png', body: pixel }),
    );
    await page.route(`**/api/admin/tracks/${pendingTrack.id}/aliases`, (route) =>
      route.fulfill({ json: [] }),
    );
    await page.route(`**/api/admin/tracks/${pendingTrack.id}/preview-url`, (route) =>
      route.fulfill({ status: 409, json: { code: 'CONFLICT' } }),
    );
    await page.route(`**/api/admin/tracks/${pendingTrack.id}/waveform`, (route) =>
      route.fulfill({ status: 409, json: { code: 'TRACK_WAVEFORM_UNAVAILABLE' } }),
    );
    await page.route(`**/api/admin/tracks/${pendingTrack.id}`, async (route) => {
      updateBody = route.request().postDataJSON();
      await route.fulfill({
        json: {
          ...pendingTrack,
          coverUrl: '/api/covers/7c9e6679-7425-40de-944b-e07fc1f90ae7',
          coverSource: 'ITUNES',
        },
      });
    });
    await page.route('**/api/covers/*', (route) =>
      route.fulfill({ status: 200, contentType: 'image/png', body: pixel }),
    );

    await page.goto('/admin/tracks');
    await page.getByTitle('Edit track').first().click();
    const covers = page.getByRole('radiogroup', { name: 'Cover image' });
    await expect(covers.getByRole('radio', { name: /After Dark/ })).toBeVisible();
    await expect(covers.locator('img')).toHaveCount(2);
    expect(searchBody).toEqual({
      title: 'Midnight Echo',
      artist: 'The Satellites',
      album: 'After Dark',
    });

    await covers.getByRole('radio', { name: /Night Drive/ }).click();
    await expect(covers.getByRole('radio', { name: /Night Drive/ })).toHaveAttribute(
      'aria-checked',
      'true',
    );
    await page.getByRole('button', { name: 'Save metadata' }).click();
    await expect(page.getByText('Track metadata saved.')).toBeVisible();
    expect(updateBody).toEqual(expect.objectContaining({ coverCandidateId: 'c2' }));
    await expect(covers.getByRole('radio', { name: 'Current cover' })).toHaveAttribute(
      'aria-checked',
      'true',
    );
  });

  test('edits metadata and aliases, previews, publishes and confirms deletion', async ({
    page,
  }) => {
    let currentTrack = { ...pendingTrack };
    const aliases = [
      {
        id: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
        type: 'TITLE',
        value: 'Echo at Midnight',
      },
    ];

    await page.route(`**/api/admin/tracks/${pendingTrack.id}/aliases`, async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ json: aliases });
      } else {
        const body = route.request().postDataJSON();
        await route.fulfill({
          status: 201,
          json: { id: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd', ...body },
        });
      }
    });
    await page.route(`**/api/admin/tracks/${pendingTrack.id}`, async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({ status: 204 });
      } else {
        currentTrack = { ...currentTrack, ...route.request().postDataJSON() };
        await route.fulfill({ json: currentTrack });
      }
    });
    await page.route(`**/api/admin/tracks/${pendingTrack.id}/preview-url`, (route) =>
      route.fulfill({
        json: {
          url: '/media/secure-preview.mp3',
          expiresAt: '2026-07-30T18:00:00Z',
        },
      }),
    );
    await page.route('**/media/secure-preview.mp3', (route) =>
      route.fulfill({ status: 200, contentType: 'audio/mpeg', body: '' }),
    );
    await page.route(`**/api/admin/tracks/${pendingTrack.id}/validate`, (route) =>
      route.fulfill({ json: { ...currentTrack, status: 'PUBLISHED' } }),
    );
    await page.route(`**/api/admin/tracks/${pendingTrack.id}/waveform`, (route) =>
      route.fulfill({ json: { durationSeconds: 214, peaks: [10, 80, 40, 60] } }),
    );
    let updateBody: Record<string, unknown> = {};
    page.on('request', (request) => {
      if (request.method() === 'PUT' && request.url().endsWith(`/tracks/${pendingTrack.id}`)) {
        updateBody = request.postDataJSON();
      }
    });

    await page.goto('/admin/tracks');
    await page.getByTitle('Edit track').first().click();
    await expect(page.getByRole('heading', { name: 'Edit Midnight Echo' })).toBeVisible();
    await expect(page.getByText('Echo at Midnight')).toBeVisible();

    const editor = page.locator('.edit-modal');
    await expect(editor.locator('audio')).toBeVisible();
    await expect(editor.locator('.waveform path')).toBeVisible();

    await page.getByLabel('Title').fill('Midnight Echoes');
    await page.getByRole('radio', { name: '3 stars' }).click();
    await page.getByLabel('Window length').fill('20');
    await page.getByLabel('Excerpt start').fill('90');
    await editor.getByLabel('Spotify').fill('https://evil.example/track');
    await editor.getByLabel('Spotify').blur();
    await expect(editor.getByText('Enter a Spotify link.')).toBeVisible();
    await editor.getByLabel('Spotify').fill('https://open.spotify.com/track/midnight');
    await expect(editor.getByText('20-second game window')).toBeVisible();
    await page.getByRole('button', { name: 'Save metadata' }).click();
    await expect(page.getByText('Track metadata saved.')).toBeVisible();
    expect(updateBody).toEqual(
      expect.objectContaining({
        title: 'Midnight Echoes',
        difficulty: 3,
        excerptStartSeconds: 90,
        excerptDurationSeconds: 20,
      }),
    );
    expect(updateBody['streamingLinks']).toEqual({
      deezer: null,
      youtube: null,
      spotify: 'https://open.spotify.com/track/midnight',
      qobuz: null,
      tidal: null,
      soundcloud: null,
    });

    await page.getByLabel('Alternative answer').fill('Midnight');
    await page.getByRole('button', { name: 'Add alias' }).click();
    await expect(page.getByText('Alternative answer added.')).toBeVisible();
    await page.getByLabel('Close editor').click();

    await page.getByTitle('Preview audio').click();
    await expect(page.getByRole('heading', { name: 'Midnight Echoes' })).toBeVisible();
    await expect(page.locator('audio')).toBeVisible();
    await page.getByLabel('Close preview').click();

    await page.getByTitle('Publish track').click();
    await expect(page.getByText('Track published.')).toBeVisible();

    await page.getByTitle('Delete track').first().click();
    await expect(page.getByRole('heading', { name: 'Delete this track?' })).toBeVisible();
    await page.getByRole('button', { name: 'Delete track' }).click();
    await expect(page.getByText('Track deleted.')).toBeVisible();
  });

  test('shows safe backend/storage failures with retry', async ({ page }) => {
    await page.route(`**/api/admin/tracks/${pendingTrack.id}/preview-url`, (route) =>
      route.fulfill({
        status: 409,
        json: { code: 'CONFLICT', message: 'Stored media is unavailable.' },
      }),
    );

    await page.goto('/admin/tracks');
    await page.getByTitle('Preview audio').click();
    await expect(page.getByRole('alert')).toContainText('Stored media is unavailable.');
    await expect(page.getByRole('button', { name: 'Retry' })).toBeVisible();
  });
});

test('normal users cannot enter the administration routes', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('access_token', 'user-access-token');
    localStorage.setItem(
      'current_user',
      JSON.stringify({
        id: '11111111-1111-4111-8111-111111111111',
        username: 'musicfan',
        email: 'musicfan@example.com',
        role: 'USER',
        avatarUrl: null,
      }),
    );
  });

  await page.goto('/admin/tracks');
  await expect(page).toHaveURL(/\/home$/);
});
