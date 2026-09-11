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

  test('edits metadata and aliases, previews, publishes and confirms deletion', async ({ page }) => {
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

    await page.goto('/admin/tracks');
    await page.getByTitle('Edit track').first().click();
    await expect(page.getByRole('heading', { name: 'Edit Midnight Echo' })).toBeVisible();
    await expect(page.getByText('Echo at Midnight')).toBeVisible();

    await page.getByLabel('Title').fill('Midnight Echoes');
    await page.getByRole('button', { name: 'Save metadata' }).click();
    await expect(page.getByText('Track metadata saved.')).toBeVisible();

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
