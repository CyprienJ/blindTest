import { expect, Page, test } from '@playwright/test';

const admin = {
  id: '22222222-2222-4222-8222-222222222222',
  username: 'playlist-admin',
  email: 'admin@example.com',
  role: 'ADMIN',
  avatarUrl: null,
};

const firstTrack = track('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Africa', 'Toto');
const secondTrack = track('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'Take On Me', 'a-ha');

function track(id: string, title: string, artist: string) {
  return {
    id,
    title,
    artist,
    album: null,
    composer: null,
    work: null,
    category: 'Pop',
    decade: 1980,
    durationSeconds: 240,
    excerptStartSeconds: 0,
    imageUrl: null,
    audioUrl: null,
    audioChecksum: 'a'.repeat(64),
    audioSizeBytes: 1234,
    youtubeUrl: null,
    status: 'PUBLISHED',
    createdAt: '2026-08-01T12:00:00',
  };
}

async function installAdminSession(page: Page): Promise<void> {
  await page.addInitScript((user) => {
    localStorage.setItem('access_token', 'admin-access-token');
    localStorage.setItem('current_user', JSON.stringify(user));
  }, admin);
}

function pageResponse(content: unknown[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    number: 0,
    size: 20,
    first: true,
    last: true,
  };
}

test.describe('admin playlist editor', () => {
  test.beforeEach(async ({ page }) => installAdminSession(page));

  test('creates a playable ordered playlist without direct API use', async ({ page }) => {
    const playlist = {
      id: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
      name: '80s Classics',
      description: 'Big choruses',
      imageUrl: null,
      trackCount: 0,
      tracks: [] as (typeof firstTrack)[],
    };
    let exists = false;

    await page.route('**/api/admin/playlists', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ json: exists ? [{ ...playlist, tracks: undefined }] : [] });
        return;
      }
      const metadata = route.request().postDataJSON();
      Object.assign(playlist, metadata);
      exists = true;
      await route.fulfill({ status: 201, json: { ...playlist, tracks: undefined } });
    });
    await page.route(`**/api/admin/playlists/${playlist.id}/tracks/order`, async (route) => {
      const ids = route.request().postDataJSON().trackIds as string[];
      playlist.tracks = ids.map((id) => [firstTrack, secondTrack].find((item) => item.id === id)!);
      await route.fulfill({ json: playlist });
    });
    await page.route(`**/api/admin/playlists/${playlist.id}/tracks`, async (route) => {
      const { trackId } = route.request().postDataJSON();
      playlist.tracks.push([firstTrack, secondTrack].find((item) => item.id === trackId)!);
      playlist.trackCount = playlist.tracks.length;
      await route.fulfill({ json: playlist });
    });
    await page.route(
      `**/api/admin/playlists/${playlist.id}/tracks/${firstTrack.id}`,
      async (route) => {
        playlist.tracks = playlist.tracks.filter((item) => item.id !== firstTrack.id);
        playlist.trackCount = playlist.tracks.length;
        await route.fulfill({ status: 204 });
      },
    );
    await page.route(`**/api/admin/playlists/${playlist.id}`, async (route) => {
      if (route.request().method() === 'PUT') {
        Object.assign(playlist, route.request().postDataJSON());
      }
      await route.fulfill({ json: playlist });
    });
    await page.route('**/api/admin/tracks?*', async (route) => {
      const query = new URL(route.request().url()).searchParams.get('search')?.toLowerCase() ?? '';
      const tracks = [firstTrack, secondTrack].filter((item) =>
        `${item.title} ${item.artist}`.toLowerCase().includes(query),
      );
      await route.fulfill({ json: pageResponse(tracks) });
    });

    await page.goto('/admin/playlists');
    await expect(page.getByRole('heading', { name: 'Playlist editor' })).toBeVisible();
    await page.getByRole('button', { name: 'New playlist' }).first().click();
    await page.getByLabel('Playlist name').fill('80s Classics');
    await page.getByLabel('Playlist description').fill('Big choruses');
    await page.getByRole('button', { name: 'Create playlist' }).click();
    await expect(page.getByText('Playlist saved.')).toBeVisible();

    await page.getByRole('button', { name: 'Add Africa' }).click();
    await page.getByRole('button', { name: 'Add Take On Me' }).click();
    await expect(page.locator('.track-row')).toHaveCount(2);
    await expect(page.locator('.track-row').first()).toContainText('Africa');

    await page.getByRole('button', { name: 'Move Take On Me up' }).click();
    await expect(page.locator('.track-row').first()).toContainText('Take On Me');
    await expect(page.getByText('Track order saved.')).toBeVisible();

    await page.getByLabel('Playlist description').fill('Synth-pop and arena classics');
    await page.getByRole('button', { name: 'Save details' }).click();
    await expect(page.getByText('Playlist details saved.')).toBeVisible();

    await page.getByRole('button', { name: 'Remove Africa' }).click();
    await expect(page.locator('.track-row')).toHaveCount(1);
    await expect(page.getByText('Africa removed from the playlist.')).toBeVisible();

    await page.getByLabel('Search published tracks').fill('Africa');
    await expect(page.getByRole('button', { name: 'Add Take On Me' })).toBeHidden();
  });

  test('warns about metadata changes and restores a failed optimistic reorder', async ({ page }) => {
    const playlist = {
      id: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
      name: 'Ordered classics',
      description: null,
      imageUrl: null,
      trackCount: 2,
      tracks: [firstTrack, secondTrack],
    };

    await page.route('**/api/admin/playlists', (route) =>
      route.fulfill({ json: [{ ...playlist, tracks: undefined }] }),
    );
    await page.route(`**/api/admin/playlists/${playlist.id}`, (route) =>
      route.fulfill({ json: playlist }),
    );
    await page.route(`**/api/admin/playlists/${playlist.id}/tracks/order`, (route) =>
      route.fulfill({
        status: 400,
        json: { code: 'PLAYLIST_ORDER_INVALID', message: 'Playlist order is stale.' },
      }),
    );
    await page.route('**/api/admin/tracks?*', (route) =>
      route.fulfill({ json: pageResponse([firstTrack, secondTrack]) }),
    );

    await page.goto('/admin/playlists');
    await expect(page.locator('.track-row').first()).toContainText('Africa');
    await page.getByRole('button', { name: 'Move Take On Me up' }).click();
    await expect(page.getByRole('alert')).toContainText('The previous order was restored.');
    await expect(page.locator('.track-row').first()).toContainText('Africa');

    await page.getByLabel('Playlist description').fill('Unsaved description');
    page.once('dialog', async (dialog) => {
      expect(dialog.message()).toContain('Discard unsaved playlist changes?');
      await dialog.dismiss();
    });
    await page.getByRole('link', { name: 'Track catalogue' }).click();
    await expect(page).toHaveURL(/\/admin\/playlists/);
  });
});
